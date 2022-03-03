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
""" P1 tests for Volumes
"""
# Import Local Modules
from nose.plugins.attrib import attr
from marvin.cloudstackTestCase import cloudstackTestCase
from marvin.cloudstackAPI import (listHypervisorCapabilities,
                                  attachIso,
                                  deleteVolume
                                  )
from marvin.lib.utils import cleanup_resources, validateList
from marvin.lib.base import (Account,
                             ServiceOffering,
                             VirtualMachine,
                             Volume,
                             Host,
                             Iso,
                             Configurations,
                             DiskOffering,
                             Domain,
                             StoragePool)
from marvin.lib.common import (get_domain,
                               get_zone,
                               get_template,
                               get_pod,
                               find_storage_pool_type,
                               update_resource_limit)
import re
import json
from types import SimpleNamespace
from marvin.codes import PASS
# Import System modules
import time


class TestAttachVolumeWithGroup(cloudstackTestCase):

    @classmethod
    def setUpClass(cls):
        cls.test_client = super(TestAttachVolumeWithGroup, cls).getClsTestClient()
        cls.api_client = cls.test_client.getApiClient()
        cls.test_data = cls.test_client.getParsedTestDataConfig()
        cls.domain = get_domain(cls.api_client)
        cls.zone = get_zone(cls.api_client, cls.test_client.getZoneForTests())
        cls.pod = get_pod(cls.api_client, cls.zone.id)
        cls.template = get_template(
            cls.api_client,
            cls.zone.id,
            cls.test_data["ostype"]
        )
        cls._cleanup = []

        cls.disk_offering = DiskOffering.create(
            cls.api_client,
            cls.test_data["disk_offering"]
        )
        cls._cleanup.append(cls.disk_offering)

        cls.account = Account.create(
            cls.api_client,
            cls.test_data["account"],
            domainid=cls.domain.id
        )
        cls._cleanup.append(cls.account)

        cls.service_offering = ServiceOffering.create(
            cls.api_client,
            cls.test_data["service_offering"]
        )
        cls._cleanup.append(cls.service_offering)

        cls.virtual_machine = VirtualMachine.create(
            cls.api_client,
            cls.test_data["virtual_machine"],
            accountid=cls.account.name,
            domainid=cls.account.domainid,
            serviceofferingid=cls.service_offering.id,
            templateid=cls.template.id,
            zoneid=cls.zone.id
        )
        cls._cleanup.append(cls.virtual_machine)

    @classmethod
    def tearDownClass(cls):
        super(TestAttachVolumeWithGroup, cls).tearDownClass()

    def setUp(self):
        self.api_client = self.test_client.getApiClient()
        self.cleanup = []

    def tearDown(self):
        super(TestAttachVolumeWithGroup, self).tearDown()

    @attr(tags=["advanced", "advancedns", "needle"])
    def test_attach_mixed_volumes(self):
        volume_list = []
        volume_count = 9
        for i in range(0, volume_count):
            volume_list.append([Volume.create(
                self.api_client,
                self.test_data["volume"],
                zoneid=self.zone.id,
                account=self.account.name,
                domainid=self.account.domainid,
                diskofferingid=self.disk_offering.id
            ), 0, 0])

        for i in range(0, volume_count):
            if i > 5:
                self.virtual_machine.attach_volume(
                    self.api_client,
                    volume_list[i][0],
                    volumegroup=2
                )
                volume_list[i][1] = 2
                volume_list[i][2] = i % 3
                continue
            if i > 2:
                self.virtual_machine.attach_volume(
                    self.api_client,
                    volume_list[i][0],
                    volumegroup=1
                )
                volume_list[i][1] = 1
                volume_list[i][2] = i % 3
                continue

            self.virtual_machine.attach_volume(
                self.api_client,
                volume_list[i][0]
            )
            volume_list[i][1] = 0
            volume_list[i][2] = (i % 3) + 1

        for i in range(0, volume_count):
            vol_res = Volume.list(
                self.api_client,
                id=volume_list[i][0].id
            )
            controller = "scsi{}:{}".format(volume_list[i][1], volume_list[i][2])
            if vol_res is not None:
                m = re.search(controller, vol_res[0]['chaininfo'])
                if m is not None:
                    self.assertTrue(True)
                else:
                    self.assertTrue(False, "Volume is not on the right controller")
            else:
                self.assertTrue(False, "Volume not found with id: {}".format(volume_list[i][0].id))

        for volume in volume_list:
            self.virtual_machine.detach_volume(
                self.api_client,
                volume[0]
            )
            self.cleanup.append(volume)

    @classmethod
    def tearDownClass(cls):
        super(TestAttachVolumeWithGroup, cls).tearDownClass()