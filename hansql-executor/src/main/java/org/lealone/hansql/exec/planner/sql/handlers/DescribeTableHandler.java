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
package org.lealone.hansql.exec.planner.sql.handlers;

import static org.lealone.hansql.exec.store.ischema.InfoSchemaConstants.COLS_COL_COLUMN_NAME;
import static org.lealone.hansql.exec.store.ischema.InfoSchemaConstants.COLS_COL_DATA_TYPE;
import static org.lealone.hansql.exec.store.ischema.InfoSchemaConstants.COLS_COL_IS_NULLABLE;
import static org.lealone.hansql.exec.store.ischema.InfoSchemaConstants.IS_SCHEMA_NAME;
import static org.lealone.hansql.exec.store.ischema.InfoSchemaConstants.SHRD_COL_TABLE_NAME;
import static org.lealone.hansql.exec.store.ischema.InfoSchemaConstants.SHRD_COL_TABLE_SCHEMA;

import java.util.Arrays;
import java.util.List;

import org.lealone.hansql.common.exceptions.UserException;
import org.lealone.hansql.exec.planner.sql.SchemaUtilites;
import org.lealone.hansql.exec.planner.sql.SqlConverter;
import org.lealone.hansql.exec.planner.sql.parser.DrillParserUtil;
import org.lealone.hansql.exec.planner.sql.parser.DrillSqlDescribeTable;
import org.lealone.hansql.exec.store.AbstractSchema;
import org.lealone.hansql.exec.store.ischema.InfoSchemaTableType;
import org.lealone.hansql.exec.work.exception.SqlExecutorSetupException;
import org.lealone.hansql.optimizer.rel.type.RelDataType;
import org.lealone.hansql.optimizer.schema.SchemaPlus;
import org.lealone.hansql.optimizer.sql.SqlCharStringLiteral;
import org.lealone.hansql.optimizer.sql.SqlIdentifier;
import org.lealone.hansql.optimizer.sql.SqlLiteral;
import org.lealone.hansql.optimizer.sql.SqlNode;
import org.lealone.hansql.optimizer.sql.SqlNodeList;
import org.lealone.hansql.optimizer.sql.SqlSelect;
import org.lealone.hansql.optimizer.sql.fun.SqlStdOperatorTable;
import org.lealone.hansql.optimizer.sql.parser.SqlParserPos;
import org.lealone.hansql.optimizer.tools.RelConversionException;
import org.lealone.hansql.optimizer.tools.ValidationException;
import org.lealone.hansql.optimizer.util.NlsString;
import org.lealone.hansql.optimizer.util.Pair;
import org.lealone.hansql.optimizer.util.Util;

public class DescribeTableHandler extends DefaultSqlHandler {
  private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(DescribeTableHandler.class);

  public DescribeTableHandler(SqlHandlerConfig config) { super(config); }

