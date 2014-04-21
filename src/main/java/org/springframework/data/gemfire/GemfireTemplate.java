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

package org.springframework.data.gemfire;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.Map;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

import com.gemstone.gemfire.GemFireCheckedException;
import com.gemstone.gemfire.GemFireException;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.Scope;
import com.gemstone.gemfire.cache.client.ClientCache;
import com.gemstone.gemfire.cache.query.IndexInvalidException;
import com.gemstone.gemfire.cache.query.Query;
import com.gemstone.gemfire.cache.query.QueryInvalidException;
import com.gemstone.gemfire.cache.query.QueryService;
import com.gemstone.gemfire.cache.query.SelectResults;
import com.gemstone.gemfire.internal.cache.LocalRegion;

/**
 * Helper class that simplifies GemFire data access code and converts {@link GemFireCheckedException} and
 * {@link GemFireException} into Spring {@link DataAccessException}, following the <tt>org.springframework.dao</tt>
 * exception hierarchy.
 * 
 *
 * The central method is <tt>execute</tt>, supporting GemFire access code implementing the GemfireCallback interface.
 * It provides dedicated handling such that neither the GemfireCallback implementation nor the calling code needs to
 * explicitly care about handling {@link Region} life-cycle exceptions.
 * Typically used to implement data access or business logic services that use GemFire within their implementation but
 * are GemFire-agnostic in their interface. The latter or code calling the latter only have to deal with business
 * objects, query objects, and <tt>org.springframework.dao</tt> exceptions. 
 * 
 * @author Costin Leau
 */
public class GemfireTemplate extends GemfireAccessor implements GemfireOperations {

	private boolean exposeNativeRegion = false;

	private Region<?, ?> regionProxy;

	public GemfireTemplate() {
	}

	public <K, V> GemfireTemplate(Region<K, V> region) {
		setRegion(region);
		afterPropertiesSet();
	}

	@Override
	public void afterPropertiesSet() {
		super.afterPropertiesSet();
		regionProxy = createRegionProxy(getRegion());
	}

	/**
	 * Sets whether to expose the native Gemfire Region to GemfireCallback
	 * code. Default is "false": a Region proxy will be returned,
	 * suppressing <code>close</code> calls.
	 * <p>As there is often a need to cast to a interface, the exposed proxy
	 * implements all interfaces implemented by the original {@link Region}.
	 * If this is not sufficient, turn this flag to "true".
	 * @see GemfireCallback
	 */
	public void setExposeNativeRegion(boolean exposeNativeRegion) {
		this.exposeNativeRegion = exposeNativeRegion;
	}

