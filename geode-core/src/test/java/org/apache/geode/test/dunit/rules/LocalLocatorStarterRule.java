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
package org.apache.geode.test.dunit.rules;

import static org.apache.geode.distributed.Locator.startLocatorAndDS;
import static org.junit.Assert.assertTrue;

import org.apache.geode.distributed.ConfigurationProperties;
import org.apache.geode.distributed.internal.DistributionConfig;
import org.apache.geode.distributed.internal.InternalLocator;
import org.apache.geode.internal.datasource.ConfigProperty;
import org.awaitility.Awaitility;
import org.junit.rules.ExternalResource;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class LocalLocatorStarterRule extends ExternalResource {
  private volatile InternalLocator internalLocator;
  private final Properties properties;
  private final int port;

  LocalLocatorStarterRule(LocatorStarterBuilder locatorStarterBuilder) {
    this.properties = locatorStarterBuilder.getProperties();
    this.port = locatorStarterBuilder.getLocatorPort();
  }

  public String getHostname() {
    return "localhost";
  }

  public int getPort() {
    return this.port;
  }

  public int getHttpPort() {
    String httpPort = properties.getProperty(ConfigurationProperties.HTTP_SERVICE_PORT);
    if (httpPort == null) {
      throw new IllegalStateException("No http port specified");
    }
    return Integer.valueOf(httpPort);
  }


  @Override
  protected void before() {
    try {
      // this will start a jmx manager and admin rest service by default
      this.internalLocator = (InternalLocator) startLocatorAndDS(port, null, properties);
    } catch (IOException e) {
      throw new RuntimeException("unable to start up locator.", e);
    }

    DistributionConfig config = this.internalLocator.getConfig();

    if (config.getEnableClusterConfiguration()) {
      Awaitility.await().atMost(65, TimeUnit.SECONDS)
          .until(() -> assertTrue(internalLocator.isSharedConfigurationRunning()));
    }
  }

  @Override
  protected void after() {
    if (internalLocator != null) {
      internalLocator.stop();
      internalLocator = null;
    }
  }
}
