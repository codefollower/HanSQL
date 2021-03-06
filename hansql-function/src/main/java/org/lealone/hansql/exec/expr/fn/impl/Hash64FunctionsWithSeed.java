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
import org.lealone.hansql.exec.expr.holders.BitHolder;
import org.lealone.hansql.exec.expr.holders.DateHolder;
import org.lealone.hansql.exec.expr.holders.Decimal28SparseHolder;
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
import org.lealone.hansql.exec.expr.holders.NullableBitHolder;
import org.lealone.hansql.exec.expr.holders.NullableDateHolder;
import org.lealone.hansql.exec.expr.holders.NullableDecimal28SparseHolder;
import org.lealone.hansql.exec.expr.holders.NullableFloat4Holder;
import org.lealone.hansql.exec.expr.holders.NullableFloat8Holder;
import org.lealone.hansql.exec.expr.holders.NullableIntHolder;
import org.lealone.hansql.exec.expr.holders.NullableTimeHolder;
import org.lealone.hansql.exec.expr.holders.NullableTimeStampHolder;
import org.lealone.hansql.exec.expr.holders.NullableVar16CharHolder;
import org.lealone.hansql.exec.expr.holders.NullableVarBinaryHolder;
import org.lealone.hansql.exec.expr.holders.NullableVarCharHolder;
import org.lealone.hansql.exec.expr.holders.TimeHolder;
import org.lealone.hansql.exec.expr.holders.TimeStampHolder;
import org.lealone.hansql.exec.expr.holders.Var16CharHolder;
import org.lealone.hansql.exec.expr.holders.VarBinaryHolder;
import org.lealone.hansql.exec.expr.holders.VarCharHolder;

/*
 * Class contains hash64 function definitions for different data types.
 */
@SuppressWarnings("unused")
public class Hash64FunctionsWithSeed {
  @FunctionTemplate(name = "hash64", scope = FunctionScope.SIMPLE, nulls = FunctionTemplate.NullHandling.INTERNAL )
  public static class NullableFloatHash implements DrillSimpleFunc {

    @Param NullableFloat4Holder in;
    @Param BigIntHolder seed;
    @Output BigIntHolder out;


    public void setup() {
    }

    public void eval() {
      if (in.isSet == 0) {
        out.value = seed.value;
      } else {
        out.value = org.lealone.hansql.exec.expr.fn.impl.HashHelper.hash64(in.value, seed.value);
      }
    }
  }

  @FunctionTemplate(name = "hash64", scope = FunctionScope.SIMPLE, nulls = FunctionTemplate.NullHandling.INTERNAL )
  public static class FloatHash implements DrillSimpleFunc {

    @Param Float4Holder in;
    @Param BigIntHolder seed;
    @Output BigIntHolder out;


    public void setup() {
    }

    public void eval() {
      out.value = org.lealone.hansql.exec.expr.fn.impl.HashHelper.hash64(in.value, seed.value);
    }
  }

  @FunctionTemplate(name = "hash64", scope = FunctionScope.SIMPLE, nulls = FunctionTemplate.NullHandling.INTERNAL )
  public static class NullableDoubleHash implements DrillSimpleFunc {

    @Param NullableFloat8Holder in;
    @Param BigIntHolder seed;
    @Output BigIntHolder out;


    public void setup() {
    }

    public void eval() {
      if (in.isSet == 0) {
        out.value = seed.value;
      } else {
        out.value = org.lealone.hansql.exec.expr.fn.impl.HashHelper.hash64(in.value, seed.value);
      }
    }
  }

  @FunctionTemplate(name = "hash64", scope = FunctionScope.SIMPLE, nulls = FunctionTemplate.NullHandling.INTERNAL )
  public static class DoubleHash implements DrillSimpleFunc {

    @Param Float8Holder in;
    @Param BigIntHolder seed;
    @Output BigIntHolder out;


    public void setup() {
    }

    public void eval() {
      out.value = org.lealone.hansql.exec.expr.fn.impl.HashHelper.hash64(in.value, seed.value);
    }
  }

