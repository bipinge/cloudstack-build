/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cloudstack.storage.vmsnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.naming.ConfigurationException;

import org.apache.cloudstack.engine.subsystem.api.storage.DataStoreProvider;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStoreProviderManager;
import org.apache.cloudstack.engine.subsystem.api.storage.SnapshotDataFactory;
import org.apache.cloudstack.engine.subsystem.api.storage.SnapshotInfo;
import org.apache.cloudstack.engine.subsystem.api.storage.SnapshotStrategy;
import org.apache.cloudstack.engine.subsystem.api.storage.SnapshotStrategy.SnapshotOperation;
import org.apache.cloudstack.engine.subsystem.api.storage.StorageStrategyFactory;
import org.apache.cloudstack.engine.subsystem.api.storage.StrategyPriority;
import org.apache.cloudstack.engine.subsystem.api.storage.VMSnapshotOptions;
import org.apache.cloudstack.engine.subsystem.api.storage.VolumeDataFactory;
import org.apache.cloudstack.engine.subsystem.api.storage.VolumeInfo;
import org.apache.cloudstack.storage.datastore.db.PrimaryDataStoreDao;
import org.apache.cloudstack.storage.datastore.db.StoragePoolVO;
import org.apache.cloudstack.storage.to.VolumeObjectTO;
import org.apache.log4j.Logger;

import com.cloud.agent.api.CreateVMSnapshotAnswer;
import com.cloud.agent.api.CreateVMSnapshotCommand;
import com.cloud.agent.api.DeleteVMSnapshotAnswer;
import com.cloud.agent.api.DeleteVMSnapshotCommand;
import com.cloud.agent.api.FreezeThawVMAnswer;
import com.cloud.agent.api.FreezeThawVMCommand;
import com.cloud.agent.api.RevertToVMSnapshotAnswer;
import com.cloud.agent.api.RevertToVMSnapshotCommand;
import com.cloud.agent.api.VMSnapshotTO;
import com.cloud.event.EventTypes;
import com.cloud.exception.AgentUnavailableException;
import com.cloud.exception.OperationTimedoutException;
import com.cloud.hypervisor.Hypervisor;
import com.cloud.storage.CreateSnapshotPayload;
import com.cloud.storage.GuestOSVO;
import com.cloud.storage.Snapshot;
import com.cloud.storage.Snapshot.State;
import com.cloud.storage.SnapshotVO;
import com.cloud.storage.Storage.StoragePoolType;
import com.cloud.storage.VolumeApiService;
import com.cloud.storage.VolumeVO;
import com.cloud.storage.dao.SnapshotDao;
import com.cloud.storage.snapshot.SnapshotApiService;
import com.cloud.storage.snapshot.SnapshotManager;
import com.cloud.user.AccountService;
import com.cloud.uservm.UserVm;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.fsm.NoTransitionException;
import com.cloud.vm.UserVmVO;
import com.cloud.vm.snapshot.VMSnapshot;
import com.cloud.vm.snapshot.VMSnapshotDetailsVO;
import com.cloud.vm.snapshot.VMSnapshotVO;
import com.cloud.vm.snapshot.dao.VMSnapshotDetailsDao;

public class StorageVMSnapshotStrategy extends DefaultVMSnapshotStrategy {
    private static final Logger s_logger = Logger.getLogger(StorageVMSnapshotStrategy.class);
    @Inject
    VolumeApiService volumeService;
    @Inject
    AccountService accountService;
    @Inject
    VolumeDataFactory volumeDataFactory;
    @Inject
    SnapshotDao snapshotDao;
    @Inject
    StorageStrategyFactory storageStrategyFactory;
    @Inject
    SnapshotDataFactory snapshotDataFactory;
    @Inject
    PrimaryDataStoreDao storagePool;
    @Inject
    DataStoreProviderManager dataStoreProviderMgr;
    @Inject
    SnapshotApiService snapshotApiService;
    @Inject
    VMSnapshotDetailsDao vmSnapshotDetailsDao;

