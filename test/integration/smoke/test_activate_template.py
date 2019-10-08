from marvin.lib.base import (Account,
                             ServiceOffering,
                             VirtualMachine,
                             DiskOffering,
                             Template,
                             Volume,
                             Zone)
from marvin.lib.common import (get_domain,
                               get_zone,
                               get_template)
from marvin.cloudstackTestCase import cloudstackTestCase, unittest
from nose.plugins.attrib import attr
import time
from marvin.cloudstackAPI import (registerTemplate, listOsTypes, activateSystemVMTemplate, createZone)
from marvin.lib.utils import cleanup_resources

class TestActivateTemplate(cloudstackTestCase):

    @classmethod
    def setUpClass(cls):
        cls.testClient = super(TestActivateTemplate, cls).getClsTestClient()
        cls.apiclient = cls.testClient.getApiClient()
        cls.dbclient = cls.testClient.getDbConnection()
        cls._cleanup = []
        cls.templates = []

        cls.services = cls.testClient.getParsedTestDataConfig()
        cls.unsupportedHypervisor = False
        cls.hypervisor = cls.testClient.getHypervisorInfo()
        if cls.hypervisor.lower() not in ['kvm']:
            # Only testing kvm
            cls.unsupportedHypervisor = True
            return

        # Get Zone, Domain and templates
        cls.domain = get_domain(cls.apiclient)
        cls.zone = get_zone(cls.apiclient, cls.testClient.getZoneForTests())
        cls.services["mode"] = cls.zone.networktype
        cls.services["virtual_machine"]["zoneid"] = cls.zone.id
        cls.account = Account.create(
            cls.apiclient,
            cls.services["account"],
            admin=True,
            domainid=cls.domain.id
        )
        cls._cleanup.append(cls.account)
        cls.user = Account.create(
            cls.apiclient,
            cls.services["account"],
            domainid=cls.domain.id
        )
        cls._cleanup.append(cls.user)
        cls.service_offering = ServiceOffering.create(
            cls.apiclient,
            cls.services["service_offerings"]["tiny"]
        )
        cls._cleanup.append(cls.service_offering)
    
    @classmethod
    def tearDownClass(cls):
        try:
            cleanup_resources(cls.apiclient, cls._cleanup)
        except Exception as e:
            raise Exception("Warning: Exception during cleanup : %s" % e)
        return

    def setUp(self):
        # Official cloudstack system vm template
        self.test_template = registerTemplate.registerTemplateCmd()
        self.test_template.hypervisor = self.hypervisor
        self.test_template.zoneid = self.zone.id
        self.test_template.name = 'test-system-kvm-4.11.3'
        self.test_template.displaytext = 'test-system-kvm-4.11.3'
        self.test_template.url = "http://download.cloudstack.org/systemvm/4.11/systemvmtemplate-4.11.3-kvm.qcow2.bz2"
        self.test_template.format = "QCOW2"
        self.test_template.system = True
        self.test_template.ostypeid = self.getOsType("Debian GNU/Linux 9 (64-bit)")
        self.md5 = "d40bce40b2d5bb4ba73e56d1e95aeae5"

        self.activateTemplateCmd = activateSystemVMTemplate.activateSystemVMTemplateCmd()

        self.test_zone = test_zone

        return

    @attr(tags=["advanced", "smoke"], required_hardware="true")
    def test_01_activate_sytem_vm_template(self):
        """
        Test activating registered template
        """
        template = self.registerTemplate(self.test_template)
        self.download(self.apiclient, template.id)
        self.activateTemplateCmd.id = template.id
        self.activateTemplate(self.activateTemplateCmd)
        return

    def test_02_copySystemVMtemplate(self):
        # this test needs 2 zones, the template will be copied from 1 zone to the next
        pass

    def registerTemplate(self, cmd):
        temp = self.apiclient.registerTemplate(cmd)[0]
        if not temp:
            self.cleanup.append(temp)
        return temp

    def activateTemplate(self, cmd):
        response = self.apiclient.activateSystemVMTemplate(cmd)[0]
        return response
    
    def copyTemplate(self, cmd):
        response = self.apiclient.copyTemplate(cmd)
        return response

    def getOsType(self, param):
        cmd = listOsTypes.listOsTypesCmd()
        cmd.description = param
        return self.apiclient.listOsTypes(cmd)[0].id

    def download(self, apiclient, template_id, retries=12, interval=10):
        """Check if template download will finish in 1 minute"""
        while retries > -1:
            time.sleep(interval)
            template_response = Template.list(
                apiclient,
                id=template_id,
                zoneid=self.zone.id,
                templatefilter='self'
            )

            if isinstance(template_response, list):
                template = template_response[0]
                if not hasattr(template, 'status') or not template or not template.status:
                    retries = retries - 1
                    continue

                # If template is ready,
                # template.status = Download Complete
                # Downloading - x% Downloaded
                # if Failed
                # Error - Any other string
                if 'Failed' in template.status:
                    raise Exception(
                        "Failed to download template: status - %s" %
                        template.status)

                elif template.status == 'Download Complete' and template.isready:
                    return

                elif 'Downloaded' in template.status:
                    retries = retries - 1
                    continue

                elif 'Installing' not in template.status:
                    if retries >= 0:
                        retries = retries - 1
                        continue
                    raise Exception(
                        "Error in downloading template: status - %s" %
                        template.status)

            else:
                retries = retries - 1
        raise Exception("Template download failed exception.")