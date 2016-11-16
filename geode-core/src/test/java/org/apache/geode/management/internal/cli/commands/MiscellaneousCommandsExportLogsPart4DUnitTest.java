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

import static org.apache.geode.management.cli.Result.Status.OK;
import static org.apache.geode.test.dunit.Host.getHost;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.io.FileUtils;
import org.apache.geode.LogWriter;
import org.apache.geode.internal.logging.LogWriterImpl;
import org.apache.geode.management.cli.Result;
import org.apache.geode.test.dunit.SerializableRunnable;
import org.apache.geode.test.junit.categories.DistributedTest;
import org.apache.geode.test.junit.categories.FlakyTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;

/**
 * Dunit class for testing gemfire function commands : export logs
 */
@Category(DistributedTest.class)
public class MiscellaneousCommandsExportLogsPart4DUnitTest extends ExportLogsTestBase {

  private static final String LOG_LINE = "some unique line to test for";

  private transient MiscellaneousCommands misc;
  private String start;

  @Before
  public void setUpJmxManagerInVM0() {
    setUpJmxManagerOnVm0ThenConnect(null); // jmx manager in VM-0
  }

  @Before
  public void setUpCacheInLocalVM() {
    start = sf.format(new Date(System.currentTimeMillis() - ONE_MINUTE));
    getCache();
    misc = new MiscellaneousCommands();
  }

  @Test
  public void testExportLogsForTimeRange1() throws IOException {
    String end = sf.format(new Date(System.currentTimeMillis() + ONE_HOUR));

    getHost(0).getVM(0).invoke(() -> getCache().getLogger().info(LOG_LINE));

    Result cmdResult = misc.exportLogs(temporaryFolder.getRoot().getCanonicalPath(), null,
        null, LOG_LEVEL, false, false, start, end);

    System.out.println(getTestMethodName() + " command result =" + cmdResult);

    assertThat(cmdResult).isNotNull();
    assertThat(cmdResult.getStatus()).isEqualTo(OK);

    assertThat(getLogFileForThisTest()).contains(LOG_LINE);
  }

  private String getLogFileForThisTest() throws IOException {
    File[] logs = new File(temporaryFolder.getRoot(), "gfsh_files").listFiles((File dir, String name) -> isLogFileForThisTest(name));
    assertThat(logs).hasSize(1);
    return FileUtils.readFileToString(logs[0]);
  }

  private boolean isLogFileForThisTest(String name) {
    return name.contains(getTestMethodName()) && name.endsWith(".log");
  }

  @Category(FlakyTest.class) // GEODE-1500 (http)
  @Test
  public void testExportLogsForTimeRangeForOnlyStartTime() throws IOException {
    Result cmdResult =
        misc.exportLogs(temporaryFolder.getRoot().getCanonicalPath(), null,
            null, LOG_LEVEL, false, false, start, null);

    System.out.println(getTestMethodName() + " command result =" + cmdResult);

    assertThat(cmdResult).isNotNull();
    assertThat(cmdResult.getStatus()).isEqualTo(OK);

//    assertThat(getLogFileForThisTest()).contains(LOG_LINE);
  }

}
