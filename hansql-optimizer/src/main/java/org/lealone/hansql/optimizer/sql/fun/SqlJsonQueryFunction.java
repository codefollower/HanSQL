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

import org.lealone.hansql.optimizer.sql.SqlCall;
import org.lealone.hansql.optimizer.sql.SqlFunction;
import org.lealone.hansql.optimizer.sql.SqlFunctionCategory;
import org.lealone.hansql.optimizer.sql.SqlJsonQueryEmptyOrErrorBehavior;
import org.lealone.hansql.optimizer.sql.SqlJsonQueryWrapperBehavior;
import org.lealone.hansql.optimizer.sql.SqlKind;
import org.lealone.hansql.optimizer.sql.SqlLiteral;
import org.lealone.hansql.optimizer.sql.SqlNode;
import org.lealone.hansql.optimizer.sql.SqlWriter;
import org.lealone.hansql.optimizer.sql.parser.SqlParserPos;
import org.lealone.hansql.optimizer.sql.type.OperandTypes;
import org.lealone.hansql.optimizer.sql.type.ReturnTypes;
import org.lealone.hansql.optimizer.sql.type.SqlTypeFamily;
import org.lealone.hansql.optimizer.sql.type.SqlTypeTransforms;

/**
 * The <code>JSON_QUERY</code> function.
 */
public class SqlJsonQueryFunction extends SqlFunction {
  public SqlJsonQueryFunction() {
    super("JSON_QUERY", SqlKind.OTHER_FUNCTION,
        ReturnTypes.cascade(ReturnTypes.VARCHAR_2000,
            SqlTypeTransforms.FORCE_NULLABLE),
        null,
        OperandTypes.family(SqlTypeFamily.ANY,
            SqlTypeFamily.ANY, SqlTypeFamily.ANY, SqlTypeFamily.ANY),
        SqlFunctionCategory.SYSTEM);
  }

  @Override public String getSignatureTemplate(int operandsCount) {
    return "{0}({1} {2} WRAPPER {3} ON EMPTY {4} ON ERROR)";
  }

  @Override public void unparse(SqlWriter writer, SqlCall call, int leftPrec,
      int rightPrec) {
    final SqlWriter.Frame frame = writer.startFunCall(getName());
    call.operand(0).unparse(writer, 0, 0);
    final SqlJsonQueryWrapperBehavior wrapperBehavior =
        getEnumValue(call.operand(1));
    switch (wrapperBehavior) {
    case WITHOUT_ARRAY:
      writer.keyword("WITHOUT ARRAY");
      break;
    case WITH_CONDITIONAL_ARRAY:
      writer.keyword("WITH CONDITIONAL ARRAY");
      break;
    case WITH_UNCONDITIONAL_ARRAY:
      writer.keyword("WITH UNCONDITIONAL ARRAY");
      break;
    default:
      throw new IllegalStateException("unreachable code");
    }
    writer.keyword("WRAPPER");
    unparseEmptyOrErrorBehavior(writer, getEnumValue(call.operand(2)));
    writer.keyword("ON EMPTY");
    unparseEmptyOrErrorBehavior(writer, getEnumValue(call.operand(3)));
    writer.keyword("ON ERROR");
    writer.endFunCall(frame);
  }

  @Override public SqlCall createCall(SqlLiteral functionQualifier,
      SqlParserPos pos, SqlNode... operands) {
    if (operands[1] == null) {
      operands[1] = SqlLiteral.createSymbol(SqlJsonQueryWrapperBehavior.WITHOUT_ARRAY, pos);
    }
    if (operands[2] == null) {
      operands[2] = SqlLiteral.createSymbol(SqlJsonQueryEmptyOrErrorBehavior.NULL, pos);
    }
    if (operands[3] == null) {
      operands[3] = SqlLiteral.createSymbol(SqlJsonQueryEmptyOrErrorBehavior.NULL, pos);
    }
    return super.createCall(functionQualifier, pos, operands);
  }

  private void unparseEmptyOrErrorBehavior(SqlWriter writer,
      SqlJsonQueryEmptyOrErrorBehavior emptyBehavior) {
    switch (emptyBehavior) {
    case NULL:
      writer.keyword("NULL");
      break;
    case ERROR:
      writer.keyword("ERROR");
      break;
    case EMPTY_ARRAY:
      writer.keyword("EMPTY ARRAY");
      break;
    case EMPTY_OBJECT:
      writer.keyword("EMPTY OBJECT");
      break;
    default:
      throw new IllegalStateException("unreachable code");
    }
  }

  private <E extends Enum<E>> E getEnumValue(SqlNode operand) {
    return (E) ((SqlLiteral) operand).getValue();
  }
}

// End SqlJsonQueryFunction.java
