/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.geode.management.internal.cli.commands;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import org.apache.geode.management.cli.Result;
import org.apache.geode.management.internal.cli.CommandContext;
import org.apache.geode.management.internal.cli.shell.OperationInvoker;
import org.apache.geode.test.junit.categories.UnitTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;

@Category(UnitTest.class)
public class DescribeConnectionCommandTest {

  private CommandContext commandContext;
  private OperationInvoker operationInvoker;

  @Before
  public void before() throws Exception {
    commandContext = mock(CommandContext.class);
    operationInvoker = mock(OperationInvoker.class);
  }

  @Test
  public void notConnected() throws Exception {
    DescribeConnectionCommand command = new DescribeConnectionCommand(commandContext);
    Result result = command.run();
    assertThat(result.getStatus()).isEqualTo(Result.Status.OK);
    assertThat(result.toString()).contains("Not connected");
  }

  @Test
  public void connected() throws Exception {
    when(commandContext.isConnectedAndReady()).thenReturn(true);
    when(commandContext.getOperationInvoker()).thenReturn(operationInvoker);
    DescribeConnectionCommand command = new DescribeConnectionCommand(commandContext);
    Result result = command.run();
    assertThat(result.getStatus()).isEqualTo(Result.Status.OK);
    assertThat(result.toString()).contains("Connection Endpoints");
  }

}