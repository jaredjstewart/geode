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
package org.apache.geode.management.internal.web.controllers;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Paths;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import org.apache.geode.management.internal.cli.CommandResponseBuilder;
import org.apache.geode.management.internal.cli.result.CommandResult;
import org.apache.geode.management.internal.cli.result.InfoResultData;
import org.apache.geode.management.internal.cli.result.ResultBuilder;
import org.apache.geode.test.junit.categories.UnitTest;

@Category(UnitTest.class)
public class ShellCommandsControllerProcessCommandTest {
  private ShellCommandsController controller;
  private MockHttpServletRequest request;
  private CommandResult fakeResult;

  @Before
  public void setup() {

    controller = new ShellCommandsController() {
      @Override
      protected String processCommand(final String command) {
        return CommandResponseBuilder.createCommandResponseJson("someMember", fakeResult);
      }
    };
    request = new MockHttpServletRequest();
  }

  @Test
  public void x(){
    fakeResult = new CommandResult(new InfoResultData("Some result message"));

    String responseJson = controller.command("xyz");
    CommandResult result = ResultBuilder.fromJson(responseJson);

    assertThat(result.nextLine()).isEqualTo(fakeResult.nextLine());
  }

  @Test
  public void resultWithFile(){
    fakeResult = new CommandResult(Paths.get("."));

    String responseJson = controller.command("xyz");
    CommandResult result = ResultBuilder.fromJson(responseJson);

    assertThat(result.hasFileToDownload()).isTrue();
  }
}