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
package org.lealone.hansql.exec.physical.impl.filter;

import org.lealone.hansql.common.exceptions.UserException;
import org.lealone.hansql.common.expression.ExpressionPosition;
import org.lealone.hansql.common.expression.LogicalExpression;
import org.lealone.hansql.common.expression.PathSegment;
import org.lealone.hansql.common.expression.SchemaPath;
import org.lealone.hansql.exec.ExecConstants;
import org.lealone.hansql.exec.exception.OutOfMemoryException;
import org.lealone.hansql.exec.exception.SchemaChangeException;
import org.lealone.hansql.exec.expr.ValueVectorReadExpression;
import org.lealone.hansql.exec.expr.fn.impl.ValueVectorHashHelper;
import org.lealone.hansql.exec.ops.FragmentContext;
import org.lealone.hansql.exec.ops.MetricDef;
import org.lealone.hansql.exec.physical.config.RuntimeFilterPOP;
import org.lealone.hansql.exec.record.AbstractSingleRecordBatch;
import org.lealone.hansql.exec.record.RecordBatch;
import org.lealone.hansql.exec.record.TypedFieldId;
import org.lealone.hansql.exec.record.VectorWrapper;
import org.lealone.hansql.exec.record.BatchSchema.SelectionVectorMode;
import org.lealone.hansql.exec.record.selection.SelectionVector2;
import org.lealone.hansql.exec.record.selection.SelectionVector4;
import org.lealone.hansql.exec.work.filter.BloomFilter;
import org.lealone.hansql.exec.work.filter.RuntimeFilterWritable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A RuntimeFilterRecordBatch steps over the ScanBatch. If the ScanBatch participates
 * in the HashJoinBatch and can be applied by a RuntimeFilter, it will generate a filtered
 * SV2, otherwise will generate a same recordCount-originalRecordCount SV2 which will not affect
 * the Query's performance ,but just do a memory transfer by the later RemovingRecordBatch op.
 */
public class RuntimeFilterRecordBatch extends AbstractSingleRecordBatch<RuntimeFilterPOP> {
  private SelectionVector2 sv2;

  private ValueVectorHashHelper.Hash64 hash64;
  private Map<String, Integer> field2id = new HashMap<>();
  private List<String> toFilterFields;
  private List<BloomFilter> bloomFilters;
  private RuntimeFilterWritable current;
  private int originalRecordCount;
  private long filteredRows = 0l;
  private long appliedTimes = 0l;
  private int batchTimes = 0;
  private boolean waited = false;
  private boolean enableRFWaiting;
  private long maxWaitingTime;
  private long rfIdentifier;
  private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(RuntimeFilterRecordBatch.class);

  public RuntimeFilterRecordBatch(RuntimeFilterPOP pop, RecordBatch incoming, FragmentContext context) throws OutOfMemoryException {
    super(pop, context, incoming);
    enableRFWaiting = context.getOptions().getOption(ExecConstants.HASHJOIN_RUNTIME_FILTER_WAITING_ENABLE_KEY).bool_val;
    maxWaitingTime = context.getOptions().getOption(ExecConstants.HASHJOIN_RUNTIME_FILTER_MAX_WAITING_TIME_KEY).num_val;
    this.rfIdentifier = pop.getIdentifier();
  }

  @Override
  public FragmentContext getContext() {
    return context;
  }

  @Override
  public int getRecordCount() {
    return sv2.getCount();
  }

  @Override
  public SelectionVector2 getSelectionVector2() {
    return sv2;
  }

  @Override
  public SelectionVector4 getSelectionVector4() {
    return null;
  }

  @Override
  protected IterOutcome doWork() {
    originalRecordCount = incoming.getRecordCount();
    sv2.setBatchActualRecordCount(originalRecordCount);
    try {
      applyRuntimeFilter();
    } catch (SchemaChangeException e) {
      throw new UnsupportedOperationException(e);
    }
    container.transferIn(incoming.getContainer());
    updateStats();
    return getFinalOutcome(false);
  }

  @Override
  public void close() {
    if (sv2 != null) {
      sv2.clear();
    }
    super.close();
    if (current != null) {
      current.close();
    }
  }

  @Override
  protected boolean setupNewSchema() throws SchemaChangeException {
    if (sv2 != null) {
      sv2.clear();
    }

    // reset the output container and hash64
    container.clear();
    hash64 = null;

    switch (incoming.getSchema().getSelectionVectorMode()) {
      case NONE:
        if (sv2 == null) {
          sv2 = new SelectionVector2(oContext.getAllocator());
        }
        break;
      case TWO_BYTE:
        sv2 = new SelectionVector2(oContext.getAllocator());
        break;
      case FOUR_BYTE:
      default:
        throw new UnsupportedOperationException();
    }

    // Prepare the output container
    for (final VectorWrapper<?> v : incoming) {
      container.addOrGet(v.getField(), callBack);
    }

    // Setup hash64
    setupHashHelper();

    if (container.isSchemaChanged()) {
      container.buildSchema(SelectionVectorMode.TWO_BYTE);
      return true;
    }
    return false;
  }

