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

package com.cloud.api.query;

import com.cloud.api.query.vo.EventJoinVO;
import com.cloud.event.dao.EventJoinDao;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.PermissionDeniedException;
import com.cloud.network.Network;
import com.cloud.network.dao.NetworkVO;
import com.cloud.user.Account;
import com.cloud.user.AccountManager;
import com.cloud.user.AccountVO;
import com.cloud.user.User;
import com.cloud.user.UserVO;
import com.cloud.utils.Pair;
import com.cloud.utils.db.EntityManager;
import com.cloud.utils.db.Filter;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.vm.VirtualMachine;
import org.apache.cloudstack.acl.SecurityChecker;
import org.apache.cloudstack.api.ApiCommandResourceType;
import org.apache.cloudstack.api.command.user.event.ListEventsCmd;
import org.apache.cloudstack.api.response.EventResponse;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.storage.datastore.db.TemplateDataStoreDao;
import org.apache.cloudstack.storage.datastore.db.TemplateDataStoreVO;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class QueryManagerImplTest {
    public static final long USER_ID = 1;
    public static final long ACCOUNT_ID = 1;

    @Mock
    EntityManager entityManager;
    @Mock
    AccountManager accountManager;
    @Mock
    EventJoinDao eventJoinDao;

    @Mock
    TemplateDataStoreDao templateDataStoreDao;

    private AccountVO account;
    private UserVO user;

    @Before
    public void setUp() throws Exception {
        setupCommonMocks();
    }

    @InjectMocks
    private QueryManagerImpl queryManager = new QueryManagerImpl();

    private void setupCommonMocks() {
        account = new AccountVO("testaccount", 1L, "networkdomain", Account.Type.NORMAL, "uuid");
        account.setId(ACCOUNT_ID);
        user = new UserVO(1, "testuser", "password", "firstname", "lastName", "email", "timezone",
                UUID.randomUUID().toString(), User.Source.UNKNOWN);
        CallContext.register(user, account);
        Mockito.when(accountManager.isRootAdmin(account.getId())).thenReturn(false);
        final SearchBuilder<EventJoinVO> searchBuilder = Mockito.mock(SearchBuilder.class);
        final SearchCriteria<EventJoinVO> searchCriteria = Mockito.mock(SearchCriteria.class);
        final EventJoinVO eventJoinVO = Mockito.mock(EventJoinVO.class);
        when(searchBuilder.entity()).thenReturn(eventJoinVO);
        when(searchBuilder.create()).thenReturn(searchCriteria);
        Mockito.when(eventJoinDao.createSearchBuilder()).thenReturn(searchBuilder);
    }

    private ListEventsCmd setupMockListEventsCmd() {
        ListEventsCmd cmd = Mockito.mock(ListEventsCmd.class);
        Mockito.when(cmd.getEntryTime()).thenReturn(null);
        Mockito.when(cmd.listAll()).thenReturn(true);
        return cmd;
    }

    @Test
    public void searchForEventsSuccess() {
        ListEventsCmd cmd = setupMockListEventsCmd();
        String uuid = UUID.randomUUID().toString();
        Mockito.when(cmd.getResourceId()).thenReturn(uuid);
        Mockito.when(cmd.getResourceType()).thenReturn(ApiCommandResourceType.Network.toString());
        List<EventJoinVO> events = new ArrayList<>();
        events.add(Mockito.mock(EventJoinVO.class));
        events.add(Mockito.mock(EventJoinVO.class));
        events.add(Mockito.mock(EventJoinVO.class));
        Pair<List<EventJoinVO>, Integer> pair = new Pair<>(events, events.size());
        NetworkVO network = Mockito.mock(NetworkVO.class);
        Mockito.when(network.getId()).thenReturn(1L);
        Mockito.when(network.getAccountId()).thenReturn(account.getId());
        Mockito.when(entityManager.findByUuidIncludingRemoved(Network.class, uuid)).thenReturn(network);
        Mockito.doNothing().when(accountManager).checkAccess(account, SecurityChecker.AccessType.ListEntry, true, network);
        Mockito.when(eventJoinDao.searchAndCount(Mockito.any(), Mockito.any(Filter.class))).thenReturn(pair);
        List<EventResponse> respList = new ArrayList<EventResponse>();
        for (EventJoinVO vt : events) {
            respList.add(eventJoinDao.newEventResponse(vt));
        }
        try (MockedStatic<ViewResponseHelper> ignored = Mockito.mockStatic(ViewResponseHelper.class)) {
            Mockito.when(ViewResponseHelper.createEventResponse(Mockito.any())).thenReturn(respList);
            ListResponse<EventResponse> result = queryManager.searchForEvents(cmd);
            Assert.assertEquals((int) result.getCount(), events.size());
        }
    }

    @Test(expected = InvalidParameterValueException.class)
    public void searchForEventsFailResourceTypeNull() {
        ListEventsCmd cmd  = setupMockListEventsCmd();
        String uuid = UUID.randomUUID().toString();
        Mockito.when(cmd.getResourceId()).thenReturn(uuid);
        Mockito.when(cmd.getResourceType()).thenReturn(null);
        queryManager.searchForEvents(cmd);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void searchForEventsFailResourceTypeInvalid() {
        ListEventsCmd cmd  = setupMockListEventsCmd();
        Mockito.when(cmd.getResourceType()).thenReturn("Some");
        queryManager.searchForEvents(cmd);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void searchForEventsFailResourceIdInvalid() {
        ListEventsCmd cmd  = setupMockListEventsCmd();
        Mockito.when(cmd.getResourceId()).thenReturn("random");
        Mockito.when(cmd.getResourceType()).thenReturn(ApiCommandResourceType.VirtualMachine.toString());
        queryManager.searchForEvents(cmd);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void searchForEventsFailResourceNotFound() {
        ListEventsCmd cmd  = setupMockListEventsCmd();
        String uuid = UUID.randomUUID().toString();
        Mockito.when(cmd.getResourceId()).thenReturn(uuid);
        Mockito.when(cmd.getResourceType()).thenReturn(ApiCommandResourceType.VirtualMachine.toString());
        Mockito.when(entityManager.findByUuidIncludingRemoved(VirtualMachine.class, uuid)).thenReturn(null);
        queryManager.searchForEvents(cmd);
    }

    @Test(expected = PermissionDeniedException.class)
    public void searchForEventsFailPermissionDenied() {
        ListEventsCmd cmd  = setupMockListEventsCmd();
        String uuid = UUID.randomUUID().toString();
        Mockito.when(cmd.getResourceId()).thenReturn(uuid);
        Mockito.when(cmd.getResourceType()).thenReturn(ApiCommandResourceType.Network.toString());
        NetworkVO network = Mockito.mock(NetworkVO.class);
        Mockito.when(network.getId()).thenReturn(1L);
        Mockito.when(network.getAccountId()).thenReturn(2L);
        Mockito.when(entityManager.findByUuidIncludingRemoved(Network.class, uuid)).thenReturn(network);
        Mockito.doThrow(new PermissionDeniedException("Denied")).when(accountManager).checkAccess(account, SecurityChecker.AccessType.ListEntry, false, network);
        queryManager.searchForEvents(cmd);
    }

    @Test
    public void getTemplateIdsFromIdsAndStoreIdWithNoTemplatesInStore() {
        List<Long> ids = new ArrayList<>();
        when(templateDataStoreDao.listByStoreId(1L)).thenReturn(new ArrayList<>());
        List<Long> result = queryManager.getTemplateIdsFromIdsAndImageStoreId(ids, 1L);
        Assert.assertEquals(0, result.size());
    }

    @Test
    public void getTemplateIdsFromIdsAndStoreIdWithNoIds() {
        List<Long> ids = new ArrayList<>();
        TemplateDataStoreVO templateDataStoreVO = Mockito.mock(TemplateDataStoreVO.class);
        when(templateDataStoreVO.getTemplateId()).thenReturn(10L);
        List<TemplateDataStoreVO> templatesInStore = new ArrayList<>();
        templatesInStore.add(templateDataStoreVO);
        when(templateDataStoreDao.listByStoreId(1L)).thenReturn(templatesInStore);
        List<Long> result = queryManager.getTemplateIdsFromIdsAndImageStoreId(ids, 1L);
        Assert.assertEquals(1, result.size());
        Assert.assertEquals(10L, result.get(0).longValue());
    }

    @Test
    public void getTemplateIdsFromIdsAndStoreIdWithNonEmptyIds() {
        List<Long> ids = new ArrayList<>();
        ids.add(10L);
        ids.add(11L);
        ids.add(12L);
        TemplateDataStoreVO templateDataStoreVO = Mockito.mock(TemplateDataStoreVO.class);
        when(templateDataStoreVO.getTemplateId()).thenReturn(10L);
        List<TemplateDataStoreVO> templatesInStore = new ArrayList<>();
        templatesInStore.add(templateDataStoreVO);
        when(templateDataStoreDao.listByStoreId(1L)).thenReturn(templatesInStore);
        List<Long> result = queryManager.getTemplateIdsFromIdsAndImageStoreId(ids, 1L);
        Assert.assertEquals(1, result.size());
        Assert.assertEquals(10L, result.get(0).longValue());
    }
}
