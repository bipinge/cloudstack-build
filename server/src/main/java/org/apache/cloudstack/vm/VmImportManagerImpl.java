// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.apache.cloudstack.vm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.ResponseGenerator;
import org.apache.cloudstack.api.ResponseObject;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.command.admin.vm.ImportUnmanagedInstanceCmd;
import org.apache.cloudstack.api.command.admin.vm.ListUnmanagedInstancesCmd;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.NicResponse;
import org.apache.cloudstack.api.response.UnmanagedInstanceDiskResponse;
import org.apache.cloudstack.api.response.UnmanagedInstanceResponse;
import org.apache.cloudstack.api.response.UserVmResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.engine.orchestration.service.NetworkOrchestrationService;
import org.apache.cloudstack.engine.orchestration.service.VolumeOrchestrationService;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import org.apache.cloudstack.storage.datastore.db.PrimaryDataStoreDao;
import org.apache.cloudstack.storage.datastore.db.StoragePoolVO;
import org.apache.cloudstack.utils.volume.VirtualMachineDiskInfo;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.log4j.Logger;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.GetUnmanagedInstancesAnswer;
import com.cloud.agent.api.GetUnmanagedInstancesCommand;
import com.cloud.capacity.CapacityManager;
import com.cloud.configuration.Config;
import com.cloud.configuration.Resource;
import com.cloud.dc.DataCenter;
import com.cloud.dc.dao.ClusterDao;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.deploy.DataCenterDeployment;
import com.cloud.deploy.DeployDestination;
import com.cloud.deploy.DeploymentPlanner;
import com.cloud.deploy.DeploymentPlanningManager;
import com.cloud.event.EventTypes;
import com.cloud.event.UsageEventUtils;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.InsufficientVirtualNetworkCapacityException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.PermissionDeniedException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.host.Host;
import com.cloud.host.HostVO;
import com.cloud.host.Status;
import com.cloud.host.dao.HostDao;
import com.cloud.hypervisor.Hypervisor;
import com.cloud.network.Network;
import com.cloud.network.NetworkModel;
import com.cloud.network.Networks;
import com.cloud.network.dao.NetworkDao;
import com.cloud.network.dao.NetworkVO;
import com.cloud.offering.DiskOffering;
import com.cloud.offering.ServiceOffering;
import com.cloud.org.Cluster;
import com.cloud.resource.ResourceManager;
import com.cloud.serializer.GsonHelper;
import com.cloud.server.ManagementService;
import com.cloud.service.ServiceOfferingVO;
import com.cloud.service.dao.ServiceOfferingDao;
import com.cloud.storage.Storage;
import com.cloud.storage.StoragePool;
import com.cloud.storage.VMTemplateStoragePoolVO;
import com.cloud.storage.Volume;
import com.cloud.storage.VolumeApiService;
import com.cloud.storage.VolumeVO;
import com.cloud.storage.dao.DiskOfferingDao;
import com.cloud.storage.dao.VMTemplateDao;
import com.cloud.storage.dao.VMTemplatePoolDao;
import com.cloud.storage.dao.VolumeDao;
import com.cloud.template.VirtualMachineTemplate;
import com.cloud.user.Account;
import com.cloud.user.AccountService;
import com.cloud.user.ResourceLimitService;
import com.cloud.user.UserVO;
import com.cloud.user.dao.UserDao;
import com.cloud.uservm.UserVm;
import com.cloud.utils.Pair;
import com.cloud.utils.net.NetUtils;
import com.cloud.vm.DiskProfile;
import com.cloud.vm.NicProfile;
import com.cloud.vm.UserVmManager;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachineManager;
import com.cloud.vm.VirtualMachineProfile;
import com.cloud.vm.VirtualMachineProfileImpl;
import com.cloud.vm.VmDetailConstants;
import com.cloud.vm.dao.NicDao;
import com.cloud.vm.dao.VMInstanceDao;
import com.google.common.base.Strings;
import com.google.gson.Gson;

public class VmImportManagerImpl implements VmImportService {
    private static final Logger LOGGER = Logger.getLogger(VmImportManagerImpl.class);

    @Inject
    private AgentManager agentManager;
    @Inject
    private DataCenterDao dataCenterDao;
    @Inject
    private ClusterDao clusterDao;
    @Inject
    private HostDao hostDao;
    @Inject
    private AccountService accountService;
    @Inject
    private UserDao userDao;
    @Inject
    private VMTemplateDao templateDao;
    @Inject
    private VMTemplatePoolDao templatePoolDao;
    @Inject
    private ServiceOfferingDao serviceOfferingDao;
    @Inject
    private DiskOfferingDao diskOfferingDao;
    @Inject
    private ResourceManager resourceManager;
    @Inject
    private ResourceLimitService resourceLimitService;
    @Inject
    private UserVmManager userVmManager;
    @Inject
    private ResponseGenerator responseGenerator;
    @Inject
    private VolumeOrchestrationService volumeManager;
    @Inject
    private VolumeDao volumeDao;
    @Inject
    private PrimaryDataStoreDao primaryDataStoreDao;
    @Inject
    private NetworkDao networkDao;
    @Inject
    private NetworkOrchestrationService networkOrchestrationService;
    @Inject
    private VMInstanceDao vmDao;
    @Inject
    private CapacityManager capacityManager;
    @Inject
    private VolumeApiService volumeApiService;
    @Inject
    private DeploymentPlanningManager deploymentPlanningManager;
    @Inject
    private VirtualMachineManager virtualMachineManager;
    @Inject
    private ManagementService managementService;
    @Inject
    private NicDao nicDao;
    @Inject
    private NetworkModel networkModel;
    @Inject
    private ConfigurationDao configurationDao;

    protected Gson gson;

    public VmImportManagerImpl() {
        gson = GsonHelper.getGsonLogger();
    }

