/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.geode.management;

import static org.apache.geode.distributed.ConfigurationProperties.SSL_CIPHERS;
import static org.apache.geode.distributed.ConfigurationProperties.SSL_ENABLED_COMPONENTS;
import static org.apache.geode.distributed.ConfigurationProperties.SSL_KEYSTORE;
import static org.apache.geode.distributed.ConfigurationProperties.SSL_KEYSTORE_PASSWORD;
import static org.apache.geode.distributed.ConfigurationProperties.SSL_KEYSTORE_TYPE;
import static org.apache.geode.distributed.ConfigurationProperties.SSL_PROTOCOLS;
import static org.apache.geode.distributed.ConfigurationProperties.SSL_TRUSTSTORE;
import static org.apache.geode.distributed.ConfigurationProperties.SSL_TRUSTSTORE_PASSWORD;
import static org.apache.geode.util.test.TestUtil.getResourcePath;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Properties;

import javax.net.ssl.HttpsURLConnection;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;

import org.apache.geode.management.internal.cli.i18n.CliStrings;
import org.apache.geode.security.SecurableCommunicationChannels;
import org.apache.geode.test.dunit.rules.GfshShellConnectionRule;
import org.apache.geode.test.dunit.rules.GfshShellConnectionRule.PortType;
import org.apache.geode.test.dunit.rules.LocatorStarterRule;
import org.apache.geode.test.junit.categories.IntegrationTest;
import org.apache.geode.test.junit.rules.serializable.SerializableTemporaryFolder;

@Category(IntegrationTest.class)
public class ConnectToLocatorSSLTest {
  @Rule
  public TemporaryFolder folder = new SerializableTemporaryFolder();

  @Rule
  public LocatorStarterRule locator = new LocatorStarterRule();

  @Rule
  public GfshShellConnectionRule gfsh = new GfshShellConnectionRule();

  private File jks = null;
  private File securityPropsFile = null;
  private Properties securityProps;

  @Before
  public void before() throws Exception {
    this.jks = new File(getResourcePath(getClass(), "/ssl/trusted.keystore"));
    securityPropsFile = folder.newFile("security.properties");
    securityProps = new Properties();
    securityProps.setProperty(SSL_ENABLED_COMPONENTS, SecurableCommunicationChannels.ALL);
    securityProps.setProperty(SSL_KEYSTORE, jks.getCanonicalPath());
    securityProps.setProperty(SSL_KEYSTORE_PASSWORD, "password");
    securityProps.setProperty(SSL_KEYSTORE_TYPE, "JKS");
    securityProps.setProperty(SSL_TRUSTSTORE, jks.getCanonicalPath());
    securityProps.setProperty(SSL_TRUSTSTORE_PASSWORD, "password");
    securityProps.setProperty(SSL_PROTOCOLS, "TLSv1.2");
    securityProps.setProperty(SSL_CIPHERS, "any");

    // saving the securityProps to a file
    OutputStream out = new FileOutputStream(securityPropsFile);
    securityProps.store(out, null);

    // start up the locator
    locator.withProperties(securityProps).startLocator();
  }

  @Test
  public void testConnectOverJmx() throws Exception {
    gfsh.connectAndVerify(locator.getJmxPort(), PortType.jmxManger,
        CliStrings.CONNECT__SECURITY_PROPERTIES, securityPropsFile.getAbsolutePath());
  }

  @Test
  public void testConnectOverLocator() throws Exception {
    gfsh.connectAndVerify(locator.getPort(), PortType.locator,
        CliStrings.CONNECT__SECURITY_PROPERTIES, securityPropsFile.getAbsolutePath());
  }

  @Test
  public void testConnectOverHttp() throws Exception {
    HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);

    gfsh.connectAndVerify(locator.getHttpPort(), PortType.http,
        CliStrings.CONNECT__SECURITY_PROPERTIES, securityPropsFile.getAbsolutePath(),
        CliStrings.CONNECT__USE_SSL, "true");

    gfsh.executeAndVerifyCommand("list members");
  }
}
