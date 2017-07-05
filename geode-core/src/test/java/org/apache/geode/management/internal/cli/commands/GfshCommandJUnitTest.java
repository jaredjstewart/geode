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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Set;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.concurrent.Synchroniser;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import org.apache.geode.cache.execute.Function;
import org.apache.geode.cache.execute.FunctionService;
import org.apache.geode.distributed.DistributedMember;
import org.apache.geode.distributed.DistributedSystem;
import org.apache.geode.internal.cache.InternalCache;
import org.apache.geode.internal.lang.StringUtils;
import org.apache.geode.internal.util.CollectionUtils;
import org.apache.geode.management.cli.CliMetaData;
import org.apache.geode.management.internal.cli.i18n.CliStrings;
import org.apache.geode.management.internal.cli.util.MemberNotFoundException;
import org.apache.geode.test.junit.categories.UnitTest;

/**
 * The GfshCommandJUnitTest class is a test suite of test cases testing the contract and
 * functionality of the GfshCommand class for implementing GemFire shell (Gfsh) commands.
 *
 * @see GfshCommand
 * @see org.jmock.Expectations
 * @see org.jmock.Mockery
 * @see org.jmock.lib.legacy.ClassImposteriser
 * @see org.junit.Assert
 * @see org.junit.Test
 * @since GemFire 7.0
 */
@Category(UnitTest.class)
public class GfshCommandJUnitTest {

  private Mockery mockContext;

  private static <T extends Function> T register(T function) {
    if (FunctionService.isRegistered(function.getId())) {
      function = (T) FunctionService.getFunction(function.getId());
    } else {
      FunctionService.registerFunction(function);
    }

    return function;
  }

  private static class DefaultGfshCommand implements GfshCommand {
  }

  private DefaultGfshCommand defaultGfshCommand;

  @Before
  public void setup() {
    mockContext = new Mockery();
    mockContext.setImposteriser(ClassImposteriser.INSTANCE);
    mockContext.setThreadingPolicy(new Synchroniser());

    defaultGfshCommand = new DefaultGfshCommand();
  }

  @After
  public void tearDown() {
    mockContext.assertIsSatisfied();
    mockContext = null;
  }

  private GfshCommand createAbstractCommandsSupport(final InternalCache cache) {
    return new TestCommands(cache);
  }

  private DistributedMember createMockMember(final String memberId, final String memberName) {
    final DistributedMember mockMember =
        mockContext.mock(DistributedMember.class, "DistributedMember " + memberId);

    mockContext.checking(new Expectations() {
      {
        allowing(mockMember).getName();
        will(returnValue(memberName));
        allowing(mockMember).getId();
        will(returnValue(memberId));
      }
    });

    return mockMember;
  }

  @Test
  public void testConvertDefaultValue() {
    assertNull(defaultGfshCommand.convertDefaultValue(null, StringUtils.EMPTY));
    assertEquals(StringUtils.EMPTY,
        defaultGfshCommand.convertDefaultValue(StringUtils.EMPTY, "test"));
    assertEquals(StringUtils.SPACE,
        defaultGfshCommand.convertDefaultValue(StringUtils.SPACE, "testing"));
    assertEquals("tested",
        defaultGfshCommand.convertDefaultValue(CliMetaData.ANNOTATION_DEFAULT_VALUE, "tested"));
  }

  @Test
  public void testGetMemberWithMatchingMemberId() {
    final InternalCache mockCache = mockContext.mock(InternalCache.class, "InternalCache");

    final DistributedSystem mockDistributedSystem =
        mockContext.mock(DistributedSystem.class, "DistributedSystem");

    final DistributedMember mockMemberSelf = createMockMember("S", "Self");
    final DistributedMember mockMemberOne = createMockMember("1", "One");
    final DistributedMember mockMemberTwo = createMockMember("2", "Two");

    mockContext.checking(new Expectations() {
      {
        oneOf(mockCache).getMembers();
        will(returnValue(CollectionUtils.asSet(mockMemberOne, mockMemberTwo)));
        oneOf(mockCache).getDistributedSystem();
        will(returnValue(mockDistributedSystem));
        oneOf(mockDistributedSystem).getDistributedMember();
        will(returnValue(mockMemberSelf));
      }
    });

    final GfshCommand commands = createAbstractCommandsSupport(mockCache);

    assertSame(mockMemberTwo, commands.getMember(mockCache, "2"));
  }

