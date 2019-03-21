/*
 * Copyright 2012-2019 the original author or authors.
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
package org.springframework.data.gemfire.mapping;

import static org.springframework.data.gemfire.mapping.MappingPdxSerializer.ExcludeAllTypesFilter.EXCLUDE_ALL_TYPES;
import static org.springframework.data.gemfire.mapping.MappingPdxSerializer.ExcludeComGemstoneGemFireTypesFilter.EXCLUDE_COM_GEMSTONE_GEMFIRE_TYPES;
import static org.springframework.data.gemfire.mapping.MappingPdxSerializer.ExcludeJavaTypesFilter.EXCLUDE_JAVA_TYPES;
import static org.springframework.data.gemfire.mapping.MappingPdxSerializer.ExcludeNullTypesFilter.EXCLUDE_NULL_TYPES;
import static org.springframework.data.gemfire.mapping.MappingPdxSerializer.ExcludeOrgSpringFrameworkTypesFilter.EXCLUDE_ORG_SPRING_FRAMEWORK_TYPES;

import java.util.Collections;
import java.util.Map;

import com.gemstone.gemfire.pdx.PdxReader;
import com.gemstone.gemfire.pdx.PdxSerializer;
import com.gemstone.gemfire.pdx.PdxWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.data.convert.EntityInstantiator;
import org.springframework.data.convert.EntityInstantiators;
import org.springframework.data.gemfire.util.Filter;
import org.springframework.data.mapping.PersistentEntity;
import org.springframework.data.mapping.PersistentProperty;
import org.springframework.data.mapping.PersistentPropertyAccessor;
import org.springframework.data.mapping.PropertyHandler;
import org.springframework.data.mapping.model.ConvertingPropertyAccessor;
import org.springframework.data.mapping.model.MappingException;
import org.springframework.data.mapping.model.PersistentEntityParameterValueProvider;
import org.springframework.data.mapping.model.SpELContext;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

/**
 * GemFire {@link PdxSerializer} implementation using the Spring Data GemFire {@link GemfireMappingContext}
 * to read and write entities from/to GemFire PDX bytes.
 *
 * @author Oliver Gierke
 * @author David Turanski
 * @author John Blum
 * @see com.gemstone.gemfire.pdx.PdxReader
 * @see com.gemstone.gemfire.pdx.PdxSerializer
 * @see com.gemstone.gemfire.pdx.PdxWriter
 * @see org.springframework.context.ApplicationContext
 * @see org.springframework.context.ApplicationContextAware
 * @see org.springframework.core.convert.ConversionService
 * @see org.springframework.data.convert.EntityInstantiator
 * @see org.springframework.data.mapping.PersistentEntity
 * @see org.springframework.data.mapping.PersistentProperty
 * @see org.springframework.data.mapping.PersistentPropertyAccessor
 * @see org.springframework.data.mapping.model.ConvertingPropertyAccessor
 * @see org.springframework.data.mapping.model.PersistentEntityParameterValueProvider
 * @since 1.2.0
 */
public class MappingPdxSerializer implements PdxSerializer, ApplicationContextAware {

	/**
	 * Factory method used to construct a new instance of {@link MappingPdxSerializer} initialized with
	 * a provided {@link GemfireMappingContext} and default {@link ConversionService}.
	 *
	 * @return a new instance of {@link MappingPdxSerializer}.
	 * @see #create(GemfireMappingContext, ConversionService)
	 * @see #newMappingContext()
	 * @see #newConversionService()
	 */
	public static MappingPdxSerializer newMappingPdxSerializer() {
		return create(newMappingContext(), newConversionService());
	}

	/**
	 * Factory method used to construct a new instance of {@link MappingPdxSerializer} initialized with
	 * the given {@link ConversionService} and a provided {@link GemfireMappingContext}.
	 *
	 * @param conversionService {@link ConversionService} used to convert persistent values to entity properties.
	 * @return a new instance of {@link MappingPdxSerializer} initialized with the given {@link ConversionService}.
	 * @see org.springframework.core.convert.ConversionService
	 * @see #create(GemfireMappingContext, ConversionService)
	 * @see #newMappingContext()
	 */
	public static MappingPdxSerializer create(ConversionService conversionService) {
		return create(newMappingContext(), conversionService);
	}