	/**
	 * Returns whether to expose the native GemFire Region to GemfireCallback
	 * code, or rather a Region proxy.
	 */
	public boolean isExposeNativeRegion() {
		return this.exposeNativeRegion;
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.gemfire.GemfireOperations#containsKey(java.lang.Object)
	 */
	@Override
	public boolean containsKey(final Object key) {
		return execute(new GemfireCallback<Boolean>() {
 			public Boolean doInGemfire(Region<?,?> region) throws GemFireCheckedException, GemFireException {
				return region.containsKey(key);
			}
		});
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.gemfire.GemfireOperations#containsKeyOnServer(java.lang.Object)
	 */
	@Override
	public boolean containsKeyOnServer(final Object key) {
		return execute(new GemfireCallback<Boolean>() {
			public Boolean doInGemfire(Region<?,?> region) throws GemFireCheckedException, GemFireException {
				return region.containsKeyOnServer(key);
			}
		});
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.gemfire.GemfireOperations#containsValue(java.lang.Object)
	 */
	@Override
	public boolean containsValue(final Object value) {
		return execute(new GemfireCallback<Boolean>() {
			public Boolean doInGemfire(Region<?,?> region) throws GemFireCheckedException, GemFireException {
				return region.containsValue(value);
			}
		});
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.gemfire.GemfireOperations#containsValueForKey(java.lang.Object)
	 */
	@Override
	public boolean containsValueForKey(final Object key) {
		return execute(new GemfireCallback<Boolean>() {
			public Boolean doInGemfire(Region<?,?> region) throws GemFireCheckedException, GemFireException {
				return region.containsValueForKey(key);
			}
		});
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.gemfire.GemfireOperations#create(K, V)
	 */
	@Override
	public <K, V> void create(final K key, final V value) {
		execute(new GemfireCallback<Object>() {
			@SuppressWarnings({ "unchecked", "rawtypes" })
			public Object doInGemfire(Region region) throws GemFireCheckedException, GemFireException {
				region.create(key, value);
				return null;
			}
		});
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.gemfire.GemfireOperations#get(K)
	 */
	@Override
	public <K, V> V get(final K key) {
		return execute(new GemfireCallback<V>() {
			@SuppressWarnings({ "unchecked", "rawtypes" })
			public V doInGemfire(Region region) throws GemFireCheckedException, GemFireException {
				return (V) region.get(key);
			}
		});
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.gemfire.GemfireOperations#put(K, V)
	 */
	@Override
	public <K, V> V put(final K key, final V value) {
		return execute(new GemfireCallback<V>() {
			@SuppressWarnings({ "unchecked", "rawtypes" })
			public V doInGemfire(Region region) throws GemFireCheckedException, GemFireException {
				return (V) region.put(key, value);
			}
		});
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.gemfire.GemfireOperations#putIfAbsent(K, V)
	 */
	@Override
	public <K, V> V putIfAbsent(final K key, final V value) {
		return execute(new GemfireCallback<V>() {
			@SuppressWarnings({ "unchecked", "rawtypes" })
			public V doInGemfire(Region region) throws GemFireCheckedException, GemFireException {
				return (V) region.putIfAbsent(key, value);
			}
		});
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.gemfire.GemfireOperations#remove(K)
	 */
	@Override
	public <K, V> V remove(final K key) {
		return execute(new GemfireCallback<V>() {
			@SuppressWarnings({ "unchecked", "rawtypes" })
			public V doInGemfire(Region region) throws GemFireCheckedException, GemFireException {
				return (V) region.remove(key);
			}
		});
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.gemfire.GemfireOperations#replace(K, V)
	 */
	@Override
	public <K, V> V replace(final K key, final V value) {
		return execute(new GemfireCallback<V>() {
			@SuppressWarnings({ "unchecked", "rawtypes" })
			public V doInGemfire(Region region) throws GemFireCheckedException, GemFireException {
				return (V) region.replace(key, value);
			}
		});
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.gemfire.GemfireOperations#replace(K, V, V)
	 */
	@Override
	public <K, V> boolean replace(final K key, final V oldValue, final V newValue) {
		return execute(new GemfireCallback<Boolean>() {
			@SuppressWarnings({ "unchecked", "rawtypes" })
			public Boolean doInGemfire(Region region) throws GemFireCheckedException, GemFireException {
				return region.replace(key, oldValue, newValue);
			}
		});
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.gemfire.GemfireOperations#getAll(java.util.Collection)
	 */
	@Override
	public <K, V> Map<K, V> getAll(final Collection<?> keys) {
		return execute(new GemfireCallback<Map<K, V>>() {
			@SuppressWarnings({ "unchecked", "rawtypes" })
			public Map<K, V> doInGemfire(Region region) throws GemFireCheckedException, GemFireException {
				return (Map<K, V>) region.getAll(keys);
			}
		});
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.gemfire.GemfireOperations#putAll(java.util.Map)
	 */
	@Override
	public <K, V> void putAll(final Map<? extends K, ? extends V> map) {
		execute(new GemfireCallback<Object>() {
			@SuppressWarnings({ "unchecked", "rawtypes" })
			public Object doInGemfire(Region region) throws GemFireCheckedException, GemFireException {
				region.putAll(map);
				return null;
			}
		});
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.gemfire.GemfireOperations#query(java.lang.String)
	 */
	@Override
	public <E> SelectResults<E> query(final String query) {
		return execute(new GemfireCallback<SelectResults<E>>() {
			@SuppressWarnings({ "unchecked", "rawtypes" })
			public SelectResults<E> doInGemfire(Region region) throws GemFireCheckedException, GemFireException {
				return region.query(query);
			}
		});
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.gemfire.GemfireOperations#find(java.lang.String, java.lang.Object)
	 */
	@Override
	public <E> SelectResults<E> find(final String query, final Object... params)
			throws InvalidDataAccessApiUsageException {
		return execute(new GemfireCallback<SelectResults<E>>() {
			@SuppressWarnings({ "unchecked", "rawtypes" })
			public SelectResults<E> doInGemfire(Region region) throws GemFireCheckedException, GemFireException {
				QueryService queryService = lookupQueryService(region);
				Query q = queryService.newQuery(query);
 				Object result = q.execute(params);
 				if (result instanceof SelectResults) {
					return (SelectResults<E>) result;
				}
				throw new InvalidDataAccessApiUsageException(
						"Result object returned from GemfireCallback isn't a SelectResult: [" + result + "]");
			}
		});
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.gemfire.GemfireOperations#findUnique(java.lang.String, java.lang.Object)
	 */
	@Override
	public <T> T findUnique(final String query, final Object... params) throws InvalidDataAccessApiUsageException {
		return execute(new GemfireCallback<T>() {
			@SuppressWarnings({ "unchecked", "rawtypes" })
			public T doInGemfire(Region region) throws GemFireCheckedException, GemFireException {
				QueryService queryService = lookupQueryService(region);
				Query q = queryService.newQuery(query);
				Object result = q.execute(params);
				if (result instanceof SelectResults) {
					SelectResults<T> selectResults = (SelectResults<T>)result;
					if (selectResults.asList().size() == 1) {
						result = selectResults.iterator().next();
					} else {
						throw new InvalidDataAccessApiUsageException(
							"Result object returned from GemfireCallback isn't unique: [" + result + "]");
					}
				}
				return (T) result;
			}
		});
	}


	/**
	 * Returns the query service used by the template in its find methods.
	 * 
	 * @param region region to find the local query service from
	 * @return query service to use, local or generic
	 */
	protected QueryService lookupQueryService(Region<?, ?> region) {
		if (region.getRegionService() instanceof ClientCache
				&& (region instanceof LocalRegion && !((LocalRegion) region).hasServerProxy())
				&& Scope.LOCAL.equals(region.getAttributes().getScope())) {
			return ((ClientCache) region.getRegionService()).getLocalQueryService();
		}
		return region.getRegionService().getQueryService();
	}


	/* (non-Javadoc)
	 * @see org.springframework.data.gemfire.GemfireOperations#execute(org.springframework.data.gemfire.GemfireCallback)
	 */
	@Override
	public <T> T execute(GemfireCallback<T> action) throws DataAccessException {
		return execute(action, isExposeNativeRegion());
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.gemfire.GemfireOperations#execute(org.springframework.data.gemfire.GemfireCallback, boolean)
	 */
	@Override
	public <T> T execute(GemfireCallback<T> action, boolean exposeNativeRegion) throws DataAccessException {
		Assert.notNull(action, "Callback object must not be null");
		try {
			Region<?, ?> regionToExpose = (exposeNativeRegion ? getRegion() : regionProxy);
			T result = action.doInGemfire(regionToExpose);
			return result;
		} catch (IndexInvalidException ex) {
			throw convertGemFireQueryException(ex);
		} catch (QueryInvalidException ex) {
			throw convertGemFireQueryException(ex);
		} catch (GemFireCheckedException ex) {
			throw convertGemFireAccessException(ex);
		} catch (GemFireException ex) {
			throw convertGemFireAccessException(ex);
		} catch (RuntimeException ex) {
			// try first the CqInvalidException (removed in 6.5)
			if (GemfireCacheUtils.isCqInvalidException(ex)) {
				throw GemfireCacheUtils.convertCqInvalidException(ex);
			}
			// callback code threw application exception
			throw ex;
		}
	}

	/**
	 * Create a close-suppressing proxy for the given GemFire {@link Region}.
	 * Called by the <code>execute</code> method.
	 * 
	 * @param region the GemFire Region to create a proxy for
	 * @return the Region proxy, implementing all interfaces
	 * implemented by the passed-in Region object 
	 * @see Region#close()
	 * @see #execute(GemfireCallback, boolean)
	 */
	@SuppressWarnings("unchecked")
	protected <K, V> Region<K, V> createRegionProxy(Region<K, V> region) {
		Class<?>[] ifcs = ClassUtils.getAllInterfacesForClass(region.getClass(), getClass().getClassLoader());
		return (Region<K, V>) Proxy.newProxyInstance(region.getClass().getClassLoader(), ifcs,
				new CloseSuppressingInvocationHandler(region));
	}

	//-------------------------------------------------------------------------
	// Convenience methods for load, save, delete
	//-------------------------------------------------------------------------

	/**
	 * Invocation handler that suppresses close calls on GemFire Regions.
	 * @see Region#close()
	 */
	private static class CloseSuppressingInvocationHandler implements InvocationHandler {

		private final Region<?, ?> target;

		public CloseSuppressingInvocationHandler(Region<?, ?> target) {
			this.target = target;
		}

		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			// Invocation on Region interface coming in...

			if (method.getName().equals("equals")) {
				// Only consider equal when proxies are identical.
				return (proxy == args[0]);
			}
			else if (method.getName().equals("hashCode")) {
				// Use hashCode of region proxy.
				return System.identityHashCode(proxy);
			}
			else if (method.getName().equals("close")) {
				// Handle close method: suppress, not valid.
				return null;
			}

			// Invoke method on target Region
			try {
				Object retVal = method.invoke(this.target, args);
				return retVal;
			} catch (InvocationTargetException ex) {
				throw ex.getTargetException();
			}
		}
	}
}
