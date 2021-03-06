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
package org.lealone.hansql.optimizer.sql2rel;

import com.google.common.base.Preconditions;

import java.math.BigDecimal;

import org.lealone.hansql.optimizer.rel.type.RelDataType;
import org.lealone.hansql.optimizer.rel.type.RelDataTypeFactory;
import org.lealone.hansql.optimizer.rex.RexBuilder;
import org.lealone.hansql.optimizer.rex.RexLiteral;
import org.lealone.hansql.optimizer.rex.RexNode;
import org.lealone.hansql.optimizer.sql.SqlCall;
import org.lealone.hansql.optimizer.sql.SqlIntervalLiteral;
import org.lealone.hansql.optimizer.sql.SqlIntervalQualifier;
import org.lealone.hansql.optimizer.sql.SqlLiteral;
import org.lealone.hansql.optimizer.sql.SqlTimeLiteral;
import org.lealone.hansql.optimizer.sql.SqlTimestampLiteral;
import org.lealone.hansql.optimizer.sql.type.SqlTypeName;
import org.lealone.hansql.optimizer.sql.validate.SqlValidator;
import org.lealone.hansql.optimizer.util.BitString;
import org.lealone.hansql.optimizer.util.ByteString;
import org.lealone.hansql.optimizer.util.DateString;
import org.lealone.hansql.optimizer.util.NlsString;
import org.lealone.hansql.optimizer.util.TimeString;
import org.lealone.hansql.optimizer.util.TimestampString;
import org.lealone.hansql.optimizer.util.Util;

/**
 * Standard implementation of {@link SqlNodeToRexConverter}.
 */
public class SqlNodeToRexConverterImpl implements SqlNodeToRexConverter {
  //~ Instance fields --------------------------------------------------------

  private final SqlRexConvertletTable convertletTable;

  //~ Constructors -----------------------------------------------------------

  SqlNodeToRexConverterImpl(SqlRexConvertletTable convertletTable) {
    this.convertletTable = convertletTable;
  }

  //~ Methods ----------------------------------------------------------------

  public RexNode convertCall(SqlRexContext cx, SqlCall call) {
    final SqlRexConvertlet convertlet = convertletTable.get(call);
    if (convertlet != null) {
      return convertlet.convertCall(cx, call);
    }

    // No convertlet was suitable. (Unlikely, because the standard
    // convertlet table has a fall-back for all possible calls.)
    throw Util.needToImplement(call);
  }

  public RexLiteral convertInterval(
      SqlRexContext cx,
      SqlIntervalQualifier intervalQualifier) {
    RexBuilder rexBuilder = cx.getRexBuilder();

    return rexBuilder.makeIntervalLiteral(intervalQualifier);
  }

  public RexNode convertLiteral(
      SqlRexContext cx,
      SqlLiteral literal) {
    RexBuilder rexBuilder = cx.getRexBuilder();
    RelDataTypeFactory typeFactory = cx.getTypeFactory();
    SqlValidator validator = cx.getValidator();
    if (literal.getValue() == null) {
      // Since there is no eq. RexLiteral of SqlLiteral.Unknown we
      // treat it as a cast(null as boolean)
      RelDataType type;
      if (literal.getTypeName() == SqlTypeName.BOOLEAN) {
        type = typeFactory.createSqlType(SqlTypeName.BOOLEAN);
        type = typeFactory.createTypeWithNullability(type, true);
      } else {
        type = validator.getValidatedNodeType(literal);
      }
      return rexBuilder.makeCast(
          type,
          rexBuilder.constantNull());
    }

    BitString bitString;
    SqlIntervalLiteral.IntervalValue intervalValue;
    long l;

    switch (literal.getTypeName()) {
    case DECIMAL:
      // exact number
      BigDecimal bd = literal.getValueAs(BigDecimal.class);
      return rexBuilder.makeExactLiteral(
          bd,
          literal.createSqlType(typeFactory));

    case DOUBLE:
      // approximate type
      // TODO:  preserve fixed-point precision and large integers
      return rexBuilder.makeApproxLiteral(literal.getValueAs(BigDecimal.class));

    case CHAR:
      return rexBuilder.makeCharLiteral(literal.getValueAs(NlsString.class));
    case BOOLEAN:
      return rexBuilder.makeLiteral(literal.getValueAs(Boolean.class));
    case BINARY:
      bitString = literal.getValueAs(BitString.class);
      Preconditions.checkArgument((bitString.getBitCount() % 8) == 0,
          "incomplete octet");

      // An even number of hexits (e.g. X'ABCD') makes whole number
      // of bytes.
      ByteString byteString = new ByteString(bitString.getAsByteArray());
      return rexBuilder.makeBinaryLiteral(byteString);
    case SYMBOL:
      return rexBuilder.makeFlag(literal.getValueAs(Enum.class));
    case TIMESTAMP:
      return rexBuilder.makeTimestampLiteral(
          literal.getValueAs(TimestampString.class),
          ((SqlTimestampLiteral) literal).getPrec());
    case TIME:
      return rexBuilder.makeTimeLiteral(
          literal.getValueAs(TimeString.class),
          ((SqlTimeLiteral) literal).getPrec());
    case DATE:
      return rexBuilder.makeDateLiteral(literal.getValueAs(DateString.class));

    case INTERVAL_YEAR:
    case INTERVAL_YEAR_MONTH:
    case INTERVAL_MONTH:
    case INTERVAL_DAY:
    case INTERVAL_DAY_HOUR:
    case INTERVAL_DAY_MINUTE:
    case INTERVAL_DAY_SECOND:
    case INTERVAL_HOUR:
    case INTERVAL_HOUR_MINUTE:
    case INTERVAL_HOUR_SECOND:
    case INTERVAL_MINUTE:
    case INTERVAL_MINUTE_SECOND:
    case INTERVAL_SECOND:
      SqlIntervalQualifier sqlIntervalQualifier =
          literal.getValueAs(SqlIntervalLiteral.IntervalValue.class)
              .getIntervalQualifier();
      return rexBuilder.makeIntervalLiteral(
          literal.getValueAs(BigDecimal.class),
          sqlIntervalQualifier);
    default:
      throw Util.unexpected(literal.getTypeName());
    }
  }
}

// End SqlNodeToRexConverterImpl.java
