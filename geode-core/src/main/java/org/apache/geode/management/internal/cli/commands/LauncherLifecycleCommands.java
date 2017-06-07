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

import static org.apache.geode.distributed.ConfigurationProperties.BIND_ADDRESS;
import static org.apache.geode.distributed.ConfigurationProperties.CACHE_XML_FILE;
import static org.apache.geode.distributed.ConfigurationProperties.CLUSTER_CONFIGURATION_DIR;
import static org.apache.geode.distributed.ConfigurationProperties.ENABLE_CLUSTER_CONFIGURATION;
import static org.apache.geode.distributed.ConfigurationProperties.ENABLE_TIME_STATISTICS;
import static org.apache.geode.distributed.ConfigurationProperties.GROUPS;
import static org.apache.geode.distributed.ConfigurationProperties.HTTP_SERVICE_BIND_ADDRESS;
import static org.apache.geode.distributed.ConfigurationProperties.HTTP_SERVICE_PORT;
import static org.apache.geode.distributed.ConfigurationProperties.JMX_MANAGER_HOSTNAME_FOR_CLIENTS;
import static org.apache.geode.distributed.ConfigurationProperties.LOAD_CLUSTER_CONFIGURATION_FROM_DIR;
import static org.apache.geode.distributed.ConfigurationProperties.LOCATORS;
import static org.apache.geode.distributed.ConfigurationProperties.LOCATOR_WAIT_TIME;
import static org.apache.geode.distributed.ConfigurationProperties.LOCK_MEMORY;
import static org.apache.geode.distributed.ConfigurationProperties.LOG_LEVEL;
import static org.apache.geode.distributed.ConfigurationProperties.MCAST_ADDRESS;
import static org.apache.geode.distributed.ConfigurationProperties.MCAST_PORT;
import static org.apache.geode.distributed.ConfigurationProperties.MEMCACHED_BIND_ADDRESS;
import static org.apache.geode.distributed.ConfigurationProperties.MEMCACHED_PORT;
import static org.apache.geode.distributed.ConfigurationProperties.MEMCACHED_PROTOCOL;
import static org.apache.geode.distributed.ConfigurationProperties.OFF_HEAP_MEMORY_SIZE;
import static org.apache.geode.distributed.ConfigurationProperties.REDIS_BIND_ADDRESS;
import static org.apache.geode.distributed.ConfigurationProperties.REDIS_PASSWORD;
import static org.apache.geode.distributed.ConfigurationProperties.REDIS_PORT;
import static org.apache.geode.distributed.ConfigurationProperties.SERVER_BIND_ADDRESS;
import static org.apache.geode.distributed.ConfigurationProperties.SOCKET_BUFFER_SIZE;
import static org.apache.geode.distributed.ConfigurationProperties.START_DEV_REST_API;
import static org.apache.geode.distributed.ConfigurationProperties.STATISTIC_ARCHIVE_FILE;
import static org.apache.geode.distributed.ConfigurationProperties.USE_CLUSTER_CONFIGURATION;
import static org.apache.geode.management.internal.cli.commands.LauncherLifecycleCommands.StartServer.START_SERVER__PASSWORD;
import static org.apache.geode.management.internal.cli.i18n.CliStrings.LOCATOR_TERM_NAME;
import static org.apache.geode.management.internal.cli.shell.MXBeanProvider.getDistributedSystemMXBean;
import static org.apache.geode.management.internal.cli.util.HostUtils.getLocatorId;

import org.apache.commons.lang.ArrayUtils;
import org.apache.geode.SystemFailure;
import org.apache.geode.cache.server.CacheServer;
import org.apache.geode.distributed.AbstractLauncher;
import org.apache.geode.distributed.ConfigurationProperties;
import org.apache.geode.distributed.LocatorLauncher;
import org.apache.geode.distributed.LocatorLauncher.LocatorState;
import org.apache.geode.distributed.ServerLauncher;
import org.apache.geode.distributed.ServerLauncher.ServerState;
import org.apache.geode.distributed.internal.ClusterConfigurationService;
import org.apache.geode.distributed.internal.DistributionConfig;
import org.apache.geode.internal.GemFireVersion;
import org.apache.geode.internal.OSProcess;
import org.apache.geode.internal.i18n.LocalizedStrings;
import org.apache.geode.internal.lang.StringUtils;
import org.apache.geode.internal.lang.SystemUtils;
import org.apache.geode.internal.process.ClusterConfigurationNotAvailableException;
import org.apache.geode.internal.process.ProcessLauncherContext;
import org.apache.geode.internal.process.ProcessStreamReader;
import org.apache.geode.internal.process.ProcessStreamReader.InputListener;
import org.apache.geode.internal.process.ProcessStreamReader.ReadingMode;
import org.apache.geode.internal.process.ProcessType;
import org.apache.geode.internal.process.signal.SignalEvent;
import org.apache.geode.internal.process.signal.SignalListener;
import org.apache.geode.internal.util.IOUtils;
import org.apache.geode.management.DistributedSystemMXBean;
import org.apache.geode.management.cli.CliMetaData;
import org.apache.geode.management.cli.ConverterHint;
import org.apache.geode.management.cli.Result;
import org.apache.geode.management.internal.cli.CliUtil;
import org.apache.geode.management.internal.cli.GfshParser;
import org.apache.geode.management.internal.cli.LogWrapper;
import org.apache.geode.management.internal.cli.domain.ConnectToLocatorResult;
import org.apache.geode.management.internal.cli.i18n.CliStrings;
import org.apache.geode.management.internal.cli.result.InfoResultData;
import org.apache.geode.management.internal.cli.result.ResultBuilder;
import org.apache.geode.management.internal.cli.shell.Gfsh;
import org.apache.geode.management.internal.cli.shell.JmxOperationInvoker;
import org.apache.geode.management.internal.cli.util.CauseFinder;
import org.apache.geode.management.internal.cli.util.CommandStringBuilder;
import org.apache.geode.management.internal.cli.util.ConnectionEndpoint;
import org.apache.geode.management.internal.cli.util.HostUtils;
import org.apache.geode.management.internal.cli.util.ThreePhraseGenerator;
import org.apache.geode.management.internal.configuration.utils.ClusterConfigurationStatusRetriever;
import org.apache.geode.management.internal.security.ResourceConstants;
import org.apache.geode.security.AuthenticationFailedException;
import org.springframework.shell.core.annotation.CliAvailabilityIndicator;
import org.springframework.shell.core.annotation.CliCommand;
import org.springframework.shell.core.annotation.CliOption;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import javax.management.MalformedObjectNameException;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;

/**
 * The LauncherLifecycleCommands class encapsulates all GemFire launcher commands for GemFire tools
 * (like starting GemFire Monitor (GFMon) and Visual Statistics Display (VSD)) as well external
 * tools (like jconsole).
 * <p>
 *
 * @see org.apache.geode.distributed.LocatorLauncher
 * @see org.apache.geode.distributed.ServerLauncher
 * @see GfshCommand
 * @see org.apache.geode.management.internal.cli.shell.Gfsh
 * @since GemFire 7.0
 */
@SuppressWarnings("unused")
public class LauncherLifecycleCommands implements GfshCommand {
  private static final String SERVER_TERM_NAME = "Server";

  private static final long PROCESS_STREAM_READER_JOIN_TIMEOUT_MILLIS = 30 * 1000;
  private static final long PROCESS_STREAM_READER_ASYNC_STOP_TIMEOUT_MILLIS = 5 * 1000;
  private static final long WAITING_FOR_STOP_TO_MAKE_PID_GO_AWAY_TIMEOUT_MILLIS = 30 * 1000;
  private static final long WAITING_FOR_PID_FILE_TO_CONTAIN_PID_TIMEOUT_MILLIS = 2 * 1000;

  protected static final int CMS_INITIAL_OCCUPANCY_FRACTION = 60;
  protected static final int INVALID_PID = -1;
  protected static final int MINIMUM_HEAP_FREE_RATIO = 10;

  protected static final String GEODE_HOME = System.getenv("GEODE_HOME");
  protected static final String JAVA_HOME = System.getProperty("java.home");

  // MUST CHANGE THIS TO REGEX SINCE VERSION CHANGES IN JAR NAME
  protected static final String GEODE_JAR_PATHNAME =
      IOUtils.appendToPath(GEODE_HOME, "lib", GemFireVersion.getGemFireJarFileName());

  protected static final String CORE_DEPENDENCIES_JAR_PATHNAME =
      IOUtils.appendToPath(GEODE_HOME, "lib", "geode-dependencies.jar");

  private final ThreePhraseGenerator nameGenerator;

  public LauncherLifecycleCommands() {
    nameGenerator = new ThreePhraseGenerator();
  }

