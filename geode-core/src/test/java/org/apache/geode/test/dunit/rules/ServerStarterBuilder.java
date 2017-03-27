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

import static org.apache.geode.distributed.ConfigurationProperties.DEPLOY_WORKING_DIR;
import static org.apache.geode.distributed.ConfigurationProperties.HTTP_SERVICE_BIND_ADDRESS;
import static org.apache.geode.distributed.ConfigurationProperties.HTTP_SERVICE_PORT;
import static org.apache.geode.distributed.ConfigurationProperties.JMX_MANAGER;
import static org.apache.geode.distributed.ConfigurationProperties.JMX_MANAGER_HOSTNAME_FOR_CLIENTS;
import static org.apache.geode.distributed.ConfigurationProperties.JMX_MANAGER_PORT;
import static org.apache.geode.distributed.ConfigurationProperties.JMX_MANAGER_START;
import static org.apache.geode.distributed.ConfigurationProperties.LOCATORS;
import static org.apache.geode.distributed.ConfigurationProperties.MCAST_PORT;
import static org.apache.geode.distributed.ConfigurationProperties.SECURITY_MANAGER;
import static org.apache.geode.distributed.ConfigurationProperties.START_DEV_REST_API;
import static org.apache.geode.distributed.ConfigurationProperties.TCP_PORT;

import org.apache.geode.cache.RegionShortcut;
import org.apache.geode.distributed.ConfigurationProperties;
import org.apache.geode.internal.AvailablePortHelper;
import org.apache.geode.security.SecurityManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class ServerStarterBuilder {
  private Properties properties = new Properties();
  private boolean automaticallyManagedWorkingDir;
  private Integer port;

  public ServerStarterBuilder() {}

  private Map<String, RegionShortcut> regionsToCreate = new HashMap<>();

  public ServerStarterBuilder withSecurityManager(
      Class<? extends SecurityManager> securityManager) {
    properties.setProperty(SECURITY_MANAGER, securityManager.getName());
    return this;
  }

  public ServerStarterBuilder createRegion(RegionShortcut type, String name) {
    regionsToCreate.put(name, type);
    return this;
  }

  public ServerStarterBuilder withProperty(String key, String value) {
    properties.setProperty(key, value);
    return this;
  }

  public ServerStarterBuilder withJMXManager() {
    int[] ports = AvailablePortHelper.getRandomAvailableTCPPorts(2);

    // if (!useDefault) {
    // do no override these properties if already exists
    properties.putIfAbsent(JMX_MANAGER_PORT, ports[0] + "");
    properties.putIfAbsent(HTTP_SERVICE_PORT, ports[1] + "");
    // }
    properties.putIfAbsent(JMX_MANAGER, "true");
    properties.putIfAbsent(JMX_MANAGER_START, "true");
    properties.putIfAbsent(HTTP_SERVICE_BIND_ADDRESS, "localhost");
    return this;
  }

  /**
   * Enables the Dev REST API with a random http port
   */
  public ServerStarterBuilder withRestService() {
    int httpPort = AvailablePortHelper.getRandomAvailableTCPPort();
    properties.setProperty(START_DEV_REST_API, "true");
    properties.setProperty(HTTP_SERVICE_BIND_ADDRESS, "localhost");
    properties.setProperty(HTTP_SERVICE_PORT, httpPort + "");
    return this;
  }

  /**
   * Enables the Dev REST API with the default http port
   */
  public ServerStarterBuilder withRestServiceOnDefaultPort() {
    properties.setProperty(START_DEV_REST_API, "true");
    properties.setProperty(HTTP_SERVICE_BIND_ADDRESS, "localhost");
    return this;
  }

  Map<String, RegionShortcut> getRegionsToCreate() {
    return this.regionsToCreate;
  }

  int getPort() {
    if (port == null) {
      throw new IllegalStateException("Port not set");
    }

    return this.port;
  }

  Properties getProperties() {
    return this.properties;
  }

  private void setDefaultProperties() {
    String workingDir = properties.getProperty(DEPLOY_WORKING_DIR);
    if (workingDir == null) {
      automaticallyManagedWorkingDir = true;
    }

    properties.putIfAbsent(ConfigurationProperties.NAME, "server");
    // properties.putIfAbsent(LOG_FILE, new File(properties.get(ConfigurationProperties.NAME) +
    // ".log").getAbsolutePath().toString());
    int[] ports = AvailablePortHelper.getRandomAvailableTCPPorts(4);

    this.port = ports[0];

    properties.putIfAbsent(TCP_PORT, ports[1] + "");
    properties.putIfAbsent(MCAST_PORT, "0");
    properties.putIfAbsent(LOCATORS, "");
  }

  public boolean hasAutomaticallyManagedWorkingDir() {
    return this.automaticallyManagedWorkingDir;
  }

  public LocalServerStarterRule buildInThisVM() {
    setDefaultProperties();
    return new LocalServerStarterRule(this);
  }
}