    private UnmanagedInstanceResponse createUnmanagedInstanceResponse(UnmanagedInstance instance, Cluster cluster, Host host) {
        UnmanagedInstanceResponse response = new UnmanagedInstanceResponse();
        response.setName(instance.getName());
        if (cluster != null) {
            response.setClusterId(cluster.getUuid());
        }
        if (host != null) {
            response.setHostId(host.getUuid());
        }
        response.setPowerState(instance.getPowerState().toString());
        response.setCpuCores(instance.getCpuCores());
        response.setCpuSpeed(instance.getCpuSpeed());
        response.setCpuCoresPerSocket(instance.getCpuCoresPerSocket());
        response.setMemory(instance.getMemory());
        response.setOperatingSystem(instance.getOperatingSystem());
        response.setObjectName(UnmanagedInstance.class.getSimpleName().toLowerCase());

        if (instance.getDisks() != null) {
            for (UnmanagedInstance.Disk disk : instance.getDisks()) {
                UnmanagedInstanceDiskResponse diskResponse = new UnmanagedInstanceDiskResponse();
                diskResponse.setDiskId(disk.getDiskId());
                if (!Strings.isNullOrEmpty(disk.getLabel())) {
                    diskResponse.setLabel(disk.getLabel());
                }
                diskResponse.setCapacity(disk.getCapacity());
                diskResponse.setController(disk.getController());
                diskResponse.setControllerUnit(disk.getControllerUnit());
                diskResponse.setPosition(disk.getPosition());
                diskResponse.setImagePath(disk.getImagePath());
                diskResponse.setDatastoreName(disk.getDatastoreName());
                diskResponse.setDatastoreHost(disk.getDatastoreHost());
                diskResponse.setDatastorePath(disk.getDatastorePath());
                diskResponse.setDatastoreType(disk.getDatastoreType());
                response.addDisk(diskResponse);
            }
        }

        if (instance.getNics() != null) {
            for (UnmanagedInstance.Nic nic : instance.getNics()) {
                NicResponse nicResponse = new NicResponse();
                nicResponse.setId(nic.getNicId());
                nicResponse.setNetworkName(nic.getNetwork());
                nicResponse.setMacAddress(nic.getMacAddress());
                if (!Strings.isNullOrEmpty(nic.getAdapterType())) {
                    nicResponse.setAdapterType(nic.getAdapterType());
                }
                if (!Strings.isNullOrEmpty(nic.getIpAddress())) {
                    nicResponse.setIpaddress(nic.getIpAddress());
                }
                nicResponse.setVlanId(nic.getVlan());
                response.addNic(nicResponse);
            }
        }
        return response;
    }

