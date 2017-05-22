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

import org.apache.commons.lang.StringUtils;
import org.apache.geode.cache.CacheFactory;
import org.apache.geode.cache.execute.Execution;
import org.apache.geode.cache.execute.Function;
import org.apache.geode.cache.execute.FunctionService;
import org.apache.geode.distributed.DistributedMember;
import org.apache.geode.distributed.internal.ClusterConfigurationService;
import org.apache.geode.distributed.internal.InternalLocator;
import org.apache.geode.internal.cache.InternalCache;
import org.apache.geode.internal.security.SecurityService;
import org.apache.geode.management.DistributedSystemMXBean;
import org.apache.geode.management.MemberMXBean;
import org.apache.geode.management.cli.CliMetaData;
import org.apache.geode.management.cli.Result;
import org.apache.geode.management.internal.ManagementConstants;
import org.apache.geode.management.internal.cli.i18n.CliStrings;
import org.apache.geode.management.internal.cli.shell.Gfsh;
import org.apache.geode.management.internal.cli.util.MemberNotFoundException;
import org.springframework.shell.core.CommandMarker;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.Set;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.Query;
import javax.management.QueryExp;

/**
 * The AbstractCommandsSupport class is an abstract base class encapsulating common functionality
 * for implementing command classes with command for the GemFire shell (gfsh).
 * <p>
 * 
 * @see org.apache.geode.cache.Cache
 * @see org.apache.geode.cache.execute.FunctionService
 * @see org.apache.geode.distributed.DistributedMember
 * @see org.apache.geode.management.internal.cli.shell.Gfsh
 * @see org.springframework.shell.core.CommandMarker
 * @since GemFire 7.0
 */
@SuppressWarnings("unused")
public abstract class AbstractCommandsSupport implements CommandMarker {
  protected static SecurityService securityService = SecurityService.getSecurityService();

  protected static void assertArgument(final boolean valid, final String message,
      final Object... args) {
    if (!valid) {
      throw new IllegalArgumentException(String.format(message, args));
    }
  }

  public static void assertNotNull(final Object obj, final String message, final Object... args) {
    if (obj == null) {
      throw new NullPointerException(String.format(message, args));
    }
  }

  public static void assertState(final boolean valid, final String message, final Object... args) {
    if (!valid) {
      throw new IllegalStateException(String.format(message, args));
    }
  }

  protected static String convertDefaultValue(final String from, final String to) {
    return (CliMetaData.ANNOTATION_DEFAULT_VALUE.equals(from) ? to : from);
  }

  protected static String toString(final Boolean condition, final String trueValue,
      final String falseValue) {
    return (Boolean.TRUE.equals(condition) ? StringUtils.defaultIfBlank(trueValue, "true")
        : StringUtils.defaultIfBlank(falseValue, "false"));
  }

  public static String toString(final Throwable t, final boolean printStackTrace) {
    String message = t.getMessage();

    if (printStackTrace) {
      StringWriter writer = new StringWriter();
      t.printStackTrace(new PrintWriter(writer));
      message = writer.toString();
    }

    return message;
  }

  public static boolean isConnectedAndReady() {
    return (getGfsh() != null && getGfsh().isConnectedAndReady());
  }

  protected ClusterConfigurationService getSharedConfiguration() {
    InternalLocator locator = InternalLocator.getLocator();
    return (locator == null) ? null : locator.getSharedConfiguration();
  }

  protected void persistClusterConfiguration(Result result, Runnable runnable) {
    if (result == null) {
      throw new IllegalArgumentException("Result should not be null");
    }
    ClusterConfigurationService sc = getSharedConfiguration();
    if (sc == null) {
      result.setCommandPersisted(false);
    } else {
      runnable.run();
      result.setCommandPersisted(true);
    }
  }

  /**
   * Gets a proxy to the DistributedSystemMXBean from the GemFire Manager's MBeanServer, or null if
   * unable to find the DistributedSystemMXBean.
   * </p>
   *
   * @return a proxy to the DistributedSystemMXBean from the GemFire Manager's MBeanServer, or null
   *         if unable to find the DistributedSystemMXBean.
   */
  public static DistributedSystemMXBean getDistributedSystemMXBean()
      throws IOException, MalformedObjectNameException {
    assertState(isConnectedAndReady(),
        "Gfsh must be connected in order to get proxy to a GemFire DistributedSystemMXBean.");
    return getGfsh().getOperationInvoker().getDistributedSystemMXBean();
  }

  /**
   * Gets a proxy to the MemberMXBean for the GemFire member specified by member name or ID from the
   * GemFire Manager's MBeanServer.
   * </p>
   *
   * @param member a String indicating the GemFire member's name or ID.
   * @return a proxy to the MemberMXBean having the specified GemFire member's name or ID from the
   *         GemFire Manager's MBeanServer, or null if no GemFire member could be found with the
   *         specified member name or ID.
   * @see #getMemberMXBean(String, String)
   */
  public static MemberMXBean getMemberMXBean(final String member) throws IOException {
    return getMemberMXBean(null, member);
  }

