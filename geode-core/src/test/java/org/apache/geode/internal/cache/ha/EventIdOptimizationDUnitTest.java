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
 */
package org.apache.geode.internal.cache.ha;

import static org.junit.Assert.*;

import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import org.junit.Test;
import org.junit.experimental.categories.Category;

import org.apache.geode.cache.AttributesFactory;
import org.apache.geode.cache.AttributesMutator;
import org.apache.geode.cache.Cache;
import org.apache.geode.cache.CacheFactory;
import org.apache.geode.cache.EntryEvent;
import org.apache.geode.cache.MirrorType;
import org.apache.geode.cache.Operation;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.RegionAttributes;
import org.apache.geode.cache.RegionDestroyedException;
import org.apache.geode.cache.RegionEvent;
import org.apache.geode.cache.Scope;
import org.apache.geode.cache.client.PoolManager;
import org.apache.geode.cache.client.internal.Connection;
import org.apache.geode.cache.client.internal.PoolImpl;
import org.apache.geode.cache.client.internal.QueueStateImpl.SequenceIdAndExpirationObject;
import org.apache.geode.cache.client.internal.ServerRegionProxy;
import org.apache.geode.cache.server.CacheServer;
import org.apache.geode.cache.util.CacheListenerAdapter;
import org.apache.geode.cache30.ClientServerTestCase;
import org.apache.geode.distributed.DistributedSystem;
import org.apache.geode.internal.AvailablePort;
import org.apache.geode.internal.cache.EventID;
import org.apache.geode.internal.cache.EventIDHolder;
import org.apache.geode.test.dunit.Host;
import org.apache.geode.test.dunit.LogWriterUtils;
import org.apache.geode.test.dunit.NetworkUtils;
import org.apache.geode.test.dunit.VM;
import org.apache.geode.test.dunit.internal.JUnit4DistributedTestCase;
import org.apache.geode.test.junit.categories.DistributedTest;

import static org.apache.geode.distributed.ConfigurationProperties.LOCATORS;
import static org.apache.geode.distributed.ConfigurationProperties.MCAST_PORT;

/**
 * This test verifies that eventId, while being sent across the network ( client
 * to server, server to client and peer to peer) , goes as optimized byte-array.
 * For client to server messages, the membership id part of event-id is not need
 * to be sent with each event. Also, the threadId and sequenceId need not be
 * sent as long if their value is small. This test has two servers and two
 * clients , each connected to one server. The events with event-ids having
 * specific values for thread-id and sequence-id are generated by client-1 and
 * sent to server-1 and then to server-2 via p2p and then finally to client-2.
 * It is verified that client-2 recieves the same values for thread-id and
 * sequence-id.
 */
@Category(DistributedTest.class)
public class EventIdOptimizationDUnitTest extends JUnit4DistributedTestCase {

  /** Cache-server1 */
  VM server1 = null;

  /** Cache-server2 */
  VM server2 = null;

  /** Client1 , connected to Cache-server1 */
  VM client1 = null;

  /** Client2 , connected to Cache-server2 */
  VM client2 = null;

  /** The long id (threadId or sequenceId) having value equivalent to byte */
  private static final long ID_VALUE_BYTE = Byte.MAX_VALUE;

  /** The long id (threadId or sequenceId) having value equivalent to short */
  private static final long ID_VALUE_SHORT = Short.MAX_VALUE;

  /** The long id (threadId or sequenceId) having value equivalent to int */
  private static final long ID_VALUE_INT = Integer.MAX_VALUE;

  /** The long id (threadId or sequenceId) having value equivalent to long */
  private static final long ID_VALUE_LONG = Long.MAX_VALUE;

  /** Name of the test region */
  private static final String REGION_NAME = "EventIdOptimizationDUnitTest_region";

  /** The cache instance for test cases */
  protected static Cache cache = null;

  /**
   * Connection proxy object to get connection for performing events that will
   * have specific eventIds
   */
  private static PoolImpl pool = null;

  /** Boolean to indicate the client to proceed for validation */
  private static volatile boolean proceedForValidation = false;

  /** Boolean to propagate the failure in listener to the client */
  private static volatile boolean validationFailed = false;

  /** StringBuffer to hold the failure messages in client listener */
  static StringBuffer failureMsg = new StringBuffer();