	/**
	 * Factory method used to construct a new instance of {@link MappingPdxSerializer} initialized with
	 * the given {@link GemfireMappingContext mapping context} supplying entity mapping meta-data,
	 * using a provided, default {@link ConversionService}.
	 *
	 * @param mappingContext {@link GemfireMappingContext} used to supply entity mapping meta-data.
	 * @return a new instance of {@link MappingPdxSerializer} initialized with
	 * the given {@link GemfireMappingContext mapping context}.
	 * @see org.springframework.data.gemfire.mapping.GemfireMappingContext
	 * @see #create(GemfireMappingContext, ConversionService)
	 * @see #newConversionService()
	 */
	public static MappingPdxSerializer create(GemfireMappingContext mappingContext) {
		return create(mappingContext, newConversionService());
	}

	/**
	 * Factory method used to construct a new instance of {@link MappingPdxSerializer} initialized with
	 * the given {@link GemfireMappingContext mapping context} and {@link ConversionService conversion service}.
	 *
	 * If either the {@link GemfireMappingContext mapping context} or the {@link ConversionService conversion service}
	 * are {@literal null}, then this factory method will provide default instances for each.
	 *
	 * @param mappingContext {@link GemfireMappingContext} used to map between application domain model object types
	 * and PDX serialized bytes based on the entity's mapping meta-data.
	 * @param conversionService {@link ConversionService} used to convert persistent values to entity properties.
	 * @return an initialized instance of the {@link MappingPdxSerializer}.
	 * @see org.springframework.core.convert.ConversionService
	 * @see org.springframework.data.gemfire.mapping.MappingPdxSerializer
	 */
	public static MappingPdxSerializer create(GemfireMappingContext mappingContext,
			ConversionService conversionService) {

		return new MappingPdxSerializer(
			resolveMappingContext(mappingContext),
			resolveConversionService(conversionService)
		);
	}

	/**
	 * Constructs a new {@link ConversionService}.
	 *
	 * @return a new {@link ConversionService}.
	 * @see org.springframework.core.convert.ConversionService
	 */
	private static ConversionService newConversionService() {
		return new DefaultConversionService();
	}

	/**
	 * Resolves the {@link ConversionService} used for conversions.
	 *
	 * @param conversionService {@link ConversionService} to evaluate.
	 * @return the given {@link ConversionService} if not {@literal null} or a new {@link ConversionService}.
	 * @see org.springframework.core.convert.ConversionService
	 * @see #newConversionService()
	 */
	private static ConversionService resolveConversionService(ConversionService conversionService) {
		return conversionService != null ? conversionService : newConversionService();
	}

	/**
	 * Constructs a new {@link GemfireMappingContext}.
	 *
	 * @return a new {@link GemfireMappingContext}.
	 * @see org.springframework.data.gemfire.mapping.GemfireMappingContext
	 */
	private static GemfireMappingContext newMappingContext() {
		return new GemfireMappingContext();
	}

	/**
	 * Resolves the {@link GemfireMappingContext mapping context} used to provide mapping meta-data.
	 *
	 * @param mappingContext {@link GemfireMappingContext} to evaluate.
	 * @return the given {@link GemfireMappingContext mapping context} if not {@literal null}
	 * or a new {@link GemfireMappingContext mapping context}.
	 * @see org.springframework.data.gemfire.mapping.GemfireMappingContext
	 * @see #newMappingContext()
	 */
	private static GemfireMappingContext resolveMappingContext(GemfireMappingContext mappingContext) {
		return mappingContext != null ? mappingContext : newMappingContext();
	}

	private final ConversionService conversionService;

	private EntityInstantiators entityInstantiators;

	private Filter<Class<?>> excludeTypeFilters = EXCLUDE_NULL_TYPES
		.and(EXCLUDE_COM_GEMSTONE_GEMFIRE_TYPES)
		.and(EXCLUDE_JAVA_TYPES)
		.and(EXCLUDE_ORG_SPRING_FRAMEWORK_TYPES);

	private Filter<Class<?>> includeTypeFilters = EXCLUDE_ALL_TYPES;

	private final GemfireMappingContext mappingContext;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private Map<?, PdxSerializer> customPdxSerializers;

	// TODO: remove? SpELContext is not used
	private SpELContext spelContext;

	/**
	 * Constructs a new instance of {@link MappingPdxSerializer} using a default {@link GemfireMappingContext}
	 * and {@link DefaultConversionService}.
	 *
	 * @see #newConversionService()
	 * @see #newMappingContext()
	 * @see org.springframework.core.convert.support.DefaultConversionService
	 * @see org.springframework.data.gemfire.mapping.GemfireMappingContext
	 */
	public MappingPdxSerializer() {
		this(newMappingContext(), newConversionService());
	}

