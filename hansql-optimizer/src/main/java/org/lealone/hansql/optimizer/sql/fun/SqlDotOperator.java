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
import org.lealone.hansql.optimizer.rel.type.RelDataTypeField;
import org.lealone.hansql.optimizer.sql.SqlCall;
import org.lealone.hansql.optimizer.sql.SqlCallBinding;
import org.lealone.hansql.optimizer.sql.SqlKind;
import org.lealone.hansql.optimizer.sql.SqlNode;
import org.lealone.hansql.optimizer.sql.SqlOperandCountRange;
import org.lealone.hansql.optimizer.sql.SqlOperatorBinding;
import org.lealone.hansql.optimizer.sql.SqlSpecialOperator;
import org.lealone.hansql.optimizer.sql.SqlUtil;
import org.lealone.hansql.optimizer.sql.SqlWriter;
import org.lealone.hansql.optimizer.sql.parser.SqlParserPos;
import org.lealone.hansql.optimizer.sql.type.OperandTypes;
import org.lealone.hansql.optimizer.sql.type.SqlOperandCountRanges;
import org.lealone.hansql.optimizer.sql.type.SqlSingleOperandTypeChecker;
import org.lealone.hansql.optimizer.sql.type.SqlTypeFamily;
import org.lealone.hansql.optimizer.sql.type.SqlTypeName;
import org.lealone.hansql.optimizer.sql.util.SqlBasicVisitor;
import org.lealone.hansql.optimizer.sql.util.SqlVisitor;
import org.lealone.hansql.optimizer.sql.validate.SqlValidator;
import org.lealone.hansql.optimizer.sql.validate.SqlValidatorScope;
import org.lealone.hansql.optimizer.sql.validate.SqlValidatorUtil;
import org.lealone.hansql.optimizer.util.Litmus;
import org.lealone.hansql.optimizer.util.Static;

/**
 * The dot operator {@code .}, used to access a field of a
 * record. For example, {@code a.b}.
 */
public class SqlDotOperator extends SqlSpecialOperator {
  SqlDotOperator() {
    super("DOT", SqlKind.DOT, 100, true, null, null, null);
  }

  @Override public ReduceResult reduceExpr(int ordinal, TokenSequence list) {
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

  @Override public void unparse(SqlWriter writer, SqlCall call, int leftPrec,
      int rightPrec) {
    final SqlWriter.Frame frame =
        writer.startList(SqlWriter.FrameTypeEnum.IDENTIFIER);
    call.operand(0).unparse(writer, leftPrec, 0);
    writer.sep(".");
    call.operand(1).unparse(writer, 0, 0);
    writer.endList(frame);
  }

  @Override public SqlOperandCountRange getOperandCountRange() {
    return SqlOperandCountRanges.of(2);
  }

  @Override public <R> void acceptCall(SqlVisitor<R> visitor, SqlCall call,
      boolean onlyExpressions, SqlBasicVisitor.ArgHandler<R> argHandler) {
    if (onlyExpressions) {
      // Do not visit operands[1] -- it is not an expression.
      argHandler.visitChild(visitor, call, 0, call.operand(0));
    } else {
      super.acceptCall(visitor, call, onlyExpressions, argHandler);
    }
  }

  @Override public RelDataType deriveType(SqlValidator validator,
      SqlValidatorScope scope, SqlCall call) {
    final SqlNode operand = call.getOperandList().get(0);
    final RelDataType nodeType =
        validator.deriveType(scope, operand);
    assert nodeType != null;
    if (!nodeType.isStruct()) {
      throw SqlUtil.newContextException(operand.getParserPosition(),
          Static.RESOURCE.incompatibleTypes());
    }

    final SqlNode fieldId = call.operand(1);
    final String fieldName = fieldId.toString();
    final RelDataTypeField field =
        nodeType.getField(fieldName, false, false);
    if (field == null) {
      throw SqlUtil.newContextException(fieldId.getParserPosition(),
          Static.RESOURCE.unknownField(fieldName));
    }
    RelDataType type = field.getType();

    // Validate and determine coercibility and resulting collation
    // name of binary operator if needed.
    type = adjustType(validator, call, type);
    SqlValidatorUtil.checkCharsetAndCollateConsistentIfCharType(type);
    return type;
  }

  public void validateCall(
      SqlCall call,
      SqlValidator validator,
      SqlValidatorScope scope,
      SqlValidatorScope operandScope) {
    assert call.getOperator() == this;
    // Do not visit call.getOperandList().get(1) here.
    // call.getOperandList().get(1) will be validated when deriveType() is called.
    call.getOperandList().get(0).validateExpr(validator, operandScope);
  }

  @Override public boolean checkOperandTypes(SqlCallBinding callBinding,
      boolean throwOnFailure) {
    final SqlNode left = callBinding.operand(0);
    final SqlNode right = callBinding.operand(1);
    final RelDataType type =
        callBinding.getValidator().deriveType(callBinding.getScope(), left);
    if (type.getSqlTypeName() != SqlTypeName.ROW) {
      return false;
    } else if (type.getSqlIdentifier().isStar()) {
      return false;
    }
    final RelDataType operandType = callBinding.getOperandType(0);
    final SqlSingleOperandTypeChecker checker = getChecker(operandType);
    return checker.checkSingleOperandType(callBinding, right, 0,
        throwOnFailure);
  }

  private SqlSingleOperandTypeChecker getChecker(RelDataType operandType) {
    switch (operandType.getSqlTypeName()) {
    case ROW:
      return OperandTypes.family(SqlTypeFamily.STRING);
    default:
      throw new AssertionError(operandType.getSqlTypeName());
    }
  }

  @Override public boolean validRexOperands(final int count, final Litmus litmus) {
    return litmus.fail("DOT is valid only for SqlCall not for RexCall");
  }

  @Override public String getAllowedSignatures(String name) {
    return "<A>.<B>";
  }

  @Override public RelDataType inferReturnType(SqlOperatorBinding opBinding) {
    final RelDataTypeFactory typeFactory = opBinding.getTypeFactory();
    final RelDataType recordType = opBinding.getOperandType(0);
    switch (recordType.getSqlTypeName()) {
    case ROW:
      final String fieldName =
          opBinding.getOperandLiteralValue(1, String.class);
      final RelDataType type = opBinding.getOperandType(0)
          .getField(fieldName, false, false)
          .getType();
      if (recordType.isNullable()) {
        return typeFactory.createTypeWithNullability(type, true);
      } else {
        return type;
      }
    default:
      throw new AssertionError();
    }
  }
}

// End SqlDotOperator.java
