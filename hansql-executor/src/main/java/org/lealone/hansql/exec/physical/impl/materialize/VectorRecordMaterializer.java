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
package org.lealone.hansql.exec.physical.impl.materialize;

import org.lealone.hansql.exec.ExecConstants;
import org.lealone.hansql.exec.context.options.OptionManager;
import org.lealone.hansql.exec.memory.BufferAllocator;
import org.lealone.hansql.exec.ops.FragmentContext;
import org.lealone.hansql.exec.ops.OperatorContext;
import org.lealone.hansql.exec.proto.UserBitShared.QueryData;
import org.lealone.hansql.exec.proto.UserBitShared.QueryId;
import org.lealone.hansql.exec.record.BatchSchema;
import org.lealone.hansql.exec.record.RecordBatch;
import org.lealone.hansql.exec.record.WritableBatch;

public class VectorRecordMaterializer implements RecordMaterializer {
  static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(VectorRecordMaterializer.class);

  private QueryId queryId;
  private RecordBatch batch;
  private BufferAllocator allocator;
  private OptionManager options;

  public VectorRecordMaterializer(FragmentContext context, OperatorContext oContext, RecordBatch batch) {
    this.queryId = context.getHandle().getQueryId();
    this.batch = batch;
    this.allocator = oContext.getAllocator();
    BatchSchema schema = batch.getSchema();
    assert schema != null : "Schema must be defined.";
    options = context.getOptions();
  }

  public QueryWritableBatch convertNext() {
    WritableBatch w = batch.getWritableBatch().transfer(allocator);
    QueryData.Builder builder = QueryData.newBuilder()
        .setQueryId(queryId)
        .setRowCount(batch.getRecordCount())
        .setDef(w.getDef());
    if (!options.getBoolean(ExecConstants.RETURN_RESULT_SET_FOR_DDL)) {
      int count = w.getDef().getAffectedRowsCount();
      builder.setAffectedRowsCount(count == -1 ? 0 : count);
    }
    return new QueryWritableBatch(builder.build(), w.getBuffers());
  }
}
