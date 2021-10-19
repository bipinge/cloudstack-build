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
package com.cloud.dc;

import com.cloud.network.Ipv6Address;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import java.util.Date;
import java.util.UUID;

@Entity
@Table(name = "dc_ipv6_range")
public class DataCenterIpv6AddressVO implements Ipv6Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    long id;

    @Column(name = "uuid")
    String uuid = UUID.randomUUID().toString();

    @Column(name = "data_center_id", updatable = false, nullable = false)
    long dataCenterId;

    @Column(name = "physical_network_id")
    Long physicalNetworkId;

    @Column(name = "ip6_gateway")
    String ip6Gateway;

    @Column(name = "ip6_cidr")
    String ip6Cidr;

    @Column(name = "router_ipv6")
    String routerIpv6;

    @Column(name = "network_id")
    Long networkId;

    @Column(name = "domain_id")
    Long domainId;

    @Column(name = "account_id")
    Long accountId;

    @Column(name = "taken")
    @Temporal(value = TemporalType.TIMESTAMP)
    private Date takenAt;

    public DataCenterIpv6AddressVO() {
    }

    public DataCenterIpv6AddressVO(long dcId, long physicalNetworkId, String ip6Gateway, String ip6Cidr, String routerIpv6) {
        this.dataCenterId = dcId;
        this.physicalNetworkId = physicalNetworkId;
        this.ip6Gateway = ip6Gateway;
        this.ip6Cidr = ip6Cidr;
        this.routerIpv6 = routerIpv6;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public String getUuid() { return uuid; };

    @Override
    public long getDataCenterId() {
        return dataCenterId;
    }

    @Override
    public long getPhysicalNetworkId() {
        return physicalNetworkId;
    }

    @Override
    public String getIp6Gateway() {
        return ip6Gateway;
    }

    @Override
    public String getIp6Cidr() {
        return ip6Cidr;
    }

    @Override
    public String getRouterIpv6() {
        return routerIpv6;
    }

    @Override
    public Long getDomainId() {
        return domainId;
    }

    @Override
    public Long getAccountId() {
        return accountId;
    }

    public Long getNetworkId() { return networkId; }

    public void setNetworkId(Long networkId) { this.networkId = networkId; }

    public void setDomainId(Long domainId) {
        this.domainId = domainId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    @Override
    public Date getTakenAt() {
        return takenAt;
    }

    public void setTakenAt(Date takenAt) {
        this.takenAt = takenAt;
    }

    @Override
    public boolean isDisplay() {
        return false;
    }
}
