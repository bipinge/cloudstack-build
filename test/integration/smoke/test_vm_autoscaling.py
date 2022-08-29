# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

"""
Tests of VM Autoscaling
"""

import logging
import time
import datetime

from nose.plugins.attrib import attr
from marvin.cloudstackTestCase import cloudstackTestCase

from marvin.lib.base import (Account,
                             Autoscale,
                             AutoScaleCondition,
                             AutoScalePolicy,
                             AutoScaleVmProfile,
                             AutoScaleVmGroup,
                             Configurations,
                             DiskOffering,
                             Domain,
                             Project,
                             ServiceOffering,
                             VirtualMachine,
                             Volume,
                             Zone,
                             Network,
                             NetworkOffering,
                             PublicIPAddress,
                             LoadBalancerRule,
                             SSHKeyPair)

from marvin.lib.common import (get_domain,
                               get_zone,
                               get_template)

MIN_MEMBER = 1
MAX_MEMBER = 2
DEFAULT_DESTROY_VM_GRACE_PERIOD = 60
DEFAULT_DURATION = 120
DEFAULT_INTERVAL = 30
NAME_PREFIX = "AS-VmGroup-"

class TestVmAutoScaling(cloudstackTestCase):
    """
    Test VM autoscaling
    """
    @classmethod
    def setUpClass(cls):
        cls.testClient = super(
            TestVmAutoScaling,
            cls).getClsTestClient()
        cls.apiclient = cls.testClient.getApiClient()
        cls.services = cls.testClient.getParsedTestDataConfig()

        zone = get_zone(cls.apiclient, cls.testClient.getZoneForTests())
        cls.zone = Zone(zone.__dict__)
        cls.template = get_template(cls.apiclient, cls.zone.id)
        cls.templatesize = int(cls.template.size / (1024 ** 3))
        cls._cleanup = []

        cls.logger = logging.getLogger("TestVmAutoScaling")
        cls.stream_handler = logging.StreamHandler()
        cls.logger.setLevel(logging.DEBUG)
        cls.logger.addHandler(cls.stream_handler)

        cls.domain = get_domain(cls.apiclient)

        # 1. Create small service offering
        cls.service_offering = ServiceOffering.create(
            cls.apiclient,
            cls.services["service_offerings"]["small"]
        )
        cls._cleanup.append(cls.service_offering)

        cls.service_offering_new = ServiceOffering.create(
            cls.apiclient,
            cls.services["service_offerings"]["small"]
        )
        cls._cleanup.append(cls.service_offering_new)

        # 2. Create disk offerings (fixed and custom)
        cls.disk_offering_override = DiskOffering.create(
            cls.apiclient,
            cls.services["disk_offering"],
            disksize=cls.templatesize + 1
        )
        cls._cleanup.append(cls.disk_offering_override)

        cls.disk_offering_custom = DiskOffering.create(
            cls.apiclient,
            cls.services["disk_offering"],
            custom=True
        )
        cls._cleanup.append(cls.disk_offering_custom)

        cls.disk_offering_custom_new = DiskOffering.create(
            cls.apiclient,
            cls.services["disk_offering"],
            custom=True
        )
        cls._cleanup.append(cls.disk_offering_custom_new)

        # 3. Create network offering for isolated networks
        cls.network_offering_isolated = NetworkOffering.create(
            cls.apiclient,
            cls.services["isolated_network_offering"]
        )
        cls.network_offering_isolated.update(cls.apiclient, state='Enabled')
        cls._cleanup.append(cls.network_offering_isolated)

        # 4. Create sub-domain
        cls.sub_domain = Domain.create(
            cls.apiclient,
            cls.services["acl"]["domain1"]
        )
        cls._cleanup.append(cls.sub_domain)

        # 5. Create regular user
        cls.regular_user = Account.create(
            cls.apiclient,
            cls.services["acl"]["accountD11A"],
            domainid=cls.sub_domain.id
        )
        cls._cleanup.append(cls.regular_user)

        # 5. Create api clients for regular user
        cls.regular_user_user = cls.regular_user.user[0]
        cls.regular_user_apiclient = cls.testClient.getUserApiClient(
            cls.regular_user_user.username, cls.sub_domain.name
        )

        # 7. Create networks for regular user
        cls.services["network"]["name"] = "Test Network Isolated - Regular user - 1"
        cls.user_network_1 = Network.create(
            cls.regular_user_apiclient,
            cls.services["network"],
            networkofferingid=cls.network_offering_isolated.id,
            zoneid=cls.zone.id
        )

        cls.services["network"]["name"] = "Test Network Isolated - Regular user - 2"
        cls.user_network_2 = Network.create(
            cls.regular_user_apiclient,
            cls.services["network"],
            networkofferingid=cls.network_offering_isolated.id,
            zoneid=cls.zone.id
        )

        # 8. Create SSH Keypairs
        cls.keypair_1 = SSHKeyPair.create(
            cls.regular_user_apiclient,
            name="keypair1"
        )
        cls.keypair_2 = SSHKeyPair.create(
            cls.regular_user_apiclient,
            name="keypair2"
        )

        # 9. Get counters for cpu and memory
        counters = Autoscale.listCounters(
            cls.regular_user_apiclient,
            provider="VirtualRouter"
        )
        for counter in counters:
            if counter.source == 'cpu':
                cls.counter_cpu_id = counter.id
            elif counter.source == 'memory':
                cls.counter_memory_id = counter.id

        # 10. Create AS conditions
        cls.scale_up_condition = AutoScaleCondition.create(
            cls.regular_user_apiclient,
            counterid = cls.counter_cpu_id,
            relationaloperator = "GE",
            threshold = 1
        )

        cls.scale_down_condition = AutoScaleCondition.create(
            cls.regular_user_apiclient,
            counterid = cls.counter_memory_id,
            relationaloperator = "LE",
            threshold = 100
        )

        cls._cleanup.append(cls.scale_up_condition)
        cls._cleanup.append(cls.scale_down_condition)

        # 11. Create AS policies
        cls.scale_up_policy = AutoScalePolicy.create(
            cls.regular_user_apiclient,
            action='ScaleUp',
            conditionids=cls.scale_up_condition.id,
            duration=DEFAULT_DURATION
        )

        cls.scale_down_policy = AutoScalePolicy.create(
            cls.regular_user_apiclient,
            action='ScaleDown',
            conditionids=cls.scale_down_condition.id,
            duration=DEFAULT_DURATION
        )

        cls._cleanup.append(cls.scale_up_policy)
        cls._cleanup.append(cls.scale_down_policy)

        # 12. Create AS VM Profile
        cls.otherdeployparams = []
        cls.addOtherDeployParam(cls.otherdeployparams, "overridediskofferingid", cls.disk_offering_override.id)
        cls.addOtherDeployParam(cls.otherdeployparams, "diskofferingid", cls.disk_offering_custom.id)
        cls.addOtherDeployParam(cls.otherdeployparams, "disksize", 3)
        cls.addOtherDeployParam(cls.otherdeployparams, "keypairs", cls.keypair_1.name + "," + cls.keypair_2.name)
        cls.addOtherDeployParam(cls.otherdeployparams, "networkids", cls.user_network_1.id + "," + cls.user_network_2.id)

        cls.autoscaling_vmprofile = AutoScaleVmProfile.create(
            cls.regular_user_apiclient,
            serviceofferingid=cls.service_offering.id,
            zoneid=cls.zone.id,
            templateid=cls.template.id,
            destroyvmgraceperiod=DEFAULT_DESTROY_VM_GRACE_PERIOD,
            otherdeployparams=cls.otherdeployparams
        )

        cls._cleanup.append(cls.autoscaling_vmprofile)

        # 13. Acquire Public IP and create LoadBalancer rule
        cls.public_ip_address = PublicIPAddress.create(
            cls.regular_user_apiclient,
            services=cls.services["network"],
            networkid=cls.user_network_1.id
        )

        cls.services["lbrule"]["openfirewall"] = False
        cls.load_balancer_rule = LoadBalancerRule.create(
            cls.regular_user_apiclient,
            cls.services["lbrule"],
            ipaddressid=cls.public_ip_address.ipaddress.id,
            networkid=cls.user_network_1.id
        )

        # 14. Create AS VM Group
        cls.autoscaling_vmgroup = AutoScaleVmGroup.create(
            cls.regular_user_apiclient,
            name=NAME_PREFIX + format(datetime.datetime.now(), '%Y%m%d-%H%M%S'),
            lbruleid=cls.load_balancer_rule.id,
            minmembers=MIN_MEMBER,
            maxmembers=MAX_MEMBER,
            scaledownpolicyids=cls.scale_down_policy.id,
            scaleuppolicyids=cls.scale_up_policy.id,
            vmprofileid=cls.autoscaling_vmprofile.id,
            interval=DEFAULT_INTERVAL
        )

        # 15. Get global config
        check_interval_config = Configurations.list(cls.apiclient, name="autoscale.stats.interval")
        cls.check_interval = check_interval_config[0].value

        # 16. define VMs not be checked
        cls.excluded_vm_ids = []

    @classmethod
    def addOtherDeployParam(cls, otherdeployparams, name, value):
        otherdeployparams.append({
            'name': name,
            'value': value
        })

    @classmethod
    def tearDownClass(cls):
        super(TestVmAutoScaling, cls).tearDownClass()

    def setUp(self):
        self.apiclient = self.testClient.getApiClient()
        self.cleanup = []

    def tearDown(self):
        super(TestVmAutoScaling, self).tearDown()

    def delete_vmgroup(self, vmgroup, apiclient, cleanup=None, expected=True):
        result = True
        try:
            AutoScaleVmGroup.delete(
                vmgroup,
                apiclient,
                cleanup=cleanup
            )
        except Exception as ex:
            result = False
            if expected:
                self.fail(f"Failed to remove Autoscaling VM Group, but expected to succeed : {ex}")
        if result and not expected:
            self.fail("Autoscaling VM Group is removed successfully, but expected to fail")

    def verifyVmCountAndProfiles(self, vmCount):
        vms = VirtualMachine.list(
            self.regular_user_apiclient,
            autoscalevmgroupid=self.autoscaling_vmgroup.id,
            listall=True
        )
        self.assertEqual(
            isinstance(vms, list),
            True,
            "List virtual machines should return a valid list"
        )
        self.assertEqual(
            len(vms) >= vmCount,
            True,
            "The number of virtual machines %s should be equal to or greater than %s" % (len(vms), vmCount)
        )

        for vm in vms:
            if vm.id not in self.excluded_vm_ids:
                self.logger.debug("==== Verifying profiles of new VM %s (%s) ====" % (vm.name, vm.id))
                self.verifyVmProfile(vm)

    def verifyVmProfile(self, vm):
        self.excluded_vm_ids.append(vm.id)

        datadisksizeInBytes = None
        diskofferingid = None
        rootdisksizeInBytes = None
        sshkeypairs = None

        affinitygroupIdsArray = []
        for affinitygroup in vm.affinitygroup:
            affinitygroupIdsArray.append(affinitygroup.id)
        affinitygroupids = ",".join(affinitygroupIdsArray)

        if vm.diskofferingid:
            diskofferingid = vm.diskofferingid
        if vm.keypairs:
            sshkeypairs = vm.keypairs
        serviceofferingid = vm.serviceofferingid
        templateid = vm.templateid

        networkIdsArray = []
        for nic in vm.nic:
            networkIdsArray.append(nic.networkid)
        networkids = ",".join(networkIdsArray)

        volumes = Volume.list(
            self.regular_user_apiclient,
            virtualmachineid=vm.id,
            listall=True
        )
        for volume in volumes:
            if volume.type == 'ROOT':
                rootdisksizeInBytes = volume.size
            elif volume.type == 'DATADISK':
                datadisksizeInBytes = volume.size
                diskofferingid = volume.diskofferingid

        vmprofiles_list = AutoScaleVmProfile.list(
            self.regular_user_apiclient,
            listall=True,
            id=self.autoscaling_vmprofile.id
        )
        vmprofile = vmprofiles_list[0]
        vmprofile_otherdeployparams = vmprofile.otherdeployparams

        self.logger.debug("vmprofile_otherdeployparams = " + str(vmprofile_otherdeployparams))
        self.logger.debug("templateid = " + templateid)
        self.logger.debug("serviceofferingid = " + serviceofferingid)
        self.logger.debug("rootdisksizeInBytes = " + str(rootdisksizeInBytes))
        self.logger.debug("datadisksizeInBytes = " + str(datadisksizeInBytes))
        self.logger.debug("diskofferingid = " + diskofferingid)
        self.logger.debug("sshkeypairs = " + sshkeypairs)
        self.logger.debug("networkids = " + networkids)
        self.logger.debug("affinitygroupids = " + affinitygroupids)

        self.assertEquals(templateid, vmprofile.templateid)
        self.assertEquals(serviceofferingid, vmprofile.serviceofferingid)

        if vmprofile_otherdeployparams.rootdisksize:
            self.assertEquals(int(rootdisksizeInBytes), int(vmprofile_otherdeployparams.rootdisksize) * (1024 ** 3))
        elif vmprofile_otherdeployparams.overridediskofferingid:
            self.assertEquals(vmprofile_otherdeployparams.overridediskofferingid, self.disk_offering_override.id)
            self.assertEquals(int(rootdisksizeInBytes), int(self.disk_offering_override.disksize) * (1024 ** 3))
        else:
            self.assertEquals(int(rootdisksizeInBytes), int(self.templatesize) * (1024 ** 3))

        if vmprofile_otherdeployparams.diskofferingid:
            self.assertEquals(diskofferingid, vmprofile_otherdeployparams.diskofferingid)
        if vmprofile_otherdeployparams.disksize:
            self.assertEquals(int(datadisksizeInBytes), int(vmprofile_otherdeployparams.disksize) * (1024 ** 3))

        if vmprofile_otherdeployparams.keypairs:
            self.assertEquals(sshkeypairs, vmprofile_otherdeployparams.keypairs)
        else:
            self.assertIsNone(sshkeypairs)

        if vmprofile_otherdeployparams.networkids:
            self.assertEquals(networkids, vmprofile_otherdeployparams.networkids)
        else:
            self.assertEquals(networkids, self.user_network_1.id)

        if vmprofile_otherdeployparams.affinitygroupids:
            self.assertEquals(affinitygroupids, vmprofile_otherdeployparams.affinitygroupids)
        else:
            self.assertEquals(affinitygroupids, '')

    @attr(tags=["advanced"], required_hardware="false")
    def test_01_scale_up_verify(self):
        """ Verify scale up of AutoScaling VM Group """
        self.logger.debug("test_01_scale_up_verify")

        # VM count increases from 0 to MIN_MEMBER
        sleeptime = int(int(self.check_interval)/1000) * 2
        self.logger.debug("==== Waiting %s seconds for %s VM(s) to be created ====" % (sleeptime, MIN_MEMBER))
        time.sleep(sleeptime)
        self.verifyVmCountAndProfiles(MIN_MEMBER)

        # VM count increases from MIN_MEMBER to MAX_MEMBER
        sleeptime = int(int(self.check_interval)/1000 + DEFAULT_INTERVAL + DEFAULT_DURATION) * (MAX_MEMBER - MIN_MEMBER)
        self.logger.debug("==== Waiting %s seconds for other %s VM(s) to be created ====" % (sleeptime, (MAX_MEMBER - MIN_MEMBER)))
        time.sleep(sleeptime)
        self.verifyVmCountAndProfiles(MAX_MEMBER)


    @attr(tags=["advanced"], required_hardware="false")
    def test_02_update_vmprofile_and_vmgroup(self):
        """ Verify update of AutoScaling VM Group and VM Profile"""
        self.logger.debug("test_02_update_vmprofile_and_vmgroup")

        vmprofiles_list = AutoScaleVmProfile.list(
            self.regular_user_apiclient,
            listall=True,
            id=self.autoscaling_vmprofile.id
        )
        self.assertEqual(
            isinstance(vmprofiles_list, list),
            True,
            "List autoscale profiles should return a valid list"
        )
        self.assertEqual(
            len(vmprofiles_list) == 1,
            True,
            "The number of autoscale profiles %s should be equal to 1" % (len(vmprofiles_list))
        )

        # Create new AS VM Profile
        otherdeployparams_new = []
        self.addOtherDeployParam(otherdeployparams_new, "rootdisksize", self.templatesize + 2)
        self.addOtherDeployParam(otherdeployparams_new, "diskofferingid", self.disk_offering_custom_new.id)
        self.addOtherDeployParam(otherdeployparams_new, "disksize", 5)
        self.addOtherDeployParam(otherdeployparams_new, "keypairs", self.keypair_1.name)
        self.addOtherDeployParam(otherdeployparams_new, "networkids", self.user_network_1.id)

        try:
            Autoscale.updateAutoscaleVMProfile(
                self.regular_user_apiclient,
                id = self.autoscaling_vmprofile.id,
                serviceofferingid = self.service_offering_new.id,
                destroyvmgraceperiod = DEFAULT_DESTROY_VM_GRACE_PERIOD + 1,
                otherdeployparams = otherdeployparams_new
            )
            self.fail("Autoscale VM Profile should not be updatable when VM Group is not Disabled")
        except Exception as ex:
            pass

        try:
            Autoscale.updateAutoscaleVMGroup(
                self.regular_user_apiclient,
                id = self.autoscaling_vmprofile.id,
                name=NAME_PREFIX + format(datetime.datetime.now(), '%Y%m%d-%H%M%S'),
                maxmembers = MAX_MEMBER + 1,
                minmembers = MIN_MEMBER + 1,
                interval = DEFAULT_INTERVAL + 1
            )
            self.fail("Autoscale VM Group should not be updatable when VM Group is not Disabled")
        except Exception as ex:
            pass

        self.autoscaling_vmgroup.disable(self.regular_user_apiclient)

        try:
            Autoscale.updateAutoscaleVMProfile(
                self.regular_user_apiclient,
                id = self.autoscaling_vmprofile.id,
                serviceofferingid = self.service_offering_new.id,
                destroyvmgraceperiod = DEFAULT_DESTROY_VM_GRACE_PERIOD + 1,
                otherdeployparams = otherdeployparams_new
            )
        except Exception as ex:
            self.fail("Autoscale VM Profile should be updatable when VM Group is Disabled")

        try:
            Autoscale.updateAutoscaleVMGroup(
                self.regular_user_apiclient,
                id = self.autoscaling_vmgroup.id,
                name=NAME_PREFIX + format(datetime.datetime.now(), '%Y%m%d-%H%M%S'),
                maxmembers = MAX_MEMBER + 1,
                minmembers = MIN_MEMBER + 1,
                interval = DEFAULT_INTERVAL + 1
            )
        except Exception as ex:
            self.fail("Autoscale VM Group should be updatable when VM Group is Disabled")

        self.autoscaling_vmgroup.enable(self.regular_user_apiclient)

        # VM count increases from MIN_MEMBER to MAX_MEMBER+1
        sleeptime = int(int(self.check_interval)/1000 + (DEFAULT_INTERVAL + 1) + DEFAULT_DURATION) * (MAX_MEMBER + 1 - MIN_MEMBER)
        self.logger.debug("==== Waiting %s seconds for other %s VM(s) to be created ====" % (sleeptime, (MAX_MEMBER + 1 - MIN_MEMBER)))
        time.sleep(sleeptime)
        self.verifyVmCountAndProfiles(MAX_MEMBER + 1)

    @attr(tags=["advanced"], required_hardware="false")
    def test_03_scale_down_verify(self):
        """ Verify scale down of AutoScaling VM Group """
        self.logger.debug("test_03_scale_down_verify")

        self.autoscaling_vmgroup.disable(self.regular_user_apiclient)

        policies = Autoscale.listAutoscalePolicies(
            self.regular_user_apiclient,
            action="ScaleUp",
            vmgroupid=self.autoscaling_vmgroup.id
        )
        self.assertEqual(
            isinstance(policies, list),
            True,
            "List autoscale policies should return a valid list"
        )
        self.assertEqual(
            len(policies) >= 1,
            True,
            "The number of autoscale policies %s should be equal to or greater than 1" % (len(policies))
        )
        scale_up_policy = policies[0]

        conditions = Autoscale.listConditions(
            self.regular_user_apiclient,
            policyid=scale_up_policy.id
        )
        self.assertEqual(
            isinstance(conditions, list),
            True,
            "List conditions should return a valid list"
        )

        for condition in conditions:
            if condition.counterid == self.counter_cpu_id:
                Autoscale.updateCondition(
                    self.regular_user_apiclient,
                    id=condition.id,
                    relationaloperator="GT",
                    threshold=101
                )

        policies = Autoscale.listAutoscalePolicies(
            self.regular_user_apiclient,
            action="ScaleDown",
            vmgroupid=self.autoscaling_vmgroup.id
        )
        self.assertEqual(
            isinstance(policies, list),
            True,
            "List autoscale policies should return a valid list"
        )
        self.assertEqual(
            len(policies) >= 1,
            True,
            "The number of autoscale policies %s should be equal to or greater than 1" % (len(policies))
        )
        scale_down_policy = policies[0]

        for condition in conditions:
            if condition.counterid == self.counter_memory_id:
                new_condition = Autoscale.createCondition(
                    self.regular_user_apiclient,
                    counterid=self.counter_memory_id,
                    relationaloperator="LT",
                    threshold=101
                )
                Autoscale.updateAutoscalePolicy(
                    self.regular_user_apiclient,
                    id=scale_down_policy.id,
                    conditionids=new_condition.id
                )
                Autoscale.deleteCondition(
                    self.regular_user_apiclient,
                    id=condition.id
                )

        self.autoscaling_vmgroup.enable(self.regular_user_apiclient)

        # VM count decreases from MAX_MEMBER+1 to MIN_MEMBER+1
        sleeptime = int(int(self.check_interval)/1000 + (DEFAULT_INTERVAL + 1) + DEFAULT_DURATION) * (MAX_MEMBER - MIN_MEMBER)
        self.logger.debug("==== Waiting %s seconds for %s VM(s) to be destroyed ====" % (sleeptime, MAX_MEMBER - MIN_MEMBER))
        time.sleep(sleeptime)
        self.verifyVmCountAndProfiles(MIN_MEMBER+1)

    @attr(tags=["advanced"], required_hardware="false")
    def test_04_remove_vm_and_vmgroup(self):
        """ Verify removal of AutoScaling VM Group and VM"""
        self.logger.debug("test_04_remove_vm_and_vmgroup")

        self.delete_vmgroup(self.autoscaling_vmgroup, self.regular_user_apiclient, cleanup=False, expected=False)
        self.delete_vmgroup(self.autoscaling_vmgroup, self.regular_user_apiclient, cleanup=True, expected=True)

    @attr(tags=["advanced"], required_hardware="false")
    def test_05_autoscaling_vmgroup_on_project_network(self):
        """ Testing VM autoscaling on project network """

        # Create project
        self.project = Project.create(
            self.regular_user_apiclient,
            self.services["project"]
        )
        self.cleanup.append(self.project)

        # Create project network
        self.services["network"]["name"] = "Test Network Isolated - Project"
        self.project_network = Network.create(
            self.regular_user_apiclient,
            self.services["network"],
            networkofferingid=self.network_offering_isolated.id,
            zoneid=self.zone.id
        )
        self.cleanup.append(self.project_network)
