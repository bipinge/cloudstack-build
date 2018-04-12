from marvin.cloudstackAPI import createSSHKeyPair, deleteSSHKeyPair


class MySSHKeyPair:
    """Manage SSH Key pairs"""

    def __init__(self, items):
        self.__dict__.update(items)

    @classmethod
    def create(cls, apiclient, name=None, account=None,
               domainid=None, projectid=None):
        """Creates SSH keypair"""
        cmd = createSSHKeyPair.createSSHKeyPairCmd()
        cmd.name = name
        if account is not None:
            cmd.account = account
        if domainid is not None:
            cmd.domainid = domainid
        if projectid is not None:
            cmd.projectid = projectid
        return MySSHKeyPair(apiclient.createSSHKeyPair(cmd).__dict__)

    def delete(self, apiclient):
        """Delete SSH key pair"""
        cmd = deleteSSHKeyPair.deleteSSHKeyPairCmd()
        cmd.name = self.name
        cmd.account = self.account
        cmd.domainid = self.domainid
        apiclient.deleteSSHKeyPair(cmd)