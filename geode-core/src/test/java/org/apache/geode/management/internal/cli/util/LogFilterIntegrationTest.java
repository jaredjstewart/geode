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
package org.apache.geode.management.internal.cli.util;


import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.io.FileUtils;
import org.apache.geode.test.junit.categories.UnitTest;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;

@Category(UnitTest.class)
public class LogFilterIntegrationTest {
  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void createDateGetsReturnedCorrectly() throws IOException, InterruptedException {
    LocalDateTime beforeFileCreation = LocalDateTime.now();

    // We want the new file to be created in a different second than beforeFileCreation,
    // so that we don't have to worry about timestamp rounding/precision causing flakiness
    Thread.sleep(2000);

    Path file = temporaryFolder.newFile("someFile.txt").toPath();
    Thread.sleep(2000);

    LocalDateTime afterFileCreation = LocalDateTime.now();
    Thread.sleep(2000);

    assertThat(LogFilter.getStartTimeOf(file)).isAfter(beforeFileCreation);
    assertThat(LogFilter.getStartTimeOf(file)).isBefore(afterFileCreation);

    FileUtils.writeStringToFile(file.toFile(), "write some file content", true);
    Thread.sleep(2000);

    assertThat(LogFilter.getEndTimeOf(file)).isAfter(beforeFileCreation);
    assertThat(LogFilter.getEndTimeOf(file)).isAfter(afterFileCreation);

    assertThat(LogFilter.getStartTimeOf(file)).isAfter(beforeFileCreation);
    assertThat(LogFilter.getStartTimeOf(file)).isBefore(afterFileCreation);
  }
}
