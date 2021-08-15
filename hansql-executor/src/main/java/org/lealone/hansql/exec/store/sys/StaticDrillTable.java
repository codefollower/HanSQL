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
package org.lealone.hansql.exec.store.sys;

import org.lealone.hansql.exec.planner.logical.DrillTable;
import org.lealone.hansql.exec.store.RecordDataType;
import org.lealone.hansql.exec.store.StoragePlugin;
import org.lealone.hansql.exec.util.ImpersonationUtil;
import org.lealone.hansql.optimizer.rel.type.RelDataType;
import org.lealone.hansql.optimizer.rel.type.RelDataTypeFactory;
import org.lealone.hansql.optimizer.schema.Schema.TableType;

/**
 * A {@link org.lealone.hansql.exec.planner.logical.DrillTable} with a defined schema
 * Currently, this is a wrapper class for {@link org.lealone.hansql.exec.store.sys.SystemTable}.
 */
public class StaticDrillTable extends DrillTable {
//  private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(StaticDrillTable.class);

  private final RecordDataType dataType;

  public StaticDrillTable(String storageEngineName, StoragePlugin plugin, TableType tableType, Object selection, RecordDataType dataType) {
    super(storageEngineName, plugin, tableType, ImpersonationUtil.getProcessUserName(), selection);
    this.dataType = dataType;
  }

  @Override
  public RelDataType getRowType(RelDataTypeFactory typeFactory) {
    return dataType.getRowType(typeFactory);
  }
}