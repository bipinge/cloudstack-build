//
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

package com.cloud.agent.api.routing;

import com.cloud.agent.api.to.LoadBalancerConfigTO;
import com.cloud.agent.api.to.LoadBalancerTO;
import com.cloud.agent.api.to.NicTO;

import org.apache.cloudstack.network.lb.LoadBalancerConfig;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * LoadBalancerConfigCommand sends the load balancer configuration
 */
public class LoadBalancerConfigCommand extends NetworkElementCommand {
    LoadBalancerTO[] loadBalancers;
    LoadBalancerConfigTO[] networkLbConfigs;
    public String lbStatsVisibility = "guest-network";
    public String lbStatsPublicIP; /* load balancer listen on this ips for stats */
    public String lbStatsPrivateIP; /* load balancer listen on this ips for stats */
    public String lbStatsGuestIP; /* load balancer listen on this ips for stats */
    public String lbStatsPort = "8081"; /*load balancer listen on this port for stats */
    public String lbStatsSrcCidrs = "0/0"; /* TODO : currently there is no filtering based on the source ip */
    public String lbStatsAuth = "admin1:AdMiN123";
    public String lbStatsUri = "/admin?stats";
    public String maxconn = "";
    public String lbProtocol;
    public String lbSslConfiguration = "";
    public boolean keepAliveEnabled = false;
    NicTO nic;
    Long vpcId;
    Boolean isTransparent = false;
    String networkCidr;

    protected LoadBalancerConfigCommand() {
    }

    public LoadBalancerConfigCommand(LoadBalancerTO[] loadBalancers, Long vpcId) {
        this.loadBalancers = loadBalancers;
        this.vpcId = vpcId;
    }

    public LoadBalancerConfigCommand(LoadBalancerTO[] loadBalancers, String publicIp, String guestIp, String privateIp, NicTO nic, Long vpcId, String maxconn,
            boolean keepAliveEnabled) {
        this.loadBalancers = loadBalancers;
        this.lbStatsPublicIP = publicIp;
        this.lbStatsPrivateIP = privateIp;
        this.lbStatsGuestIP = guestIp;
        this.nic = nic;
        this.vpcId = vpcId;
        this.maxconn = maxconn;
        this.keepAliveEnabled = keepAliveEnabled;
    }

    public NicTO getNic() {
        return nic;
    }

    public LoadBalancerTO[] getLoadBalancers() {
        return loadBalancers;
    }

    public Long getVpcId() {
        return vpcId;
    }

    public LoadBalancerConfigTO[] getNetworkLbConfigs() {
        return this.networkLbConfigs;
    }

    public void setNetworkLbConfigs(List<? extends LoadBalancerConfig> networkLbConfigs) {
        if (CollectionUtils.isEmpty(networkLbConfigs)) {
            this.networkLbConfigs = new LoadBalancerConfigTO[0];
            return;
        }
        this.networkLbConfigs = new LoadBalancerConfigTO[networkLbConfigs.size()];
        int i = 0;
        for (LoadBalancerConfig lbConfig : networkLbConfigs) {
            this.networkLbConfigs[i++] = new LoadBalancerConfigTO(lbConfig);
        }
    }

    public void setIsTransparent(final Boolean isTransparent) {
        this.isTransparent = isTransparent;
    }

    public Boolean isTransparent() {
        return isTransparent;
    }

    public void setNetworkCidr(String networkCidr) {
        this.networkCidr = networkCidr;
    }

    public String getNetworkCidr() {
        return networkCidr;
    }
}
