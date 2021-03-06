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
package org.lealone.hansql.exec.vector.accessor.reader;

import org.lealone.hansql.exec.record.metadata.ColumnMetadata;
import org.lealone.hansql.exec.vector.BaseDataValueVector;
import org.lealone.hansql.exec.vector.accessor.ColumnReaderIndex;

import io.netty.buffer.DrillBuf;

/**
 * Column reader implementation that acts as the basis for the
 * generated, vector-specific implementations. All set methods
 * throw an exception; subclasses simply override the supported
 * method(s).
 */

public abstract class BaseScalarReader extends AbstractScalarReader {

  public abstract static class BaseFixedWidthReader extends BaseScalarReader {

    public abstract int width();
  }

  public abstract static class BaseVarWidthReader extends BaseScalarReader {

    protected OffsetVectorReader offsetsReader;

    @Override
    public void bindVector(ColumnMetadata schema, VectorAccessor va) {
      super.bindVector(schema, va);
      offsetsReader = new OffsetVectorReader(
          VectorAccessors.varWidthOffsetVectorAccessor(va));
    }

    @Override
    public void bindIndex(ColumnReaderIndex index) {
      super.bindIndex(index);
      offsetsReader.bindIndex(index);
    }
  }

  /**
   * Provide access to the DrillBuf for the data vector.
   */

  public interface BufferAccessor {
    DrillBuf buffer();
  }

  private static class SingleVectorBufferAccessor implements BufferAccessor {
    private final DrillBuf buffer;

    public SingleVectorBufferAccessor(VectorAccessor va) {
      BaseDataValueVector vector = va.vector();
      buffer = vector.getBuffer();
    }

    @Override
    public DrillBuf buffer() { return buffer; }
  }

  private static class HyperVectorBufferAccessor implements BufferAccessor {
    private final VectorAccessor vectorAccessor;

    public HyperVectorBufferAccessor(VectorAccessor va) {
      vectorAccessor = va;
    }

    @Override
    public DrillBuf buffer() {
      BaseDataValueVector vector = vectorAccessor.vector();
      return vector.getBuffer();
    }
  }

  protected ColumnMetadata schema;
  protected VectorAccessor vectorAccessor;
  protected BufferAccessor bufferAccessor;

  public static ScalarObjectReader buildOptional(ColumnMetadata schema,
      VectorAccessor va, BaseScalarReader reader) {

    // Reader is bound to the values vector inside the nullable vector.

    reader.bindVector(schema, VectorAccessors.nullableValuesAccessor(va));

    // The nullability of each value depends on the "bits" vector
    // in the nullable vector.

    reader.bindNullState(new NullStateReaders.NullableIsSetVectorStateReader(va));

    // Wrap the reader in an object reader.

    return new ScalarObjectReader(reader);
  }

  public static ScalarObjectReader buildRequired(ColumnMetadata schema,
      VectorAccessor va, BaseScalarReader reader) {

    // Reader is bound directly to the required vector.

    reader.bindVector(schema, va);

    // The reader is required, values can't be null.

    reader.bindNullState(NullStateReaders.REQUIRED_STATE_READER);

    // Wrap the reader in an object reader.

    return new ScalarObjectReader(reader);
  }

  public void bindVector(ColumnMetadata schema, VectorAccessor va) {
    this.schema = schema;
    vectorAccessor = va;
    bufferAccessor = bufferAccessor(va);
  }

  protected BufferAccessor bufferAccessor(VectorAccessor va) {
    if (va.isHyper()) {
      return new HyperVectorBufferAccessor(va);
    } else {
      return new SingleVectorBufferAccessor(va);
    }
  }

  @Override
  public void bindIndex(ColumnReaderIndex rowIndex) {
    super.bindIndex(rowIndex);
    vectorAccessor.bind(rowIndex);
  }

  @Override
  public ColumnMetadata schema() { return schema; }
}
