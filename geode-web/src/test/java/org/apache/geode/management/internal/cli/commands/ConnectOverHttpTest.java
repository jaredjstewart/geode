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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import org.apache.geode.management.internal.cli.shell.OperationInvoker;
import org.apache.geode.management.internal.web.domain.Link;
import org.apache.geode.management.internal.web.shell.SimpleHttpOperationInvoker;
import org.apache.geode.security.SimpleTestSecurityManager;
import org.apache.geode.test.dunit.rules.GfshShellConnectionRule;
import org.apache.geode.test.dunit.rules.ServerStarterRule;
import org.apache.geode.test.junit.ResultCaptor;

import org.apache.geode.test.junit.categories.DistributedTest;

@Category(DistributedTest.class)
public class ConnectOverHttpTest {

  @ClassRule
  public static ServerStarterRule server = new ServerStarterRule()
      .withSecurityManager(SimpleTestSecurityManager.class).withJMXManager().withAutoStart();

  @Rule
  public GfshShellConnectionRule gfshRule = new GfshShellConnectionRule();

  @Test
  public void connectOverHttpWithInvalidCredential() throws Exception {
    gfshRule.secureConnect(server.getHttpPort(), GfshShellConnectionRule.PortType.http, "test",
        "fail");
    assertThat(gfshRule.isConnected()).isFalse();
  }

  @Test
  public void connectOverHttpWithValidCredential() throws Exception {
    gfshRule.secureConnect(server.getHttpPort(), GfshShellConnectionRule.PortType.http, "test",
        "test");
    assertThat(gfshRule.isConnected()).isTrue();
  }

  @Test
  public void testOverJmx() throws Exception {
    gfshRule.secureConnectAndVerify(server.getJmxPort(), GfshShellConnectionRule.PortType.jmxManger,
        "test", "test");
    gfshRule.executeAndVerifyCommand("list members");
  }

  @Test
  public void gfshConnectSetsSimpleHttpOperationInvoker() throws Exception {
    gfshRule.secureConnectAndVerify(server.getHttpPort(), GfshShellConnectionRule.PortType.http,
        "clusterRead", "clusterRead");
    OperationInvoker invoker = gfshRule.getGfsh().getOperationInvoker();
    assertThat(invoker).isInstanceOf(SimpleHttpOperationInvoker.class);
  }

  // final AtomicReference<Link> link = new AtomicReference<>();
  Link link;

  @Test
  public void listMember() throws Exception {
    gfshRule.secureConnectAndVerify(server.getHttpPort(), GfshShellConnectionRule.PortType.http,
        "clusterRead", "clusterRead");

    SimpleHttpOperationInvoker invoker =
        (SimpleHttpOperationInvoker) gfshRule.getGfsh().getOperationInvoker();

    // gfshRule.getGfsh().setOperationInvoker(new SimpleHttpOperationInvoker(gfshRule.getGfsh(),
    // Collections.emptyMap()){
    // public Link createLink(final CommandRequest command){
    // link = super.createLink(command);
    // return link;
    // }
    // });

    // ArgumentCaptor<Link> linkCaptor = ArgumentCaptor.forClass(Link.class);

    invoker = spy(invoker);
    gfshRule.getGfsh().setOperationInvoker(invoker);

    // ResultCaptor<Link> resultCaptor = new ResultCaptor<>();
    //
    // doAnswer(resultCaptor).when(invoker).createLink(any());
    // gfshRule.executeAndVerifyCommand("list members");
    //
    // // verify(invoker).createLink(linkCaptor.capture());
    //
    // // Link link = linkCaptor.getValue();
    // Link link = resultCaptor.getResult();
    assertThat(link.toString()).contains("gemfire/v1/management/commands?cmd=list%20members");
  }
}
