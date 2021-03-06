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
package org.lealone.hansql.exec.expr.fn.impl;

import org.lealone.hansql.exec.expr.holders.BigIntHolder;
import org.lealone.hansql.exec.expr.holders.VarDecimalHolder;
import org.lealone.hansql.exec.expr.DrillSimpleFunc;
import org.lealone.hansql.exec.expr.annotations.FunctionTemplate;
import org.lealone.hansql.exec.expr.annotations.Output;
import org.lealone.hansql.exec.expr.annotations.Param;
import org.lealone.hansql.exec.expr.annotations.FunctionTemplate.FunctionScope;
import org.lealone.hansql.exec.expr.holders.NullableVarDecimalHolder;
import org.lealone.hansql.exec.expr.holders.Float4Holder;
import org.lealone.hansql.exec.expr.holders.Float8Holder;
import org.lealone.hansql.exec.expr.holders.IntHolder;
import org.lealone.hansql.exec.expr.holders.NullableBigIntHolder;
import org.lealone.hansql.exec.expr.holders.NullableFloat4Holder;
import org.lealone.hansql.exec.expr.holders.NullableFloat8Holder;
import org.lealone.hansql.exec.expr.holders.NullableIntHolder;

/**
 * hash32 function definitions for numeric data types. These functions cast the input numeric value to a
 * double before doing the hashing. See comments in {@link Hash64AsDouble} for the reason for doing this.
 */
@SuppressWarnings("unused")
public class Hash32AsDouble {
  @FunctionTemplate(name = "hash32AsDouble", scope = FunctionScope.SIMPLE, nulls = FunctionTemplate.NullHandling.INTERNAL)

  public static class NullableFloatHash implements DrillSimpleFunc {

    @Param
    NullableFloat4Holder in;
    @Output
    IntHolder out;

    public void setup() {
    }

    public void eval() {
      if (in.isSet == 0) {
        out.value = 0;
      } else {
        out.value = org.lealone.hansql.exec.expr.fn.impl.HashHelper.hash32(in.value, 0);
      }
    }
  }

  @FunctionTemplate(name = "hash32AsDouble", scope = FunctionScope.SIMPLE, nulls = FunctionTemplate.NullHandling.INTERNAL)
  public static class FloatHash implements DrillSimpleFunc {

    @Param
    Float4Holder in;
    @Output
    IntHolder out;

    public void setup() {
    }

    public void eval() {
      out.value = org.lealone.hansql.exec.expr.fn.impl.HashHelper.hash32(in.value, 0);
    }
  }

  @FunctionTemplate(name = "hash32AsDouble", scope = FunctionScope.SIMPLE, nulls = FunctionTemplate.NullHandling.INTERNAL)
  public static class NullableDoubleHash implements DrillSimpleFunc {

    @Param
    NullableFloat8Holder in;
    @Output
    IntHolder out;

    public void setup() {
    }

    public void eval() {
      if (in.isSet == 0) {
        out.value = 0;
      } else {
        out.value = org.lealone.hansql.exec.expr.fn.impl.HashHelper.hash32(in.value, 0);
      }
    }
  }

  @FunctionTemplate(name = "hash32AsDouble", scope = FunctionScope.SIMPLE, nulls = FunctionTemplate.NullHandling.INTERNAL)
  public static class DoubleHash implements DrillSimpleFunc {

    @Param
    Float8Holder in;
    @Output
    IntHolder out;

    public void setup() {
    }

    public void eval() {
      out.value = org.lealone.hansql.exec.expr.fn.impl.HashHelper.hash32(in.value, 0);
    }
  }

  @FunctionTemplate(name = "hash32AsDouble", scope = FunctionScope.SIMPLE, nulls = FunctionTemplate.NullHandling.INTERNAL)
  public static class NullableBigIntHash implements DrillSimpleFunc {

    @Param
    NullableBigIntHolder in;
    @Output
    IntHolder out;

    public void setup() {
    }

    public void eval() {
      if (in.isSet == 0) {
        out.value = 0;
      } else {
        out.value = org.lealone.hansql.exec.expr.fn.impl.HashHelper.hash32(in.value, 0);
      }
    }
  }

  @FunctionTemplate(name = "hash32AsDouble", scope = FunctionScope.SIMPLE, nulls = FunctionTemplate.NullHandling.INTERNAL)
  public static class NullableIntHash implements DrillSimpleFunc {
    @Param
    NullableIntHolder in;
    @Output
    IntHolder out;

    public void setup() {
    }

    public void eval() {
      if (in.isSet == 0) {
        out.value = 0;
      } else {
        out.value = org.lealone.hansql.exec.expr.fn.impl.HashHelper.hash32(in.value, 0);
      }
    }
  }

  @FunctionTemplate(name = "hash32AsDouble", scope = FunctionScope.SIMPLE, nulls = FunctionTemplate.NullHandling.INTERNAL)
  public static class BigIntHash implements DrillSimpleFunc {

    @Param
    BigIntHolder in;
    @Output
    IntHolder out;

    public void setup() {
    }

    public void eval() {
      out.value = org.lealone.hansql.exec.expr.fn.impl.HashHelper.hash32(in.value, 0);
    }
  }

  @FunctionTemplate(name = "hash32AsDouble", scope = FunctionScope.SIMPLE, nulls = FunctionTemplate.NullHandling.INTERNAL)
  public static class IntHash implements DrillSimpleFunc {
    @Param
    IntHolder in;
    @Output
    IntHolder out;

    public void setup() {
    }

    public void eval() {
      out.value = org.lealone.hansql.exec.expr.fn.impl.HashHelper.hash32(in.value, 0);
    }
  }

  @FunctionTemplate(name = "hash32AsDouble", scope = FunctionScope.SIMPLE, nulls = FunctionTemplate.NullHandling.INTERNAL)
  public static class VarDecimalHash implements DrillSimpleFunc {
    @Param
    VarDecimalHolder in;
    @Output
    IntHolder out;

    public void setup() {
    }

    public void eval() {
      java.math.BigDecimal input = org.lealone.hansql.exec.util.DecimalUtility.getBigDecimalFromDrillBuf(in.buffer,
              in.start, in.end - in.start, in.scale);
      out.value = org.lealone.hansql.exec.expr.fn.impl.HashHelper.hash32(input.doubleValue(), 0);
    }
  }

  @FunctionTemplate(name = "hash32AsDouble", scope = FunctionScope.SIMPLE, nulls = FunctionTemplate.NullHandling.INTERNAL)
  public static class NullableVarDecimalHash implements DrillSimpleFunc {
    @Param
    NullableVarDecimalHolder in;
    @Output
    IntHolder out;

    public void setup() {
    }

    public void eval() {
      if (in.isSet == 0) {
        out.value = 0;
      } else {
        java.math.BigDecimal input = org.lealone.hansql.exec.util.DecimalUtility.getBigDecimalFromDrillBuf(in.buffer,
                in.start, in.end - in.start, in.scale);
        out.value = org.lealone.hansql.exec.expr.fn.impl.HashHelper.hash32(input.doubleValue(), 0);
      }
    }
  }
}
