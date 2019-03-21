/*
 * Copyright 2018-2019 the original author or authors.
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

package org.springframework.data.gemfire.util;

/**
 * The {@link AbstractFilter} class is an abstract base class encapsulating functionality
 * common to all {@link Filter} implementations.
 *
 * @author John Blum
 * @see org.springframework.data.gemfire.util.Filter
 * @since 1.9.0
 */
public abstract class AbstractFilter<T> implements Filter<T> {

	/**
	 * Combines this {@link Filter} with the given {@link Filter} using {@literal logical AND}.
	 *
	 * @param filter {@link Filter} to compose with this {@link Filter}.
	 * @return a new {@link Filter} composed of this {@link Filter} and the given {@link Filter}
	 * using {@literal logical AND}.
	 * @see org.springframework.data.gemfire.util.Filter
	 */
	@Override
	public Filter<T> and(final Filter<T> filter) {

		return new AbstractFilter<T>() {

			@Override
			public boolean accept(T obj) {
				return AbstractFilter.this.accept(obj) && filter.accept(obj);
			}
		};
	}

	/**
	 * Negates the result of the {@link #accept(Object)} method.
	 *
	 * @return a new {@link Filter} negating the results of the {@link #accept(Object)} method.
	 * @see org.springframework.data.gemfire.util.Filter
	 */
	@Override
	public Filter<T> negate() {

		return new AbstractFilter<T>() {

			@Override
			public boolean accept(T obj) {
				return !AbstractFilter.this.accept(obj);
			}
		};
	}

	/**
	 * Combines this {@link Filter} with the given {@link Filter} using {@literal logical OR}.
	 *
	 * @param filter {@link Filter} to compose with this {@link Filter}.
	 * @return a new {@link Filter} composed of this {@link Filter} and the given {@link Filter}
	 * using {@literal logical OR}.
	 * @see org.springframework.data.gemfire.util.Filter
	 */
	@Override
	public Filter<T> or(final Filter<T> filter) {

		return new AbstractFilter<T>() {

			@Override
			public boolean accept(T obj) {
				return AbstractFilter.this.accept(obj) || filter.accept(obj);
			}
		};
	}
}
