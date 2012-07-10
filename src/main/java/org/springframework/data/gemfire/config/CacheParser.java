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

package org.springframework.data.gemfire.config;

import java.util.List;

import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.support.ManagedMap;
import org.springframework.beans.factory.xml.AbstractSimpleBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.data.gemfire.CacheFactoryBean;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

import com.gemstone.gemfire.internal.datasource.ConfigProperty;

/**
 * Parser for &lt;cache;gt; definitions.
 * 
 * @author Costin Leau
 * @author Oliver Gierke
 * @author David Turanski
 */
class CacheParser extends AbstractSimpleBeanDefinitionParser {

	@Override
	protected Class<?> getBeanClass(Element element) {
		return CacheFactoryBean.class;
	}

	@Override
	protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
		super.doParse(element, builder);

		ParsingUtils.setPropertyValue(element, builder, "cache-xml-location", "cacheXml");
		ParsingUtils.setPropertyReference(element, builder, "properties-ref", "properties");
		ParsingUtils.setPropertyReference(element, builder, "pdx-serializer", "pdxSerializer");
		ParsingUtils.setPropertyValue(element, builder, "pdx-disk-store", "pdxDiskStoreName");
		ParsingUtils.setPropertyValue(element, builder, "pdx-persistent");
		ParsingUtils.setPropertyValue(element, builder, "pdx-read-serialized");
		ParsingUtils.setPropertyValue(element, builder, "pdx-ignore-unread-fields");
		ParsingUtils.setPropertyValue(element, builder, "use-bean-factory-locator");
		ParsingUtils.setPropertyValue(element, builder, "copy-on-read");
		ParsingUtils.setPropertyValue(element, builder, "lock-timeout");
		ParsingUtils.setPropertyValue(element, builder, "lock-lease");
		ParsingUtils.setPropertyValue(element, builder, "message-sync-interval");
		ParsingUtils.setPropertyValue(element, builder, "search-timeout");
		ParsingUtils.setPropertyValue(element, builder, "critical-heap-percentage");
		ParsingUtils.setPropertyValue(element, builder, "eviction-heap-percentage");

		List<Element> txListeners = DomUtils.getChildElementsByTagName(element, "transaction-listener");
		if (!CollectionUtils.isEmpty(txListeners)) {
			ManagedList<Object> transactionListeners = new ManagedList<Object>();
			for (Element txListener : txListeners) {
				transactionListeners.add(ParsingUtils.parseRefOrNestedBeanDeclaration(parserContext, txListener,
						builder));
			}
			builder.addPropertyValue("transactionListeners", transactionListeners);
		}
		Element txWriter = DomUtils.getChildElementByTagName(element, "transaction-writer");
		if (txWriter != null) {
			builder.addPropertyValue("transactionWriter",
					ParsingUtils.parseRefOrNestedBeanDeclaration(parserContext, txWriter, builder));
		}

		parseDynamicRegionFactory(element, builder);
		parseJndiBindings(element, builder);
	}

	private void parseDynamicRegionFactory(Element element, BeanDefinitionBuilder builder) {
		Element dynamicRegionFactory = DomUtils.getChildElementByTagName(element, "dynamic-region-factory");
		if (dynamicRegionFactory != null) {
			BeanDefinitionBuilder dynamicRegionSupport = buildDynamicRegionSupport(dynamicRegionFactory);
			postProcessDynamicRegionSupport(element, dynamicRegionSupport);
			builder.addPropertyValue("dynamicRegionSupport", dynamicRegionSupport.getBeanDefinition());
		}
	}

	/**
	 * @param dynamicRegionSupport BDB for &lt;dynamic-region-factory&gt;
	 * element
	 */
	protected void postProcessDynamicRegionSupport(Element element, BeanDefinitionBuilder dynamicRegionSupport) {

	}

	private BeanDefinitionBuilder buildDynamicRegionSupport(Element dynamicRegionFactory) {
		BeanDefinitionBuilder result = null;
		if (dynamicRegionFactory != null) {
			BeanDefinitionBuilder dynamicRegionSupport = BeanDefinitionBuilder
					.genericBeanDefinition(CacheFactoryBean.DynamicRegionSupport.class);
			String diskDir = dynamicRegionFactory.getAttribute("disk-dir");
			if (StringUtils.hasText(diskDir)) {
				dynamicRegionSupport.addPropertyValue("diskDir", diskDir);
			}
			String persistent = dynamicRegionFactory.getAttribute("persistent");
			if (StringUtils.hasText(persistent)) {
				dynamicRegionSupport.addPropertyValue("persistent", persistent);
			}

			String registerInterest = dynamicRegionFactory.getAttribute("register-interest");
			if (StringUtils.hasText(registerInterest)) {
				dynamicRegionSupport.addPropertyValue("registerInterest", registerInterest);
			}
			result = dynamicRegionSupport;
		}
		return result;
	}

	private void parseJndiBindings(Element element, BeanDefinitionBuilder builder) {
		List<Element> jndiBindings = DomUtils.getChildElementsByTagName(element, "jndi-binding");
		if (!CollectionUtils.isEmpty(jndiBindings)) {
			ManagedList<Object> jndiDataSources = new ManagedList<Object>();
			ManagedMap<String, String> jndiAttributes = new ManagedMap<String, String>();
			for (Element jndiBinding : jndiBindings) {
				BeanDefinitionBuilder jndiDataSource = BeanDefinitionBuilder
						.genericBeanDefinition(CacheFactoryBean.JndiDataSource.class);
				NamedNodeMap nnm = jndiBinding.getAttributes();
				for (int i = 0; i < nnm.getLength(); i++) {
					Attr attr = (Attr) nnm.item(i);
					jndiAttributes.put(attr.getLocalName(), attr.getValue());
				}
				jndiDataSource.addPropertyValue("attributes", jndiAttributes);

				List<Element> jndiProps = DomUtils.getChildElementsByTagName(element, "jndi-prop");
				if (!CollectionUtils.isEmpty(jndiProps)) {
					ManagedList<ConfigProperty> props = new ManagedList<ConfigProperty>();
					for (Element jndiProp : jndiProps) {
						String key = jndiProp.getAttribute("key");
						String value = jndiProp.getNodeValue();
						String type = StringUtils.hasText(jndiProp.getAttribute("type")) ? jndiProp
								.getAttribute("type") : String.class.getName();
						props.add(new ConfigProperty(key, value, type));
					}
					jndiDataSource.addPropertyValue("props", props);
				}
				jndiDataSources.add(jndiDataSource.getBeanDefinition());
			}
			builder.addPropertyValue("jndiDataSources", jndiDataSources);
		}
	}

	@Override
	protected String resolveId(Element element, AbstractBeanDefinition definition, ParserContext parserContext)
			throws BeanDefinitionStoreException {
		String name = super.resolveId(element, definition, parserContext);
		if (!StringUtils.hasText(name)) {
			name = "gemfire-cache";
		}
		return name;
	}

}