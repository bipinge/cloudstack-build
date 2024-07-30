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
package com.cloud.user.dao;

import com.cloud.user.Account;
import com.cloud.user.Account.State;
import com.cloud.user.AccountVO;
import com.cloud.user.User;
import com.cloud.user.UserVO;
import com.cloud.utils.Pair;
import com.cloud.utils.Ternary;
import com.cloud.utils.crypt.DBEncryptionUtil;
import com.cloud.utils.db.Filter;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.GenericSearchBuilder;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.db.SearchCriteria.Func;
import com.cloud.utils.db.SearchCriteria.Op;
import org.apache.cloudstack.acl.ApiKeyPairVO;
import org.apache.cloudstack.acl.apikeypair.ApiKeyPair;
import org.apache.commons.lang3.StringUtils;
import com.cloud.utils.db.TransactionLegacy;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Date;
import java.util.List;

@Component
public class AccountDaoImpl extends GenericDaoBase<AccountVO, Long> implements AccountDao {
    private static final String FIND_USER_ACCOUNT_BY_API_KEY = "SELECT u.id, u.username, u.account_id, u.state, " +
            "a.id, a.account_name, a.type, a.role_id, a.domain_id, a.state, ak.id, ak.start_date, ak.end_date, ak.secret_key, " +
            "ak.removed FROM `cloud`.`user` u INNER JOIN `cloud`.`account` a ON u.account_id = a.id INNER JOIN `cloud`.`api_keypair` ak " +
            "ON ak.user_id = u.id WHERE ak.api_key = ? AND u.removed IS NULL";


    protected final SearchBuilder<AccountVO> AllFieldsSearch;
    protected final SearchBuilder<AccountVO> AccountTypeSearch;
    protected final SearchBuilder<AccountVO> DomainAccountsSearch;
    protected final SearchBuilder<AccountVO> CleanupForRemovedAccountsSearch;
    protected final SearchBuilder<AccountVO> CleanupForDisabledAccountsSearch;
    protected final SearchBuilder<AccountVO> NonProjectAccountSearch;
    protected final SearchBuilder<AccountVO> AccountByRoleSearch;
    protected final GenericSearchBuilder<AccountVO, Long> AccountIdsSearch;
    protected final GenericSearchBuilder<AccountVO, Long> ActiveDomainCount;

    public AccountDaoImpl() {
        AllFieldsSearch = createSearchBuilder();
        AllFieldsSearch.and("id", AllFieldsSearch.entity().getId(), SearchCriteria.Op.EQ);
        AllFieldsSearch.and("accountName", AllFieldsSearch.entity().getAccountName(), SearchCriteria.Op.EQ);
        AllFieldsSearch.and("domainId", AllFieldsSearch.entity().getDomainId(), SearchCriteria.Op.EQ);
        AllFieldsSearch.and("state", AllFieldsSearch.entity().getState(), SearchCriteria.Op.EQ);
        AllFieldsSearch.and("type", AllFieldsSearch.entity().getType(), SearchCriteria.Op.EQ);
        AllFieldsSearch.done();

        AccountTypeSearch = createSearchBuilder();
        AccountTypeSearch.and("domainId", AccountTypeSearch.entity().getDomainId(), SearchCriteria.Op.EQ);
        AccountTypeSearch.and("type", AccountTypeSearch.entity().getType(), SearchCriteria.Op.EQ);
        AccountTypeSearch.done();

        DomainAccountsSearch = createSearchBuilder();
        DomainAccountsSearch.and("domainId", DomainAccountsSearch.entity().getDomainId(), SearchCriteria.Op.EQ);
        DomainAccountsSearch.and("removed", DomainAccountsSearch.entity().getRemoved(), SearchCriteria.Op.NULL);
        DomainAccountsSearch.done();

        CleanupForRemovedAccountsSearch = createSearchBuilder();
        CleanupForRemovedAccountsSearch.and("cleanup", CleanupForRemovedAccountsSearch.entity().getNeedsCleanup(), SearchCriteria.Op.EQ);
        CleanupForRemovedAccountsSearch.and("removed", CleanupForRemovedAccountsSearch.entity().getRemoved(), SearchCriteria.Op.NNULL);
        CleanupForRemovedAccountsSearch.and("domainid", CleanupForRemovedAccountsSearch.entity().getDomainId(), SearchCriteria.Op.EQ);
        CleanupForRemovedAccountsSearch.done();

        CleanupForDisabledAccountsSearch = createSearchBuilder();
        CleanupForDisabledAccountsSearch.and("cleanup", CleanupForDisabledAccountsSearch.entity().getNeedsCleanup(), SearchCriteria.Op.EQ);
        CleanupForDisabledAccountsSearch.and("removed", CleanupForDisabledAccountsSearch.entity().getRemoved(), SearchCriteria.Op.NULL);
        CleanupForDisabledAccountsSearch.and("state", CleanupForDisabledAccountsSearch.entity().getState(), SearchCriteria.Op.EQ);
        CleanupForDisabledAccountsSearch.done();

        NonProjectAccountSearch = createSearchBuilder();
        NonProjectAccountSearch.and("accountName", NonProjectAccountSearch.entity().getAccountName(), SearchCriteria.Op.EQ);
        NonProjectAccountSearch.and("domainId", NonProjectAccountSearch.entity().getDomainId(), SearchCriteria.Op.EQ);
        NonProjectAccountSearch.and("state", NonProjectAccountSearch.entity().getState(), SearchCriteria.Op.EQ);
        NonProjectAccountSearch.and("type", NonProjectAccountSearch.entity().getType(), SearchCriteria.Op.NEQ);
        NonProjectAccountSearch.done();

        AccountIdsSearch = createSearchBuilder(Long.class);
        AccountIdsSearch.selectFields(AccountIdsSearch.entity().getId());
        AccountIdsSearch.and("ids", AccountIdsSearch.entity().getDomainId(), Op.IN);
        AccountIdsSearch.done();

        AccountByRoleSearch = createSearchBuilder();
        AccountByRoleSearch.and("roleId", AccountByRoleSearch.entity().getRoleId(), SearchCriteria.Op.EQ);
        AccountByRoleSearch.done();

        ActiveDomainCount = createSearchBuilder(Long.class);
        ActiveDomainCount.select(null, Func.COUNT, null);
        ActiveDomainCount.and("domain", ActiveDomainCount.entity().getDomainId(), SearchCriteria.Op.EQ);
        ActiveDomainCount.and("state", ActiveDomainCount.entity().getState(), SearchCriteria.Op.EQ);
        ActiveDomainCount.groupBy(ActiveDomainCount.entity().getDomainId());
        ActiveDomainCount.done();
    }

