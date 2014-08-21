/*******************************************************************************
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.cloud.hypervisor.ovm3.object;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.TimeZone;

import org.apache.log4j.Logger;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.TimingOutCallback;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.apache.xmlrpc.client.XmlRpcClientRequestImpl;

public class Connection extends XmlRpcClient {
    private static final Logger LOGGER = Logger.getLogger(Connection.class);
    private final XmlRpcClientConfigImpl xmlClientConfig = new XmlRpcClientConfigImpl();
    public XmlRpcClient xmlClient;
    private String hostUser = null;
    private String hostPass = null;
    private String hostIp;
    private Integer hostPort = 8898;
    private Boolean hostUseSsl = false;
    private String cert = "";
    private String key = "";
    /* default to 20 mins ? */
    private final Integer timeoutMs = 1200;
    private final Integer timeoutS = timeoutMs * 1000;

    public Connection() {
        // xmlClient = setupXmlClient();
    }

    public Connection(String ip, Integer port, String username, String password) {
        hostIp = ip;
        hostPort = port;
        hostUser = username;
        hostPass = password;
        xmlClient = setupXmlClient();
    }

    public Connection(String ip, String username, String password) {
        hostIp = ip;
        hostUser = username;
        hostPass = password;
        xmlClient = setupXmlClient();
    }

    private XmlRpcClient setupXmlClient() {
        final XmlRpcClient client = new XmlRpcClient();

        URL url;
        try {
            /* TODO: should add SSL checking here! */
            String prot = "http";
            if (hostUseSsl) {
                prot = "https";
            }
            url = new URL(prot + "://" + hostIp + ":" + hostPort.toString());
            xmlClientConfig.setTimeZone(TimeZone.getTimeZone("UTC"));
            xmlClientConfig.setServerURL(url);
            /* disable, we use asyncexecute to control timeout */
            xmlClientConfig.setReplyTimeout(0);
            /* default to 60 secs */
            xmlClientConfig.setConnectionTimeout(60000);
            /* reply time is 5 mins */
            xmlClientConfig.setReplyTimeout(60 * 15000);
            if (hostUser != null && hostPass != null) {
                xmlClientConfig.setBasicUserName(hostUser);
                xmlClientConfig.setBasicPassword(hostPass);
            }
            xmlClientConfig.setXmlRpcServer(null);
            client.setConfig(xmlClientConfig);
            client.setTypeFactory(new RpcTypeFactory(client));
        } catch (MalformedURLException e) {
            LOGGER.info("Incorrect URL: ", e);
        }
        return client;
    }

    public Object call(String method, List<?> params) throws XmlRpcException {
        return callTimeoutInSec(method, params, this.timeoutS);
    }

    public Object call(String method, List<?> params, boolean debug)
            throws XmlRpcException {
        return callTimeoutInSec(method, params, this.timeoutS, debug);
    }

    public Object callTimeoutInSec(String method, List<?> params,
            int timeout, boolean debug) throws XmlRpcException {
        TimingOutCallback callback = new TimingOutCallback(timeout * 1000);
        if (debug) {
            /*
             * some parameters including hostUser password should not be printed in
             * log
             */
            LOGGER.debug("Call Ovm3 agent: " + method + " with " + params);
        }
        long startTime = System.currentTimeMillis();
        try {
            /* returns actual xml */
            XmlRpcClientRequestImpl req = new XmlRpcClientRequestImpl(xmlClient.getClientConfig(), method, params);
            xmlClient.executeAsync(req, callback);
            return callback.waitForResponse();
        } catch (TimingOutCallback.TimeoutException e) {
            LOGGER.info("Timeout: ", e);
            throw new XmlRpcException(e.getMessage());
        } catch (XmlRpcException e) {
            LOGGER.info("XML RPC Exception occured: ", e);
            throw e;
        } catch (RuntimeException e) {
            LOGGER.info("Runtime Exception: ", e);
            throw new XmlRpcException(e.getMessage());
        } catch (Throwable e) {
            LOGGER.error("Holy crap batman!: ", e);
            throw new XmlRpcException(e.getMessage(), e);
        } finally {
            long endTime = System.currentTimeMillis();
            /* in seconds */
            float during = (endTime - startTime) / (float) 1000;
            LOGGER.debug("Ovm3 call " + method + " finished in " + during
                    + " secs, on " + hostIp + ":" + hostPort);
        }
    }

    public Object callTimeoutInSec(String method, List<?> params, int timeout)  throws XmlRpcException {
        return callTimeoutInSec(method, params, timeout, true);
    }

    public String getIp() {
        return hostIp;
    }

    public Integer getPort() {
        return hostPort;
    }

    public String getUserName() {
        return hostUser;
    }

    public String getPassword() {
        return hostPass;
    }

    public Boolean getUseSsl() {
        return hostUseSsl;
    }

    public String getCert() {
        return cert;
    }

    public String getKey() {
        return key;
    }
}