	/**
	 * Constructs a new instance of {@link MappingPdxSerializer} initialized with the given
	 * {@link GemfireMappingContext} and {@link ConversionService}.
	 *
	 * @param mappingContext {@link GemfireMappingContext} used by the {@link MappingPdxSerializer} to map
	 * between application domain object types and PDX serialized bytes based on the entity mapping meta-data.
	 * @param conversionService {@link ConversionService} used by the {@link MappingPdxSerializer} to convert
	 * PDX serialized data to application object property types.
	 * @throws IllegalArgumentException if either the {@link GemfireMappingContext} or the {@link ConversionService}
	 * is {@literal null}.
	 */
	public MappingPdxSerializer(GemfireMappingContext mappingContext, ConversionService conversionService) {

		Assert.notNull(mappingContext, "MappingContext must not be null");
		Assert.notNull(conversionService, "ConversionService must not be null");

		this.mappingContext = mappingContext;
		this.conversionService = conversionService;
		this.entityInstantiators = new EntityInstantiators();
		this.customPdxSerializers = Collections.emptyMap();
		this.spelContext = new SpELContext(PdxReaderPropertyAccessor.INSTANCE);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.spelContext = new SpELContext(this.spelContext, applicationContext);
	}

	/**
	 * Returns a reference to the configured {@link ConversionService} used to convert data store types
	 * to application domain object types.
	 *
	 * @return a reference to the configured {@link ConversionService}.
	 * @see org.springframework.core.convert.ConversionService
	 */
	protected ConversionService getConversionService() {
		return this.conversionService;
	}

	/**
	 * Configures custom {@link PdxSerializer PDX serializers} used to customize the serialization for specific
	 * application {@link Class domain types}.
	 *
	 * @param customPdxSerializers {@link Map mapping} containing custom {@link PdxSerializer PDX serializers}
	 * used to customize the serialization of specific application {@link Class domain types}.
	 * @throws IllegalArgumentException if the {@link Map custom PDX serializer mapping} is {@literal null}.
	 * @see com.gemstone.gemfire.pdx.PdxSerializer
	 * @see java.util.Map
	 */
	public void setCustomPdxSerializers(Map<?, PdxSerializer> customPdxSerializers) {

		Assert.notNull(customPdxSerializers, "Custom PdxSerializers must not be null");

		this.customPdxSerializers = customPdxSerializers;
	}

	/**
	 * @deprecated please use ({@link #setCustomPdxSerializers(Map)} instead.
	 */
	@Deprecated
	public void setCustomSerializers(Map<Class<?>, PdxSerializer> customSerializers) {
		setCustomPdxSerializers(customSerializers);
	}

	/**
	 * Returns a {@link Map mapping} of application {@link Class domain types} to custom
	 * {@link PdxSerializer PDX serializers} used to customize the serialization
	 * for specific application {@link Class domain types}.
	 *
	 * @return a {@link Map mapping} of application {@link Class domain types}
	 * to custom {@link PdxSerializer PDX serializers}.
	 * @see com.gemstone.gemfire.pdx.PdxSerializer
	 * @see java.util.Map
	 */
	protected Map<?, PdxSerializer> getCustomPdxSerializers() {
		return Collections.unmodifiableMap(this.customPdxSerializers);
	}

	/**
	 * @deprecated please use {@link #getCustomPdxSerializers()} instead.
	 */
	@Deprecated
	@SuppressWarnings("unchecked")
	protected Map<Class<?>, PdxSerializer> getCustomSerializers() {
		return (Map<Class<?>, PdxSerializer>) getCustomPdxSerializers();
	}

	/**
	 * Returns a custom PDX serializer for the given {@link PersistentProperty entity persistent property}.
	 *
	 * @param property {@link PersistentProperty} of the entity used to lookup the custom PDX serializer.
	 * @return a custom {@link PdxSerializer} for the given entity {@link PersistentProperty},
	 * or {@literal null} if no custom {@link PdxSerializer} could be found.
	 * @see com.gemstone.gemfire.pdx.PdxSerializer
	 */
	protected PdxSerializer getCustomPdxSerializer(PersistentProperty<?> property) {

		Map<?, PdxSerializer> customPdxSerializers = getCustomPdxSerializers();

		PdxSerializer customPdxSerializer = customPdxSerializers.get(property);

		customPdxSerializer = customPdxSerializer != null ? customPdxSerializer
			: customPdxSerializers.get(toFullyQualifiedPropertyName(property));

		customPdxSerializer = customPdxSerializer != null ? customPdxSerializer
			: customPdxSerializers.get(property.getType());

		return customPdxSerializer;
	}

