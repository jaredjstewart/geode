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
import static org.apache.geode.distributed.ConfigurationProperties.JMX_MANAGER_PORT;
import static org.apache.geode.distributed.ConfigurationProperties.JMX_MANAGER_START;
import static org.apache.geode.distributed.ConfigurationProperties.NAME;
import static org.apache.geode.distributed.Locator.startLocatorAndDS;
import static org.junit.Assert.assertTrue;

import org.apache.geode.distributed.ConfigurationProperties;
import org.apache.geode.distributed.internal.DistributionConfig;
import org.apache.geode.distributed.internal.InternalLocator;
import org.apache.geode.internal.AvailablePortHelper;
import org.apache.geode.security.SecurityManager;
import org.awaitility.Awaitility;
import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * This is a rule to start up a locator in your current VM. It's useful for your Integration Tests.
 *
 * You can create this rule with and without a Property. If the rule is created with a property, the
 * locator will started automatically for you. If not, you can start the locator by using one of the
 * startLocator function. Either way, the rule will handle shutting down the locator properly for
 * you.
 *
 * If you need a rule to start a server/locator in different VMs for Distributed tests, You should
 * use {@link LocatorServerStartupRule}.
 */

public class LocatorStarterRule extends ExternalResource {
  private Properties properties;

  private File workingDir;
  private String oldUserDirProperty;
  protected transient TemporaryFolder temporaryFolder;
  private InternalLocator internalLocator;

  private LocatorStarterRule(LocatorStarterRule.Builder builder) {
    this.properties = builder.properties;

    if (builder.createWorkingDir) {
      temporaryFolder = new TemporaryFolder();
    }

    this.workingDir = builder.workingDir;
  }

  public InternalLocator getLocator() {
    return this.internalLocator;
  }

  @Override
  public void before() throws IOException {
    if (this.temporaryFolder != null) {
      this.temporaryFolder.create();
      this.workingDir = temporaryFolder.getRoot();
    }

    if (this.workingDir != null) {
      this.oldUserDirProperty = System.getProperty("user.dir");
      properties.setProperty(DEPLOY_WORKING_DIR, this.workingDir.getAbsolutePath());
    }

   this.internalLocator = new LocatorStarter(properties).start();
  }

  @Override
  public void after() {
    if (this.internalLocator != null) {
      this.internalLocator.stop();
    }

    if (oldUserDirProperty != null) {
      System.setProperty("user.dir", oldUserDirProperty);
    }

    if (temporaryFolder != null) {
      temporaryFolder.delete();
    }
  }

  public int getHttpPort() {
    return this.internalLocator.getPort();
  }

  public static class Builder {
    private File workingDir = null;
    private boolean createWorkingDir = false;
    private Properties properties = new Properties();

    public Builder() {

    }

    public Builder withSecurityManager(Class<? extends SecurityManager> securityManager) {
      properties.put(ConfigurationProperties.SECURITY_MANAGER, securityManager.getName());
      return this;
    }

    public Builder withJMXManager() {
      properties.putIfAbsent(JMX_MANAGER_PORT,
          AvailablePortHelper.getRandomAvailableTCPPort() + "");
      properties.putIfAbsent(HTTP_SERVICE_PORT,
          AvailablePortHelper.getRandomAvailableTCPPort() + "");
      properties.putIfAbsent(JMX_MANAGER, "true");
      properties.putIfAbsent(JMX_MANAGER_START, "true");
      properties.putIfAbsent(HTTP_SERVICE_BIND_ADDRESS, "localhost");
      return this;
    }

    public LocatorStarterRule build() {
      properties.putIfAbsent(NAME, "locator");
      return new LocatorStarterRule(this);
    }

    /**
     * This rule will automatically manage the working directory of its member with a
     * TemporaryFolder rule
     */
    public Builder withWorkingDir() {
      this.createWorkingDir = true;
      return this;
    }

    public Builder withWorkingDir(File workDir) {
      this.workingDir = workDir;
      return this;
    }

    public Builder withProperties(Properties props) {
      if (props != null) {
        this.properties.putAll(props);
      }
      return this;
    }
  }
}