  @CliCommand(value = StartLocator.START_LOCATOR, help = StartLocator.START_LOCATOR__HELP)
  @CliMetaData(shellOnly = true,
      relatedTopic = {CliStrings.TOPIC_GEODE_LOCATOR, CliStrings.TOPIC_GEODE_LIFECYCLE})
  public Result startLocator(
      @CliOption(key = StartLocator.START_LOCATOR__MEMBER_NAME,
          help = StartLocator.START_LOCATOR__MEMBER_NAME__HELP) String memberName,
      @CliOption(key = StartLocator.START_LOCATOR__BIND_ADDRESS,
          help = StartLocator.START_LOCATOR__BIND_ADDRESS__HELP) final String bindAddress,
      @CliOption(key = StartLocator.START_LOCATOR__CLASSPATH,
          help = StartLocator.START_LOCATOR__CLASSPATH__HELP) final String classpath,
      @CliOption(key = StartLocator.START_LOCATOR__FORCE, unspecifiedDefaultValue = "false",
          specifiedDefaultValue = "true",
          help = StartLocator.START_LOCATOR__FORCE__HELP) final Boolean force,
      @CliOption(key = StartLocator.START_LOCATOR__GROUP, optionContext = ConverterHint.MEMBERGROUP,
          help = StartLocator.START_LOCATOR__GROUP__HELP) final String group,
      @CliOption(key = StartLocator.START_LOCATOR__HOSTNAME_FOR_CLIENTS,
          help = StartLocator.START_LOCATOR__HOSTNAME_FOR_CLIENTS__HELP) final String hostnameForClients,
      @CliOption(key = ConfigurationProperties.JMX_MANAGER_HOSTNAME_FOR_CLIENTS,
          help = StartLocator.START_LOCATOR__JMX_MANAGER_HOSTNAME_FOR_CLIENTS__HELP) final String jmxManagerHostnameForClients,
      @CliOption(key = StartLocator.START_LOCATOR__INCLUDE_SYSTEM_CLASSPATH,
          specifiedDefaultValue = "true", unspecifiedDefaultValue = "false",
          help = StartLocator.START_LOCATOR__INCLUDE_SYSTEM_CLASSPATH__HELP) final Boolean includeSystemClasspath,
      @CliOption(key = StartLocator.START_LOCATOR__LOCATORS,
          optionContext = ConverterHint.LOCATOR_DISCOVERY_CONFIG,
          help = StartLocator.START_LOCATOR__LOCATORS__HELP) final String locators,
      @CliOption(key = StartLocator.START_LOCATOR__LOG_LEVEL,
          optionContext = ConverterHint.LOG_LEVEL,
          help = StartLocator.START_LOCATOR__LOG_LEVEL__HELP) final String logLevel,
      @CliOption(key = StartLocator.START_LOCATOR__MCAST_ADDRESS,
          help = StartLocator.START_LOCATOR__MCAST_ADDRESS__HELP) final String mcastBindAddress,
      @CliOption(key = StartLocator.START_LOCATOR__MCAST_PORT,
          help = StartLocator.START_LOCATOR__MCAST_PORT__HELP) final Integer mcastPort,
      @CliOption(key = StartLocator.START_LOCATOR__PORT,
          help = StartLocator.START_LOCATOR__PORT__HELP) final Integer port,
      @CliOption(key = StartLocator.START_LOCATOR__DIR,
          help = StartLocator.START_LOCATOR__DIR__HELP) String workingDirectory,
      @CliOption(key = StartLocator.START_LOCATOR__PROPERTIES,
          optionContext = ConverterHint.FILE_PATH,
          help = StartLocator.START_LOCATOR__PROPERTIES__HELP) String gemfirePropertiesPathname,
      @CliOption(key = StartLocator.START_LOCATOR__SECURITY_PROPERTIES,
          optionContext = ConverterHint.FILE_PATH,
          help = StartLocator.START_LOCATOR__SECURITY_PROPERTIES__HELP) String gemfireSecurityPropertiesPathname,
      @CliOption(key = StartLocator.START_LOCATOR__INITIALHEAP,
          help = StartLocator.START_LOCATOR__INITIALHEAP__HELP) final String initialHeap,
      @CliOption(key = StartLocator.START_LOCATOR__MAXHEAP,
          help = StartLocator.START_LOCATOR__MAXHEAP__HELP) final String maxHeap,
      @CliOption(key = StartLocator.START_LOCATOR__J, optionContext = GfshParser.J_OPTION_CONTEXT,
          help = StartLocator.START_LOCATOR__J__HELP) final String[] jvmArgsOpts,
      @CliOption(key = StartLocator.START_LOCATOR__CONNECT, unspecifiedDefaultValue = "true",
          specifiedDefaultValue = "true",
          help = StartLocator.START_LOCATOR__CONNECT__HELP) final boolean connect,
      @CliOption(key = StartLocator.START_LOCATOR__ENABLE__SHARED__CONFIGURATION,
          unspecifiedDefaultValue = "true", specifiedDefaultValue = "true",
          help = StartLocator.START_LOCATOR__ENABLE__SHARED__CONFIGURATION__HELP) final boolean enableSharedConfiguration,
      @CliOption(key = StartLocator.START_LOCATOR__LOAD__SHARED_CONFIGURATION__FROM__FILESYSTEM,
          unspecifiedDefaultValue = "false",
          help = StartLocator.START_LOCATOR__LOAD__SHARED_CONFIGURATION__FROM__FILESYSTEM__HELP) final boolean loadSharedConfigurationFromDirectory,
      @CliOption(key = StartLocator.START_LOCATOR__CLUSTER__CONFIG__DIR,
          unspecifiedDefaultValue = "",
          help = StartLocator.START_LOCATOR__CLUSTER__CONFIG__DIR__HELP) final String clusterConfigDir,
      @CliOption(key = StartLocator.START_LOCATOR__HTTP_SERVICE_PORT,
          help = StartLocator.START_LOCATOR__HTTP_SERVICE_PORT__HELP) final Integer httpServicePort,
      @CliOption(key = StartLocator.START_LOCATOR__HTTP_SERVICE_BIND_ADDRESS,
          help = StartLocator.START_LOCATOR__HTTP_SERVICE_BIND_ADDRESS__HELP) final String httpServiceBindAddress) {
    try {
      if (StringUtils.isBlank(memberName)) {
        // when the user doesn't give us a name, we make one up!
        memberName = nameGenerator.generate('-');
      }

      workingDirectory = resolveWorkingDir(workingDirectory, memberName);

      gemfirePropertiesPathname = CliUtil.resolvePathname(gemfirePropertiesPathname);

      if (StringUtils.isNotBlank(gemfirePropertiesPathname)
          && !IOUtils.isExistingPathname(gemfirePropertiesPathname)) {
        return ResultBuilder.createUserErrorResult(
            CliStrings.format(CliStrings.GEODE_0_PROPERTIES_1_NOT_FOUND_MESSAGE, StringUtils.EMPTY,
                gemfirePropertiesPathname));
      }

      gemfireSecurityPropertiesPathname =
          CliUtil.resolvePathname(gemfireSecurityPropertiesPathname);

      if (StringUtils.isNotBlank(gemfireSecurityPropertiesPathname)
          && !IOUtils.isExistingPathname(gemfireSecurityPropertiesPathname)) {
        return ResultBuilder.createUserErrorResult(
            CliStrings.format(CliStrings.GEODE_0_PROPERTIES_1_NOT_FOUND_MESSAGE, "Security ",
                gemfireSecurityPropertiesPathname));
      }

      File locatorPidFile = new File(workingDirectory, ProcessType.LOCATOR.getPidFileName());

      final int oldPid = readPid(locatorPidFile);

      Properties gemfireProperties = new Properties();

      setPropertyIfNotNull(gemfireProperties, GROUPS, group);
      setPropertyIfNotNull(gemfireProperties, LOCATORS, locators);
      setPropertyIfNotNull(gemfireProperties, LOG_LEVEL, logLevel);
      setPropertyIfNotNull(gemfireProperties, MCAST_ADDRESS, mcastBindAddress);
      setPropertyIfNotNull(gemfireProperties, MCAST_PORT, mcastPort);
      setPropertyIfNotNull(gemfireProperties, ENABLE_CLUSTER_CONFIGURATION,
          enableSharedConfiguration);
      setPropertyIfNotNull(gemfireProperties, LOAD_CLUSTER_CONFIGURATION_FROM_DIR,
          loadSharedConfigurationFromDirectory);
      setPropertyIfNotNull(gemfireProperties, CLUSTER_CONFIGURATION_DIR, clusterConfigDir);
      setPropertyIfNotNull(gemfireProperties, HTTP_SERVICE_PORT, httpServicePort);
      setPropertyIfNotNull(gemfireProperties, HTTP_SERVICE_BIND_ADDRESS, httpServiceBindAddress);
      setPropertyIfNotNull(gemfireProperties, JMX_MANAGER_HOSTNAME_FOR_CLIENTS,
          jmxManagerHostnameForClients);


      // read the OSProcess enable redirect system property here -- TODO: replace with new GFSH
      // argument
      final boolean redirectOutput =
          Boolean.getBoolean(OSProcess.ENABLE_OUTPUT_REDIRECTION_PROPERTY);
      LocatorLauncher.Builder locatorLauncherBuilder =
          new LocatorLauncher.Builder().setBindAddress(bindAddress).setForce(force).setPort(port)
              .setRedirectOutput(redirectOutput).setWorkingDirectory(workingDirectory);
      if (hostnameForClients != null) {
        locatorLauncherBuilder.setHostnameForClients(hostnameForClients);
      }
      if (memberName != null) {
        locatorLauncherBuilder.setMemberName(memberName);
      }
      LocatorLauncher locatorLauncher = locatorLauncherBuilder.build();

      String[] locatorCommandLine = createStartLocatorCommandLine(locatorLauncher,
          gemfirePropertiesPathname, gemfireSecurityPropertiesPathname, gemfireProperties,
          classpath, includeSystemClasspath, jvmArgsOpts, initialHeap, maxHeap);

      final Process locatorProcess = new ProcessBuilder(locatorCommandLine)
          .directory(new File(locatorLauncher.getWorkingDirectory())).start();

      locatorProcess.getInputStream().close();
      locatorProcess.getOutputStream().close();

      // fix TRAC bug #51967 by using NON_BLOCKING on Windows
      final ReadingMode readingMode =
          SystemUtils.isWindows() ? ReadingMode.NON_BLOCKING : ReadingMode.BLOCKING;

      final StringBuffer message = new StringBuffer(); // need thread-safe StringBuffer
      InputListener inputListener = new InputListener() {
        @Override
        public void notifyInputLine(String line) {
          message.append(line);
          if (readingMode == ReadingMode.BLOCKING) {
            message.append(StringUtils.LINE_SEPARATOR);
          }
        }
      };

      ProcessStreamReader stderrReader = new ProcessStreamReader.Builder(locatorProcess)
          .inputStream(locatorProcess.getErrorStream()).inputListener(inputListener)
          .readingMode(readingMode).continueReadingMillis(2 * 1000).build().start();

      LocatorState locatorState;

      String previousLocatorStatusMessage = null;

      LauncherSignalListener locatorSignalListener = new LauncherSignalListener();

      final boolean registeredLocatorSignalListener =
          getGfsh().getSignalHandler().registerListener(locatorSignalListener);

      try {
        getGfsh().logInfo(String.format(StartLocator.START_LOCATOR__RUN_MESSAGE,
            IOUtils.tryGetCanonicalPathElseGetAbsolutePath(
                new File(locatorLauncher.getWorkingDirectory()))),
            null);

        locatorState = LocatorState.fromDirectory(workingDirectory, memberName);
        do {
          if (locatorProcess.isAlive()) {
            Gfsh.print(".");

            synchronized (this) {
              TimeUnit.MILLISECONDS.timedWait(this, 500);
            }

            locatorState = LocatorState.fromDirectory(workingDirectory, memberName);

            String currentLocatorStatusMessage = locatorState.getStatusMessage();

            if (locatorState.isStartingOrNotResponding()
                && !(StringUtils.isBlank(currentLocatorStatusMessage)
                    || currentLocatorStatusMessage.equalsIgnoreCase(previousLocatorStatusMessage)
                    || currentLocatorStatusMessage.trim().toLowerCase().equals("null"))) {
              Gfsh.println();
              Gfsh.println(currentLocatorStatusMessage);
              previousLocatorStatusMessage = currentLocatorStatusMessage;
            }
          } else {
            final int exitValue = locatorProcess.exitValue();

            return ResultBuilder.createShellClientErrorResult(String.format(
                StartLocator.START_LOCATOR__PROCESS_TERMINATED_ABNORMALLY_ERROR_MESSAGE, exitValue,
                locatorLauncher.getWorkingDirectory(), message.toString()));
          }
        } while (!(registeredLocatorSignalListener && locatorSignalListener.isSignaled())
            && locatorState.isStartingOrNotResponding());
      } finally {
        stderrReader.stopAsync(PROCESS_STREAM_READER_ASYNC_STOP_TIMEOUT_MILLIS); // stop will close
                                                                                 // ErrorStream
        getGfsh().getSignalHandler().unregisterListener(locatorSignalListener);
      }

      Gfsh.println();

      final boolean asyncStart =
          (registeredLocatorSignalListener && locatorSignalListener.isSignaled()
              && ServerState.isStartingNotRespondingOrNull(locatorState));

      InfoResultData infoResultData = ResultBuilder.createInfoResultData();

      if (asyncStart) {
        infoResultData
            .addLine(String.format(CliStrings.ASYNC_PROCESS_LAUNCH_MESSAGE, LOCATOR_TERM_NAME));
      } else {
        infoResultData.addLine(locatorState.toString());

        String locatorHostName;
        InetAddress bindAddr = locatorLauncher.getBindAddress();
        if (bindAddr != null) {
          locatorHostName = bindAddr.getCanonicalHostName();
        } else {
          locatorHostName = StringUtils.defaultIfBlank(locatorLauncher.getHostnameForClients(),
              HostUtils.getLocalHost());
        }

        int locatorPort = Integer.parseInt(locatorState.getPort());

        // AUTO-CONNECT
        // If the connect succeeds add the connected message to the result,
        // Else, ask the user to use the "connect" command to connect to the Locator.
        if (shouldAutoConnect(connect)) {
          doAutoConnect(locatorHostName, locatorPort, gemfirePropertiesPathname,
              gemfireSecurityPropertiesPathname, infoResultData);
        }

        // Report on the state of the Shared Configuration service if enabled...
        if (enableSharedConfiguration) {
          infoResultData.addLine(
              ClusterConfigurationStatusRetriever.fromLocator(locatorHostName, locatorPort));
        }
      }

      return ResultBuilder.buildResult(infoResultData);
    } catch (IllegalArgumentException e) {
      String message = e.getMessage();
      if (message != null && message.matches(
          LocalizedStrings.Launcher_Builder_UNKNOWN_HOST_ERROR_MESSAGE.toLocalizedString(".+"))) {
        message =
            CliStrings.format(CliStrings.LAUNCHERLIFECYCLECOMMANDS__MSG__FAILED_TO_START_0_REASON_1,
                LOCATOR_TERM_NAME, message);
      }
      return ResultBuilder.createUserErrorResult(message);
    } catch (IllegalStateException e) {
      return ResultBuilder.createUserErrorResult(e.getMessage());
    } catch (VirtualMachineError e) {
      SystemFailure.initiateFailure(e);
      throw e;
    } catch (Throwable t) {
      SystemFailure.checkFailure();
      String errorMessage = String.format(StartLocator.START_LOCATOR__GENERAL_ERROR_MESSAGE,
          StringUtils.defaultIfBlank(workingDirectory, memberName), getLocatorId(bindAddress, port),
          toString(t, getGfsh().getDebug()));
      getGfsh().logToFile(errorMessage, t);
      return ResultBuilder.createShellClientErrorResult(errorMessage);
    } finally {
      Gfsh.redirectInternalJavaLoggers();
    }
  }

