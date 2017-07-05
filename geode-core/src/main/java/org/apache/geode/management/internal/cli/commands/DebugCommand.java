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

import static org.apache.geode.management.internal.cli.commands.DebugCommand.Debug.OFF;
import static org.apache.geode.management.internal.cli.commands.DebugCommand.Debug.ON;

import java.io.IOException;

import org.apache.geode.management.cli.Result;
import org.apache.geode.management.internal.cli.CommandContext;
import org.apache.geode.management.internal.cli.i18n.CliStrings;
import org.apache.geode.management.internal.cli.result.ErrorResultData;
import org.apache.geode.management.internal.cli.result.ResultBuilder;

public class DebugCommand {

  private final CommandContext context;
  private final Debug state;

  public enum Debug {
    ON, OFF

  }

  public DebugCommand(CommandContext context, Debug state) {
    this.context = context;
    this.state = state;
  }

  public Result run() throws IOException {
    if (this.context != null) {
      // Handle state
      if (this.state == ON) {
        this.context.setDebug(true);
      } else if (this.state == OFF) {
        this.context.setDebug(false);
      } else {
        return ResultBuilder.createUserErrorResult(
            CliStrings.format(CliStrings.DEBUG__MSG_0_INVALID_STATE_VALUE, this.state));
      }

    } else {
      ErrorResultData errorResultData =
          ResultBuilder.createErrorResultData().setErrorCode(ResultBuilder.ERRORCODE_DEFAULT)
              .addLine(CliStrings.ECHO__MSG__NO_GFSH_INSTANCE);
      return ResultBuilder.buildResult(errorResultData);
    }
    return ResultBuilder.createInfoResult(CliStrings.DEBUG__MSG_DEBUG_STATE_IS + this.state);
  }
}
