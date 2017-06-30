/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.geode.management.internal.cli.remote;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Collections;
import java.util.Properties;

import org.junit.Before;
import org.junit.Test;

import org.apache.geode.internal.security.SecurityService;
import org.apache.geode.management.cli.CommandStatement;

public class CommandProcessorTest {

  Properties properties;
  SecurityService securityService;
  CommandExecutor executor;
  CommandProcessor commandProcessor;

  @Before
  public void before(){
    properties = new Properties();
    securityService = mock(SecurityService.class);
    executor = mock(CommandExecutor.class);
    commandProcessor = new CommandProcessor(properties, securityService, executor);
  }

  @Test
  public void createCommandStatement() throws Exception {
    CommandStatement stmt = commandProcessor.createCommandStatement("start locator", Collections.emptyMap());
    assertThat(stmt).isNotNull();
    assertThat(stmt.getCommandString()).isEqualTo("start locator");
  }
}