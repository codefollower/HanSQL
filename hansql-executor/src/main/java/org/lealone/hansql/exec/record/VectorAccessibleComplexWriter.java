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
package org.lealone.hansql.exec.record;

import org.lealone.hansql.exec.vector.complex.writer.BaseWriter.ComplexWriter;
import org.lealone.hansql.common.types.TypeProtos.MajorType;
import org.lealone.hansql.exec.vector.ValueVector;
import org.lealone.hansql.exec.vector.complex.MapVector;
import org.lealone.hansql.exec.vector.complex.impl.ComplexWriterImpl;

public class VectorAccessibleComplexWriter extends MapVector {
  private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(VectorAccessibleComplexWriter.class);

  private final VectorContainer vc;

  public VectorAccessibleComplexWriter(VectorContainer vc) {
    super("", null, null);
    this.vc = vc;
  }

  @Override
  public <T extends ValueVector> T addOrGet(String name, MajorType type, Class<T> clazz) {
    final ValueVector v = vc.addOrGet(name, type, clazz);
    putChild(name, v);
    return this.typeify(v, clazz);

  }

  public static ComplexWriter getWriter(String name, VectorContainer container) {
    VectorAccessibleComplexWriter vc = new VectorAccessibleComplexWriter(container);
    return new ComplexWriterImpl(name, vc);
  }
}