  /** The last key for operations, to notify for proceeding to validation */
  private static final String LAST_KEY = "LAST_KEY";

  /**
   * The eventID for the last key, used to identify the last event so that
   * client can proceed for validation
   */
  private static final EventID eventIdForLastKey = new EventID(new byte[] { 1, 2 }, 3, 4);

  /**
   * An array of eventIds having possible combinations of threadId and
   * sequenceId values
   */
  private static final EventID[] eventIds = new EventID[] { new EventID(new byte[] { 1, 1 }, ID_VALUE_BYTE, ID_VALUE_BYTE), new EventID(new byte[] { 1, 1 }, ID_VALUE_BYTE, ID_VALUE_SHORT), new EventID(new byte[] { 1, 1 }, ID_VALUE_BYTE, ID_VALUE_INT), new EventID(new byte[] { 1, 1 }, ID_VALUE_BYTE, ID_VALUE_LONG),

      new EventID(new byte[] { 1, 1 }, ID_VALUE_SHORT, ID_VALUE_BYTE), new EventID(new byte[] { 1, 1 }, ID_VALUE_SHORT, ID_VALUE_SHORT), new EventID(new byte[] { 1, 1 }, ID_VALUE_SHORT, ID_VALUE_INT), new EventID(new byte[] { 1, 1 }, ID_VALUE_SHORT, ID_VALUE_LONG),

      new EventID(new byte[] { 1, 1 }, ID_VALUE_INT, ID_VALUE_BYTE), new EventID(new byte[] { 1, 1 }, ID_VALUE_INT, ID_VALUE_SHORT), new EventID(new byte[] { 1, 1 }, ID_VALUE_INT, ID_VALUE_INT), new EventID(new byte[] { 1, 1 }, ID_VALUE_INT, ID_VALUE_LONG),

      new EventID(new byte[] { 1, 1 }, ID_VALUE_LONG, ID_VALUE_BYTE), new EventID(new byte[] { 1, 1 }, ID_VALUE_LONG, ID_VALUE_SHORT), new EventID(new byte[] { 1, 1 }, ID_VALUE_LONG, ID_VALUE_INT), new EventID(new byte[] { 1, 1 }, ID_VALUE_LONG, ID_VALUE_LONG) };

  @Override
  public final void postSetUp() throws Exception {
    disconnectAllFromDS();

    final Host host = Host.getHost(0);
    server1 = host.getVM(0);
    server2 = host.getVM(1);
    client1 = host.getVM(2);
    client2 = host.getVM(3);

    int PORT1 = ((Integer) server1.invoke(() -> EventIdOptimizationDUnitTest.createServerCache())).intValue();
    int PORT2 = ((Integer) server2.invoke(() -> EventIdOptimizationDUnitTest.createServerCache())).intValue();

    client1.invoke(() -> EventIdOptimizationDUnitTest.createClientCache1(NetworkUtils.getServerHostName(host), new Integer(PORT1)));
    client2.invoke(() -> EventIdOptimizationDUnitTest.createClientCache2(NetworkUtils.getServerHostName(host), new Integer(PORT2)));
  }

  /**
   * Creates the cache
   * 
   * @param props -
   *          distributed system props
   * @throws Exception -
   *           thrown in any problem occurs in creating cache
   */
  private void createCache(Properties props) throws Exception {
    DistributedSystem ds = getSystem(props);
    cache = CacheFactory.create(ds);
    assertNotNull(cache);
  }

  /** Creates cache and starts the bridge-server */
  public static Integer createServerCache() throws Exception {
    new EventIdOptimizationDUnitTest().createCache(new Properties());
    AttributesFactory factory = new AttributesFactory();
    factory.setScope(Scope.DISTRIBUTED_ACK);
    factory.setMirrorType(MirrorType.KEYS_VALUES);
    RegionAttributes attrs = factory.create();
    cache.createRegion(REGION_NAME, attrs);

    // create multiple dummy regions to use them in destroyRegion case for
    // testing eventIDs
    for (int i = 0; i < eventIds.length; i++) {
      cache.createRegion(REGION_NAME + i, attrs);
    }
    CacheServer server = cache.addCacheServer();
    assertNotNull(server);
    int port = AvailablePort.getRandomAvailablePort(AvailablePort.SOCKET);
    server.setPort(port);
    server.setNotifyBySubscription(true);
    server.start();
    return new Integer(server.getPort());
  }