  @Test
  public void testGetMemberWithMatchingMemberName() {
    final InternalCache mockCache = mockContext.mock(InternalCache.class, "InternalCache");

    final DistributedSystem mockDistributedSystem =
        mockContext.mock(DistributedSystem.class, "DistributedSystem");

    final DistributedMember mockMemberSelf = createMockMember("S", "Self");
    final DistributedMember mockMemberOne = createMockMember("1", "One");
    final DistributedMember mockMemberTwo = createMockMember("2", "Two");

    mockContext.checking(new Expectations() {
      {
        oneOf(mockCache).getMembers();
        will(returnValue(CollectionUtils.asSet(mockMemberOne, mockMemberTwo)));
        oneOf(mockCache).getDistributedSystem();
        will(returnValue(mockDistributedSystem));
        oneOf(mockDistributedSystem).getDistributedMember();
        will(returnValue(mockMemberSelf));
      }
    });

    final GfshCommand commands = createAbstractCommandsSupport(mockCache);

    assertSame(mockMemberOne, commands.getMember(mockCache, "One"));
  }

  @Test
  public void testGetMemberWithMatchingMemberNameCaseInsensitive() {
    final InternalCache mockCache = mockContext.mock(InternalCache.class, "InternalCache");

    final DistributedSystem mockDistributedSystem =
        mockContext.mock(DistributedSystem.class, "DistributedSystem");

    final DistributedMember mockMemberSelf = createMockMember("S", "Self");
    final DistributedMember mockMemberOne = createMockMember("1", "One");
    final DistributedMember mockMemberTwo = createMockMember("2", "Two");

    mockContext.checking(new Expectations() {
      {
        oneOf(mockCache).getMembers();
        will(returnValue(CollectionUtils.asSet(mockMemberOne, mockMemberTwo)));
        oneOf(mockCache).getDistributedSystem();
        will(returnValue(mockDistributedSystem));
        oneOf(mockDistributedSystem).getDistributedMember();
        will(returnValue(mockMemberSelf));
      }
    });

    final GfshCommand commands = createAbstractCommandsSupport(mockCache);

    assertSame(mockMemberSelf, commands.getMember(mockCache, "self"));
  }

  @Test(expected = MemberNotFoundException.class)
  public void testGetMemberThrowsMemberNotFoundException() {
    final InternalCache mockCache = mockContext.mock(InternalCache.class, "InternalCache");

    final DistributedSystem mockDistributedSystem =
        mockContext.mock(DistributedSystem.class, "DistributedSystem");

    final DistributedMember mockMemberSelf = createMockMember("S", "Self");
    final DistributedMember mockMemberOne = createMockMember("1", "One");
    final DistributedMember mockMemberTwo = createMockMember("2", "Two");

    mockContext.checking(new Expectations() {
      {
        oneOf(mockCache).getMembers();
        will(returnValue(CollectionUtils.asSet(mockMemberOne, mockMemberTwo)));
        oneOf(mockCache).getDistributedSystem();
        will(returnValue(mockDistributedSystem));
        oneOf(mockDistributedSystem).getDistributedMember();
        will(returnValue(mockMemberSelf));
      }
    });

    final GfshCommand commands = createAbstractCommandsSupport(mockCache);

    try {
      commands.getMember(mockCache, "zero");
    } catch (MemberNotFoundException expected) {
      assertEquals(CliStrings.format(CliStrings.MEMBER_NOT_FOUND_ERROR_MESSAGE, "zero"),
          expected.getMessage());
      throw expected;
    }
  }

  @Test
  public void testGetMembers() {
    final InternalCache mockCache = mockContext.mock(InternalCache.class, "InternalCache");

    final DistributedSystem mockDistributedSystem =
        mockContext.mock(DistributedSystem.class, "DistributedSystem");

    final DistributedMember mockMemberSelf = createMockMember("S", "Self");
    final DistributedMember mockMemberOne = createMockMember("1", "One");
    final DistributedMember mockMemberTwo = createMockMember("2", "Two");

    mockContext.checking(new Expectations() {
      {
        oneOf(mockCache).getMembers();
        will(returnValue(CollectionUtils.asSet(mockMemberOne, mockMemberTwo)));
        oneOf(mockCache).getDistributedSystem();
        will(returnValue(mockDistributedSystem));
        oneOf(mockDistributedSystem).getDistributedMember();
        will(returnValue(mockMemberSelf));
      }
    });

    final GfshCommand commands = createAbstractCommandsSupport(mockCache);

    final Set<DistributedMember> expectedMembers =
        CollectionUtils.asSet(mockMemberOne, mockMemberTwo, mockMemberSelf);
    final Set<DistributedMember> actualMembers = commands.getMembers(mockCache);

    assertNotNull(actualMembers);
    assertEquals(expectedMembers.size(), actualMembers.size());
    assertTrue(actualMembers.containsAll(expectedMembers));
  }

