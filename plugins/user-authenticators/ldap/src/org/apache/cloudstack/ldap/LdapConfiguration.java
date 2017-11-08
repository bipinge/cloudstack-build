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
package org.apache.cloudstack.ldap;

import java.util.List;

import javax.inject.Inject;
import javax.naming.directory.SearchControls;

import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;

import com.cloud.utils.Pair;
import org.apache.cloudstack.ldap.dao.LdapConfigurationDao;

public class LdapConfiguration implements Configurable{
    private final static String factory = "com.sun.jndi.ldap.LdapCtxFactory";

    private static final ConfigKey<Long> ldapReadTimeout = new ConfigKey<Long>(Long.class, "ldap.read.timeout", "Advanced", "1000",
        "LDAP connection Timeout in milli sec", true, ConfigKey.Scope.Global, 1l);

    private static final ConfigKey<Integer> ldapPageSize = new ConfigKey<Integer>(Integer.class, "ldap.request.page.size", "Advanced", "1000",
                                                                               "page size sent to ldap server on each request to get user", true, ConfigKey.Scope.Global, 1);
    private static final ConfigKey<String> ldapProvider = new ConfigKey<String>(String.class, "ldap.provider", "Advanced", "openldap", "ldap provider ex:openldap, microsoftad",
                                                                                true, ConfigKey.Scope.Global, null);

    private static final ConfigKey<Boolean> ldapEnableNestedGroups = new ConfigKey<Boolean>(Boolean.class, "ldap.nested.groups.enable", "Advanced", "true",
            "if true, nested groups will also be queried", true, ConfigKey.Scope.Global, null);

    private static final ConfigKey<String> ldapMemberOfAttribute = new ConfigKey<String>(String.class, "ldap.memberof.attribute", "Advanced", "memberof",
            "the reverse membership attibute for group members", true, ConfigKey.Scope.Global, null);

    private static final ConfigKey<String> ldapBaseDn = new ConfigKey<String>(
            "Advanced",
            String.class,
            "ldap.basedn",
            null,
            "Sets the basedn for LDAP",
            true,
            ConfigKey.Scope.Domain);

    private static final ConfigKey<String> ldapBindPassword = new ConfigKey<String>(
            "Advanced",
            String.class,
            "ldap.bind.password",
            null,
            "Sets the bind password for LDAP",
            true,
            ConfigKey.Scope.Domain);
    private static final ConfigKey<String> ldapBindPrincipal = new ConfigKey<String>(
            "Advanced",
            String.class,
            "ldap.bind.principal",
            null,
            "Sets the bind principal for LDAP",
            true,
            ConfigKey.Scope.Domain);
    private static final ConfigKey<String> ldapEmailAttribute = new ConfigKey<String>(
            "Advanced",
            String.class,
            "ldap.email.attribute",
            "mail",
            "Sets the email attribute used within LDAP",
            true,
            ConfigKey.Scope.Domain);
    private static final ConfigKey<String> ldapFirstnameAttribute = new ConfigKey<String>(
            "Advanced",
            String.class,
            "ldap.firstname.attribute",
            "givenname",
            "Sets the firstname attribute used within LDAP",
            true,
            ConfigKey.Scope.Domain);
    private static final ConfigKey<String> ldapLastnameAttribute = new ConfigKey<String>(
            "Advanced",
            String.class, "ldap.lastname.attribute",
            "sn",
            "Sets the lastname attribute used within LDAP",
            true,
            ConfigKey.Scope.Domain);
    private static final ConfigKey<String> ldapUsernameAttribute = new ConfigKey<String>(
            "Advanced",
            String.class,
            "ldap.username.attribute",
            "uid",
            "Sets the username attribute used within LDAP",
            true,
            ConfigKey.Scope.Domain);
    private static final ConfigKey<String> ldapUserObject = new ConfigKey<String>(
            "Advanced",
            String.class,
            "ldap.user.object",
            "inetOrgPerson",
            "Sets the object type of users within LDAP",
            true,
            ConfigKey.Scope.Domain);
    private static final ConfigKey<String> ldapSearchGroupPrinciple = new ConfigKey<String>(
            "Advanced",
            String.class,
            "ldap.search.group.principle",
            null,
            "Sets the principle of the group that users must be a member of",
            true,
            ConfigKey.Scope.Domain);
    private static final ConfigKey<String> ldapGroupObject = new ConfigKey<String>(
            "Advanced",
            String.class,
            "ldap.group.object",
            "groupOfUniqueNames",
            "Sets the object type of groups within LDAP",
            true,
            ConfigKey.Scope.Domain);
    private static final ConfigKey<String> ldapGroupUniqueMemberAttribute = new ConfigKey<String>(
            "Advanced",
            String.class,
            "ldap.group.user.uniquemember",
            "uniquemember",
            "Sets the attribute for uniquemembers within a group",
            true,
            ConfigKey.Scope.Domain);

    private static final ConfigKey<String> ldapTrustStore = new ConfigKey<String>(
            "Advanced",
            String.class,
            "ldap.truststore",
            null,
            "Sets the path to the truststore to use for SSL",
            true);
    private static final ConfigKey<String> ldapTrustStorePassword = new ConfigKey<String>(
            "Advanced",
            String.class,
            "ldap.truststore.password",
            null,
            "Sets the password for the truststore",
            true);

    private final static int scope = SearchControls.SUBTREE_SCOPE;

    @Inject
    private LdapConfigurationDao _ldapConfigurationDao;

    public LdapConfiguration() {
    }

