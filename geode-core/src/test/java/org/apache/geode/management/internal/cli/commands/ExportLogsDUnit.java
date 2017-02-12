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

package org.apache.geode.management.internal.cli.commands;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.io.FileUtils;
import org.apache.geode.cache.AttributesFactory;
import org.apache.geode.cache.DataPolicy;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.RegionShortcut;
import org.apache.geode.cache.Scope;
import org.apache.geode.internal.cache.GemFireCacheImpl;
import org.apache.geode.internal.cache.InternalRegionArguments;
import org.apache.geode.internal.logging.LogService;
import org.apache.geode.management.internal.cli.functions.ExportLogsFunction;
import org.apache.geode.management.internal.cli.result.CommandResult;
import org.apache.geode.management.internal.configuration.EventTestCacheWriter;
import org.apache.geode.management.internal.configuration.domain.Configuration;
import org.apache.geode.management.internal.configuration.utils.ZipUtils;
import org.apache.geode.test.dunit.rules.GfshShellConnectionRule;
import org.apache.geode.test.dunit.rules.Locator;
import org.apache.geode.test.dunit.rules.LocatorServerStartupRule;
import org.apache.geode.test.dunit.rules.Server;
import org.apache.logging.log4j.core.Appender;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;


public class ExportLogsDUnit {
  @Rule
  public LocatorServerStartupRule lsRule = new LocatorServerStartupRule();

  @Rule
  public GfshShellConnectionRule gfshConnector = new GfshShellConnectionRule();

  private Locator locator;

  @Test
  public void testExport() throws Exception {
    locator = lsRule.startLocatorVM(0);

    Server server = lsRule.startServerVM(1, locator.getPort());
    Server server2 = lsRule.startServerVM(2, locator.getPort());

    gfshConnector.connectAndVerify(locator);

    CommandResult result = gfshConnector.executeAndVerifyCommand(
        "export logs  --dir=" + lsRule.getTempFolder().getRoot().getCanonicalPath());

    File locatorWorkingDir = locator.getWorkingDir();
    List<File> zipFilesInDir = Stream.of(locatorWorkingDir.listFiles())
        .filter(f -> f.getName().endsWith(".zip")).collect(toList());

    assertThat(zipFilesInDir).hasSize(1);

    File unzippedLogFileDir = lsRule.getTempFolder().newFolder("unzippedLogs");
    ZipUtils.unzip(zipFilesInDir.get(0).getCanonicalPath(), unzippedLogFileDir.getCanonicalPath());

    Set<File> actualDirs =
        Stream.of(unzippedLogFileDir.listFiles()).filter(File::isDirectory).collect(toSet());

    assertThat(actualDirs).hasSize(2);

    Set<String> expectedDirNames = Stream.of(server.getName(), server2.getName()).collect(toSet());
    Set<String> actualDirNames = actualDirs.stream().map(File::getName).collect(toSet());

    assertThat(actualDirNames).isEqualTo(expectedDirNames);

    System.out.println("Unzipped artifacts:");
    for (File dir : actualDirs) {
      Set<String> fileNamesInDir = Stream.of(dir.listFiles()).map(File::getName).collect(toSet());

      System.out.println(dir.getCanonicalPath() + " : " + fileNamesInDir);
      assertThat(fileNamesInDir).contains(dir.getName() + ".log");
      assertThat(fileNamesInDir).hasSize(1);
      // TODO: Verify contents of files. (Write tests for logs containing multiple log levels,
      // where some lines get through a filter and some do not
    }

    // Ensure export logs region does not accumulate data
    server.invoke(() -> {
      Region exportLogsRegion = ExportLogsFunction.createOrGetExistingExportLogsRegion(false);
      assertThat(exportLogsRegion.size()).isEqualTo(0);
    });
    server2.invoke(() -> {
      Region exportLogsRegion = ExportLogsFunction.createOrGetExistingExportLogsRegion(false);
      assertThat(exportLogsRegion.size()).isEqualTo(0);
    });
    locator.invoke(() -> {
      Region exportLogsRegion = ExportLogsFunction.createOrGetExistingExportLogsRegion(true);
      assertThat(exportLogsRegion.size()).isEqualTo(0);
    });
  }


}
