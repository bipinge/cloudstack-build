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
package com.cloud.network.element;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import org.apache.cloudstack.network.ExternalNetworkDeviceManager.NetworkDevice;
import org.apache.log4j.Logger;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.routing.NetworkElementCommand;
import com.cloud.agent.api.routing.SetFirewallRulesCommand;
import com.cloud.agent.api.to.FirewallRuleTO;
import com.cloud.api.ApiDBUtils;
import com.cloud.api.commands.AddVyosRouterFirewallCmd;
import com.cloud.api.commands.ConfigureVyosRouterFirewallCmd;
import com.cloud.api.commands.DeleteVyosRouterFirewallCmd;
import com.cloud.api.commands.ListVyosRouterFirewallNetworksCmd;
import com.cloud.api.commands.ListVyosRouterFirewallsCmd;
import com.cloud.api.response.VyosRouterFirewallResponse;
import com.cloud.configuration.Config;
import com.cloud.configuration.ConfigurationManager;
import com.cloud.dc.DataCenter;
import com.cloud.dc.DataCenter.NetworkType;
import com.cloud.dc.VlanVO;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.dc.dao.VlanDao;
import com.cloud.deploy.DeployDestination;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.InsufficientNetworkCapacityException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.host.Host;
import com.cloud.host.dao.HostDao;
import com.cloud.host.dao.HostDetailsDao;
import com.cloud.network.ExternalFirewallDeviceManagerImpl;
import com.cloud.network.IpAddress;
import com.cloud.network.Network;
import com.cloud.network.Network.Capability;
import com.cloud.network.Network.Provider;
import com.cloud.network.Network.Service;
import com.cloud.network.NetworkModel;
import com.cloud.network.Networks.BroadcastDomainType;
import com.cloud.network.PhysicalNetwork;
import com.cloud.network.PhysicalNetworkServiceProvider;
import com.cloud.network.PublicIpAddress;
import com.cloud.network.dao.ExternalFirewallDeviceDao;
import com.cloud.network.dao.ExternalFirewallDeviceVO;
import com.cloud.network.dao.ExternalFirewallDeviceVO.FirewallDeviceState;
import com.cloud.network.dao.FirewallRulesDao;
import com.cloud.network.dao.IPAddressDao;
import com.cloud.network.dao.IPAddressVO;
import com.cloud.network.dao.NetworkDao;
import com.cloud.network.dao.NetworkExternalFirewallDao;
import com.cloud.network.dao.NetworkExternalFirewallVO;
import com.cloud.network.dao.NetworkServiceMapDao;
import com.cloud.network.dao.NetworkVO;
import com.cloud.network.dao.PhysicalNetworkDao;
import com.cloud.network.dao.PhysicalNetworkVO;
import com.cloud.network.resource.VyosRouterResource;
import com.cloud.network.rules.FirewallRule;
import com.cloud.network.rules.FirewallRuleVO;
import com.cloud.network.rules.PortForwardingRule;
import com.cloud.network.rules.StaticNat;
import com.cloud.offering.NetworkOffering;
import com.cloud.offerings.dao.NetworkOfferingDao;
import com.cloud.utils.NumbersUtil;
import com.cloud.utils.db.EntityManager;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.NicProfile;
import com.cloud.vm.ReservationContext;
import com.cloud.vm.VirtualMachineProfile;


