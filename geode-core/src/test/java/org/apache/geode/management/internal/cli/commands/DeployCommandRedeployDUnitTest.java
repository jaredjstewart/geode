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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.apache.geode.cache.execute.Execution;
import org.apache.geode.cache.execute.FunctionService;
import org.apache.geode.cache.execute.ResultCollector;
import org.apache.geode.distributed.DistributedSystem;
import org.apache.geode.internal.ClassBuilder;
import org.apache.geode.internal.ClassPathLoader;
import org.apache.geode.internal.cache.GemFireCacheImpl;
import org.apache.geode.management.internal.cli.result.CommandResult;
import org.apache.geode.test.dunit.rules.GfshShellConnectionRule;
import org.apache.geode.test.dunit.rules.Locator;
import org.apache.geode.test.dunit.rules.LocatorServerStartupRule;
import org.apache.geode.test.dunit.rules.Server;
import org.apache.geode.test.junit.categories.DistributedTest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;

/**
 * Unit tests for the DeployCommands class
 * 
 * @since GemFire 7.0
 */
@SuppressWarnings("serial")
@Category(DistributedTest.class)
public class DeployCommandRedeployDUnitTest implements Serializable {
  private final String functionName = "DeployCommandRedeployDUnitFunction";
  private final String jarName = "DeployCommandRedeployDUnitTest.jar";

  private static final String VERSION1 = "Version1";
  private static final String VERSION2 = "Version2";

  private File jarVersion1;
  private File jarVersion2;

  private Locator locator;
  private Server server1;
  private Server server2;

  @Rule
  public LocatorServerStartupRule lsRule = new LocatorServerStartupRule();

  @Rule
  public transient GfshShellConnectionRule gfshConnector = new GfshShellConnectionRule();

  @Before
  public void setup() throws Exception {
    jarVersion1 = createVersionOfJar(VERSION1);
    jarVersion2 = createVersionOfJar(VERSION2);

    locator = lsRule.startLocatorVM(0);
    server1 = lsRule.startServerVM(1, locator.getPort());
    server2 = lsRule.startServerVM(2, locator.getPort());

    gfshConnector.connectAndVerify(locator);
  }

  @Test
  public void redeployJarWithNewVersionOfFunction() throws Exception {
    gfshConnector.executeAndVerifyCommand("deploy --jar=" + jarVersion1.getCanonicalPath());

    server1.invoke(() -> assertThatCanLoad("jddunit.function." + jarName, functionName));
    server1.invoke(() -> assertThatFunctionHasVersion(functionName, VERSION1));


    gfshConnector.executeAndVerifyCommand("deploy --jar=" + jarVersion2.getCanonicalPath());
    server1.invoke(() -> assertThatCanLoad("jddunit.function." + jarName, functionName));
    server1.invoke(() -> assertThatFunctionHasVersion(functionName, VERSION2));
  }


  private File createVersionOfJar(String version) throws IOException {
    String classContents =
        "package jddunit.function;" + "import org.apache.geode.cache.execute.Function;"
            + "import org.apache.geode.cache.execute.FunctionContext;" + "public class "
            + functionName + " implements Function {" + "public boolean hasResult() {return true;}"
            + "public void execute(FunctionContext context) {context.getResultSender().lastResult(\""
            + version + "\");}" + "public String getId() {return \"" + functionName + "\";}"
            + "public boolean optimizeForWrite() {return false;}"
            + "public boolean isHA() {return false;}}";

    File jar = new File(lsRule.getTempFolder().newFolder(version), this.jarName);
    ClassBuilder functionClassBuilder = new ClassBuilder();
    functionClassBuilder.writeJarFromContent("jddunit/function/" + functionName, classContents,
        jar);

    return jar;
  }

  private void assertThatFunctionHasVersion(String functionId, String version) {
    GemFireCacheImpl gemFireCache = GemFireCacheImpl.getInstance();
    DistributedSystem distributedSystem = gemFireCache.getDistributedSystem();
    Execution execution =
        FunctionService.onMember(distributedSystem, distributedSystem.getDistributedMember());
    List<String> result = (List<String>) execution.execute(functionId).getResult();
    assertThat(result.get(0)).isEqualTo(version);
  }

  private void assertThatCanLoad(String jarName, String className) throws ClassNotFoundException {
    assertThat(ClassPathLoader.getLatest().getJarDeployer().findDeployedJar(jarName)).isNotNull();
    assertThat(ClassPathLoader.getLatest().forName(className)).isNotNull();
  }
}
