/*
 * Copyright 2011-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.data.gemfire.client;

import java.util.Properties;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.data.gemfire.CacheFactoryBean;
import org.springframework.data.gemfire.GemfireUtils;
import org.springframework.data.gemfire.config.GemfireConstants;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.gemstone.gemfire.cache.CacheClosedException;
import com.gemstone.gemfire.cache.GemFireCache;
import com.gemstone.gemfire.cache.client.ClientCache;
import com.gemstone.gemfire.cache.client.ClientCacheFactory;
import com.gemstone.gemfire.cache.client.Pool;
import com.gemstone.gemfire.cache.client.PoolManager;
import com.gemstone.gemfire.distributed.DistributedSystem;
import com.gemstone.gemfire.pdx.PdxSerializer;

/**
 * FactoryBean dedicated to creating GemFire client caches.
 * 
 * @author Costin Leau
 * @author Lyndon Adams
 * @author John Blum
 * @see org.springframework.context.ApplicationListener
 * @see org.springframework.context.event.ContextRefreshedEvent
 * @see org.springframework.data.gemfire.CacheFactoryBean
 * @see com.gemstone.gemfire.cache.GemFireCache
 * @see com.gemstone.gemfire.cache.client.ClientCache
 * @see com.gemstone.gemfire.cache.client.ClientCacheFactory
 * @see com.gemstone.gemfire.cache.client.Pool
 * @see com.gemstone.gemfire.cache.client.PoolManager
 * @see com.gemstone.gemfire.distributed.DistributedSystem
 */
@SuppressWarnings("unused")
public class ClientCacheFactoryBean extends CacheFactoryBean implements ApplicationListener<ContextRefreshedEvent> {

	protected Boolean keepAlive = false;

	protected Boolean readyForEvents = false;

	private Pool pool;

	protected String poolName;

	@Override
	protected void postProcessPropertiesBeforeInitialization(Properties gemfireProperties) {
	}

	/**
	 * Fetches an existing GemFire ClientCache instance from the ClientCacheFactory.
	 *
	 * @param <T> is Class type extension of GemFireCache.
	 * @return the existing GemFire ClientCache instance if available.
	 * @throws com.gemstone.gemfire.cache.CacheClosedException if an existing GemFire Cache instance does not exist.
	 * @throws java.lang.IllegalStateException if the GemFire cache instance is not a ClientCache.
	 * @see com.gemstone.gemfire.cache.GemFireCache
	 * @see com.gemstone.gemfire.cache.client.ClientCacheFactory#getAnyInstance()
	 */
	@Override
	@SuppressWarnings("unchecked")
	protected <T extends GemFireCache> T fetchCache() {
		return (T) ClientCacheFactory.getAnyInstance();
	}

	/**
	 * Resolves the GemFire System properties used to configure the GemFire ClientCache instance.
	 *
	 * @return a Properties object containing GemFire System properties used to configure
	 * the GemFire ClientCache instance.
	 */
	@Override
	protected Properties resolveProperties() {
		Properties gemfireProperties = super.resolveProperties();

		DistributedSystem distributedSystem = getDistributedSystem();

		if (GemfireUtils.isConnected(distributedSystem)) {
			Properties distributedSystemProperties = (Properties) distributedSystem.getProperties().clone();
			distributedSystemProperties.putAll(gemfireProperties);
			gemfireProperties = distributedSystemProperties;
		}

		return gemfireProperties;
	}

	/* (non-Javadoc) */
	<T extends DistributedSystem> T getDistributedSystem() {
		return GemfireUtils.getDistributedSystem();
	}

	/**
	 * Creates an instance of GemFire factory initialized with the given GemFire System Properties
	 * to create an instance of a GemFire cache.
	 *
	 * @param gemfireProperties a Properties object containing GemFire System properties.
	 * @return an instance of a GemFire factory used to create a GemFire cache instance.
	 * @see java.util.Properties
	 * @see com.gemstone.gemfire.cache.client.ClientCacheFactory
	 */
	@Override
	protected Object createFactory(Properties gemfireProperties) {
		return new ClientCacheFactory(gemfireProperties);
	}