    private static final String STORAGE_SNAPSHOT = "kvmStorageSnapshot";

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
       return super.configure(name, params);
    }

    @Override
    public VMSnapshot takeVMSnapshot(VMSnapshot vmSnapshot) {
        Long hostId = vmSnapshotHelper.pickRunningHost(vmSnapshot.getVmId());
        UserVm userVm = userVmDao.findById(vmSnapshot.getVmId());
        VMSnapshotVO vmSnapshotVO = (VMSnapshotVO) vmSnapshot;

        CreateVMSnapshotAnswer answer = null;
        FreezeThawVMCommand freezeCommand = null;
        FreezeThawVMAnswer freezeAnswer = null;
        FreezeThawVMCommand thawCmd = null;
        FreezeThawVMAnswer thawAnswer = null;
        List<SnapshotInfo> forRollback = new ArrayList<>();
        long startFreeze = 0;
        try {
            vmSnapshotHelper.vmSnapshotStateTransitTo(vmSnapshotVO, VMSnapshot.Event.KVMCreateRequested);
        } catch (NoTransitionException e) {
            throw new CloudRuntimeException(e.getMessage());
        }

        boolean result = false;
        try {
            GuestOSVO guestOS = guestOSDao.findById(userVm.getGuestOSId());
            List<VolumeObjectTO> volumeTOs = vmSnapshotHelper.getVolumeTOList(userVm.getId());

            long prev_chain_size = 0;
            long virtual_size = 0;

            VMSnapshotTO current = null;
            VMSnapshotVO currentSnapshot = vmSnapshotDao.findCurrentSnapshotByVmId(userVm.getId());
            if (currentSnapshot != null) {
                current = vmSnapshotHelper.getSnapshotWithParents(currentSnapshot);
            }
            VMSnapshotOptions options = ((VMSnapshotVO) vmSnapshot).getOptions();
            boolean quiescevm = true;
            if (options != null) {
                quiescevm = options.needQuiesceVM();
            }
            VMSnapshotTO target = new VMSnapshotTO(vmSnapshot.getId(), vmSnapshot.getName(), vmSnapshot.getType(), null, vmSnapshot.getDescription(), false, current, quiescevm);
            if (current == null) {
                vmSnapshotVO.setParent(null);
            } else {
                vmSnapshotVO.setParent(current.getId());
            }
            CreateVMSnapshotCommand ccmd = new CreateVMSnapshotCommand(userVm.getInstanceName(), userVm.getUuid(), target, volumeTOs,  guestOS.getDisplayName());
            s_logger.info("Creating VM snapshot for KVM hypervisor without memory");

            List<VolumeInfo> vinfos = new ArrayList<>();
            for (VolumeObjectTO volumeObjectTO : volumeTOs) {
                vinfos.add(volumeDataFactory.getVolume(volumeObjectTO.getId()));
                virtual_size += volumeObjectTO.getSize();
                VolumeVO volumeVO = volumeDao.findById(volumeObjectTO.getId());
                prev_chain_size += volumeVO.getVmSnapshotChainSize() == null ? 0 : volumeVO.getVmSnapshotChainSize();
            }

            boolean backupToSecondary = SnapshotManager.BackupSnapshotAfterTakingSnapshot.value() == null || SnapshotManager.BackupSnapshotAfterTakingSnapshot.value();

            if (!backupToSecondary) {
                for (VolumeInfo volumeInfo : vinfos) {
                    checkBackupIsSupported(ccmd, volumeInfo);
                }
            }
            freezeCommand = new FreezeThawVMCommand(userVm.getInstanceName());
            freezeCommand.setOption("freeze");
            freezeAnswer = (FreezeThawVMAnswer) agentMgr.send(hostId, freezeCommand);
            startFreeze = System.nanoTime();

            thawCmd = new FreezeThawVMCommand(userVm.getInstanceName());
            thawCmd.setOption("thaw");
            if (freezeAnswer != null && freezeAnswer.getResult()) {
                s_logger.info("The virtual machine is frozen");
                for (VolumeInfo vol : vinfos) {
                    long startSnapshtot = System.nanoTime();
                    SnapshotInfo snapInfo = createDiskSnapshot(vmSnapshot, forRollback, vol);
                    if (!backupToSecondary && snapInfo != null) {
                        snapInfo.markBackedUp();
                    }
                    if (snapInfo == null) {
                        thawAnswer = (FreezeThawVMAnswer) agentMgr.send(hostId, thawCmd);
                        throw new CloudRuntimeException("Could not take snapshot for volume with id=" + vol.getId());
                    }
                    s_logger.info(String.format("Snapshot with id=%s, took  %s miliseconds", snapInfo.getId(),
                            TimeUnit.MILLISECONDS.convert(elapsedTime(startSnapshtot), TimeUnit.NANOSECONDS)));
                }
                answer = new CreateVMSnapshotAnswer(ccmd, true, "");
                answer.setVolumeTOs(volumeTOs);
                thawAnswer = (FreezeThawVMAnswer) agentMgr.send(hostId, thawCmd);
                if (thawAnswer != null && thawAnswer.getResult()) {
                    s_logger.info(String.format(
                            "Virtual machne is thawed. The freeze of virtual machine took %s miliseconds.",
                            TimeUnit.MILLISECONDS.convert(elapsedTime(startFreeze), TimeUnit.NANOSECONDS)));
                    if (backupToSecondary) {
                        for (SnapshotInfo snapshot : forRollback) {
                            backupSnapshot(snapshot, forRollback);
                        }
                    }
                }
            } else {
                throw new CloudRuntimeException("Could not freeze VM." + freezeAnswer.getDetails());
            }
            if (answer != null && answer.getResult()) {
                processAnswer(vmSnapshotVO, userVm, answer, null);
                s_logger.debug("Create vm snapshot " + vmSnapshot.getName() + " succeeded for vm: " + userVm.getInstanceName());
                long new_chain_size = 0;
                for (VolumeObjectTO volumeTo : answer.getVolumeTOs()) {
                    publishUsageEvent(EventTypes.EVENT_VM_SNAPSHOT_CREATE, vmSnapshot, userVm, volumeTo);
                    new_chain_size += volumeTo.getSize();
                }
                publishUsageEvent(EventTypes.EVENT_VM_SNAPSHOT_ON_PRIMARY, vmSnapshot, userVm, new_chain_size - prev_chain_size, virtual_size);
                result = true;
                return vmSnapshot;
            } else {
                String errMsg = "Creating VM snapshot: " + vmSnapshot.getName() + " failed";
                s_logger.error(errMsg);
                throw new CloudRuntimeException(errMsg);
            }
        } catch (OperationTimedoutException e) {
            s_logger.debug("Creating VM snapshot: " + vmSnapshot.getName() + " failed: " + e.toString());
            throw new CloudRuntimeException(
                    "Creating VM snapshot: " + vmSnapshot.getName() + " failed: " + e.toString());
        } catch (AgentUnavailableException e) {
            s_logger.debug("Creating VM snapshot: " + vmSnapshot.getName() + " failed", e);
            throw new CloudRuntimeException(
                    "Creating VM snapshot: " + vmSnapshot.getName() + " failed: " + e.toString());
        } catch (CloudRuntimeException e) {
            throw new CloudRuntimeException(e.getMessage());
        } finally {
            if (thawAnswer == null && freezeAnswer != null) {
                s_logger.info(String.format("Freeze of virtual machine took %s miliseconds.", TimeUnit.MILLISECONDS
                                                .convert(elapsedTime(startFreeze), TimeUnit.NANOSECONDS)));
                try {
                    thawAnswer = (FreezeThawVMAnswer) agentMgr.send(hostId, thawCmd);
                } catch (AgentUnavailableException | OperationTimedoutException e) {
                    s_logger.debug("Could not unfreeze the VM due to " + e);
                }
            }
            if (!result) {
                for (SnapshotInfo snapshotInfo : forRollback) {
                    rollbackDiskSnapshot(snapshotInfo);
                }
                try {
                    List<VMSnapshotDetailsVO> vmSnapshotDetails = vmSnapshotDetailsDao.listDetails(vmSnapshot.getId());
                    for (VMSnapshotDetailsVO vmSnapshotDetailsVO : vmSnapshotDetails) {
                        if (vmSnapshotDetailsVO.getName().equals(STORAGE_SNAPSHOT)) {
                            vmSnapshotDetailsDao.remove(vmSnapshotDetailsVO.getId());
                        }
                    }
                    vmSnapshotHelper.vmSnapshotStateTransitTo(vmSnapshot, VMSnapshot.Event.OperationFailed);
                } catch (NoTransitionException e1) {
                    s_logger.error("Cannot set vm snapshot state due to: " + e1.getMessage());
                }
            }
        }
    }


    @Override
    public boolean deleteVMSnapshot(VMSnapshot vmSnapshot) {
        UserVmVO userVm = userVmDao.findById(vmSnapshot.getVmId());
        VMSnapshotVO vmSnapshotVO = (VMSnapshotVO) vmSnapshot;
        try {
            vmSnapshotHelper.vmSnapshotStateTransitTo(vmSnapshot, VMSnapshot.Event.ExpungeRequested);
        } catch (NoTransitionException e) {
            s_logger.debug("Failed to change vm snapshot state with event ExpungeRequested");
            throw new CloudRuntimeException(
                    "Failed to change vm snapshot state with event ExpungeRequested: " + e.getMessage());
        }

        List<VolumeObjectTO> volumeTOs = vmSnapshotHelper.getVolumeTOList(vmSnapshot.getVmId());

        String vmInstanceName = userVm.getInstanceName();
        VMSnapshotTO parent = vmSnapshotHelper.getSnapshotWithParents(vmSnapshotVO).getParent();
        VMSnapshotTO vmSnapshotTO = new VMSnapshotTO(vmSnapshot.getId(), vmSnapshot.getName(), vmSnapshot.getType(),
                vmSnapshot.getCreated().getTime(), vmSnapshot.getDescription(), vmSnapshot.getCurrent(), parent, true);
        GuestOSVO guestOS = guestOSDao.findById(userVm.getGuestOSId());
        DeleteVMSnapshotCommand deleteSnapshotCommand = new DeleteVMSnapshotCommand(vmInstanceName, vmSnapshotTO,
                volumeTOs, guestOS.getDisplayName());

        try {
            deleteDiskSnapshot(vmSnapshot);
            processAnswer(vmSnapshotVO, userVm, new DeleteVMSnapshotAnswer(deleteSnapshotCommand, volumeTOs), null);
            long full_chain_size = 0;
            for (VolumeObjectTO volumeTo : volumeTOs) {
                publishUsageEvent(EventTypes.EVENT_VM_SNAPSHOT_DELETE, vmSnapshot, userVm, volumeTo);
                full_chain_size += volumeTo.getSize();
            }
            publishUsageEvent(EventTypes.EVENT_VM_SNAPSHOT_OFF_PRIMARY, vmSnapshot, userVm, full_chain_size, 0L);
            return true;
        } catch (CloudRuntimeException err) {
            //In case of failure all volume's snapshots will be set to BackedUp state, because in most cases they won't be consistent
            List<VMSnapshotDetailsVO> listSnapshots = vmSnapshotDetailsDao.listDetails(vmSnapshot.getId());
            for (VMSnapshotDetailsVO vmSnapshotDetailsVO : listSnapshots) {
                if (vmSnapshotDetailsVO.getName().equals(STORAGE_SNAPSHOT)) {
                    SnapshotVO snapshot = snapshotDao.findById(Long.parseLong(vmSnapshotDetailsVO.getValue()));
                    if (snapshot != null) {
                        snapshot.setState(State.BackedUp);
                        snapshotDao.update(snapshot.getId(), snapshot);
                        vmSnapshotDetailsDao.remove(vmSnapshotDetailsVO.getId());
                    }
                }
            }
            s_logger.error("Delete vm snapshot " + vmSnapshot.getName() + " of vm " + userVm.getInstanceName()
                    + " failed due to " + err);
            throw new CloudRuntimeException("Delete vm snapshot " + vmSnapshot.getName() + " of vm "
                    + userVm.getInstanceName() + " failed due to " + err);
        }
    }

    @Override
    public boolean revertVMSnapshot(VMSnapshot vmSnapshot) {
        VMSnapshotVO vmSnapshotVO = (VMSnapshotVO) vmSnapshot;
        UserVmVO userVm = userVmDao.findById(vmSnapshot.getVmId());
        try {
            vmSnapshotHelper.vmSnapshotStateTransitTo(vmSnapshotVO, VMSnapshot.Event.RevertRequested);
        } catch (NoTransitionException e) {
            throw new CloudRuntimeException(e.getMessage());
        }

        boolean result = false;
        try {
            List<VolumeObjectTO> volumeTOs = vmSnapshotHelper.getVolumeTOList(userVm.getId());
            String vmInstanceName = userVm.getInstanceName();
            VMSnapshotTO parent = vmSnapshotHelper.getSnapshotWithParents(vmSnapshotVO).getParent();

            VMSnapshotTO vmSnapshotTO = new VMSnapshotTO(vmSnapshotVO.getId(), vmSnapshotVO.getName(), vmSnapshotVO.getType(),
                                       vmSnapshotVO.getCreated().getTime(), vmSnapshotVO.getDescription(), vmSnapshotVO.getCurrent(), parent, true);
            GuestOSVO guestOS = guestOSDao.findById(userVm.getGuestOSId());
            RevertToVMSnapshotCommand revertToSnapshotCommand = new RevertToVMSnapshotCommand(vmInstanceName,
                    userVm.getUuid(), vmSnapshotTO, volumeTOs, guestOS.getDisplayName());
            List<VolumeInfo> volumeInfos = new ArrayList<>();
            for (VolumeObjectTO volumeObjectTO : volumeTOs) {
                volumeInfos.add(volumeDataFactory.getVolume(volumeObjectTO.getId()));
            }
            revertDiskSnapshot(vmSnapshot);
            RevertToVMSnapshotAnswer answer = new RevertToVMSnapshotAnswer(revertToSnapshotCommand, true, "");
            answer.setVolumeTOs(volumeTOs);
            processAnswer(vmSnapshotVO, userVm, answer, null);
            result = true;
        } catch (CloudRuntimeException e) {
            s_logger.error(e);
            throw new CloudRuntimeException(e);
        } finally {
            if (!result) {
                try {
                    vmSnapshotHelper.vmSnapshotStateTransitTo(vmSnapshot, VMSnapshot.Event.OperationFailed);
                } catch (NoTransitionException e1) {
                    s_logger.error("Cannot set vm snapshot state due to: " + e1.getMessage());
                }
            }
        }
        return result;
    }

    @Override
    public StrategyPriority canHandle(VMSnapshot vmSnapshot) {
       UserVmVO userVm = userVmDao.findById(vmSnapshot.getVmId());
       if ( SnapshotManager.VMsnapshotKVM.value() && userVm.getHypervisorType() == Hypervisor.HypervisorType.KVM
                    && vmSnapshot.getType() == VMSnapshot.Type.Disk) {
           return StrategyPriority.HYPERVISOR;
       }
       return StrategyPriority.CANT_HANDLE;
    }

    @Override
    public boolean deleteVMSnapshotFromDB(VMSnapshot vmSnapshot, boolean unmanage) {
       return super.deleteVMSnapshotFromDB(vmSnapshot, unmanage);
    }

    private long elapsedTime(long startTime) {
        long endTime = System.nanoTime();
        return endTime - startTime;
    }

    //If snapshot.backup.to.secondary is not enabled check if disks are on NFS
    private void checkBackupIsSupported(CreateVMSnapshotCommand ccmd, VolumeInfo volumeInfo) {
        StoragePoolVO storage = storagePool.findById(volumeInfo.getPoolId());
        DataStoreProvider provider = dataStoreProviderMgr.getDefaultPrimaryDataStoreProvider();
        s_logger.info(String.format("Backup to secondary storage is set to false, storagePool=%s, storageProvider=%s ", storage.getPoolType(), provider.getName()));
        if (storage.getPoolType() == StoragePoolType.NetworkFilesystem || storage.getPoolType() == StoragePoolType.Filesystem) {
            String err = "Backup to secondary should be enabled for NFS primary storage";
            s_logger.debug(err);
            throw new CloudRuntimeException(err);
        }
    }

    //Backup to secondary storage. It is mandatory for storages which are using qemu/libvirt
    protected void backupSnapshot(SnapshotInfo snapshot, List<SnapshotInfo> forRollback) {
        try {
            SnapshotStrategy snapshotStrategy = storageStrategyFactory.getSnapshotStrategy(snapshot, SnapshotOperation.TAKE);
            SnapshotInfo snInfo = snapshotStrategy.backupSnapshot(snapshot);
            if (snInfo == null) {
                throw new CloudRuntimeException("Could not backup snapshot for volume");
            }
            s_logger.info(String.format("Backedup snapshot with id=%s, path=%s", snInfo.getId(), snInfo.getPath()));
        } catch (Exception e) {
            forRollback.removeIf(snap -> snap.equals(snapshot));
            throw new CloudRuntimeException("Could not backup snapshot for volume " + e.getMessage());
        }
    }

    //Rollback if one of disks snapshot fails
    protected void rollbackDiskSnapshot(SnapshotInfo snapshotInfo) {
        Long snapshotID = snapshotInfo.getId();
        SnapshotVO snapshot = snapshotDao.findById(snapshotID);
        deleteSnapshotByStrategy(snapshot);
        s_logger.debug("Rollback is executed: deleting snapshot with id:" + snapshotID);
    }

    protected void deleteSnapshotByStrategy(SnapshotVO snapshot) {
        //The snapshot could not be deleted separately, that's why we set snapshot state to BackedUp for operation delete VM snapshots and rollback
        snapshot.setState(Snapshot.State.BackedUp);
        snapshotDao.persist(snapshot);
        SnapshotStrategy strategy = storageStrategyFactory.getSnapshotStrategy(snapshot, SnapshotOperation.DELETE);
        if (strategy != null) {
            boolean snapshotForDelete = strategy.deleteSnapshot(snapshot.getId());
            if (!snapshotForDelete) {
                throw new CloudRuntimeException("Failed to delete snapshot");
            }
        } else {
            throw new CloudRuntimeException("Could not find the primary storage of the snapshot");
        }
    }

    protected void deleteDiskSnapshot(VMSnapshot vmSnapshot) {
        //we can find disks snapshots related to vmSnapshot in vm_snapshot_details table
        List<VMSnapshotDetailsVO> listSnapshots = vmSnapshotDetailsDao.listDetails(vmSnapshot.getId());
        for (VMSnapshotDetailsVO vmSnapshotDetailsVO : listSnapshots) {
            if (vmSnapshotDetailsVO.getName().equals(STORAGE_SNAPSHOT)) {
                SnapshotVO snapshot = snapshotDao.findById(Long.parseLong(vmSnapshotDetailsVO.getValue()));

                if (snapshot == null) {
                    throw new CloudRuntimeException("Could not find snapshot for VM snapshot");
                }
                deleteSnapshotByStrategy(snapshot);
                vmSnapshotDetailsDao.remove(vmSnapshotDetailsVO.getId());
            }
        }
    }

    protected void revertDiskSnapshot(VMSnapshot vmSnapshot) {
        List<VMSnapshotDetailsVO> listSnapshots = vmSnapshotDetailsDao.listDetails(vmSnapshot.getId());
        for (VMSnapshotDetailsVO vmSnapshotDetailsVO : listSnapshots) {
            if (vmSnapshotDetailsVO.getName().equals(STORAGE_SNAPSHOT)) {
                SnapshotVO snapshotVO = snapshotDao.findById(Long.parseLong(vmSnapshotDetailsVO.getValue()));
                Snapshot snapshot= snapshotApiService.revertSnapshot(snapshotVO.getId());
                if (snapshot == null) {
                    throw new CloudRuntimeException( "Failed to revert snapshot");
                }
            }
        }
    }

    protected SnapshotInfo createDiskSnapshot(VMSnapshot vmSnapshot, List<SnapshotInfo> forRollback, VolumeInfo vol) {
        String snapshotName = vmSnapshot.getId() + "_" + vol.getUuid();
        SnapshotVO snapshot = new SnapshotVO(vol.getDataCenterId(), vol.getAccountId(), vol.getDomainId(), vol.getId(), vol.getDiskOfferingId(),
                              snapshotName, (short) SnapshotVO.MANUAL_POLICY_ID,  "MANUAL",  vol.getSize(), vol.getMinIops(),  vol.getMaxIops(), Hypervisor.HypervisorType.KVM, null);
        snapshot.setState(Snapshot.State.AllocatedKVM);
        snapshot = snapshotDao.persist(snapshot);
        vol.addPayload(setPayload(vol, snapshot));
        SnapshotInfo snapshotInfo = snapshotDataFactory.getSnapshot(snapshot.getId(), vol.getDataStore());
        snapshotInfo.addPayload(vol.getpayload());
        SnapshotStrategy snapshotStrategy = storageStrategyFactory.getSnapshotStrategy(snapshotInfo, SnapshotOperation.TAKE);
        if (snapshotStrategy == null) {
            throw new CloudRuntimeException("Could not find strategy for snapshot uuid:" + snapshotInfo.getUuid());
        }
        snapshotInfo = snapshotStrategy.takeSnapshot(snapshotInfo);
        if (snapshotInfo == null) {
            throw new CloudRuntimeException("Failed to create snapshot");
        } else {
          forRollback.add(snapshotInfo);
        }
        vmSnapshotDetailsDao.persist(new VMSnapshotDetailsVO(vmSnapshot.getId(), STORAGE_SNAPSHOT, String.valueOf(snapshot.getId()), true));
        return snapshotInfo;
    }

    protected CreateSnapshotPayload setPayload(VolumeInfo vol, SnapshotVO snapshotCreate) {
        CreateSnapshotPayload payload = new CreateSnapshotPayload();
        payload.setSnapshotId(snapshotCreate.getId());
        payload.setSnapshotPolicyId(SnapshotVO.MANUAL_POLICY_ID);
        payload.setLocationType(snapshotCreate.getLocationType());
        payload.setAccount(accountService.getAccount(vol.getAccountId()));
        payload.setAsyncBackup(false);
        payload.setQuiescevm(false);
        return payload;
    }
}
