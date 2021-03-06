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
package org.lealone.hansql.exec.physical.impl.scan.file;

import org.lealone.hansql.common.types.TypeProtos.DataMode;
import org.lealone.hansql.common.types.TypeProtos.MajorType;
import org.lealone.hansql.common.types.TypeProtos.MinorType;
import org.lealone.hansql.exec.store.ColumnExplorer.ImplicitFileColumns;

/**
 * Definition of a file metadata (AKA "implicit") column for this query.
 * Provides the static definition, along
 * with the name set for the implicit column in the session options for the query.
 */

public class FileMetadataColumnDefn {
  public final ImplicitFileColumns defn;
  public final String colName;

  public FileMetadataColumnDefn(String colName, ImplicitFileColumns defn) {
    this.colName = colName;
    this.defn = defn;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder()
      .append("[FileInfoColumnDefn name=\"")
      .append(colName)
      .append("\", defn=")
      .append(defn)
      .append("]");
    return buf.toString();
  }

  public String colName() { return colName; }

  public MajorType dataType() {
    return MajorType.newBuilder()
        .setMinorType(MinorType.VARCHAR)
        .setMode(DataMode.REQUIRED)
        .build();
  }
}
