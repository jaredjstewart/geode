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

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import org.apache.geode.management.cli.Result;
import org.apache.geode.management.internal.cli.result.CommandResult;
import org.apache.geode.test.dunit.rules.GfshShellConnectionRule;
import org.apache.geode.test.dunit.rules.ServerStarterRule;

public class CommandOverHttpTest {

  @ClassRule
  public static ServerStarterRule server = new ServerStarterRule().withJMXManager().withAutoStart();

  @Rule
  public GfshShellConnectionRule gfshRule = new GfshShellConnectionRule();

  @Before
  public void before() throws Exception {
    gfshRule.connectAndVerify(server.getHttpPort(), GfshShellConnectionRule.PortType.http);
  }

  @Test
  public void testListClient() throws Exception {
    CommandResult result = gfshRule.executeCommand("list clients");
    assertThat(result.getStatus()).isEqualTo(Result.Status.ERROR);
    assertThat(result.toString()).contains("No clients were retrieved for cache-servers");
  }

  @Test
  public void testDescribeClient() throws Exception {
    CommandResult result = gfshRule.executeCommand("describe client --clientID=xyz");
    assertThat(result.getStatus()).isEqualTo(Result.Status.ERROR);
    assertThat(result.toString()).contains("Specified Client ID xyz not present");
  }

  @Test
  public void exportLogs() throws Exception {
    CommandResult result = gfshRule.executeCommand("export logs");
    System.out.println(result.toString());
    assertThat(result.getStatus()).isEqualTo(Result.Status.OK);
  }
}
