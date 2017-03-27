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
 *
 */

package org.apache.geode.test.dunit.rules;

import org.apache.geode.distributed.internal.InternalLocator;

import java.io.File;

public class Locator implements Member {
  private File workingDir;
  private int port;
  private int jmxPort;
  private int httpPort;
  private String name;

  public Locator(InternalLocator locator)    {
    this.workingDir = locator.getConfig().getDeployWorkingDir();
    this.port = locator.getConfig().getTcpPort();
    this.jmxPort = locator.getConfig().getJmxManagerPort();
    this.httpPort = locator.getConfig().getHttpServicePort();
    this.name = locator.getConfig().getName();
}

  @Override
  public File getWorkingDir() {
    return workingDir;
  }

  @Override
  public int getPort() {
    return port;
  }

  @Override
  public int getJmxPort() {
    return jmxPort;
  }

  @Override
  public int getHttpPort() {
    return httpPort;
  }

  @Override
  public String getName() {
    return name;
  }
}
