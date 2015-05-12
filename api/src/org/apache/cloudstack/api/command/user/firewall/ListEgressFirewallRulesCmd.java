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
// under the License.package org.apache.cloudstack.api.command.user.firewall;

package org.apache.cloudstack.api.command.user.firewall;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.response.FirewallResponse;
import org.apache.cloudstack.api.response.ListResponse;

import com.cloud.network.rules.FirewallRule;
import com.cloud.utils.Pair;

@APICommand(name = "listEgressFirewallRules", description = "Lists all egress firewall rules for network id.", responseObject = FirewallResponse.class, entityType = {FirewallRule.class},
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class ListEgressFirewallRulesCmd extends ListFirewallRulesCmd {
    public static final Logger s_logger = Logger.getLogger(ListEgressFirewallRulesCmd.class.getName());
    private static final String s_name = "listegressfirewallrulesresponse";


    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    @Override
    public FirewallRule.TrafficType getTrafficType() {
        return FirewallRule.TrafficType.Egress;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public void execute() {
        Pair<List<? extends FirewallRule>, Integer> result = _firewallService.listFirewallRules(this);
        ListResponse<FirewallResponse> response = new ListResponse<FirewallResponse>();
        List<FirewallResponse> fwResponses = new ArrayList<FirewallResponse>();

        if (result != null) {
            for (FirewallRule fwRule : result.first()) {
                FirewallResponse ruleData = _responseGenerator.createFirewallResponse(fwRule);
                ruleData.setObjectName("firewallrule");
                fwResponses.add(ruleData);
            }
            response.setResponses(fwResponses, result.second());
        }
        response.setResponseName(getCommandName());
        setResponseObject(response);
    }
}
