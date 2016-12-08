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

import static org.apache.geode.distributed.ConfigurationProperties.NAME;
import static org.apache.geode.test.dunit.Host.getHost;

import org.apache.geode.test.dunit.Host;
import org.apache.geode.test.dunit.Invoke;
import org.apache.geode.test.dunit.VM;
import org.apache.geode.test.junit.rules.serializable.SerializableTemporaryFolder;
import org.junit.After;
import org.junit.Before;
import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Properties;


/**
 * this rule can help you start up locator/server in different VMs you can multiple members/servers
 * combination
 */
public class ModifiedLocatorServerStartupRule extends ExternalResource implements Serializable {

  // these are only availabe in each VM
  public static ServerStarterRule serverStarter;
  public static LocatorStarterRule locatorStarter;

  // these are available in test vm
  private Host host = getHost(0);

  private Member[] members = new Member[4];

  private TemporaryFolder temporaryFolder = new SerializableTemporaryFolder();

  @Before
  public void before() throws IOException {
    temporaryFolder.create();
    System.out.println ("[Jared]Temporary folder:  " + temporaryFolder.getRoot().getCanonicalPath());
    Invoke.invokeInEveryVM("Stop each VM", () -> stop());
  }

  @After
  public void after() {
    temporaryFolder.delete();
    Invoke.invokeInEveryVM("Stop each VM", () -> stop());
  }

  /**
   * Returns getHost(0).getVM(0) as a locator instance with the given configuration properties.
   *
   * @param locatorProperties
   *
   * @return VM locator vm
   *
   * @throws IOException
   */
  public Member startLocatorVM(int index, Properties locatorProperties)
      throws IOException {
    String name = "locator-" + index;
    File workingDir = new File(temporaryFolder.getRoot(), name);
    if (!workingDir.exists()) {
      temporaryFolder.newFolder(name);
    }
    VM locatorVM = host.getVM(index);
    locatorProperties.setProperty(NAME, name);
    int locatorPort = locatorVM.invoke(() -> {
      System.setProperty("user.dir", workingDir.getCanonicalPath());
      System.out.println ("[Jared] Setting current dir to " + workingDir.getCanonicalPath());
      locatorStarter = new LocatorStarterRule(locatorProperties);
      locatorStarter.startLocator();
      return locatorStarter.locator.getPort();
    });
    Member locator = new Member(locatorVM, locatorPort, workingDir);
    members[index] = locator;
    return locator;
  }

  /**
   * starts a cache server that does not connect to a locator
   *
   * @return VM node vm
   */

  public Member startServerVM(int index, Properties properties) throws IOException {
    return startServerVM(index, properties, 0);
  }

  /**
   * starts a cache server that connect to the locator running at the given port.
   * 
   * @param index
   * @param properties
   * @param locatorPort
   * @return
   */
  public Member startServerVM(int index, Properties properties, int locatorPort)
      throws IOException {
    String name = "server-" + index;
    VM nodeVM = host.getVM(index);
    File workingDir = new File(temporaryFolder.getRoot(), name);
    if (!workingDir.exists()) {
      temporaryFolder.newFolder(name);
    }
    properties.setProperty(NAME, name);
    int port = nodeVM.invoke(() -> {
      System.setProperty("user.dir", workingDir.getCanonicalPath());
      serverStarter = new ServerStarterRule(properties);
      serverStarter.startServer(locatorPort);
      return serverStarter.server.getPort();
    });
    Member server = new Member(nodeVM, port, workingDir);
    members[index] = server;
    return server;
  }

  public Member getMember(int index) {
    return members[index];
  }

  public TemporaryFolder getRootFolder() {
    return temporaryFolder;
  }

  public final void stop() {
    if (serverStarter != null) {
      serverStarter.after();
    }
    if (locatorStarter != null) {
      locatorStarter.after();
    }
  }

}
