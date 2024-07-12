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

package org.apache.cloudstack.network.dao;

import com.cloud.utils.db.DB;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.JoinBuilder;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;

import org.apache.cloudstack.network.BgpPeer;
import org.apache.cloudstack.network.BgpPeerNetworkMapVO;
import org.apache.cloudstack.network.BgpPeerVO;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.List;

@Component
@DB
public class BgpPeerDaoImpl extends GenericDaoBase<BgpPeerVO, Long> implements BgpPeerDao {
    protected SearchBuilder<BgpPeerVO> NetworkIdSearch;
    protected SearchBuilder<BgpPeerVO> AllFieldsSearch;

    @Inject
    BgpPeerNetworkMapDao bgpPeerNetworkMapDao;

    @PostConstruct
    public void init() {
        final SearchBuilder<BgpPeerNetworkMapVO> networkSearchBuilder = bgpPeerNetworkMapDao.createSearchBuilder();
        networkSearchBuilder.and("networkId", networkSearchBuilder.entity().getNetworkId(), SearchCriteria.Op.EQ);
        networkSearchBuilder.and("state", networkSearchBuilder.entity().getState(), SearchCriteria.Op.EQ);
        NetworkIdSearch = createSearchBuilder();
        NetworkIdSearch.join("network", networkSearchBuilder, networkSearchBuilder.entity().getBgpPeerId(),
                NetworkIdSearch.entity().getId(), JoinBuilder.JoinType.INNER);
        NetworkIdSearch.done();

        AllFieldsSearch = createSearchBuilder();
        AllFieldsSearch.and("zoneId", AllFieldsSearch.entity().getDataCenterId(), SearchCriteria.Op.EQ);
        AllFieldsSearch.and("domainId", AllFieldsSearch.entity().getDomainId(), SearchCriteria.Op.EQ);
        AllFieldsSearch.and("accountId", AllFieldsSearch.entity().getAccountId(), SearchCriteria.Op.EQ);
        AllFieldsSearch.and("asNumber", AllFieldsSearch.entity().getAsNumber(), SearchCriteria.Op.EQ);
        AllFieldsSearch.and("ip4Address", AllFieldsSearch.entity().getIp4Address(), SearchCriteria.Op.EQ);
        AllFieldsSearch.and("ip6Address", AllFieldsSearch.entity().getIp6Address(), SearchCriteria.Op.EQ);
        AllFieldsSearch.done();
    }

    @Override
    public List<BgpPeerVO> listByNetworkId(long networkId) {
        SearchCriteria<BgpPeerVO> sc = NetworkIdSearch.create();
        sc.setJoinParameters("network", "networkId", networkId);
        sc.setJoinParameters("network", "state", BgpPeer.State.Active);
        return listBy(sc);
    }

    @Override
    public BgpPeerVO findByZoneAndAsNumberAndAddress(long zoneId, Long asNumber, String ip4Address, String ip6Address) {
        SearchCriteria<BgpPeerVO> sc = AllFieldsSearch.create();
        sc.setParameters( "zoneId", zoneId);
        sc.setParameters( "asNumber", asNumber);
        if (ip4Address != null) {
            sc.setParameters( "ip4Address", ip4Address);
        }
        if (ip6Address != null) {
            sc.setParameters( "ip6Address", ip6Address);
        }
        return findOneBy(sc);
    }
}