  private void setPropertyIfNotNull(Properties properties, String key, Object value) {
    if (key != null && value != null) {
      properties.setProperty(key, value.toString());
    }
  }

  protected String[] createStartLocatorCommandLine(final LocatorLauncher launcher,
      final String gemfirePropertiesPathname, final String gemfireSecurityPropertiesPathname,
      final Properties gemfireProperties, final String userClasspath,
      final Boolean includeSystemClasspath, final String[] jvmArgsOpts, final String initialHeap,
      final String maxHeap) throws MalformedObjectNameException {
    List<String> commandLine = new ArrayList<>();

    commandLine.add(getJavaPath());
    commandLine.add("-server");
    commandLine.add("-classpath");
    commandLine
        .add(getLocatorClasspath(Boolean.TRUE.equals(includeSystemClasspath), userClasspath));

    addCurrentLocators(commandLine, gemfireProperties);
    addGemFirePropertyFile(commandLine, gemfirePropertiesPathname);
    addGemFireSecurityPropertyFile(commandLine, gemfireSecurityPropertiesPathname);
    addGemFireSystemProperties(commandLine, gemfireProperties);
    addJvmArgumentsAndOptions(commandLine, jvmArgsOpts);
    addInitialHeap(commandLine, initialHeap);
    addMaxHeap(commandLine, maxHeap);

    commandLine.add(
        "-D".concat(AbstractLauncher.SIGNAL_HANDLER_REGISTRATION_SYSTEM_PROPERTY.concat("=true")));
    commandLine.add("-Djava.awt.headless=true");
    commandLine.add(
        "-Dsun.rmi.dgc.server.gcInterval".concat("=").concat(Long.toString(Long.MAX_VALUE - 1)));
    commandLine.add(LocatorLauncher.class.getName());
    commandLine.add(LocatorLauncher.Command.START.getName());

    if (StringUtils.isNotBlank(launcher.getMemberName())) {
      commandLine.add(launcher.getMemberName());
    }

    if (launcher.getBindAddress() != null) {
      commandLine.add("--bind-address=" + launcher.getBindAddress().getCanonicalHostName());
    }

    if (launcher.isDebugging() || isDebugging()) {
      commandLine.add("--debug");
    }

    if (launcher.isForcing()) {
      commandLine.add("--force");
    }

    if (StringUtils.isNotBlank(launcher.getHostnameForClients())) {
      commandLine.add("--hostname-for-clients=" + launcher.getHostnameForClients());
    }

    if (launcher.getPort() != null) {
      commandLine.add("--port=" + launcher.getPort());
    }

    if (launcher.isRedirectingOutput()) {
      commandLine.add("--redirect-output");
    }

    return commandLine.toArray(new String[commandLine.size()]);
  }

  // TODO should we connect implicitly when in non-interactive, headless mode (e.g. gfsh -e "start
  // locator ...")?
  // With execute option (-e), there could be multiple commands which might presume that a prior
  // "start locator"
  // has formed the connection.
  private boolean shouldAutoConnect(final boolean connect) {
    return (connect && !(getGfsh() == null || isConnectedAndReady()));
  }

  private boolean doAutoConnect(final String locatorHostname, final int locatorPort,
      final String gemfirePropertiesPathname, final String gemfireSecurityPropertiesPathname,
      final InfoResultData infoResultData) {
    boolean connectSuccess = false;
    boolean jmxManagerAuthEnabled = false;
    boolean jmxManagerSslEnabled = false;

    Map<String, String> configurationProperties = loadConfigurationProperties(
        gemfireSecurityPropertiesPathname, loadConfigurationProperties(gemfirePropertiesPathname));
    Map<String, String> locatorConfigurationProperties = new HashMap<>(configurationProperties);

    String responseFailureMessage = null;

    for (int attempts = 0; (attempts < 10 && !connectSuccess); attempts++) {
      try {
        ConnectToLocatorResult connectToLocatorResult =
            ShellCommands.connectToLocator(locatorHostname, locatorPort,
                ShellCommands.getConnectLocatorTimeoutInMS() / 4, locatorConfigurationProperties);

        ConnectionEndpoint memberEndpoint = connectToLocatorResult.getMemberEndpoint();

        jmxManagerSslEnabled = connectToLocatorResult.isJmxManagerSslEnabled();

        if (!jmxManagerSslEnabled) {
          configurationProperties.clear();
        }

        getGfsh().setOperationInvoker(new JmxOperationInvoker(memberEndpoint.getHost(),
            memberEndpoint.getPort(), null, null, configurationProperties, null));

        String shellAndLogMessage = CliStrings.format(CliStrings.CONNECT__MSG__SUCCESS,
            "JMX Manager " + memberEndpoint.toString(false));

        infoResultData.addLine("\n");
        infoResultData.addLine(shellAndLogMessage);
        getGfsh().logToFile(shellAndLogMessage, null);

        connectSuccess = true;
        responseFailureMessage = null;
      } catch (IllegalStateException unexpected) {
        if (CauseFinder.indexOfCause(unexpected, ClassCastException.class, false) != -1) {
          responseFailureMessage = "The Locator might require SSL Configuration.";
        }
      } catch (SecurityException ignore) {
        getGfsh().logToFile(ignore.getMessage(), ignore);
        jmxManagerAuthEnabled = true;
        break; // no need to continue after SecurityException
      } catch (AuthenticationFailedException ignore) {
        getGfsh().logToFile(ignore.getMessage(), ignore);
        jmxManagerAuthEnabled = true;
        break; // no need to continue after AuthenticationFailedException
      } catch (SSLException ignore) {
        if (ignore instanceof SSLHandshakeException) {
          // try to connect again without SSL since the SSL handshake failed implying a plain text
          // connection...
          locatorConfigurationProperties.clear();
        } else {
          // another type of SSL error occurred (possibly a configuration issue); pass the buck...
          getGfsh().logToFile(ignore.getMessage(), ignore);
          responseFailureMessage = "Check your SSL configuration and try again.";
          break;
        }
      } catch (Exception ignore) {
        getGfsh().logToFile(ignore.getMessage(), ignore);
        responseFailureMessage = "Failed to connect; unknown cause: " + ignore.getMessage();
      }
    }

    if (!connectSuccess) {
      doOnConnectionFailure(locatorHostname, locatorPort, jmxManagerAuthEnabled,
          jmxManagerSslEnabled, infoResultData);
    }

    if (StringUtils.isNotBlank(responseFailureMessage)) {
      infoResultData.addLine("\n");
      infoResultData.addLine(responseFailureMessage);
    }

    return connectSuccess;
  }

  private void doOnConnectionFailure(final String locatorHostName, final int locatorPort,
      final boolean jmxManagerAuthEnabled, final boolean jmxManagerSslEnabled,
      final InfoResultData infoResultData) {

    infoResultData.addLine("\n");
    infoResultData.addLine(CliStrings.format(StartLocator.START_LOCATOR__USE__0__TO__CONNECT,
        new CommandStringBuilder(CliStrings.CONNECT)
            .addOption(CliStrings.CONNECT__LOCATOR, locatorHostName + "[" + locatorPort + "]")
            .toString()));

    StringBuilder message = new StringBuilder();

    if (jmxManagerAuthEnabled) {
      message.append("Authentication");
    }
    if (jmxManagerSslEnabled) {
      message.append(jmxManagerAuthEnabled ? " and " : StringUtils.EMPTY)
          .append("SSL configuration");
    }
    if (jmxManagerAuthEnabled || jmxManagerSslEnabled) {
      message.append(" required to connect to the Manager.");
      infoResultData.addLine("\n");
      infoResultData.addLine(message.toString());
    }
  }

  private Map<String, String> loadConfigurationProperties(
      final String configurationPropertiesPathname) {
    return loadConfigurationProperties(configurationPropertiesPathname, null);
  }

  private Map<String, String> loadConfigurationProperties(
      final String configurationPropertiesPathname, Map<String, String> configurationProperties) {
    configurationProperties =
        (configurationProperties != null ? configurationProperties : new HashMap<>());

    if (IOUtils.isExistingPathname(configurationPropertiesPathname)) {
      try {
        configurationProperties.putAll(ShellCommands
            .loadPropertiesFromURL(new File(configurationPropertiesPathname).toURI().toURL()));
      } catch (MalformedURLException ignore) {
        LogWrapper.getInstance()
            .warning(String.format(
                "Failed to load GemFire configuration properties from pathname (%1$s)!",
                configurationPropertiesPathname), ignore);
      }
    }

    return configurationProperties;
  }

  // TODO re-evaluate whether a MalformedObjectNameException should be thrown here; just because we
  // were not able to find
  // the "current" Locators in order to conveniently add the new member to the GemFire cluster does
  // not mean we should
  // throw an Exception!
  protected void addCurrentLocators(final List<String> commandLine,
      final Properties gemfireProperties) throws MalformedObjectNameException {
    if (StringUtils.isBlank(gemfireProperties.getProperty(LOCATORS))) {
      String currentLocators = getCurrentLocators();

      if (StringUtils.isNotBlank(currentLocators)) {
        commandLine.add("-D".concat(ProcessLauncherContext.OVERRIDDEN_DEFAULTS_PREFIX)
            .concat(LOCATORS).concat("=").concat(currentLocators));
      }
    }
  }

  protected void addGemFirePropertyFile(final List<String> commandLine,
      final String gemfirePropertiesPathname) {
    if (StringUtils.isNotBlank(gemfirePropertiesPathname)) {
      commandLine.add("-DgemfirePropertyFile=" + gemfirePropertiesPathname);
    }
  }

  protected void addGemFireSecurityPropertyFile(final List<String> commandLine,
      final String gemfireSecurityPropertiesPathname) {
    if (StringUtils.isNotBlank(gemfireSecurityPropertiesPathname)) {
      commandLine.add("-DgemfireSecurityPropertyFile=" + gemfireSecurityPropertiesPathname);
    }
  }

  protected void addGemFireSystemProperties(final List<String> commandLine,
      final Properties gemfireProperties) {
    for (final Object property : gemfireProperties.keySet()) {
      final String propertyName = property.toString();
      final String propertyValue = gemfireProperties.getProperty(propertyName);
      if (StringUtils.isNotBlank(propertyValue)) {
        commandLine.add(
            "-D" + DistributionConfig.GEMFIRE_PREFIX + "" + propertyName + "=" + propertyValue);
      }
    }
  }

  protected void addInitialHeap(final List<String> commandLine, final String initialHeap) {
    if (StringUtils.isNotBlank(initialHeap)) {
      commandLine.add("-Xms" + initialHeap);
    }
  }

  protected void addJvmArgumentsAndOptions(final List<String> commandLine,
      final String[] jvmArgsOpts) {
    if (jvmArgsOpts != null) {
      commandLine.addAll(Arrays.asList(jvmArgsOpts));
    }
  }

  // Fix for Bug #47192 - "Causing the GemFire member (JVM process) to exit on OutOfMemoryErrors"
  protected void addJvmOptionsForOutOfMemoryErrors(final List<String> commandLine) {
    if (SystemUtils.isHotSpotVM()) {
      if (SystemUtils.isWindows()) {
        // ProcessBuilder "on Windows" needs every word (space separated) to be
        // a different element in the array/list. See #47312. Need to study why!
        commandLine.add("-XX:OnOutOfMemoryError=taskkill /F /PID %p");
      } else { // All other platforms (Linux, Mac OS X, UNIX, etc)
        commandLine.add("-XX:OnOutOfMemoryError=kill -KILL %p");
      }
    } else if (SystemUtils.isJ9VM()) {
      // NOTE IBM states the following IBM J9 JVM command-line option/switch has side-effects on
      // "performance",
      // as noted in the reference documentation...
      // http://publib.boulder.ibm.com/infocenter/javasdk/v6r0/index.jsp?topic=/com.ibm.java.doc.diagnostics.60/diag/appendixes/cmdline/commands_jvm.html
      commandLine.add("-Xcheck:memory");
    } else if (SystemUtils.isJRockitVM()) {
      // NOTE the following Oracle JRockit JVM documentation was referenced to identify the
      // appropriate JVM option to
      // set when handling OutOfMemoryErrors.
      // http://docs.oracle.com/cd/E13150_01/jrockit_jvm/jrockit/jrdocs/refman/optionXX.html
      commandLine.add("-XXexitOnOutOfMemory");
    }
  }

