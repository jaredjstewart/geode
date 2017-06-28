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
package org.apache.geode.management.internal.cli.remote;

import org.springframework.shell.event.ParseResult;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

import org.apache.geode.management.cli.Result;
import org.apache.geode.management.cli.Result.Status;
import org.apache.geode.management.internal.cli.GfshParseResult;
import org.apache.geode.management.internal.cli.LogWrapper;

/**
 * 
 * 
 * @since GemFire 7.0
 */
public class CommandExecutor {
  private LogWrapper logWrapper = LogWrapper.getInstance();

  public Object execute(ParseResult parseResult) throws RuntimeException {
    Result result = null;
    Assert.notNull(parseResult, "Parse result required");
    if (!GfshParseResult.class.isInstance(parseResult)) {
      // Remote command means implemented for Gfsh and ParseResult should be GfshParseResult.
      // TODO: should this message be more specific?
      throw new IllegalArgumentException("Command Configuration/Definition error.");
    }

    GfshParseResult gfshParseResult = (GfshParseResult) parseResult;

    // try {
    result = (Result) ReflectionUtils.invokeMethod(gfshParseResult.getMethod(),
        gfshParseResult.getInstance(), gfshParseResult.getArguments());
    // } catch (NotAuthorizedException e) {
    // result = ResultBuilder
    // .createGemFireUnAuthorizedErrorResult("Unauthorized. Reason: " + e.getMessage());
    // } catch (JMXInvocationException | IllegalStateException e) {
    // Gfsh.getCurrentInstance().logWarning(e.getMessage(), e);
    // } catch (CommandProcessingException e) {
    // Gfsh.getCurrentInstance().logWarning(e.getMessage(), null);
    // Object errorData = e.getErrorData();
    // if (errorData != null && errorData instanceof Throwable) {
    // logWrapper.warning(e.getMessage(), (Throwable) errorData);
    // } else {
    // logWrapper.warning(e.getMessage());
    // }
    // } catch (Exception e) {
    // Gfsh.getCurrentInstance().logWarning("Unexpected exception occurred. " + e.getMessage(), e);
    // // Log other exceptions in gfsh log
    // logWrapper.warning("Unexpected error occurred while executing command : "
    // + ((GfshParseResult) parseResult).getUserInput(), e);
    // }

    if (result != null && Status.ERROR.equals(result.getStatus())) {
      logWrapper.info("Error occurred while executing \"" + gfshParseResult.getUserInput() + "\".");
    }
    return result;
  }
}
