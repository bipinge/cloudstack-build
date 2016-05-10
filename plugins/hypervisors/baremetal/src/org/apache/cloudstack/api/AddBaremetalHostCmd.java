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
//
// Automatically generated by addcopyright.py at 01/29/2013
package org.apache.cloudstack.api;

import org.apache.cloudstack.api.command.admin.host.AddHostCmd;
import org.apache.cloudstack.api.response.HostResponse;

import com.cloud.baremetal.manager.BareMetalDiscoverer;
import com.cloud.baremetal.networkservice.BaremetalSwitchResponse;

@APICommand(name = "addBaremetalHost", description = "add a baremetal host", responseObject = HostResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class AddBaremetalHostCmd extends AddHostCmd {

    @Parameter(name = ApiConstants.IP_ADDRESS, type = CommandType.STRING, description = "ip address intentionally allocated to this host after provisioning")
    private String vmIpAddress;

    @Parameter(name = "switchid", type = CommandType.UUID, entityType = BaremetalSwitchResponse.class, description = "the ID of the baremetal switch to which the host is connected")
    private Long switchId;

    @Parameter(name = "switchport", type = CommandType.STRING, description = "the port to which the host is connected on the baremetal switch")
    private String switchPort;

    public AddBaremetalHostCmd() {
    }

    @Override
    public void execute() {
        this.getFullUrlParams().put(ApiConstants.BAREMETAL_DISCOVER_NAME, BareMetalDiscoverer.class.getName());
        super.execute();
    }

    public String getVmIpAddress() {
        return vmIpAddress;
    }

    public void setVmIpAddress(String vmIpAddress) {
        this.vmIpAddress = vmIpAddress;
    }

    public Long getSwitchId() {
        return switchId;
    }

    public void setSwitchId(Long switchId) {
        this.switchId = switchId;
    }

    public String getSwitchPort() {
        return switchPort;
    }

    public void setSwitchPort(String switchPort) {
        this.switchPort = switchPort;
    }
}
