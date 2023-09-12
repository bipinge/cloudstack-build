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
package com.cloud.storage.resource;

import org.apache.cloudstack.storage.command.QuerySnapshotZoneCopyAnswer;
import org.apache.cloudstack.storage.command.QuerySnapshotZoneCopyCommand;
import org.apache.cloudstack.storage.to.SnapshotObjectTO;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.cloud.agent.api.Answer;

@RunWith(MockitoJUnitRunner.class)
public class StorageSubsystemCommandHandlerBaseTest {

    @Test
    public void testHandleQuerySnapshotCommand() {
        StorageSubsystemCommandHandlerBase storageSubsystemCommandHandlerBase = new StorageSubsystemCommandHandlerBase(Mockito.mock(StorageProcessor.class));
        QuerySnapshotZoneCopyCommand querySnapshotZoneCopyCommand = new QuerySnapshotZoneCopyCommand(Mockito.mock(SnapshotObjectTO.class));
        Answer answer = storageSubsystemCommandHandlerBase.handleStorageCommands(querySnapshotZoneCopyCommand);
        Assert.assertTrue(answer instanceof QuerySnapshotZoneCopyAnswer);
        QuerySnapshotZoneCopyAnswer querySnapshotZoneCopyAnswer = (QuerySnapshotZoneCopyAnswer)answer;
        Assert.assertFalse(querySnapshotZoneCopyAnswer.getResult());
        Assert.assertEquals("Unsupported command", querySnapshotZoneCopyAnswer.getDetails());
    }
}