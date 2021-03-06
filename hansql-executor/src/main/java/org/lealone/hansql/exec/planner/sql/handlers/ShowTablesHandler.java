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

import static org.lealone.hansql.exec.store.ischema.InfoSchemaConstants.IS_SCHEMA_NAME;
import static org.lealone.hansql.exec.store.ischema.InfoSchemaConstants.SHRD_COL_TABLE_NAME;
import static org.lealone.hansql.exec.store.ischema.InfoSchemaConstants.SHRD_COL_TABLE_SCHEMA;

import java.util.Arrays;
import java.util.List;

import org.lealone.hansql.common.exceptions.UserException;
import org.lealone.hansql.exec.planner.sql.SchemaUtilites;
import org.lealone.hansql.exec.planner.sql.SqlConverter;
import org.lealone.hansql.exec.planner.sql.parser.DrillParserUtil;
import org.lealone.hansql.exec.planner.sql.parser.SqlShowTables;
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

public class ShowTablesHandler extends DefaultSqlHandler {
  private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ShowTablesHandler.class);

  public ShowTablesHandler(SqlHandlerConfig config) { super(config); }

  /** Rewrite the parse tree as SELECT ... FROM INFORMATION_SCHEMA.`TABLES` ... */
  @Override
  public SqlNode rewrite(SqlNode sqlNode) throws SqlExecutorSetupException {
    SqlShowTables node = unwrap(sqlNode, SqlShowTables.class);
    List<SqlNode> selectList = Arrays.asList(
        new SqlIdentifier(SHRD_COL_TABLE_SCHEMA, SqlParserPos.ZERO),
        new SqlIdentifier(SHRD_COL_TABLE_NAME, SqlParserPos.ZERO));

    SqlNode fromClause = new SqlIdentifier(Arrays.asList(IS_SCHEMA_NAME, InfoSchemaTableType.TABLES.name()), SqlParserPos.ZERO);

    SchemaPlus schemaPlus;
    if (node.getDb() != null) {
      List<String> schemaNames = node.getDb().names;
      schemaPlus = SchemaUtilites.findSchema(config.getConverter().getDefaultSchema(), schemaNames);

      if (schemaPlus == null) {
        throw UserException.validationError()
            .message(String.format("Invalid schema name [%s]", SchemaUtilites.getSchemaPath(schemaNames)))
            .build(logger);
      }

    } else {
      // If no schema is given in SHOW TABLES command, list tables from current schema
      schemaPlus = config.getConverter().getDefaultSchema();
    }

    if (SchemaUtilites.isRootSchema(schemaPlus)) {
      // If the default schema is a root schema, throw an error to select a default schema
      throw UserException.validationError()
          .message("No default schema selected. Select a schema using 'USE schema' command")
          .build(logger);
    }

    AbstractSchema drillSchema = SchemaUtilites.unwrapAsDrillSchemaInstance(schemaPlus);

    SqlNode where = DrillParserUtil.createCondition(
        new SqlIdentifier(SHRD_COL_TABLE_SCHEMA, SqlParserPos.ZERO),
        SqlStdOperatorTable.EQUALS,
        SqlLiteral.createCharString(drillSchema.getFullSchemaName(), Util.getDefaultCharset().name(), SqlParserPos.ZERO));

    SqlNode filter = null;
    if (node.getLikePattern() != null) {
      SqlNode likePattern = node.getLikePattern();
      SqlNode column = new SqlIdentifier(SHRD_COL_TABLE_NAME, SqlParserPos.ZERO);
      // wrap columns name values and condition in lower function if case insensitive
      if (!drillSchema.areTableNamesCaseSensitive() && likePattern instanceof SqlCharStringLiteral) {
        NlsString conditionString = ((SqlCharStringLiteral) likePattern).getNlsString();
        likePattern = SqlCharStringLiteral.createCharString(
            conditionString.getValue().toLowerCase(),
            conditionString.getCharsetName(),
            likePattern.getParserPosition());
        column = SqlStdOperatorTable.LOWER.createCall(SqlParserPos.ZERO, column);
      }
      filter = DrillParserUtil.createCondition(column, SqlStdOperatorTable.LIKE, likePattern);
    } else if (node.getWhereClause() != null) {
      filter = node.getWhereClause();
    }

    where = DrillParserUtil.createCondition(where, SqlStdOperatorTable.AND, filter);

    return new SqlSelect(SqlParserPos.ZERO, null, new SqlNodeList(selectList, SqlParserPos.ZERO),
        fromClause, where, null, null, null, null, null, null);
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