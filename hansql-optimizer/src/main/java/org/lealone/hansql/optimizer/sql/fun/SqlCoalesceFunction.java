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

import java.util.List;

import org.lealone.hansql.optimizer.sql.SqlCall;
import org.lealone.hansql.optimizer.sql.SqlFunction;
import org.lealone.hansql.optimizer.sql.SqlFunctionCategory;
import org.lealone.hansql.optimizer.sql.SqlKind;
import org.lealone.hansql.optimizer.sql.SqlNode;
import org.lealone.hansql.optimizer.sql.SqlNodeList;
import org.lealone.hansql.optimizer.sql.parser.SqlParserPos;
import org.lealone.hansql.optimizer.sql.type.OperandTypes;
import org.lealone.hansql.optimizer.sql.type.ReturnTypes;
import org.lealone.hansql.optimizer.sql.type.SqlTypeTransforms;
import org.lealone.hansql.optimizer.sql.validate.SqlValidator;
import org.lealone.hansql.optimizer.util.Util;

/**
 * The <code>COALESCE</code> function.
 */
public class SqlCoalesceFunction extends SqlFunction {
  //~ Constructors -----------------------------------------------------------

  public SqlCoalesceFunction() {
    // NOTE jvs 26-July-2006:  We fill in the type strategies here,
    // but normally they are not used because the validator invokes
    // rewriteCall to convert COALESCE into CASE early.  However,
    // validator rewrite can optionally be disabled, in which case these
    // strategies are used.
    super("COALESCE",
        SqlKind.COALESCE,
        ReturnTypes.cascade(ReturnTypes.LEAST_RESTRICTIVE,
            SqlTypeTransforms.LEAST_NULLABLE),
        null,
        OperandTypes.SAME_VARIADIC,
        SqlFunctionCategory.SYSTEM);
  }

  //~ Methods ----------------------------------------------------------------

  // override SqlOperator
  public SqlNode rewriteCall(SqlValidator validator, SqlCall call) {
    validateQuantifier(validator, call); // check DISTINCT/ALL

    List<SqlNode> operands = call.getOperandList();

    if (operands.size() == 1) {
      // No CASE needed
      return operands.get(0);
    }

    SqlParserPos pos = call.getParserPosition();

    SqlNodeList whenList = new SqlNodeList(pos);
    SqlNodeList thenList = new SqlNodeList(pos);

    // todo: optimize when know operand is not null.

    for (SqlNode operand : Util.skipLast(operands)) {
      whenList.add(
          SqlStdOperatorTable.IS_NOT_NULL.createCall(pos, operand));
      thenList.add(SqlNode.clone(operand));
    }
    SqlNode elseExpr = Util.last(operands);
    assert call.getFunctionQuantifier() == null;
    return SqlCase.createSwitched(pos, null, whenList, thenList, elseExpr);
  }
}

// End SqlCoalesceFunction.java
