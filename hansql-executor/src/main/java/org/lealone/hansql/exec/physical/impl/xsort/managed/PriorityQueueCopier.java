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
package org.lealone.hansql.exec.physical.impl.xsort.managed;

import java.io.IOException;
import java.util.List;

import org.lealone.hansql.exec.compile.TemplateClassDefinition;
import org.lealone.hansql.exec.exception.SchemaChangeException;
import org.lealone.hansql.exec.memory.BufferAllocator;
import org.lealone.hansql.exec.record.VectorAccessible;

public interface PriorityQueueCopier extends AutoCloseable {
  public void setup(BufferAllocator allocator, VectorAccessible hyperBatch,
      List<BatchGroup> batchGroups, VectorAccessible outgoing) throws SchemaChangeException;

  public int next(int targetRecordCount);

  public final static TemplateClassDefinition<PriorityQueueCopier> TEMPLATE_DEFINITION =
      new TemplateClassDefinition<>(PriorityQueueCopier.class, PriorityQueueCopierTemplate.class);

  @Override
  abstract public void close() throws IOException; // specify this to leave out the Exception
}