  /**
   * Creates the client cache1, connected to server1
   * 
   * @param port -
   *          bridgeserver port
   * @throws Exception -
   *           thrown if any problem occurs in setting up the client
   */
  public static void createClientCache1(String hostName, Integer port) throws Exception {
    Properties props = new Properties();
    props.setProperty(MCAST_PORT, "0");
    props.setProperty(LOCATORS, "");
    new EventIdOptimizationDUnitTest().createCache(props);

    AttributesFactory factory = new AttributesFactory();
    ClientServerTestCase.configureConnectionPool(factory, hostName, port.intValue(), -1, true, -1, 2, null);
    final CacheServer bs1 = cache.addCacheServer();
    bs1.setPort(port.intValue());

    pool = (PoolImpl) PoolManager.find("testPool");
  }

  /**
   * Creates the client cache2, connected to server3
   * 
   * @param port -
   *          bridgeserver port
   * @throws Exception -
   *           thrown if any problem occurs in setting up the client
   */
  public static void createClientCache2(String hostName, Integer port) throws Exception {
    Properties props = new Properties();
    props.setProperty(MCAST_PORT, "0");
    props.setProperty(LOCATORS, "");
    new EventIdOptimizationDUnitTest().createCache(props);
    AttributesFactory factory = new AttributesFactory();
    ClientServerTestCase.configureConnectionPool(factory, hostName, port.intValue(), -1, true, -1, 2, null);

    factory.setScope(Scope.DISTRIBUTED_ACK);

    factory.addCacheListener(new CacheListenerAdapter() {
      public void afterCreate(EntryEvent event) {
        String key = (String) event.getKey();
        validateEventsAtReceivingClientListener(key);
      }

      public void afterDestroy(EntryEvent event) {
        String key = (String) event.getKey();
        validateEventsAtReceivingClientListener(key);
      }

      public void afterRegionDestroy(RegionEvent event) {

        validateEventsAtReceivingClientListener(" <destroyRegion Event> ");
      }

      public void afterRegionClear(RegionEvent event) {

        validateEventsAtReceivingClientListener(" <clearRegion Event> ");
      }

    });
    RegionAttributes attrs = factory.create();
    Region region = cache.createRegion(REGION_NAME, attrs);
    region.registerInterest("ALL_KEYS");
    for (int i = 0; i < eventIds.length; i++) {
      region = cache.createRegion(REGION_NAME + i, attrs);
      region.registerInterest("ALL_KEYS");
    }

    pool = (PoolImpl) PoolManager.find("testPool");
  }

  /**
   * Generates events having specific values of threadId and sequenceId, via put
   * operation through connection object
   * 
   * @throws Exception -
   *           thrown if any problem occurs in put operation
   */
  public static void generateEventsByPutOperation() throws Exception {
    Connection connection = pool.acquireConnection();
    String regionName = Region.SEPARATOR + REGION_NAME;
    ServerRegionProxy srp = new ServerRegionProxy(regionName, pool);

    for (int i = 0; i < eventIds.length; i++) {
      srp.putOnForTestsOnly(connection, "KEY-" + i, "VAL-" + i, eventIds[i], null);
    }
    srp.putOnForTestsOnly(connection, LAST_KEY, "LAST_VAL", eventIdForLastKey, null);
  }

  /**
   * Generates events having specific values of threadId and sequenceId, via
   * destroyEntry operation through connection object
   * 
   * @throws Exception -
   *           thrown if any problem occurs in destroyEntry operation
   */
  public static void generateEventsByDestroyEntryOperation() throws Exception {
    Connection connection = pool.acquireConnection();
    String regionName = Region.SEPARATOR + REGION_NAME;
    ServerRegionProxy srp = new ServerRegionProxy(regionName, pool);

    for (int i = 0; i < eventIds.length; i++) {
      srp.destroyOnForTestsOnly(connection, "KEY-" + i, null, Operation.DESTROY, new EventIDHolder(eventIds[i]), null);
    }
    srp.destroyOnForTestsOnly(connection, LAST_KEY, null, Operation.DESTROY, new EventIDHolder(eventIdForLastKey), null);
  }

