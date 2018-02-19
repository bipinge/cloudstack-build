/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.cloud.network.vpc;

import org.apache.cloudstack.acl.SecurityChecker.AccessType;
import org.apache.cloudstack.api.command.user.network.UpdateNetworkACLListCmd;
import org.apache.cloudstack.context.CallContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.cloud.network.vpc.dao.NetworkACLDao;
import com.cloud.user.Account;
import com.cloud.user.AccountManager;
import com.cloud.utils.db.EntityManager;

@RunWith(PowerMockRunner.class)
public class NetworkACLServiceImplTest {

    @Spy
    @InjectMocks
    private NetworkACLServiceImpl networkACLServiceImpl = new NetworkACLServiceImpl();
    @Mock
    private NetworkACLDao networkACLDao;
    @Mock
    private AccountManager accountManager;
    @Mock
    private EntityManager entityManager;

    @Mock
    private UpdateNetworkACLListCmd updateNetworkACLListCmdMock;
    @Mock
    private NetworkACLVO networkACLVOMock;

    private long networkAclListId = 1l;

    @Before
    public void before() {
        Mockito.when(networkACLDao.findById(networkAclListId)).thenReturn(networkACLVOMock);

        PowerMockito.mockStatic(CallContext.class);
        PowerMockito.when(CallContext.current()).thenReturn(Mockito.mock(CallContext.class));
    }

    @Test
    @PrepareForTest(CallContext.class)
    public void updateNetworkACLTestParametersNotNull() {
        String name = "name";
        String description = "desc";
        String customId = "customId";

        Mockito.when(updateNetworkACLListCmdMock.getName()).thenReturn(name);
        Mockito.when(updateNetworkACLListCmdMock.getDescription()).thenReturn(description);
        Mockito.when(updateNetworkACLListCmdMock.getCustomId()).thenReturn(customId);
        Mockito.when(updateNetworkACLListCmdMock.getId()).thenReturn(networkAclListId);
        Mockito.when(updateNetworkACLListCmdMock.getDisplay()).thenReturn(false);

        networkACLServiceImpl.updateNetworkACL(updateNetworkACLListCmdMock);

        InOrder inOrder = Mockito.inOrder(networkACLDao, entityManager, entityManager, accountManager, networkACLVOMock);

        inOrder.verify(networkACLDao).findById(networkAclListId);
        inOrder.verify(entityManager).findById(Mockito.eq(Vpc.class), Mockito.anyLong());
        inOrder.verify(accountManager).checkAccess(Mockito.any(Account.class), Mockito.isNull(AccessType.class), Mockito.eq(true), Mockito.any(Vpc.class));

        inOrder.verify(networkACLVOMock).setName(name);
        inOrder.verify(networkACLVOMock).setDescription(description);
        inOrder.verify(networkACLVOMock).setUuid(customId);
        inOrder.verify(networkACLVOMock).setDisplay(false);

        inOrder.verify(networkACLDao).update(networkAclListId, networkACLVOMock);
        inOrder.verify(networkACLDao).findById(networkAclListId);
    }

    @Test
    @PrepareForTest(CallContext.class)
    public void updateNetworkACLTestParametersWithNullValues() {
        Mockito.when(updateNetworkACLListCmdMock.getName()).thenReturn(null);
        Mockito.when(updateNetworkACLListCmdMock.getDescription()).thenReturn(null);
        Mockito.when(updateNetworkACLListCmdMock.getCustomId()).thenReturn(null);
        Mockito.when(updateNetworkACLListCmdMock.getId()).thenReturn(networkAclListId);
        Mockito.when(updateNetworkACLListCmdMock.getDisplay()).thenReturn(null);

        networkACLServiceImpl.updateNetworkACL(updateNetworkACLListCmdMock);

        InOrder inOrder = Mockito.inOrder(networkACLDao, entityManager, entityManager, accountManager, networkACLVOMock);

        inOrder.verify(networkACLDao).findById(networkAclListId);
        inOrder.verify(entityManager).findById(Mockito.eq(Vpc.class), Mockito.anyLong());
        inOrder.verify(accountManager).checkAccess(Mockito.any(Account.class), Mockito.isNull(AccessType.class), Mockito.eq(true), Mockito.any(Vpc.class));

        Mockito.verify(networkACLVOMock, Mockito.times(0)).setName(null);
        inOrder.verify(networkACLVOMock, Mockito.times(0)).setDescription(null);
        inOrder.verify(networkACLVOMock, Mockito.times(0)).setUuid(null);
        inOrder.verify(networkACLVOMock, Mockito.times(0)).setDisplay(false);

        inOrder.verify(networkACLDao).update(networkAclListId, networkACLVOMock);
        inOrder.verify(networkACLDao).findById(networkAclListId);
    }
}
