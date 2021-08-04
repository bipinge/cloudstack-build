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
package com.cloud.utils;

import com.cloud.utils.net.NetUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.List;

// TODO : will determine later
public class TungstenUtils {
    private static final String defaultVhostInterface = "vhost0";
    private static final String defaultForwardingMode = "l3";
    private static final String proxyVm = "ConsoleProxy";
    private static final String secstoreVm = "SecondaryStorageVm";
    private static final String userVm = "User";
    private static final String guestType = "Guest";
    private static final String publicType = "Public";
    private static final String managementType = "Management";
    private static final String sharedNetwork = "sharedNetwork";
    private static final String securityGroup = "securityGroup";

    public static final String INGRESS_RULE = "ingress";
    public static final String EGRESS_RULE = "egress";
    public static final String LOCAL = "local";

    public static final String NO_EXPORT = "no-export";
    public static final String NO_EXPORT_SUBCONFED = "no-export-subconfed";
    public static final String ACCEPT_OWN = "accept-own";
    public static final String NO_ADVERTISE = "no-advertise";
    public static final String NO_REORIGINATE = "no-reoriginate";

    public static final int MAX_CIDR = 32;
    public static final int MAX_IPV6_CIDR = 128;
    public static final int DNS_SERVICE_PORT = 53;
    public static final int NTP_SERVICE_PORT = 123;
    public static final int WEB_SERVICE_PORT = 8080;
    public static final String IPV4 = "IPv4";
    public static final String IPV6 = "IPv6";
    public static final String ALL_IP4_PREFIX = "0.0.0.0";
    public static final String ALL_IP6_PREFIX = "::";
    public static final String ANY = "any";
    public static final String DENY_ACTION = "deny";
    public static final String PASS_ACTION = "pass";
    public static final String ONE_WAY_DIRECTION = ">";
    public static final String TWO_WAY_DIRECTION = "<>";
    public static final String FABRIC_NETWORK_FQN = "default-domain:default-project:ip-fabric";
    public static final String DEFAULT_FABRIC_NAME = "ip-fabric";
    public static final String DEFAULT_PROJECT_FQN = "default-domain:default-project";

    public static String getTapName(final String macAddress) {
        return "tap" + macAddress.replace(":", "");
    }

    public static String getDefaultVhostInterface() {
        return defaultVhostInterface;
    }

    public static String getDefaultForwardingMode() {
        return defaultForwardingMode;
    }

    public static String getVmiName(String trafficType, String vmType, String vmName, long nicId) {
        if (nicId != 0 && trafficType == guestType)
            return "vmi" + trafficType + vmType + nicId;
        else
            return "vmi" + trafficType + vmType + vmName;
    }

    public static String getInstanceIpName(String trafficType, String vmType, String vmName, long nicId) {
        if (nicId != 0 && trafficType == getGuestType())
            return "instanceIp" + trafficType + vmType + nicId;
        else
            return "instanceIp" + trafficType + vmType + vmName;
    }

    public static String getV6InstanceIpName(String trafficType, String vmType, String vmName, long nicId) {
        if (nicId != 0 && trafficType == getGuestType())
            return "instanceV6Ip" + trafficType + vmType + nicId;
        else
            return "instanceV6Ip" + trafficType + vmType + vmName;
    }

    public static String getSecondaryInstanceIpName(long nicSecondaryIpId) {
        return "instanceIpSecondaryIp" + nicSecondaryIpId;
    }

    public static String getLogicalRouterName(long networkId) {
        return "logicalRouter" + networkId;
    }

    public static String getNetworkGatewayVmiName(long vnId) {
        return "internalGatewayVmi" + vnId;
    }

    public static String getNetworkGatewayIiName(long pvnId) {
        return "internalGatewayIi" + pvnId;
    }

    public static String getPublicNetworkName(long zoneId) {
        return "publicNetwork" + zoneId;
    }

    public static String getGuestNetworkName(String networkName) {
        return networkName + "-" + RandomStringUtils.random(6, true, true);
    }

    public static String getSharedNetworkName(long networkId) {
        return sharedNetwork + networkId;
    }

    public static String getManagementNetworkName(long mvnId) {
        return "managementNetwork" + mvnId;
    }

    public static String getControlNetworkName(long cvnId) {
        return "controlNetwork" + cvnId;
    }

    public static String getProxyVm() {
        return proxyVm;
    }

    public static String getSecstoreVm() {
        return secstoreVm;
    }

    public static String getUserVm() {
        return userVm;
    }

    public static String getGuestType() {
        return guestType;
    }

    public static String getPublicType() {
        return publicType;
    }

    public static String getManagementType() {
        return managementType;
    }

    public static String getVgwName(long zoneId) {
        return "vgw" + zoneId;
    }