    @Override
    public List<AccountVO> findCleanupsForRemovedAccounts(Long domainId) {
        SearchCriteria<AccountVO> sc = CleanupForRemovedAccountsSearch.create();
        sc.setParameters("cleanup", true);

        if (domainId != null) {
            sc.setParameters("domainid", domainId);
        }

        return searchIncludingRemoved(sc, null, null, false);
    }

    @Override
    public List<AccountVO> findCleanupsForDisabledAccounts() {
        SearchCriteria<AccountVO> sc = CleanupForDisabledAccountsSearch.create();
        sc.setParameters("cleanup", true);
        sc.setParameters("state", State.DISABLED);

        return listBy(sc);
    }

    @Override
    public Ternary<User, Account, ApiKeyPair> findUserAccountByApiKey(String apiKey) {
        TransactionLegacy txn = TransactionLegacy.currentTxn();
        PreparedStatement pstmt = null;
        Ternary<User, Account, ApiKeyPair> userAcctTernary = null;
        try {
            String sql = FIND_USER_ACCOUNT_BY_API_KEY;
            pstmt = txn.prepareAutoCloseStatement(sql);
            pstmt.setString(1, apiKey);
            ResultSet rs = pstmt.executeQuery();
            // TODO:  make sure we don't have more than 1 result?  ApiKey had better be unique
            if (rs.next()) {
                User user = new UserVO(rs.getLong(1));
                user.setUsername(rs.getString(2));
                user.setAccountId(rs.getLong(3));
                user.setState(State.getValueOf(rs.getString(4)));

                AccountVO account = new AccountVO(rs.getLong(5));
                account.setAccountName(rs.getString(6));
                account.setType(Account.Type.getFromValue(rs.getInt(7)));
                account.setRoleId(rs.getLong(8));
                account.setDomainId(rs.getLong(9));
                account.setState(State.getValueOf(rs.getString(10)));

                ApiKeyPairVO keyPair = new ApiKeyPairVO(rs.getLong(11));
                keyPair.setStartDate(rs.getDate(12));
                keyPair.setEndDate(rs.getDate(13));
                keyPair.setSecretKey(DBEncryptionUtil.decrypt(rs.getString(14)));
                keyPair.setRemoved(rs.getDate(15));

                userAcctTernary = new Ternary<User, Account, ApiKeyPair>(user, account, keyPair);
            }
        } catch (Exception e) {
            logger.warn("Exception finding user/acct by api key: " + apiKey, e);
        }
        return userAcctTernary;
    }

    @Override
    public List<AccountVO> findAccountsLike(String accountName) {
        return findAccountsLike(accountName, null).first();
    }

    @Override
    public Pair<List<AccountVO>, Integer> findAccountsLike(String accountName, Filter filter) {
        SearchCriteria<AccountVO> sc = createSearchCriteria();
        if (StringUtils.isNotEmpty(accountName)) {
            sc.addAnd("accountName", SearchCriteria.Op.LIKE, "%" + accountName + "%");
        }
        return searchAndCount(sc, filter);
    }

    @Override
    public Account findEnabledAccount(String accountName, Long domainId) {
        SearchCriteria<AccountVO> sc = AllFieldsSearch.create("accountName", accountName);
        sc.setParameters("domainId", domainId);
        sc.setParameters("state", State.ENABLED);
        return findOneBy(sc);
    }

