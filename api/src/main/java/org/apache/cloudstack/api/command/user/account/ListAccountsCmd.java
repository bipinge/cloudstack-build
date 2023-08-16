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
package org.apache.cloudstack.api.command.user.account;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiCommandResourceType;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiConstants.DomainDetails;
import org.apache.cloudstack.api.BaseListDomainResourcesCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ResponseObject.ResponseView;
import org.apache.cloudstack.api.command.user.UserCmd;
import org.apache.cloudstack.api.response.AccountResponse;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.ResourceIconResponse;
import org.apache.log4j.Logger;

import com.cloud.exception.InvalidParameterValueException;
import com.cloud.server.ResourceIcon;
import com.cloud.server.ResourceTag;
import com.cloud.user.Account;

@APICommand(name = "listAccounts", description = "Lists Accounts and provides detailed Account information for listed Accounts", responseObject = AccountResponse.class, responseView = ResponseView.Restricted, entityType = {Account.class},
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = true)
public class ListAccountsCmd extends BaseListDomainResourcesCmd implements UserCmd {
    public static final Logger s_logger = Logger.getLogger(ListAccountsCmd.class.getName());
    private static final String s_name = "listaccountsresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.ACCOUNT_TYPE,
               type = CommandType.INTEGER,
               description = "List Accounts by Account type. Valid Account types are 1 (admin), 2 (domain-admin), and 0 (user).")
    private Integer accountType;

    @Parameter(name = ApiConstants.ID, type = CommandType.UUID, entityType = AccountResponse.class, description = "List Account by Account ID")
    private Long id;

    @Parameter(name = ApiConstants.IS_CLEANUP_REQUIRED, type = CommandType.BOOLEAN, description = "List accounts by cleanuprequired attribute (values are true or false)")
    private Boolean cleanupRequired;

    @Parameter(name = ApiConstants.NAME, type = CommandType.STRING, description = "List account by account name")
    private String searchName;

    @Parameter(name = ApiConstants.STATE, type = CommandType.STRING, description = "List accounts by state. Valid states are enabled, disabled, and locked.")
    private String state;

    @Parameter(name = ApiConstants.DETAILS,
               type = CommandType.LIST,
               collectionType = CommandType.STRING,
               description = "Comma separated list of account details requested, value can be a list of [ all, resource, min]")
    private List<String> viewDetails;

    @Parameter(name = ApiConstants.SHOW_RESOURCE_ICON, type = CommandType.BOOLEAN,
            description = "Flag to display the resource icon for accounts")
    private Boolean showIcon;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public Account.Type getAccountType() {
        return Account.Type.getFromValue(accountType);
    }

    public Long getId() {
        return id;
    }

    public Boolean isCleanupRequired() {
        return cleanupRequired;
    }

    public String getSearchName() {
        return searchName;
    }

    public String getState() {
        return state;
    }

    public EnumSet<DomainDetails> getDetails() throws InvalidParameterValueException {
        EnumSet<DomainDetails> dv;
        if (viewDetails == null || viewDetails.size() <= 0) {
            dv = EnumSet.of(DomainDetails.all);
        } else {
            try {
                ArrayList<DomainDetails> dc = new ArrayList<DomainDetails>();
                for (String detail : viewDetails) {
                    dc.add(DomainDetails.valueOf(detail));
                }
                dv = EnumSet.copyOf(dc);
            } catch (IllegalArgumentException e) {
                throw new InvalidParameterValueException("The details parameter contains a non permitted value. The allowed values are " +
                    EnumSet.allOf(DomainDetails.class));
            }
        }
        return dv;
    }

    public Boolean getShowIcon() {
        return showIcon != null ? showIcon : false;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public void execute() {
        ListResponse<AccountResponse> response = _queryService.searchForAccounts(this);
        response.setResponseName(getCommandName());
        setResponseObject(response);
        if (response != null && response.getCount() > 0 && getShowIcon()) {
            updateAccountResponse(response.getResponses());
        }
    }

    private void updateAccountResponse(List<AccountResponse> response) {
        for (AccountResponse accountResponse : response) {
            ResourceIcon resourceIcon = resourceIconManager.getByResourceTypeAndUuid(ResourceTag.ResourceObjectType.Account, accountResponse.getObjectId());
            if (resourceIcon == null) {
                continue;
            }
            ResourceIconResponse iconResponse = _responseGenerator.createResourceIconResponse(resourceIcon);
            accountResponse.setResourceIconResponse(iconResponse);
        }
    }

    @Override
    public ApiCommandResourceType getApiResourceType() {
        return ApiCommandResourceType.Account;
    }
}
