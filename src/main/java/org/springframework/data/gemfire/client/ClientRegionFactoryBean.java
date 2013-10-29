/*
 * Copyright 2010-2013 the original author or authors.
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

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.io.Resource;
import org.springframework.data.gemfire.DataPolicyConverter;
import org.springframework.data.gemfire.RegionLookupFactoryBean;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import com.gemstone.gemfire.cache.CacheClosedException;
import com.gemstone.gemfire.cache.CacheListener;
import com.gemstone.gemfire.cache.DataPolicy;
import com.gemstone.gemfire.cache.GemFireCache;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.RegionAttributes;
import com.gemstone.gemfire.cache.client.ClientCache;
import com.gemstone.gemfire.cache.client.ClientRegionFactory;
import com.gemstone.gemfire.cache.client.ClientRegionShortcut;
import com.gemstone.gemfire.cache.client.Pool;
import com.gemstone.gemfire.internal.cache.GemFireCacheImpl;

/**
 * Client extension for GemFire Regions.
 * <p/>
 * @author Costin Leau
 * @author David Turanski
 * @author John Blum
 */
public class ClientRegionFactoryBean<K, V> extends RegionLookupFactoryBean<K, V> implements BeanFactoryAware,
		DisposableBean {

	private boolean close = true;
	private boolean destroy = false;

	private BeanFactory beanFactory;

	private Boolean persistent;

	private CacheListener<K, V>[] cacheListeners;

	private ClientRegionShortcut shortcut = null;

	private DataPolicy dataPolicy;

	private Interest<K>[] interests;

	private RegionAttributes<K, V> attributes;

	private Resource snapshot;

	private String diskStoreName;
	private String poolName;

	@Override
	public void afterPropertiesSet() throws Exception {
		super.afterPropertiesSet();
		postProcess(getRegion());
	}

	@Override
	protected Region<K, V> lookupFallback(GemFireCache cache, String regionName) throws Exception {
		Assert.isTrue(cache instanceof ClientCache, "Unable to create regions from " + cache);

		// TODO use of GemFire internal class!
		if (cache instanceof GemFireCacheImpl) {
			Assert.isTrue(((GemFireCacheImpl) cache).isClient(), "A client-cache instance is required.");
		}

		final ClientCache clientCache = (ClientCache) cache;

		// first look at shortcut
		ClientRegionFactory<K, V> factory = clientCache.createClientRegionFactory(resolveClientRegionShortcut());

		// map the attributes onto the client
		if (attributes != null) {
			CacheListener<K, V>[] listeners = attributes.getCacheListeners();
			if (!ObjectUtils.isEmpty(listeners)) {
				for (CacheListener<K, V> listener : listeners) {
					factory.addCacheListener(listener);
				}
			}
			factory.setCloningEnabled(attributes.getCloningEnabled());
			factory.setConcurrencyLevel(attributes.getConcurrencyLevel());
			factory.setCustomEntryIdleTimeout(attributes.getCustomEntryIdleTimeout());
			factory.setCustomEntryTimeToLive(attributes.getCustomEntryTimeToLive());
			factory.setDiskStoreName(attributes.getDiskStoreName());
			factory.setDiskSynchronous(attributes.isDiskSynchronous());
			factory.setEntryIdleTimeout(attributes.getEntryIdleTimeout());
			factory.setEntryTimeToLive(attributes.getEntryTimeToLive());
			factory.setEvictionAttributes(attributes.getEvictionAttributes());
			factory.setInitialCapacity(attributes.getInitialCapacity());
			factory.setKeyConstraint(attributes.getKeyConstraint());
			factory.setLoadFactor(attributes.getLoadFactor());
			factory.setPoolName(attributes.getPoolName());
			factory.setRegionIdleTimeout(attributes.getRegionIdleTimeout());
			factory.setRegionTimeToLive(attributes.getRegionTimeToLive());
			factory.setStatisticsEnabled(attributes.getStatisticsEnabled());
			factory.setValueConstraint(attributes.getValueConstraint());
		}

		if (!ObjectUtils.isEmpty(cacheListeners)) {
			for (CacheListener<K, V> listener : cacheListeners) {
				factory.addCacheListener(listener);
			}
		}

		if (StringUtils.hasText(poolName)) {
			// try to eagerly initialize the pool name, if defined as a bean
			if (beanFactory.isTypeMatch(poolName, Pool.class)) {
				if (log.isDebugEnabled()) {
					log.debug("Found bean definition for pool '" + poolName + "'. Eagerly initializing it...");
				}
				beanFactory.getBean(poolName, Pool.class);
			}

			factory.setPoolName(poolName);
		}
		else {
			Pool pool = beanFactory.getBean(Pool.class);
			factory.setPoolName(pool.getName());
		}

		if (diskStoreName != null) {
			factory.setDiskStoreName(diskStoreName);
		}

		Region<K, V> region = factory.create(regionName);
		log.info("Created new cache region [" + regionName + "]");

		if (snapshot != null) {
			region.loadSnapshot(snapshot.getInputStream());
		}

		return region;
	}

	protected ClientRegionShortcut resolveClientRegionShortcut() {
		ClientRegionShortcut resolvedShortcut = this.shortcut;

		if (resolvedShortcut == null) {
			if (this.dataPolicy != null) {
				assertDataPolicyAndPersistentAttributeAreCompatible(this.dataPolicy);

				if (DataPolicy.EMPTY.equals(this.dataPolicy)) {
					resolvedShortcut = ClientRegionShortcut.PROXY;
				}
				else if (DataPolicy.NORMAL.equals(this.dataPolicy)) {
					resolvedShortcut = ClientRegionShortcut.CACHING_PROXY;
				}
				else if (DataPolicy.PERSISTENT_REPLICATE.equals(this.dataPolicy)) {
					resolvedShortcut = ClientRegionShortcut.LOCAL_PERSISTENT;
				}
				else {
					// NOTE the Data Policy validation is based on the ClientRegionShortcut initialization logic
					// in com.gemstone.gemfire.internal.cache.GemFireCacheImpl.initializeClientRegionShortcuts
					throw new IllegalArgumentException(String.format(
						"Data Policy '%1$s' is invalid for Client Regions.", this.dataPolicy));
				}
			}
			else {
				resolvedShortcut = (isPersistent() ? ClientRegionShortcut.LOCAL_PERSISTENT
					: ClientRegionShortcut.LOCAL);
			}

		}

		// NOTE the ClientRegionShortcut and Persistent attribute will be compatible if the shortcut was derived from
		// the Data Policy.
		assertClientRegionShortcutAndPersistentAttributeAreCompatible(resolvedShortcut);

		return resolvedShortcut;
	}

	protected void postProcess(Region<K, V> region) {
		if (!ObjectUtils.isEmpty(interests)) {
			for (Interest<K> interest : interests) {
				if (interest instanceof RegexInterest) {
					region.registerInterestRegex((String) interest.getKey(), interest.getPolicy(),
						interest.isDurable(), interest.isReceiveValues());
				}
				else {
					region.registerInterest(interest.getKey(), interest.getPolicy(), interest.isDurable(),
						interest.isReceiveValues());
				}
			}
		}
	}

	@Override
	public void destroy() throws Exception {
		Region<K, V> region = getObject();
		// unregister interests
		try {
			if (region != null && !ObjectUtils.isEmpty(interests)) {
				for (Interest<K> interest : interests) {
					if (interest instanceof RegexInterest) {
						region.unregisterInterestRegex((String) interest.getKey());
					}
					else {
						region.unregisterInterest(interest.getKey());
					}
				}
			}
			// should not really happen since interests are validated at
			// start/registration
		}
		catch (UnsupportedOperationException ex) {
			log.warn("Cannot unregister cache interests", ex);
		}

		if (region != null) {
			if (close) {
				if (!region.getRegionService().isClosed()) {
					try {
						region.close();
					}
					catch (CacheClosedException cce) {
						// nothing to see folks, move on.
					}
				}
			}
			else if (destroy) {
				region.destroyRegion();
			}
		}
		region = null;
	}

	/**
	 * Sets the region attributes used for the region used by this factory.
	 * Allows maximum control in specifying the region settings. Used only when
	 * a new region is created. Note that using this method allows for advanced
	 * customization of the region - while it provides a lot of flexibility,
	 * note that it's quite easy to create misconfigured regions (especially in
	 * a client/server scenario).
	 *
	 * @param attributes the attributes to set on a newly created region
	 */
	public void setAttributes(RegionAttributes<K, V> attributes) {
		this.attributes = attributes;
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory;
	}

	/**
	 * Set the interests for this client region. Both key and regex interest are
	 * supported.
	 * 
	 * @param interests the interests to set
	 */
	public void setInterests(Interest<K>[] interests) {
		this.interests = interests;
	}

	/**
	 * @return the interests
	 */
	Interest<K>[] getInterests() {
		return interests;
	}

	/**
	 * Sets the pool used by this client.
	 *
	 * @param pool
	 */
	public void setPool(Pool pool) {
		Assert.notNull(pool, "pool cannot be null");
		setPoolName(pool.getName());
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
	 * Initializes the client using a GemFire {@link ClientRegionShortcut}. The
	 * recommended way for creating clients since it covers all the major
	 * scenarios with minimal configuration.
	 * 
	 * @param shortcut
	 */
	public void setShortcut(ClientRegionShortcut shortcut) {
		this.shortcut = shortcut;
	}

	/**
	 * Indicates whether the region referred by this factory bean, will be
	 * destroyed on shutdown (default false). Note: destroy and close are
	 * mutually exclusive. Enabling one will automatically disable the other.
	 * 
	 * @param destroy whether or not to destroy the region
	 * 
	 * @see #setClose(boolean)
	 */
	public void setDestroy(boolean destroy) {
		this.destroy = destroy;

		if (destroy) {
			close = false;
		}
	}

	/**
	 * Indicates whether the region referred by this factory bean, will be
	 * closed on shutdown (default true). Note: destroy and close are mutually
	 * exclusive. Enabling one will automatically disable the other.
	 * 
	 * @param close whether to close or not the region
	 * @see #setDestroy(boolean)
	 */
	public void setClose(boolean close) {
		this.close = close;
		if (close) {
			destroy = false;
		}
	}

	/**
	 * Sets the snapshots used for loading a newly <i>created</i> region. That
	 * is, the snapshot will be used <i>only</i> when a new region is created -
	 * if the region already exists, no loading will be performed.
	 * 
	 * @see #setName(String)
	 * @param snapshot the snapshot to set
	 */
	public void setSnapshot(Resource snapshot) {
		this.snapshot = snapshot;
	}

	/**
	 * Sets the cache listeners used for the region used by this factory. Used
	 * only when a new region is created.Overrides the settings specified
	 * through {@link #setAttributes(RegionAttributes)}.
	 * 
	 * @param cacheListeners the cacheListeners to set on a newly created region
	 */
	public void setCacheListeners(CacheListener<K, V>[] cacheListeners) {
		this.cacheListeners = cacheListeners;
	}

	protected void assertClientRegionShortcutAndPersistentAttributeAreCompatible(final ClientRegionShortcut resolvedShortcut) {
		final boolean persistentNotSpecified = (this.persistent == null);

		if (ClientRegionShortcut.LOCAL_PERSISTENT.equals(resolvedShortcut)
				|| ClientRegionShortcut.LOCAL_PERSISTENT_OVERFLOW.equals(resolvedShortcut)) {
			Assert.isTrue(persistentNotSpecified || isPersistent(), String.format(
				"Client Region Shortcut '%1$s' is invalid when persistent is false.", resolvedShortcut));
		}
		else {
			Assert.isTrue(persistentNotSpecified || isNotPersistent(), String.format(
				"Client Region Shortcut '%1$s' is invalid when persistent is true.", resolvedShortcut));
		}
	}
	/**
	 * Validates that the settings for Data Policy and the 'persistent' attribute in <gfe:*-region/> elements
	 * are compatible.
	 * <p/>
	 * @param resolvedDataPolicy the GemFire Data Policy resolved form the Spring GemFire XML namespace configuration
	 * meta-data.
	 * @see #isPersistent()
	 * @see #isNotPersistent()
	 * @see com.gemstone.gemfire.cache.DataPolicy
	 */
	protected void assertDataPolicyAndPersistentAttributeAreCompatible(final DataPolicy resolvedDataPolicy) {
		final boolean persistentNotSpecified = (this.persistent == null);

		if (resolvedDataPolicy.withPersistence()) {
			Assert.isTrue(persistentNotSpecified || isPersistent(), String.format(
				"Data Policy '%1$s' is invalid when persistent is false.", resolvedDataPolicy));
		}
		else {
			// NOTE otherwise, the Data Policy is with persistence, so...
			Assert.isTrue(persistentNotSpecified || isNotPersistent(), String.format(
				"Data Policy '%1$s' is invalid when persistent is true.", resolvedDataPolicy));
		}
	}

	/**
	 * Sets the Data Policy. Used only when a new Region is created.
	 * <p/>
	 * @param dataPolicy the client Region's Data Policy.
	 * @see com.gemstone.gemfire.cache.DataPolicy
	 */
	public void setDataPolicy(DataPolicy dataPolicy) {
		this.dataPolicy = dataPolicy;
	}

	/**
	 * An alternate way to set the Data Policy, using the String name of the enumerated value.
	 * <p/>
	 * @param dataPolicyName the enumerated value String name of the Data Policy.
	 * @see com.gemstone.gemfire.cache.DataPolicy
	 * @see #setDataPolicy(com.gemstone.gemfire.cache.DataPolicy)
	 * @deprecated use setDataPolicy(:DataPolicy) instead.
	 */
	public void setDataPolicyName(String dataPolicyName) {
		final DataPolicy resolvedDataPolicy = new DataPolicyConverter().convert(dataPolicyName);
		Assert.notNull(resolvedDataPolicy, String.format("Data Policy '%1$s' is invalid.", dataPolicyName));
		setDataPolicy(resolvedDataPolicy);
	}

	protected boolean isPersistent() {
		return Boolean.TRUE.equals(persistent);
	}

	protected boolean isNotPersistent() {
		return Boolean.FALSE.equals(persistent);
	}

	public void setPersistent(final boolean persistent) {
		this.persistent = persistent;
	}

	/**
	 * Sets the name of disk store to use for overflow and persistence
	 * @param diskStoreName
	 */
	public void setDiskStoreName(String diskStoreName) {
		this.diskStoreName = diskStoreName;
	}

}
