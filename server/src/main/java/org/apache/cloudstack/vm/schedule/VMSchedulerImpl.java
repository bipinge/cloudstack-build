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

package org.apache.cloudstack.vm.schedule;

import com.cloud.api.ApiGsonHelper;
import com.cloud.event.ActionEventUtils;
import com.cloud.event.EventTypes;
import com.cloud.user.User;
import com.cloud.utils.DateUtil;
import com.cloud.utils.component.ComponentContext;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.db.GlobalLock;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachineManager;
import com.google.common.primitives.Longs;
import org.apache.cloudstack.api.ApiCommandResourceType;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.command.user.vm.RebootVMCmd;
import org.apache.cloudstack.api.command.user.vm.StartVMCmd;
import org.apache.cloudstack.api.command.user.vm.StopVMCmd;
import org.apache.cloudstack.framework.jobs.AsyncJobDispatcher;
import org.apache.cloudstack.framework.jobs.AsyncJobManager;
import org.apache.cloudstack.framework.jobs.impl.AsyncJobVO;
import org.apache.cloudstack.managed.context.ManagedContextTimerTask;
import org.apache.cloudstack.vm.schedule.dao.VMScheduleDao;
import org.apache.cloudstack.vm.schedule.dao.VMScheduledJobDao;
import org.apache.commons.lang.time.DateUtils;
import org.apache.log4j.Logger;
import org.springframework.scheduling.support.CronExpression;