  /** Rewrite the parse tree as SELECT ... FROM INFORMATION_SCHEMA.COLUMNS ... */
  @Override
  public SqlNode rewrite(SqlNode sqlNode) throws SqlExecutorSetupException {
    DrillSqlDescribeTable node = unwrap(sqlNode, DrillSqlDescribeTable.class);

    try {
      List<SqlNode> selectList = Arrays.asList(
          new SqlIdentifier(COLS_COL_COLUMN_NAME, SqlParserPos.ZERO),
          new SqlIdentifier(COLS_COL_DATA_TYPE, SqlParserPos.ZERO),
          new SqlIdentifier(COLS_COL_IS_NULLABLE, SqlParserPos.ZERO));

      SqlNode fromClause = new SqlIdentifier(Arrays.asList(IS_SCHEMA_NAME, InfoSchemaTableType.COLUMNS.name()), SqlParserPos.ZERO);

      SchemaPlus defaultSchema = config.getConverter().getDefaultSchema();
      List<String> schemaPathGivenInCmd = Util.skipLast(node.getTable().names);
      SchemaPlus schema = SchemaUtilites.findSchema(defaultSchema, schemaPathGivenInCmd);

      if (schema == null) {
        SchemaUtilites.throwSchemaNotFoundException(defaultSchema, SchemaUtilites.getSchemaPath(schemaPathGivenInCmd));
      }

      if (SchemaUtilites.isRootSchema(schema)) {
        throw UserException.validationError()
            .message("No schema selected.")
            .build(logger);
      }

      // find resolved schema path
      AbstractSchema drillSchema = SchemaUtilites.unwrapAsDrillSchemaInstance(schema);
      String schemaPath = drillSchema.getFullSchemaName();

      String tableName = Util.last(node.getTable().names);

      if (schema.getTable(tableName) == null) {
        throw UserException.validationError()
            .message("Unknown table [%s] in schema [%s]", tableName, schemaPath)
            .build(logger);
      }

      SqlNode schemaCondition = null;
      if (!SchemaUtilites.isRootSchema(schema)) {
        schemaCondition = DrillParserUtil.createCondition(
            new SqlIdentifier(SHRD_COL_TABLE_SCHEMA, SqlParserPos.ZERO),
            SqlStdOperatorTable.EQUALS,
            SqlLiteral.createCharString(schemaPath, Util.getDefaultCharset().name(), SqlParserPos.ZERO)
        );
      }

      SqlNode tableNameColumn = new SqlIdentifier(SHRD_COL_TABLE_NAME, SqlParserPos.ZERO);

      // if table names are case insensitive, wrap column values and condition in lower function
      if (!drillSchema.areTableNamesCaseSensitive()) {
        tableNameColumn = SqlStdOperatorTable.LOWER.createCall(SqlParserPos.ZERO, tableNameColumn);
        tableName = tableName.toLowerCase();
      }

      SqlNode where = DrillParserUtil.createCondition(tableNameColumn,
          SqlStdOperatorTable.EQUALS,
          SqlLiteral.createCharString(tableName, Util.getDefaultCharset().name(), SqlParserPos.ZERO));

      where = DrillParserUtil.createCondition(schemaCondition, SqlStdOperatorTable.AND, where);

      SqlNode columnFilter = null;
      if (node.getColumn() != null) {
        columnFilter =
            DrillParserUtil.createCondition(
                SqlStdOperatorTable.LOWER.createCall(SqlParserPos.ZERO, new SqlIdentifier(COLS_COL_COLUMN_NAME, SqlParserPos.ZERO)),
                SqlStdOperatorTable.EQUALS,
                SqlLiteral.createCharString(node.getColumn().toString().toLowerCase(), Util.getDefaultCharset().name(), SqlParserPos.ZERO));
      } else if (node.getColumnQualifier() != null) {
        SqlNode columnQualifier = node.getColumnQualifier();
        SqlNode column = new SqlIdentifier(COLS_COL_COLUMN_NAME, SqlParserPos.ZERO);
        if (columnQualifier instanceof SqlCharStringLiteral) {
          NlsString conditionString = ((SqlCharStringLiteral) columnQualifier).getNlsString();
          columnQualifier = SqlCharStringLiteral.createCharString(
              conditionString.getValue().toLowerCase(),
              conditionString.getCharsetName(),
              columnQualifier.getParserPosition());
          column = SqlStdOperatorTable.LOWER.createCall(SqlParserPos.ZERO, column);
        }
        columnFilter = DrillParserUtil.createCondition(column, SqlStdOperatorTable.LIKE, columnQualifier);
      }

      where = DrillParserUtil.createCondition(where, SqlStdOperatorTable.AND, columnFilter);

      return new SqlSelect(SqlParserPos.ZERO, null, new SqlNodeList(selectList, SqlParserPos.ZERO),
          fromClause, where, null, null, null, null, null, null);
    } catch (Exception ex) {
      throw UserException.planError(ex)
          .message("Error while rewriting DESCRIBE query: %d", ex.getMessage())
          .build(logger);
    }
  }

  @Override
  protected Pair<SqlNode, RelDataType> validateNode(SqlNode sqlNode) throws ValidationException,
      RelConversionException, SqlExecutorSetupException {
    SqlConverter converter = config.getConverter();
    // set this to true since INFORMATION_SCHEMA in the root schema, not in the default
    converter.useRootSchemaAsDefault(true);
    Pair<SqlNode, RelDataType> sqlNodeRelDataTypePair = super.validateNode(sqlNode);
    converter.useRootSchemaAsDefault(false);
    return sqlNodeRelDataTypePair;
  }
}
