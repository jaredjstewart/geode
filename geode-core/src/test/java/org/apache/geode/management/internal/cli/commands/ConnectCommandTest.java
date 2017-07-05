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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;

import java.util.Map;

import org.junit.Test;
import org.junit.experimental.categories.Category;

import org.apache.geode.management.internal.Credential;
import org.apache.geode.management.internal.cli.shell.Gfsh;
import org.apache.geode.management.internal.cli.util.ConnectionEndpoint;
import org.apache.geode.management.internal.web.shell.SimpleHttpOperationInvoker;
import org.apache.geode.test.junit.categories.UnitTest;

@Category(UnitTest.class)
public class ConnectCommandTest {

  private ConnectionEndpoint memberRmiHostPort = null;
  private ConnectionEndpoint locatorTcpHostPort = null;
  private Credential user = null;
  private Credential keystore = null;
  private Credential truststore = null;
  private String sslCiphers = null;
  private String sslProtocols = null;
  private boolean useHttp = false;
  private boolean useSsl = false;
  private Gfsh gfsh = null;
  private String gfSecurityPropertiesPath = null;
  private String url = null;


  @Test
  public void connectionReportsGenericErrorWhenNotGivenValidArguments() throws Exception {
    ConnectCommand command = new ConnectCommand(locatorTcpHostPort, memberRmiHostPort, user, keystore, truststore, sslCiphers,
        sslProtocols, useHttp, useSsl, gfsh, gfSecurityPropertiesPath, url);
    assertThatThrownBy(command::run).isInstanceOf(NullPointerException.class);
  }

  @Test
  public void connectSetsSimpleHttpOperationInvoker() throws Exception {
    useHttp = true;
    url = "";
    user = new Credential(null, null);
    keystore = new Credential(null, null);
    truststore = new Credential(null, null);
    gfsh = mock(Gfsh.class);

    doCallRealMethod().when(gfsh).getOperationInvoker();
    doCallRealMethod().when(gfsh).setOperationInvoker(any());

    ConnectCommand command = new ConnectCommand(locatorTcpHostPort, memberRmiHostPort, user, keystore, truststore, sslCiphers,
        sslProtocols, useHttp, useSsl, gfsh, gfSecurityPropertiesPath, url) {
      @Override
      public void verifyAuthenticatedConnection(Map<String, String> securityProps, String query) {}
    };

    command.run();
    assertThat(gfsh.getOperationInvoker()).isInstanceOf(SimpleHttpOperationInvoker.class);
  }

  @Test
  public void testReadSSLConfig() throws Exception {
  }
}
