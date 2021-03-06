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
package org.lealone.hansql.optimizer.sql.dialect;

import org.lealone.hansql.optimizer.config.NullCollation;
import org.lealone.hansql.optimizer.rel.type.RelDataType;
import org.lealone.hansql.optimizer.rel.type.RelDataTypeSystem;
import org.lealone.hansql.optimizer.sql.SqlBasicCall;
import org.lealone.hansql.optimizer.sql.SqlCall;
import org.lealone.hansql.optimizer.sql.SqlDataTypeSpec;
import org.lealone.hansql.optimizer.sql.SqlDialect;
import org.lealone.hansql.optimizer.sql.SqlFunction;
import org.lealone.hansql.optimizer.sql.SqlFunctionCategory;
import org.lealone.hansql.optimizer.sql.SqlIdentifier;
import org.lealone.hansql.optimizer.sql.SqlIntervalQualifier;
import org.lealone.hansql.optimizer.sql.SqlKind;
import org.lealone.hansql.optimizer.sql.SqlLiteral;
import org.lealone.hansql.optimizer.sql.SqlNode;
import org.lealone.hansql.optimizer.sql.SqlNodeList;
import org.lealone.hansql.optimizer.sql.SqlSelect;
import org.lealone.hansql.optimizer.sql.SqlWriter;
import org.lealone.hansql.optimizer.sql.fun.SqlCase;
import org.lealone.hansql.optimizer.sql.fun.SqlStdOperatorTable;
import org.lealone.hansql.optimizer.sql.parser.SqlParserPos;
import org.lealone.hansql.optimizer.sql.type.InferTypes;
import org.lealone.hansql.optimizer.sql.type.OperandTypes;
import org.lealone.hansql.optimizer.sql.type.ReturnTypes;
import org.lealone.hansql.optimizer.util.TimeUnit;
import org.lealone.hansql.optimizer.util.TimeUnitRange;

/**
 * A <code>SqlDialect</code> implementation for the MySQL database.
 */
public class MysqlSqlDialect extends SqlDialect {
  public static final SqlDialect DEFAULT =
      new MysqlSqlDialect(EMPTY_CONTEXT
          .withDatabaseProduct(DatabaseProduct.MYSQL)
          .withIdentifierQuoteString("`")
          .withNullCollation(NullCollation.LOW));

  /** MySQL specific function. */
  public static final SqlFunction ISNULL_FUNCTION =
      new SqlFunction("ISNULL", SqlKind.OTHER_FUNCTION,
          ReturnTypes.BOOLEAN, InferTypes.FIRST_KNOWN,
          OperandTypes.ANY, SqlFunctionCategory.SYSTEM);

  /** Creates a MysqlSqlDialect. */
  public MysqlSqlDialect(Context context) {
    super(context);
  }

  @Override public boolean supportsCharSet() {
    return false;
  }

  @Override public void unparseOffsetFetch(SqlWriter writer, SqlNode offset,
      SqlNode fetch) {
    unparseFetchUsingLimit(writer, offset, fetch);
  }

  @Override public SqlNode emulateNullDirection(SqlNode node,
      boolean nullsFirst, boolean desc) {
    return emulateNullDirectionWithIsNull(node, nullsFirst, desc);
  }

  @Override public boolean supportsAggregateFunction(SqlKind kind) {
    switch (kind) {
    case COUNT:
    case SUM:
    case SUM0:
    case MIN:
    case MAX:
    case SINGLE_VALUE:
      return true;
    }
    return false;
  }

  @Override public boolean supportsNestedAggregations() {
    return false;
  }

  @Override public CalendarPolicy getCalendarPolicy() {
    return CalendarPolicy.SHIFT;
  }

  @Override public SqlNode getCastSpec(RelDataType type) {
    switch (type.getSqlTypeName()) {
    case VARCHAR:
      // MySQL doesn't have a VARCHAR type, only CHAR.
      return new SqlDataTypeSpec(new SqlIdentifier("CHAR", SqlParserPos.ZERO),
          type.getPrecision(), -1, null, null, SqlParserPos.ZERO);
    case INTEGER:
    case BIGINT:
      return new SqlDataTypeSpec(new SqlIdentifier("_SIGNED", SqlParserPos.ZERO),
          type.getPrecision(), -1, null, null, SqlParserPos.ZERO);
    }
    return super.getCastSpec(type);
  }

  @Override public SqlNode rewriteSingleValueExpr(SqlNode aggCall) {
    final SqlNode operand = ((SqlBasicCall) aggCall).operand(0);
    final SqlLiteral nullLiteral = SqlLiteral.createNull(SqlParserPos.ZERO);
    final SqlNode unionOperand = new SqlSelect(SqlParserPos.ZERO, SqlNodeList.EMPTY,
        SqlNodeList.of(nullLiteral), null, null, null, null, SqlNodeList.EMPTY, null, null, null);
    // For MySQL, generate
    //   CASE COUNT(*)
    //   WHEN 0 THEN NULL
    //   WHEN 1 THEN <result>
    //   ELSE (SELECT NULL UNION ALL SELECT NULL)
    //   END
    final SqlNode caseExpr =
        new SqlCase(SqlParserPos.ZERO,
            SqlStdOperatorTable.COUNT.createCall(SqlParserPos.ZERO, operand),
            SqlNodeList.of(
                SqlLiteral.createExactNumeric("0", SqlParserPos.ZERO),
                SqlLiteral.createExactNumeric("1", SqlParserPos.ZERO)),
            SqlNodeList.of(
                nullLiteral,
                operand),
            SqlStdOperatorTable.SCALAR_QUERY.createCall(SqlParserPos.ZERO,
                SqlStdOperatorTable.UNION_ALL
                    .createCall(SqlParserPos.ZERO, unionOperand, unionOperand)));

    LOGGER.debug("SINGLE_VALUE rewritten into [{}]", caseExpr);

    return caseExpr;
  }

