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


package org.apache.cloudstack.api.command.admin.shutdown;

import org.apache.log4j.Logger;

import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.api.response.ReadyForShutdownCmdResponse;

@APICommand(name = "readyforshutdown", description = "checks whether cloudstack is ready for shutdown or not", responseObject = ReadyForShutdownCmdResponse.class, requestHasSensitiveInfo = false, responseHasSensitiveInfo = false,
authorized = {RoleType.Admin, RoleType.ResourceAdmin, RoleType.DomainAdmin, RoleType.User})

public class ReadyForShutdownCmd extends BaseCmd {
public static final Logger s_logger = Logger.getLogger(ReadyForShutdownCmd.class.getName());
public static final String s_name = "readyforshutdown";

/////////////////////////////////////////////////////
/////////////// API Implementation///////////////////
/////////////////////////////////////////////////////

    @Override
        public String getCommandName() {
    return s_name;
}

@Override
        public long getEntityOwnerId() {
    return 0;
}

@Override
        public void execute() {

         ReadyForShutdownCmdResponse response = _queryService.isReadyForShutdown(this);
         response.setResponseName(getCommandName());
         this.setResponseObject(response);
  }
}