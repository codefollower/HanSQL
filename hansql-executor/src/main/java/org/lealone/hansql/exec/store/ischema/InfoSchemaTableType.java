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
package org.lealone.hansql.exec.store.ischema;

import org.lealone.hansql.exec.context.options.OptionManager;
import org.lealone.hansql.exec.store.ischema.InfoSchemaTable.Catalogs;
import org.lealone.hansql.exec.store.ischema.InfoSchemaTable.Columns;
import org.lealone.hansql.exec.store.ischema.InfoSchemaTable.Files;
import org.lealone.hansql.exec.store.ischema.InfoSchemaTable.Schemata;
import org.lealone.hansql.exec.store.ischema.InfoSchemaTable.Tables;
import org.lealone.hansql.exec.store.ischema.InfoSchemaTable.Views;
import org.lealone.hansql.exec.store.pojo.PojoRecordReader;
import org.lealone.hansql.optimizer.rel.type.RelDataType;
import org.lealone.hansql.optimizer.rel.type.RelDataTypeFactory;
import org.lealone.hansql.optimizer.schema.SchemaPlus;

/**
 * The set of tables/views in INFORMATION_SCHEMA.
 */
public enum InfoSchemaTableType {

  CATALOGS(new Catalogs()),
  SCHEMATA(new Schemata()),
  VIEWS(new Views()),
  COLUMNS(new Columns()),
  TABLES(new Tables()),
  FILES(new Files());

  private final InfoSchemaTable<?> tableDef;

  /**
   * ...
   * @param  tableDef  the definition (columns and data generator) of the table
   */
  InfoSchemaTableType(InfoSchemaTable<?> tableDef) {
    this.tableDef = tableDef;
  }

  public <S> PojoRecordReader<S> getRecordReader(SchemaPlus rootSchema, InfoSchemaFilter filter, OptionManager optionManager) {
    @SuppressWarnings("unchecked")
    InfoSchemaRecordGenerator<S> recordGenerator = (InfoSchemaRecordGenerator<S>) tableDef.getRecordGenerator(optionManager);
    recordGenerator.setInfoSchemaFilter(filter);
    recordGenerator.scanSchema(rootSchema);
    return recordGenerator.getRecordReader();
  }

  public RelDataType getRowType(RelDataTypeFactory typeFactory) {
    return tableDef.getRowType(typeFactory);
  }
}