  @Override public void unparseCall(SqlWriter writer, SqlCall call,
      int leftPrec, int rightPrec) {
    switch (call.getKind()) {
    case FLOOR:
      if (call.operandCount() != 2) {
        super.unparseCall(writer, call, leftPrec, rightPrec);
        return;
      }

      unparseFloor(writer, call);
      break;

    default:
      super.unparseCall(writer, call, leftPrec, rightPrec);
    }
  }

  /**
   * Unparses datetime floor for MySQL. There is no TRUNC function, so simulate
   * this using calls to DATE_FORMAT.
   *
   * @param writer Writer
   * @param call Call
   */
  private void unparseFloor(SqlWriter writer, SqlCall call) {
    SqlLiteral node = call.operand(1);
    TimeUnitRange unit = (TimeUnitRange) node.getValue();

    if (unit == TimeUnitRange.WEEK) {
      writer.print("STR_TO_DATE");
      SqlWriter.Frame frame = writer.startList("(", ")");

      writer.print("DATE_FORMAT(");
      call.operand(0).unparse(writer, 0, 0);
      writer.print(", '%x%v-1'), '%x%v-%w'");
      writer.endList(frame);
      return;
    }

    String format;
    switch (unit) {
    case YEAR:
      format = "%Y-01-01";
      break;
    case MONTH:
      format = "%Y-%m-01";
      break;
    case DAY:
      format = "%Y-%m-%d";
      break;
    case HOUR:
      format = "%Y-%m-%d %H:00:00";
      break;
    case MINUTE:
      format = "%Y-%m-%d %H:%i:00";
      break;
    case SECOND:
      format = "%Y-%m-%d %H:%i:%s";
      break;
    default:
      throw new AssertionError("MYSQL does not support FLOOR for time unit: "
          + unit);
    }

    writer.print("DATE_FORMAT");
    SqlWriter.Frame frame = writer.startList("(", ")");
    call.operand(0).unparse(writer, 0, 0);
    writer.sep(",", true);
    writer.print("'" + format + "'");
    writer.endList(frame);
  }


  @Override public void unparseSqlIntervalQualifier(SqlWriter writer,
      SqlIntervalQualifier qualifier, RelDataTypeSystem typeSystem) {

    //  Unit Value         | Expected Format
    // --------------------+-------------------------------------------
    //  MICROSECOND        | MICROSECONDS
    //  SECOND             | SECONDS
    //  MINUTE             | MINUTES
    //  HOUR               | HOURS
    //  DAY                | DAYS
    //  WEEK               | WEEKS
    //  MONTH              | MONTHS
    //  QUARTER            | QUARTERS
    //  YEAR               | YEARS
    //  MINUTE_SECOND      | 'MINUTES:SECONDS'
    //  HOUR_MINUTE        | 'HOURS:MINUTES'
    //  DAY_HOUR           | 'DAYS HOURS'
    //  YEAR_MONTH         | 'YEARS-MONTHS'
    //  MINUTE_MICROSECOND | 'MINUTES:SECONDS.MICROSECONDS'
    //  HOUR_MICROSECOND   | 'HOURS:MINUTES:SECONDS.MICROSECONDS'
    //  SECOND_MICROSECOND | 'SECONDS.MICROSECONDS'
    //  DAY_MINUTE         | 'DAYS HOURS:MINUTES'
    //  DAY_MICROSECOND    | 'DAYS HOURS:MINUTES:SECONDS.MICROSECONDS'
    //  DAY_SECOND         | 'DAYS HOURS:MINUTES:SECONDS'
    //  HOUR_SECOND        | 'HOURS:MINUTES:SECONDS'

    if (!qualifier.useDefaultFractionalSecondPrecision()) {
      throw new AssertionError("Fractional second precision is not supported now ");
    }

    final String start = validate(qualifier.timeUnitRange.startUnit).name();
    if (qualifier.timeUnitRange.startUnit == TimeUnit.SECOND
        || qualifier.timeUnitRange.endUnit == null) {
      writer.keyword(start);
    } else {
      writer.keyword(start + "_" + qualifier.timeUnitRange.endUnit.name());
    }
  }

  private TimeUnit validate(TimeUnit timeUnit) {
    switch (timeUnit) {
    case MICROSECOND:
    case SECOND:
    case MINUTE:
    case HOUR:
    case DAY:
    case WEEK:
    case MONTH:
    case QUARTER:
    case YEAR:
      return timeUnit;
    default:
      throw new AssertionError(" Time unit " + timeUnit + "is not supported now.");
    }
  }
}

// End MysqlSqlDialect.java
