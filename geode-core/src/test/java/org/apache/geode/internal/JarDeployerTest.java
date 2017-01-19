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

package org.apache.geode.internal;


import static org.apache.geode.internal.Assert.fail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.geode.test.junit.categories.UnitTest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

@Category(UnitTest.class)
public class JarDeployerTest {

  private ClassBuilder classBuilder;

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Rule
  public RestoreSystemProperties restoreSystemProperties = new RestoreSystemProperties();

  @Before
  public void setup() {
    System.setProperty("user.dir", temporaryFolder.getRoot().getAbsolutePath());
    classBuilder = new ClassBuilder();
    ClassPathLoader.setLatestToDefault(temporaryFolder.getRoot());
  }

  @Test
  public void testDeployFileAndChange() throws IOException, ClassNotFoundException {
    final JarDeployer jarDeployer = ClassPathLoader.getLatest().getJarDeployer();

    // First deploy of the JAR file
    byte[] jarBytes = this.classBuilder.createJarFromName("ClassA");
    DeployedJar deployedJar =
        jarDeployer.deploy(new String[] {"JarDeployerDUnit.jar"}, new byte[][] {jarBytes})[0];

    assertThat(deployedJar.getFile()).exists();
    assertThat(deployedJar.getFile().getName()).contains(".v1.");
    assertThat(deployedJar.getFile().getName()).doesNotContain(".v2.");
    assertThat(jarDeployer.getNextVersionedJarFile("JarDeployerDUnit.jar").getName()).contains(".v2.");

    assertThat(ClassPathLoader.getLatest().forName("ClassA")).isNotNull();

    assertThat(doesFileMatchBytes(deployedJar.getFile(), jarBytes));

    // Now deploy an updated JAR file and make sure that the next version of the JAR file
    // was created and the first one was deleted.
    jarBytes = this.classBuilder.createJarFromName("ClassB");
    DeployedJar newJarClassLoader =
        jarDeployer.deploy(new String[] {"JarDeployerDUnit.jar"}, new byte[][] {jarBytes})[0];
    File nextDeployedJar = new File(newJarClassLoader.getFileCanonicalPath());

    assertThat(nextDeployedJar.exists());
    assertThat(nextDeployedJar.getName()).contains(".v2.");
    assertThat(doesFileMatchBytes(nextDeployedJar, jarBytes));

    assertThat(ClassPathLoader.getLatest().forName("ClassB")).isNotNull();

    assertThatThrownBy(() -> ClassPathLoader.getLatest().forName("ClassA"))
        .isExactlyInstanceOf(ClassNotFoundException.class);


    assertThat(jarDeployer.findSortedOldVersionsOfJar("JarDeployerDUnit.jar")).hasSize(2);
    assertThat(jarDeployer.findDistinctDeployedJars()).hasSize(1);
  }

  @Test
  public void testDeployNoUpdateWhenNoChange() throws IOException, ClassNotFoundException {
    final JarDeployer jarDeployer = ClassPathLoader.getLatest().getJarDeployer();

    // First deploy of the JAR file
    byte[] jarBytes = this.classBuilder.createJarFromName("JarDeployerDUnitDNUWNC");
    DeployedJar jarClassLoader =
        jarDeployer.deploy(new String[] {"JarDeployerDUnit.jar"}, new byte[][] {jarBytes})[0];
    File deployedJar = new File(jarClassLoader.getFileCanonicalPath());

    assertThat(deployedJar).exists();
    assertThat(deployedJar.getName()).contains(".v1.");
    DeployedJar newJarClassLoader =
        jarDeployer.deploy(new String[] {"JarDeployerDUnit.jar"}, new byte[][] {jarBytes})[0];
    assertThat(newJarClassLoader).isNull();
  }

  @Test
  public void testDeployToInvalidDirectory() throws IOException, ClassNotFoundException {
    final File alternateDir = new File(temporaryFolder.getRoot(), "JarDeployerDUnit");
    FileUtil.delete(alternateDir);

    ClassPathLoader.setLatestToDefault(alternateDir);
    final JarDeployer jarDeployer = ClassPathLoader.getLatest().getJarDeployer();

    final CyclicBarrier barrier = new CyclicBarrier(2);
    final byte[] jarBytes = this.classBuilder.createJarFromName("JarDeployerDUnitDTID");

    // Test to verify that deployment fails if the directory doesn't exist.
    assertThatThrownBy(() -> {
      jarDeployer.deploy(new String[] {"JarDeployerDUnit.jar"}, new byte[][] {jarBytes});
    }).isInstanceOf(IOException.class);

    // Test to verify that deployment succeeds if the directory doesn't
    // initially exist, but is then created while the JarDeployer is looping
    // looking for a valid directory.
    Thread thread = new Thread() {
      @Override
      public void run() {
        try {
          barrier.await();
        } catch (InterruptedException iex) {
          fail("Interrupted while waiting.");
        } catch (BrokenBarrierException bbex) {
          fail("Broken barrier.");
        }

        try {
          jarDeployer.deploy(new String[] {"JarDeployerDUnit.jar"}, new byte[][] {jarBytes});
        } catch (IOException ioex) {
          fail("IOException received unexpectedly.");
        } catch (ClassNotFoundException cnfex) {
          fail("ClassNotFoundException received unexpectedly.");
        }
      }
    };
    thread.start();

    try {
      barrier.await();
      Thread.sleep(500);
      alternateDir.mkdir();
      thread.join();
    } catch (InterruptedException iex) {
      fail("Interrupted while waiting.");
    } catch (BrokenBarrierException bbex) {
      fail("Broken barrier.");
    }
  }

  @Test
  public void testVersionNumberCreation() throws IOException, ClassNotFoundException {
    final JarDeployer jarDeployer = ClassPathLoader.getLatest().getJarDeployer();

    File versionedName = jarDeployer.getNextVersionedJarFile("myJar.jar");
    assertThat(versionedName.getName()).isEqualTo(JarDeployer.JAR_PREFIX + "myJar.v1.jar");

    byte[] jarBytes = this.classBuilder.createJarFromName("ClassA");
    DeployedJar jarClassLoader =
        jarDeployer.deploy(new String[] {"myJar.jar"}, new byte[][] {jarBytes})[0];
    File deployedJar = new File(jarClassLoader.getFileCanonicalPath());

    assertThat(deployedJar.getName()).isEqualTo(JarDeployer.JAR_PREFIX + "myJar.v1.jar");
    assertThat(jarDeployer.getNextVersionedJarFile("myJar.jar").getName())
        .isEqualTo(JarDeployer.JAR_PREFIX + "myJar.v2.jar");

  }

  @Test
  public void testVersionNumberMatcher() throws IOException {
    final JarDeployer jarDeployer = ClassPathLoader.getLatest().getJarDeployer();

    int version = jarDeployer.extractVersionFromFilename(
        temporaryFolder.newFile(JarDeployer.JAR_PREFIX + "MyJar.v1.jar").getName());

    assertThat(version).isEqualTo(1);
  }

  protected boolean doesFileMatchBytes(final File file, final byte[] bytes) throws IOException {
    // If the don't have the same number of bytes then nothing to do
    if (file.length() != bytes.length) {
      return false;
    }

    // Open the file then loop comparing each byte
    InputStream inStream = new FileInputStream(file);
    int index = 0;
    try {
      for (; index < bytes.length; index++) {
        if (((byte) inStream.read()) != bytes[index]) {
          break;
        }
      }
    } finally {
      inStream.close();
    }

    // If we didn't get to the end then something was different
    if (index < bytes.length) {
      return false;
    }

    return true;
  }

}