import javax.inject.Inject;
import javax.persistence.EntityExistsException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class VMSchedulerImpl extends ManagerBase implements VMScheduler {
    private static Logger LOGGER = Logger.getLogger(VMSchedulerImpl.class);
    @Inject
    private VMScheduledJobDao vmScheduledJobDao;
    @Inject
    private VMScheduleDao vmScheduleDao;
    @Inject
    private VirtualMachineManager virtualMachineManager;
    @Inject
    private AsyncJobManager asyncJobManager;
    private AsyncJobDispatcher asyncJobDispatcher;
    private Timer vmSchedulerTimer;
    private Date currentTimestamp;

    public AsyncJobDispatcher getAsyncJobDispatcher() {
        return asyncJobDispatcher;
    }

    public void setAsyncJobDispatcher(final AsyncJobDispatcher dispatcher) {
        asyncJobDispatcher = dispatcher;
    }

    @Override
    public void removeScheduledJobs(List<Long> vmScheduleIds) {
        if (vmScheduleIds == null || vmScheduleIds.isEmpty()) {
            LOGGER.debug("Removed 0 scheduled jobs");
            return;
        }
        int rowsRemoved = vmScheduledJobDao.expungeJobsForSchedules(vmScheduleIds, null);
        LOGGER.debug(String.format("Removed %s VM scheduled jobs", rowsRemoved));
    }

    @Override
    public void updateScheduledJob(VMScheduleVO vmSchedule) {
        removeScheduledJobs(Longs.asList(vmSchedule.getId()));
        scheduleNextJob(vmSchedule);
    }

    public Date scheduleNextJob(Long vmScheduleId) {
        VMScheduleVO vmSchedule = vmScheduleDao.findById(vmScheduleId);
        if (vmSchedule != null) {
            return scheduleNextJob(vmSchedule);
        }
        LOGGER.debug(String.format("VM Schedule [id=%s] is removed. Not scheduling next job.", vmScheduleId));
        return null;
    }

    @Override
    public Date scheduleNextJob(VMScheduleVO vmSchedule) {
        if (!vmSchedule.getEnabled()) {
            LOGGER.debug(String.format("VM Schedule [id=%s] for VM [id=%s] is disabled. Not scheduling next job.", vmSchedule.getUuid(), vmSchedule.getVmId()));
            return null;
        }

        CronExpression cron = DateUtil.parseSchedule(vmSchedule.getSchedule());
        Date startDate = vmSchedule.getStartDate();
        Date endDate = vmSchedule.getEndDate();
        VirtualMachine vm = virtualMachineManager.findById(vmSchedule.getVmId());

        Date now = new Date();
        if (endDate != null && now.compareTo(endDate) > 0) {
            LOGGER.info(String.format("End time is less than current time. Disabling VM schedule [id=%s] for VM [id=%s].", vmSchedule.getUuid(), vmSchedule.getVmId()));
            vmSchedule.setEnabled(false);
            vmScheduleDao.persist(vmSchedule);
            return null;
        }

        ZonedDateTime ts = null;
        ZoneId tz = vmSchedule.getTimeZoneId();
        if (startDate.after(now)) {
            ts = cron.next(ZonedDateTime.ofInstant(startDate.toInstant(), tz));
        } else {
            ts = cron.next(ZonedDateTime.ofInstant(now.toInstant(), tz));
        }
        Date scheduledDateTime = Date.from(ts.toInstant());
        VMScheduledJobVO scheduledJob = new VMScheduledJobVO(vmSchedule.getVmId(), vmSchedule.getId(), vmSchedule.getAction(), scheduledDateTime);
        try {
            vmScheduledJobDao.persist(scheduledJob);
            ActionEventUtils.onScheduledActionEvent(User.UID_SYSTEM, vm.getAccountId(), EventTypes.EVENT_VM_SCHEDULE_SCHEDULED,
                    String.format("Scheduled action (%s) [vmId: %s scheduleId: %s]  at %s", vmSchedule.getAction(), vm.getUuid(), vmSchedule.getUuid(), scheduledDateTime),
                    vm.getId(), ApiCommandResourceType.VirtualMachine.toString(), true, 0);
        } catch (EntityExistsException exception) {
            LOGGER.debug("Job is already scheduled.");
        }
        return scheduledDateTime;
    }

    @Override
    public boolean start() {

        // Adding 1 minute to currentTimestamp to ensure that
        // jobs which were to be run at current time, doesn't cause issues
        currentTimestamp = DateUtils.addMinutes(new Date(), 1);

        scheduleNextJobs();

        final TimerTask schedulerPollTask = new ManagedContextTimerTask() {
            @Override
            protected void runInContext() {
                try {
                    poll(new Date());
                } catch (final Throwable t) {
                    LOGGER.warn("Catch throwable in VM scheduler ", t);
                }
            }
        };

        vmSchedulerTimer = new Timer("VMSchedulerPollTask");
        /*
         TODO: Check if we should use schedule or scheduleAtFixedRate here?
          scheduleAtFixedRate can result in issues if the task is taking more than 1 minute
          schedule might result in skipping of some scheduled jobs
        */
        vmSchedulerTimer.schedule(schedulerPollTask, 5000L, 60 * 1000L);
        return true;
    }

    @Override
    public void poll(Date timestamp) {
        currentTimestamp = DateUtils.round(timestamp, Calendar.MINUTE);
        String displayTime = DateUtil.displayDateInTimezone(DateUtil.GMT_TIMEZONE, currentTimestamp);
        LOGGER.debug(String.format("VM scheduler.poll is being called at %s", displayTime));

        GlobalLock scanLock = GlobalLock.getInternLock("vmScheduler.poll");
        try {
            if (scanLock.lock(30)) {
                try {
                    scheduleNextJobs();
                } finally {
                    scanLock.unlock();
                }
            }
        } finally {
            scanLock.releaseRef();
        }

        scanLock = GlobalLock.getInternLock("vmScheduler.poll");
        try {
            if (scanLock.lock(30)) {
                try {
                    startJobs(); // Create async job and update scheduled job
                } finally {
                    scanLock.unlock();
                }
            }
        } finally {
            scanLock.releaseRef();
        }

        try {
            cleanupVMScheduledJobs();
        } catch (Exception e) {
            LOGGER.warn("Error in cleaning up vm scheduled jobs", e);
        }
    }

    /**
     * Check status and schedule next job
     */
    private void scheduleNextJobs() {
        for (final VMScheduleVO schedule : vmScheduleDao.listAllActiveSchedules()) {
            scheduleNextJob(schedule);
        }
    }

    /**
     * Delete scheduled jobs before vm.scheduler.expire.interval days
     */
    private void cleanupVMScheduledJobs() {
        Date deleteBeforeDate = DateUtils.addDays(currentTimestamp, -1 * VMScheduledJobExpireInterval.value());

        SearchBuilder<VMScheduledJobVO> sb = vmScheduledJobDao.createSearchBuilder();
        sb.and("scheduled_timestamp", sb.entity().getScheduledTime(), SearchCriteria.Op.LT);
        SearchCriteria<VMScheduledJobVO> sc = sb.create();
        sc.setParameters("scheduled_timestamp", deleteBeforeDate);

        int rowsRemoved = vmScheduledJobDao.expunge(sc);
        LOGGER.info(String.format("Cleaned up %d VM scheduled job entries", rowsRemoved));
    }

    private void executeJobs(Map<Long, VMScheduledJobVO> jobsToExecute) {
        String displayTime = DateUtil.displayDateInTimezone(DateUtil.GMT_TIMEZONE, currentTimestamp);

        for (Map.Entry<Long, VMScheduledJobVO> entry : jobsToExecute.entrySet()) {
            VMScheduledJobVO vmScheduledJob = entry.getValue();
            VirtualMachine vm = virtualMachineManager.findById(vmScheduledJob.getVmId());

            VMScheduledJobVO tmpVMScheduleJob = null;
            try {
                if (LOGGER.isDebugEnabled()) {
                    final Date scheduledTimestamp = vmScheduledJob.getScheduledTime();
                    displayTime = DateUtil.displayDateInTimezone(DateUtil.GMT_TIMEZONE, scheduledTimestamp);
                    LOGGER.debug(String.format("Executing %s for VM id %d for schedule id: %d at %s", vmScheduledJob.getAction(), vmScheduledJob.getVmId(), vmScheduledJob.getVmScheduleId(), displayTime));
                }

                tmpVMScheduleJob = vmScheduledJobDao.acquireInLockTable(vmScheduledJob.getId());
                Long jobId = processJob(vmScheduledJob, vm);
                if (jobId != null) {
                    tmpVMScheduleJob.setAsyncJobId(jobId);
                    vmScheduledJobDao.update(vmScheduledJob.getId(), tmpVMScheduleJob);
                }
            } catch (final Exception e) {
                LOGGER.warn(String.format("Executing scheduled job id: %s failed due to %s", vmScheduledJob.getId(), e));
            } finally {
                if (tmpVMScheduleJob != null) {
                    vmScheduledJobDao.releaseFromLockTable(vmScheduledJob.getId());
                }
            }
        }
    }

    private Long processJob(VMScheduledJobVO vmScheduledJob, VirtualMachine vm) {
        if (!Arrays.asList(VirtualMachine.State.Running, VirtualMachine.State.Stopped).contains(vm.getState())) {
            ActionEventUtils.onCompletedActionEvent(User.UID_SYSTEM, vm.getAccountId(), null,
                    EventTypes.EVENT_VM_SCHEDULE_SKIPPED, true,
                    String.format("Skipping action (%s) for [vmId:%s scheduleId: %s] because VM is invalid state: %s", vmScheduledJob.getAction(), vm.getUuid(), vmScheduledJob.getVmScheduleId(), vm.getState()),
                    vm.getId(), ApiCommandResourceType.VirtualMachine.toString(), 0);
            return null;
        }

        final Long eventId = ActionEventUtils.onCompletedActionEvent(User.UID_SYSTEM, vm.getAccountId(), null,
                EventTypes.EVENT_VM_SCHEDULE_EXECUTE, true,
                String.format("Executing action (%s) for VM Id:%s", vmScheduledJob.getAction(), vm.getUuid()),
                vm.getId(), ApiCommandResourceType.VirtualMachine.toString(), 0);

        if (vm.getState() == VirtualMachine.State.Running) {
                switch (vmScheduledJob.getAction()) {
                    case STOP:
                        return executeStopVMJob(vm, eventId, false);
                    case FORCE_STOP:
                        return executeStopVMJob(vm, eventId, true);
                    case REBOOT:
                        return executeRebootVMJob(vm, eventId, false);
                    case FORCE_REBOOT:
                        return executeRebootVMJob(vm, eventId, true);
                }
        } else if (vm.getState() == VirtualMachine.State.Stopped && vmScheduledJob.getAction() == VMSchedule.Action.START) {
                return executeStartVMJob(vm, eventId);
        }

        String logMessage = String.format("Skipping action (%s) for [vmId:%s scheduleId: %s] because VM is in state: %s",
                vmScheduledJob.getAction(), vm.getUuid(), vmScheduledJob.getVmScheduleId(), vm.getState());
        ActionEventUtils.onCompletedActionEvent(User.UID_SYSTEM, vm.getAccountId(), null,
                EventTypes.EVENT_VM_SCHEDULE_SKIPPED, true,
                logMessage,
                vm.getId(), ApiCommandResourceType.VirtualMachine.toString(), 0);
        LOGGER.warn(logMessage);
        return null;
    }

    private void skipJobs(Map<Long, VMScheduledJobVO> jobsToExecute, Map<Long, List<VMScheduledJobVO>> jobsNotToExecute) {
        for (Map.Entry<Long, List<VMScheduledJobVO>> entry : jobsNotToExecute.entrySet()) {
            Long vmId = entry.getKey();
            List<VMScheduledJobVO> skippedVmScheduledJobVOS = entry.getValue();
            VirtualMachine vm = virtualMachineManager.findById(vmId);
            for (final VMScheduledJobVO skippedVmScheduledJobVO : skippedVmScheduledJobVOS) {
                VMScheduledJobVO scheduledJob = jobsToExecute.get(vmId);
                LOGGER.info(String.format("Skipping scheduled job [id: %s, vmId: %s] because of conflict with another scheduled job [id: %s]", skippedVmScheduledJobVO.getId(), vmId, scheduledJob.getUuid()));
                ActionEventUtils.onCompletedActionEvent(User.UID_SYSTEM, vm.getAccountId(), null,
                        EventTypes.EVENT_VM_SCHEDULE_SKIPPED, true,
                        String.format("Skipping scheduled job [id: %s, vmId: %s] because of conflict with another scheduled job [id: %s]", skippedVmScheduledJobVO.getId(), vmId, scheduledJob.getUuid()),
                        vm.getId(), ApiCommandResourceType.VirtualMachine.toString(), 0);
            }
        }
    }

    /**
     * Create async jobs for VM scheduled jobs
     */
    private void startJobs() {
        String displayTime = DateUtil.displayDateInTimezone(DateUtil.GMT_TIMEZONE, currentTimestamp);

        final List<VMScheduledJobVO> vmScheduledJobs = vmScheduledJobDao.listJobsToStart(currentTimestamp);
        LOGGER.debug(String.format("Got %d scheduled jobs to be executed at %s", vmScheduledJobs.size(), displayTime));

        Map<Long, VMScheduledJobVO> jobsToExecute = new HashMap<>();
        Map<Long, List<VMScheduledJobVO>> jobsNotToExecute = new HashMap<>();
        for (final VMScheduledJobVO vmScheduledJobVO : vmScheduledJobs) {
            long vmId = vmScheduledJobVO.getVmId();
            if (jobsToExecute.get(vmId) == null) {
                jobsToExecute.put(vmId, vmScheduledJobVO);
            } else {
                jobsNotToExecute.computeIfAbsent(vmId, k -> new ArrayList<>()).add(vmScheduledJobVO);
            }
        }

        executeJobs(jobsToExecute);
        skipJobs(jobsToExecute, jobsNotToExecute);
    }

    private long executeStartVMJob(VirtualMachine vm, long eventId) {
        final Map<String, String> params = new HashMap<>();
        params.put(ApiConstants.ID, String.valueOf(vm.getId()));
        params.put("ctxUserId", "1");
        params.put("ctxAccountId", String.valueOf(vm.getAccountId()));
        params.put("ctxStartEventId", String.valueOf(eventId));

        final StartVMCmd cmd = new StartVMCmd();
        ComponentContext.inject(cmd);

        AsyncJobVO job = new AsyncJobVO("", User.UID_SYSTEM, vm.getAccountId(), StartVMCmd.class.getName(), ApiGsonHelper.getBuilder().create().toJson(params), vm.getId(), cmd.getApiResourceType() != null ? cmd.getApiResourceType().toString() : null, null);
        job.setDispatcher(asyncJobDispatcher.getName());

        return asyncJobManager.submitAsyncJob(job);
    }

    private long executeStopVMJob(VirtualMachine vm, long eventId, boolean isForced) {
        final Map<String, String> params = new HashMap<>();
        params.put(ApiConstants.ID, String.valueOf(vm.getId()));
        params.put(ApiConstants.CTX_USER_ID, "1");
        params.put(ApiConstants.CTX_ACCOUNT_ID, String.valueOf(vm.getAccountId()));
        params.put(ApiConstants.CTX_START_EVENT_ID, String.valueOf(eventId));
        params.put(ApiConstants.FORCED, String.valueOf(isForced));

        final StopVMCmd cmd = new StopVMCmd();
        ComponentContext.inject(cmd);

        AsyncJobVO job = new AsyncJobVO("", User.UID_SYSTEM, vm.getAccountId(), StopVMCmd.class.getName(), ApiGsonHelper.getBuilder().create().toJson(params), vm.getId(), cmd.getApiResourceType() != null ? cmd.getApiResourceType().toString() : null, null);
        job.setDispatcher(asyncJobDispatcher.getName());

        return asyncJobManager.submitAsyncJob(job);
    }

    private long executeRebootVMJob(VirtualMachine vm, long eventId, boolean isForced) {
        final Map<String, String> params = new HashMap<>();
        params.put(ApiConstants.ID, String.valueOf(vm.getId()));
        params.put(ApiConstants.CTX_USER_ID, "1");
        params.put(ApiConstants.CTX_ACCOUNT_ID, String.valueOf(vm.getAccountId()));
        params.put(ApiConstants.CTX_START_EVENT_ID, String.valueOf(eventId));
        params.put(ApiConstants.FORCED, String.valueOf(isForced));

        final RebootVMCmd cmd = new RebootVMCmd();
        ComponentContext.inject(cmd);

        AsyncJobVO job = new AsyncJobVO("", User.UID_SYSTEM, vm.getAccountId(), RebootVMCmd.class.getName(), ApiGsonHelper.getBuilder().create().toJson(params), vm.getId(), cmd.getApiResourceType() != null ? cmd.getApiResourceType().toString() : null, null);
        job.setDispatcher(asyncJobDispatcher.getName());

        return asyncJobManager.submitAsyncJob(job);
    }
}
