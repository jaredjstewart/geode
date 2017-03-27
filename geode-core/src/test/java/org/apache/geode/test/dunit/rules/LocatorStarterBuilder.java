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

import static org.apache.geode.distributed.ConfigurationProperties.HTTP_SERVICE_BIND_ADDRESS;
import static org.apache.geode.distributed.ConfigurationProperties.HTTP_SERVICE_PORT;
import static org.apache.geode.distributed.ConfigurationProperties.JMX_MANAGER;
import static org.apache.geode.distributed.ConfigurationProperties.JMX_MANAGER_HOSTNAME_FOR_CLIENTS;
import static org.apache.geode.distributed.ConfigurationProperties.JMX_MANAGER_PORT;
import static org.apache.geode.distributed.ConfigurationProperties.JMX_MANAGER_START;
import static org.apache.geode.distributed.ConfigurationProperties.SECURITY_MANAGER;
import static org.apache.geode.distributed.ConfigurationProperties.TCP_PORT;

import org.apache.geode.distributed.ConfigurationProperties;
import org.apache.geode.internal.AvailablePortHelper;
import org.apache.geode.security.SecurityManager;

import java.util.Properties;

public class LocatorStarterBuilder {
  private Properties properties = new Properties();
  private Integer locatorPort;

  public LocatorStarterBuilder() {}

  public LocatorStarterBuilder withSecurityManager(
      Class<? extends SecurityManager> securityManager) {
    properties.setProperty(SECURITY_MANAGER, securityManager.getName());
    return this;
  }

  Properties getProperties() {
    return this.properties;
  }

  int getLocatorPort() {
    if (locatorPort == null) {
      throw new IllegalStateException("Locator port not set");
    }
    return this.locatorPort;
  }

  private void setDefaultProperties() {
    properties.putIfAbsent(ConfigurationProperties.NAME, "locator");
    // properties.putIfAbsent(LOG_FILE, new File(properties.get(ConfigurationProperties.NAME) +
    // ".log").getAbsolutePath().toString());
    int[] ports = AvailablePortHelper.getRandomAvailableTCPPorts(4);

    if (locatorPort == null) {
      locatorPort = ports[0];
    }

    properties.putIfAbsent(JMX_MANAGER_PORT, ports[1] + "");
    properties.putIfAbsent(TCP_PORT, ports[2] + "");
    properties.putIfAbsent(HTTP_SERVICE_PORT, ports[3] + "");

    properties.putIfAbsent(JMX_MANAGER, "true");
    properties.putIfAbsent(JMX_MANAGER_START, "true");
    properties.putIfAbsent(HTTP_SERVICE_BIND_ADDRESS, "localhost");
    properties.putIfAbsent(JMX_MANAGER_HOSTNAME_FOR_CLIENTS, "localhost");
  }

  public LocalLocatorStarterRule buildInThisVM() {
    setDefaultProperties();
    return new LocalLocatorStarterRule(this);
  }

}