    public static String getSgVgwName(long networkId) {
        return "sgvgw" + networkId;
    }

    public static String getVrfNetworkName(List<String> networkQualifiedName) {
        List<String> vrfList = new ArrayList<>(networkQualifiedName);
        vrfList.add(networkQualifiedName.get(networkQualifiedName.size() - 1));
        return StringUtils.join(vrfList, ":");
    }

    public static String getFloatingIpPoolName(long zoneId) {
        return "floating-ip-pool" + zoneId;
    }

    public static String getFloatingIpName(long nicId) {
        return "floating-ip" + nicId;
    }

    public static String getSnatNetworkStartName(List<String> projectFqn, String logicalRouterUuid) {
        return StringUtils.join(projectFqn, "__") + "__snat_" + logicalRouterUuid;
    }

    public static String getSnatNetworkEndName() {
        return "right";
    }

    public static String getPublicNetworkPolicyName(long publicIpAddressId) {
        return "public-network-policy" + publicIpAddressId;
    }

    public static String getManagementPolicyName(long zoneId) {
        return "management-network-policy" + zoneId;
    }

    public static String getFabricPolicyName(long zoneId) {
        return "fabric-network-policy" + zoneId;
    }

    public static String getVirtualNetworkPolicyName(long networkId) {
        return "virtual-network-policy" + networkId;
    }

    public static String getDefaultPublicNetworkPolicyName(long zoneId) {
        return "default-public-network-policy" + zoneId;
    }

    public static String getDefaultPublicSubnetPolicyName(long subnetId) {
        return "default-public-subnet-policy" + subnetId;
    }

    public static String getRuleNetworkPolicyName(long ruleId) {
        return "rule-network-policy" + ruleId;
    }

    public static String getSubnetName(long networkId) {
        return "subnet-name" + networkId;
    }

    public static String getIPV4SubnetName(long networkId) {
        return "subnet-name-IPV4" + networkId;
    }

    public static String getIPV6SubnetName(long networkId) {
        return "subnet-name-IPV6" + networkId;
    }

    public static String getLoadBalancerVmiName(long publicIpId) {
        return "loadbalancer-vmi" + publicIpId;
    }

    public static String getLoadBalancerIiName(long publicIpId) {
        return "loadbalancer-ii" + publicIpId;
    }

    public static String getLoadBalancerName(long publicIpId) {
        return "loadbalancer" + publicIpId;
    }

    public static String getLoadBalancerListenerName(long ruleId) {
        return "loadbalancer-listener" + ruleId;
    }

    public static String getLoadBalancerHealthMonitorName(long publicIpId) {
        return "loadbalancer-healthmonitor" + publicIpId;
    }

    public static String getLoadBalancerPoolName(long ruleId) {
        return "loadbalancer-pool" + ruleId;
    }

    public static String getLoadBalancerMemberName(long ruleId, String memberIp) {
        return "loadbalancer-member" + ruleId + memberIp;
    }

    public static String getLoadBalancerAlgorithm(String algorithm) {
        switch (algorithm) {
            case "leastconn":
                return "LEAST_CONNECTIONS";
            case "source":
                return "SOURCE_IP";
            default:
                return "ROUND_ROBIN";
        }
    }

    public static String getLoadBalancerSession(String stickiness) {
        switch (stickiness) {
            case "LbCookie":
                return "HTTP_COOKIE";
            case "AppCookie":
                return "APP_COOKIE";
            case "SourceBased":
                return "SOURCE_IP";
            default:
                return null;
        }
    }

    public static String getEthertTypeFromCidr(String cidr) {
        if (NetUtils.isValidIp4Cidr(cidr)) {
            return IPV4;
        } else {
            return IPV6;
        }
    }

    public static String getTungstenProtocol(String protocol, String cidr) {
        switch (protocol) {
            case NetUtils.TCP_PROTO:
                return NetUtils.TCP_PROTO;
            case NetUtils.UDP_PROTO:
                return NetUtils.UDP_PROTO;
            case NetUtils.ICMP_PROTO:
                if (NetUtils.isValidIp4Cidr(cidr))
                    return NetUtils.ICMP_PROTO;
                else
                    return NetUtils.ICMP6_PROTO;
            default:
                return NetUtils.ANY_PROTO;
        }
    }

    public static String getSecurityGroupName(String name, long accountId) {
        return securityGroup + name + accountId;
    }

    public static String getSingleIpAddressCidr(String ipAddress) {
        if (NetUtils.isValidIp4(ipAddress)) {
            return ipAddress + "/" + MAX_CIDR;
        }

        if (NetUtils.isValidIp6(ipAddress)) {
            return ipAddress + "/" + MAX_IPV6_CIDR;
        }

        return null;
    }
}
