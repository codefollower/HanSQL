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
<@pp.dropOutputFile />

<#list cast.types as type>
<#if type.major == ("IntDecimal") || type.major == ("BigIntDecimal")>
<@pp.changeOutputFile name="/org/lealone/hansql/exec/expr/fn/impl/gcast/Cast${type.from}${type.to}.java" />

<#include "/@includes/license.ftl" />

package org.lealone.hansql.exec.expr.fn.impl.gcast;

<#include "/@includes/vv_imports.ftl" />

import org.lealone.hansql.exec.expr.DrillSimpleFunc;
import org.lealone.hansql.exec.expr.annotations.FunctionTemplate;
import org.lealone.hansql.exec.expr.annotations.FunctionTemplate.NullHandling;
import org.lealone.hansql.exec.expr.annotations.Output;
import org.lealone.hansql.exec.expr.annotations.Param;
import org.lealone.hansql.exec.expr.holders.*;
import org.lealone.hansql.exec.record.RecordBatch;
import org.lealone.hansql.exec.util.DecimalUtility;
import org.lealone.hansql.exec.expr.annotations.Workspace;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.DrillBuf;

import java.nio.ByteBuffer;

/*
 * This class is generated using freemarker and the ${.template_name} template.
 */

@SuppressWarnings("unused")
@FunctionTemplate(name = "cast${type.to?upper_case}",
                  scope = FunctionTemplate.FunctionScope.SIMPLE,
                  returnType = FunctionTemplate.ReturnType.DECIMAL_CAST,
                  nulls = NullHandling.NULL_IF_NULL)
public class Cast${type.from}${type.to} implements DrillSimpleFunc {
  @Param ${type.from}Holder in;
  @Inject DrillBuf buffer;
  @Param IntHolder precision;
  @Param IntHolder scale;
  @Output ${type.to}Holder out;

  public void setup() {
  }

  public void eval() {
    out.scale = scale.value;
    out.precision = precision.value;

    out.start = 0;
    out.buffer = buffer;
    java.math.BigDecimal bd = new java.math.BigDecimal(in.value);

    org.lealone.hansql.exec.util.DecimalUtility.checkValueOverflow(bd, precision.value, scale.value);

    bd = bd.setScale(out.scale, java.math.BigDecimal.ROUND_DOWN);

    byte[] bytes = bd.unscaledValue().toByteArray();
    int len = bytes.length;
    out.buffer = out.buffer.reallocIfNeeded(len);
    out.buffer.setBytes(out.start, bytes);
    out.end = out.start + len;
  }
}
</#if> <#-- type.major -->
</#list>
