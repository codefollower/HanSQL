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
package org.lealone.hansql.exec.vector;

import org.lealone.hansql.exec.vector.complex.impl.AbstractFieldReader;
import org.lealone.hansql.common.types.TypeProtos;

public class UntypedHolderReaderImpl extends AbstractFieldReader {

  private final UntypedNullHolder holder;

  public UntypedHolderReaderImpl(UntypedNullHolder holder) {
    this.holder = holder;
  }

  @Override
  public int size() {
    return 0;
  }

  @Override
  public boolean next() {
    return false;
  }

  @Override
  public TypeProtos.MajorType getType() {
    return holder.getType();
  }

  @Override
  public boolean isSet() {
    return false;
  }
}