    private List<String> getAdditionalNameFilters(Cluster cluster) {
        List<String> additionalNameFilter = new ArrayList<>();
        if (cluster == null) {
            return additionalNameFilter;
        }
        if (cluster.getHypervisorType() == Hypervisor.HypervisorType.VMware) {
            // VMWare considers some templates as VM and they are not filtered by VirtualMachineMO.isTemplate()
            List<VMTemplateStoragePoolVO> templates = templatePoolDao.listAll();
            for (VMTemplateStoragePoolVO template : templates) {
                additionalNameFilter.add(template.getInstallPath());
            }

            // VMWare considers some removed volumes as VM
            List<VolumeVO> volumes = volumeDao.findIncludingRemovedByZone(cluster.getDataCenterId());
            for (VolumeVO volumeVO : volumes) {
                if (volumeVO.getRemoved() == null) {
                    continue;
                }
                if (Strings.isNullOrEmpty(volumeVO.getChainInfo())) {
                    continue;
                }
                List<String> volumeFileNames = new ArrayList<>();
                try {
                    VirtualMachineDiskInfo diskInfo = gson.fromJson(volumeVO.getChainInfo(), VirtualMachineDiskInfo.class);
                    String[] files = diskInfo.getDiskChain();
                    if (files.length == 1) {
                        continue;
                    }
                    boolean firstFile = true;
                    for (final String file : files) {
                        if (firstFile) {
                            firstFile = false;
                            continue;
                        }
                        String path = file;
                        String[] split = path.split(" ");
                        path = split[split.length - 1];
                        split = path.split("/");
                        ;
                        path = split[split.length - 1];
                        split = path.split("\\.");
                        path = split[0];
                        if (!Strings.isNullOrEmpty(path)) {
                            if (!additionalNameFilter.contains(path)) {
                                volumeFileNames.add(path);
                            }
                            if (path.contains("-")) {
                                split = path.split("-");
                                path = split[0];
                                if (!Strings.isNullOrEmpty(path) && !path.equals("ROOT") && !additionalNameFilter.contains(path)) {
                                    volumeFileNames.add(path);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                }
                if (!volumeFileNames.isEmpty()) {
                    additionalNameFilter.addAll(volumeFileNames);
                }
            }
        }
        return additionalNameFilter;
    }

    private List<String> getHostManagedVms(Host host) {
        List<String> managedVms = new ArrayList<>();
        List<VMInstanceVO> instances = vmDao.listByHostId(host.getId());
        for (VMInstanceVO instance : instances) {
            managedVms.add(instance.getInstanceName());
        }
        instances = vmDao.listByLastHostId(host.getId());
        for (VMInstanceVO instance : instances) {
            managedVms.add(instance.getInstanceName());
        }
        return managedVms;
    }

    private boolean hostSupportsServiceOffering(HostVO host, ServiceOffering serviceOffering) {
        if (host == null) {
            return false;
        }
        if (serviceOffering == null) {
            return false;
        }
        if (Strings.isNullOrEmpty(serviceOffering.getHostTag())) {
            return true;
        }
        hostDao.loadHostTags(host);
        return host.getHostTags() != null && host.getHostTags().contains(serviceOffering.getHostTag());
    }

    private boolean storagePoolSupportsDiskOffering(StoragePool pool, DiskOffering diskOffering) {
        if (pool == null) {
            return false;
        }
        if (diskOffering == null) {
            return false;
        }
        return volumeApiService.doesTargetStorageSupportDiskOffering(pool, diskOffering.getTags());
    }

    private boolean storagePoolSupportsServiceOffering(StoragePool pool, ServiceOffering serviceOffering) {
        if (pool == null) {
            return false;
        }
        if (serviceOffering == null) {
            return false;
        }
        return volumeApiService.doesTargetStorageSupportDiskOffering(pool, serviceOffering.getTags());
    }

    private ServiceOfferingVO getUnmanagedInstanceServiceOffering(final UnmanagedInstance instance, ServiceOfferingVO serviceOffering, final Account owner, final DataCenter zone, final Map<String, String> details)
            throws ServerApiException, PermissionDeniedException, ResourceAllocationException {
        if (instance == null) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, String.format("VM is not valid!"));
        }
        if (serviceOffering == null) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, String.format("Service offering is not valid!", instance.getName()));
        }
        accountService.checkAccess(owner, serviceOffering, zone);
        final Integer cpu = instance.getCpuCores();
        final Integer memory = instance.getMemory();
        Integer cpuSpeed = instance.getCpuSpeed();
        if (cpu == null || cpu == 0) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, String.format("CPU cores for VM not valid!", instance.getName()));
        }
        if (memory == null || memory == 0) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, String.format("Memory for VM not valid!", instance.getName()));
        }
        if (serviceOffering.isDynamic()) {
            if (cpuSpeed == null || cpuSpeed == 0) {
                try {
                    cpuSpeed = Integer.parseInt(details.get(VmDetailConstants.CPU_SPEED));
                } catch (Exception e) {
                    cpuSpeed = 0;
                }
            }
            Map<String, String> parameters = new HashMap<>();
            parameters.put(VmDetailConstants.CPU_NUMBER, String.valueOf(cpu));
            parameters.put(VmDetailConstants.MEMORY, String.valueOf(memory));
            if (serviceOffering.getSpeed() == null && cpuSpeed > 0) {
                parameters.put(VmDetailConstants.CPU_SPEED, String.valueOf(cpuSpeed));
            }
            serviceOffering.setDynamicFlag(true);
            userVmManager.validateCustomParameters(serviceOffering, parameters);
            serviceOffering = serviceOfferingDao.getComputeOffering(serviceOffering, parameters);
        } else {
            if (!cpu.equals(serviceOffering.getCpu()) && !instance.getPowerState().equals(UnmanagedInstance.PowerState.PowerOff)) {
                throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, String.format("Service offering (%s) %d CPU cores does not matches VM CPU cores %d  and VM is not in powered off state (Power state: %s)!", serviceOffering.getUuid(), serviceOffering.getCpu(), cpu, instance.getPowerState()));
            }
            if (!memory.equals(serviceOffering.getRamSize()) && !instance.getPowerState().equals(UnmanagedInstance.PowerState.PowerOff)) {
                throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, String.format("Service offering (%s) %dMB memory does not matches VM memory %dMB and VM is not in powered off state (Power state: %s)!", serviceOffering.getUuid(), serviceOffering.getRamSize(), memory, instance.getPowerState()));
            }
            if (cpuSpeed != null && cpuSpeed > 0 && !cpuSpeed.equals(serviceOffering.getSpeed()) && !instance.getPowerState().equals(UnmanagedInstance.PowerState.PowerOff)) {
                throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, String.format("Service offering (%s) %dMHz CPU speed does not matches VM CPU speed %dMHz and VM is not in powered off state (Power state: %s)!", serviceOffering.getUuid(), serviceOffering.getSpeed(), cpuSpeed, instance.getPowerState()));
            }
        }
        resourceLimitService.checkResourceLimit(owner, Resource.ResourceType.cpu, new Long(serviceOffering.getCpu()));
        resourceLimitService.checkResourceLimit(owner, Resource.ResourceType.memory, new Long(serviceOffering.getRamSize()));
        return serviceOffering;
    }

    private Map<String, Network.IpAddresses> getNicIpAddresses(final List<UnmanagedInstance.Nic> nics, final Map<String, Network.IpAddresses> callerNicIpAddressMap) {
        Map<String, Network.IpAddresses> nicIpAddresses = new HashMap<>();
        for (UnmanagedInstance.Nic nic : nics) {
            Network.IpAddresses ipAddresses = null;
            if (MapUtils.isNotEmpty(callerNicIpAddressMap) && callerNicIpAddressMap.containsKey(nic.getNicId())) {
                ipAddresses = callerNicIpAddressMap.get(nic.getNicId());
            }
            if (!Strings.isNullOrEmpty(nic.getIpAddress()) &&
                    (ipAddresses == null || Strings.isNullOrEmpty(ipAddresses.getIp4Address()))) {
                if (ipAddresses == null) {
                    ipAddresses = new Network.IpAddresses(null, null);
                }
                ipAddresses.setIp4Address(nic.getIpAddress());
            }
            if (ipAddresses != null) {
                nicIpAddresses.put(nic.getNicId(), ipAddresses);
            }
        }
        return nicIpAddresses;
    }

    private StoragePool getStoragePool(final UnmanagedInstance.Disk disk, final DataCenter zone, final Cluster cluster) {
        StoragePool storagePool = null;
        if (disk==null) {
            return null;
        }
        final String dsHost = disk.getDatastoreHost();
        final String dsPath = disk.getDatastorePath();
        final String dsType = disk.getDatastoreType();
        final String dsName = disk.getDatastoreName();
        if (dsType.equals("VMFS")) {
            List<StoragePoolVO> pools = primaryDataStoreDao.listPoolsByCluster(cluster.getId());
            for (StoragePool pool : pools) {
                if (pool.getPoolType() != Storage.StoragePoolType.VMFS) {
                    continue;
                }
                if (pool.getPath().endsWith(dsName)) {
                    storagePool = pool;
                    break;
                }
            }
        } else {
            List<StoragePoolVO> pools = primaryDataStoreDao.listPoolByHostPath(dsHost, dsPath);
            for (StoragePool pool : pools) {
                if (pool.getDataCenterId() == zone.getId() &&
                        pool.getClusterId() == cluster.getId()) {
                    storagePool = pool;
                    break;
                }
            }
        }
        return storagePool;
    }

    private void checkUnmanagedDiskAndOfferingForImport(UnmanagedInstance.Disk disk, DiskOffering diskOffering, final Account owner, final DataCenter zone, final Cluster cluster, final boolean migrateAllowed)
            throws ServerApiException, PermissionDeniedException, ResourceAllocationException {
        if (diskOffering == null) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, String.format("Disk offering for disk ID: %s not found during VM import!", disk.getDiskId()));
        }
        accountService.checkAccess(owner, diskOffering, zone);
        resourceLimitService.checkResourceLimit(owner, Resource.ResourceType.volume);
        if (disk.getCapacity() == null || disk.getCapacity() == 0) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, String.format("Size of disk(ID: %s) is found invalid during VM import!", disk.getDiskId()));
        }
        if (!diskOffering.isCustomized() && diskOffering.getDiskSize() == 0) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, String.format("Size of fixed disk offering(ID: %s) is found invalid during VM import!", diskOffering.getUuid()));
        }
        if (!diskOffering.isCustomized() && diskOffering.getDiskSize() < disk.getCapacity()) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, String.format("Size of disk offering(ID: %s) %dGB is found less than the size of disk(ID: %s) %dGB during VM import!", diskOffering.getUuid(), (diskOffering.getDiskSize() / Resource.ResourceType.bytesToGiB), disk.getDiskId(), (disk.getCapacity() / (Resource.ResourceType.bytesToGiB))));
        }
        StoragePool storagePool = getStoragePool(disk, zone, cluster);
        if (storagePool == null) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, String.format("Storage pool for disk ID: %s with datastore: %s not found in zone ID: %s, cluster ID: %s!", disk.getDiskId(), disk.getDatastoreName(), zone.getUuid(), cluster.getUuid()));
        }
        if (!migrateAllowed && !storagePoolSupportsDiskOffering(storagePool, diskOffering)) {
            throw new InvalidParameterValueException(String.format("Disk offering: %s is not compatible with storage pool: %s of unmanaged disk: %s", diskOffering.getUuid(), storagePool.getUuid(), disk.getDiskId()));
        }
    }

    private void checkUnmanagedDiskAndOfferingForImport(List<UnmanagedInstance.Disk> disks, final Map<String, Long> diskOfferingMap, final Account owner, final DataCenter zone, final Cluster cluster, final boolean migrateAllowed)
            throws ServerApiException, PermissionDeniedException, ResourceAllocationException {
        for (UnmanagedInstance.Disk disk : disks) {
            if (!diskOfferingMap.containsKey(disk.getDiskId())) {
                throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, String.format("Disk offering for disk ID: %s not found during VM import!", disk.getDiskId()));
            }
            checkUnmanagedDiskAndOfferingForImport(disk, diskOfferingDao.findById(diskOfferingMap.get(disk.getDiskId())), owner, zone, cluster, migrateAllowed);
        }
    }

    private void checkUnmanagedNicAndNetworkForImport(UnmanagedInstance.Nic nic, Network network, final Network.IpAddresses ipAddresses, final DataCenter zone, final String hostName, final Account owner) throws ServerApiException {
        if (network == null) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, String.format("Network for nic ID: %s not found during VM import!", nic.getNicId()));
        }
        if (network.getDataCenterId() != zone.getId()) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, String.format("Network(ID: %s) for nic(ID: %s) belongs to a different zone than VM to be imported!", network.getUuid(), nic.getNicId()));
        }
        networkModel.checkNetworkPermissions(owner, network);
        if (nic.getVlan() != null && (Strings.isNullOrEmpty(network.getBroadcastUri().toString()) || !network.getBroadcastUri().toString().equals(String.format("vlan://%d", nic.getVlan())))) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, String.format("VLAN of network(ID: %s) %s is found different from the VLAN of nic(ID: %s) vlan://%d during VM import!", network.getUuid(), network.getBroadcastUri().toString(), nic.getNicId(), nic.getVlan()));
        }
        if (!network.getGuestType().equals(Network.GuestType.L2)) {
            if (ipAddresses == null) { // No IP address was provided by API and NIC details don't have one
                throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, String.format("NIC(ID: %s) needs a valid IP address for it to be associated with network(ID: %s)! %s parameter of API can be used for this.", nic.getNicId(), network.getUuid(), ApiConstants.NIC_IP_ADDRESS_LIST));
            }
            if (Strings.isNullOrEmpty(ipAddresses.getIp4Address()) || Strings.isNullOrEmpty(ipAddresses.getIp6Address())) {
                networkModel.checkRequestedIpAddresses(network.getId(), ipAddresses); // This only checks ipv6
            }
            if (Strings.isNullOrEmpty(ipAddresses.getIp4Address())) {
                Set<Long> ips = networkModel.getAvailableIps(network, ipAddresses.getIp4Address());
                if (CollectionUtils.isEmpty(ips)) {
                    throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, String.format("IP address %s for NIC(ID: %s) is not available in network(ID: %s)!", nic.getNicId(), network.getUuid(), ApiConstants.NIC_IP_ADDRESS_LIST));
                }
            }
        }
        // Check for duplicate hostname in network, get all vms hostNames in the network
        List<String> hostNames = vmDao.listDistinctHostNames(network.getId());
        if (CollectionUtils.isNotEmpty(hostNames) && hostNames.contains(hostName)) {
            throw new InvalidParameterValueException("The vm with hostName " + hostName + " already exists in the network domain: " + network.getNetworkDomain() + "; network="
                    + network);
        }
    }

    private Map<String, Long> getUnmanagedNicNetworkMap(List<UnmanagedInstance.Nic> nics, final Map<String, Long> callerNicNetworkMap, final Map<String, Network.IpAddresses> callerNicIpAddressMap, final DataCenter zone, final String hostName, final Account owner) throws ServerApiException {
        Map<String, Long> nicNetworkMap = new HashMap<>();
        for (UnmanagedInstance.Nic nic : nics) {
            Network network = null;
            Network.IpAddresses ipAddresses = null;
            if (MapUtils.isNotEmpty(callerNicIpAddressMap) && callerNicIpAddressMap.containsKey(nic.getNicId())) {
                ipAddresses = callerNicIpAddressMap.get(nic.getNicId());
            }
            if (!callerNicNetworkMap.containsKey(nic.getNicId())) {
                if (nic.getVlan() != null && nic.getVlan() != 0) {
                    // Find a suitable network
                    List<NetworkVO> networks = networkDao.listByZone(zone.getId());
                    for (NetworkVO networkVO : networks) {
                        if (networkVO.getTrafficType() == Networks.TrafficType.None || Networks.TrafficType.isSystemNetwork(networkVO.getTrafficType())) {
                            continue;
                        }
                        try {
                            checkUnmanagedNicAndNetworkForImport(nic, networkVO, ipAddresses, zone, hostName, owner);
                            network = networkVO;
                            break;
                        } catch (Exception e) {
                        }
                    }
                }
            } else {
                network = networkDao.findById(callerNicNetworkMap.get(nic.getNicId()));
                checkUnmanagedNicAndNetworkForImport(nic, network, ipAddresses, zone, hostName, owner);
            }
            if (network == null) {
                throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, String.format("Suitable network for nic(ID: %s) not found during VM import!", nic.getNicId()));
            }
            nicNetworkMap.put(nic.getNicId(), network.getId());
        }
        return nicNetworkMap;
    }

    private Pair<DiskProfile, StoragePool> importDisk(UnmanagedInstance.Disk disk, VirtualMachine vm, Cluster cluster, DiskOffering diskOffering,
                                                      Volume.Type type, String name, Long diskSize, VirtualMachineTemplate template,
                                                      Account owner, Long deviceId, boolean migrateAllowed) {
        VirtualMachineDiskInfo diskInfo = new VirtualMachineDiskInfo();
        diskInfo.setDiskDeviceBusName(String.format("%s%d:%d", disk.getController(), disk.getControllerUnit(), disk.getPosition()));
        diskInfo.setDiskChain(new String[]{disk.getImagePath()});
        final String imagePath = disk.getImagePath();
        final DataCenter zone = dataCenterDao.findById(vm.getDataCenterId());
        StoragePool storagePool = getStoragePool(disk, zone, cluster);
        if (storagePool == null) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, String.format("Storage pool for disk ID: %s with datastore: %s not found in zone ID: %s, cluster ID: %s!", disk.getDiskId(), disk.getDatastoreName(), zone.getUuid(), cluster.getUuid()));
        }
        DiskProfile profile = volumeManager.importVolume(type, name, diskOffering, diskSize,
                diskOffering.getMinIops(), diskOffering.getMaxIops(), vm, template, owner, deviceId, storagePool.getId(), imagePath, gson.toJson(diskInfo));

        return new Pair<DiskProfile, StoragePool>(profile, storagePool);
    }

    private NicProfile importNic(UnmanagedInstance.Nic nic, VirtualMachine vm, Network network, Network.IpAddresses ipAddresses, boolean isDefaultNic) throws InsufficientVirtualNetworkCapacityException {
        Pair<NicProfile, Integer> result = networkOrchestrationService.importNic(nic.getMacAddress(), 0, network, isDefaultNic, vm, ipAddresses);
        if (result == null) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, String.format("NIC ID: %s import failed!", nic.getNicId()));
        }
        return result.first();
    }

    private void cleanupFailedImportVM(final UserVm userVm) {
        if (userVm == null) {
            return;
        }
        // Remove all volumes
        volumeDao.deleteVolumesByInstance(userVm.getId());
        // Remove all nics
        nicDao.removeNicsForInstance(userVm.getId());
        // Remove vm
        vmDao.remove(userVm.getId());
    }

    private UserVm migrateImportedVM(HostVO sourceHost, VirtualMachineTemplate template, ServiceOfferingVO serviceOffering, UserVm userVm, final Account owner, List<Pair<DiskProfile, StoragePool>> diskProfileStoragePoolList) {
        UserVm vm = userVm;
        if (vm == null) {
            LOGGER.error(String.format("Failed to check migrations need during VM import!"));
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, String.format("Failed to check migrations need during VM import!"));
        }
        if (sourceHost == null || serviceOffering == null || diskProfileStoragePoolList == null) {
            LOGGER.error(String.format("Failed to check migrations need during import, VM: %s!", userVm.getInstanceName()));
            cleanupFailedImportVM(vm);
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, String.format("Failed to check migrations need during import, VM: %s!", userVm.getInstanceName()));
        }
        if (!hostSupportsServiceOffering(sourceHost, serviceOffering)) {
            final VirtualMachineProfile profile = new VirtualMachineProfileImpl(vm, template, serviceOffering, owner, null);
            DeploymentPlanner.ExcludeList excludeList = new DeploymentPlanner.ExcludeList();
            excludeList.addHost(sourceHost.getId());
            final DataCenterDeployment plan = new DataCenterDeployment(sourceHost.getDataCenterId(), sourceHost.getPodId(), sourceHost.getClusterId(), null, null, null);
            DeployDestination dest = null;
            try {
                dest = deploymentPlanningManager.planDeployment(profile, plan, excludeList, null);
            } catch (Exception e) {
                LOGGER.warn(String.format("VM import failed for unmanaged vm: %s during vm migration, finding deployment destination!", vm.getInstanceName()), e);
                cleanupFailedImportVM(vm);
                throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, String.format("VM import failed for unmanaged vm: %s during vm migration, finding deployment destination!", vm.getInstanceName()));
            }
            if (dest != null) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(" Found " + dest + " for migrating the vm to.");
                }
            }
            if (dest == null) {
                cleanupFailedImportVM(vm);
                throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, String.format("VM import failed for unmanaged vm: %s during vm migration, no deployment destination found!", vm.getInstanceName()));
            }
            try {
                virtualMachineManager.migrate(vm.getUuid(), sourceHost.getId(), dest);
                vm = userVmManager.getUserVm(vm.getId());
            } catch (Exception e) {
                LOGGER.error(String.format("VM import failed for unmanaged vm: %s during vm migration!", vm.getInstanceName()), e);
                cleanupFailedImportVM(vm);
                throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, String.format("VM import failed for unmanaged vm: %s during vm migration! %s", userVm.getInstanceName(), e.getMessage()));
            }
        }
        for (Pair<DiskProfile, StoragePool> diskProfileStoragePool : diskProfileStoragePoolList) {
            if (diskProfileStoragePool == null ||
                    diskProfileStoragePool.first() == null ||
                    diskProfileStoragePool.second() == null) {
                continue;
            }
            DiskProfile profile = diskProfileStoragePool.first();
            DiskOffering dOffering = diskOfferingDao.findById(profile.getDiskOfferingId());
            if (dOffering == null) {
                continue;
            }
            VolumeVO volumeVO = volumeDao.findById(profile.getVolumeId());
            if (volumeVO == null) {
                continue;
            }
            boolean poolSupportsOfferings = storagePoolSupportsDiskOffering(diskProfileStoragePool.second(), dOffering);
            if (poolSupportsOfferings && profile.getType() == Volume.Type.ROOT) {
                poolSupportsOfferings = storagePoolSupportsServiceOffering(diskProfileStoragePool.second(), serviceOffering);
            }
            if (poolSupportsOfferings) {
                continue;
            }
            Pair<List<? extends StoragePool>, List<? extends StoragePool>> poolsPair = managementService.listStoragePoolsForMigrationOfVolume(profile.getVolumeId());
            List<? extends StoragePool> storagePools = poolsPair.second();
            if (CollectionUtils.isEmpty(storagePools)) {
                cleanupFailedImportVM(vm);
                throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, String.format("VM import failed for unmanaged vm: %s during volume migration!", userVm.getInstanceName()));
            }
            StoragePool storagePool = storagePools.get(0);
            try {
                volumeManager.migrateVolume(volumeVO, storagePool);
            } catch (Exception e) {
                LOGGER.error(String.format("VM import failed for unmanaged vm: %s during volume migration!", vm.getInstanceName()), e);
                cleanupFailedImportVM(vm);
                throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, String.format("VM import failed for unmanaged vm: %s during volume migration! %s", userVm.getInstanceName(), Strings.nullToEmpty(e.getMessage())));
            }
        }
        return userVm;
    }

    private void publishVMUsageUpdateResourceCount(final UserVm userVm, ServiceOfferingVO serviceOfferingVO) {
        if (userVm == null || serviceOfferingVO == null) {
            LOGGER.error("Failed to publish usage records during VM import!");
            return;
        }
        if (!serviceOfferingVO.isDynamic()) {
            UsageEventUtils.publishUsageEvent(EventTypes.EVENT_VM_IMPORT, userVm.getAccountId(), userVm.getDataCenterId(), userVm.getId(), userVm.getHostName(), serviceOfferingVO.getId(), userVm.getTemplateId(),
                    userVm.getHypervisorType().toString(), VirtualMachine.class.getName(), userVm.getUuid(), userVm.isDisplayVm());
        } else {
            UsageEventUtils.publishUsageEvent(EventTypes.EVENT_VM_IMPORT, userVm.getAccountId(), userVm.getAccountId(), userVm.getDataCenterId(), userVm.getHostName(), serviceOfferingVO.getId(), userVm.getTemplateId(),
                    userVm.getHypervisorType().toString(), VirtualMachine.class.getName(), userVm.getUuid(), userVm.getDetails(), userVm.isDisplayVm());
        }
        resourceLimitService.incrementResourceCount(userVm.getAccountId(), Resource.ResourceType.user_vm, userVm.isDisplayVm());
        resourceLimitService.incrementResourceCount(userVm.getAccountId(), Resource.ResourceType.cpu, userVm.isDisplayVm(), new Long(serviceOfferingVO.getCpu()));
        resourceLimitService.incrementResourceCount(userVm.getAccountId(), Resource.ResourceType.memory, userVm.isDisplayVm(), new Long(serviceOfferingVO.getRamSize()));
        // Save usage event and update resource count for user vm volumes
        List<VolumeVO> volumes = volumeDao.findByInstance(userVm.getId());
        for (VolumeVO volume : volumes) {
            UsageEventUtils.publishUsageEvent(EventTypes.EVENT_VOLUME_CREATE, volume.getAccountId(), volume.getDataCenterId(), volume.getId(), volume.getName(), volume.getDiskOfferingId(), null, volume.getSize(),
                    Volume.class.getName(), volume.getUuid(), volume.isDisplayVolume());

            resourceLimitService.incrementResourceCount(userVm.getAccountId(), Resource.ResourceType.volume, volume.isDisplayVolume());
            resourceLimitService.incrementResourceCount(userVm.getAccountId(), Resource.ResourceType.primary_storage, volume.isDisplayVolume(), volume.getSize());
        }
    }

    private UserVm importVirtualMachineInternal(final UnmanagedInstance unmanagedInstance, final String instanceName, final DataCenter zone, final Cluster cluster, final HostVO host,
                                                final VirtualMachineTemplate template, final String displayName, final String hostName, final Account caller, final Account owner, final Long userId,
                                                final ServiceOfferingVO serviceOffering, final DiskOffering diskOffering, final Map<String, Long> dataDiskOfferingMap,
                                                final Map<String, Long> nicNetworkMap, final Map<String, Network.IpAddresses> callerNicIpAddressMap,
                                                final Map<String, String> details, final boolean migrateAllowed) {
        UserVm userVm = null;

        ServiceOfferingVO validatedServiceOffering = null;
        try {
            validatedServiceOffering = getUnmanagedInstanceServiceOffering(unmanagedInstance, serviceOffering, owner, zone, details);
        } catch (Exception e) {
            LOGGER.error("Service offering for VM import not compatible!", e);
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, String.format("Failed to import VM: %s! %s", unmanagedInstance.getName(), Strings.nullToEmpty(e.getMessage())));
        }

        Map<String, String> allDetails = new HashMap<>(details);
        if (validatedServiceOffering.isDynamic()) {
            allDetails.put(VmDetailConstants.CPU_NUMBER, String.valueOf(validatedServiceOffering.getCpu()));
            allDetails.put(VmDetailConstants.MEMORY, String.valueOf(validatedServiceOffering.getRamSize()));
            if (serviceOffering.getSpeed() == null) {
                allDetails.put(VmDetailConstants.CPU_SPEED, String.valueOf(validatedServiceOffering.getSpeed()));
            }
        }

        if (!migrateAllowed && !hostSupportsServiceOffering(host, validatedServiceOffering)) {
            throw new InvalidParameterValueException(String.format("Service offering: %s is not compatible with host: %s of unmanaged VM: %s", serviceOffering.getUuid(), host.getUuid(), instanceName));
        }
        // Check disks and supplied disk offerings
        List<UnmanagedInstance.Disk> unmanagedInstanceDisks = unmanagedInstance.getDisks();
        if (CollectionUtils.isEmpty(unmanagedInstanceDisks)) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, String.format("No attached disks found for the unmanaged VM: %s", instanceName));
        }
        final UnmanagedInstance.Disk rootDisk = unmanagedInstance.getDisks().get(0);
        List<UnmanagedInstance.Disk> dataDisks = new ArrayList<>();
        try {
            checkUnmanagedDiskAndOfferingForImport(rootDisk, diskOffering, owner, zone, cluster, migrateAllowed);
            if (unmanagedInstanceDisks.size() > 1) { // Data disk(s) present
                dataDisks.addAll(unmanagedInstanceDisks);
                dataDisks.remove(0);
                checkUnmanagedDiskAndOfferingForImport(dataDisks, dataDiskOfferingMap, owner, zone, cluster, migrateAllowed);
            }
            resourceLimitService.checkResourceLimit(owner, Resource.ResourceType.volume, unmanagedInstanceDisks.size());
        } catch (ResourceAllocationException e) {
            LOGGER.error(String.format("Volume resource allocation error for owner: %s!", owner.getUuid()), e);
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, String.format("Volume resource allocation error for owner: %s! %s", owner.getUuid(), Strings.nullToEmpty(e.getMessage())));
        }
        // Check NICs and supplied networks
        Map<String, Network.IpAddresses> nicIpAddressMap = getNicIpAddresses(unmanagedInstance.getNics(), callerNicIpAddressMap);
        Map<String, Long> allNicNetworkMap = getUnmanagedNicNetworkMap(unmanagedInstance.getNics(), nicNetworkMap, nicIpAddressMap, zone, hostName, owner);
        VirtualMachine.PowerState powerState = VirtualMachine.PowerState.PowerOff;
        if (unmanagedInstance.getPowerState().equals(UnmanagedInstance.PowerState.PowerOn)) {
            powerState = VirtualMachine.PowerState.PowerOn;
        }
        try {
            userVm = userVmManager.importVM(zone, host, template, instanceName, displayName, owner,
                    null, caller, true, null, owner.getAccountId(), userId,
                    validatedServiceOffering, diskOffering, null, hostName,
                    cluster.getHypervisorType(), allDetails, powerState);
        } catch (InsufficientCapacityException ice) {
            LOGGER.error(String.format("Failed to import vm name: %s", instanceName), ice);
            throw new ServerApiException(ApiErrorCode.INSUFFICIENT_CAPACITY_ERROR, ice.getMessage());
        }
        if (userVm == null) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, String.format("Failed to import vm name: %s", instanceName));
        }
        List<Pair<DiskProfile, StoragePool>> diskProfileStoragePoolList = new ArrayList<>();
        try {
            if (rootDisk.getCapacity() == null || rootDisk.getCapacity() == 0) {
                throw new InvalidParameterValueException(String.format("Root disk ID: %s size is invalid", rootDisk.getDiskId()));
            }
            diskProfileStoragePoolList.add(importDisk(rootDisk, userVm, cluster, diskOffering, Volume.Type.ROOT, String.format("ROOT-%d", userVm.getId()), (rootDisk.getCapacity() / Resource.ResourceType.bytesToGiB), template, owner, null, migrateAllowed));
            for (UnmanagedInstance.Disk disk : dataDisks) {
                if (disk.getCapacity() == null || disk.getCapacity() == 0) {
                    throw new InvalidParameterValueException(String.format("Disk ID: %s size is invalid", rootDisk.getDiskId()));
                }
                DiskOffering offering = diskOfferingDao.findById(dataDiskOfferingMap.get(disk.getDiskId()));
                diskProfileStoragePoolList.add(importDisk(disk, userVm, cluster, offering, Volume.Type.DATADISK, String.format("DATA-%d-%s", userVm.getId(), disk.getDiskId()), (disk.getCapacity() / Resource.ResourceType.bytesToGiB), template, owner, null, migrateAllowed));
            }
        } catch (Exception e) {
            LOGGER.error(String.format("Failed to import volumes while importing vm: %s", instanceName), e);
            cleanupFailedImportVM(userVm);
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, String.format("Failed to import volumes while importing vm: %s. %s", instanceName, Strings.nullToEmpty(e.getMessage())));
        }
        try {
            boolean firstNic = true;
            for (UnmanagedInstance.Nic nic : unmanagedInstance.getNics()) {
                Network network = networkDao.findById(allNicNetworkMap.get(nic.getNicId()));
                Network.IpAddresses ipAddresses = nicIpAddressMap.get(nic.getNicId());
                if (ipAddresses == null || (Strings.isNullOrEmpty(ipAddresses.getIp4Address()) && Strings.isNullOrEmpty(ipAddresses.getIp6Address()))) {
                    if (ipAddresses == null) {
                        ipAddresses = new Network.IpAddresses(null, null);
                    }
                    if (!Strings.isNullOrEmpty(nic.getIpAddress())) {
                        ipAddresses.setIp4Address(nic.getIpAddress());
                    }
                }
                importNic(nic, userVm, network, ipAddresses, firstNic);
                firstNic = false;
            }
        } catch (Exception e) {
            LOGGER.error(String.format("Failed to import NICs while importing vm: %s", instanceName), e);
            cleanupFailedImportVM(userVm);
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, String.format("Failed to import NICs while importing vm: %s. %s", instanceName, Strings.nullToEmpty(e.getMessage())));
        }
        if (migrateAllowed) {
            userVm = migrateImportedVM(host, template, validatedServiceOffering, userVm, owner, diskProfileStoragePoolList);
        }
        publishVMUsageUpdateResourceCount(userVm, validatedServiceOffering);
        return userVm;
    }

    @Override
    public ListResponse<UnmanagedInstanceResponse> listUnmanagedInstances(ListUnmanagedInstancesCmd cmd) {
        final Account caller = CallContext.current().getCallingAccount();
        if (caller.getType() != Account.ACCOUNT_TYPE_ADMIN) {
            throw new PermissionDeniedException(String.format("Cannot perform this operation, Calling account is not root admin: %s", caller.getUuid()));
        }
        final Long clusterId = cmd.getClusterId();
        if (clusterId == null) {
            throw new InvalidParameterValueException(String.format("Cluster ID cannot be null!"));
        }
        final Cluster cluster = clusterDao.findById(clusterId);
        if (cluster == null) {
            throw new InvalidParameterValueException(String.format("Cluster ID: %d cannot be found!", clusterId));
        }
        if (cluster.getHypervisorType() != Hypervisor.HypervisorType.VMware) {
            throw new InvalidParameterValueException(String.format("VM ingestion is currently not supported for hypervisor: %s", cluster.getHypervisorType().toString()));
        }
        List<HostVO> hosts = resourceManager.listHostsInClusterByStatus(clusterId, Status.Up);
        List<String> additionalNameFilters = getAdditionalNameFilters(cluster);
        List<UnmanagedInstanceResponse> responses = new ArrayList<>();
        for (HostVO host : hosts) {
            List<String> managedVms = new ArrayList<>();
            managedVms.addAll(additionalNameFilters);
            managedVms.addAll(getHostManagedVms(host));

            GetUnmanagedInstancesCommand command = new GetUnmanagedInstancesCommand();
            command.setInstanceName(cmd.getName());
            command.setManagedInstancesNames(managedVms);
            Answer answer = agentManager.easySend(host.getId(), command);
            if (!(answer instanceof GetUnmanagedInstancesAnswer)) {
                continue;
            }
            GetUnmanagedInstancesAnswer unmanagedInstancesAnswer = (GetUnmanagedInstancesAnswer) answer;
            HashMap<String, UnmanagedInstance> unmanagedInstances = new HashMap<>();
            unmanagedInstances.putAll(unmanagedInstancesAnswer.getUnmanagedInstances());
            Set<String> keys = unmanagedInstances.keySet();
            for (String key : keys) {
                responses.add(createUnmanagedInstanceResponse(unmanagedInstances.get(key), cluster, host));
            }
        }
        ListResponse<UnmanagedInstanceResponse> listResponses = new ListResponse<>();
        listResponses.setResponses(responses, responses.size());
        return listResponses;
    }

    @Override
    public UserVmResponse importUnmanagedInstance(ImportUnmanagedInstanceCmd cmd) {
        final Account caller = CallContext.current().getCallingAccount();
        if (caller.getType() != Account.ACCOUNT_TYPE_ADMIN) {
            throw new PermissionDeniedException(String.format("Cannot perform this operation, Calling account is not root admin: %s", caller.getUuid()));
        }
        final Long clusterId = cmd.getClusterId();
        if (clusterId == null) {
            throw new InvalidParameterValueException(String.format("Cluster ID cannot be null!"));
        }
        final Cluster cluster = clusterDao.findById(clusterId);
        if (cluster == null) {
            throw new InvalidParameterValueException(String.format("Cluster ID: %d cannot be found!", clusterId));
        }
        if (cluster.getHypervisorType() != Hypervisor.HypervisorType.VMware) {
            throw new InvalidParameterValueException(String.format("VM import is currently not supported for hypervisor: %s", cluster.getHypervisorType().toString()));
        }
        final DataCenter zone = dataCenterDao.findById(cluster.getDataCenterId());
        final String instanceName = cmd.getName();
        if (Strings.isNullOrEmpty(instanceName)) {
            throw new InvalidParameterValueException(String.format("Instance name cannot be empty!"));
        }
        final Account owner = accountService.getActiveAccountById(cmd.getEntityOwnerId());

        Long userId = null;
        List<UserVO> userVOs = userDao.listByAccount(owner.getAccountId());
        if (CollectionUtils.isNotEmpty(userVOs)) {
            userId = userVOs.get(0).getId();
        }
        final Long templateId = cmd.getTemplateId();
        if (templateId == null) {
            throw new InvalidParameterValueException(String.format("Template ID cannot be null!"));
        }
        final VirtualMachineTemplate template = templateDao.findById(templateId);
        if (template == null) {
            throw new InvalidParameterValueException(String.format("Template ID: %d cannot be found!", templateId));
        }
        final Long serviceOfferingId = cmd.getServiceOfferingId();
        if (serviceOfferingId == null) {
            throw new InvalidParameterValueException(String.format("Service offering ID cannot be null!"));
        }
        final ServiceOfferingVO serviceOffering = serviceOfferingDao.findById(serviceOfferingId);
        if (serviceOffering == null) {
            throw new InvalidParameterValueException(String.format("Service offering ID: %d cannot be found!", serviceOfferingId));
        }
        accountService.checkAccess(owner, serviceOffering, zone);
        try {
            resourceLimitService.checkResourceLimit(owner, Resource.ResourceType.user_vm, 1);
        } catch (ResourceAllocationException e) {
            LOGGER.error(String.format("VM resource allocation error for account: %s", owner.getUuid()), e);
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, String.format("VM resource allocation error for account: %s! %s", owner.getUuid(), Strings.nullToEmpty(e.getMessage())));
        }
        final Long diskOfferingId = cmd.getDiskOfferingId();
        if (diskOfferingId == null) {
            throw new InvalidParameterValueException(String.format("Service offering ID cannot be null!"));
        }
        final DiskOffering diskOffering = diskOfferingDao.findById(diskOfferingId);
        if (diskOffering == null) {
            throw new InvalidParameterValueException(String.format("Disk offering ID: %d cannot be found!", diskOfferingId));
        }
        String displayName = cmd.getDisplayName();
        if (Strings.isNullOrEmpty(displayName)) {
            displayName = instanceName;
        }
        String hostName = cmd.getHostName();
        if (Strings.isNullOrEmpty(hostName)) {
            if (!NetUtils.verifyDomainNameLabel(instanceName, true)) {
                throw new InvalidParameterValueException(String.format("Please provide hostname for the VM. VM name contains unsupported characters for it to be used as hostname"));
            }
            hostName = instanceName;
        }
        if (!NetUtils.verifyDomainNameLabel(hostName, true)) {
            throw new InvalidParameterValueException("Invalid VM hostname. VM hostname can contain ASCII letters 'a' through 'z', the digits '0' through '9', "
                    + "and the hyphen ('-'), must be between 1 and 63 characters long, and can't start or end with \"-\" and can't start with digit");
        }
        if (cluster.getHypervisorType().equals(Hypervisor.HypervisorType.VMware) &&
                Boolean.parseBoolean(configurationDao.getValue(Config.SetVmInternalNameUsingDisplayName.key()))) {
            // If global config vm.instancename.flag is set to true, then CS will set guest VM's name as it appears on the hypervisor, to its hostname.
            // In case of VMware since VM name must be unique within a DC, check if VM with the same hostname already exists in the zone.
            VMInstanceVO vmByHostName = vmDao.findVMByHostNameInZone(hostName, zone.getId());
            if (vmByHostName != null && vmByHostName.getState() != VirtualMachine.State.Expunging) {
                throw new InvalidParameterValueException(String.format("Failed to import VM: %s! There already exists a VM by the hostname: %s in zone: %s", instanceName, hostName, zone.getUuid()));
            }
        }
        final Map<String, Long> nicNetworkMap = cmd.getNicNetworkList();
        final Map<String, Network.IpAddresses> nicIpAddressMap = cmd.getNicIpAddressList();
        final Map<String, Long> dataDiskOfferingMap = cmd.getDataDiskToDiskOfferingList();
        final Map<String, String> details = cmd.getDetails();
        List<HostVO> hosts = resourceManager.listHostsInClusterByStatus(clusterId, Status.Up);
        UserVm userVm = null;
        List<String> additionalNameFilters = getAdditionalNameFilters(cluster);
        for (HostVO host : hosts) {
            List<String> managedVms = new ArrayList<>();
            managedVms.addAll(additionalNameFilters);
            managedVms.addAll(getHostManagedVms(host));
            GetUnmanagedInstancesCommand command = new GetUnmanagedInstancesCommand(instanceName);
            command.setManagedInstancesNames(managedVms);
            Answer answer = agentManager.easySend(host.getId(), command);
            if (!(answer instanceof GetUnmanagedInstancesAnswer)) {
                continue;
            }
            GetUnmanagedInstancesAnswer unmanagedInstancesAnswer = (GetUnmanagedInstancesAnswer) answer;
            HashMap<String, UnmanagedInstance> unmanagedInstances = unmanagedInstancesAnswer.getUnmanagedInstances();
            if (MapUtils.isEmpty(unmanagedInstances)) {
                continue;
            }
            Set<String> names = unmanagedInstances.keySet();
            for (String name : names) {
                if (instanceName.equals(name)) {
                    UnmanagedInstance unmanagedInstance = unmanagedInstances.get(name);
                    if (unmanagedInstance == null) {
                        throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, String.format("Unable to retrieve details for unmanaged VM: %s", name));
                    }
                    userVm = importVirtualMachineInternal(unmanagedInstance, instanceName, zone, cluster, host,
                            template, displayName, hostName, caller, owner, userId,
                            serviceOffering, diskOffering, dataDiskOfferingMap,
                            nicNetworkMap, nicIpAddressMap,
                            details, cmd.getMigrateAllowed());
                    break;
                }
            }
            if (userVm != null) {
                break;
            }
        }
        if (userVm == null) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, String.format("Failed to find unmanaged vm with name: %s", instanceName));
        }
        return responseGenerator.createUserVmResponse(ResponseObject.ResponseView.Full, "virtualmachine", userVm).get(0);
    }

    @Override
    public List<Class<?>> getCommands() {
        final List<Class<?>> cmdList = new ArrayList<Class<?>>();
        cmdList.add(ListUnmanagedInstancesCmd.class);
        cmdList.add(ImportUnmanagedInstanceCmd.class);
        return cmdList;
    }
}