public class VyosRouterExternalFirewallElement extends ExternalFirewallDeviceManagerImpl implements SourceNatServiceProvider, FirewallServiceProvider,
        PortForwardingServiceProvider, IpDeployer, VyosRouterFirewallElementService, StaticNatServiceProvider {

    private static final Logger s_logger = Logger.getLogger(VyosRouterExternalFirewallElement.class);

    private static final Map<Service, Map<Capability, String>> capabilities = setCapabilities();

    @Inject
    NetworkModel _networkManager;
    @Inject
    HostDao _hostDao;
    @Inject
    ConfigurationManager _configMgr;
    @Inject
    NetworkOfferingDao _networkOfferingDao;
    @Inject
    NetworkDao _networksDao;
    @Inject
    DataCenterDao _dcDao;
    @Inject
    PhysicalNetworkDao _physicalNetworkDao;
    @Inject
    ExternalFirewallDeviceDao _fwDevicesDao;
    @Inject
    NetworkExternalFirewallDao _networkFirewallDao;
    @Inject
    NetworkDao _networkDao;
    @Inject
    NetworkServiceMapDao _ntwkSrvcDao;
    @Inject
    HostDetailsDao _hostDetailDao;
    @Inject
    ConfigurationDao _configDao;
    @Inject
    EntityManager _entityMgr;
    @Inject
    FirewallRulesDao _fwRulesDao;
    @Inject
    NetworkModel _networkModel;
    @Inject
    IPAddressDao _ipAddressDao;
    @Inject
    VlanDao _vlanDao;
    @Inject
    AgentManager _agentMgr;


    private boolean canHandle(Network network, Service service) {
        DataCenter zone = _entityMgr.findById(DataCenter.class, network.getDataCenterId());
        if (zone.getNetworkType() == NetworkType.Advanced && network.getGuestType() != Network.GuestType.Isolated) {
            s_logger.trace("Element " + getProvider().getName() + "is not handling network type = " + network.getGuestType());
            return false;
        }

        if (service == null) {
            if (!_networkManager.isProviderForNetwork(getProvider(), network.getId())) {
                s_logger.trace("Element " + getProvider().getName() + " is not a provider for the network " + network);
                return false;
            }
        } else {
            if (!_networkManager.isProviderSupportServiceInNetwork(network.getId(), service, getProvider())) {
                s_logger.trace("Element " + getProvider().getName() + " doesn't support service " + service.getName() + " in the network " + network);
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean implement(Network network, NetworkOffering offering, DeployDestination dest, ReservationContext context) throws ResourceUnavailableException,
        ConcurrentOperationException, InsufficientNetworkCapacityException {
        DataCenter zone = _entityMgr.findById(DataCenter.class, network.getDataCenterId());

        // don't have to implement network is Basic zone
        if (zone.getNetworkType() == NetworkType.Basic) {
            s_logger.debug("Not handling network implement in zone of type " + NetworkType.Basic);
            return false;
        }

        if (!canHandle(network, null)) {
            return false;
        }

        try {
            return manageGuestNetworkWithExternalFirewall(true, network);
        } catch (InsufficientCapacityException capacityException) {
            s_logger.error("Fail to implement the Vyos Router for network " + network, capacityException);
            return false;
        }
    }

    @Override
    public boolean prepare(Network config, NicProfile nic, VirtualMachineProfile vm, DeployDestination dest, ReservationContext context)
        throws ConcurrentOperationException, InsufficientNetworkCapacityException, ResourceUnavailableException {
        return true;
    }

    @Override
    public boolean release(Network config, NicProfile nic, VirtualMachineProfile vm, ReservationContext context) {
        return true;
    }

    @Override
    public boolean shutdown(Network network, ReservationContext context, boolean cleanup) throws ResourceUnavailableException, ConcurrentOperationException {
        DataCenter zone = _entityMgr.findById(DataCenter.class, network.getDataCenterId());

        // don't have to implement network is Basic zone
        if (zone.getNetworkType() == NetworkType.Basic) {
            s_logger.debug("Not handling network shutdown in zone of type " + NetworkType.Basic);
            return false;
        }

        if (!canHandle(network, null)) {
            return false;
        }
        try {
            return manageGuestNetworkWithExternalFirewall(false, network);
        } catch (InsufficientCapacityException capacityException) {
            // TODO: handle out of capacity exception
            return false;
        }
    }

    @Override
    public boolean destroy(Network config, ReservationContext context) {
        return true;
    }

    @Override
    public boolean applyFWRules(Network config, List<? extends FirewallRule> rules) throws ResourceUnavailableException {
        if (!canHandle(config, Service.Firewall)) {
            return false;
        }

        return applyFirewallRules(config, rules);
    }

    @Override
    public Provider getProvider() {
        return Provider.VyosRouter;
    }

    @Override
    public Map<Service, Map<Capability, String>> getCapabilities() {
        return capabilities;
    }

    private static Map<Service, Map<Capability, String>> setCapabilities() {
        Map<Service, Map<Capability, String>> capabilities = new HashMap<Service, Map<Capability, String>>();

        // Set capabilities for Firewall service
        Map<Capability, String> firewallCapabilities = new HashMap<Capability, String>();
        firewallCapabilities.put(Capability.SupportedProtocols, "tcp,udp,icmp");
        firewallCapabilities.put(Capability.SupportedEgressProtocols, "tcp,udp,icmp,all");
        firewallCapabilities.put(Capability.MultipleIps, "true");
        firewallCapabilities.put(Capability.TrafficStatistics, "per public ip");
        firewallCapabilities.put(Capability.SupportedTrafficDirection, "ingress, egress");
        capabilities.put(Service.Firewall, firewallCapabilities);

        capabilities.put(Service.Gateway, null);

        Map<Capability, String> sourceNatCapabilities = new HashMap<Capability, String>();
        // Specifies that this element supports either one source NAT rule per account;
        sourceNatCapabilities.put(Capability.SupportedSourceNatTypes, "peraccount");
        capabilities.put(Service.SourceNat, sourceNatCapabilities);

        // Specifies that port forwarding rules are supported by this element
        capabilities.put(Service.PortForwarding, null);

        // Specifies that static NAT rules are supported by this element
        capabilities.put(Service.StaticNat, null);

        return capabilities;
    }

    @Override
    public boolean applyPFRules(Network network, List<PortForwardingRule> rules) throws ResourceUnavailableException {
        if (!canHandle(network, Service.PortForwarding)) {
            return false;
        }

        return applyPortForwardingRules(network, rules);
    }

    @Override
    public boolean isReady(PhysicalNetworkServiceProvider provider) {

        List<ExternalFirewallDeviceVO> fwDevices = _fwDevicesDao.listByPhysicalNetworkAndProvider(provider.getPhysicalNetworkId(), Provider.VyosRouter.getName());
        // true if at-least one Vyos Router device is added in to physical network and is in configured (in enabled state) state
        if (fwDevices != null && !fwDevices.isEmpty()) {
            for (ExternalFirewallDeviceVO fwDevice : fwDevices) {
                if (fwDevice.getDeviceState() == FirewallDeviceState.Enabled) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean shutdownProviderInstances(PhysicalNetworkServiceProvider provider, ReservationContext context) throws ConcurrentOperationException,
        ResourceUnavailableException {
        // TODO Auto-generated method stub
        return true;
    }

    @Override
    public boolean canEnableIndividualServices() {
        return true;
    }

    @Override
    public List<Class<?>> getCommands() {
        List<Class<?>> cmdList = new ArrayList<Class<?>>();
        cmdList.add(AddVyosRouterFirewallCmd.class);
        cmdList.add(ConfigureVyosRouterFirewallCmd.class);
        cmdList.add(DeleteVyosRouterFirewallCmd.class);
        cmdList.add(ListVyosRouterFirewallNetworksCmd.class);
        cmdList.add(ListVyosRouterFirewallsCmd.class);
        return cmdList;
    }

    @Override
    public ExternalFirewallDeviceVO addVyosRouterFirewall(AddVyosRouterFirewallCmd cmd) {
        String deviceName = cmd.getDeviceType();
        if (!deviceName.equalsIgnoreCase(NetworkDevice.VyosRouter.getName())) {
            throw new InvalidParameterValueException("Invalid Vyos Router device type");
        }
        return addExternalFirewall(cmd.getPhysicalNetworkId(), cmd.getUrl(), cmd.getUsername(), cmd.getPassword(), deviceName, new VyosRouterResource());
    }

    @Override
    public boolean deleteVyosRouterFirewall(DeleteVyosRouterFirewallCmd cmd) {
        Long fwDeviceId = cmd.getFirewallDeviceId();

        ExternalFirewallDeviceVO fwDeviceVO = _fwDevicesDao.findById(fwDeviceId);
        if (fwDeviceVO == null || !fwDeviceVO.getDeviceName().equalsIgnoreCase(NetworkDevice.VyosRouter.getName())) {
            throw new InvalidParameterValueException("No Vyos Router device found with ID: " + fwDeviceId);
        }
        return deleteExternalFirewall(fwDeviceVO.getHostId());
    }

    @Override
    public ExternalFirewallDeviceVO configureVyosRouterFirewall(ConfigureVyosRouterFirewallCmd cmd) {
        Long fwDeviceId = cmd.getFirewallDeviceId();
        Long deviceCapacity = cmd.getFirewallCapacity();

        ExternalFirewallDeviceVO fwDeviceVO = _fwDevicesDao.findById(fwDeviceId);
        if (fwDeviceVO == null || !fwDeviceVO.getDeviceName().equalsIgnoreCase(NetworkDevice.VyosRouter.getName())) {
            throw new InvalidParameterValueException("No Vyos Router device found with ID: " + fwDeviceId);
        }

        if (deviceCapacity != null) {
            // check if any networks are using this Vyos Router device
            List<NetworkExternalFirewallVO> networks = _networkFirewallDao.listByFirewallDeviceId(fwDeviceId);
            if ((networks != null) && !networks.isEmpty()) {
                if (deviceCapacity < networks.size()) {
                    throw new CloudRuntimeException("There are more number of networks already using this Vyos Router device than configured capacity");
                }
            }
            if (deviceCapacity != null) {
                fwDeviceVO.setCapacity(deviceCapacity);
            }
        }

        fwDeviceVO.setDeviceState(FirewallDeviceState.Enabled);
        _fwDevicesDao.update(fwDeviceId, fwDeviceVO);
        return fwDeviceVO;
    }

    @Override
    public List<ExternalFirewallDeviceVO> listVyosRouterFirewalls(ListVyosRouterFirewallsCmd cmd) {
        Long physcialNetworkId = cmd.getPhysicalNetworkId();
        Long fwDeviceId = cmd.getFirewallDeviceId();
        PhysicalNetworkVO pNetwork = null;
        List<ExternalFirewallDeviceVO> fwDevices = new ArrayList<ExternalFirewallDeviceVO>();

        if (physcialNetworkId == null && fwDeviceId == null) {
            throw new InvalidParameterValueException("Either physical network Id or load balancer device Id must be specified");
        }

        if (fwDeviceId != null) {
            ExternalFirewallDeviceVO fwDeviceVo = _fwDevicesDao.findById(fwDeviceId);
            if (fwDeviceVo == null || !fwDeviceVo.getDeviceName().equalsIgnoreCase(NetworkDevice.VyosRouter.getName())) {
                throw new InvalidParameterValueException("Could not find Vyos Router device with ID: " + fwDeviceId);
            }
            fwDevices.add(fwDeviceVo);
        }

        if (physcialNetworkId != null) {
            pNetwork = _physicalNetworkDao.findById(physcialNetworkId);
            if (pNetwork == null) {
                throw new InvalidParameterValueException("Could not find phyical network with ID: " + physcialNetworkId);
            }
            fwDevices = _fwDevicesDao.listByPhysicalNetworkAndProvider(physcialNetworkId, Provider.VyosRouter.getName());
        }

        return fwDevices;
    }

    @Override
    public List<? extends Network> listNetworks(ListVyosRouterFirewallNetworksCmd cmd) {
        Long fwDeviceId = cmd.getFirewallDeviceId();
        List<NetworkVO> networks = new ArrayList<NetworkVO>();

        ExternalFirewallDeviceVO fwDeviceVo = _fwDevicesDao.findById(fwDeviceId);
        if (fwDeviceVo == null || !fwDeviceVo.getDeviceName().equalsIgnoreCase(NetworkDevice.VyosRouter.getName())) {
            throw new InvalidParameterValueException("Could not find Vyos Router device with ID " + fwDeviceId);
        }

        List<NetworkExternalFirewallVO> networkFirewallMaps = _networkFirewallDao.listByFirewallDeviceId(fwDeviceId);
        if (networkFirewallMaps != null && !networkFirewallMaps.isEmpty()) {
            for (NetworkExternalFirewallVO networkFirewallMap : networkFirewallMaps) {
                NetworkVO network = _networkDao.findById(networkFirewallMap.getNetworkId());
                networks.add(network);
            }
        }

        return networks;
    }

    @Override
    public VyosRouterFirewallResponse createVyosRouterFirewallResponse(ExternalFirewallDeviceVO fwDeviceVO) {
        VyosRouterFirewallResponse response = new VyosRouterFirewallResponse();
        Map<String, String> fwDetails = _hostDetailDao.findDetails(fwDeviceVO.getHostId());
        Host fwHost = _hostDao.findById(fwDeviceVO.getHostId());

        response.setId(fwDeviceVO.getUuid());
        PhysicalNetwork pnw = ApiDBUtils.findPhysicalNetworkById(fwDeviceVO.getPhysicalNetworkId());
        if (pnw != null) {
            response.setPhysicalNetworkId(pnw.getUuid());
        }
        response.setDeviceName(fwDeviceVO.getDeviceName());
        if (fwDeviceVO.getCapacity() == 0) {
            long defaultFwCapacity = NumbersUtil.parseLong(_configDao.getValue(Config.DefaultExternalFirewallCapacity.key()), 50);
            response.setDeviceCapacity(defaultFwCapacity);
        } else {
            response.setDeviceCapacity(fwDeviceVO.getCapacity());
        }
        response.setProvider(fwDeviceVO.getProviderName());
        response.setDeviceState(fwDeviceVO.getDeviceState().name());
        response.setIpAddress(fwHost.getPrivateIpAddress());
        response.setPublicInterface(fwDetails.get("publicInterface"));
        response.setPrivateInterface(fwDetails.get("privateInterface"));
        response.setTimeout(fwDetails.get("timeout"));
        response.setObjectName("vyosrouterfirewall");
        return response;
    }

    @Override
    public boolean verifyServicesCombination(Set<Service> services) {
        if (!services.contains(Service.Firewall)) {
            s_logger.warn("Vyos Router must be used as Firewall Service Provider in the network");
            return false;
        }
        return true;
    }

    @Override
    public IpDeployer getIpDeployer(Network network) {
        return this;
    }

    @Override
    public boolean applyIps(Network network, List<? extends PublicIpAddress> ipAddress, Set<Service> service) throws ResourceUnavailableException {
        // return true, as IP will be associated as part of static NAT/port forwarding rule configuration
        return true;
    }

    @Override
    public boolean applyStaticNats(Network config, List<? extends StaticNat> rules) throws ResourceUnavailableException {
        if (!canHandle(config, Service.StaticNat)) {
            return false;
        }
        return applyStaticNatRules(config, rules);
    }

    //Override sendFirewallRules to populate publicVlanTag, privateVlanTag, and guestCidr for all firewall rules.
    @Override
    protected void sendFirewallRules(List<FirewallRuleTO> firewallRules, DataCenter zone, long externalFirewallId) throws ResourceUnavailableException {
        if (!firewallRules.isEmpty()) {
            SetFirewallRulesCommand cmd = new SetFirewallRulesCommand(firewallRules);

            long firewallRuleId=firewallRules.get(0).getId();

            //When setting up the default egress firewall rules this Id will be empty. Skip this since it has already been setup during network implementation.
            if (firewallRuleId == 0) {
                return;
            }
            FirewallRuleVO fwr = _fwRulesDao.findById(firewallRuleId);
            long nwId = fwr.getNetworkId();
            NetworkVO network = _networkDao.findById(nwId);
            NetworkOffering offering = _networkOfferingDao.findById(network.getNetworkOfferingId());
            boolean sharedSourceNat = offering.getSharedSourceNat();

            IPAddressVO sourceNatIp = null;
            if (!sharedSourceNat) {
                // Get the source NAT IP address for this network
                List<? extends IpAddress> sourceNatIps = _networkModel.listPublicIpsAssignedToAccount(network.getAccountId(), zone.getId(), true);

                for (IpAddress ipAddress : sourceNatIps) {
                    if (ipAddress.getAssociatedWithNetworkId().longValue() == network.getId()) {
                        sourceNatIp = _ipAddressDao.findById(ipAddress.getId());
                        break;
                    }
                }
            }

            String guestVlanTag = BroadcastDomainType.getValue(network.getBroadcastUri());
            String guestVlanCidr = network.getCidr();

            String publicVlanTag = null;

            if (sourceNatIp != null) {
                VlanVO publicVlan = _vlanDao.findById(sourceNatIp.getVlanId());
                publicVlanTag = publicVlan.getVlanTag();
            }


            cmd.setAccessDetail("PUBLIC_VLAN_TAG", publicVlanTag);
            cmd.setAccessDetail(NetworkElementCommand.GUEST_NETWORK_CIDR, guestVlanCidr);
            cmd.setAccessDetail(NetworkElementCommand.GUEST_VLAN_TAG, String.valueOf(guestVlanTag));


            Answer answer = _agentMgr.easySend(externalFirewallId, cmd);
            if (answer == null || !answer.getResult()) {
                String details = (answer != null) ? answer.getDetails() : "details unavailable";
                String msg = "External firewall was unable to apply static nat rules to the SRX appliance in zone " + zone.getName() + " due to: " + details + ".";
                s_logger.error(msg);
                throw new ResourceUnavailableException(msg, DataCenter.class, zone.getId());
            }
        }
    }

}