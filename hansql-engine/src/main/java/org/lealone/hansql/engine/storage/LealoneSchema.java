/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lealone.hansql.engine.storage;

import java.util.Collections;
import java.util.List;

import org.lealone.hansql.exec.store.AbstractSchema;
import org.lealone.hansql.optimizer.schema.SchemaPlus;

public class LealoneSchema extends AbstractSchema {

    public LealoneSchema(List<String> parentSchemaPath, String name) {
        super(parentSchemaPath, name);
    }

    @Override
    public String getTypeName() {
        return null;
    }

    public void setHolder(SchemaPlus plusOfThis) {
        LealoneSchema schema = new LealoneSchema(Collections.emptyList(), getName());
        SchemaPlus hPlus = plusOfThis.add(getName(), schema);
        schema.setHolder(hPlus);
    }
}
