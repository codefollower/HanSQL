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
package org.lealone.hansql.exec.physical.impl.svremover;

import org.lealone.hansql.common.types.TypeProtos;
import org.lealone.hansql.common.types.Types;
import org.lealone.hansql.exec.record.VectorAccessible;
import org.lealone.hansql.exec.record.VectorContainer;
import org.lealone.hansql.exec.record.VectorWrapper;
import org.lealone.hansql.exec.vector.AllocationHelper;
import org.lealone.hansql.exec.vector.ValueVector;

public abstract class AbstractCopier implements Copier {
  protected ValueVector[] vvOut;
  protected VectorContainer outgoing;

  @Override
  public void setup(VectorAccessible incoming, VectorContainer outgoing) {
    this.outgoing = outgoing;

    final int count = outgoing.getNumberOfColumns();
    vvOut = new ValueVector[count];

    for (int index = 0; index < count; index++) {
      vvOut[index] = outgoing.getValueVector(index).getValueVector();
    }
  }

  @Override
  public int copyRecords(int index, int recordCount) {
    allocateOutgoing(outgoing, recordCount);
    return insertRecords(0, index, recordCount);
  }

  @Override
  public int appendRecord(int index) {
    int outgoingPosition = outgoing.getRecordCount();
    copyEntryIndirect(index, outgoingPosition);
    outgoingPosition++;
    updateCounts(outgoingPosition);
    return outgoingPosition;
  }

  @Override
  public int appendRecords(int index, int recordCount) {
    return insertRecords(outgoing.getRecordCount(), index, recordCount);
  }

  private int insertRecords(int outgoingPosition, int index, int recordCount) {
    final int endIndex = index + recordCount;

    for(int svIndex = index; svIndex < endIndex; svIndex++, outgoingPosition++){
      copyEntryIndirect(svIndex, outgoingPosition);
    }

    updateCounts(outgoingPosition);
    return outgoingPosition;
  }

  protected void updateCounts(int numRecords) {
    outgoing.setRecordCount(numRecords);

    for (int vectorIndex = 0; vectorIndex < vvOut.length; vectorIndex++) {
      vvOut[vectorIndex].getMutator().setValueCount(numRecords);
    }
  }

  public abstract void copyEntryIndirect(int inIndex, int outIndex);

  public abstract void copyEntry(int inIndex, int outIndex);

  public static void allocateOutgoing(VectorContainer outgoing, int recordCount) {
    for(VectorWrapper<?> out : outgoing) {
      TypeProtos.MajorType type = out.getField().getType();

      if (!Types.isFixedWidthType(type) || Types.isRepeated(type)) {
        out.getValueVector().allocateNew();
      } else {
        AllocationHelper.allocate(out.getValueVector(), recordCount, 1);
      }
    }
  }
}
