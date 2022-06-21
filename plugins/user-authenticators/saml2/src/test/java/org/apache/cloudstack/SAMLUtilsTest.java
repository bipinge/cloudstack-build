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

package org.apache.cloudstack;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.regex.Pattern;

import org.apache.cloudstack.saml.SAML2AuthManager;
import org.apache.cloudstack.saml.SAMLUtils;
import org.apache.cloudstack.utils.security.CertUtils;
import org.junit.Test;
import org.opensaml.DefaultBootstrap;
import org.opensaml.saml2.core.AuthnRequest;
import org.opensaml.saml2.core.LogoutRequest;

import junit.framework.TestCase;

public class SAMLUtilsTest extends TestCase {

    @Test
    public void testGenerateSecureRandomId() throws Exception {
        assertTrue(SAMLUtils.generateSecureRandomId().length() > 0);
    }

    @Test
    public void testGenerateSecureRandomId2() throws Exception {
        for (int i = 0; i < 20; i++) {
            String randomId = SAMLUtils.generateSecureRandomId();
            System.out.println("randomId is " + randomId);
            assertTrue(Pattern.compile("^[a-z]").matcher(randomId).find());
        }
    }

    @Test
    public void testBuildAuthnRequestObject() throws Exception {
        String consumerUrl = "http://someurl.com";
        String idpUrl = "http://idp.domain.example";
        String spId = "cloudstack";
        String authnId = SAMLUtils.generateSecureRandomId();
        AuthnRequest req = SAMLUtils.buildAuthnRequestObject(authnId, spId, idpUrl, consumerUrl);
        assertEquals(req.getAssertionConsumerServiceURL(), consumerUrl);
        assertEquals(req.getDestination(), idpUrl);
        assertEquals(req.getIssuer().getValue(), spId);
    }

    @Test
    public void testBuildAuthnRequestUrlWithoutQueryParam() throws Exception {
        String consumerUrl = "http://someurl.com";
        String idpUrl = "http://idp.domain.example";
        String spId = "cloudstack";
        String authnId = SAMLUtils.generateSecureRandomId();
        DefaultBootstrap.bootstrap();
        AuthnRequest req = SAMLUtils.buildAuthnRequestObject(authnId, spId, idpUrl, consumerUrl);
        String redirectUrl = idpUrl.contains("?") ? idpUrl + "&" + SAMLUtils.generateSAMLRequestSignature("SAMLRequest=" + SAMLUtils.encodeSAMLRequest(req), null, SAML2AuthManager.SAMLSignatureAlgorithm.value()) : idpUrl + "?" + SAMLUtils.generateSAMLRequestSignature("SAMLRequest=" + SAMLUtils.encodeSAMLRequest(req), null, SAML2AuthManager.SAMLSignatureAlgorithm.value());
        assertEquals(redirectUrl, idpUrl + "?" + SAMLUtils.generateSAMLRequestSignature("SAMLRequest=" + SAMLUtils.encodeSAMLRequest(req), null, SAML2AuthManager.SAMLSignatureAlgorithm.value()));
    }

    @Test
    public void testBuildAuthnRequestUrlWithQueryParam() throws Exception {
        String consumerUrl = "http://someurl.com";
        String idpUrl = "http://idp.domain.example?idpid=CX1298373";
        String spId = "cloudstack";
        String authnId = SAMLUtils.generateSecureRandomId();
        DefaultBootstrap.bootstrap();
        AuthnRequest req = SAMLUtils.buildAuthnRequestObject(authnId, spId, idpUrl, consumerUrl);
        String redirectUrl = idpUrl.contains("?") ? idpUrl + "&" + SAMLUtils.generateSAMLRequestSignature("SAMLRequest=" + SAMLUtils.encodeSAMLRequest(req), null, SAML2AuthManager.SAMLSignatureAlgorithm.value()) : idpUrl + "?" + SAMLUtils.generateSAMLRequestSignature("SAMLRequest=" + SAMLUtils.encodeSAMLRequest(req), null, SAML2AuthManager.SAMLSignatureAlgorithm.value());
        assertEquals(redirectUrl, idpUrl + "&" + SAMLUtils.generateSAMLRequestSignature("SAMLRequest=" + SAMLUtils.encodeSAMLRequest(req), null, SAML2AuthManager.SAMLSignatureAlgorithm.value()));
    }

    @Test
    public void testBuildLogoutRequest() throws Exception {
        String logoutUrl = "http://logoutUrl";
        String spId = "cloudstack";
        String nameId = "_12345";
        LogoutRequest req = SAMLUtils.buildLogoutRequest(logoutUrl, spId, nameId);
        assertEquals(req.getDestination(), logoutUrl);
        assertEquals(req.getIssuer().getValue(), spId);
    }

    @Test
    public void testX509Helpers() throws Exception {
        KeyPair keyPair = CertUtils.generateRandomKeyPair(4096);

        String privateKeyString = SAMLUtils.encodePrivateKey(keyPair.getPrivate());
        String publicKeyString = SAMLUtils.encodePublicKey(keyPair.getPublic());

        PrivateKey privateKey = SAMLUtils.decodePrivateKey(privateKeyString);
        PublicKey publicKey = SAMLUtils.decodePublicKey(publicKeyString);

        assertNotNull(privateKey);
        assertNotNull(publicKey);
        assertTrue(privateKey.equals(keyPair.getPrivate()));
        assertTrue(publicKey.equals(keyPair.getPublic()));
    }
}