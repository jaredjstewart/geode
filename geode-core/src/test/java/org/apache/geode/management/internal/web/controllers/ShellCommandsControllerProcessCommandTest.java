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
package org.apache.geode.management.internal.web.controllers;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import org.apache.geode.management.internal.cli.CommandResponseBuilder;
import org.apache.geode.management.internal.cli.result.CommandResult;
import org.apache.geode.management.internal.cli.result.ErrorResultData;
import org.apache.geode.management.internal.cli.result.InfoResultData;
import org.apache.geode.management.internal.cli.result.ResultBuilder;
import org.apache.geode.test.junit.categories.IntegrationTest;
import org.apache.geode.test.junit.categories.UnitTest;

@Category(UnitTest.class)
public class ShellCommandsControllerProcessCommandTest {
  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private ShellCommandsController controller;
  private CommandResult fakeResult;

  @Before
  public void setup() {

    controller = new ShellCommandsController() {
      @Override
      protected String processCommand(final String command) {
        return CommandResponseBuilder.createCommandResponseJson("someMember", fakeResult);
      }
    };
  }

  @Test
  public void infoOkResult() throws IOException {
    fakeResult = new CommandResult(new InfoResultData("Some info message"));

    ResponseEntity<InputStreamResource> responseJsonStream = controller.command("xyz");
    assertThatContentTypeEquals(responseJsonStream, MediaType.APPLICATION_JSON);

    String responseJson = toString(responseJsonStream);
    CommandResult result = ResultBuilder.fromJson(responseJson);

    assertThat(result.nextLine()).isEqualTo(fakeResult.nextLine());
  }

  @Test
  public void errorResult() throws IOException {
    ErrorResultData errorResultData = new ErrorResultData("Some error message");
    fakeResult = new CommandResult(errorResultData);

    ResponseEntity<InputStreamResource> responseJsonStream = controller.command("xyz");
    assertThatContentTypeEquals(responseJsonStream, MediaType.APPLICATION_JSON);

    String responseJson = toString(responseJsonStream);
    CommandResult result = ResultBuilder.fromJson(responseJson);

    assertThat(result.nextLine()).isEqualTo(fakeResult.nextLine());
  }

  @Test
  public void resultWithFile() throws IOException {
    File tempFile = temporaryFolder.newFile();
    FileUtils.writeStringToFile(tempFile, "some file contents", "UTF-8");

    fakeResult = new CommandResult(tempFile.toPath());

    ResponseEntity<InputStreamResource> responseFileStream = controller.command("xyz");

    assertThatContentTypeEquals(responseFileStream, MediaType.APPLICATION_OCTET_STREAM);

    String fileContents = toFileContents(responseFileStream);
    assertThat(fileContents).isEqualTo("some file contents");
  }

  private String toFileContents(ResponseEntity<InputStreamResource> response) throws IOException {
    return IOUtils.toString(response.getBody().getInputStream(), "UTF-8");
  }

  private String toString(ResponseEntity<InputStreamResource> response) throws IOException {
    return IOUtils.toString(response.getBody().getInputStream(), "UTF-8");
  }

  private void assertThatContentTypeEquals(ResponseEntity<InputStreamResource> response,
                                           MediaType mediaType) {
    assertThat(response.getHeaders().get(HttpHeaders.CONTENT_TYPE))
        .containsExactly(mediaType.toString());

  }
}