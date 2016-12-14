/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.geode.test.dunit.rules;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class GroupConfig implements Serializable {
  public String name;
  private Set<String> jars = new HashSet<>();
  private Set<String> configFiles = new HashSet<>();
  private Set<String> regions = new HashSet<>();
  private String maxLogFileSize;

  public GroupConfig(GroupConfig that) {
    this.jars.addAll(that.jars);
    this.configFiles.addAll(that.configFiles);
    this.regions.addAll(that.regions);
    this.maxLogFileSize = that.maxLogFileSize;
    this.name = that.name;
  }

  public GroupConfig( String name) {
    this.name = name;
  }

  public GroupConfig regions(String... regions) {
    GroupConfig copy = new GroupConfig(this);
    copy.regions.addAll(Arrays.asList(regions));
    return copy;
  }

  public GroupConfig jars(String... jars) {
    GroupConfig copy = new GroupConfig(this);
    copy.jars.addAll(Arrays.asList(jars));
    return copy;
  }

  public GroupConfig removeJar(String jar) {
    GroupConfig copy = new GroupConfig(this);
    copy.jars.remove(jar);
    return copy;
  }

  public GroupConfig addJar(String jar) {
    GroupConfig copy = new GroupConfig(this);
    copy.jars.add(jar);
    return copy;
  }

  public GroupConfig maxLogFileSize(String maxLogFileSize) {
    GroupConfig copy = new GroupConfig(this);
    copy.maxLogFileSize = maxLogFileSize;
    return copy;
  }

  public Set<String> getJars() {
    return Collections.unmodifiableSet(this.jars);
  }
  public Set<String> getConfigFiles() {
    return Collections.unmodifiableSet(this.configFiles);
  }
  public Set<String> getRegions() {
    return Collections.unmodifiableSet(this.regions);
  }

  public String getMaxLogFileSize() {
    return this.maxLogFileSize;
  }
}