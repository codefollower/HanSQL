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
package org.lealone.hansql.exec.vector.complex.impl;

import java.util.Map;

import org.lealone.hansql.exec.vector.complex.impl.AbstractFieldReader;
import org.lealone.hansql.exec.vector.complex.impl.NullReader;
import org.lealone.hansql.exec.vector.complex.impl.RepeatedMapWriter;
import org.lealone.hansql.exec.vector.complex.impl.SingleMapWriter;
import org.lealone.hansql.exec.vector.complex.writer.BaseWriter.MapWriter;

import org.apache.drill.shaded.guava.com.google.common.collect.Maps;
import org.lealone.hansql.common.types.TypeProtos.MajorType;
import org.lealone.hansql.exec.expr.holders.RepeatedMapHolder;
import org.lealone.hansql.exec.vector.ValueVector;
import org.lealone.hansql.exec.vector.complex.RepeatedMapVector;
import org.lealone.hansql.exec.vector.complex.reader.FieldReader;

@SuppressWarnings("unused")
public class RepeatedMapReaderImpl extends AbstractFieldReader{
  private static final int NO_VALUES = Integer.MAX_VALUE - 1;

  private final RepeatedMapVector vector;
  private final Map<String, FieldReader> fields = Maps.newHashMap();
  private int currentOffset;
  private int maxOffset;

  public RepeatedMapReaderImpl(RepeatedMapVector vector) {
    this.vector = vector;
  }

  @Override
  public FieldReader reader(String name) {
    FieldReader reader = fields.get(name);
    if (reader == null) {
      ValueVector child = vector.getChild(name);
      if (child == null) {
        reader = NullReader.INSTANCE;
      } else {
        reader = child.getReader();
      }
      fields.put(name, reader);
      reader.setPosition(currentOffset);
    }
    return reader;
  }

  @Override
  public FieldReader reader() {
    if (isNull()) {
      return NullReader.INSTANCE;
    }

    setChildrenPosition(currentOffset);
    return new SingleLikeRepeatedMapReaderImpl(vector, this);
  }

  @Override
  public void reset() {
    super.reset();
    currentOffset = 0;
    maxOffset = 0;
    for (FieldReader reader:fields.values()) {
      reader.reset();
    }
    fields.clear();
  }

  @Override
  public int size() {
    return isNull() ? 0 : maxOffset - currentOffset;
  }

  @Override
  public void setPosition(int index) {
    if (index < 0 || index == NO_VALUES) {
      currentOffset = NO_VALUES;
      return;
    }

    super.setPosition(index);
    RepeatedMapHolder h = new RepeatedMapHolder();
    vector.getAccessor().get(index, h);
    if (h.start == h.end) {
      currentOffset = NO_VALUES;
    } else {
      currentOffset = h.start - 1;
      maxOffset = h.end - 1;
      setChildrenPosition(currentOffset);
    }
  }

  public void setSinglePosition(int index, int childIndex) {
    super.setPosition(index);
    RepeatedMapHolder h = new RepeatedMapHolder();
    vector.getAccessor().get(index, h);
    if (h.start == h.end) {
      currentOffset = NO_VALUES;
    } else {
      int singleOffset = h.start + childIndex;
      assert singleOffset < h.end;
      currentOffset = singleOffset;
      maxOffset = singleOffset + 1;
      setChildrenPosition(singleOffset);
    }
  }

  @Override
  public boolean next() {
    if (currentOffset < maxOffset) {
      setChildrenPosition(++currentOffset);
      return true;
    } else {
      currentOffset = NO_VALUES;
      return false;
    }
  }

  public boolean isNull() {
    return currentOffset == NO_VALUES;
  }

  @Override
  public Object readObject() {
    return vector.getAccessor().getObject(idx());
  }

  @Override
  public MajorType getType() {
    return vector.getField().getType();
  }

  @Override
  public java.util.Iterator<String> iterator() {
    return vector.fieldNameIterator();
  }

  @Override
  public boolean isSet() {
    return true;
  }

  @Override
  public void copyAsValue(MapWriter writer) {
    if (isNull()) {
      return;
    }
    RepeatedMapWriter impl = (RepeatedMapWriter) writer;
    impl.container.copyFromSafe(idx(), impl.idx(), vector);
  }

  public void copyAsValueSingle(MapWriter writer) {
    if (isNull()) {
      return;
    }
    SingleMapWriter impl = (SingleMapWriter) writer;
    impl.container.copyFromSafe(currentOffset, impl.idx(), vector);
  }

  @Override
  public void copyAsField(String name, MapWriter writer) {
    if (isNull()) {
      return;
    }
    RepeatedMapWriter impl = (RepeatedMapWriter) writer.map(name);
    impl.container.copyFromSafe(idx(), impl.idx(), vector);
  }

  private void setChildrenPosition(int index) {
    for (FieldReader r : fields.values()) {
      r.setPosition(index);
    }
  }
}
