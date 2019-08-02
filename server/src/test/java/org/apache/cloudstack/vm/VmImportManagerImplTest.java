package org.apache.cloudstack.vm;

import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.apache.cloudstack.api.ResponseGenerator;
import org.apache.cloudstack.api.ResponseObject;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.command.admin.ingestion.ImportUnmanageInstanceCmd;
import org.apache.cloudstack.api.command.admin.ingestion.ListUnmanagedInstancesCmd;
import org.apache.cloudstack.api.response.UserVmResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.engine.orchestration.service.NetworkOrchestrationService;
import org.apache.cloudstack.engine.orchestration.service.VolumeOrchestrationService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.GetUnmanagedInstancesAnswer;
import com.cloud.agent.api.GetUnmanagedInstancesCommand;
import com.cloud.dc.ClusterVO;
import com.cloud.dc.DataCenter;
import com.cloud.dc.DataCenterVO;
import com.cloud.dc.dao.ClusterDao;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.PermissionDeniedException;
import com.cloud.host.Host;
import com.cloud.host.HostVO;
import com.cloud.host.Status;
import com.cloud.hypervisor.Hypervisor;
import com.cloud.network.Network;
import com.cloud.network.dao.NetworkDao;
import com.cloud.network.dao.NetworkVO;
import com.cloud.offering.DiskOffering;
import com.cloud.offering.ServiceOffering;
import com.cloud.resource.ResourceManager;
import com.cloud.service.ServiceOfferingVO;
import com.cloud.service.dao.ServiceOfferingDao;
import com.cloud.storage.DiskOfferingVO;
import com.cloud.storage.VMTemplateStoragePoolVO;
import com.cloud.storage.VMTemplateVO;
import com.cloud.storage.Volume;
import com.cloud.storage.dao.DiskOfferingDao;
import com.cloud.storage.dao.VMTemplateDao;
import com.cloud.storage.dao.VMTemplatePoolDao;
import com.cloud.template.VirtualMachineTemplate;
import com.cloud.user.Account;
import com.cloud.user.AccountService;
import com.cloud.user.AccountVO;
import com.cloud.user.User;
import com.cloud.user.UserVO;
import com.cloud.user.dao.UserDao;
import com.cloud.uservm.UserVm;
import com.cloud.utils.Pair;
import com.cloud.vm.DiskProfile;
import com.cloud.vm.NicProfile;
import com.cloud.vm.UserVmService;
import com.cloud.vm.UserVmVO;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.dao.VMInstanceDao;

public class VmImportManagerImplTest {

    @InjectMocks
    private VmImportService vmIngestionService = new VmImportManagerImpl();

    @Mock
    private UserVmService userVmService;
    @Mock
    private ClusterDao clusterDao;
    @Mock
    private ResourceManager resourceManager;
    @Mock
    private VMTemplatePoolDao templatePoolDao;
    @Mock
    private AgentManager agentManager;
    @Mock
    private AccountService accountService;
    @Mock
    private UserDao userDao;
    @Mock
    private DataCenterDao dataCenterDao;
    @Mock
    private VMTemplateDao templateDao;
    @Mock
    private VMInstanceDao vmDao;
    @Mock
    private ServiceOfferingDao serviceOfferingDao;
    @Mock
    private DiskOfferingDao diskOfferingDao;
    @Mock
    private NetworkDao networkDao;
    @Mock
    private NetworkOrchestrationService networkOrchestrationService;
    @Mock
    private VolumeOrchestrationService volumeManager;
    @Mock
    public ResponseGenerator responseGenerator;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        AccountVO account = new AccountVO("admin", 1L, "", Account.ACCOUNT_TYPE_ADMIN, "uuid");
        UserVO user = new UserVO(1, "adminuser", "password", "firstname", "lastName", "email", "timezone", UUID.randomUUID().toString(), User.Source.UNKNOWN);
        CallContext.register(user, account);

