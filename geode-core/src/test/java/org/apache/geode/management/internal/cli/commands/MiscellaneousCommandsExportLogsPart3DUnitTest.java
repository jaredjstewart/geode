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

import org.apache.geode.cache.Cache;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.RegionFactory;
import org.apache.geode.cache.RegionShortcut;
import org.apache.geode.internal.FileUtil;
import org.apache.geode.internal.logging.LogWriterImpl;
import org.apache.geode.management.cli.Result;
import org.apache.geode.management.internal.cli.result.CommandResult;
import org.apache.geode.test.dunit.Host;
import org.apache.geode.test.dunit.SerializableRunnable;
import org.apache.geode.test.dunit.VM;
import org.apache.geode.test.junit.categories.DistributedTest;
import org.apache.geode.test.junit.categories.FlakyTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

import static org.apache.geode.management.cli.Result.Status.OK;
import static org.apache.geode.test.dunit.Assert.assertEquals;
import static org.apache.geode.test.dunit.Assert.fail;
import static org.apache.geode.test.dunit.LogWriterUtils.getLogWriter;
import static org.apache.geode.distributed.ConfigurationProperties.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dunit class for testing gemfire function commands : export logs
 */
@Category(DistributedTest.class)
public class MiscellaneousCommandsExportLogsPart3DUnitTest extends ExportLogsTestBase {

  private static final String GROUP1 = "Group1";

  private transient MiscellaneousCommands misc;
  private String start;
  private String end;


  @Before
  public void setUpCacheInLocalVM() {
    start = sf.format(new Date(System.currentTimeMillis() - ONE_MINUTE));
    end = sf.format(new Date(System.currentTimeMillis() + ONE_HOUR));
    getCache();
    misc = new MiscellaneousCommands();
  }

  @Category(FlakyTest.class) // GEODE-672
  @Test
  public void testExportLogsForGroup() throws IOException {
    Properties props = new Properties();
    props.setProperty(NAME, "Manager");
    props.setProperty(GROUPS, GROUP1);
    setUpJmxManagerOnVm0ThenConnect(props);

    Result cmdResult = misc.exportLogsPreprocessing(temporaryFolder.getRoot().getCanonicalPath(), new String[]{ GROUP1 }, null,
        LOG_LEVEL, false, false, start, end, 1);

    System.out.println(getTestMethodName() + " command result =" + cmdResult);

    assertThat(cmdResult).isNotNull();
    assertThat(cmdResult.getStatus()).isEqualTo(OK);
  }

  @Test
  public void testExportLogsForMember() throws IOException {
    setUpJmxManagerOnVm0ThenConnect(null);

    final VM vm1 = Host.getHost(0).getVM(1);
    final String vm1MemberId = vm1.invoke(this::getMemberId);

    Result cmdResult = misc.exportLogsPreprocessing(temporaryFolder.getRoot().getCanonicalPath(), null,
        vm1MemberId, LOG_LEVEL, false, false, start, end, 1);

    System.out.println(getTestMethodName() + " command result =" + cmdResult);

    assertThat(cmdResult).isNotNull();
    assertThat(cmdResult.getStatus()).isEqualTo(OK);
  }

  private String getMemberId() {
    return getCache().getDistributedSystem().getDistributedMember().getId();
  }

}
