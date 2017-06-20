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
package org.apache.geode.management.internal.cli.commands;

import static org.apache.geode.distributed.ConfigurationProperties.SSL_CIPHERS;
import static org.apache.geode.distributed.ConfigurationProperties.SSL_ENABLED_COMPONENTS;
import static org.apache.geode.distributed.ConfigurationProperties.SSL_KEYSTORE;
import static org.apache.geode.distributed.ConfigurationProperties.SSL_KEYSTORE_PASSWORD;
import static org.apache.geode.distributed.ConfigurationProperties.SSL_PROTOCOLS;
import static org.apache.geode.distributed.ConfigurationProperties.SSL_TRUSTSTORE;
import static org.apache.geode.distributed.ConfigurationProperties.SSL_TRUSTSTORE_PASSWORD;
import static org.apache.geode.util.test.TestUtil.getResourcePath;

import org.apache.geode.security.SecurableCommunicationChannels;
import org.apache.geode.test.dunit.rules.gfsh.GfshRule;
import org.apache.geode.test.dunit.rules.gfsh.GfshScript;
import org.apache.geode.test.junit.categories.DistributedTest;
import org.apache.geode.test.junit.categories.IntegrationTest;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

@Category(DistributedTest.class)
public class StatusLocatorRealGfshTest {
  @Rule
  public GfshRule gfshRule = new GfshRule();

  @ClassRule
  public static  TemporaryFolder temporaryFolder = new TemporaryFolder();

  private static String sslSecurityPropsFile;
  private static String jksFile;

  @BeforeClass
  public static void setup() throws Exception {
    File jks = new File(getResourcePath(StatusLocatorRealGfshTest.class, "/ssl/trusted.keystore"));

    Properties securityProps = new Properties();
    securityProps.setProperty(SSL_ENABLED_COMPONENTS, SecurableCommunicationChannels.ALL);
    securityProps.setProperty(SSL_KEYSTORE, jks.getCanonicalPath());
    securityProps.setProperty(SSL_KEYSTORE_PASSWORD, "password");
    securityProps.setProperty(SSL_TRUSTSTORE, jks.getCanonicalPath());
    securityProps.setProperty(SSL_TRUSTSTORE_PASSWORD, "password");
    securityProps.setProperty(SSL_PROTOCOLS, "TLSv1.2");
    securityProps.setProperty(SSL_CIPHERS, "any");

    File  securityPropsFile = temporaryFolder.newFile("security.properties");
    securityProps.store(new FileOutputStream(securityPropsFile), null);

    sslSecurityPropsFile = securityPropsFile.getAbsolutePath();
    jksFile = jks.getAbsolutePath();
  }

  @Test
  public void statusLocatorSucceedsWhenConnected() throws Exception {
    gfshRule.execute(GfshScript.of("start locator --name=locator1").awaitAtMost(1, TimeUnit.MINUTES)
        .expectExitCode(0));

    gfshRule.execute(GfshScript.of("connect", "status locator --name=locator1")
        .awaitAtMost(1, TimeUnit.MINUTES).expectExitCode(0));
  }

  @Test
  public void statusLocatorFailsWhenNotConnected() throws Exception {
    gfshRule.execute(GfshScript.of("start locator --name=locator1").awaitAtMost(1, TimeUnit.MINUTES)
        .expectExitCode(0));

    gfshRule.execute(GfshScript.of("status locator --name=locator1")
        .awaitAtMost(1, TimeUnit.MINUTES).expectExitCode(1));
  }

  @Test
  public void sslOverJmx() {
    gfshRule.execute(GfshScript.of("start locator --name=locator1 --security-properties-file=" + sslSecurityPropsFile).awaitAtMost(1, TimeUnit.MINUTES)
        .expectExitCode(0));

    gfshRule.execute(GfshScript.of("connect --locator=localhost[10334] --security-properties-file=" + sslSecurityPropsFile, "list members")
        .awaitAtMost(1, TimeUnit.MINUTES).expectExitCode(0));
  }

  @Test
  public void sslOverHttp() {
    gfshRule.execute(GfshScript.of("start locator --name=locator1 --security-properties-file=" + sslSecurityPropsFile).awaitAtMost(1, TimeUnit.MINUTES)
        .expectExitCode(0));

    gfshRule.execute(GfshScript.of("connect --use-http=true --use-ssl=true --trust-store=/Users/jstewart/projects/open/geode-core/build/resources/test/ssl/trusted.keystore --trust-store-password=password --security-properties-file="+sslSecurityPropsFile, "list members")
        .awaitAtMost(1, TimeUnit.MINUTES).expectExitCode(0));
    gfshRule.execute(GfshScript.of("connect --use-http=true --use-ssl=true --trust-store=/Users/jstewart/projects/open/geode-core/build/resources/test/ssl/trusted.keystore --trust-store-password=password --key-store-password=password --key-store=/Users/jstewart/projects/open/geode-core/build/resources/test/ssl/trusted.keystore --security-properties-file="+sslSecurityPropsFile, "list members")
        .awaitAtMost(1, TimeUnit.MINUTES).expectExitCode(0));
  }

}
