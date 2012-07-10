/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.data.gemfire.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.springframework.data.gemfire.RecreatingContextTest;

import com.gemstone.gemfire.cache.server.CacheServer;

/**
 * 
 * @author Costin Leau
 */
public class CacheServerNamespaceTest extends RecreatingContextTest {

	@Override
	protected String location() {
		return "org/springframework/data/gemfire/config/server-ns.xml";
	}

	@Test
	public void testInitOrder() throws Exception {
		// the test is actually executed through Init#afterPropertiesSet
	}

	@Test
	public void testBasicCacheServer() throws Exception {
		CacheServer cacheServer = ctx.getBean("advanced-config", CacheServer.class);
		assertTrue(cacheServer.isRunning());
		assertEquals(1, cacheServer.getGroups().length);
		assertEquals("test-server", cacheServer.getGroups()[0]);
		assertEquals(22, cacheServer.getMaxConnections());

	}
}
