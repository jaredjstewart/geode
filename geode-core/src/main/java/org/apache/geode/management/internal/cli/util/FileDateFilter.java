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

package org.apache.geode.management.internal.cli.util;

import org.apache.geode.management.internal.cli.commands.MiscellaneousCommands;
import org.joda.time.Interval;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Time;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class FileDateFilter {
  private Interval specifiedInterval;

  public FileDateFilter(Interval specifiedInterval) {
    this.specifiedInterval = specifiedInterval;
  }

  public boolean acceptsFile(Path file) {
    try {
      long fileStartTime = getStartTimeOf(file);
      long fileEndTime = getEndTimeOf(file);
      Interval fileInterval = new Interval(fileStartTime, fileEndTime);
      return fileInterval.overlaps(specifiedInterval);
    } catch (IOException e) {
      throw new RuntimeException("Error accessing file", e);
    }
  }

  private static long getStartTimeOf(Path file) throws IOException {
    return Files.readAttributes(file, BasicFileAttributes.class).creationTime().toInstant()
        .toEpochMilli();
  }

  private static long getEndTimeOf(Path file) throws IOException {
    return file.toFile().lastModified();
  }

}
