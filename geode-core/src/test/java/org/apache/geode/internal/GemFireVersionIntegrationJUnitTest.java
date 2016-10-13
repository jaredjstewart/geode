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
package org.apache.geode.internal;

import org.apache.geode.cache.Cache;
import org.apache.geode.cache.CacheFactory;
import org.apache.geode.distributed.internal.DistributionConfigImpl;
import org.apache.geode.test.junit.categories.IntegrationTest;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import static org.apache.geode.distributed.ConfigurationProperties.MCAST_PORT;
import static org.junit.Assert.assertNotNull;

@Category(IntegrationTest.class)
public class GemFireVersionIntegrationJUnitTest {

  /**
   * test that the gemfire.properties generated by default is able
   * to start a server
   */
  @Test
  public void testDefaultConfig() throws IOException {
    String[] args = new String[1];
    args[0] = "gf" + System.nanoTime() + ".properties";
    DistributionConfigImpl.main(args);
    Properties props = new Properties();
    props.load(new FileInputStream(args[0]));
    props.setProperty(MCAST_PORT, "0");
    CacheFactory cacheFactory = new CacheFactory(props);
    Cache c = cacheFactory.create();
    assertNotNull(c);
    c.close();
  }
}