  @Test
  public void testGetMembersContainsOnlySelf() {
    final InternalCache mockCache = mockContext.mock(InternalCache.class, "InternalCache");

    final DistributedSystem mockDistributedSystem =
        mockContext.mock(DistributedSystem.class, "DistributedSystem");
    final DistributedMember mockMemberSelf = createMockMember("S", "Self");

    mockContext.checking(new Expectations() {
      {
        oneOf(mockCache).getMembers();
        will(returnValue(Collections.emptySet()));
        oneOf(mockCache).getDistributedSystem();
        will(returnValue(mockDistributedSystem));
        oneOf(mockDistributedSystem).getDistributedMember();
        will(returnValue(mockMemberSelf));
      }
    });

    final GfshCommand commands = createAbstractCommandsSupport(mockCache);

    final Set<DistributedMember> expectedMembers = CollectionUtils.asSet(mockMemberSelf);
    final Set<DistributedMember> actualMembers = commands.getMembers(mockCache);

    assertNotNull(actualMembers);
    assertEquals(expectedMembers.size(), actualMembers.size());
    assertTrue(actualMembers.containsAll(expectedMembers));
  }

  @Test
  public void testRegister() {
    try {
      final Function mockFunction = mockContext.mock(Function.class, "Function");

      mockContext.checking(new Expectations() {
        {
          exactly(3).of(mockFunction).getId();
          will(returnValue("testRegister"));
          oneOf(mockFunction).isHA();
          will(returnValue(true));
          oneOf(mockFunction).hasResult();
          will(returnValue(true));
        }
      });

      final GfshCommand commands =
          createAbstractCommandsSupport(mockContext.mock(InternalCache.class));

      assertFalse(FunctionService.isRegistered("testRegister"));
      assertSame(mockFunction, register(mockFunction));
      assertTrue(FunctionService.isRegistered("testRegister"));
    } finally {
      FunctionService.unregisterFunction("testRegister");
    }
  }

  @Test
  public void testRegisteredAlready() {
    try {
      final Function registeredFunction = mockContext.mock(Function.class, "Registered Function");
      final Function unregisteredFunction =
          mockContext.mock(Function.class, "Unregistered Function");

      mockContext.checking(new Expectations() {
        {
          exactly(2).of(registeredFunction).getId();
          will(returnValue("testRegisteredAlready"));
          oneOf(registeredFunction).isHA();
          will(returnValue(false));
          exactly(2).of(unregisteredFunction).getId();
          will(returnValue("testRegisteredAlready"));
        }
      });

      final GfshCommand commands =
          createAbstractCommandsSupport(mockContext.mock(InternalCache.class));

      FunctionService.registerFunction(registeredFunction);

      assertTrue(FunctionService.isRegistered("testRegisteredAlready"));
      assertSame(registeredFunction, register(unregisteredFunction));
      assertTrue(FunctionService.isRegistered("testRegisteredAlready"));
    } finally {
      FunctionService.unregisterFunction("testRegisteredAlready");
    }
  }

  @Test
  public void testToStringOnBoolean() {
    assertEquals("false", defaultGfshCommand.toString(null, null, null));
    assertEquals("true", defaultGfshCommand.toString(true, null, null));
    assertEquals("true", defaultGfshCommand.toString(Boolean.TRUE, null, null));
    assertEquals("false", defaultGfshCommand.toString(false, null, null));
    assertEquals("false", defaultGfshCommand.toString(Boolean.FALSE, null, null));
    assertEquals("false", defaultGfshCommand.toString(true, "false", "true"));
    assertEquals("true", defaultGfshCommand.toString(false, "false", "true"));
    assertEquals("Yes", defaultGfshCommand.toString(true, "Yes", "No"));
    assertEquals("Yes", defaultGfshCommand.toString(false, "No", "Yes"));
    assertEquals("TRUE", defaultGfshCommand.toString(Boolean.TRUE, "TRUE", "FALSE"));
    assertEquals("FALSE", defaultGfshCommand.toString(Boolean.FALSE, "TRUE", "FALSE"));
  }

  @Test
  public void testToStringOnThrowable() {
    assertEquals("test", defaultGfshCommand.toString(new Throwable("test"), false));
  }

  @Test
  public void testToStringOnThrowablePrintingStackTrace() {
    final StringWriter writer = new StringWriter();
    final Throwable t = new Throwable("test");

    t.printStackTrace(new PrintWriter(writer));

    assertEquals(writer.toString(), defaultGfshCommand.toString(t, true));
  }

  private static class TestCommands implements GfshCommand {

    private final InternalCache cache;

    protected TestCommands(final InternalCache cache) {
      assert cache != null : "The InternalCache cannot be null!";
      this.cache = cache;
    }

    @Override
    public InternalCache getCache() {
      return this.cache;
    }
  }

}
