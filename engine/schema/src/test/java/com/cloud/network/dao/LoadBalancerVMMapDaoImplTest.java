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
package com.cloud.network.dao;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;

@RunWith(MockitoJUnitRunner.class)
public class LoadBalancerVMMapDaoImplTest {

    @Spy
    LoadBalancerVMMapDaoImpl loadBalancerVMMapDaoImplSpy;

    @Test
    public void testExpungeByVmListNoVms() {
        Assert.assertEquals(0, loadBalancerVMMapDaoImplSpy.expungeByVmList(
                new ArrayList<>(), 100L));
        Assert.assertEquals(0, loadBalancerVMMapDaoImplSpy.expungeByVmList(
                null, 100L));
    }

    @Test
    public void testExpungeByVmList() {
        SearchBuilder<LoadBalancerVMMapVO> sb = Mockito.mock(SearchBuilder.class);
        SearchCriteria<LoadBalancerVMMapVO> sc = Mockito.mock(SearchCriteria.class);
        Mockito.when(sb.create()).thenReturn(sc);
        Mockito.doAnswer((Answer<Integer>) invocationOnMock -> {
            Long batchSize = (Long)invocationOnMock.getArguments()[1];
            return batchSize == null ? 0 : batchSize.intValue();
        }).when(loadBalancerVMMapDaoImplSpy).batchExpunge(Mockito.any(SearchCriteria.class), Mockito.anyLong());
        Mockito.when(loadBalancerVMMapDaoImplSpy.createSearchBuilder()).thenReturn(sb);
        final LoadBalancerVMMapVO mockedVO = Mockito.mock(LoadBalancerVMMapVO.class);
        Mockito.when(sb.entity()).thenReturn(mockedVO);
        List<Long> vmIds = List.of(1L, 2L);
        Object[] array = vmIds.toArray();
        Long batchSize = 50L;
        Assert.assertEquals(batchSize.intValue(), loadBalancerVMMapDaoImplSpy.expungeByVmList(List.of(1L, 2L), batchSize));
        Mockito.verify(sc).setParameters("vmIds", array);
        Mockito.verify(loadBalancerVMMapDaoImplSpy, Mockito.times(1))
                .batchExpunge(sc, batchSize);
    }
}