	/**
	 * Converts the entity {@link PersistentProperty} to a {@link String fully-qualified property name}.
	 *
	 * @param property {@link PersistentProperty} of the entity.
	 * @return the {@link String fully-qualified property name of the entity {@link PersistentProperty}.
	 * @see org.springframework.data.mapping.PersistentProperty
	 */
	String toFullyQualifiedPropertyName(PersistentProperty<?> property) {
		return property.getOwner().getType().getName().concat(".").concat(property.getName());
	}

	/**
	 * @deprecated please use {@link #getCustomPdxSerializer(PersistentProperty)} instead.
	 */
	@Deprecated
	protected PdxSerializer getCustomSerializer(Class<?> type) {
		return getCustomPdxSerializers().get(type);
	}

	/**
	 * Configures the {@link EntityInstantiator}s used to create the instances read by this PdxSerializer.
	 *
	 * @param entityInstantiators {@link EntityInstantiator EntityInstantiators} used to create the instances
	 * read by this {@link PdxSerializer}; must not be {@literal null}.
	 * @see org.springframework.data.convert.EntityInstantiator
	 */
	public void setGemfireInstantiators(EntityInstantiators entityInstantiators) {

		Assert.notNull(entityInstantiators, "EntityInstantiators must not be null");

		this.entityInstantiators = entityInstantiators;
	}

	/**
	 * Configures the {@link EntityInstantiator EntityInstantiators} used to create the instances
	 * read by this {@link PdxSerializer}.
	 *
	 * @param gemfireInstantiators mapping of {@link Class types} to {@link EntityInstantiator} objects;
	 * must not be {@literal null}.
	 * @see org.springframework.data.convert.EntityInstantiator
	 * @see java.util.Map
>>>>>>> 3ed1599... SGF-745 - Add ability to filter types de/serialized by the o.s.d.g.mapping.MappingPdxSerializer.
	 */
	public void setGemfireInstantiators(Map<Class<?>, EntityInstantiator> gemfireInstantiators) {

		Assert.notNull(gemfireInstantiators, "GemFire EntityInstantiators are required");

		this.entityInstantiators = new EntityInstantiators(gemfireInstantiators);
	}

	protected EntityInstantiators getGemfireInstantiators() {
		return this.entityInstantiators;
	}

	/**
	 * Looks up and returns an EntityInstantiator to construct and initialize an instance of the object defined
	 * by the given PersistentEntity (meta-data).
	 *
	 * @param entity the PersistentEntity object used to lookup the custom EntityInstantiator.
	 * @return an EntityInstantiator for the given PersistentEntity.
	 * @see org.springframework.data.convert.EntityInstantiator
	 * @see org.springframework.data.mapping.PersistentEntity
	 */
	protected EntityInstantiator getInstantiatorFor(PersistentEntity entity) {
		return getGemfireInstantiators().getInstantiatorFor(entity);
	}

	/**
	 * Returns a reference to the configured {@link Logger} used to log {@link String messages}
	 * about the functions of this {@link PdxSerializer}.
	 *
	 * @return a reference to the configured {@link Logger}.
	 * @see org.apache.commons.logging.Log
	 */
	protected Logger getLogger() {
		return this.logger;
	}

	/**
	 * Returns a reference to the configured {@link GemfireMappingContext mapping context} used to handling mapping
	 * logic between GemFire persistent entities and application domain object {@link Class types}.
	 *
	 * @return a reference to the configured {@link GemfireMappingContext mapping context} for Pivotal GemFire.
	 * @see org.springframework.data.gemfire.mapping.GemfireMappingContext
	 */
	protected GemfireMappingContext getMappingContext() {
		return this.mappingContext;
	}