	/**
	 * Initializes the GemFire factory used to create the GemFire client cache instance.  Sets PDX options
	 * specified by the user.
	 *
	 * @param factory the GemFire factory used to create an instance of the GemFire client cache.
	 * @return the initialized GemFire client cache factory.
	 * @see #isPdxOptionsSpecified()
	 */
	@Override
	protected Object prepareFactory(final Object factory) {
		if (isPdxOptionsSpecified()) {
			ClientCacheFactory clientCacheFactory = (ClientCacheFactory) factory;

			if (pdxSerializer != null) {
				Assert.isAssignable(PdxSerializer.class, pdxSerializer.getClass(), "Invalid pdx serializer used");
				clientCacheFactory.setPdxSerializer((PdxSerializer) pdxSerializer);
			}
			if (pdxDiskStoreName != null) {
				clientCacheFactory.setPdxDiskStore(pdxDiskStoreName);
			}
			if (pdxIgnoreUnreadFields != null) {
				clientCacheFactory.setPdxIgnoreUnreadFields(pdxIgnoreUnreadFields);
			}
			if (pdxPersistent != null) {
				clientCacheFactory.setPdxPersistent(pdxPersistent);
			}
			if (pdxReadSerialized != null) {
				clientCacheFactory.setPdxReadSerialized(pdxReadSerialized);
			}
		}

		return factory;
	}

	/**
	 * Creates a new GemFire cache instance using the provided factory.
	 *
	 * @param <T> parameterized Class type extension of GemFireCache.
	 * @param factory the appropriate GemFire factory used to create a cache instance.
	 * @return an instance of the GemFire cache.
	 * @see com.gemstone.gemfire.cache.GemFireCache
	 * @see com.gemstone.gemfire.cache.client.ClientCacheFactory#create()
	 */
	@Override
	@SuppressWarnings("unchecked")
	protected <T extends GemFireCache> T createCache(Object factory) {
		return (T) initializePool((ClientCacheFactory) factory).create();
	}

	/**
	 * Initialize the Pool settings on the ClientCacheFactory.
	 *
	 * @param clientCacheFactory the GemFire ClientCacheFactory used to configure and create a GemFire ClientCache.
	 * @see com.gemstone.gemfire.cache.client.ClientCacheFactory
	 */
	private ClientCacheFactory initializePool(ClientCacheFactory clientCacheFactory) {
		resolvePool(this.pool);
		return clientCacheFactory;
	}

	/**
	 * Resolves the appropriate GemFire Pool from configuration used to configure the ClientCache.
	 *
	 * @param pool the preferred GemFire Pool to use in the configuration of the ClientCache.
	 * @return the resolved GemFire Pool.
	 * @see com.gemstone.gemfire.cache.client.Pool
	 * @see com.gemstone.gemfire.cache.client.PoolManager#find(String)
	 */
	private Pool resolvePool(final Pool pool) {
		Pool localPool = pool;

		if (localPool == null) {
			localPool = PoolManager.find(poolName);

			if (localPool == null) {
				if (StringUtils.hasText(poolName) && getBeanFactory().isTypeMatch(poolName, Pool.class)) {
					localPool = getBeanFactory().getBean(poolName, Pool.class);
				}
				else {
					try {
						localPool = getBeanFactory().getBean(Pool.class);
						this.poolName = localPool.getName();
					}
					catch (BeansException ignore) {
						log.info(String.format("no bean of type '%1$s' having name '%2$s' was found",
							Pool.class.getName(), (StringUtils.hasText(poolName) ? poolName
								: GemfireConstants.DEFAULT_GEMFIRE_POOL_NAME)));
					}
				}
			}
		}

		return localPool;
	}

