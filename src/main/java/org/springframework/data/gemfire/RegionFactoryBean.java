/*
 * Copyright 2010-2012 the original author or authors.
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

package org.springframework.data.gemfire;

import java.lang.reflect.Field;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.io.Resource;
import org.springframework.data.gemfire.client.ClientRegionFactoryBean;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;

import com.gemstone.gemfire.cache.AttributesFactory;
import com.gemstone.gemfire.cache.Cache;
import com.gemstone.gemfire.cache.CacheClosedException;
import com.gemstone.gemfire.cache.CacheListener;
import com.gemstone.gemfire.cache.CacheLoader;
import com.gemstone.gemfire.cache.CacheWriter;
import com.gemstone.gemfire.cache.GemFireCache;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.RegionAttributes;
import com.gemstone.gemfire.cache.RegionFactory;
import com.gemstone.gemfire.cache.Scope;

/**
 * FactoryBean for creating generic GemFire {@link Region}s. Will try to first
 * locate the region (by name) and, in case none if found, proceed to creating
 * one using the given settings.
 * 
 * Note that this factory bean allows for very flexible creation of GemFire
 * {@link Region}. For "client" regions however, see
 * {@link ClientRegionFactoryBean} which offers easier configuration and
 * defaults.
 * 
 * @author Costin Leau
 * @author David Turanski
 */
public abstract class RegionFactoryBean<K, V> extends RegionLookupFactoryBean<K, V> implements DisposableBean {

	protected final Log log = LogFactory.getLog(getClass());

	private boolean destroy = false;

	private boolean close = true;

	private Resource snapshot;

	private CacheListener<K, V> cacheListeners[];

	private CacheLoader<K, V> cacheLoader;

	private CacheWriter<K, V> cacheWriter;

	private RegionAttributes<K, V> attributes;

	private Scope scope;

	private boolean persistent;

	private String diskStoreName;

	private String dataPolicyName;

	private Region<K, V> region;

	private List<Region<?, ?>> subRegions;

	@Override
	public void afterPropertiesSet() throws Exception {
		super.afterPropertiesSet();
		region = getRegion();
		postProcess(region);
	}

	@Override
	protected Region<K, V> lookupFallback(GemFireCache cache, String regionName) throws Exception {
		Assert.isTrue(cache instanceof Cache, "Unable to create regions from " + cache);

		Cache c = (Cache) cache;

		if (attributes != null)
			AttributesFactory.validateAttributes(attributes);

		final RegionFactory<K, V> regionFactory = (attributes != null ? c.createRegionFactory(attributes) : c
				.<K, V> createRegionFactory());

		if (!ObjectUtils.isEmpty(cacheListeners)) {
			for (CacheListener<K, V> listener : cacheListeners) {
				regionFactory.addCacheListener(listener);
			}
		}

		if (cacheLoader != null) {
			regionFactory.setCacheLoader(cacheLoader);
		}

		if (cacheWriter != null) {
			regionFactory.setCacheWriter(cacheWriter);
		}

		resolveDataPolicy(regionFactory, persistent, dataPolicyName);

		if (scope != null) {
			regionFactory.setScope(scope);
		}

		if (diskStoreName != null) {
			regionFactory.setDiskStoreName(diskStoreName);
		}

		if (attributes != null) {
			Assert.state(!attributes.isLockGrantor() || scope.isGlobal(),
					"Lock grantor only applies to a global scoped region");
		}
		// get underlying AttributesFactory
		postProcess(findAttrFactory(regionFactory));

		Region<K, V> reg = regionFactory.create(regionName);
		log.info("Created new cache region [" + regionName + "]");
		if (snapshot != null) {
			reg.loadSnapshot(snapshot.getInputStream());
		}

		if (attributes != null && attributes.isLockGrantor()) {
			reg.becomeLockGrantor();
		}

		return reg;
	}

	protected abstract void resolveDataPolicy(RegionFactory<K, V> regionFactory, boolean persistent,
			String dataPolicyName);

	@SuppressWarnings("unchecked")
	private AttributesFactory<K, V> findAttrFactory(RegionFactory<K, V> regionFactory) {
		Field attrField = ReflectionUtils.findField(RegionFactory.class, "attrsFactory", AttributesFactory.class);
		ReflectionUtils.makeAccessible(attrField);
		return (AttributesFactory<K, V>) ReflectionUtils.getField(attrField, regionFactory);
	}

	/**
	 * Post-process the attribute factory object used for configuring the region
	 * of this factory bean during the initialization process. The object is
	 * already initialized and configured by the factory bean before this method
	 * is invoked.
	 * 
	 * @param attrFactory attribute factory
	 * @deprecated as of GemFire 6.5, the use of {@link AttributesFactory} has
	 * been deprecated
	 */
	@Deprecated
	protected void postProcess(AttributesFactory<K, V> attrFactory) {
	}

	/**
	 * Post-process the region object for this factory bean during the
	 * initialization process. The object is already initialized and configured
	 * by the factory bean before this method is invoked.
	 * 
	 * @param region
	 */
	protected void postProcess(Region<K, V> region) {
		// do nothing
	}

	@Override
	public void destroy() throws Exception {
		if (region != null) {
			if (close) {
				if (!region.getCache().isClosed()) {
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

	public void setSubRegions(List<Region<?, ?>> subRegions) {
		log.info("setting subRegions");
		this.subRegions = subRegions;
	}

	/**
	 * Sets the cache loader used for the region used by this factory. Used only
	 * when a new region is created.Overrides the settings specified through
	 * {@link #setAttributes(RegionAttributes)}.
	 * 
	 * @param cacheLoader the cacheLoader to set on a newly created region
	 */
	public void setCacheLoader(CacheLoader<K, V> cacheLoader) {
		this.cacheLoader = cacheLoader;
	}

	/**
	 * Sets the cache writer used for the region used by this factory. Used only
	 * when a new region is created. Overrides the settings specified through
	 * {@link #setAttributes(RegionAttributes)}.
	 * 
	 * @param cacheWriter the cacheWriter to set on a newly created region
	 */
	public void setCacheWriter(CacheWriter<K, V> cacheWriter) {
		this.cacheWriter = cacheWriter;
	}

	/**
	 * Sets the region scope. Used only when a new region is created. Overrides
	 * the settings specified through {@link #setAttributes(RegionAttributes)}.
	 * 
	 * @see Scope
	 * @param scope the region scope
	 */
	public void setScope(Scope scope) {
		this.scope = scope;
	}

	public void setPersistent(boolean persistent) {
		this.persistent = persistent;
	}

	/**
	 * Sets the dataPolicy as a String. Required to support property
	 * placeholders
	 * @param dataPolicyName the dataPolicy name (NORMAL, PRELOADED, etc)
	 */
	public void setDataPolicyName(String dataPolicyName) {
		this.dataPolicyName = dataPolicyName;
	}

	/**
	 * Sets the name of disk store to use for overflow and persistence
	 * @param diskStoreName
	 */
	public void setDiskStoreName(String diskStoreName) {
		this.diskStoreName = diskStoreName;
	}

	/**
	 * Sets the region attributes used for the region used by this factory.
	 * Allows maximum control in specifying the region settings. Used only when
	 * a new region is created.
	 * 
	 * @param attributes the attributes to set on a newly created region
	 */
	public void setAttributes(RegionAttributes<K, V> attributes) {
		this.attributes = attributes;
	}
}