	/**
	 * Looks up and returns the {@link PersistentEntity} meta-data for the given entity object.
	 *
	 * @param entity actual persistent entity, application domain object.
	 * @return the {@link PersistentEntity} meta-data for the given entity object.
	 * @see org.springframework.data.gemfire.mapping.GemfirePersistentEntity
	 * @see #getPersistentEntity(Class)
	 */
	protected GemfirePersistentEntity<?> getPersistentEntity(Object entity) {
		return getPersistentEntity(entity.getClass());
	}

	/**
	 * Looks up and returns the {@link PersistentEntity} meta-data for the given entity {@link Class} type.
	 *
	 * @param entityType {@link Class} type of the actual persistent entity, application domain object {@link Class}.
	 * @return the {@link PersistentEntity} meta-data for the given entity {@link Class} type.
	 * @see org.springframework.data.gemfire.mapping.GemfirePersistentEntity
	 * @see #getMappingContext()
	 */
	protected GemfirePersistentEntity<?> getPersistentEntity(Class<?> entityType) {
		return getMappingContext().getPersistentEntity(entityType);
	}

	/**
	 * Sets the {@link Filter type filters} used to exclude or filter {@link Class types} serializable
	 * by this {@link MappingPdxSerializer PDX serializer}.
	 *
	 * This operation is null-safe and rather than overriding the existing {@link Filter type filters},
	 * this set operation combines the given {@link Filter type filters} with
	 * the exiting {@link Filter type filters} joined by {@literal and}.
	 *
	 * @param typeFilters {@link Filter type filters} used to to exclude {@link Class types} serializable
	 * by this {@link MappingPdxSerializer PDX serializer}.
	 * @see org.springframework.data.gemfire.util.Filter
	 */
	public void setExcludeTypeFilters(Filter<Class<?>> typeFilters) {

		this.excludeTypeFilters = typeFilters != null
			? this.excludeTypeFilters.and(typeFilters)
			: this.excludeTypeFilters;
	}
	/**
	 * Sets the {@link Filter type filters} used to include or filter {@link Class types} serializable
	 * by this {@link MappingPdxSerializer PDX serializer}.
	 *
	 * This operation is null-safe and rather than overriding the existing {@link Filter type filters},
	 * this set operation combines the given {@link Filter type filters} with
	 * the exiting {@link Filter type filters} joined by {@literal or}.
	 *
	 * @param typeFilters {@link Filter type filters} used to to include {@link Class types} serializable
	 * by this {@link MappingPdxSerializer PDX serializer}.
	 * @see org.springframework.data.gemfire.util.Filter
	 */
	public void setIncludeTypeFilters(Filter<Class<?>> typeFilters) {

		this.includeTypeFilters = typeFilters != null
			? this.includeTypeFilters.or(typeFilters)
			: this.includeTypeFilters;
	}

	/**
	 * Returns the {@link Filter type filters} used to filter {@link Class types} serializable
	 * by this {@link MappingPdxSerializer PdxSerializer}.
	 *
	 * @return the resolved {@link Filter type filter}.
	 * @see org.springframework.data.gemfire.util.Filter
	 */
	protected Filter<Class<?>> getTypeFilters() {
		return this.excludeTypeFilters.or(EXCLUDE_NULL_TYPES.and(this.includeTypeFilters));
	}

	@Override
	public Object fromData(Class<?> type, PdxReader reader) {
		return getTypeFilters().accept(type) ? doFromData(type, reader) : null;
	}