  public static MemberMXBean getMemberMXBean(final String serviceName, final String member)
      throws IOException {
    assertState(isConnectedAndReady(),
        "Gfsh must be connected in order to get proxy to a GemFire Member MBean.");

    MemberMXBean memberBean = null;

    try {
      String objectNamePattern = ManagementConstants.OBJECTNAME__PREFIX;

      objectNamePattern += (org.apache.geode.internal.lang.StringUtils.isBlank(serviceName)
          ? org.apache.geode.internal.lang.StringUtils.EMPTY
          : "service=" + serviceName + org.apache.geode.internal.lang.StringUtils.COMMA_DELIMITER);
      objectNamePattern += "type=Member,*";

      // NOTE throws a MalformedObjectNameException, however, this should not happen since the
      // ObjectName is constructed
      // here in a conforming pattern
      final ObjectName objectName = ObjectName.getInstance(objectNamePattern);

      final QueryExp query = Query.or(Query.eq(Query.attr("Name"), Query.value(member)),
          Query.eq(Query.attr("Id"), Query.value(member)));

      final Set<ObjectName> memberObjectNames =
          getGfsh().getOperationInvoker().queryNames(objectName, query);

      if (!memberObjectNames.isEmpty()) {
        memberBean = getGfsh().getOperationInvoker()
            .getMBeanProxy(memberObjectNames.iterator().next(), MemberMXBean.class);
      }
    } catch (MalformedObjectNameException e) {
      getGfsh().logSevere(e.getMessage(), e);
    }

    return memberBean;
  }

  public static boolean isDebugging() {
    return (getGfsh() != null && getGfsh().getDebug());
  }

  protected boolean isLogging() {
    return (getGfsh() != null);
  }

  protected InternalCache getCache() {
    return (InternalCache) CacheFactory.getAnyInstance();
  }

  public static Gfsh getGfsh() {
    return Gfsh.getCurrentInstance();
  }

  @SuppressWarnings("deprecated")
  protected DistributedMember getMember(final InternalCache cache, final String memberName) {
    for (final DistributedMember member : getMembers(cache)) {
      if (memberName.equalsIgnoreCase(member.getName())
          || memberName.equalsIgnoreCase(member.getId())) {
        return member;
      }
    }

    throw new MemberNotFoundException(
        CliStrings.format(CliStrings.MEMBER_NOT_FOUND_ERROR_MESSAGE, memberName));
  }

  /**
   * Gets all members in the GemFire distributed system/cache.
   *
   * @param cache the GemFire cache.
   * @return all members in the GemFire distributed system/cache.
   * @see org.apache.geode.management.internal.cli.CliUtil#getAllMembers(org.apache.geode.internal.cache.InternalCache)
   * @deprecated use CliUtil.getAllMembers(org.apache.geode.cache.Cache) instead
   */
  @Deprecated
  protected Set<DistributedMember> getMembers(final InternalCache cache) {
    Set<DistributedMember> members = new HashSet<DistributedMember>(cache.getMembers());
    members.add(cache.getDistributedSystem().getDistributedMember());
    return members;
  }

  protected Execution getMembersFunctionExecutor(final Set<DistributedMember> members) {
    return FunctionService.onMembers(members);
  }

  protected void logInfo(final String message) {
    logInfo(message, null);
  }

  protected void logInfo(final Throwable cause) {
    logInfo(cause.getMessage(), cause);
  }

  protected void logInfo(final String message, final Throwable cause) {
    if (isLogging()) {
      getGfsh().logInfo(message, cause);
    }
  }

  protected void logWarning(final String message) {
    logWarning(message, null);
  }

  protected void logWarning(final Throwable cause) {
    logWarning(cause.getMessage(), cause);
  }

  protected void logWarning(final String message, final Throwable cause) {
    if (isLogging()) {
      getGfsh().logWarning(message, cause);
    }
  }

  protected void logSevere(final String message) {
    logSevere(message, null);
  }

  protected void logSevere(final Throwable cause) {
    logSevere(cause.getMessage(), cause);
  }

  protected void logSevere(final String message, final Throwable cause) {
    if (isLogging()) {
      getGfsh().logSevere(message, cause);
    }
  }

  @SuppressWarnings("unchecked")
  protected <T extends Function> T register(T function) {
    if (FunctionService.isRegistered(function.getId())) {
      function = (T) FunctionService.getFunction(function.getId());
    } else {
      FunctionService.registerFunction(function);
    }

    return function;
  }

}