    @Override
    public Account findEnabledNonProjectAccount(String accountName, Long domainId) {
        SearchCriteria<AccountVO> sc = NonProjectAccountSearch.create("accountName", accountName);
        sc.setParameters("domainId", domainId);
        sc.setParameters("state", State.ENABLED);
        sc.setParameters("type", Account.Type.PROJECT);
        return findOneBy(sc);
    }

    @Override
    public Account findActiveAccount(String accountName, Long domainId) {
        SearchCriteria<AccountVO> sc = AllFieldsSearch.create("accountName", accountName);
        sc.setParameters("domainId", domainId);
        return findOneBy(sc);
    }

    @Override
    public Account findActiveAccountById(Long accountId, Long domainId) {
        SearchCriteria<AccountVO> sc = AllFieldsSearch.create("id", accountId);
        sc.setParameters("domainId", domainId);
        return findOneBy(sc);
    }

    @Override
    public Account findActiveNonProjectAccount(String accountName, Long domainId) {
        SearchCriteria<AccountVO> sc = NonProjectAccountSearch.create("accountName", accountName);
        sc.setParameters("domainId", domainId);
        sc.setParameters("type", Account.Type.PROJECT);
        return findOneBy(sc);
    }

    @Override
    public Account findAccountIncludingRemoved(String accountName, Long domainId) {
        SearchCriteria<AccountVO> sc = AllFieldsSearch.create("accountName", accountName);
        sc.setParameters("domainId", domainId);
        return findOneIncludingRemovedBy(sc);
    }

    @Override
    public Account findNonProjectAccountIncludingRemoved(String accountName, Long domainId) {
        SearchCriteria<AccountVO> sc = NonProjectAccountSearch.create("accountName", accountName);
        sc.setParameters("domainId", domainId);
        sc.setParameters("type", Account.Type.PROJECT);
        return findOneIncludingRemovedBy(sc);
    }

    @Override
    public List<AccountVO> listAccounts(String accountName, Long domainId, Filter filter) {
        SearchCriteria<AccountVO> sc = AllFieldsSearch.create("accountName", accountName);
        sc.setParameters("domainId", domainId);
        return listIncludingRemovedBy(sc, filter);
    }

    @Override
    public List<AccountVO> findActiveAccounts(Long maxAccountId, Filter filter) {
        if (maxAccountId == null) {
            return null;
        }

        SearchCriteria<AccountVO> sc = createSearchCriteria();
        sc.addAnd("id", SearchCriteria.Op.LTEQ, maxAccountId);

        return listBy(sc, filter);
    }

    @Override
    public List<AccountVO> findRecentlyDeletedAccounts(Long maxAccountId, Date earliestRemovedDate, Filter filter) {
        if (earliestRemovedDate == null) {
            return null;
        }
        SearchCriteria<AccountVO> sc = createSearchCriteria();
        if (maxAccountId != null) {
            sc.addAnd("id", SearchCriteria.Op.LTEQ, maxAccountId);
        }
        sc.addAnd("removed", SearchCriteria.Op.NNULL);
        sc.addAnd("removed", SearchCriteria.Op.GTEQ, earliestRemovedDate);

        return listIncludingRemovedBy(sc, filter);
    }

    @Override
    public List<AccountVO> findNewAccounts(Long minAccountId, Filter filter) {
        if (minAccountId == null) {
            return null;
        }

        SearchCriteria<AccountVO> sc = createSearchCriteria();
        sc.addAnd("id", SearchCriteria.Op.GT, minAccountId);

        return listIncludingRemovedBy(sc, filter);
    }

    @Override
    public List<AccountVO> findActiveAccountsForDomain(Long domain) {
        SearchCriteria<AccountVO> sc = DomainAccountsSearch.create();
        sc.setParameters("domainId", domain);
        return listBy(sc);
    }

    @Override
    public List<AccountVO> findAccountsByRole(Long roleId) {
        SearchCriteria<AccountVO> sc = AccountByRoleSearch.create();
        sc.setParameters("roleId", roleId);
        return listBy(sc);
    }

    @Override
    public void markForCleanup(long accountId) {
        AccountVO account = findByIdIncludingRemoved(accountId);
        if (!account.getNeedsCleanup()) {
            account.setNeedsCleanup(true);
            if (!update(accountId, account)) {
                logger.warn("Failed to mark account id=" + accountId + " for cleanup");
            }
        }
    }

    @Override
    public List<Long> getAccountIdsForDomains(List<Long> domainIds) {
        SearchCriteria<Long> sc = AccountIdsSearch.create();
        sc.setParameters("ids", domainIds.toArray(new Object[domainIds.size()]));
        return customSearchIncludingRemoved(sc, null);
    }

    @Override
    public long getDomainIdForGivenAccountId(long id) {
        long domain_id = -1;
        try {
            AccountVO account_vo = findById(id);
            domain_id = account_vo.getDomainId();
        }
        catch (Exception e) {
            logger.warn("getDomainIdForGivenAccountId: Exception :" + e.getMessage());
        }
        finally {
            return domain_id;
        }
    }

    @Override
    public int getActiveDomains() {
        SearchCriteria<Long> sc = ActiveDomainCount.create();
        sc.setParameters("state", "enabled");
        return customSearch(sc, null).size();
    }
}