  @FunctionTemplate(names = {"hash64", "hash64AsDouble"}, scope = FunctionScope.SIMPLE, nulls = FunctionTemplate.NullHandling.INTERNAL )
  public static class NullableVarBinaryHash implements DrillSimpleFunc {

    @Param NullableVarBinaryHolder in;
    @Param BigIntHolder seed;
    @Output BigIntHolder out;


    public void setup() {
    }

    public void eval() {
      if (in.isSet == 0) {
        out.value = seed.value;
      } else {
        out.value = org.lealone.hansql.exec.expr.fn.impl.HashHelper.hash64(in.start, in.end, in.buffer, seed.value);
      }
    }
  }

  @FunctionTemplate(names = {"hash64", "hash64AsDouble"}, scope = FunctionScope.SIMPLE, nulls = FunctionTemplate.NullHandling.INTERNAL )
  public static class NullableVarCharHash implements DrillSimpleFunc {

    @Param NullableVarCharHolder in;
    @Param BigIntHolder seed;
    @Output BigIntHolder out;


    public void setup() {
    }

    public void eval() {
      if (in.isSet == 0) {
        out.value = seed.value;
      } else {
        out.value = org.lealone.hansql.exec.expr.fn.impl.HashHelper.hash64(in.start, in.end, in.buffer, seed.value);
      }
    }
  }

  @FunctionTemplate(names = {"hash64", "hash64AsDouble"}, scope = FunctionScope.SIMPLE, nulls = FunctionTemplate.NullHandling.INTERNAL )
  public static class NullableVar16CharHash implements DrillSimpleFunc {

    @Param NullableVar16CharHolder in;
    @Param BigIntHolder seed;
    @Output BigIntHolder out;


    public void setup() {
    }

    public void eval() {
      if (in.isSet == 0) {
        out.value = seed.value;
      } else {
        out.value = org.lealone.hansql.exec.expr.fn.impl.HashHelper.hash64(in.start, in.end, in.buffer, seed.value);
      }
    }
  }

  @FunctionTemplate(name = "hash64", scope = FunctionScope.SIMPLE, nulls = FunctionTemplate.NullHandling.INTERNAL)
  public static class NullableBigIntHash implements DrillSimpleFunc {

    @Param NullableBigIntHolder in;
    @Param BigIntHolder seed;
    @Output BigIntHolder out;


    public void setup() {
    }

    public void eval() {
      if (in.isSet == 0) {
        out.value = seed.value;
      }
      else {
        out.value = org.lealone.hansql.exec.expr.fn.impl.HashHelper.hash64(in.value, seed.value);
      }
    }
  }

  @FunctionTemplate(name = "hash64", scope = FunctionScope.SIMPLE, nulls = FunctionTemplate.NullHandling.INTERNAL)
  public static class NullableIntHash implements DrillSimpleFunc {
    @Param NullableIntHolder in;
    @Param BigIntHolder seed;
    @Output BigIntHolder out;


    public void setup() {
    }

    public void eval() {
      if (in.isSet == 0) {
        out.value = seed.value;
      }
      else {
        out.value = org.lealone.hansql.exec.expr.fn.impl.HashHelper.hash64(in.value, seed.value);
      }
    }
  }

  @FunctionTemplate(names = {"hash64", "hash64AsDouble"}, scope = FunctionScope.SIMPLE, nulls = FunctionTemplate.NullHandling.INTERNAL)
  public static class VarBinaryHash implements DrillSimpleFunc {

    @Param VarBinaryHolder in;
    @Param BigIntHolder seed;
    @Output BigIntHolder out;


    public void setup() {
    }

    public void eval() {
      out.value = org.lealone.hansql.exec.expr.fn.impl.HashHelper.hash64(in.start, in.end, in.buffer, seed.value);
    }
  }

  @FunctionTemplate(names = {"hash64", "hash64AsDouble"}, scope = FunctionScope.SIMPLE, nulls = FunctionTemplate.NullHandling.INTERNAL)
  public static class VarCharHash implements DrillSimpleFunc {