  /**
   * Takes care of setting up HashHelper if RuntimeFilter is received and the HashHelper is not already setup. For each
   * schema change hash64 should be reset and this method needs to be called again.
   */
  private void setupHashHelper() {
    current = context.getRuntimeFilter(rfIdentifier);
    if (current == null) {
      return;
    }
    if (bloomFilters == null) {
      bloomFilters = current.unwrap();
    }
    // Check if HashHelper is initialized or not
    if (hash64 == null) {
      ValueVectorHashHelper hashHelper = new ValueVectorHashHelper(incoming, context);
      try {
        //generate hash helper
        this.toFilterFields = current.getRuntimeFilterBDef().getProbeFieldsList();
        List<LogicalExpression> hashFieldExps = new ArrayList<>();
        List<TypedFieldId> typedFieldIds = new ArrayList<>();
        for (String toFilterField : toFilterFields) {
          SchemaPath schemaPath = new SchemaPath(new PathSegment.NameSegment(toFilterField), ExpressionPosition.UNKNOWN);
          TypedFieldId typedFieldId = container.getValueVectorId(schemaPath);
          int[] fieldIds = typedFieldId.getFieldIds();
          this.field2id.put(toFilterField, fieldIds[0]);
          typedFieldIds.add(typedFieldId);
          ValueVectorReadExpression toHashFieldExp = new ValueVectorReadExpression(typedFieldId);
          hashFieldExps.add(toHashFieldExp);
        }
        hash64 = hashHelper.getHash64(hashFieldExps.toArray(new LogicalExpression[hashFieldExps.size()]), typedFieldIds.toArray(new TypedFieldId[typedFieldIds.size()]));
      } catch (Exception e) {
        throw UserException.internalError(e).build(logger);
      }
    }
  }

  /**
   * If RuntimeFilter is available then applies the filter condition on the incoming batch records and creates an SV2
   * to store indexes which passes the filter condition. In case when RuntimeFilter is not available it just pass
   * through all the records from incoming batch to downstream.
   * @throws SchemaChangeException
   */
  private void applyRuntimeFilter() throws SchemaChangeException {
    if (originalRecordCount <= 0) {
      sv2.setRecordCount(0);
      return;
    }
    current = context.getRuntimeFilter(rfIdentifier);
    timedWaiting();
    batchTimes++;
    sv2.allocateNew(originalRecordCount);
    if (current == null) {
      // means none of the rows are filtered out hence set all the indexes
      for (int i = 0; i < originalRecordCount; ++i) {
        sv2.setIndex(i, i);
      }
      sv2.setRecordCount(originalRecordCount);
      return;
    }
    // Setup a hash helper if needed
    setupHashHelper();
    //To make each independent bloom filter work together to construct a final filter result: BitSet.
    BitSet bitSet = new BitSet(originalRecordCount);

    int filterSize = toFilterFields.size();
    int svIndex = 0;
    if (filterSize == 1) {
      BloomFilter bloomFilter = bloomFilters.get(0);
      String fieldName = toFilterFields.get(0);
      int fieldId = field2id.get(fieldName);
      for (int rowIndex = 0; rowIndex < originalRecordCount; rowIndex++) {
        long hash = hash64.hash64Code(rowIndex, 0, fieldId);
        boolean contain = bloomFilter.find(hash);
        if (contain) {
          sv2.setIndex(svIndex, rowIndex);
          svIndex++;
        } else {
          filteredRows++;
        }
      }
    } else {
      for (int i = 0; i < toFilterFields.size(); i++) {
        BloomFilter bloomFilter = bloomFilters.get(i);
        String fieldName = toFilterFields.get(i);
        computeBitSet(field2id.get(fieldName), bloomFilter, bitSet);
      }
      for (int i = 0; i < originalRecordCount; i++) {
        boolean contain = bitSet.get(i);
        if (contain) {
          sv2.setIndex(svIndex, i);
          svIndex++;
        } else {
          filteredRows++;
        }
      }
    }


    appliedTimes++;
    sv2.setRecordCount(svIndex);
  }

  private void computeBitSet(int fieldId, BloomFilter bloomFilter, BitSet bitSet) throws SchemaChangeException {
    for (int rowIndex = 0; rowIndex < originalRecordCount; rowIndex++) {
      long hash = hash64.hash64Code(rowIndex, 0, fieldId);
      boolean contain = bloomFilter.find(hash);
      if (contain) {
        bitSet.set(rowIndex, true);
      } else {
        bitSet.set(rowIndex, false);
      }
    }
  }

  @Override
  public void dump() {
    logger.error("RuntimeFilterRecordBatch[container={}, selectionVector={}, toFilterFields={}, "
        + "originalRecordCount={}, batchSchema={}]",
        container, sv2, toFilterFields, originalRecordCount, incoming.getSchema());
  }

  public enum Metric implements MetricDef {
    FILTERED_ROWS, APPLIED_TIMES;

    @Override
    public int metricId() {
      return ordinal();
    }
  }

  public void updateStats() {
    stats.setLongStat(Metric.FILTERED_ROWS, filteredRows);
    stats.setLongStat(Metric.APPLIED_TIMES, appliedTimes);
  }

  private void timedWaiting() {
    if (!enableRFWaiting || waited) {
      return;
    }
    //Downstream HashJoinBatch prefetch first batch from both sides in buildSchema phase hence waiting is done post that phase
    if (current == null && batchTimes > 0) {
      waited = true;
      try {
        stats.startWait();
        current = context.getRuntimeFilter(rfIdentifier, maxWaitingTime, TimeUnit.MILLISECONDS);
      } finally {
        stats.stopWait();
      }
    }
  }
}
