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

import org.springframework.core.annotation.AliasFor;

/**
 * {@link Annotation} defining the Partition {@link Region} in which the application persistent entity will be stored.
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
public @interface PartitionRegion {

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
	 * Sets the name of the {@link com.gemstone.gemfire.cache.Region} to which this persistent entity's
	 * {@link com.gemstone.gemfire.cache.Region} will be collocated.
	 *
	 * Collocation is used in data access, querying operations where the user wishes to combine data
	 * from multiple {@link com.gemstone.gemfire.cache.Region Regions} into a single result set returned
	 * from an OQL statement.
	 *
	 * Defaults to unset.
	 */
	String collocatedWith() default "";

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
	 * Defines an array of fixed partitions in a {@link com.gemstone.gemfire.cache.DataPolicy#PARTITION}
	 * {@link com.gemstone.gemfire.cache.Region}
	 *
	 * Default is unset.
	 *
	 * @see com.gemstone.gemfire.cache.FixedPartitionAttributes
	 */
	FixedPartition[] fixedPartitions() default {};

	/**
	 * Determines whether this {@link com.gemstone.gemfire.cache.Region Region's} data access operations participates in
	 * any existing, Global JTA transaction in progress.
	 *
	 * Defaults to {@literal false} (will NOT ignore JTA).
	 */
	boolean ignoreJta() default false;

	/**
	 * Name of the {@link com.gemstone.gemfire.cache.PartitionResolver} used to customize the partitioning strategy
	 * in this persistent entity's {@link com.gemstone.gemfire.cache.DataPolicy#PARTITION}
	 * {@link com.gemstone.gemfire.cache.Region}.
	 *
	 * This setting may also be the name of a Spring bean defined in the Spring context.
	 *
	 * Defaults to unset, thus using the default GemFire/Geode partitioning strategy.
	 */
	String partitionResolverName() default "";

	/**
	 * Determines whether this persistent entity's {@link com.gemstone.gemfire.cache.Region} is persistent,
	 * storing data to disk.
	 *
	 * Note, this setting independent of whether or not the {@link com.gemstone.gemfire.cache.Region} associated
	 * with this persistent entity overflows data to disk during eviction due to entry/heap/memory constraints.
	 *
	 * A {@link com.gemstone.gemfire.cache.Region} can also be persistent without an explicit
	 * {@link com.gemstone.gemfire.cache.DiskStore} defined; in that case, GemFire/Geode writes to the "DEFAULT"
	 * {@link com.gemstone.gemfire.cache.DiskStore}.
	 *
	 * Defaults to {@literal false}.
	 *
	 * @see com.gemstone.gemfire.cache.DataPolicy
	 */
	boolean persistent() default false;

	/**
	 * Defines the number of redundant copies of this persistent entity's data.
	 *
	 * Defaults to {@literal 0}.
	 */
	int redundantCopies() default 0;

	/**
	 * {@link FixedPartition} defined fixed partition meta-data within
	 * a {@link com.gemstone.gemfire.cache.DataPolicy#PARTITION} {@link com.gemstone.gemfire.cache.Region}.
	 *
	 * @see com.gemstone.gemfire.cache.FixedPartitionAttributes
	 */
	@interface FixedPartition {

		/**
		 * Name of the fixed partition.
		 */
		String name();

		/**
		 * Set whether this partition is the primary partition in
		 * the {@link com.gemstone.gemfire.cache.DataPolicy#PARTITION} {@link com.gemstone.gemfire.cache.Region}.
		 *
		 * Defaults to {@literal false}.
		 */
		boolean primary() default false;

		/**
		 * Defines the number of bucket in this partition.
		 *
		 * Defaults to {@literal 1}.
		 */
		int numBuckets() default 1;

	}
}