        UnmanagedInstance instance = new UnmanagedInstance();
        instance.setName("TestInstance");
        instance.setCpuCores(2);
        instance.setCpuCoresPerSocket(1);
        instance.setCpuSpeed(100);
        instance.setMemory(1024);
        instance.setOperatingSystem("CentOS 7");
        List<UnmanagedInstance.Disk> instanceDisks = new ArrayList<>();
        UnmanagedInstance.Disk instanceDisk = new UnmanagedInstance.Disk();
        instanceDisk.setDiskId("1000-1");
        instanceDisk.setLabel("DiskLabel");
        instanceDisk.setImagePath("[SomeID] path/path.vmdk");
        instanceDisk.setCapacity(5242880L);
        instanceDisks.add(instanceDisk);
        instance.setDisks(instanceDisks);
        List<UnmanagedInstance.Nic> instanceNics = new ArrayList<>();
        UnmanagedInstance.Nic instanceNic = new UnmanagedInstance.Nic();
        instanceNic.setNicId("NIC 1");
        instanceNic.setAdapterType("VirtualE1000E");
        instanceNic.setMacAddress("02:00:2e:0f:00:02");
        instanceNic.setVlan(1024);
        instanceNics.add(instanceNic);
        instance.setNics(instanceNics);
        instance.setPowerState("POWERED_ON");

        ClusterVO cluster = new ClusterVO(1, 1, "Cluster");
        cluster.setHypervisorType(Hypervisor.HypervisorType.VMware.toString());
        when(clusterDao.findById(Mockito.anyLong())).thenReturn(cluster);

        List<HostVO> hosts = new ArrayList<>();
        HostVO hostVO = Mockito.mock(HostVO.class);
        hosts.add(hostVO);
        when(resourceManager.listHostsInClusterByStatus(Mockito.anyLong(), Mockito.any(Status.class))).thenReturn(hosts);
        List<VMTemplateStoragePoolVO> templates = new ArrayList<>();
        when(templatePoolDao.listAll()).thenReturn(templates);
        List<VMInstanceVO> vms = new ArrayList<>();
        when(vmDao.listByHostId(Mockito.anyLong())).thenReturn(vms);
        when(vmDao.listByLastHostId(Mockito.anyLong())).thenReturn(vms);
        GetUnmanagedInstancesCommand cmd = Mockito.mock(GetUnmanagedInstancesCommand.class);
        HashMap<String, UnmanagedInstance> map = new HashMap<>();
        map.put(instance.getName(), instance);
        Answer answer = new GetUnmanagedInstancesAnswer(cmd, "", map);
        when(agentManager.easySend(Mockito.anyLong(), Mockito.any(GetUnmanagedInstancesCommand.class))).thenReturn(answer);

