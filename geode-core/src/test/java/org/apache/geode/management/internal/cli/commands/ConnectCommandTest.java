/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.geode.management.internal.cli.commands;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Map;

import org.junit.Test;
import org.junit.experimental.categories.Category;

import org.apache.geode.management.cli.Result;
import org.apache.geode.management.internal.cli.shell.Gfsh;
import org.apache.geode.management.internal.cli.util.ConnectionEndpoint;
import org.apache.geode.management.internal.web.domain.LinkIndex;
import org.apache.geode.management.internal.web.shell.CapturingHttpOperationInvoker;
import org.apache.geode.test.junit.categories.UnitTest;

@Category(UnitTest.class)
public class ConnectCommandTest {

  private ConnectionEndpoint memberRmiHostPort = null;
  private ConnectionEndpoint locatorTcpHostPort = null;
  private String userName = null;
  private String password = null;
  private String keystore = null;
  private String keystorePassword = null;
  private String truststore = null;
  private String truststorePassword = null;
  private String sslCiphers = null;
  private String sslProtocols = null;
  private boolean useHttp = false;
  private boolean useSsl = false;
  private Gfsh gfsh = null;
  private String gfSecurityPropertiesPath = null;
  private String url = null;


  @Test
  public void connectionReportsGenericErrorWhenNotGivenValidArguments() throws Exception {
    ConnectCommand
        command =
        new ConnectCommand(memberRmiHostPort, locatorTcpHostPort, userName, password, keystore,
            keystorePassword, truststore, truststorePassword, sslCiphers, sslProtocols, useHttp,
            useSsl, gfsh, gfSecurityPropertiesPath, url);

    Result result = command.run();
    assertThat(result.getStatus()).describedAs(result.toString()).isEqualTo(Result.Status.ERROR);
  }

  @Test
  public void x() throws Exception {
    useHttp = true;
    url = "http://google.com";
    gfsh = mock(Gfsh.class);
//    SimpleHttpOperationInvoker invoker = spy(SimpleHttpOperationInvoker.class);
//    final ClientHttpRequest request, final Class<T> responseType
//    ArgumentCaptor<ClientHttpRequest> requestCaptor = ArgumentCaptor.forClass(ClientHttpRequest.class);
//
//    doReturn(null).when(invoker).send(any(), any() );

    CapturingHttpOperationInvoker operationInvoker = new CapturingHttpOperationInvoker();

    ConnectCommand
        command =
        new ConnectCommand(memberRmiHostPort, locatorTcpHostPort, userName, password, keystore,
            keystorePassword, truststore, truststorePassword, sslCiphers, sslProtocols, useHttp,
            useSsl, gfsh, gfSecurityPropertiesPath, url, operationInvoker) {
          @Override
          public LinkIndex getLinkIndex(Map<String, String> securityProps, String query) {
            return null;
          }
        };

    Result result = command.run();
//    verify(invoker).send(requestCaptor.capture(), any());
//


    assertThat(operationInvoker.getRequest()).isNotNull();
    assertThat(operationInvoker.getRequest().getURI()).isEqualTo(null);
  }
}