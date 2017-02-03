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

package org.apache.geode.management.internal.cli.functions;

import static java.util.stream.Collectors.toSet;

import org.apache.geode.cache.Cache;
import org.apache.geode.cache.CacheFactory;
import org.apache.geode.cache.execute.Function;
import org.apache.geode.cache.execute.FunctionContext;
import org.apache.geode.distributed.internal.InternalDistributedSystem;
import org.apache.geode.internal.InternalEntity;
import org.apache.geode.internal.lang.StringUtils;
import org.apache.geode.internal.logging.InternalLogWriter;
import org.apache.geode.internal.logging.LogService;
import org.apache.geode.internal.logging.LogWriterImpl;
import org.apache.geode.management.internal.cli.commands.MiscellaneousCommands;
import org.apache.geode.management.internal.cli.util.LogExporter;
import org.apache.geode.management.internal.cli.util.LogFilter;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;

public class ExportLogsFunction implements Function, InternalEntity {
  private static final Logger LOGGER = LogService.getLogger();
  private static final long serialVersionUID = 1L;

  @Override
  public void execute(final FunctionContext context) {
    try {
      // TODO: change this to get cache from FunctionContext when it becomes available
      Cache cache = CacheFactory.getAnyInstance();
      String memberId = ((InternalDistributedSystem) cache.getDistributedSystem()).getMemberId();
      LOGGER.debug("ExportLogsFunction started for member {}", memberId);

      Object[] args = (Object[]) context.getArguments();

      LocalDateTime startTime = getStartTime(args);
      LocalDateTime endTime = getEndTime(args);

      String logLevel = getLogLevel(args);
      boolean logLevelOnly = getLogLevelOnly(args);
      Set<String> permittedLogLevels = getPermittedLogLevels(logLevel, logLevelOnly);

      LogFilter logFilter = new LogFilter(permittedLogLevels, startTime, endTime);

      Path workingDir = Paths.get(System.getProperty("user.dir"));
      new LogExporter(logFilter).export(workingDir);

    } catch (Exception e) {
      context.getResultSender().sendException(e);
    }
  }

  private Set<String> getPermittedLogLevels(String logLevel, boolean logLevelOnly) {
    if (logLevelOnly) {
      return Stream.of(logLevel).collect(toSet());
    }

    // Return all log levels lower than or equal to the specified logLevel
    return Arrays.stream(InternalLogWriter.levelNames).filter((String level) -> {
      int logLevelCode = LogWriterImpl.levelNameToCode(level);
      int logLevelCodeThreshold = LogWriterImpl.levelNameToCode(logLevel);

      return logLevelCode <= logLevelCodeThreshold;
    }).collect(toSet());
  }



  private static LocalDateTime parseTime(String dateString) {
    try {
      SimpleDateFormat df = new SimpleDateFormat(MiscellaneousCommands.FORMAT);
      return df.parse(dateString).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    } catch (ParseException e) {
      try {
        SimpleDateFormat df = new SimpleDateFormat(MiscellaneousCommands.ONLY_DATE_FORMAT);
        return df.parse(dateString).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
      } catch (ParseException e1) {
        return null;
      }
    }
  }

  private String getLogLevel(Object[] args) {
    return (String) args[0];
  }

  private boolean getLogLevelOnly(Object[] args) {
    return (boolean) args[1];
  }

  private LocalDateTime getStartTime(Object[] args) {
    return parseTime((String) args[2]);
  }

  private LocalDateTime getEndTime(Object[] args) {
    return parseTime((String) args[3]);
  }

  @Override
  public boolean isHA() {
    return false;
  }
}
