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
package org.apache.cloudstack.network;

import com.cloud.exception.NetworkRuleConflictException;
import com.cloud.network.Network;
import com.cloud.network.rules.FirewallRule;
import com.cloud.network.vpc.Vpc;
import com.cloud.network.vpc.VpcOffering;
import com.cloud.offering.NetworkOffering;
import com.cloud.utils.Pair;
import com.cloud.utils.component.PluggableService;

import org.apache.cloudstack.api.command.admin.network.CreateBgpPeerCmd;
import org.apache.cloudstack.api.command.admin.network.CreateIpv4GuestSubnetCmd;
import org.apache.cloudstack.api.command.admin.network.CreateIpv4SubnetForGuestNetworkCmd;
import org.apache.cloudstack.api.command.admin.network.DedicateBgpPeerCmd;
import org.apache.cloudstack.api.command.admin.network.DedicateIpv4GuestSubnetCmd;
import org.apache.cloudstack.api.command.admin.network.DeleteBgpPeerCmd;
import org.apache.cloudstack.api.command.admin.network.DeleteIpv4GuestSubnetCmd;
import org.apache.cloudstack.api.command.admin.network.DeleteIpv4SubnetForGuestNetworkCmd;
import org.apache.cloudstack.api.command.admin.network.ListBgpPeersCmd;
import org.apache.cloudstack.api.command.admin.network.ListIpv4GuestSubnetsCmd;
import org.apache.cloudstack.api.command.admin.network.ListIpv4SubnetsForGuestNetworkCmd;
import org.apache.cloudstack.api.command.admin.network.ReleaseDedicatedBgpPeerCmd;
import org.apache.cloudstack.api.command.admin.network.ReleaseDedicatedIpv4GuestSubnetCmd;
import org.apache.cloudstack.api.command.admin.network.UpdateBgpPeerCmd;
import org.apache.cloudstack.api.command.admin.network.UpdateIpv4GuestSubnetCmd;
import org.apache.cloudstack.api.command.user.network.routing.CreateRoutingFirewallRuleCmd;
import org.apache.cloudstack.api.command.user.network.routing.ListRoutingFirewallRulesCmd;
import org.apache.cloudstack.api.command.user.network.routing.UpdateRoutingFirewallRuleCmd;
import org.apache.cloudstack.api.response.BgpPeerResponse;
import org.apache.cloudstack.api.response.DataCenterIpv4SubnetResponse;
import org.apache.cloudstack.api.response.Ipv4SubnetForGuestNetworkResponse;
import org.apache.cloudstack.datacenter.DataCenterIpv4GuestSubnet;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;

import java.util.List;

public interface RoutedIpv4Manager extends PluggableService, Configurable {

    ConfigKey<Integer> RoutedNetworkIPv4MaxCidrSize = new ConfigKey<>(ConfigKey.CATEGORY_NETWORK, Integer.class,
            "routed.network.ipv4.max.cidr.size", "30", "The maximum value of the cidr size for isolated networks in ROUTED mode",
            true, ConfigKey.Scope.Account);

    ConfigKey<Integer> RoutedNetworkIPv4MinCidrSize = new ConfigKey<>(ConfigKey.CATEGORY_NETWORK, Integer.class,
            "routed.network.ipv4.min.cidr.size", "24", "The minimum value of the cidr size for isolated networks in ROUTED mode",
            true, ConfigKey.Scope.Account);

    ConfigKey<Integer> RoutedVpcIPv4MaxCidrSize = new ConfigKey<>(ConfigKey.CATEGORY_NETWORK, Integer.class,
            "routed.ipv4.vpc.max.cidr.size", "29", "The maximum value of the cidr size for VPC in ROUTED mode",
            true, ConfigKey.Scope.Account);

    ConfigKey<Integer> RoutedVpcIPv4MinCidrSize = new ConfigKey<>(ConfigKey.CATEGORY_NETWORK, Integer.class,
            "routed.ipv4.vpc.min.cidr.size", "23", "The minimum value of the cidr size for VPC in ROUTED mode",
            true, ConfigKey.Scope.Account);

    ConfigKey<Boolean> RoutedIPv4NetworkCidrAutoAllocationEnabled = new ConfigKey<>(ConfigKey.CATEGORY_ADVANCED, Boolean.class,
            "routed.ipv4.network.cidr.auto.allocation.enabled",
            "true",
            "Indicates whether the auto-allocation of network CIDR for routed network is enabled or not.",
            true,
            ConfigKey.Scope.Account);

