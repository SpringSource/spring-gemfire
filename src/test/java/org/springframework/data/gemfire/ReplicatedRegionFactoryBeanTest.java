/*
 * Copyright 2010-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.data.gemfire;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.Test;

import com.gemstone.gemfire.cache.DataPolicy;
import com.gemstone.gemfire.cache.RegionFactory;

/**
 * The ReplicatedRegionFactoryBeanTest class is a test suite of test cases testing the component functionality
 * and correct behavior of the ReplicatedRegionFactoryBean class.
 * <p/>
 * @author John Blum
 * @see org.mockito.Mockito
 * @see org.junit.Test
 * @see org.springframework.data.gemfire.ReplicatedRegionFactoryBean
 * @since 1.3.3
 */
@SuppressWarnings("unchecked")
public class ReplicatedRegionFactoryBeanTest {

	private final ReplicatedRegionFactoryBean factoryBean = new ReplicatedRegionFactoryBean();

	protected RegionFactory<?, ?> createMockRegionFactory() {
		return mock(RegionFactory.class);
	}

	@Test
	public void testResolveDataPolicyWithPersistentUnspecifiedAndDataPolicyUnspecified() {
		RegionFactory mockRegionFactory = createMockRegionFactory();
		factoryBean.resolveDataPolicy(mockRegionFactory, null, null);
		verify(mockRegionFactory).setDataPolicy(eq(DataPolicy.REPLICATE));
	}

	@Test
	public void testResolveDataPolicyWhenNotPersistentAndDataPolicyUnspecified() {
		RegionFactory mockRegionFactory = createMockRegionFactory();
		factoryBean.setPersistent(false);
		factoryBean.resolveDataPolicy(mockRegionFactory, false, null);
		verify(mockRegionFactory).setDataPolicy(eq(DataPolicy.REPLICATE));
	}

	@Test
	public void testResolveDataPolicyWhenPersistentAndDataPolicyUnspecified() {
		RegionFactory mockRegionFactory = createMockRegionFactory();
		factoryBean.setPersistent(true);
		factoryBean.resolveDataPolicy(mockRegionFactory, true, null);
		verify(mockRegionFactory).setDataPolicy(eq(DataPolicy.PERSISTENT_REPLICATE));
	}

	@Test
	public void testResolveDataPolicyWithPersistentUnspecifiedAndEmptyDataPolicy() {
		RegionFactory mockRegionFactory = createMockRegionFactory();
		factoryBean.resolveDataPolicy(mockRegionFactory, null, "EMPTY");
		verify(mockRegionFactory).setDataPolicy(eq(DataPolicy.EMPTY));
	}

