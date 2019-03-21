/*
 * Copyright 2016-2019 the original author or authors.
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
 *
 */

package org.springframework.data.gemfire.mapping;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.gemstone.gemfire.cache.client.ClientRegionShortcut;
import com.gemstone.gemfire.cache.client.Pool;

import org.springframework.core.annotation.AliasFor;
import org.springframework.data.gemfire.config.xml.GemfireConstants;

/**
 * {@link Annotation} defining the Client {@link Region} in which the application persistent entity will be stored.
 *
 * @author John Blum
 * @see org.springframework.data.gemfire.mapping.Region
 * @since 1.9.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@Region
@SuppressWarnings("unused")
public @interface ClientRegion {

	/**
	 * Name, or fully-qualified bean name of the {@link com.gemstone.gemfire.cache.Region}
	 * in which the application persistent entity will be stored (e.g. "Users", or "/Local/Admin/Users").
	 *
	 * Defaults to simple name of the application persistent entity defined by {@link java.lang.Class#getSimpleName()}.
	 *
	 * @return the name or fully-qualified path of the {@link Region} in which the application persistent entity
	 * will be stored.
	 */
	@AliasFor(annotation = Region.class, attribute = "name")
	String name() default "";

	/**
	 * Name, or fully-qualified bean name of the {@link com.gemstone.gemfire.cache.Region}
	 * in which the application persistent entity will be stored (e.g. "Users", or "/Local/Admin/Users").
	 *
	 * Defaults to simple name of the application persistent entity defined by {@link java.lang.Class#getSimpleName()}.
	 *
	 * @return the name or fully-qualified path of the {@link Region} in which the application persistent entity
	 * will be stored.
	 */
	@AliasFor(annotation = Region.class, attribute = "value")
	String value() default "";

	/**
	 * Name of the {@link com.gemstone.gemfire.cache.DiskStore} in which this persistent entity's data is overflowed
	 * and/or persisted.
	 *
	 * Maybe the name of a Spring bean defined in the Spring context.
	 *
	 * Defaults to unset.
	 */
	String diskStoreName() default "";

	/**
	 * Determines whether disk-based operations (used in overflow and persistent) are synchronous or asynchronous.
	 *
	 * Defaults to {@literal synchronous}.
	 */
	boolean diskSynchronous() default true;

	/**
	 * Name of the GemFire/Geode {@link Pool} used by this persistent entity's {@link com.gemstone.gemfire.cache.Region}
	 * data access operations sent to the corresponding {@link com.gemstone.gemfire.cache.Region}
	 * on the GemFire/Geode Server.
	 *
	 * Defaults to {@literal gemfirePool}.
	 */
	String poolName() default GemfireConstants.DEFAULT_GEMFIRE_POOL_NAME;

	/**
	 * {@link ClientRegionShortcut} used by this persistent entity's client {@link com.gemstone.gemfire.cache.Region}
	 * to define the {@link com.gemstone.gemfire.cache.DataPolicy}.
	 *
	 * Defaults to {@link ClientRegionShortcut#PROXY}.
	 *
	 * @see com.gemstone.gemfire.cache.client.ClientRegionShortcut
	 */
	ClientRegionShortcut shortcut() default ClientRegionShortcut.PROXY;

}
