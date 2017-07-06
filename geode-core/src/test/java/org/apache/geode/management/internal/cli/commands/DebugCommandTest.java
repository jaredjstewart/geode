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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;

import org.apache.geode.management.cli.Result;
import org.apache.geode.management.internal.cli.CommandContext;
import org.apache.geode.management.internal.cli.commands.DebugCommand.Debug;
import org.apache.geode.test.junit.categories.UnitTest;

@Category(UnitTest.class)
public class DebugCommandTest {

  private CommandContext commandContext;
  private ArgumentCaptor<Boolean> argument;

  @Before
  public void before() throws Exception {
    commandContext = mock(CommandContext.class);
    argument = ArgumentCaptor.forClass(Boolean.class);
  }

  @Test
  public void valueOfOnReturnsON() throws Exception {
    assertThat(Debug.valueOf("ON")).isSameAs(Debug.ON);
  }

  @Test
  public void valueOfOffReturnsOFF() throws Exception {
    assertThat(Debug.valueOf("OFF")).isSameAs(Debug.OFF);
  }

  @Test
  public void valueOfEmptyReturns() throws Exception {
    assertThatThrownBy(() -> Debug.valueOf("")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void valueOfNullReturns() throws Exception {
    assertThatThrownBy(() -> Debug.valueOf(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  public void invokesCommandContextSetDebugOff() throws Exception {
    DebugCommand command = new DebugCommand(commandContext, Debug.OFF);
    command.run();
    verify(commandContext).setDebug(argument.capture());
    assertThat(argument.getValue()).isFalse();
  }

  @Test
  public void invokesCommandContextSetDebugOn() throws Exception {
    DebugCommand command = new DebugCommand(commandContext, Debug.ON);
    command.run();
    verify(commandContext).setDebug(argument.capture());
    assertThat(argument.getValue()).isTrue();
  }

  @Test
  public void nullDebugResultsInError() throws Exception {
    DebugCommand command = new DebugCommand(commandContext, null);
    Result result = command.run();
    assertThat(result.getStatus()).isEqualTo(Result.Status.ERROR);
  }
}