	@Test
	public void testResolveDataPolicyWhenNotPersistentAndEmptyDataPolicy() {
		RegionFactory mockRegionFactory = createMockRegionFactory();
		factoryBean.setPersistent(false);
		factoryBean.resolveDataPolicy(mockRegionFactory, false, "empty");
		verify(mockRegionFactory).setDataPolicy(eq(DataPolicy.EMPTY));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testResolveDataPolicyWhenPersistentAndEmptyDataPolicy() {
		RegionFactory mockRegionFactory = createMockRegionFactory();
		try {
			factoryBean.setPersistent(true);
			factoryBean.resolveDataPolicy(mockRegionFactory, true, "empty");
		}
		catch (IllegalArgumentException e) {
			assertEquals("Data Policy 'EMPTY' is invalid when persistent is true.", e.getMessage());
			throw e;
		}
		finally {
			verify(mockRegionFactory, never()).setDataPolicy(eq(DataPolicy.EMPTY));
		}
	}

	@Test(expected = IllegalArgumentException.class)
	public void testResolveDataPolicyWithPersistentUnspecifiedAndInvalidDataPolicyName() {
		RegionFactory mockRegionFactory = createMockRegionFactory();

		try {
			factoryBean.resolveDataPolicy(mockRegionFactory, null, "INVALID_DATA_POLICY_NAME");
		}
		catch (IllegalArgumentException e) {
			assertEquals("Data Policy 'INVALID_DATA_POLICY_NAME' is invalid.", e.getMessage());
			throw e;
		}
		finally {
			verify(mockRegionFactory, never()).setDataPolicy(null);
			verify(mockRegionFactory, never()).setDataPolicy(eq(DataPolicy.REPLICATE));
			verify(mockRegionFactory, never()).setDataPolicy(eq(DataPolicy.PERSISTENT_REPLICATE));
		}
	}

	@Test(expected = IllegalArgumentException.class)
	public void testResolveDataPolicyWithPersistentUnspecifiedAndInvalidDataPolicyRegionType() {
		RegionFactory mockRegionFactory = createMockRegionFactory();

		try {
			factoryBean.resolveDataPolicy(mockRegionFactory, null, "PARTITION");
		}
		catch (IllegalArgumentException e) {
			assertEquals("Data Policy 'PARTITION' is not supported in Replicated Regions.", e.getMessage());
			throw e;
		}
		finally {
			verify(mockRegionFactory, never()).setDataPolicy(null);
			verify(mockRegionFactory, never()).setDataPolicy(eq(DataPolicy.PARTITION));
		}
	}

	@Test
	public void testResolveDataPolicyWhenPersistentUnspecifiedAndReplicatedDataPolicy() {
		RegionFactory mockRegionFactory = createMockRegionFactory();
		factoryBean.resolveDataPolicy(mockRegionFactory, null, "REPLICATE");
		verify(mockRegionFactory).setDataPolicy(eq(DataPolicy.REPLICATE));
	}

	@Test
	public void testResolveDataPolicyWhenNotPersistentAndReplicatedDataPolicy() {
		RegionFactory mockRegionFactory = createMockRegionFactory();
		factoryBean.setPersistent(false);
		factoryBean.resolveDataPolicy(mockRegionFactory, false, "REPLICATE");
		verify(mockRegionFactory).setDataPolicy(eq(DataPolicy.REPLICATE));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testResolveDataPolicyWhenPersistentAndReplicatedDataPolicy() {
		RegionFactory mockRegionFactory = createMockRegionFactory();

		try {
			factoryBean.setPersistent(true);
			factoryBean.resolveDataPolicy(mockRegionFactory, true, "REPLICATE");
		}
		catch (IllegalArgumentException e) {
			assertEquals("Data Policy 'REPLICATE' is invalid when persistent is true.", e.getMessage());
			throw e;
		}
		finally {
			verify(mockRegionFactory, never()).setDataPolicy(null);
			verify(mockRegionFactory, never()).setDataPolicy(eq(DataPolicy.REPLICATE));
		}
	}

	@Test
	public void testResolveDataPolicyWhenPersistentUnspecifiedAndPersistentReplicatedDataPolicy() {
		RegionFactory mockRegionFactory = createMockRegionFactory();
		factoryBean.resolveDataPolicy(mockRegionFactory, null, "PERSISTENT_REPLICATE");
		verify(mockRegionFactory).setDataPolicy(eq(DataPolicy.PERSISTENT_REPLICATE));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testResolveDataPolicyWhenNotPersistentAndPersistentReplicatedDataPolicy() {
		RegionFactory mockRegionFactory = createMockRegionFactory();

		try {
			factoryBean.setPersistent(false);
			factoryBean.resolveDataPolicy(mockRegionFactory, false, "PERSISTENT_REPLICATE");
		}
		catch (IllegalArgumentException e) {
			assertEquals("Data Policy 'PERSISTENT_REPLICATE' is invalid when persistent is false.", e.getMessage());
			throw e;
		}
		finally {
			verify(mockRegionFactory, never()).setDataPolicy(null);
			verify(mockRegionFactory, never()).setDataPolicy(eq(DataPolicy.REPLICATE));
		}
	}

	@Test
	public void testResolveDataPolicyWhenPersistentAndPersistentReplicatedDataPolicy() {
		RegionFactory mockRegionFactory = createMockRegionFactory();
		factoryBean.setPersistent(true);
		factoryBean.resolveDataPolicy(mockRegionFactory, true, "PERSISTENT_REPLICATE");
		verify(mockRegionFactory).setDataPolicy(eq(DataPolicy.PERSISTENT_REPLICATE));
	}

}
