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

import java.util.Arrays;

import org.lealone.hansql.optimizer.rel.type.RelDataType;
import org.lealone.hansql.optimizer.rel.type.RelDataTypeFactory;
import org.lealone.hansql.optimizer.sql.SqlCall;
import org.lealone.hansql.optimizer.sql.SqlCallBinding;
import org.lealone.hansql.optimizer.sql.SqlKind;
import org.lealone.hansql.optimizer.sql.SqlNode;
import org.lealone.hansql.optimizer.sql.SqlOperandCountRange;
import org.lealone.hansql.optimizer.sql.SqlOperatorBinding;
import org.lealone.hansql.optimizer.sql.SqlSpecialOperator;
import org.lealone.hansql.optimizer.sql.SqlWriter;
import org.lealone.hansql.optimizer.sql.parser.SqlParserPos;
import org.lealone.hansql.optimizer.sql.type.OperandTypes;
import org.lealone.hansql.optimizer.sql.type.SqlOperandCountRanges;
import org.lealone.hansql.optimizer.sql.type.SqlSingleOperandTypeChecker;
import org.lealone.hansql.optimizer.sql.type.SqlTypeFamily;
import org.lealone.hansql.optimizer.sql.type.SqlTypeName;

/**
 * The item operator {@code [ ... ]}, used to access a given element of an
 * array or map. For example, {@code myArray[3]} or {@code "myMap['foo']"}.
 */
class SqlItemOperator extends SqlSpecialOperator {

  private static final SqlSingleOperandTypeChecker ARRAY_OR_MAP =
      OperandTypes.or(
          OperandTypes.family(SqlTypeFamily.ARRAY),
          OperandTypes.family(SqlTypeFamily.MAP),
          OperandTypes.family(SqlTypeFamily.ANY));

  SqlItemOperator() {
    super("ITEM", SqlKind.OTHER_FUNCTION, 100, true, null, null, null);
  }

  @Override public ReduceResult reduceExpr(int ordinal,
      TokenSequence list) {
    SqlNode left = list.node(ordinal - 1);
    SqlNode right = list.node(ordinal + 1);
    return new ReduceResult(ordinal - 1,
        ordinal + 2,
        createCall(
            SqlParserPos.sum(
                Arrays.asList(left.getParserPosition(),
                    right.getParserPosition(),
                    list.pos(ordinal))),
            left,
            right));
  }

  @Override public void unparse(
      SqlWriter writer, SqlCall call, int leftPrec, int rightPrec) {
    call.operand(0).unparse(writer, leftPrec, 0);
    final SqlWriter.Frame frame = writer.startList("[", "]");
    call.operand(1).unparse(writer, 0, 0);
    writer.endList(frame);
  }

  @Override public SqlOperandCountRange getOperandCountRange() {
    return SqlOperandCountRanges.of(2);
  }

  @Override public boolean checkOperandTypes(
      SqlCallBinding callBinding,
      boolean throwOnFailure) {
    final SqlNode left = callBinding.operand(0);
    final SqlNode right = callBinding.operand(1);
    if (!ARRAY_OR_MAP.checkSingleOperandType(callBinding, left, 0,
        throwOnFailure)) {
      return false;
    }
    final RelDataType operandType = callBinding.getOperandType(0);
    final SqlSingleOperandTypeChecker checker = getChecker(operandType);
    return checker.checkSingleOperandType(callBinding, right, 0,
        throwOnFailure);
  }

  private SqlSingleOperandTypeChecker getChecker(RelDataType operandType) {
    switch (operandType.getSqlTypeName()) {
    case ARRAY:
      return OperandTypes.family(SqlTypeFamily.INTEGER);
    case MAP:
      return OperandTypes.family(
          operandType.getKeyType().getSqlTypeName().getFamily());
    case ANY:
    case DYNAMIC_STAR:
      return OperandTypes.or(
          OperandTypes.family(SqlTypeFamily.INTEGER),
          OperandTypes.family(SqlTypeFamily.CHARACTER));
    default:
      throw new AssertionError(operandType.getSqlTypeName());
    }
  }

  @Override public String getAllowedSignatures(String name) {
    return "<ARRAY>[<INTEGER>]\n"
        + "<MAP>[<VALUE>]";
  }

  @Override public RelDataType inferReturnType(SqlOperatorBinding opBinding) {
    final RelDataTypeFactory typeFactory = opBinding.getTypeFactory();
    final RelDataType operandType = opBinding.getOperandType(0);
    switch (operandType.getSqlTypeName()) {
    case ARRAY:
      return typeFactory.createTypeWithNullability(
          operandType.getComponentType(), true);
    case MAP:
      return typeFactory.createTypeWithNullability(operandType.getValueType(),
          true);
    case ANY:
    case DYNAMIC_STAR:
      return typeFactory.createTypeWithNullability(
          typeFactory.createSqlType(SqlTypeName.ANY), true);
    default:
      throw new AssertionError();
    }
  }
}

// End SqlItemOperator.java