  protected void addMaxHeap(final List<String> commandLine, final String maxHeap) {
    if (StringUtils.isNotBlank(maxHeap)) {
      commandLine.add("-Xmx" + maxHeap);
      commandLine.add("-XX:+UseConcMarkSweepGC");
      commandLine.add("-XX:CMSInitiatingOccupancyFraction=" + CMS_INITIAL_OCCUPANCY_FRACTION);
      // commandLine.add("-XX:MinHeapFreeRatio=" + MINIMUM_HEAP_FREE_RATIO);
    }
  }

  protected int readPid(final File pidFile) {
    assert pidFile != null : "The file from which to read the process ID (pid) cannot be null!";

    if (pidFile.isFile()) {
      BufferedReader fileReader = null;
      try {
        fileReader = new BufferedReader(new FileReader(pidFile));
        return Integer.parseInt(fileReader.readLine());
      } catch (IOException | NumberFormatException ignore) {
      } finally {
        IOUtils.close(fileReader);
      }
    }

    return INVALID_PID;
  }

  @Deprecated
  protected String getClasspath(final String userClasspath) {
    String classpath = getSystemClasspath();

    if (StringUtils.isNotBlank(userClasspath)) {
      classpath += (File.pathSeparator + userClasspath);
    }

    return classpath;
  }

  protected String getLocatorClasspath(final boolean includeSystemClasspath,
      final String userClasspath) {
    return toClasspath(includeSystemClasspath, new String[] {CORE_DEPENDENCIES_JAR_PATHNAME},
        userClasspath);
  }

  protected String getServerClasspath(final boolean includeSystemClasspath,
      final String userClasspath) {
    List<String> jarFilePathnames = new ArrayList<>();

    jarFilePathnames.add(CORE_DEPENDENCIES_JAR_PATHNAME);

    return toClasspath(includeSystemClasspath,
        jarFilePathnames.toArray(new String[jarFilePathnames.size()]), userClasspath);
  }

  protected String getSystemClasspath() {
    return System.getProperty("java.class.path");
  }

  String toClasspath(final boolean includeSystemClasspath, String[] jarFilePathnames,
      String... userClasspaths) {
    // gemfire jar must absolutely be the first JAR file on the CLASSPATH!!!
    StringBuilder classpath = new StringBuilder(getGemFireJarPath());

    userClasspaths = (userClasspaths != null ? userClasspaths : ArrayUtils.EMPTY_STRING_ARRAY);

    // Then, include user-specified classes on CLASSPATH to enable the user to override GemFire JAR
    // dependencies
    // with application-specific versions; this logic/block corresponds to classes/jar-files
    // specified with the
    // --classpath option to the 'start locator' and 'start server commands'; also this will
    // override any
    // System CLASSPATH environment variable setting, which is consistent with the Java platform
    // behavior...
    for (String userClasspath : userClasspaths) {
      if (StringUtils.isNotBlank(userClasspath)) {
        classpath.append((classpath.length() == 0) ? StringUtils.EMPTY : File.pathSeparator);
        classpath.append(userClasspath);
      }
    }

    // Now, include any System-specified CLASSPATH environment variable setting...
    if (includeSystemClasspath) {
      classpath.append(File.pathSeparator);
      classpath.append(getSystemClasspath());
    }

    jarFilePathnames =
        (jarFilePathnames != null ? jarFilePathnames : ArrayUtils.EMPTY_STRING_ARRAY);

    // And finally, include all GemFire dependencies on the CLASSPATH...
    for (String jarFilePathname : jarFilePathnames) {
      if (StringUtils.isNotBlank(jarFilePathname)) {
        classpath.append((classpath.length() == 0) ? StringUtils.EMPTY : File.pathSeparator);
        classpath.append(jarFilePathname);
      }
    }

    return classpath.toString();
  }

  protected String getGemFireJarPath() {
    String classpath = getSystemClasspath();
    String gemfireJarPath = GEODE_JAR_PATHNAME;

    for (String classpathElement : classpath.split(File.pathSeparator)) {
      // MUST CHANGE THIS TO REGEX SINCE VERSION CHANGES IN JAR NAME
      if (classpathElement.endsWith("gemfire-core-8.2.0.0-SNAPSHOT.jar")) {
        gemfireJarPath = classpathElement;
        break;
      }
    }

    return gemfireJarPath;
  }

  protected String getJavaPath() {
    return new File(new File(JAVA_HOME, "bin"), "java").getPath();
  }

