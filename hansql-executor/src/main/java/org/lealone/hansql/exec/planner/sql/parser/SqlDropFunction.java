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
package org.lealone.hansql.exec.planner.sql.parser;

import org.apache.drill.shaded.guava.com.google.common.collect.Lists;
import org.lealone.hansql.exec.planner.sql.handlers.AbstractSqlHandler;
import org.lealone.hansql.exec.planner.sql.handlers.DropFunctionHandler;
import org.lealone.hansql.exec.planner.sql.handlers.SqlHandlerConfig;
import org.lealone.hansql.optimizer.sql.SqlCall;
import org.lealone.hansql.optimizer.sql.SqlKind;
import org.lealone.hansql.optimizer.sql.SqlLiteral;
import org.lealone.hansql.optimizer.sql.SqlNode;
import org.lealone.hansql.optimizer.sql.SqlOperator;
import org.lealone.hansql.optimizer.sql.SqlSpecialOperator;
import org.lealone.hansql.optimizer.sql.SqlWriter;
import org.lealone.hansql.optimizer.sql.parser.SqlParserPos;

import java.util.List;

public class SqlDropFunction extends DrillSqlCall {

  private final SqlNode jar;

  public static final SqlSpecialOperator OPERATOR = new SqlSpecialOperator("DROP_FUNCTION", SqlKind.OTHER_DDL) {
    @Override
    public SqlCall createCall(SqlLiteral functionQualifier, SqlParserPos pos, SqlNode... operands) {
      return new SqlDropFunction(pos, operands[0]);
    }
  };

  public SqlDropFunction(SqlParserPos pos, SqlNode jar) {
    super(pos);
    this.jar = jar;
  }

  @Override
  public SqlOperator getOperator() {
    return OPERATOR;
  }

  @Override
  public List<SqlNode> getOperandList() {
    List<SqlNode> opList = Lists.newArrayList();
    opList.add(jar);
    return opList;
  }

  @Override
  public void unparse(SqlWriter writer, int leftPrec, int rightPrec) {
    writer.keyword("DROP");
    writer.keyword("FUNCTION");
    writer.keyword("USING");
    writer.keyword("JAR");
    jar.unparse(writer, leftPrec, rightPrec);
  }

  @Override
  public AbstractSqlHandler getSqlHandler(SqlHandlerConfig config) {
    return new DropFunctionHandler(config);
  }

  public SqlNode getJar() { return jar; }

}