	/**
	 * Converts a set of PDX serialized bytes to an {@link Object} of the specified {@link Class type}.
	 *
	 * @param type desired {@link Class type} of the {@link Object}.
	 * @param reader {@link PdxReader} used to access the PDX bytes to convert.
	 * @return an {@link Object} of the specified {@link Class type} converted from the PDX bytes.
	 * @see com.gemstone.gemfire.pdx.PdxReader
	 * @see java.lang.Object
	 * @see java.lang.Class
	 */
	Object doFromData(final Class<?> type, final PdxReader reader) {

		final GemfirePersistentEntity<?> entity = getPersistentEntity(type);

		final Object instance = getInstantiatorFor(entity)
			.createInstance(entity, new PersistentEntityParameterValueProvider<GemfirePersistentProperty>(entity,
				new GemfirePropertyValueProvider(reader), null));

		final PersistentPropertyAccessor propertyAccessor =
			new ConvertingPropertyAccessor(entity.getPropertyAccessor(instance), getConversionService());

		entity.doWithProperties(new PropertyHandler<GemfirePersistentProperty>() {

			@Override
			public void doWithPersistentProperty(GemfirePersistentProperty persistentProperty) {

				if (isWritable(entity, persistentProperty)) {

					PdxSerializer customPdxSerializer = getCustomPdxSerializer(persistentProperty);

					Object value = null;

					try {
						if (getLogger().isDebugEnabled()) {
							getLogger().debug(String.format("Setting property [%1$s] for entity [%2$s] of type [%3$s] from PDX%4$s",
								persistentProperty.getName(), instance, type, (customPdxSerializer != null ?
									String.format(" using custom PdxSerializer [%1$s]", customPdxSerializer) : "")));
						}

						value = (customPdxSerializer != null
							? customPdxSerializer.fromData(persistentProperty.getType(), reader)
							: reader.readField(persistentProperty.getName()));

						propertyAccessor.setProperty(persistentProperty, value);
					}
					catch (Exception cause) {
						throw new MappingException(String.format(
							"While setting value [%1$s] of property [%2$s] for entity of type [%3$s] from PDX%4$s",
							value, persistentProperty.getName(), type, (customPdxSerializer != null ?
								String.format(" using custom PdxSerializer [%1$s]", customPdxSerializer) : "")), cause);
					}
				}
			}
		});

		return propertyAccessor.getBean();
	}

	/**
	 * Determines whether the {@link PersistentProperty persistent property}
	 * of the given {@link PersistentEntity entity } is writable.
	 *
	 * The {@link PersistentProperty persistent property} is considered {@literal writable} if the property
	 * is not a constructor parameter of the {@link PersistentEntity entity's} {@link Class type}, the property
	 * has a {@literal setter} method and the property is not {@literal transient}.
	 *
	 * @param entity {@link GemfirePersistentEntity} containing the {@link GemfirePersistentProperty property}.
	 * @param persistentProperty {@link GemfirePersistentProperty} to evaluate.
	 * @return a boolean value indicating whether the {@link PersistentProperty persistent property}
	 * of the given {@link PersistentEntity entity } is writable.
	 * @see org.springframework.data.gemfire.mapping.GemfirePersistentEntity
	 * @see org.springframework.data.gemfire.mapping.GemfirePersistentProperty
	 */
	boolean isWritable(GemfirePersistentEntity<?> entity, GemfirePersistentProperty persistentProperty) {

		return !entity.isConstructorArgument(persistentProperty)
			&& persistentProperty.isWritable()
			&& !persistentProperty.isTransient();
	}

	@Override
	public boolean toData(Object value, PdxWriter writer) {
		return getTypeFilters().accept(resolveType(value)) && doToData(value, writer);
	}

	/**
	 * Converts the given {@link Object} into a stream of PDX bytes.
	 *
	 * @param value {@link Object} to convert.
	 * @param writer {@link PdxWriter} used to stream the given {@link Object} into a stream of PDX bytes.
	 * @return a boolean value indicating whether this {@link MappingPdxSerializer PDX serializer} was able to
	 * write the given {@link Object} as a stream of PDX bytes.
	 * @see com.gemstone.gemfire.pdx.PdxWriter
	 * @see java.lang.Object
	 */
	@SuppressWarnings("unchecked")
	boolean doToData(Object value, final PdxWriter writer) {

		final GemfirePersistentEntity<?> entity = getPersistentEntity(value);

		// Entity will be null for simple types (e.g. int, Long, String, etc).
		if (entity != null) {

			final PersistentPropertyAccessor propertyAccessor =
				new ConvertingPropertyAccessor(entity.getPropertyAccessor(value), getConversionService());

			entity.doWithProperties(new PropertyHandler<GemfirePersistentProperty>() {

				@Override
				public void doWithPersistentProperty(GemfirePersistentProperty persistentProperty) {

					if (isReadable(persistentProperty)) {

						PdxSerializer customPdxSerializer = getCustomPdxSerializer(persistentProperty);

						Object propertyValue = null;

						try {

							propertyValue = propertyAccessor.getProperty(persistentProperty);

							if (getLogger().isDebugEnabled()) {
								getLogger().debug(String.format("Serializing entity [%1$s] property [%2$s] value [%3$s] of type [%4$s] to PDX%5$s",
									entity.getType().getName(), persistentProperty.getName(), propertyValue,
									ObjectUtils.nullSafeClassName(propertyValue), (customPdxSerializer != null
										? String.format(" using custom PdxSerializer [%s]", customPdxSerializer) : "")));
							}

							if (customPdxSerializer != null) {
								customPdxSerializer.toData(propertyValue, writer);
							}
							else {
								writer.writeField(persistentProperty.getName(), propertyValue,
									(Class<Object>) persistentProperty.getType());
							}
						}
						catch (Exception cause) {
							throw new MappingException(String.format(
								"While serializing entity [%1$s] property [%2$s] value [%3$s] of type [%4$s] to PDX%5$s",
								entity.getType().getName(), persistentProperty.getName(), propertyValue,
								ObjectUtils.nullSafeClassName(propertyValue), (customPdxSerializer != null
									? String.format(" using custom PdxSerializer [%1$s].",
									customPdxSerializer.getClass().getName()) : "")), cause);
						}
					}
				}
			});

			GemfirePersistentProperty idProperty = entity.getIdProperty();

			if (idProperty != null) {
				writer.markIdentityField(idProperty.getName());
			}

			return true;
		}
		return false;
	}

