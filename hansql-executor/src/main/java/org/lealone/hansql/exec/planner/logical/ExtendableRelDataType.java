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
package org.lealone.hansql.exec.planner.logical;

import org.lealone.hansql.exec.planner.types.ExtendableRelDataTypeHolder;
import org.lealone.hansql.exec.planner.types.RelDataTypeDrillImpl;
import org.lealone.hansql.optimizer.rel.type.RelDataTypeFactory;

/**
 * RelDataType for non-dynamic table structure which
 * may be extended by adding partitions or implicit columns.
 */
public class ExtendableRelDataType extends RelDataTypeDrillImpl {

  public ExtendableRelDataType(ExtendableRelDataTypeHolder holder, RelDataTypeFactory typeFactory) {
    super(holder, typeFactory);
  }

  @Override
  protected void generateTypeString(StringBuilder sb, boolean withDetail) {
    sb.append("(ExtendableRelDataType").append(getFieldNames()).append(")");
  }

  @Override
  public boolean isDynamicStruct() {
    return false;
  }
}