  @CliCommand(value = StartServer.START_SERVER, help = StartServer.START_SERVER__HELP)
  @CliMetaData(shellOnly = true,
      relatedTopic = {CliStrings.TOPIC_GEODE_SERVER, CliStrings.TOPIC_GEODE_LIFECYCLE})
  public Result startServer(
      @CliOption(key = StartServer.START_SERVER__NAME,
          help = StartServer.START_SERVER__NAME__HELP) String memberName,
      @CliOption(key = StartServer.START_SERVER__ASSIGN_BUCKETS, unspecifiedDefaultValue = "false",
          specifiedDefaultValue = "true",
          help = StartServer.START_SERVER__ASSIGN_BUCKETS__HELP) final Boolean assignBuckets,
      @CliOption(key = StartServer.START_SERVER__BIND_ADDRESS,
          help = StartServer.START_SERVER__BIND_ADDRESS__HELP) final String bindAddress,
      @CliOption(key = StartServer.START_SERVER__CACHE_XML_FILE,
          optionContext = ConverterHint.FILE_PATH,
          help = StartServer.START_SERVER__CACHE_XML_FILE__HELP) String cacheXmlPathname,
      @CliOption(key = StartServer.START_SERVER__CLASSPATH,
          /* optionContext = ConverterHint.FILE_PATH, // there's an issue with TAB here */
          help = StartServer.START_SERVER__CLASSPATH__HELP) final String classpath,
      @CliOption(key = StartServer.START_SERVER__CRITICAL__HEAP__PERCENTAGE,
          help = StartServer.START_SERVER__CRITICAL__HEAP__HELP) final Float criticalHeapPercentage,
      @CliOption(key = StartServer.START_SERVER__CRITICAL_OFF_HEAP_PERCENTAGE,
          help = StartServer.START_SERVER__CRITICAL_OFF_HEAP__HELP) final Float criticalOffHeapPercentage,
      @CliOption(key = StartServer.START_SERVER__DIR,
          help = StartServer.START_SERVER__DIR__HELP) String workingDirectory,
      @CliOption(key = StartServer.START_SERVER__DISABLE_DEFAULT_SERVER,
          unspecifiedDefaultValue = "false", specifiedDefaultValue = "true",
          help = StartServer.START_SERVER__DISABLE_DEFAULT_SERVER__HELP) final Boolean disableDefaultServer,
      @CliOption(key = StartServer.START_SERVER__DISABLE_EXIT_WHEN_OUT_OF_MEMORY,
          unspecifiedDefaultValue = "false", specifiedDefaultValue = "true",
          help = StartServer.START_SERVER__DISABLE_EXIT_WHEN_OUT_OF_MEMORY_HELP) final Boolean disableExitWhenOutOfMemory,
      @CliOption(key = StartServer.START_SERVER__ENABLE_TIME_STATISTICS,
          specifiedDefaultValue = "true",
          help = StartServer.START_SERVER__ENABLE_TIME_STATISTICS__HELP) final Boolean enableTimeStatistics,
      @CliOption(key = StartServer.START_SERVER__EVICTION__HEAP__PERCENTAGE,
          help = StartServer.START_SERVER__EVICTION__HEAP__PERCENTAGE__HELP) final Float evictionHeapPercentage,
      @CliOption(key = StartServer.START_SERVER__EVICTION_OFF_HEAP_PERCENTAGE,
          help = StartServer.START_SERVER__EVICTION_OFF_HEAP_PERCENTAGE__HELP) final Float evictionOffHeapPercentage,
      @CliOption(key = StartServer.START_SERVER__FORCE, unspecifiedDefaultValue = "false",
          specifiedDefaultValue = "true",
          help = StartServer.START_SERVER__FORCE__HELP) final Boolean force,
      @CliOption(key = StartServer.START_SERVER__GROUP, optionContext = ConverterHint.MEMBERGROUP,
          help = StartServer.START_SERVER__GROUP__HELP) final String group,
      @CliOption(key = StartServer.START_SERVER__HOSTNAME__FOR__CLIENTS,
          help = StartServer.START_SERVER__HOSTNAME__FOR__CLIENTS__HELP) final String hostNameForClients,
      @CliOption(key = ConfigurationProperties.JMX_MANAGER_HOSTNAME_FOR_CLIENTS,
          help = StartServer.START_SERVER__JMX_MANAGER_HOSTNAME_FOR_CLIENTS__HELP) final String jmxManagerHostnameForClients,
      @CliOption(key = StartServer.START_SERVER__INCLUDE_SYSTEM_CLASSPATH,
          specifiedDefaultValue = "true", unspecifiedDefaultValue = "false",
          help = StartServer.START_SERVER__INCLUDE_SYSTEM_CLASSPATH__HELP) final Boolean includeSystemClasspath,
      @CliOption(key = StartServer.START_SERVER__INITIAL_HEAP,
          help = StartServer.START_SERVER__INITIAL_HEAP__HELP) final String initialHeap,
      @CliOption(key = StartServer.START_SERVER__J, optionContext = GfshParser.J_OPTION_CONTEXT,
          help = StartServer.START_SERVER__J__HELP) final String[] jvmArgsOpts,
      @CliOption(key = StartServer.START_SERVER__LOCATORS,
          optionContext = ConverterHint.LOCATOR_DISCOVERY_CONFIG,
          help = StartServer.START_SERVER__LOCATORS__HELP) final String locators,
      @CliOption(key = StartServer.START_SERVER__LOCATOR_WAIT_TIME,
          help = StartServer.START_SERVER__LOCATOR_WAIT_TIME_HELP) final Integer locatorWaitTime,
      @CliOption(key = StartServer.START_SERVER__LOCK_MEMORY, specifiedDefaultValue = "true",
          help = StartServer.START_SERVER__LOCK_MEMORY__HELP) final Boolean lockMemory,
      @CliOption(key = StartServer.START_SERVER__LOG_LEVEL, optionContext = ConverterHint.LOG_LEVEL,
          help = StartServer.START_SERVER__LOG_LEVEL__HELP) final String logLevel,
      @CliOption(key = StartServer.START_SERVER__MAX__CONNECTIONS,
          help = StartServer.START_SERVER__MAX__CONNECTIONS__HELP) final Integer maxConnections,
      @CliOption(key = StartServer.START_SERVER__MAXHEAP,
          help = StartServer.START_SERVER__MAXHEAP__HELP) final String maxHeap,
      @CliOption(key = StartServer.START_SERVER__MAX__MESSAGE__COUNT,
          help = StartServer.START_SERVER__MAX__MESSAGE__COUNT__HELP) final Integer maxMessageCount,
      @CliOption(key = StartServer.START_SERVER__MAX__THREADS,
          help = StartServer.START_SERVER__MAX__THREADS__HELP) final Integer maxThreads,
      @CliOption(key = StartServer.START_SERVER__MCAST_ADDRESS,
          help = StartServer.START_SERVER__MCAST_ADDRESS__HELP) final String mcastBindAddress,
      @CliOption(key = StartServer.START_SERVER__MCAST_PORT,
          help = StartServer.START_SERVER__MCAST_PORT__HELP) final Integer mcastPort,
      @CliOption(key = StartServer.START_SERVER__MEMCACHED_PORT,
          help = StartServer.START_SERVER__MEMCACHED_PORT__HELP) final Integer memcachedPort,
      @CliOption(key = StartServer.START_SERVER__MEMCACHED_PROTOCOL,
          help = StartServer.START_SERVER__MEMCACHED_PROTOCOL__HELP) final String memcachedProtocol,
      @CliOption(key = StartServer.START_SERVER__MEMCACHED_BIND_ADDRESS,
          help = StartServer.START_SERVER__MEMCACHED_BIND_ADDRESS__HELP) final String memcachedBindAddress,
      @CliOption(key = StartServer.START_SERVER__REDIS_PORT,
          help = StartServer.START_SERVER__REDIS_PORT__HELP) final Integer redisPort,
      @CliOption(key = StartServer.START_SERVER__REDIS_BIND_ADDRESS,
          help = StartServer.START_SERVER__REDIS_BIND_ADDRESS__HELP) final String redisBindAddress,
      @CliOption(key = StartServer.START_SERVER__REDIS_PASSWORD,
          help = StartServer.START_SERVER__REDIS_PASSWORD__HELP) final String redisPassword,
      @CliOption(key = StartServer.START_SERVER__MESSAGE__TIME__TO__LIVE,
          help = StartServer.START_SERVER__MESSAGE__TIME__TO__LIVE__HELP) final Integer messageTimeToLive,
      @CliOption(key = StartServer.START_SERVER__OFF_HEAP_MEMORY_SIZE,
          help = StartServer.START_SERVER__OFF_HEAP_MEMORY_SIZE__HELP) final String offHeapMemorySize,
      @CliOption(key = StartServer.START_SERVER__PROPERTIES,
          optionContext = ConverterHint.FILE_PATH,
          help = StartServer.START_SERVER__PROPERTIES__HELP) String gemfirePropertiesPathname,
      @CliOption(key = StartServer.START_SERVER__REBALANCE, unspecifiedDefaultValue = "false",
          specifiedDefaultValue = "true",
          help = StartServer.START_SERVER__REBALANCE__HELP) final Boolean rebalance,
      @CliOption(key = StartServer.START_SERVER__SECURITY_PROPERTIES,
          optionContext = ConverterHint.FILE_PATH,
          help = StartServer.START_SERVER__SECURITY_PROPERTIES__HELP) String gemfireSecurityPropertiesPathname,
      @CliOption(key = StartServer.START_SERVER__SERVER_BIND_ADDRESS,
          unspecifiedDefaultValue = CacheServer.DEFAULT_BIND_ADDRESS,
          help = StartServer.START_SERVER__SERVER_BIND_ADDRESS__HELP) final String serverBindAddress,
      @CliOption(key = StartServer.START_SERVER__SERVER_PORT,
          unspecifiedDefaultValue = ("" + CacheServer.DEFAULT_PORT),
          help = StartServer.START_SERVER__SERVER_PORT__HELP) final Integer serverPort,
      @CliOption(key = StartServer.START_SERVER__SOCKET__BUFFER__SIZE,
          help = StartServer.START_SERVER__SOCKET__BUFFER__SIZE__HELP) final Integer socketBufferSize,
      @CliOption(key = StartServer.START_SERVER__SPRING_XML_LOCATION,
          help = StartServer.START_SERVER__SPRING_XML_LOCATION_HELP) final String springXmlLocation,
      @CliOption(key = StartServer.START_SERVER__STATISTIC_ARCHIVE_FILE,
          help = StartServer.START_SERVER__STATISTIC_ARCHIVE_FILE__HELP) final String statisticsArchivePathname,
      @CliOption(key = StartServer.START_SERVER__USE_CLUSTER_CONFIGURATION,
          unspecifiedDefaultValue = "true", specifiedDefaultValue = "true",
          help = StartServer.START_SERVER__USE_CLUSTER_CONFIGURATION__HELP) final Boolean requestSharedConfiguration,
      @CliOption(key = StartServer.START_SERVER__REST_API, unspecifiedDefaultValue = "false",
          specifiedDefaultValue = "true",
          help = StartServer.START_SERVER__REST_API__HELP) final Boolean startRestApi,
      @CliOption(key = StartServer.START_SERVER__HTTP_SERVICE_PORT, unspecifiedDefaultValue = "",
          help = StartServer.START_SERVER__HTTP_SERVICE_PORT__HELP) final String httpServicePort,
      @CliOption(key = StartServer.START_SERVER__HTTP_SERVICE_BIND_ADDRESS,
          unspecifiedDefaultValue = "",
          help = StartServer.START_SERVER__HTTP_SERVICE_BIND_ADDRESS__HELP) final String httpServiceBindAddress,
      @CliOption(key = StartServer.START_SERVER__USERNAME, unspecifiedDefaultValue = "",
          help = StartServer.START_SERVER__USERNAME__HELP) final String userName,
      @CliOption(key = START_SERVER__PASSWORD, unspecifiedDefaultValue = "",
          help = StartServer.START_SERVER__PASSWORD__HELP) String passwordToUse)
  // NOTICE: keep the parameters in alphabetical order based on their CliStrings.START_SERVER_* text
  {
    try {
      if (StringUtils.isBlank(memberName)) {
        // when the user doesn't give us a name, we make one up!
        memberName = nameGenerator.generate('-');
      }

      // prompt for password is username is specified in the command
      if (StringUtils.isNotBlank(userName)) {
        if (StringUtils.isBlank(passwordToUse)) {
          passwordToUse = getGfsh().readPassword(START_SERVER__PASSWORD + ": ");
        }
        if (StringUtils.isBlank(passwordToUse)) {
          return ResultBuilder.createConnectionErrorResult(
              StartServer.START_SERVER__MSG__PASSWORD_MUST_BE_SPECIFIED);
        }
      }

      workingDirectory = resolveWorkingDir(workingDirectory, memberName);

      cacheXmlPathname = CliUtil.resolvePathname(cacheXmlPathname);

      if (StringUtils.isNotBlank(cacheXmlPathname)
          && !IOUtils.isExistingPathname(cacheXmlPathname)) {
        return ResultBuilder.createUserErrorResult(
            CliStrings.format(CliStrings.CACHE_XML_NOT_FOUND_MESSAGE, cacheXmlPathname));
      }

      gemfirePropertiesPathname = CliUtil.resolvePathname(gemfirePropertiesPathname);

      if (StringUtils.isNotBlank(gemfirePropertiesPathname)
          && !IOUtils.isExistingPathname(gemfirePropertiesPathname)) {
        return ResultBuilder.createUserErrorResult(
            CliStrings.format(CliStrings.GEODE_0_PROPERTIES_1_NOT_FOUND_MESSAGE, StringUtils.EMPTY,
                gemfirePropertiesPathname));
      }

      gemfireSecurityPropertiesPathname =
          CliUtil.resolvePathname(gemfireSecurityPropertiesPathname);

      if (StringUtils.isNotBlank(gemfireSecurityPropertiesPathname)
          && !IOUtils.isExistingPathname(gemfireSecurityPropertiesPathname)) {
        return ResultBuilder.createUserErrorResult(
            CliStrings.format(CliStrings.GEODE_0_PROPERTIES_1_NOT_FOUND_MESSAGE, "Security ",
                gemfireSecurityPropertiesPathname));
      }

      File serverPidFile = new File(workingDirectory, ProcessType.SERVER.getPidFileName());

      final int oldPid = readPid(serverPidFile);

      Properties gemfireProperties = new Properties();

      setPropertyIfNotNull(gemfireProperties, BIND_ADDRESS, bindAddress);
      setPropertyIfNotNull(gemfireProperties, CACHE_XML_FILE, cacheXmlPathname);
      setPropertyIfNotNull(gemfireProperties, ENABLE_TIME_STATISTICS, enableTimeStatistics);
      setPropertyIfNotNull(gemfireProperties, GROUPS, group);
      setPropertyIfNotNull(gemfireProperties, JMX_MANAGER_HOSTNAME_FOR_CLIENTS,
          jmxManagerHostnameForClients);
      setPropertyIfNotNull(gemfireProperties, LOCATORS, locators);
      setPropertyIfNotNull(gemfireProperties, LOCATOR_WAIT_TIME, locatorWaitTime);
      setPropertyIfNotNull(gemfireProperties, LOG_LEVEL, logLevel);
      setPropertyIfNotNull(gemfireProperties, MCAST_ADDRESS, mcastBindAddress);
      setPropertyIfNotNull(gemfireProperties, MCAST_PORT, mcastPort);
      setPropertyIfNotNull(gemfireProperties, MEMCACHED_PORT, memcachedPort);
      setPropertyIfNotNull(gemfireProperties, MEMCACHED_PROTOCOL, memcachedProtocol);
      setPropertyIfNotNull(gemfireProperties, MEMCACHED_BIND_ADDRESS, memcachedBindAddress);
      setPropertyIfNotNull(gemfireProperties, REDIS_PORT, redisPort);
      setPropertyIfNotNull(gemfireProperties, REDIS_BIND_ADDRESS, redisBindAddress);
      setPropertyIfNotNull(gemfireProperties, REDIS_PASSWORD, redisPassword);
      setPropertyIfNotNull(gemfireProperties, STATISTIC_ARCHIVE_FILE, statisticsArchivePathname);
      setPropertyIfNotNull(gemfireProperties, USE_CLUSTER_CONFIGURATION,
          requestSharedConfiguration);
      setPropertyIfNotNull(gemfireProperties, LOCK_MEMORY, lockMemory);
      setPropertyIfNotNull(gemfireProperties, OFF_HEAP_MEMORY_SIZE, offHeapMemorySize);
      setPropertyIfNotNull(gemfireProperties, START_DEV_REST_API, startRestApi);
      setPropertyIfNotNull(gemfireProperties, HTTP_SERVICE_PORT, httpServicePort);
      setPropertyIfNotNull(gemfireProperties, HTTP_SERVICE_BIND_ADDRESS, httpServiceBindAddress);
      // if username is specified in the command line, it will overwrite what's set in the
      // properties file
      if (StringUtils.isNotBlank(userName)) {
        gemfireProperties.setProperty(ResourceConstants.USER_NAME, userName);
        gemfireProperties.setProperty(ResourceConstants.PASSWORD, passwordToUse);
      }


      // read the OSProcess enable redirect system property here -- TODO: replace with new GFSH
      // argument
      final boolean redirectOutput =
          Boolean.getBoolean(OSProcess.ENABLE_OUTPUT_REDIRECTION_PROPERTY);

      ServerLauncher.Builder serverLauncherBuilder = new ServerLauncher.Builder()
          .setAssignBuckets(assignBuckets).setDisableDefaultServer(disableDefaultServer)
          .setForce(force).setRebalance(rebalance).setRedirectOutput(redirectOutput)
          .setServerBindAddress(serverBindAddress).setServerPort(serverPort)
          .setSpringXmlLocation(springXmlLocation).setWorkingDirectory(workingDirectory)
          .setCriticalHeapPercentage(criticalHeapPercentage)
          .setEvictionHeapPercentage(evictionHeapPercentage)
          .setCriticalOffHeapPercentage(criticalOffHeapPercentage)
          .setEvictionOffHeapPercentage(evictionOffHeapPercentage).setMaxConnections(maxConnections)
          .setMaxMessageCount(maxMessageCount).setMaxThreads(maxThreads)
          .setMessageTimeToLive(messageTimeToLive).setSocketBufferSize(socketBufferSize);
      if (hostNameForClients != null) {
        serverLauncherBuilder.setHostNameForClients(hostNameForClients);
      }
      if (memberName != null) {
        serverLauncherBuilder.setMemberName(memberName);
      }
      ServerLauncher serverLauncher = serverLauncherBuilder.build();

      String[] serverCommandLine = createStartServerCommandLine(serverLauncher,
          gemfirePropertiesPathname, gemfireSecurityPropertiesPathname, gemfireProperties,
          classpath, includeSystemClasspath, jvmArgsOpts, disableExitWhenOutOfMemory, initialHeap,
          maxHeap);

      if (getGfsh().getDebug()) {
        getGfsh().logInfo(StringUtils.join(serverCommandLine, StringUtils.SPACE), null);
      }

      Process serverProcess = new ProcessBuilder(serverCommandLine)
          .directory(new File(serverLauncher.getWorkingDirectory())).start();

      serverProcess.getInputStream().close();
      serverProcess.getOutputStream().close();

      // fix TRAC bug #51967 by using NON_BLOCKING on Windows
      final ReadingMode readingMode =
          SystemUtils.isWindows() ? ReadingMode.NON_BLOCKING : ReadingMode.BLOCKING;

      final StringBuffer message = new StringBuffer(); // need thread-safe StringBuffer
      InputListener inputListener = new InputListener() {
        @Override
        public void notifyInputLine(String line) {
          message.append(line);
          if (readingMode == ReadingMode.BLOCKING) {
            message.append(StringUtils.LINE_SEPARATOR);
          }
        }
      };

      ProcessStreamReader stderrReader = new ProcessStreamReader.Builder(serverProcess)
          .inputStream(serverProcess.getErrorStream()).inputListener(inputListener)
          .readingMode(readingMode).continueReadingMillis(2 * 1000).build().start();

      ServerState serverState;

      String previousServerStatusMessage = null;

      LauncherSignalListener serverSignalListener = new LauncherSignalListener();

      final boolean registeredServerSignalListener =
          getGfsh().getSignalHandler().registerListener(serverSignalListener);

      try {
        getGfsh().logInfo(String.format(StartServer.START_SERVER__RUN_MESSAGE,
            IOUtils.tryGetCanonicalPathElseGetAbsolutePath(
                new File(serverLauncher.getWorkingDirectory()))),
            null);

        serverState = ServerState.fromDirectory(workingDirectory, memberName);
        do {
          if (serverProcess.isAlive()) {
            Gfsh.print(".");

            synchronized (this) {
              TimeUnit.MILLISECONDS.timedWait(this, 500);
            }

            serverState = ServerState.fromDirectory(workingDirectory, memberName);

            String currentServerStatusMessage = serverState.getStatusMessage();

            if (serverState.isStartingOrNotResponding()
                && !(StringUtils.isBlank(currentServerStatusMessage)
                    || currentServerStatusMessage.equalsIgnoreCase(previousServerStatusMessage)
                    || currentServerStatusMessage.trim().toLowerCase().equals("null"))) {
              Gfsh.println();
              Gfsh.println(currentServerStatusMessage);
              previousServerStatusMessage = currentServerStatusMessage;
            }
          } else {
            final int exitValue = serverProcess.exitValue();

            return ResultBuilder.createShellClientErrorResult(
                String.format(StartServer.START_SERVER__PROCESS_TERMINATED_ABNORMALLY_ERROR_MESSAGE,
                    exitValue, serverLauncher.getWorkingDirectory(), message.toString()));

          }
        } while (!(registeredServerSignalListener && serverSignalListener.isSignaled())
            && serverState.isStartingOrNotResponding());
      } finally {
        stderrReader.stopAsync(PROCESS_STREAM_READER_ASYNC_STOP_TIMEOUT_MILLIS); // stop will close
                                                                                 // ErrorStream
        getGfsh().getSignalHandler().unregisterListener(serverSignalListener);
      }

      Gfsh.println();

      final boolean asyncStart = ServerState.isStartingNotRespondingOrNull(serverState);

      if (asyncStart) { // async start
        Gfsh.print(String.format(CliStrings.ASYNC_PROCESS_LAUNCH_MESSAGE, SERVER_TERM_NAME));
        return ResultBuilder.createInfoResult("");
      } else {
        return ResultBuilder.createInfoResult(serverState.toString());
      }
    } catch (IllegalArgumentException e) {
      String message = e.getMessage();
      if (message != null && message.matches(
          LocalizedStrings.Launcher_Builder_UNKNOWN_HOST_ERROR_MESSAGE.toLocalizedString(".+"))) {
        message =
            CliStrings.format(CliStrings.LAUNCHERLIFECYCLECOMMANDS__MSG__FAILED_TO_START_0_REASON_1,
                SERVER_TERM_NAME, message);
      }
      return ResultBuilder.createUserErrorResult(message);
    } catch (IllegalStateException e) {
      return ResultBuilder.createUserErrorResult(e.getMessage());
    } catch (ClusterConfigurationNotAvailableException e) {
      return ResultBuilder.createShellClientErrorResult(e.getMessage());
    } catch (VirtualMachineError e) {
      SystemFailure.initiateFailure(e);
      throw e;
    } catch (Throwable t) {
      SystemFailure.checkFailure();
      return ResultBuilder.createShellClientErrorResult(String.format(
          StartServer.START_SERVER__GENERAL_ERROR_MESSAGE, toString(t, getGfsh().getDebug())));
    }
  }

