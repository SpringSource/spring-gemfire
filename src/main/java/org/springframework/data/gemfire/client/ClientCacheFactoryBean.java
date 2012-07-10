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

package org.springframework.data.gemfire.client;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Properties;

import org.springframework.data.gemfire.CacheFactoryBean;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.gemstone.gemfire.cache.GemFireCache;
import com.gemstone.gemfire.cache.client.ClientCacheFactory;
import com.gemstone.gemfire.cache.client.Pool;
import com.gemstone.gemfire.cache.client.PoolManager;
import com.gemstone.gemfire.distributed.DistributedSystem;
import com.gemstone.gemfire.pdx.PdxSerializer;

/**
 * FactoryBean dedicated to creating client caches (caches for client JVMs).
 * Acts an utility class (as client caches are a subset with a particular configuration of the generic cache).
 * 
 * @author Costin Leau
 */
public class ClientCacheFactoryBean extends CacheFactoryBean {

	/**
	 * Inner class to avoid a hard dependency on the GemFire 6.6 API.
	 * 
	 * @author Costin Leau
	 */
	private class PdxOptions implements Runnable {

		private ClientCacheFactory factory;

		PdxOptions(ClientCacheFactory factory) {
			this.factory = factory;
		}

		public void run() {
			if (pdxSerializer != null) {
				Assert.isAssignable(PdxSerializer.class, pdxSerializer.getClass(), "Invalid pdx serializer used");
				factory.setPdxSerializer((PdxSerializer) pdxSerializer);
			}
			if (pdxDiskStoreName != null) {
				factory.setPdxDiskStore(pdxDiskStoreName);
			}
			if (pdxIgnoreUnreadFields != null) {
				factory.setPdxIgnoreUnreadFields(pdxIgnoreUnreadFields);
			}
			if (pdxPersistent != null) {
				factory.setPdxPersistent(pdxPersistent);
			}
			if (pdxReadSerialized != null) {
				factory.setPdxReadSerialized(pdxReadSerialized);
			}
		}
	}

	private String poolName;
	private Pool pool;

	@Override
	protected GemFireCache createCache(Object factory) {
		ClientCacheFactory ccf = (ClientCacheFactory) factory;
		initializePool(ccf);
		return ccf.create();
	}

	@Override
	protected Object createFactory(Properties props) {
		return new ClientCacheFactory(props);
	}

	@Override
	protected GemFireCache fetchCache() {
		return ClientCacheFactory.getAnyInstance();
	}

	private void initializePool(ClientCacheFactory ccf) {
		Pool p = pool;

		if (p == null && StringUtils.hasText(poolName)) {
			p = PoolManager.find(poolName);

			// initialize a client-like Distributed System before initializing the pool
			if (p == null) {
				Properties prop = mergeProperties();
				prop.setProperty("mcast-port", "0");
				prop.setProperty("locators", "");

				DistributedSystem system = DistributedSystem.connect(prop);
			}

			// trigger pool initialization
			Assert.isTrue(getBeanFactory().isTypeMatch(poolName, Pool.class), "No bean named " + poolName + " of type "
					+ Pool.class.getName() + " found");

			p = getBeanFactory().getBean(poolName, Pool.class);
			Assert.notNull(p, "No pool named [" + poolName + "] found");
		}

		if (p != null) {
			// copy the pool settings - this way if the pool is not found, at least the cache will have a similar config
			ccf.setPoolFreeConnectionTimeout(p.getFreeConnectionTimeout());
			ccf.setPoolIdleTimeout(p.getIdleTimeout());
			ccf.setPoolLoadConditioningInterval(p.getLoadConditioningInterval());
			ccf.setPoolMaxConnections(p.getMaxConnections());
			ccf.setPoolMinConnections(p.getMinConnections());
			ccf.setPoolMultiuserAuthentication(p.getMultiuserAuthentication());
			ccf.setPoolPingInterval(p.getPingInterval());
			ccf.setPoolPRSingleHopEnabled(p.getPRSingleHopEnabled());
			ccf.setPoolReadTimeout(p.getReadTimeout());
			ccf.setPoolRetryAttempts(p.getRetryAttempts());
			ccf.setPoolServerGroup(p.getServerGroup());
			ccf.setPoolSocketBufferSize(p.getSocketBufferSize());
			ccf.setPoolStatisticInterval(p.getStatisticInterval());
			ccf.setPoolSubscriptionAckInterval(p.getSubscriptionAckInterval());
			ccf.setPoolSubscriptionEnabled(p.getSubscriptionEnabled());
			ccf.setPoolSubscriptionMessageTrackingTimeout(p.getSubscriptionMessageTrackingTimeout());
			ccf.setPoolSubscriptionRedundancy(p.getSubscriptionRedundancy());
			ccf.setPoolThreadLocalConnections(p.getThreadLocalConnections());

			List<InetSocketAddress> locators = p.getLocators();
			if (locators != null) {
				for (InetSocketAddress inet : locators) {
					ccf.addPoolLocator(inet.getHostName(), inet.getPort());
				}
			}


			List<InetSocketAddress> servers = p.getServers();
			if (locators != null) {
				for (InetSocketAddress inet : servers) {
					ccf.addPoolServer(inet.getHostName(), inet.getPort());
				}
			}
		}
	}

	/**
	 * Sets the pool name used by this client.
	 * 
	 * @param poolName
	 */
	public void setPoolName(String poolName) {
		Assert.hasText(poolName, "pool name is required");
		this.poolName = poolName;
	}

	/**
	 * Sets the pool used by this client.
	 * 
	 * @param pool
	 */
	public void setPool(Pool pool) {
		Assert.notNull(pool, "pool cannot be null");
		this.pool = pool;
	}

	@Override
	protected void applyPdxOptions(Object factory) {
		if (factory instanceof ClientCacheFactory) {
			new PdxOptions((ClientCacheFactory) factory).run();
		}
	}
}