/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lealone.hansql.exec.store;

import java.io.IOException;

import org.lealone.hansql.optimizer.schema.SchemaPlus;

/**
 * StoragePlugins implements this interface to register the schemas they provide.
 */
public interface SchemaFactory {

  String DEFAULT_WS_NAME = "default";

  /**
   * Register the schemas provided by this SchemaFactory implementation under the given parent schema.
   *
   * @param schemaConfig Configuration for schema objects.
   * @param parent Reference to parent schema.
   * @throws IOException in case of error during schema registration
   */
  void registerSchemas(SchemaConfig schemaConfig, SchemaPlus parent) throws IOException;
}
