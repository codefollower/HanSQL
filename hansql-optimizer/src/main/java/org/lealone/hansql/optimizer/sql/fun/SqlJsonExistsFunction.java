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
import org.lealone.hansql.optimizer.sql.SqlKind;
import org.lealone.hansql.optimizer.sql.SqlWriter;
import org.lealone.hansql.optimizer.sql.type.OperandTypes;
import org.lealone.hansql.optimizer.sql.type.ReturnTypes;

/**
 * The <code>JSON_EXISTS</code> function.
 */
public class SqlJsonExistsFunction extends SqlFunction {
  public SqlJsonExistsFunction() {
    super("JSON_EXISTS", SqlKind.OTHER_FUNCTION,
        ReturnTypes.BOOLEAN_FORCE_NULLABLE, null,
        OperandTypes.or(OperandTypes.ANY, OperandTypes.ANY_ANY),
        SqlFunctionCategory.SYSTEM);
  }

  @Override public String getSignatureTemplate(int operandsCount) {
    assert operandsCount == 1 || operandsCount == 2;
    if (operandsCount == 1) {
      return "{0}({1})";
    }
    return "{0}({1} {2} ON ERROR)";
  }

  @Override public void unparse(SqlWriter writer, SqlCall call, int leftPrec,
      int rightPrec) {
    final SqlWriter.Frame frame = writer.startFunCall(getName());
    call.operand(0).unparse(writer, 0, 0);
    if (call.operandCount() == 2) {
      call.operand(1).unparse(writer, 0, 0);
      writer.keyword("ON ERROR");
    }
    writer.endFunCall(frame);
  }
}

// End SqlJsonExistsFunction.java