    @Param VarCharHolder in;
    @Param BigIntHolder seed;
    @Output BigIntHolder out;


    public void setup() {
    }

    public void eval() {
      out.value = org.lealone.hansql.exec.expr.fn.impl.HashHelper.hash64(in.start, in.end, in.buffer, seed.value);
    }
  }

  @FunctionTemplate(names = {"hash64", "hash64AsDouble"}, scope = FunctionScope.SIMPLE, nulls = FunctionTemplate.NullHandling.INTERNAL)
  public static class Var16CharHash implements DrillSimpleFunc {

    @Param Var16CharHolder in;
    @Param BigIntHolder seed;
    @Output BigIntHolder out;


    public void setup() {
    }

    public void eval() {
      out.value = org.lealone.hansql.exec.expr.fn.impl.HashHelper.hash64(in.start, in.end, in.buffer, seed.value);
    }
  }

  @FunctionTemplate(name = "hash64", scope = FunctionScope.SIMPLE, nulls = FunctionTemplate.NullHandling.INTERNAL)
  public static class BigIntHash implements DrillSimpleFunc {

    @Param BigIntHolder in;
    @Param BigIntHolder seed;
    @Output BigIntHolder out;


    public void setup() {
    }

    public void eval() {
      out.value = org.lealone.hansql.exec.expr.fn.impl.HashHelper.hash64(in.value, seed.value);
    }
  }

  @FunctionTemplate(name = "hash64", scope = FunctionScope.SIMPLE, nulls = FunctionTemplate.NullHandling.INTERNAL)
  public static class IntHash implements DrillSimpleFunc {
    @Param IntHolder in;
    @Param BigIntHolder seed;
    @Output BigIntHolder out;


    public void setup() {
    }

    public void eval() {
      // TODO: implement hash function for other types
      out.value = org.lealone.hansql.exec.expr.fn.impl.HashHelper.hash64(in.value, seed.value);
    }
  }
  @FunctionTemplate(names = {"hash64", "hash64AsDouble"}, scope = FunctionScope.SIMPLE, nulls = FunctionTemplate.NullHandling.INTERNAL)
  public static class DateHash implements DrillSimpleFunc {
    @Param  DateHolder in;
    @Param BigIntHolder seed;
    @Output BigIntHolder out;


    public void setup() {
    }

    public void eval() {
      out.value = org.lealone.hansql.exec.expr.fn.impl.HashHelper.hash64(in.value, seed.value);
    }
  }

  @FunctionTemplate(names = {"hash64", "hash64AsDouble"}, scope = FunctionScope.SIMPLE, nulls = FunctionTemplate.NullHandling.INTERNAL)
  public static class NullableDateHash implements DrillSimpleFunc {
    @Param  NullableDateHolder in;
    @Param BigIntHolder seed;
    @Output BigIntHolder out;


    public void setup() {
    }

    public void eval() {
      if (in.isSet == 0) {
        out.value = seed.value;
      } else {
        out.value = org.lealone.hansql.exec.expr.fn.impl.HashHelper.hash64(in.value, seed.value);
      }
    }
  }

  @FunctionTemplate(names = {"hash64", "hash64AsDouble"}, scope = FunctionScope.SIMPLE, nulls = FunctionTemplate.NullHandling.INTERNAL)
  public static class TimeStampHash implements DrillSimpleFunc {
    @Param  TimeStampHolder in;
    @Param BigIntHolder seed;
    @Output BigIntHolder out;


    public void setup() {
    }

    public void eval() {
      out.value = org.lealone.hansql.exec.expr.fn.impl.HashHelper.hash64(in.value, seed.value);
    }
  }

  @FunctionTemplate(names = {"hash64", "hash64AsDouble"}, scope = FunctionScope.SIMPLE, nulls = FunctionTemplate.NullHandling.INTERNAL)
  public static class NullableTimeStampHash implements DrillSimpleFunc {
    @Param  NullableTimeStampHolder in;
    @Param BigIntHolder seed;
    @Output BigIntHolder out;


