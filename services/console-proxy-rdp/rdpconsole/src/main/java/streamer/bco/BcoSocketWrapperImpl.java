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
package streamer.bco;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.crypto.tls.Certificate;
import org.bouncycastle.crypto.tls.DefaultTlsClient;
import org.bouncycastle.crypto.tls.ServerOnlyTlsAuthentication;
import org.bouncycastle.crypto.tls.TlsAuthentication;
import org.bouncycastle.crypto.tls.TlsClientProtocol;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import streamer.Direction;
import streamer.Event;
import streamer.SocketWrapperImpl;
import streamer.ssl.SSLState;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.security.Security;

@SuppressWarnings("deprecation")
public class BcoSocketWrapperImpl extends SocketWrapperImpl {
    private static final Logger s_logger = LogManager.getLogger(BcoSocketWrapperImpl.class);

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private TlsClientProtocol bcoSslSocket;

    public BcoSocketWrapperImpl(String id, SSLState sslState) {
        super(id, sslState);
    }

    @Override
    public void upgradeToSsl() {

        if (sslSocket != null)
            // Already upgraded
            return;

        if (verbose)
            System.out.println("[" + this + "] INFO: Upgrading socket to SSL.");

        try {

            SecureRandom secureRandom = new SecureRandom();
            bcoSslSocket = new TlsClientProtocol(socket.getInputStream(), socket.getOutputStream(), secureRandom);

            bcoSslSocket.connect(new DefaultTlsClient() {
                @Override
                public TlsAuthentication getAuthentication() throws IOException {
                    return new ServerOnlyTlsAuthentication() {
                        @Override
                        public void notifyServerCertificate(final Certificate certificate) throws IOException {
                            try {
                                if (sslState != null) {
                                    sslState.serverCertificateSubjectPublicKeyInfo = certificate.getCertificateAt(0).getSubjectPublicKeyInfo().getEncoded();
                                }
                            } catch (IOException e) {
                                throw new RuntimeException("Cannot get server public key.", e);
                            }
                        }
                    };
                }
            });

            InputStream sis = bcoSslSocket.getInputStream();
            source.setInputStream(sis);

            OutputStream sos = bcoSslSocket.getOutputStream();
            sink.setOutputStream(sos);

        } catch (Exception e) {
            throw new RuntimeException("Cannot upgrade socket to SSL: " + e.getMessage(), e);
        }

    }

    @Override
    public void shutdown() {
        try {
            handleEvent(Event.STREAM_CLOSE, Direction.IN);
        } catch (Exception e) {
            s_logger.info("[ignored]"
                    + "failure handling close event for bso input stream: " + e.getLocalizedMessage());
        }
        try {
            handleEvent(Event.STREAM_CLOSE, Direction.OUT);
        } catch (Exception e) {
            s_logger.info("[ignored]"
                    + "failure handling close event for bso output stream: " + e.getLocalizedMessage());
        }
        try {
            if (bcoSslSocket != null)
                bcoSslSocket.close();
        } catch (Exception e) {
            s_logger.info("[ignored]"
                    + "failure handling close event for bso socket: " + e.getLocalizedMessage());
        }
        try {
            socket.close();
        } catch (Exception e) {
            s_logger.info("[ignored]"
                    + "failure handling close event for socket: " + e.getLocalizedMessage());
        }
    }

    @Override
    public String toString() {
        return "BcoSocketWrapper(" + id + ")";
    }

}
