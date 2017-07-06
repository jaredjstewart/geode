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
package org.apache.geode.management.internal;

public class Credential {
  private final String identifier;
  private final String password;

  public Credential(String identifier, String password) {
    this.identifier = identifier;
    this.password = password;
  }

  public String getIdentifier() {
    return identifier;
  }

  public String getPassword() {
    return password;
  }

  public boolean isValid() {
    return hasIdentifier() && hasPassword();
  }

  public void onValid(Runnable lambda) {
    if (isValid()) {
      lambda.run();
    }
  }

  /**
   * blank and empty string password should be considered valid
   * 
   * @return
   */
  public boolean hasPassword() {
    return password != null;
  }

  public boolean hasIdentifier() {
    return identifier != null;
  }
}
