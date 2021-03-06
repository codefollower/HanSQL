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
package org.lealone.hansql.exec.expr.fn.impl.conv;

import org.lealone.hansql.exec.expr.holders.VarBinaryHolder;
import org.lealone.hansql.exec.expr.DrillSimpleFunc;
import org.lealone.hansql.exec.expr.annotations.FunctionTemplate;
import org.lealone.hansql.exec.expr.annotations.Output;
import org.lealone.hansql.exec.expr.annotations.FunctionTemplate.FunctionScope;
import org.lealone.hansql.exec.expr.annotations.FunctionTemplate.NullHandling;

/**
 * This and {@link DummyConvertFrom} class merely act as a placeholder so that Optiq
 * allows 'convert_to()' and 'convert_from()' functions in SQL.
 */
@FunctionTemplate(name = "convert_to", scope = FunctionScope.SIMPLE, nulls = NullHandling.NULL_IF_NULL,
                  outputWidthCalculatorType = FunctionTemplate.OutputWidthCalculatorType.DEFAULT)
public class DummyConvertTo implements DrillSimpleFunc {

  @Output VarBinaryHolder out;

  @Override
  public void setup() { }

  @Override
  public void eval() { }
}
