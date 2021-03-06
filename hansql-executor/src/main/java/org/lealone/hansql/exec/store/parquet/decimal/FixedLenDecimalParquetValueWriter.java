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
package org.lealone.hansql.exec.store.parquet.decimal;

import io.netty.buffer.DrillBuf;

import org.apache.parquet.io.api.Binary;
import org.apache.parquet.io.api.RecordConsumer;
import org.lealone.hansql.exec.util.DecimalUtility;

import java.util.Arrays;

/**
 * Parquet value writer for passing decimal values
 * into {@code RecordConsumer} to be stored as FIXED_LEN_BYTE_ARRAY type.
 */
public class FixedLenDecimalParquetValueWriter extends DecimalValueWriter {

  @Override
  public void writeValue(RecordConsumer consumer, DrillBuf buffer, int start, int end, int precision) {
    int typeLength = DecimalUtility.getMaxBytesSizeForPrecision(precision);
    int length = end - start;
    int startPos = typeLength - length;
    byte[] output = new byte[typeLength];
    if (startPos >= 0) {
      buffer.getBytes(start, output, startPos, length);
      if (output[startPos] < 0) {
        Arrays.fill(output, 0, output.length - length, (byte) -1);
      }
    } else {
      // in this case value from FIXED_LEN_BYTE_ARRAY or BINARY field with greater length was taken, ignore leading bytes
      buffer.getBytes(start - startPos, output, 0, length + startPos);
    }
    consumer.addBinary(Binary.fromReusedByteArray(output));
  }
}