  protected String[] createStartServerCommandLine(final ServerLauncher launcher,
      final String gemfirePropertiesPathname, final String gemfireSecurityPropertiesPathname,
      final Properties gemfireProperties, final String userClasspath,
      final Boolean includeSystemClasspath, final String[] jvmArgsOpts,
      final Boolean disableExitWhenOutOfMemory, final String initialHeap, final String maxHeap)
      throws MalformedObjectNameException {
    List<String> commandLine = new ArrayList<>();

    commandLine.add(getJavaPath());
    commandLine.add("-server");
    commandLine.add("-classpath");
    commandLine.add(getServerClasspath(Boolean.TRUE.equals(includeSystemClasspath), userClasspath));

    addCurrentLocators(commandLine, gemfireProperties);
    addGemFirePropertyFile(commandLine, gemfirePropertiesPathname);
    addGemFireSecurityPropertyFile(commandLine, gemfireSecurityPropertiesPathname);
    addGemFireSystemProperties(commandLine, gemfireProperties);
    addJvmArgumentsAndOptions(commandLine, jvmArgsOpts);

    // NOTE asserting not equal to true rather than equal to false handles the null case and ensures
    // the user
    // explicitly specified the command-line option in order to disable JVM memory checks.
    if (!Boolean.TRUE.equals(disableExitWhenOutOfMemory)) {
      addJvmOptionsForOutOfMemoryErrors(commandLine);
    }

    addInitialHeap(commandLine, initialHeap);
    addMaxHeap(commandLine, maxHeap);

    commandLine.add(
        "-D".concat(AbstractLauncher.SIGNAL_HANDLER_REGISTRATION_SYSTEM_PROPERTY.concat("=true")));
    commandLine.add("-Djava.awt.headless=true");
    commandLine.add(
        "-Dsun.rmi.dgc.server.gcInterval".concat("=").concat(Long.toString(Long.MAX_VALUE - 1)));

    commandLine.add(ServerLauncher.class.getName());
    commandLine.add(ServerLauncher.Command.START.getName());

    if (StringUtils.isNotBlank(launcher.getMemberName())) {
      commandLine.add(launcher.getMemberName());
    }

    if (launcher.isAssignBuckets()) {
      commandLine.add("--assign-buckets");
    }

    if (launcher.isDebugging() || isDebugging()) {
      commandLine.add("--debug");
    }

    if (launcher.isDisableDefaultServer()) {
      commandLine.add("--disable-default-server");
    }

    if (launcher.isForcing()) {
      commandLine.add("--force");
    }

    if (launcher.isRebalancing()) {
      commandLine.add("--rebalance");
    }

    if (launcher.isRedirectingOutput()) {
      commandLine.add("--redirect-output");
    }

    if (launcher.getServerBindAddress() != null) {
      commandLine
          .add("--server-bind-address=" + launcher.getServerBindAddress().getCanonicalHostName());
    }

    if (launcher.getServerPort() != null) {
      commandLine.add("--server-port=" + launcher.getServerPort());
    }

    if (launcher.isSpringXmlLocationSpecified()) {
      commandLine.add("--spring-xml-location=".concat(launcher.getSpringXmlLocation()));
    }

    if (launcher.getCriticalHeapPercentage() != null) {
      commandLine.add("--" + StartServer.START_SERVER__CRITICAL__HEAP__PERCENTAGE + "="
          + launcher.getCriticalHeapPercentage());
    }

    if (launcher.getEvictionHeapPercentage() != null) {
      commandLine.add("--" + StartServer.START_SERVER__EVICTION__HEAP__PERCENTAGE + "="
          + launcher.getEvictionHeapPercentage());
    }

    if (launcher.getCriticalOffHeapPercentage() != null) {
      commandLine.add("--" + StartServer.START_SERVER__CRITICAL_OFF_HEAP_PERCENTAGE + "="
          + launcher.getCriticalOffHeapPercentage());
    }

    if (launcher.getEvictionOffHeapPercentage() != null) {
      commandLine.add("--" + StartServer.START_SERVER__EVICTION_OFF_HEAP_PERCENTAGE + "="
          + launcher.getEvictionOffHeapPercentage());
    }

    if (launcher.getMaxConnections() != null) {
      commandLine.add(
          "--" + StartServer.START_SERVER__MAX__CONNECTIONS + "=" + launcher.getMaxConnections());
    }

    if (launcher.getMaxMessageCount() != null) {
      commandLine.add("--" + StartServer.START_SERVER__MAX__MESSAGE__COUNT + "="
          + launcher.getMaxMessageCount());
    }

    if (launcher.getMaxThreads() != null) {
      commandLine
          .add("--" + StartServer.START_SERVER__MAX__THREADS + "=" + launcher.getMaxThreads());
    }

    if (launcher.getMessageTimeToLive() != null) {
      commandLine.add("--" + StartServer.START_SERVER__MESSAGE__TIME__TO__LIVE + "="
          + launcher.getMessageTimeToLive());
    }

    if (launcher.getSocketBufferSize() != null) {
      commandLine.add("--" + StartServer.START_SERVER__SOCKET__BUFFER__SIZE + "="
          + launcher.getSocketBufferSize());
    }

    if (launcher.getHostNameForClients() != null) {
      commandLine.add("--" + StartServer.START_SERVER__HOSTNAME__FOR__CLIENTS + "="
          + launcher.getHostNameForClients());
    }

    return commandLine.toArray(new String[commandLine.size()]);
  }