	/**
	 * Inform the GemFire cluster that this client cache is ready to receive events iff the client is non-durable.
	 *
	 * @param event the ApplicationContextEvent fired when the ApplicationContext is refreshed.
	 * @see com.gemstone.gemfire.cache.client.ClientCache#readyForEvents()
	 * @see #getReadyForEvents()
	 * @see #getObject()
	 */
	@Override
	public void onApplicationEvent(final ContextRefreshedEvent event) {
		if (isReadyForEvents()) {
			try {
				((ClientCache) fetchCache()).readyForEvents();
			}
			catch (IllegalStateException ignore) {
				// thrown if clientCache.readyForEvents() is called on a non-durable client
			}
			catch (CacheClosedException ignore) {
			}
		}
	}

	@Override
	protected void close(final GemFireCache cache) {
		((ClientCache) cache).close(isKeepAlive());
	}

	@Override
	public final void setEnableAutoReconnect(final Boolean enableAutoReconnect) {
		throw new UnsupportedOperationException("Auto-reconnect is not supported on ClientCache.");
	}

	@Override
	public final Boolean getEnableAutoReconnect() {
		return Boolean.FALSE;
	}

	@Override
	public Class<? extends GemFireCache> getObjectType() {
		return (cache != null ? cache.getClass() : ClientCache.class);
	}

	/**
	 * Sets whether the server(s) should keep the durable client's queue alive for the duration of the timeout
	 * when the client voluntarily disconnects.
	 *
	 * @param keepAlive a boolean value indicating to the server to keep the durable client's queues alive.
	 */
	public void setKeepAlive(Boolean keepAlive) {
		this.keepAlive = keepAlive;
	}

	/**
	 * Determines whether the server(s) should keep the durable client's queue alive for the duration of the timeout
	 * when the client voluntarily disconnects.
	 *
	 * @return a boolean value indicating whether the server should keep the durable client's queues alive.
	 */
	public boolean isKeepAlive() {
		return Boolean.TRUE.equals(this.keepAlive);
	}

	/**
	 * Sets the pool used by this client.
	 *
	 * @param pool the GemFire pool used by the Client Cache to obtain connections to the GemFire cluster.
	 */
	public void setPool(Pool pool) {
		Assert.notNull(pool, "GemFire Pool must not be null");
		this.pool = pool;
	}

	/**
	 * Sets the pool name used by this client.
	 *
	 * @param poolName set the name of the GemFire Pool used by the GemFire Client Cache.
	 */
	public void setPoolName(String poolName) {
		Assert.hasText(poolName, "Pool 'name' is required");
		this.poolName = poolName;
	}

	/**
	 * Gets the pool name used by this client.
	 *
	 * @return the name of the GemFire Pool used by the GemFire Client Cache.
	 */
	public String getPoolName() {
		return poolName;
	}

	/**
	 * Set the readyForEvents flag.
	 *
	 * @param readyForEvents sets a boolean flag to notify the server that this durable client is ready
	 * to receive updates.
	 * @see #getReadyForEvents()
	 */
	public void setReadyForEvents(Boolean readyForEvents){
		this.readyForEvents = readyForEvents;
	}

	/**
	 * Gets the value for the readyForEvents property.
	 *
	 * @return a boolean value indicating the state of the 'readyForEvents' property.
	 */
	public Boolean getReadyForEvents(){
		return this.readyForEvents;
	}

	/* (non-Javadoc) */
	public boolean isReadyForEvents() {
		return Boolean.TRUE.equals(getReadyForEvents());
	}

	@Override
	public final Boolean getUseClusterConfiguration() {
		return Boolean.FALSE;
	}

	@Override
	public final void setUseClusterConfiguration(Boolean useClusterConfiguration) {
		throw new UnsupportedOperationException("Shared, cluster configuration is not applicable to clients.");
	}

}