    public LdapConfiguration(final LdapConfigurationDao ldapConfigurationDao) {
        _ldapConfigurationDao = ldapConfigurationDao;
    }

    @Deprecated
    public LdapConfiguration(final ConfigurationDao configDao, final LdapConfigurationDao ldapConfigurationDao) {
        _ldapConfigurationDao = ldapConfigurationDao;
    }

    public String getAuthentication() {
        if ((getBindPrincipal() == null) && (getBindPassword() == null)) {
            return "none";
        } else {
            return "simple";
        }
    }

    public String getBaseDn() {
        return ldapBaseDn.defaultValue();
    }

    public String getBaseDn(long domain) {
        return ldapBaseDn.valueIn(domain);
    }

    public String getBindPassword() {
        return ldapBindPassword.value();
    }

    public String getBindPassword(long domain) {
        return ldapBindPassword.valueIn(domain);
    }

    public String getBindPrincipal() {
        return ldapBindPrincipal.value();
    }

    public String getBindPrincipal(long domain) {
        return ldapBindPrincipal.valueIn(domain);
    }

    public String getEmailAttribute() {
        return ldapEmailAttribute.value();
    }

    public String getEmailAttribute(long domain) {
        return ldapEmailAttribute.valueIn(domain);
    }

    public String getFactory() {
        return factory;
    }

    public String getFirstnameAttribute() {
        return ldapFirstnameAttribute.value();
    }

    public String getFirstnameAttribute(long domain) {
        return ldapFirstnameAttribute.valueIn(domain);
    }

    public String getLastnameAttribute() {
        return ldapLastnameAttribute.value();
    }

    public String getLastnameAttribute(long domain) {
        return ldapLastnameAttribute.valueIn(domain);
    }

    public String getProviderUrl() {
        final String protocol = getSSLStatus() == true ? "ldaps://" : "ldap://";
        final Pair<List<LdapConfigurationVO>, Integer> result = _ldapConfigurationDao.searchConfigurations(null, 0);
        final StringBuilder providerUrls = new StringBuilder();
        String delim = "";
        for (final LdapConfigurationVO resource : result.first()) {
            final String providerUrl = protocol + resource.getHostname() + ":" + resource.getPort();
            providerUrls.append(delim).append(providerUrl);
            delim = " ";
        }
        return providerUrls.toString();
    }

    public String[] getReturnAttributes() {
        return new String[] {getUsernameAttribute(), getEmailAttribute(), getFirstnameAttribute(), getLastnameAttribute(), getCommonNameAttribute(),
                getUserAccountControlAttribute()};
    }

    public int getScope() {
        return scope;
    }

    public String getSearchGroupPrinciple() {
        return ldapSearchGroupPrinciple.value();
    }

    public String getSearchGroupPrinciple(long domain) {
        return ldapSearchGroupPrinciple.valueIn(domain);
    }

    public boolean getSSLStatus() {
        boolean sslStatus = false;
        if (getTrustStore() != null && getTrustStorePassword() != null) {
            sslStatus = true;
        }
        return sslStatus;
    }

    public String getTrustStore() {
        return ldapTrustStore.value();
    }

    public String getTrustStorePassword() {
        return ldapTrustStorePassword.value();
    }

    public String getUsernameAttribute() {
        return ldapUsernameAttribute.value();
    }

    public String getUsernameAttribute(long domain) {
        return ldapUsernameAttribute.valueIn(domain);
    }

    public String getUserObject() {
        return ldapUserObject.value();
    }

    public String getUserObject(long domain) {
        return ldapUserObject.valueIn(domain);
    }

    public String getGroupObject() {
        return ldapGroupObject.value();
    }

    public String getGroupObject(long domain) {
        return ldapGroupObject.valueIn(domain);
    }

    public String getGroupUniqueMemeberAttribute() {
        return ldapGroupUniqueMemberAttribute.value();
    }

    public String getGroupUniqueMemeberAttribute(long domain) {
        return ldapGroupUniqueMemberAttribute.valueIn(domain);
    }

    public String getCommonNameAttribute() {
        return "cn";
    }

    public String getUserAccountControlAttribute() {
        return "userAccountControl";
    }

    public Long getReadTimeout() {
        return ldapReadTimeout.value();
    }

    public Integer getLdapPageSize() {
        return ldapPageSize.value();
    }

    public LdapUserManager.Provider getLdapProvider() {
        LdapUserManager.Provider provider;
        try {
            provider = LdapUserManager.Provider.valueOf(ldapProvider.value().toUpperCase());
        } catch (IllegalArgumentException ex) {
            //openldap is the default
            provider = LdapUserManager.Provider.OPENLDAP;
        }
        return provider;
    }

    public boolean isNestedGroupsEnabled() {
        return ldapEnableNestedGroups.value();
    }

    public static String getLdapMemberOfAttribute() {
        return ldapMemberOfAttribute.value();
    }

    @Override
    public String getConfigComponentName() {
        return LdapConfiguration.class.getSimpleName();
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey<?>[]{
                ldapReadTimeout,
                ldapPageSize,
                ldapProvider,
                ldapEnableNestedGroups,
                ldapBaseDn,
                ldapBindPassword,
                ldapBindPrincipal,
                ldapEmailAttribute,
                ldapFirstnameAttribute,
                ldapLastnameAttribute,
                ldapUsernameAttribute,
                ldapUserObject,
                ldapSearchGroupPrinciple,
                ldapGroupObject,
                ldapGroupUniqueMemberAttribute,
                ldapTrustStore,
                ldapTrustStorePassword,
                ldapMemberOfAttribute
        };
    }
}