  private String getCurrentLocators() throws MalformedObjectNameException {
    String delimitedLocators = "";
    try {
      if (isConnectedAndReady()) {
        final DistributedSystemMXBean dsMBeanProxy = getDistributedSystemMXBean();
        if (dsMBeanProxy != null) {
          final String[] locators = dsMBeanProxy.listLocators();
          if (locators != null && locators.length > 0) {
            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < locators.length; i++) {
              if (i > 0) {
                sb.append(",");
              }
              sb.append(locators[i]);
            }
            delimitedLocators = sb.toString();
          }
        }
      }
    } catch (IOException e) { // thrown by getDistributedSystemMXBean
      // leave delimitedLocators = ""
      getGfsh().logWarning("DistributedSystemMXBean is unavailable\n", e);
    }
    return delimitedLocators;
  }

  @Deprecated
  protected File readIntoTempFile(final String classpathResourceLocation) throws IOException {
    String resourceName = classpathResourceLocation
        .substring(classpathResourceLocation.lastIndexOf(File.separator) + 1);
    File resourceFile = new File(System.getProperty("java.io.tmpdir"), resourceName);

    if (!resourceFile.exists() && resourceFile.createNewFile()) {
      BufferedReader resourceReader = new BufferedReader(new InputStreamReader(
          ClassLoader.getSystemClassLoader().getResourceAsStream(classpathResourceLocation)));

      BufferedWriter resourceFileWriter = new BufferedWriter(new FileWriter(resourceFile, false));

      try {
        for (String line = resourceReader.readLine(); line != null; line =
            resourceReader.readLine()) {
          resourceFileWriter.write(line);
          resourceFileWriter.write(StringUtils.LINE_SEPARATOR);
        }

        resourceFileWriter.flush();
      } finally {
        IOUtils.close(resourceReader);
        IOUtils.close(resourceFileWriter);
      }
    }

    resourceFile.deleteOnExit();

    return resourceFile;
  }

  @CliAvailabilityIndicator({StartLocator.START_LOCATOR, CliStrings.STOP_LOCATOR,
      CliStrings.STATUS_LOCATOR, StartServer.START_SERVER, CliStrings.STOP_SERVER,
      CliStrings.STATUS_SERVER, CliStrings.START_MANAGER, CliStrings.START_PULSE,
      CliStrings.START_VSD, CliStrings.START_DATABROWSER})
  public boolean launcherCommandsAvailable() {
    return true;
  }

  protected static class LauncherSignalListener implements SignalListener {

    private volatile boolean signaled = false;

    public boolean isSignaled() {
      return signaled;
    }

    public void handle(final SignalEvent event) {
      // System.err.printf("Gfsh LauncherSignalListener Received Signal '%1$s' (%2$d)...%n",
      // event.getSignal().getName(), event.getSignal().getNumber());
      this.signaled = true;
    }
  }

  protected String resolveWorkingDir(String userSpecifiedDir, String memberName) {
    File workingDir =
        (userSpecifiedDir == null) ? new File(memberName) : new File(userSpecifiedDir);

    String workingDirPath = IOUtils.tryGetCanonicalPathElseGetAbsolutePath(workingDir);

    if (!workingDir.exists()) {
      if (!workingDir.mkdirs()) {
        throw new IllegalStateException(String.format(
            "Could not create directory %s. Please verify directory path or user permissions.",
            workingDirPath));
      }
    }

    return workingDirPath;
  }

  public static class StartLocator {
    public static final String START_LOCATOR = "start locator";
    public static final String START_LOCATOR__HELP = "Start a Locator.";
    public static final String START_LOCATOR__BIND_ADDRESS = "bind-address";
    public static final String START_LOCATOR__BIND_ADDRESS__HELP =
        "IP address on which the Locator will be bound.  By default, the Locator is bound to all local addresses.";
    public static final String START_LOCATOR__CLASSPATH = "classpath";
    public static final String START_LOCATOR__CLASSPATH__HELP =
        "Location of user application classes required by the Locator. The user classpath is prepended to the Locator's classpath.";
    public static final String START_LOCATOR__DIR = "dir";
    public static final String START_LOCATOR__DIR__HELP =
        "Directory in which the Locator will be started and ran. The default is ./<locator-member-name>";
    public static final String START_LOCATOR__FORCE = "force";
    public static final String START_LOCATOR__FORCE__HELP =
        "Whether to allow the PID file from a previous Locator run to be overwritten.";
    public static final String START_LOCATOR__GROUP = "group";
    public static final String START_LOCATOR__GROUP__HELP =
        "Group(s) the Locator will be a part of.";
    public static final String START_LOCATOR__HOSTNAME_FOR_CLIENTS = "hostname-for-clients";

    public static final String START_LOCATOR__JMX_MANAGER_HOSTNAME_FOR_CLIENTS__HELP =
        "Hostname given to clients that ask the locator for the location of a JMX Manager";
    public static final String START_LOCATOR__HOSTNAME_FOR_CLIENTS__HELP =
        "Hostname or IP address that will be sent to clients so they can connect to this Locator. The default is the bind-address of the Locator.";
    public static final String START_LOCATOR__INCLUDE_SYSTEM_CLASSPATH = "include-system-classpath";
    public static final String START_LOCATOR__INCLUDE_SYSTEM_CLASSPATH__HELP =
        "Includes the System CLASSPATH on the Locator's CLASSPATH. The System CLASSPATH is not included by default.";
    public static final String START_LOCATOR__LOCATORS = LOCATORS;
    public static final String START_LOCATOR__LOCATORS__HELP =
        "Sets the list of Locators used by this Locator to join the appropriate Geode cluster.";
    public static final String START_LOCATOR__LOG_LEVEL = LOG_LEVEL;
    public static final String START_LOCATOR__LOG_LEVEL__HELP =
        "Sets the level of output logged to the Locator log file.  " + CliStrings.LOG_LEVEL_VALUES;
    public static final String START_LOCATOR__MCAST_ADDRESS = MCAST_ADDRESS;
    public static final String START_LOCATOR__MCAST_ADDRESS__HELP =
        "The IP address or hostname used to bind the UPD socket for multi-cast networking so the Locator can communicate with other members in the Geode cluster using a common multicast address and port.  If mcast-port is zero, then mcast-address is ignored.";
    public static final String START_LOCATOR__MCAST_PORT = MCAST_PORT;
    public static final String START_LOCATOR__MCAST_PORT__HELP =
        "Sets the port used for multi-cast networking so the Locator can communicate with other members of the Geode cluster.  A zero value disables mcast.";
    public static final String START_LOCATOR__MEMBER_NAME = "name";
    public static final String START_LOCATOR__MEMBER_NAME__HELP =
        "The member name to give this Locator in the Geode cluster.";
    public static final String START_LOCATOR__PORT = "port";
    public static final String START_LOCATOR__PORT__HELP = "Port the Locator will listen on.";
    public static final String START_LOCATOR__PROPERTIES = "properties-file";
    public static final String START_LOCATOR__PROPERTIES__HELP =
        "The gemfire.properties file for configuring the Locator's distributed system. The file's path can be absolute or relative to the gfsh working directory (--dir=)."; // TODO:GEODE-1466:
    // update
    // golden
    // file
    // to
    // geode.properties
    public static final String START_LOCATOR__SECURITY_PROPERTIES = "security-properties-file";
    public static final String START_LOCATOR__SECURITY_PROPERTIES__HELP =
        "The gfsecurity.properties file for configuring the Locator's security configuration in the distributed system. The file's path can be absolute or relative to gfsh directory (--dir=).";
    public static final String START_LOCATOR__INITIALHEAP = "initial-heap";
    public static final String START_LOCATOR__INITIALHEAP__HELP =
        "Initial size of the heap in the same format as the JVM -Xms parameter.";
    public static final String START_LOCATOR__J = "J";
    public static final String START_LOCATOR__J__HELP =
        "Argument passed to the JVM on which the Locator will run. For example, --J=-Dfoo.bar=true will set the property \"foo.bar\" to \"true\".";
    public static final String START_LOCATOR__MAXHEAP = "max-heap";
    public static final String START_LOCATOR__MAXHEAP__HELP =
        "Maximum size of the heap in the same format as the JVM -Xmx parameter.";
    public static final String START_LOCATOR__GENERAL_ERROR_MESSAGE =
        "An error occurred while attempting to start a Locator in %1$s on %2$s: %3$s";
    public static final String START_LOCATOR__PROCESS_TERMINATED_ABNORMALLY_ERROR_MESSAGE =
        "The Locator process terminated unexpectedly with exit status %1$d. Please refer to the log file in %2$s for full details.%n%n%3$s";
    public static final String START_LOCATOR__RUN_MESSAGE = "Starting a Geode Locator in %1$s...";
    public static final String START_LOCATOR__CONNECT = "connect";
    public static final String START_LOCATOR__CONNECT__HELP =
        "When connect is set to false , Gfsh does not automatically connect to the locator which is started using this command.";
    public static final String START_LOCATOR__USE__0__TO__CONNECT =
        "Please use \"{0}\" to connect Gfsh to the locator.";
    public static final String START_LOCATOR__ENABLE__SHARED__CONFIGURATION =
        ENABLE_CLUSTER_CONFIGURATION;
    public static final String START_LOCATOR__ENABLE__SHARED__CONFIGURATION__HELP =
        "When " + START_LOCATOR__ENABLE__SHARED__CONFIGURATION
            + " is set to true, locator hosts and serves cluster configuration.";
    public static final String START_LOCATOR__LOAD__SHARED_CONFIGURATION__FROM__FILESYSTEM =
        "load-cluster-configuration-from-dir";
    public static final String START_LOCATOR__LOAD__SHARED_CONFIGURATION__FROM__FILESYSTEM__HELP =
        "When \" " + START_LOCATOR__LOAD__SHARED_CONFIGURATION__FROM__FILESYSTEM
            + " \" is set to true, the locator loads the cluster configuration from the \""
            + ClusterConfigurationService.CLUSTER_CONFIG_ARTIFACTS_DIR_NAME + "\" directory.";
    public static final String START_LOCATOR__CLUSTER__CONFIG__DIR = "cluster-config-dir";
    public static final String START_LOCATOR__CLUSTER__CONFIG__DIR__HELP =
        "Directory used by the cluster configuration service to store the cluster configuration on the filesystem";
    public static final String START_LOCATOR__HTTP_SERVICE_PORT = "http-service-port";
    public static final String START_LOCATOR__HTTP_SERVICE_PORT__HELP =
        "Port on which HTTP Service will listen on";
    public static final String START_LOCATOR__HTTP_SERVICE_BIND_ADDRESS =
        "http-service-bind-address";
    public static final String START_LOCATOR__HTTP_SERVICE_BIND_ADDRESS__HELP =
        "The IP address on which the HTTP Service will be bound.  By default, the Server is bound to all local addresses.";

  }

  public static class StartServer {
    /* 'start server' command */
    public static final String START_SERVER = "start server";
    public static final String START_SERVER__HELP = "Start a Geode Cache Server.";
    public static final String START_SERVER__ASSIGN_BUCKETS = "assign-buckets";
    public static final String START_SERVER__ASSIGN_BUCKETS__HELP =
        "Whether to assign buckets to the partitioned regions of the cache on server start.";
    public static final String START_SERVER__BIND_ADDRESS = "bind-address";
    public static final String START_SERVER__BIND_ADDRESS__HELP =
        "The IP address on which the Server will be bound.  By default, the Server is bound to all local addresses.";
    public static final String START_SERVER__CACHE_XML_FILE = CACHE_XML_FILE;
    public static final String START_SERVER__CACHE_XML_FILE__HELP =
        "Specifies the name of the XML file or resource to initialize the cache with when it is created.";
    public static final String START_SERVER__CLASSPATH = "classpath";
    public static final String START_SERVER__CLASSPATH__HELP =
        "Location of user application classes required by the Server. The user classpath is prepended to the Server's classpath.";
    public static final String START_SERVER__DIR = "dir";
    public static final String START_SERVER__DIR__HELP =
        "Directory in which the Cache Server will be started and ran. The default is ./<server-member-name>";
    public static final String START_SERVER__DISABLE_DEFAULT_SERVER = "disable-default-server";
    public static final String START_SERVER__DISABLE_DEFAULT_SERVER__HELP =
        "Whether the Cache Server will be started by default.";
    public static final String START_SERVER__DISABLE_EXIT_WHEN_OUT_OF_MEMORY =
        "disable-exit-when-out-of-memory";
    public static final String START_SERVER__DISABLE_EXIT_WHEN_OUT_OF_MEMORY_HELP =
        "Prevents the JVM from exiting when an OutOfMemoryError occurs.";
    public static final String START_SERVER__ENABLE_TIME_STATISTICS = ENABLE_TIME_STATISTICS;
    public static final String START_SERVER__ENABLE_TIME_STATISTICS__HELP =
        "Causes additional time-based statistics to be gathered for Geode operations.";
    public static final String START_SERVER__FORCE = "force";
    public static final String START_SERVER__FORCE__HELP =
        "Whether to allow the PID file from a previous Cache Server run to be overwritten.";
    public static final String START_SERVER__GROUP = "group";
    public static final String START_SERVER__GROUP__HELP =
        "Group(s) the Cache Server will be a part of.";
    public static final String START_SERVER__INCLUDE_SYSTEM_CLASSPATH = "include-system-classpath";
    public static final String START_SERVER__INCLUDE_SYSTEM_CLASSPATH__HELP =
        "Includes the System CLASSPATH on the Server's CLASSPATH. The System CLASSPATH is not included by default.";
    public static final String START_SERVER__INITIAL_HEAP = "initial-heap";
    public static final String START_SERVER__INITIAL_HEAP__HELP =
        "Initial size of the heap in the same format as the JVM -Xms parameter.";
    public static final String START_SERVER__J = "J";
    public static final String START_SERVER__J__HELP =
        "Argument passed to the JVM on which the server will run. For example, --J=-Dfoo.bar=true will set the system property \"foo.bar\" to \"true\".";
    public static final String START_SERVER__JMX_MANAGER_HOSTNAME_FOR_CLIENTS__HELP =
        "Hostname given to clients that ask the server for the location of a JMX Manager";
    public static final String START_SERVER__LOCATORS = LOCATORS;
    public static final String START_SERVER__LOCATORS__HELP =
        "Sets the list of Locators used by the Cache Server to join the appropriate Geode cluster.";
    public static final String START_SERVER__LOCK_MEMORY = ConfigurationProperties.LOCK_MEMORY;
    public static final String START_SERVER__LOCK_MEMORY__HELP =
        "Causes Geode to lock heap and off-heap memory pages into RAM. This prevents the operating system from swapping the pages out to disk, which can cause severe performance degradation. When you use this option, also configure the operating system limits for locked memory.";
    public static final String START_SERVER__LOCATOR_WAIT_TIME = "locator-wait-time";
    public static final String START_SERVER__LOCATOR_WAIT_TIME_HELP =
        "Sets the number of seconds the server will wait for a locator to become available during startup before giving up.";
    public static final String START_SERVER__LOG_LEVEL = LOG_LEVEL;
    public static final String START_SERVER__LOG_LEVEL__HELP =
        "Sets the level of output logged to the Cache Server log file.  "
            + CliStrings.LOG_LEVEL_VALUES;
    public static final String START_SERVER__MAXHEAP = "max-heap";
    public static final String START_SERVER__MAXHEAP__HELP =
        "Maximum size of the heap in the same format as the JVM -Xmx parameter.";
    public static final String START_SERVER__MCAST_ADDRESS = MCAST_ADDRESS;
    public static final String START_SERVER__MCAST_ADDRESS__HELP =
        "The IP address or hostname used to bind the UPD socket for multi-cast networking so the Cache Server can communicate with other members in the Geode cluster.  If mcast-port is zero, then mcast-address is ignored.";
    public static final String START_SERVER__MCAST_PORT = MCAST_PORT;
    public static final String START_SERVER__MCAST_PORT__HELP =
        "Sets the port used for multi-cast networking so the Cache Server can communicate with other members of the Geode cluster.  A zero value disables mcast.";
    public static final String START_SERVER__NAME = "name";
    public static final String START_SERVER__NAME__HELP =
        "The member name to give this Cache Server in the Geode cluster.";
    public static final String START_SERVER__MEMCACHED_PORT = MEMCACHED_PORT;
    public static final String START_SERVER__MEMCACHED_PORT__HELP =
        "Sets the port that the Geode memcached service listens on for memcached clients.";
    public static final String START_SERVER__MEMCACHED_PROTOCOL = MEMCACHED_PROTOCOL;
    public static final String START_SERVER__MEMCACHED_PROTOCOL__HELP =
        "Sets the protocol that the Geode memcached service uses (ASCII or BINARY).";
    public static final String START_SERVER__MEMCACHED_BIND_ADDRESS = MEMCACHED_BIND_ADDRESS;
    public static final String START_SERVER__MEMCACHED_BIND_ADDRESS__HELP =
        "Sets the IP address the Geode memcached service listens on for memcached clients. The default is to bind to the first non-loopback address for this machine.";
    public static final String START_SERVER__OFF_HEAP_MEMORY_SIZE =
        ConfigurationProperties.OFF_HEAP_MEMORY_SIZE;
    public static final String START_SERVER__OFF_HEAP_MEMORY_SIZE__HELP =
        "The total size of off-heap memory specified as off-heap-memory-size=<n>[g|m]. <n> is the size. [g|m] indicates whether the size should be interpreted as gigabytes or megabytes. A non-zero size causes that much memory to be allocated from the operating system and reserved for off-heap use.";
    public static final String START_SERVER__PROPERTIES = "properties-file";
    public static final String START_SERVER__PROPERTIES__HELP =
        "The gemfire.properties file for configuring the Cache Server's distributed system. The file's path can be absolute or relative to the gfsh working directory."; // TODO:GEODE-1466:
    // update
    // golden
    // file
    // to
    // geode.properties
    public static final String START_SERVER__REDIS_PORT = ConfigurationProperties.REDIS_PORT;
    public static final String START_SERVER__REDIS_PORT__HELP =
        "Sets the port that the Geode Redis service listens on for Redis clients.";
    public static final String START_SERVER__REDIS_BIND_ADDRESS =
        ConfigurationProperties.REDIS_BIND_ADDRESS;
    public static final String START_SERVER__REDIS_BIND_ADDRESS__HELP =
        "Sets the IP address the Geode Redis service listens on for Redis clients. The default is to bind to the first non-loopback address for this machine.";
    public static final String START_SERVER__REDIS_PASSWORD =
        ConfigurationProperties.REDIS_PASSWORD;
    public static final String START_SERVER__REDIS_PASSWORD__HELP =
        "Sets the authentication password for GeodeRedisServer"; // TODO:GEODE-1566: update golden
    // file to GeodeRedisServer
    public static final String START_SERVER__SECURITY_PROPERTIES = "security-properties-file";
    public static final String START_SERVER__SECURITY_PROPERTIES__HELP =
        "The gfsecurity.properties file for configuring the Server's security configuration in the distributed system. The file's path can be absolute or relative to gfsh directory.";
    public static final String START_SERVER__REBALANCE = "rebalance";
    public static final String START_SERVER__REBALANCE__HELP =
        "Whether to initiate rebalancing across the Geode cluster.";
    public static final String START_SERVER__SERVER_BIND_ADDRESS = SERVER_BIND_ADDRESS;
    public static final String START_SERVER__SERVER_BIND_ADDRESS__HELP =
        "The IP address that this distributed system's server sockets in a client-server topology will be bound. If set to an empty string then all of the local machine's addresses will be listened on.";
    public static final String START_SERVER__SERVER_PORT = "server-port";
    public static final String START_SERVER__SERVER_PORT__HELP =
        "The port that the distributed system's server sockets in a client-server topology will listen on.  The default server-port is "
            + CacheServer.DEFAULT_PORT + ".";
    public static final String START_SERVER__SPRING_XML_LOCATION = "spring-xml-location";
    public static final String START_SERVER__SPRING_XML_LOCATION_HELP =
        "Specifies the location of a Spring XML configuration file(s) for bootstrapping and configuring a Geode Server.";
    public static final String START_SERVER__STATISTIC_ARCHIVE_FILE = STATISTIC_ARCHIVE_FILE;
    public static final String START_SERVER__STATISTIC_ARCHIVE_FILE__HELP =
        "The file that statistic samples are written to.  An empty string (default) disables statistic archival.";
    // public static final String START_SERVER__START_LOCATOR = "start-locator";
    // public static final String START_SERVER__START_LOCATOR__HELP =
    // "To start embedded Locator with given endpoints in the format: host[port]. If no endpoints
    // are
    // given defaults (localhost[10334]) are assumed.";
    public static final String START_SERVER__USE_CLUSTER_CONFIGURATION = USE_CLUSTER_CONFIGURATION;
    public static final String START_SERVER__USE_CLUSTER_CONFIGURATION__HELP =
        "When set to true, the server requests the configuration from locator's cluster configuration service.";
    public static final String START_SERVER__GENERAL_ERROR_MESSAGE =
        "An error occurred while attempting to start a Geode Cache Server: %1$s";
    public static final String START_SERVER__PROCESS_TERMINATED_ABNORMALLY_ERROR_MESSAGE =
        "The Cache Server process terminated unexpectedly with exit status %1$d. Please refer to the log file in %2$s for full details.%n%n%3$s";
    public static final String START_SERVER__RUN_MESSAGE = "Starting a Geode Server in %1$s...";


    public static final String START_SERVER__CRITICAL__HEAP__PERCENTAGE =
        "critical-heap-percentage";
    public static final String START_SERVER__CRITICAL__HEAP__HELP =
        "Set the percentage of heap at or above which the cache is considered in danger of becoming inoperable due to garbage collection pauses or out of memory exceptions";

    public static final String START_SERVER__EVICTION__HEAP__PERCENTAGE =
        "eviction-heap-percentage";
    public static final String START_SERVER__EVICTION__HEAP__PERCENTAGE__HELP =
        "Set the percentage of heap at or above which the eviction should begin on Regions configured for HeapLRU eviction. Changing this value may cause eviction to begin immediately."
            + "Only one change to this attribute or critical heap percentage will be allowed at any given time and its effect will be fully realized before the next change is allowed. This feature requires additional VM flags to perform properly. ";

    public static final String START_SERVER__CRITICAL_OFF_HEAP_PERCENTAGE =
        "critical-off-heap-percentage";
    public static final String START_SERVER__CRITICAL_OFF_HEAP__HELP =
        "Set the percentage of off-heap memory at or above which the cache is considered in danger of becoming inoperable due to out of memory exceptions";

    public static final String START_SERVER__EVICTION_OFF_HEAP_PERCENTAGE =
        "eviction-off-heap-percentage";
    public static final String START_SERVER__EVICTION_OFF_HEAP_PERCENTAGE__HELP =
        "Set the percentage of off-heap memory at or above which the eviction should begin on Regions configured for off-heap and HeapLRU eviction. Changing this value may cause eviction to begin immediately."
            + " Only one change to this attribute or critical off-heap percentage will be allowed at any given time and its effect will be fully realized before the next change is allowed.";
    public static final String START_SERVER__HOSTNAME__FOR__CLIENTS = "hostname-for-clients";
    public static final String START_SERVER__HOSTNAME__FOR__CLIENTS__HELP =
        "Sets the ip address or host name that this cache server is to listen on for client connections."
            + "Setting a specific hostname-for-clients will cause server locators to use this value when telling clients how to connect to this cache server. This is useful in the case where the cache server may refer to itself with one hostname, but the clients need to use a different hostname to find the cache server."
            + "The value \"\" causes the bind-address to be given to clients."
            + "A null value will be treated the same as the default \"\".";

    public static final String START_SERVER__LOAD__POLL__INTERVAL = "load-poll-interval";
    public static final String START_SERVER__LOAD__POLL__INTERVAL__HELP =
        "Set the frequency in milliseconds to poll the load probe on this cache server";


    public static final String START_SERVER__MAX__CONNECTIONS = "max-connections";
    public static final String START_SERVER__MAX__CONNECTIONS__HELP =
        "Sets the maxium number of client connections allowed. When the maximum is reached the cache server will stop accepting connections";

    public static final String START_SERVER__MAX__THREADS = "max-threads";
    public static final String START_SERVER__MAX__THREADS__HELP =
        "Sets the maxium number of threads allowed in this cache server to service client requests. The default of 0 causes the cache server to dedicate a thread for every client connection";

    public static final String START_SERVER__MAX__MESSAGE__COUNT = "max-message-count";
    public static final String START_SERVER__MAX__MESSAGE__COUNT__HELP =
        "Sets maximum number of messages that can be enqueued in a client-queue.";

    public static final String START_SERVER__MESSAGE__TIME__TO__LIVE = "message-time-to-live";
    public static final String START_SERVER__MESSAGE__TIME__TO__LIVE__HELP =
        "Sets the time (in seconds ) after which a message in the client queue will expire";

    public static final String START_SERVER__SOCKET__BUFFER__SIZE = SOCKET_BUFFER_SIZE;
    public static final String START_SERVER__SOCKET__BUFFER__SIZE__HELP =
        "Sets the buffer size in bytes of the socket connection for this CacheServer. The default is 32768 bytes.";

    public static final String START_SERVER__TCP__NO__DELAY = "tcp-no-delay";
    public static final String START_SERVER__TCP__NO__DELAY__HELP =
        "Configures the tcpNoDelay setting of sockets used to send messages to clients. TcpNoDelay is enabled by default";

    public static final String START_SERVER__REST_API = "start-rest-api";
    public static final String START_SERVER__REST_API__HELP =
        "When set to true, will start the REST API service.";
    public static final String START_SERVER__HTTP_SERVICE_PORT = "http-service-port";
    public static final String START_SERVER__HTTP_SERVICE_PORT__HELP =
        "Port on which HTTP Service will listen on";
    public static final String START_SERVER__HTTP_SERVICE_BIND_ADDRESS =
        "http-service-bind-address";
    public static final String START_SERVER__HTTP_SERVICE_BIND_ADDRESS__HELP =
        "The IP address on which the HTTP Service will be bound.  By default, the Server is bound to all local addresses.";
    public static final String START_SERVER__USERNAME = "user";
    public static final String START_SERVER__USERNAME__HELP =
        "User name to securely connect to the cluster. If the --password parameter is not specified then it will be prompted for.";
    public static final String START_SERVER__PASSWORD = "password";
    public static final String START_SERVER__PASSWORD__HELP =
        "Password to securely connect to the cluster.";
    public static final String START_SERVER__MSG__PASSWORD_MUST_BE_SPECIFIED =
        "password must be specified.";
  }
}