  /**
   * Generates events having specific values of threadId and sequenceId, via
   * destroyRegionOperation through connection object
   * 
   * @throws Exception -
   *           thrown if any problem occurs in destroyRegionOperation
   */
  public static void generateEventsByDestroyRegionOperation() throws Exception {
    Connection connection = pool.acquireConnection();
    String regionName = Region.SEPARATOR + REGION_NAME;

    for (int i = 0; i < 1; i++) {
      ServerRegionProxy srp = new ServerRegionProxy(regionName + i, pool);
      srp.destroyRegionOnForTestsOnly(connection, eventIds[i], null);
    }
    {
      ServerRegionProxy srp = new ServerRegionProxy(regionName, pool);
      srp.destroyRegionOnForTestsOnly(connection, eventIdForLastKey, null);
    }
  }

  /**
   * Generates events having specific values of threadId and sequenceId, via
   * clearRegionOperation through connection object
   * 
   * @throws Exception -
   *           thrown if any problem occurs in clearRegionOperation
   */
  public static void generateEventsByClearRegionOperation() throws Exception {
    Connection connection = pool.acquireConnection();
    String regionName = Region.SEPARATOR + REGION_NAME;
    ServerRegionProxy srp = new ServerRegionProxy(regionName, pool);

    for (int i = 0; i < eventIds.length; i++) {
      srp.clearOnForTestsOnly(connection, eventIds[i], null);
    }
    srp.clearOnForTestsOnly(connection, eventIdForLastKey, null);
  }

  /**
   * Generates events having specific values of threadId and sequenceId from
   * client1 via put operation and verifies that the values received on client2
   * match with those sent from client1.
   * 
   * @throws Exception -
   *           thrown if any exception occurs in test
   */
  @Test
  public void testEventIdOptimizationByPutOperation() throws Exception {
    client1.invoke(() -> EventIdOptimizationDUnitTest.generateEventsByPutOperation());
    client2.invoke(() -> EventIdOptimizationDUnitTest.verifyEventIdsOnClient2());

  }

  /**
   * Generates events having specific values of threadId and sequenceId from
   * client1 via destroyEntry operation and verifies that the values received on
   * client2 match with those sent from client1.
   * 
   * @throws Exception -
   *           thrown if any exception occurs in test
   */
  @Test
  public void testEventIdOptimizationByDestroyEntryOperation() throws Exception {
    client1.invoke(() -> EventIdOptimizationDUnitTest.generateEventsByDestroyEntryOperation());
    client2.invoke(() -> EventIdOptimizationDUnitTest.verifyEventIdsOnClient2());
  }

  /**
   * Generates events having specific values of threadId and sequenceId from
   * client1 via destroyRegion operation and verifies that the values received
   * on client2 match with those sent from client1.
   * 
   * @throws Exception -
   *           thrown if any exception occurs in test
   */
  @Test
  public void testEventIdOptimizationByDestroyRegionOperation() throws Exception {
    client1.invoke(() -> EventIdOptimizationDUnitTest.generateEventsByDestroyRegionOperation());
    client2.invoke(() -> EventIdOptimizationDUnitTest.verifyEventIdsOnClient2());
  }

  /**
   * Generates events having specific values of threadId and sequenceId from
   * client1 via clearRegion operation and verifies that the values received on
   * client2 match with those sent from client1.
   * 
   * @throws Exception -
   *           thrown if any exception occurs in test
   */
  @Test
  public void testEventIdOptimizationByClearRegionOperation() throws Exception {
    client1.invoke(() -> EventIdOptimizationDUnitTest.generateEventsByClearRegionOperation());
    client2.invoke(() -> EventIdOptimizationDUnitTest.verifyEventIdsOnClient2());
  }

  /**
   * Waits for the listener to receive all events and validates that no
   * exception occured in client
   */
  public static void verifyEventIdsOnClient2() {
    if (!proceedForValidation) {
      synchronized (EventIdOptimizationDUnitTest.class) {
        if (!proceedForValidation)
          try {
            LogWriterUtils.getLogWriter().info("Client2 going in wait before starting validation");
            EventIdOptimizationDUnitTest.class.wait();
          } catch (InterruptedException e) {
            fail("interrupted");
          }
      }
    }
    LogWriterUtils.getLogWriter().info("Starting validation on client2");
    if (validationFailed) {
      fail("\n The following eventIds recieved by client2 were not present in the eventId array sent by client1 \n" + failureMsg);
    }
    LogWriterUtils.getLogWriter().info("Validation complete on client2, goin to unregister listeners");

    Region region = cache.getRegion(Region.SEPARATOR + REGION_NAME);
    if (region != null && !region.isDestroyed()) {
      try {
        AttributesMutator mutator = region.getAttributesMutator();
        mutator.initCacheListeners(null);
      } catch (RegionDestroyedException ignore) {
      }
    }

    for (int i = 0; i < eventIds.length; i++) {
      region = cache.getRegion(Region.SEPARATOR + REGION_NAME + i);
      if (region != null && !region.isDestroyed()) {
        try {
          AttributesMutator mutator = region.getAttributesMutator();
          mutator.initCacheListeners(null);
        } catch (RegionDestroyedException ignore) {
        }
      }
    }

    LogWriterUtils.getLogWriter().info("Test completed, Unregistered the listeners");
  }