	/**
	 * Determines whether the given {@link PersistentProperty persistent property} is readable.
	 *
	 * The {@link PersistentProperty persistent property} is considered {@literal readable}
	 * if the property is not {@literal transient}.
	 *
	 * @param persistentProperty {@link GemfirePersistentProperty} to evaluate.
	 * @return a boolean value indicating whether the {@link PersistentProperty persistent property}
	 * is readable.
	 * @see org.springframework.data.gemfire.mapping.GemfirePersistentProperty
	 */
	boolean isReadable(GemfirePersistentProperty persistentProperty) {
		return !persistentProperty.isTransient();
	}

	/**
	 * Resolves the {@link Class type} of the given {@link Object}.
	 *
	 * @param obj {@link Object} to evaluate.
	 * @return the {@link Class type} of the given {@link Object}.
	 * @see java.lang.Object#getClass()
	 * @see java.lang.Class
	 */
	Class<?> resolveType(Object obj) {
		return obj != null ? obj.getClass() : null;
	}

	public static class ExcludeAllTypesFilter extends org.springframework.data.gemfire.util.AbstractFilter<Class<?>> {

		public static final Filter<Class<?>> EXCLUDE_ALL_TYPES = new ExcludeAllTypesFilter();

		@Override
		public boolean accept(Class<?> obj) {
			return false;
		}
	}

	public static class ExcludeComGemstoneGemFireTypesFilter extends org.springframework.data.gemfire.util.AbstractFilter<Class<?>> {

		public static final Filter<Class<?>> EXCLUDE_COM_GEMSTONE_GEMFIRE_TYPES =
			new ExcludeComGemstoneGemFireTypesFilter();

		protected static final String COM_GEMSTONE_GEMFIRE_PACKAGE_NAME = "com.gemstone.gemfire";

		@Override
		public boolean accept(Class<?> type) {
			return type != null && !type.getPackage().getName().startsWith(COM_GEMSTONE_GEMFIRE_PACKAGE_NAME);
		}
	}

	public static class ExcludeJavaTypesFilter extends org.springframework.data.gemfire.util.AbstractFilter<Class<?>> {

		public static final Filter<Class<?>> EXCLUDE_JAVA_TYPES = new ExcludeJavaTypesFilter();

		protected static final String JAVA_PACKAGE_NAME = "java";

		@Override
		public boolean accept(Class<?> type) {
			return type != null && !type.getPackage().getName().startsWith(JAVA_PACKAGE_NAME);
		}
	}

	public static class ExcludeNullTypesFilter extends org.springframework.data.gemfire.util.AbstractFilter<Class<?>> {

		public static final Filter<Class<?>> EXCLUDE_NULL_TYPES = new ExcludeNullTypesFilter();

		@Override
		public boolean accept(Class<?> type) {
			return type != null;
		}
	}

	public static class ExcludeOrgSpringFrameworkTypesFilter
			extends org.springframework.data.gemfire.util.AbstractFilter<Class<?>> {

		public static final Filter<Class<?>> EXCLUDE_ORG_SPRING_FRAMEWORK_TYPES =
			new ExcludeOrgSpringFrameworkTypesFilter();

		protected static final String ORG_SPRING_FRAMEWORK_PACKAGE_NAME = "org.springframework";

		@Override
		public boolean accept(Class<?> type) {
			return type != null && !type.getPackage().getName().startsWith(ORG_SPRING_FRAMEWORK_PACKAGE_NAME);
		}
	}
}
