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

package com.cloud.hypervisor.kvm.resource.wrapper;

import org.apache.cloudstack.utils.qemu.QemuCommand;
import org.apache.log4j.Logger;
import org.libvirt.Connect;
import org.libvirt.Domain;
import org.libvirt.DomainInfo.DomainState;
import org.libvirt.LibvirtException;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.FreezeThawVMAnswer;
import com.cloud.agent.api.FreezeThawVMCommand;
import com.cloud.hypervisor.kvm.resource.LibvirtComputingResource;
import com.cloud.resource.CommandWrapper;
import com.cloud.resource.ResourceWrapper;
import com.google.gson.Gson;
import com.google.gson.JsonParser;

@ResourceWrapper(handles = FreezeThawVMCommand.class)
public class LibvirtFreezeThawVMCommandWrapper extends CommandWrapper<FreezeThawVMCommand, Answer, LibvirtComputingResource> {

    private static final Logger s_logger = Logger.getLogger(LibvirtFreezeThawVMCommandWrapper.class);

    @Override
    public Answer execute(FreezeThawVMCommand command, LibvirtComputingResource serverResource) {
        String vmName = command.getVmName();
        Domain domain = null;

        try {
            final LibvirtUtilitiesHelper libvirtUtilitiesHelper = serverResource.getLibvirtUtilitiesHelper();
            Connect connect = libvirtUtilitiesHelper.getConnection();
            domain = serverResource.getDomain(connect, vmName);
            if (domain == null) {
                return new FreezeThawVMAnswer(command, false, String.format("Failed to %s due to %s was not found",
                        command.getOption().toUpperCase(), vmName));
            }
            DomainState domainState = domain.getInfo().state ;
            if (domainState != DomainState.VIR_DOMAIN_RUNNING) {
                return new FreezeThawVMAnswer(command, false,
                        String.format("%s of VM failed due to vm %s is in %s state", command.getOption().toUpperCase(),
                                vmName, domainState));
            }

            String result = result(command.getOption(), domain);
            if (result == null || (result.startsWith("error") || new JsonParser().parse(result).getAsJsonObject().get("return").getAsInt() != 2)) {
                return new FreezeThawVMAnswer(command, false, String.format("Failed to %s vm %s due to result status is: %s",
                        command.getOption().toUpperCase(), vmName, result));
            }
            return new FreezeThawVMAnswer(command, true, String.format("%s of VM - %s is successful", command.getOption().toUpperCase(), vmName));
        } catch (LibvirtException libvirtException) {
            return new FreezeThawVMAnswer(command, false,  String.format("Failed to %s VM - %s due to %s",
                    command.getOption().toUpperCase(), vmName, libvirtException.getMessage()));
        }finally {
            if (domain != null) {
                try {
                    domain.free();
                } catch (LibvirtException e) {
                    s_logger.trace("Ingore error ", e);
                }
            }
        }
    }

    private String result(String cmd, Domain domain) throws LibvirtException {
        String result = null;
        if (cmd.equals("freeze")) {
            result = domain.qemuAgentCommand(new Gson().toJson(QemuCommand.executeQemuCommand(QemuCommand.AGENT_FREEZE, null)).toString(), 10, 0);
        }else if (cmd.equals("thaw")) {
            result = domain.qemuAgentCommand(new Gson().toJson(QemuCommand.executeQemuCommand(QemuCommand.AGENT_THAW, null)).toString(), 10, 0);
        }
        return result;
    }
}