  /**
   * Closes the cache
   * 
   */
  public static void closeCache() {
    if (cache != null && !cache.isClosed()) {
      cache.close();
      cache.getDistributedSystem().disconnect();
    }
  }

  /**
   * Closes the caches on clients and servers
   */
  @Override
  public final void preTearDown() throws Exception {
    // close client
    client1.invoke(() -> EventIdOptimizationDUnitTest.closeCache());
    client2.invoke(() -> EventIdOptimizationDUnitTest.closeCache());
    // close server
    server1.invoke(() -> EventIdOptimizationDUnitTest.closeCache());
    server2.invoke(() -> EventIdOptimizationDUnitTest.closeCache());
  }

  /**
   * Function to assert that the ThreadIdtoSequence id Map is not Null and has
   * only one entry.
   * 
   * @return - eventID object from the ThreadIdToSequenceIdMap
   */
  public static Object assertThreadIdToSequenceIdMapHasEntryId() {
    Map map = pool.getThreadIdToSequenceIdMap();
    assertNotNull(map);
    // The map size can now be 1 or 2 because of the server thread putting
    // the marker in the queue. If it is 2, the first entry is the server
    // thread; the second is the client thread. If it is 1, the entry is the
    // client thread. The size changes because of the map.clear call below.

    assertTrue(map.size() != 0);

    // Set the entry to the last entry
    Map.Entry entry = null;
    for (Iterator threadIdToSequenceIdMapIterator = map.entrySet().iterator(); threadIdToSequenceIdMapIterator.hasNext();) {
      entry = (Map.Entry) threadIdToSequenceIdMapIterator.next();
    }

    ThreadIdentifier tid = (ThreadIdentifier) entry.getKey();
    SequenceIdAndExpirationObject seo = (SequenceIdAndExpirationObject) entry.getValue();
    long sequenceId = seo.getSequenceId();
    EventID evId = new EventID(tid.getMembershipID(), tid.getThreadID(), sequenceId);
    synchronized (map) {
      map.clear();
    }
    return evId;
  }

  /**
   * Validates that the eventId of the event received in callback is contained
   * in the eventId array originally used by client1 to generate the events and
   * notifies client2 to proceed for validation once the LAST_KEY is received
   * 
   * @param key -
   *          the key of the event for EntryEvent / token indicating type of
   *          region operation for RegionEvent
   */
  public static void validateEventsAtReceivingClientListener(String key) {
    EventID eventIdAtClient2 = (EventID) assertThreadIdToSequenceIdMapHasEntryId();
    if ((eventIdAtClient2.getThreadID() == eventIdForLastKey.getThreadID()) && (eventIdAtClient2.getSequenceID() == eventIdForLastKey.getSequenceID())) {
      synchronized (EventIdOptimizationDUnitTest.class) {
        LogWriterUtils.getLogWriter().info("Notifying client2 to proceed for validation");
        proceedForValidation = true;
        EventIdOptimizationDUnitTest.class.notify();
      }
    } else {
      boolean containsEventId = false;
      for (int i = 0; i < eventIds.length; i++) {
        if ((eventIdAtClient2.getThreadID() == eventIds[i].getThreadID()) && (eventIdAtClient2.getSequenceID() == eventIds[i].getSequenceID())) {
          containsEventId = true;
          break;
        }
      }
      if (!containsEventId) {
        validationFailed = true;
        failureMsg.append("key = ").append(key).append(" ; eventID = ").append(eventIdAtClient2).append(System.getProperty("line.separator"));
      }
    }
  }
}
