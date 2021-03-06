/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lealone.hansql.optimizer.sql.fun;

import static org.lealone.hansql.optimizer.util.Static.RESOURCE;

import org.lealone.hansql.optimizer.rel.type.RelDataType;
import org.lealone.hansql.optimizer.sql.SqlFunction;
import org.lealone.hansql.optimizer.sql.SqlFunctionCategory;
import org.lealone.hansql.optimizer.sql.SqlKind;
import org.lealone.hansql.optimizer.sql.SqlOperatorBinding;
import org.lealone.hansql.optimizer.sql.SqlSyntax;
import org.lealone.hansql.optimizer.sql.type.OperandTypes;
import org.lealone.hansql.optimizer.sql.type.SqlOperandTypeChecker;
import org.lealone.hansql.optimizer.sql.type.SqlTypeName;
import org.lealone.hansql.optimizer.sql.type.SqlTypeUtil;
import org.lealone.hansql.optimizer.sql.validate.SqlMonotonicity;

/**
 * Base class for time functions such as "LOCALTIME", "LOCALTIME(n)".
 */
public class SqlAbstractTimeFunction extends SqlFunction {
  //~ Static fields/initializers ---------------------------------------------

  private static final SqlOperandTypeChecker OTC_CUSTOM =
      OperandTypes.or(
          OperandTypes.POSITIVE_INTEGER_LITERAL, OperandTypes.NILADIC);

  //~ Instance fields --------------------------------------------------------

  private final SqlTypeName typeName;

  //~ Constructors -----------------------------------------------------------

  protected SqlAbstractTimeFunction(String name, SqlTypeName typeName) {
    super(name, SqlKind.OTHER_FUNCTION, null, null, OTC_CUSTOM,
        SqlFunctionCategory.TIMEDATE);
    this.typeName = typeName;
  }

  //~ Methods ----------------------------------------------------------------

  public SqlSyntax getSyntax() {
    return SqlSyntax.FUNCTION_ID;
  }

  public RelDataType inferReturnType(
      SqlOperatorBinding opBinding) {
    // REVIEW jvs 20-Feb-2005: Need to take care of time zones.
    int precision = 0;
    if (opBinding.getOperandCount() == 1) {
      RelDataType type = opBinding.getOperandType(0);
      if (SqlTypeUtil.isNumeric(type)) {
        precision = opBinding.getOperandLiteralValue(0, Integer.class);
      }
    }
    assert precision >= 0;
    if (precision > SqlTypeName.MAX_DATETIME_PRECISION) {
      throw opBinding.newError(
          RESOURCE.argumentMustBeValidPrecision(
              opBinding.getOperator().getName(), 0,
              SqlTypeName.MAX_DATETIME_PRECISION));
    }
    return opBinding.getTypeFactory().createSqlType(typeName, precision);
  }

  // All of the time functions are increasing. Not strictly increasing.
  @Override public SqlMonotonicity getMonotonicity(SqlOperatorBinding call) {
    return SqlMonotonicity.INCREASING;
  }

  // Plans referencing context variables should never be cached
  public boolean isDynamicFunction() {
    return true;
  }
}

// End SqlAbstractTimeFunction.java
