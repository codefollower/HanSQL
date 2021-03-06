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
package org.lealone.hansql.exec.physical.impl.aggregate;

import org.lealone.hansql.common.exceptions.UserException;
import org.lealone.hansql.common.expression.SchemaPath;
import org.lealone.hansql.exec.cache.VectorAccessibleSerializable;
import org.lealone.hansql.exec.ops.FragmentContext;
import org.lealone.hansql.exec.ops.OperatorContext;
import org.lealone.hansql.exec.physical.impl.spill.SpillSet;
import org.lealone.hansql.exec.record.BatchSchema;
import org.lealone.hansql.exec.record.CloseableRecordBatch;
import org.lealone.hansql.exec.record.SimpleRecordBatch;
import org.lealone.hansql.exec.record.TypedFieldId;
import org.lealone.hansql.exec.record.VectorContainer;
import org.lealone.hansql.exec.record.VectorWrapper;
import org.lealone.hansql.exec.record.WritableBatch;
import org.lealone.hansql.exec.record.selection.SelectionVector2;
import org.lealone.hansql.exec.record.selection.SelectionVector4;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

/**
 * A class to replace "incoming" - instead scanning a spilled partition file
 */
public class SpilledRecordbatch implements CloseableRecordBatch {

  private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SimpleRecordBatch.class);

  private VectorContainer container;
  private InputStream spillStream;
  private int spilledBatches;
  private FragmentContext context;
  private BatchSchema schema;
  private SpillSet spillSet;
  private String spillFile;
  VectorAccessibleSerializable vas;
  private IterOutcome initialOutcome;
  // Represents last outcome of next(). If an Exception is thrown
  // during the method's execution a value IterOutcome.STOP will be assigned.
  private IterOutcome lastOutcome;

  public SpilledRecordbatch(String spillFile, int spilledBatches, FragmentContext context, BatchSchema schema, OperatorContext oContext, SpillSet spillSet) {
    this.context = context;
    this.schema = schema;
    this.spilledBatches = spilledBatches;
    this.spillSet = spillSet;
    this.spillFile = spillFile;
    vas = new VectorAccessibleSerializable(oContext.getAllocator());
    container = vas.get();

    try {
      this.spillStream = this.spillSet.openForInput(spillFile);
    } catch (IOException e) {
      throw UserException.resourceError(e).build(HashAggBatch.logger);
    }

    initialOutcome = next(); // initialize the container
    lastOutcome = initialOutcome;
  }

  @Override
  public SelectionVector2 getSelectionVector2() {
    throw new UnsupportedOperationException();
  }

  @Override
  public SelectionVector4 getSelectionVector4() {
    throw new UnsupportedOperationException();
  }

  @Override
  public TypedFieldId getValueVectorId(SchemaPath path) {
    return container.getValueVectorId(path);
  }

  @Override
  public VectorWrapper<?> getValueAccessorById(Class<?> clazz, int... ids) {
    return container.getValueAccessorById(clazz, ids);
  }

  @Override
  public Iterator<VectorWrapper<?>> iterator() {
    return container.iterator();
  }

  @Override
  public FragmentContext getContext() { return context; }

  @Override
  public BatchSchema getSchema() { return schema; }

  @Override
  public WritableBatch getWritableBatch() {
    return WritableBatch.get(this);
  }

  @Override
  public VectorContainer getOutgoingContainer() { return container; }

  @Override
  public VectorContainer getContainer() { return container; }

  @Override
  public int getRecordCount() { return container.getRecordCount(); }

  @Override
  public void kill(boolean sendUpstream) {
    this.close(); // delete the current spill file
  }

  /**
   * Read the next batch from the spill file
   *
   * @return IterOutcome
   */
  @Override
  public IterOutcome next() {

    if (!context.getExecutorState().shouldContinue()) {
      lastOutcome = IterOutcome.STOP;
      return lastOutcome;
    }

    if ( spilledBatches <= 0 ) { // no more batches to read in this partition
      this.close();
      lastOutcome = IterOutcome.NONE;
      return lastOutcome;
    }

    if ( spillStream == null ) {
      lastOutcome = IterOutcome.STOP;
      throw new IllegalStateException("Spill stream was null");
    }

    if ( spillSet.getPosition(spillStream)  < 0 ) {
      HashAggTemplate.logger.warn("Position is {} for stream {}", spillSet.getPosition(spillStream), spillStream.toString());
    }

    try {
      if ( container.getNumberOfColumns() > 0 ) { // container already initialized
        // Pass our container to the reader because other classes (e.g. HashAggBatch, HashTable)
        // may have a reference to this container (as an "incoming")
        vas.readFromStreamWithContainer(container, spillStream);
      }
      else { // first time - create a container
        vas.readFromStream(spillStream);
        container = vas.get();
      }
    } catch (IOException e) {
      lastOutcome = IterOutcome.STOP;
      throw UserException.dataReadError(e).addContext("Failed reading from a spill file").build(HashAggTemplate.logger);
    } catch (Exception e) {
      lastOutcome = IterOutcome.STOP;
      throw e;
    }

    spilledBatches--; // one less batch to read
    lastOutcome = IterOutcome.OK;
    return lastOutcome;
  }

  /**
   *  Return the initial outcome (from the first next() call )
   */
  public IterOutcome getInitialOutcome() { return initialOutcome; }

  @Override
  public void dump() {
    logger.error("SpilledRecordbatch[container={}, spilledBatches={}, schema={}, spillFile={}, spillSet={}]",
        container, spilledBatches, schema, spillFile, spillSet);
  }

  @Override
  public boolean hasFailed() {
    return lastOutcome == IterOutcome.STOP;
  }

  /**
   * Note: ignoring any IO errors (e.g. file not found)
   */
  @Override
  public void close() {
    container.clear();
    try {
      if (spillStream != null) {
        spillStream.close();
        spillStream = null;
      }

      spillSet.delete(spillFile);
    }
    catch (IOException e) {
      /* ignore */
    } finally {
    }
  }
}