    // Methods for DataCenterIpv4GuestSubnet APIs
    DataCenterIpv4GuestSubnet createDataCenterIpv4GuestSubnet(CreateIpv4GuestSubnetCmd createIpv4GuestSubnetCmd);

    DataCenterIpv4SubnetResponse createDataCenterIpv4SubnetResponse(DataCenterIpv4GuestSubnet result);

    boolean deleteDataCenterIpv4GuestSubnet(DeleteIpv4GuestSubnetCmd deleteIpv4GuestSubnetCmd);

    DataCenterIpv4GuestSubnet updateDataCenterIpv4GuestSubnet(UpdateIpv4GuestSubnetCmd updateIpv4GuestSubnetCmd);

    List<? extends DataCenterIpv4GuestSubnet> listDataCenterIpv4GuestSubnets(ListIpv4GuestSubnetsCmd listIpv4GuestSubnetsCmd);

    DataCenterIpv4GuestSubnet dedicateDataCenterIpv4GuestSubnet(DedicateIpv4GuestSubnetCmd dedicateIpv4GuestSubnetCmd);

    DataCenterIpv4GuestSubnet releaseDedicatedDataCenterIpv4GuestSubnet(ReleaseDedicatedIpv4GuestSubnetCmd releaseDedicatedIpv4GuestSubnetCmd);

    // Methods for Ipv4SubnetForGuestNetwork APIs
    Ipv4GuestSubnetNetworkMap createIpv4SubnetForGuestNetwork(CreateIpv4SubnetForGuestNetworkCmd createIpv4SubnetForGuestNetworkCmd);

    boolean deleteIpv4SubnetForGuestNetwork(DeleteIpv4SubnetForGuestNetworkCmd deleteIpv4SubnetForGuestNetworkCmd);

    void releaseIpv4SubnetForGuestNetwork(long networkId);

    void releaseIpv4SubnetForVpc(long vpcId);

    List<? extends Ipv4GuestSubnetNetworkMap> listIpv4GuestSubnetsForGuestNetwork(ListIpv4SubnetsForGuestNetworkCmd listIpv4SubnetsForGuestNetworkCmd);

    Ipv4SubnetForGuestNetworkResponse createIpv4SubnetForGuestNetworkResponse(Ipv4GuestSubnetNetworkMap subnet);

    // Methods for internal calls
    void getOrCreateIpv4SubnetForGuestNetwork(Network network, String networkCidr);

    Ipv4GuestSubnetNetworkMap getOrCreateIpv4SubnetForGuestNetwork(Network network, Integer networkCidrSize);

    void getOrCreateIpv4SubnetForVpc(Vpc vpc, String networkCidr);

    Ipv4GuestSubnetNetworkMap getOrCreateIpv4SubnetForVpc(Vpc vpc, Integer vpcCidrSize);

    void assignIpv4SubnetToNetwork(String cidr, long networkId);

    void assignIpv4SubnetToVpc(String cidr, long vpcId);

    // Methods for Routing firewall rules
    FirewallRule createRoutingFirewallRule(CreateRoutingFirewallRuleCmd createRoutingFirewallRuleCmd) throws NetworkRuleConflictException;

    Pair<List<? extends FirewallRule>, Integer> listRoutingFirewallRules(ListRoutingFirewallRulesCmd listRoutingFirewallRulesCmd);

    FirewallRule updateRoutingFirewallRule(UpdateRoutingFirewallRuleCmd updateRoutingFirewallRuleCmd);

    boolean revokeRoutingFirewallRule(Long id);

    boolean applyRoutingFirewallRule(long id);

    boolean isVirtualRouterGateway(Network network);

    boolean isVirtualRouterGateway(NetworkOffering networkOffering);

    boolean isRoutedNetwork(Network network);

    boolean isDynamicRoutedNetwork(Network network);

    boolean isRoutedVpc(Vpc vpc);

    boolean isVpcVirtualRouterGateway(VpcOffering vpcOffering);

    BgpPeer createBgpPeer(CreateBgpPeerCmd createBgpPeerCmd);

    BgpPeerResponse createBgpPeerResponse(BgpPeer result);

    boolean deleteBgpPeer(DeleteBgpPeerCmd deleteBgpPeerCmd);

    BgpPeer updateBgpPeer(UpdateBgpPeerCmd updateBgpPeerCmd);

    BgpPeer dedicateBgpPeer(DedicateBgpPeerCmd dedicateBgpPeerCmd);

    BgpPeer releaseDedicatedBgpPeer(ReleaseDedicatedBgpPeerCmd releaseDedicatedBgpPeerCmd);

    List<? extends BgpPeer> listBgpPeers(ListBgpPeersCmd listBgpPeersCmd);
}
