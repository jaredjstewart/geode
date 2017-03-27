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

import org.apache.geode.distributed.internal.DistributionConfig;
import org.apache.geode.distributed.internal.InternalLocator;
import org.awaitility.Awaitility;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class LocatorStarter {
  private transient InternalLocator locator;
  private Properties properties;

  public LocatorStarter(Properties properties) {
    this.properties = properties;
  }

  public InternalLocator start() {
    if (this.locator != null) {
      throw new IllegalStateException("Locator has already been started");
    }

    try {
      // this will start a jmx manager and admin rest service by default
       this.locator = (InternalLocator) startLocatorAndDS(0, null, properties);
    } catch (IOException e) {
      throw new RuntimeException("unable to start up locator.", e);
    }

    DistributionConfig config = locator.getConfig();
    int memberPort = locator.getConfig().getTcpPort();
    locator.resetInternalLocatorFileNamesWithCorrectPortNumber(memberPort);

    if (config.getEnableClusterConfiguration()) {
      Awaitility.await().atMost(65, TimeUnit.SECONDS)
          .until(() -> assertTrue(locator.isSharedConfigurationRunning()));
    }

    return this.locator;
  }


  public void stop() {
    if (this.locator != null) {
      this.locator.stop();
    }
  }

}
