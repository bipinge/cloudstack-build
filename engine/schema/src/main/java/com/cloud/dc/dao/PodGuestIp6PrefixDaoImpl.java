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

package com.cloud.dc.dao;

import java.util.List;

import org.springframework.stereotype.Component;

import com.cloud.dc.PodGuestIp6PrefixVO;
import com.cloud.utils.db.DB;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.QueryBuilder;
import com.cloud.utils.db.SearchCriteria;

@Component
@DB
public class PodGuestIp6PrefixDaoImpl extends GenericDaoBase<PodGuestIp6PrefixVO, Long> implements PodGuestIp6PrefixDao {

    public PodGuestIp6PrefixDaoImpl() {
    }

    @Override
    public List<PodGuestIp6PrefixVO> listByPodId(long podId) {
        QueryBuilder<PodGuestIp6PrefixVO> sc = QueryBuilder.create(PodGuestIp6PrefixVO.class);
        sc.and(sc.entity().getPodId(), SearchCriteria.Op.EQ, podId);
        return sc.list();
    }

    @Override
    public List<PodGuestIp6PrefixVO> listByDataCenterId(long dcId) {
        QueryBuilder<PodGuestIp6PrefixVO> sc = QueryBuilder.create(PodGuestIp6PrefixVO.class);
        sc.and(sc.entity().getDataCenterId(), SearchCriteria.Op.EQ, dcId);
        return sc.list();
    }
}
