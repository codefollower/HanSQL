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
package org.lealone.hansql.exec.planner.sql;

import org.apache.drill.shaded.guava.com.google.common.base.Preconditions;
import org.lealone.hansql.common.types.TypeProtos;
import org.lealone.hansql.exec.expr.fn.DrillFuncHolder;
import org.lealone.hansql.optimizer.rel.type.RelDataType;
import org.lealone.hansql.optimizer.rel.type.RelDataTypeFactory;
import org.lealone.hansql.optimizer.sql.SqlCall;
import org.lealone.hansql.optimizer.sql.SqlOperatorBinding;
import org.lealone.hansql.optimizer.sql.type.SqlTypeName;
import org.lealone.hansql.optimizer.sql.validate.SqlValidator;
import org.lealone.hansql.optimizer.sql.validate.SqlValidatorScope;

import java.util.ArrayList;

public class DrillSqlOperatorWithoutInference extends DrillSqlOperator {
  private static final TypeProtos.MajorType NONE = TypeProtos.MajorType.getDefaultInstance();
  private final TypeProtos.MajorType returnType;

  public DrillSqlOperatorWithoutInference(String name, int argCount, TypeProtos.MajorType returnType, boolean isDeterminisitic, boolean isNiladic) {
    super(name,
        new ArrayList< DrillFuncHolder>(),
        argCount,
        argCount,
        isDeterminisitic,
        DynamicReturnType.INSTANCE,
        isNiladic);
    this.returnType = Preconditions.checkNotNull(returnType);
  }

  protected RelDataType getReturnDataType(final RelDataTypeFactory factory) {
    if (TypeProtos.MinorType.BIT.equals(returnType.getMinorType())) {
      return factory.createSqlType(SqlTypeName.BOOLEAN);
    }
    return factory.createTypeWithNullability(factory.createSqlType(SqlTypeName.ANY), true);
  }

  private RelDataType getNullableReturnDataType(final RelDataTypeFactory factory) {
    return factory.createTypeWithNullability(getReturnDataType(factory), true);
  }

  @Override
  public RelDataType deriveType(SqlValidator validator, SqlValidatorScope scope, SqlCall call) {
    if (NONE.equals(returnType)) {
      return validator.getTypeFactory().createSqlType(SqlTypeName.ANY);
    }
    /*
     * We return a nullable output type both in validation phase and in
     * Sql to Rel phase. We don't know the type of the output until runtime
     * hence have to choose the least restrictive type to avoid any wrong
     * results.
     */
    return getNullableReturnDataType(validator.getTypeFactory());
  }

  @Override
  public RelDataType inferReturnType(SqlOperatorBinding opBinding) {
    return getNullableReturnDataType(opBinding.getTypeFactory());
  }
}
