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

import org.lealone.hansql.exec.record.RecordBatch;
import org.lealone.hansql.exec.record.VectorWrapper;
import org.lealone.hansql.exec.record.BatchSchema.SelectionVectorMode;
import org.lealone.hansql.exec.vector.ValueVector;

public class VectorValidator {
  public static void validate(RecordBatch batch) {
    int count = batch.getRecordCount();
    long hash = 12345;
    SelectionVectorMode mode = batch.getSchema().getSelectionVectorMode();
    switch(mode) {
      case NONE: {
        for (VectorWrapper w : batch) {
          ValueVector v = w.getValueVector();
          for (int i = 0; i < count; i++) {
            Object obj = v.getAccessor().getObject(i);
            if (obj != null) {
              hash = obj.hashCode() ^ hash;
            }
          }
        }
        break;
      }
      case TWO_BYTE: {
        for (VectorWrapper w : batch) {
          ValueVector v = w.getValueVector();
          for (int i = 0; i < count; i++) {
            int index = batch.getSelectionVector2().getIndex(i);
            Object obj = v.getAccessor().getObject(index);
            if (obj != null) {
              hash = obj.hashCode() ^ hash;
            }
          }
        }
        break;
      }
      case FOUR_BYTE: {
        for (VectorWrapper w : batch) {
          ValueVector[] vv = w.getValueVectors();
          for (int i = 0; i < count; i++) {
            int index = batch.getSelectionVector4().get(i);
            ValueVector v = vv[index >> 16];
            Object obj = v.getAccessor().getObject(index & 65535);
            if (obj != null) {
              hash = obj.hashCode() ^ hash;
            }
          }
        }
      }
    }
    if (hash == 0) {
//      System.out.println(hash);
    }
  }
}