    public void setup() {
    }

    public void eval() {
      if (in.isSet == 0) {
        out.value = seed.value;
      } else {
        out.value = org.lealone.hansql.exec.expr.fn.impl.HashHelper.hash64(in.value, seed.value);
      }
    }
  }

  @FunctionTemplate(names = {"hash64", "hash64AsDouble"}, scope = FunctionScope.SIMPLE, nulls = FunctionTemplate.NullHandling.INTERNAL)
  public static class TimeHash implements DrillSimpleFunc {
    @Param  TimeHolder in;
    @Param BigIntHolder seed;
    @Output BigIntHolder out;


    public void setup() {
    }

    public void eval() {
      out.value = org.lealone.hansql.exec.expr.fn.impl.HashHelper.hash64(in.value, seed.value);
    }
  }

  @FunctionTemplate(names = {"hash64", "hash64AsDouble"}, scope = FunctionScope.SIMPLE, nulls = FunctionTemplate.NullHandling.INTERNAL)
  public static class NullableTimeHash implements DrillSimpleFunc {
    @Param  NullableTimeHolder in;
    @Param BigIntHolder seed;
    @Output BigIntHolder out;


    public void setup() {
    }

    public void eval() {
      if (in.isSet == 0) {
        out.value = seed.value;
      } else {
        out.value = org.lealone.hansql.exec.expr.fn.impl.HashHelper.hash64(in.value, seed.value);
      }
    }
  }

  @FunctionTemplate(name = "hash64", scope = FunctionScope.SIMPLE, nulls = FunctionTemplate.NullHandling.INTERNAL)
  public static class VarDecimalHash implements DrillSimpleFunc {
    @Param  VarDecimalHolder in;
    @Param BigIntHolder seed;
    @Output BigIntHolder out;

    public void setup() {
    }

    public void eval() {
      out.value = org.lealone.hansql.exec.expr.fn.impl.HashHelper.hash64(in.start, in.start + Decimal28SparseHolder.WIDTH, in.buffer, seed.value);
    }
  }

  @FunctionTemplate(name = "hash64", scope = FunctionScope.SIMPLE, nulls = FunctionTemplate.NullHandling.INTERNAL)
  public static class NullableVarDecimalHash implements DrillSimpleFunc {
    @Param  NullableVarDecimalHolder in;
    @Param BigIntHolder seed;
    @Output BigIntHolder out;

    public void setup() {
    }

    public void eval() {
      if (in.isSet == 0) {
        out.value = seed.value;
      } else {
        out.value = org.lealone.hansql.exec.expr.fn.impl.HashHelper.hash64(in.start, in.start + NullableDecimal28SparseHolder.WIDTH, in.buffer, seed.value);
      }
    }
  }

  @FunctionTemplate(names = {"hash64", "hash64AsDouble"}, scope = FunctionScope.SIMPLE, nulls = FunctionTemplate.NullHandling.INTERNAL )
  public static class NullableBitHash implements DrillSimpleFunc {

    @Param NullableBitHolder in;
    @Param BigIntHolder seed;
    @Output BigIntHolder out;


    public void setup() {
    }

    public void eval() {
      if (in.isSet == 0) {
        out.value = seed.value;
      } else {
        out.value = org.lealone.hansql.exec.expr.fn.impl.HashHelper.hash64(in.value, seed.value);
      }
    }
  }

  @FunctionTemplate(names = {"hash64", "hash64AsDouble"}, scope = FunctionScope.SIMPLE, nulls = FunctionTemplate.NullHandling.INTERNAL )
  public static class BitHash implements DrillSimpleFunc {

    @Param BitHolder in;
    @Param BigIntHolder seed;
    @Output BigIntHolder out;


    public void setup() {
    }

    public void eval() {
      out.value = org.lealone.hansql.exec.expr.fn.impl.HashHelper.hash64(in.value, seed.value);
    }
  }}
