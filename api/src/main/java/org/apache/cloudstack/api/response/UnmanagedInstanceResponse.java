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

package org.apache.cloudstack.api.response;

import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseResponse;
import org.apache.cloudstack.api.EntityReference;
import org.apache.cloudstack.vm.UnmanagedInstanceTO;

import com.cloud.serializer.Param;
import com.google.gson.annotations.SerializedName;

@EntityReference(value = UnmanagedInstanceTO.class)
public class UnmanagedInstanceResponse extends BaseResponse {

    @SerializedName(ApiConstants.NAME)
    @Param(description = "The name of the Instance")
    private String name;

    @SerializedName(ApiConstants.CLUSTER_ID)
    @Param(description = "The ID of the cluster to which Instance belongs")
    private String clusterId;

    @SerializedName(ApiConstants.HOST_ID)
    @Param(description = "The ID of the host to which Instance belongs")
    private String hostId;

    @SerializedName(ApiConstants.HOST_NAME)
    @Param(description = "The name of the host to which Instance belongs")
    private String hostName;

    @SerializedName(ApiConstants.POWER_STATE)
    @Param(description = "The power state of the Instance")
    private String  powerState;

    @SerializedName(ApiConstants.CPU_NUMBER)
    @Param(description = "The CPU cores of the Instance")
    private Integer cpuCores;

    @SerializedName(ApiConstants.CPU_CORE_PER_SOCKET)
    @Param(description = "The CPU cores per socket for the Instance. VMware specific")
    private Integer cpuCoresPerSocket;

    @SerializedName(ApiConstants.CPU_SPEED)
    @Param(description = "The CPU speed of the Instance")
    private Integer cpuSpeed;

    @SerializedName(ApiConstants.MEMORY)
    @Param(description = "The memory of the Instance in MB")
    private Integer memory;

    @SerializedName(ApiConstants.OS_ID)
    @Param(description = "The operating system ID of the Instance")
    private String operatingSystemId;

    @SerializedName(ApiConstants.OS_DISPLAY_NAME)
    @Param(description = "The operating system of the Instance")
    private String operatingSystem;

    @SerializedName(ApiConstants.DISK)
    @Param(description = "The list of disks associated with the Instance", responseObject = UnmanagedInstanceDiskResponse.class)
    private Set<UnmanagedInstanceDiskResponse> disks;

    @SerializedName(ApiConstants.NIC)
    @Param(description = "The list of NICs associated with the Instance", responseObject = NicResponse.class)
    private Set<NicResponse> nics;

    public UnmanagedInstanceResponse() {
        disks = new LinkedHashSet<UnmanagedInstanceDiskResponse>();
        nics = new LinkedHashSet<NicResponse>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getClusterId() {
        return clusterId;
    }

    public void setClusterId(String clusterId) {
        this.clusterId = clusterId;
    }

    public String getHostId() {
        return hostId;
    }

    public void setHostId(String hostId) {
        this.hostId = hostId;
    }

    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public String getPowerState() {
        return powerState;
    }

    public void setPowerState(String powerState) {
        this.powerState = powerState;
    }

    public Integer getCpuCores() {
        return cpuCores;
    }

    public void setCpuCores(Integer cpuCores) {
        this.cpuCores = cpuCores;
    }

    public Integer getCpuCoresPerSocket() {
        return cpuCoresPerSocket;
    }

    public void setCpuCoresPerSocket(Integer cpuCoresPerSocket) {
        this.cpuCoresPerSocket = cpuCoresPerSocket;
    }

    public Integer getCpuSpeed() {
        return cpuSpeed;
    }

    public void setCpuSpeed(Integer cpuSpeed) {
        this.cpuSpeed = cpuSpeed;
    }

    public Integer getMemory() {
        return memory;
    }

    public void setMemory(Integer memory) {
        this.memory = memory;
    }

    public String getOperatingSystemId() {
        return operatingSystemId;
    }

    public void setOperatingSystemId(String operatingSystemId) {
        this.operatingSystemId = operatingSystemId;
    }

    public String getOperatingSystem() {
        return operatingSystem;
    }

    public void setOperatingSystem(String operatingSystem) {
        this.operatingSystem = operatingSystem;
    }

    public Set<UnmanagedInstanceDiskResponse> getDisks() {
        return disks;
    }

    public void setDisks(Set<UnmanagedInstanceDiskResponse> disks) {
        this.disks = disks;
    }

    public void addDisk(UnmanagedInstanceDiskResponse disk) {
        this.disks.add(disk);
    }

    public Set<NicResponse> getNics() {
        return nics;
    }

    public void setNics(Set<NicResponse> nics) {
        this.nics = nics;
    }

    public void addNic(NicResponse nic) {
        this.nics.add(nic);
    }
}