        when(dataCenterDao.findById(Mockito.anyLong())).thenReturn(Mockito.mock(DataCenterVO.class));
        when(accountService.getActiveAccountById(Mockito.anyLong())).thenReturn(Mockito.mock(Account.class));
        List<UserVO> users = new ArrayList<>();
        users.add(Mockito.mock(UserVO.class));
        when(userDao.listByAccount(Mockito.anyLong())).thenReturn(users);
        when(templateDao.findById(Mockito.anyLong())).thenReturn(Mockito.mock(VMTemplateVO.class));
        when(serviceOfferingDao.findById(Mockito.anyLong())).thenReturn(Mockito.mock(ServiceOfferingVO.class));
        DiskOfferingVO diskOfferingVO = Mockito.mock(DiskOfferingVO.class);
        when(diskOfferingVO.isCustomized()).thenReturn(false);
        when(diskOfferingDao.findById(Mockito.anyLong())).thenReturn(diskOfferingVO);
        UserVmVO userVm = Mockito.mock(UserVmVO.class);
        userVm.setInstanceName(instance.getName());
        userVm.setHostName(instance.getName());
        when(userVmService.importVM(Mockito.any(DataCenter.class), Mockito.any(Host.class), Mockito.any(VirtualMachineTemplate.class), Mockito.anyString(), Mockito.anyString(),
                Mockito.any(Account.class), Mockito.anyString(), Mockito.any(Account.class), Mockito.anyBoolean(), Mockito.anyString(),
                Mockito.anyLong(), Mockito.anyLong(), Mockito.any(ServiceOffering.class), Mockito.any(DiskOffering.class), Mockito.anyString(),
                Mockito.anyString(), Mockito.any(Hypervisor.HypervisorType.class), Mockito.anyMap(), Mockito.any(VirtualMachine.PowerState.class))).thenReturn(userVm);
        NetworkVO networkVO = Mockito.mock(NetworkVO.class);
        networkVO.setBroadcastUri(URI.create(String.format("vlan://%d", instanceNic.getVlan())));
        when(networkDao.findById(Mockito.anyLong())).thenReturn(networkVO);
        NicProfile profile = Mockito.mock(NicProfile.class);
        Integer deviceId = 100;
        Pair<NicProfile, Integer> pair = new Pair<NicProfile, Integer>(profile, deviceId);
        when(networkOrchestrationService.importNic(Mockito.anyString(), Mockito.anyInt(), Mockito.any(Network.class), Mockito.anyBoolean(), Mockito.any(VirtualMachine.class), Mockito.anyString())).thenReturn(pair);
        when(volumeManager.importVolume(Mockito.any(Volume.Type.class), Mockito.anyString(), Mockito.any(DiskOffering.class), Mockito.anyLong(),
                Mockito.anyLong(), Mockito.anyLong(), Mockito.any(VirtualMachine.class), Mockito.any(VirtualMachineTemplate.class),
                Mockito.any(Account.class), Mockito.anyLong(), Mockito.anyLong(), Mockito.anyString(), Mockito.anyString())).thenReturn(Mockito.mock(DiskProfile.class));
        List<UserVmResponse> userVmResponses = new ArrayList<>();
        UserVmResponse userVmResponse = new UserVmResponse();
        userVmResponse.setInstanceName(instance.getName());
        userVmResponses.add(userVmResponse);
        when(responseGenerator.createUserVmResponse(Mockito.any(ResponseObject.ResponseView.class), Mockito.anyString(), Mockito.any(UserVm.class))).thenReturn(userVmResponses);
    }

    @After
    public void tearDown() {
        CallContext.unregister();
    }

    @Test
    public void listUnmanagedInstancesTest() {
        ListUnmanagedInstancesCmd cmd = Mockito.mock(ListUnmanagedInstancesCmd.class);
        vmIngestionService.listUnmanagedInstances(cmd);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void listUnmanagedInstancesInvalidHypervisorTest() {
        ListUnmanagedInstancesCmd cmd = Mockito.mock(ListUnmanagedInstancesCmd.class);
        ClusterVO cluster = new ClusterVO(1, 1, "Cluster");
        cluster.setHypervisorType(Hypervisor.HypervisorType.KVM.toString());
        when(clusterDao.findById(Mockito.anyLong())).thenReturn(cluster);
        vmIngestionService.listUnmanagedInstances(cmd);
    }

    @Test(expected = PermissionDeniedException.class)
    public void listUnmanagedInstancesInvalidCallerTest() {
        CallContext.unregister();
        AccountVO account = new AccountVO("user", 1L, "", Account.ACCOUNT_TYPE_NORMAL, "uuid");
        UserVO user = new UserVO(1, "testuser", "password", "firstname", "lastName", "email", "timezone", UUID.randomUUID().toString(), User.Source.UNKNOWN);
        CallContext.register(user, account);
        ListUnmanagedInstancesCmd cmd = Mockito.mock(ListUnmanagedInstancesCmd.class);
        vmIngestionService.listUnmanagedInstances(cmd);
    }

    @Test
    public void importUnmanagedInstanceTest() {
        ImportUnmanageInstanceCmd importUnmanageInstanceCmd = Mockito.mock(ImportUnmanageInstanceCmd.class);
        when(importUnmanageInstanceCmd.getName()).thenReturn("TestInstance");
        vmIngestionService.importUnmanagedInstance(importUnmanageInstanceCmd);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void importUnmanagedInstanceInvalidHostnameTest() {
        ImportUnmanageInstanceCmd importUnmanageInstanceCmd = Mockito.mock(ImportUnmanageInstanceCmd.class);
        when(importUnmanageInstanceCmd.getName()).thenReturn("TestInstance");
        when(importUnmanageInstanceCmd.getName()).thenReturn("some name");
        vmIngestionService.importUnmanagedInstance(importUnmanageInstanceCmd);
    }

    @Test(expected = ServerApiException.class)
    public void importUnmanagedInstanceMissingInstanceTest() {
        ImportUnmanageInstanceCmd importUnmanageInstanceCmd = Mockito.mock(ImportUnmanageInstanceCmd.class);
        when(importUnmanageInstanceCmd.getName()).thenReturn("SomeInstance");
        vmIngestionService.importUnmanagedInstance(importUnmanageInstanceCmd);
    }
}