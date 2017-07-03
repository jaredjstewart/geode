package org.apache.geode.management.internal.cli;
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

import java.io.IOException;

import org.apache.geode.i18n.StringId;
import org.apache.geode.management.internal.cli.shell.OperationInvoker;

public interface CommandContext {

  void setOperationInvoker(OperationInvoker invoker);
  OperationInvoker getOperationInvoker();


  boolean isConnectedAndReady();
  boolean isQuietMode();


  String readPassword(String s) throws IOException;
  String readText(String s) throws IOException;


  boolean logToFile(String s, Throwable t);
  void logInfo(String s, Throwable t);
  void printAsSevere(String format);




}
