/*
 * Copyright 2011-2015 the original author or authors.
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
package com.github.geequery.springdata.repository.support;

import java.lang.reflect.Method;
import java.util.Map;

import javax.persistence.LockModeType;

/**
 * Interface to abstract {@link CrudMethodMetadata} that provide the {@link LockModeType} to be used for query
 * execution.
 */
public interface CrudMethodMetadata {
	/**
	 * Returns all query hints to be applied to queries executed for the CRUD method.
	 * 
	 * @return
	 */
	Map<String, Object> getQueryHints();

	/**
	 * Returns the {@link Method} to be used.
	 * 
	 * @return
	 * @since 1.9
	 */
	Method getMethod();
}
