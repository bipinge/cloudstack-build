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

import com.cloud.hypervisor.Hypervisor;
import com.cloud.utils.component.PluggableService;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;
import static com.cloud.hypervisor.Hypervisor.HypervisorType.KVM;
import static com.cloud.hypervisor.Hypervisor.HypervisorType.VMware;

public interface UnmanagedVMsManager extends VmImportService, UnmanageVMService, PluggableService, Configurable {

    ConfigKey<Boolean> UnmanageVMPreserveNic = new ConfigKey<>("Advanced", Boolean.class, "unmanage.vm.preserve.nics", "false",
            "If set to true, do not remove VM nics (and its MAC addresses) when unmanaging a VM, leaving them allocated but not reserved. " +
                    "If set to false, nics are removed and MAC addresses can be reassigned", true, ConfigKey.Scope.Zone);

    ConfigKey<Integer> RemoteKvmInstanceDisksCopyTimeout = new ConfigKey<>(Integer.class,
            "remote.kvm.instance.disks.copy.timeout",
            "Advanced",
            "30",
            "Timeout (in mins) to prepare and copy the disks of remote KVM instance while importing the instance from an external host",
            true,
            ConfigKey.Scope.Global,
            null);

    static boolean isSupported(Hypervisor.HypervisorType hypervisorType) {
        return hypervisorType == VMware || hypervisorType == KVM;
    }
}
