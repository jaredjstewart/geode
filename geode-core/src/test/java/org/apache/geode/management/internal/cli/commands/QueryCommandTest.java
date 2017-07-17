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

import static org.apache.geode.management.internal.cli.commands.QueryInterceptor.FILE_ALREADY_EXISTS_MESSAGE;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;

import org.apache.geode.cache.Cache;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.RegionShortcut;
import org.apache.geode.management.cli.Result;
import org.apache.geode.management.internal.cli.result.CommandResult;
import org.apache.geode.management.internal.cli.shell.Gfsh;
import org.apache.geode.test.dunit.rules.GfshShellConnectionRule;
import org.apache.geode.test.dunit.rules.ServerStarterRule;
import org.apache.geode.test.junit.categories.IntegrationTest;

@Category(IntegrationTest.class)
public class QueryCommandTest {
  @ClassRule
  public static ServerStarterRule server =
      new ServerStarterRule().withJMXManager().withRegion(RegionShortcut.REPLICATE, "A");

  public static class Customer {
    public String name;
    public String address;

    public Customer(String name, String address) {
      this.name = name;
      this.address = address;
    }

    public String toString() {
      return name + address;
    }
  }

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @BeforeClass
  public static void beforeClass() {
    Cache cache = server.getCache();
    Region region = cache.getRegion("A");
    // have a table size larger than the fetch size
    for (int i = 0; i < Gfsh.DEFAULT_APP_FETCH_SIZE + 1; i++) {
      region.put("key" + i, "value" + i);
      // region.put("key" + i, new Customer("name" +i, "address" +i));
    }
  }

  @Rule
  public GfshShellConnectionRule gfsh = new GfshShellConnectionRule();

  @Test
  public void doesShowLimitIfLimitNotInQuery() throws Exception {
    gfsh.connectAndVerify(server.getJmxPort(), GfshShellConnectionRule.PortType.jmxManger);
    String result = gfsh.execute("query --query='select * from /A'");
    assertThat(result).contains("Rows   : " + Gfsh.DEFAULT_APP_FETCH_SIZE);
    assertThat(result).contains("Limit  : " + Gfsh.DEFAULT_APP_FETCH_SIZE);
  }

  @Test
  public void doesNotShowLimitIfLimitInQuery() throws Exception {
    gfsh.connectAndVerify(server.getJmxPort(), GfshShellConnectionRule.PortType.jmxManger);
    String result = gfsh.execute("query --query='select * from /A limit 50'");
    assertThat(result).contains("Rows   : 50");
    assertThat(result).doesNotContain("Limit");
  }

  @Test
  public void overHttp() throws Exception {
    gfsh.connectAndVerify(server.getHttpPort(), GfshShellConnectionRule.PortType.http);
    String result = gfsh.execute("query --query='select * from /A'");
    assertThat(result).contains("Rows   : " + Gfsh.DEFAULT_APP_FETCH_SIZE);
  }

  @Test
  public void invalidQueryShouldNotCreateFile() throws Exception {
    gfsh.connectAndVerify(server.getHttpPort(), GfshShellConnectionRule.PortType.http);
    File outputFile = temporaryFolder.newFile("queryOutput.txt");
    FileUtils.deleteQuietly(outputFile);
    String result =
        gfsh.execute("query --query='invalid query' --file=" + outputFile.getAbsolutePath());
    assertThat(outputFile).doesNotExist();
    assertThat(result).containsPattern("Result\\s+:\\s+false");
    assertThat(result).doesNotContain("Query results output to");
  }

  @Test
  public void canOutputToFile() throws Exception {
    File outputFile = temporaryFolder.newFile("queryOutput.txt");
    FileUtils.deleteQuietly(outputFile);

    gfsh.connectAndVerify(server.getHttpPort(), GfshShellConnectionRule.PortType.http);
    CommandResult result = gfsh.executeAndVerifyCommand(
        "query --query='select * from /A' --file=" + outputFile.getAbsolutePath());
    assertThat(outputFile).exists();
    assertThat(result.getContent().toString()).contains(outputFile.getAbsolutePath());

    List<String> lines = Files.readLines(outputFile, StandardCharsets.UTF_8);

    assertThat(lines.get(0)).isEqualTo("Result");
    assertThat(lines.get(1)).isEqualTo("--------");
    lines.subList(2, lines.size()).forEach(line -> assertThat(line).matches("value\\d+"));
  }

  @Test
  public void outputToFileStillDisplaysResultMetaData() throws Exception {
    File outputFile = temporaryFolder.newFile("queryOutput.txt");
    FileUtils.deleteQuietly(outputFile);

    gfsh.connectAndVerify(server.getJmxPort(), GfshShellConnectionRule.PortType.jmxManger);
    String result =
        gfsh.execute("query --query='select * from /A' --file=" + outputFile.getAbsolutePath());

    assertThat(result).contains("Rows");
    assertThat(result).contains("Limit");
    assertThat(result).contains("Query results output to");
  }

  @Test
  public void doesNotOverwriteExistingFile() throws Exception {
    File outputFile = temporaryFolder.newFile("queryOutput.txt");
    assertThat(outputFile).exists();

    gfsh.connectAndVerify(server.getHttpPort(), GfshShellConnectionRule.PortType.http);
    CommandResult result = gfsh
        .executeCommand("query --query='select * from /A' --file=" + outputFile.getAbsolutePath());
    assertThat(result.getStatus()).isEqualTo(Result.Status.ERROR);
    assertThat(result.getContent().toString()).contains(FILE_ALREADY_EXISTS_MESSAGE);
  }
}
