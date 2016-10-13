/*
 *
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
package org.apache.geode.tools.pulse.internal.util;

import java.io.IOException;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;

/**
 * 
 *
 */
public class ConnectionUtil {

  public static SocketFactory getSocketFactory(boolean usessl) throws IOException {
    if (usessl) {
      return (SSLSocketFactory) SSLSocketFactory.getDefault();
    } else {
      return SocketFactory.getDefault();
    }
  }

}
