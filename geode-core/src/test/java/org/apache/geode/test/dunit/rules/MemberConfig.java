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
import java.security.acl.Group;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class MemberConfig implements Serializable {
  private List<GroupConfig> groups;

  public MemberConfig(GroupConfig... groupConfigs) {
    this.groups = new ArrayList<GroupConfig>();

    Collections.addAll(this.groups, groupConfigs);
  }

  public String getMaxLogFileSize() {
    if (this.groups.size() == 0) {
      return null;
    }
    GroupConfig lastGroupAdded = this.groups.get(this.groups.size() - 1);
    return lastGroupAdded.getMaxLogFileSize();
  }

  public List<String> getJarNames() {
    return groups.stream().flatMap((GroupConfig groupConfig) -> groupConfig.getJars().stream()).collect(
        Collectors.toList());
  }

  public List<String> getRegions() {
    return groups.stream().flatMap((GroupConfig groupConfig) -> groupConfig.getRegions().stream()).collect(
        Collectors.toList());
  }

  public List<GroupConfig> getGroups() {
    return Collections.unmodifiableList(groups);
  }

}
