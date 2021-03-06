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

import org.lealone.hansql.exec.proto.BitData.FragmentRecordBatch;
import org.lealone.hansql.exec.proto.UserBitShared.QueryId;
import org.lealone.hansql.exec.proto.UserBitShared.RecordBatchDef;
import org.lealone.hansql.exec.record.MaterializedField;

import io.netty.buffer.ByteBuf;

public class FragmentWritableBatch{
  static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(FragmentWritableBatch.class);

  private static RecordBatchDef EMPTY_DEF = RecordBatchDef.newBuilder().setRecordCount(0).build();

  private final ByteBuf[] buffers;
  private final FragmentRecordBatch header;

  public FragmentWritableBatch(final boolean isLast, final QueryId queryId, final int sendMajorFragmentId, final int sendMinorFragmentId, final int receiveMajorFragmentId, final int receiveMinorFragmentId, final WritableBatch batch){
    this(isLast, queryId, sendMajorFragmentId, sendMinorFragmentId, receiveMajorFragmentId, new int[]{receiveMinorFragmentId}, batch.getDef(), batch.getBuffers());
  }

  public FragmentWritableBatch(final boolean isLast, final QueryId queryId, final int sendMajorFragmentId, final int sendMinorFragmentId, final int receiveMajorFragmentId, final int[] receiveMinorFragmentIds, final WritableBatch batch){
    this(isLast, queryId, sendMajorFragmentId, sendMinorFragmentId, receiveMajorFragmentId, receiveMinorFragmentIds, batch.getDef(), batch.getBuffers());
  }

  private FragmentWritableBatch(final boolean isLast, final QueryId queryId, final int sendMajorFragmentId, final int sendMinorFragmentId, final int receiveMajorFragmentId, final int[] receiveMinorFragmentId, final RecordBatchDef def, final ByteBuf... buffers){
    this.buffers = buffers;
    final FragmentRecordBatch.Builder builder = FragmentRecordBatch.newBuilder()
        .setIsLastBatch(isLast)
        .setDef(def)
        .setQueryId(queryId)
        .setReceivingMajorFragmentId(receiveMajorFragmentId)
        .setSendingMajorFragmentId(sendMajorFragmentId)
        .setSendingMinorFragmentId(sendMinorFragmentId);

    for(final int i : receiveMinorFragmentId){
      builder.addReceivingMinorFragmentId(i);
    }

    this.header = builder.build();
  }


  public static FragmentWritableBatch getEmptyLast(final QueryId queryId, final int sendMajorFragmentId, final int sendMinorFragmentId, final int receiveMajorFragmentId, final int receiveMinorFragmentId){
    return getEmptyLast(queryId, sendMajorFragmentId, sendMinorFragmentId, receiveMajorFragmentId, new int[]{receiveMinorFragmentId});
  }

  public static FragmentWritableBatch getEmptyLast(final QueryId queryId, final int sendMajorFragmentId, final int sendMinorFragmentId, final int receiveMajorFragmentId, final int[] receiveMinorFragmentIds){
    return new FragmentWritableBatch(true, queryId, sendMajorFragmentId, sendMinorFragmentId, receiveMajorFragmentId, receiveMinorFragmentIds, EMPTY_DEF);
  }


  public static FragmentWritableBatch getEmptyLastWithSchema(final QueryId queryId, final int sendMajorFragmentId, final int sendMinorFragmentId,
                                                             final int receiveMajorFragmentId, final int receiveMinorFragmentId, final BatchSchema schema){
    return getEmptyBatchWithSchema(true, queryId, sendMajorFragmentId, sendMinorFragmentId, receiveMajorFragmentId,
        receiveMinorFragmentId, schema);
  }

  public static FragmentWritableBatch getEmptyBatchWithSchema(final boolean isLast, final QueryId queryId, final int sendMajorFragmentId,
      final int sendMinorFragmentId, final int receiveMajorFragmentId, final int receiveMinorFragmentId, final BatchSchema schema){

    final RecordBatchDef.Builder def = RecordBatchDef.newBuilder();
    if (schema != null) {
      for (final MaterializedField field : schema) {
        def.addField(field.getSerializedField());
      }
    }
    return new FragmentWritableBatch(isLast, queryId, sendMajorFragmentId, sendMinorFragmentId, receiveMajorFragmentId,
        new int[] { receiveMinorFragmentId }, def.build());
  }

  public ByteBuf[] getBuffers(){
    return buffers;
  }

  public long getByteCount() {
    long n = 0;
    for (final ByteBuf buf : buffers) {
      n += buf.readableBytes();
    }
    return n;
  }

  public FragmentRecordBatch getHeader() {
    return header;

  }





}
