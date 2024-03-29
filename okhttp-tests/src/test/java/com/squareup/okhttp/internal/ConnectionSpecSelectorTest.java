/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.okhttp.internal;

import com.squareup.okhttp.ConnectionSpec;
import com.squareup.okhttp.TlsVersion;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConnectionSpecSelectorTest {
  static {
    Internal.initializeInstanceForTests();
  }

  public static final SSLHandshakeException RETRYABLE_EXCEPTION = new SSLHandshakeException(
      "Simulated handshake exception");

  private SSLContext sslContext = SslContextBuilder.localhost();

  // Android-changed: Use TLS 1.3 and 1.2 for testing
  private static final ConnectionSpec TLS_SPEC_1_3 =
      new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
      .tlsVersions(TlsVersion.TLS_1_3)
      .build();

  private static final ConnectionSpec TLS_SPEC_1_2 =
      new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
      .tlsVersions(TlsVersion.TLS_1_2)
      .build();


  @Test
  public void nonRetryableIOException() throws Exception {
    ConnectionSpecSelector connectionSpecSelector =
    // Android-changed: Use TLS 1.3 and 1.2 for testing
        createConnectionSpecSelector(TLS_SPEC_1_3, TLS_SPEC_1_2);
    SSLSocket socket = createSocketWithEnabledProtocols(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2);
    connectionSpecSelector.configureSecureSocket(socket);

    boolean retry = connectionSpecSelector.connectionFailed(
        new IOException("Non-handshake exception"));
    assertFalse(retry);
    socket.close();
  }

  @Test
  public void nonRetryableSSLHandshakeException() throws Exception {
    ConnectionSpecSelector connectionSpecSelector =
    // Android-changed: Use TLS 1.3 and 1.2
        createConnectionSpecSelector(TLS_SPEC_1_3, TLS_SPEC_1_2);
    SSLSocket socket = createSocketWithEnabledProtocols(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2);
    connectionSpecSelector.configureSecureSocket(socket);

    SSLHandshakeException trustIssueException =
        new SSLHandshakeException("Certificate handshake exception");
    trustIssueException.initCause(new CertificateException());
    boolean retry = connectionSpecSelector.connectionFailed(trustIssueException);
    assertFalse(retry);
    socket.close();
  }

  @Test
  public void retryableSSLHandshakeException() throws Exception {
    ConnectionSpecSelector connectionSpecSelector =
    // Android-changed: Use TLS 1.3 and 1.2
        createConnectionSpecSelector(TLS_SPEC_1_3, TLS_SPEC_1_2);
    SSLSocket socket = createSocketWithEnabledProtocols(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2);
    connectionSpecSelector.configureSecureSocket(socket);

    boolean retry = connectionSpecSelector.connectionFailed(RETRYABLE_EXCEPTION);
    assertTrue(retry);
    socket.close();
  }

  @Test
  public void someFallbacksSupported() throws Exception {
    ConnectionSpec sslV3 =
        new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .tlsVersions(TlsVersion.SSL_3_0)
            .build();

    // Android-changed: Use TLS 1.3 and 1.2 for testing
    ConnectionSpecSelector connectionSpecSelector = createConnectionSpecSelector(
        TLS_SPEC_1_3, TLS_SPEC_1_2, sslV3);

    // Android-changed: Use TLS 1.3 and 1.2 for testing
    TlsVersion[] enabledSocketTlsVersions = { TlsVersion.TLS_1_3, TlsVersion.TLS_1_2 };
    SSLSocket socket = createSocketWithEnabledProtocols(enabledSocketTlsVersions);

    // Android-changed: Use TLS 1.3 and 1.2 for testing
    // TLS_SPEC_1_3 is used here.
    connectionSpecSelector.configureSecureSocket(socket);
    assertEnabledProtocols(socket, TlsVersion.TLS_1_3);

    boolean retry = connectionSpecSelector.connectionFailed(RETRYABLE_EXCEPTION);
    assertTrue(retry);
    socket.close();

    // Android-changed: Use TLS 1.3 and 1.2 for testing
    // TLS_SPEC_1_2 is used here.
    socket = createSocketWithEnabledProtocols(enabledSocketTlsVersions);
    connectionSpecSelector.configureSecureSocket(socket);
    assertEnabledProtocols(socket, TlsVersion.TLS_1_2);

    retry = connectionSpecSelector.connectionFailed(RETRYABLE_EXCEPTION);
    assertFalse(retry);
    socket.close();

    // sslV3 is not used because SSLv3 is not enabled on the socket.
  }

  private static ConnectionSpecSelector createConnectionSpecSelector(
      ConnectionSpec... connectionSpecs) {
    return new ConnectionSpecSelector(Arrays.asList(connectionSpecs));
  }

  private SSLSocket createSocketWithEnabledProtocols(TlsVersion... tlsVersions) throws IOException {
    SSLSocket socket = (SSLSocket) sslContext.getSocketFactory().createSocket();
    socket.setEnabledProtocols(javaNames(tlsVersions));
    return socket;
  }

  private static void assertEnabledProtocols(SSLSocket socket, TlsVersion... required) {
    Set<String> actual = new LinkedHashSet<>(Arrays.asList(socket.getEnabledProtocols()));
    Set<String> expected = new LinkedHashSet<>(Arrays.asList(javaNames(required)));
    assertEquals(expected, actual);
  }

  private static String[] javaNames(TlsVersion... tlsVersions) {
    String[] protocols = new String[tlsVersions.length];
    for (int i = 0; i < tlsVersions.length; i++) {
      protocols[i] = tlsVersions[i].javaName();
    }
    return protocols;
  }
}
