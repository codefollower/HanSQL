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

import javax.inject.Inject;

import org.lealone.hansql.exec.expr.holders.BitHolder;
import org.lealone.hansql.exec.expr.holders.IntHolder;
import org.lealone.hansql.exec.expr.holders.VarCharHolder;
import org.lealone.hansql.common.types.TypeProtos.MinorType;
import org.lealone.hansql.exec.expr.DrillSimpleFunc;
import org.lealone.hansql.exec.expr.annotations.FunctionTemplate;
import org.lealone.hansql.exec.expr.annotations.Output;
import org.lealone.hansql.exec.expr.annotations.Param;
import org.lealone.hansql.exec.expr.annotations.FunctionTemplate.NullHandling;
import org.lealone.hansql.exec.expr.holders.UnionHolder;
import org.lealone.hansql.exec.resolver.TypeCastRules;
import org.lealone.hansql.exec.vector.complex.reader.FieldReader;

import io.netty.buffer.DrillBuf;

/**
 * The class contains additional functions for union types in addition to those in GUnionFunctions
 */
public class UnionFunctions {

  /**
   * Returns zero if the inputs have equivalent types. Two numeric types are considered equivalent, as are a combination
   * of date/timestamp. If not equivalent, returns a value determined by the numeric value of the MinorType enum
   */
  @FunctionTemplate(names = {"compareType"},
          scope = FunctionTemplate.FunctionScope.SIMPLE,
          nulls = NullHandling.INTERNAL)
  public static class CompareType implements DrillSimpleFunc {

    @Param
    FieldReader input1;
    @Param
    FieldReader input2;
    @Output
    IntHolder out;

    @Override
    public void setup() {}

    @Override
    public void eval() {
      org.lealone.hansql.common.types.TypeProtos.MinorType type1;
      if (input1.isSet()) {
        type1 = input1.getType().getMinorType();
      } else {
        type1 = org.lealone.hansql.common.types.TypeProtos.MinorType.NULL;
      }
      org.lealone.hansql.common.types.TypeProtos.MinorType type2;
      if (input2.isSet()) {
        type2 = input2.getType().getMinorType();
      } else {
        type2 = org.lealone.hansql.common.types.TypeProtos.MinorType.NULL;
      }

      out.value = org.lealone.hansql.exec.expr.fn.impl.UnionFunctions.compareTypes(type1, type2);
    }
  }

  public static int compareTypes(MinorType type1, MinorType type2) {
    int typeValue1 = getTypeValue(type1);
    int typeValue2 = getTypeValue(type2);
    return typeValue1 - typeValue2;
  }

  /**
   * Gives a type ordering modeled after the behavior of MongoDB
   * Numeric types are first, folowed by string types, followed by binary, then boolean, then date, then timestamp
   * Any other times will be sorted after that
   * @param type
   * @return
   */
  private static int getTypeValue(MinorType type) {
    if (TypeCastRules.isNumericType(type)) {
      return 0;
    }
    switch (type) {
    case TINYINT:
    case SMALLINT:
    case INT:
    case BIGINT:
    case UINT1:
    case UINT2:
    case UINT4:
    case UINT8:
    case DECIMAL9:
    case DECIMAL18:
    case DECIMAL28SPARSE:
    case DECIMAL38SPARSE:
    case VARDECIMAL:
    case FLOAT4:
    case FLOAT8:
      return 0;
    case VARCHAR:
    case VAR16CHAR:
      return 1;
    case VARBINARY:
      return 2;
    case BIT:
      return 3;
    case DATE:
      return 4;
    case TIMESTAMP:
      return 5;
    default:
      return 6 + type.getNumber();
    }
  }

  @FunctionTemplate(names = {"typeOf"},
          scope = FunctionTemplate.FunctionScope.SIMPLE,
          nulls = NullHandling.INTERNAL)
  public static class GetType implements DrillSimpleFunc {

    @Param
    FieldReader input;
    @Output
    VarCharHolder out;
    @Inject
    DrillBuf buf;

    @Override
    public void setup() {}

    @Override
    public void eval() {

      String typeName;
      if (input.isSet()) {
        typeName = input.getType().getMinorType().name();
      } else {
        typeName = org.lealone.hansql.common.types.TypeProtos.MinorType.NULL.name();
      }
      byte[] type = typeName.getBytes();
      buf = buf.reallocIfNeeded(type.length);
      buf.setBytes(0, type);
      out.buffer = buf;
      out.start = 0;
      out.end = type.length;
    }
  }

  @FunctionTemplate(name = "sqlTypeOf",
          scope = FunctionTemplate.FunctionScope.SIMPLE,
          nulls = NullHandling.INTERNAL)
  public static class GetSqlType implements DrillSimpleFunc {

    @Param
    FieldReader input;
    @Output
    VarCharHolder out;
    @Inject
    DrillBuf buf;

    @Override
    public void setup() {}

    @Override
    public void eval() {

      String typeName = org.lealone.hansql.common.types.Types.getExtendedSqlTypeName(input.getType());
      byte[] type = typeName.getBytes();
      buf = buf.reallocIfNeeded(type.length);
      buf.setBytes(0, type);
      out.buffer = buf;
      out.start = 0;
      out.end = type.length;
    }
  }

  @FunctionTemplate(name = "drillTypeOf",
          scope = FunctionTemplate.FunctionScope.SIMPLE,
          nulls = NullHandling.INTERNAL)
  public static class GetDrillType implements DrillSimpleFunc {

    @Param
    FieldReader input;
    @Output
    VarCharHolder out;
    @Inject
    DrillBuf buf;

    @Override
    public void setup() {}

    @Override
    public void eval() {

      String typeName = input.getType().getMinorType().name();
      byte[] type = typeName.getBytes();
      buf = buf.reallocIfNeeded(type.length);
      buf.setBytes(0, type);
      out.buffer = buf;
      out.start = 0;
      out.end = type.length;
    }
  }

  @FunctionTemplate(name = "modeOf",
          scope = FunctionTemplate.FunctionScope.SIMPLE,
          nulls = NullHandling.INTERNAL)
  public static class GetMode implements DrillSimpleFunc {

    @Param
    FieldReader input;
    @Output
    VarCharHolder out;
    @Inject
    DrillBuf buf;

    @Override
    public void setup() {}

    @Override
    public void eval() {

      String typeName = org.lealone.hansql.common.types.Types.getSqlModeName(
          input.getType());
      byte[] type = typeName.getBytes();
      buf = buf.reallocIfNeeded(type.length);
      buf.setBytes(0, type);
      out.buffer = buf;
      out.start = 0;
      out.end = type.length;
    }
  }

  @SuppressWarnings("unused")
  @FunctionTemplate(names = {"castUNION", "castToUnion"}, scope = FunctionTemplate.FunctionScope.SIMPLE, nulls=NullHandling.NULL_IF_NULL)
  public static class CastUnionToUnion implements DrillSimpleFunc{

    @Param FieldReader in;
    @Output
    UnionHolder out;

    @Override
    public void setup() {}

    @Override
    public void eval() {
      out.reader = in;
      out.isSet = in.isSet() ? 1 : 0;
    }
  }

  @SuppressWarnings("unused")
  @FunctionTemplate(name = "ASSERT_LIST", scope = FunctionTemplate.FunctionScope.SIMPLE, nulls=NullHandling.INTERNAL)
  public static class CastUnionList implements DrillSimpleFunc {

    @Param UnionHolder in;
    @Output UnionHolder out;

    @Override
    public void setup() {}

    @Override
    public void eval() {
      if (in.isSet == 1) {
        if (in.reader.getType().getMinorType() != org.lealone.hansql.common.types.TypeProtos.MinorType.LIST) {
          throw new UnsupportedOperationException("The input is not a LIST type");
        }
        out.reader = in.reader;
      } else {
        out.isSet = 0;
      }
    }
  }

  @SuppressWarnings("unused")
  @FunctionTemplate(name = "IS_LIST", scope = FunctionTemplate.FunctionScope.SIMPLE, nulls=NullHandling.INTERNAL)
  public static class UnionIsList implements DrillSimpleFunc {

    @Param UnionHolder in;
    @Output BitHolder out;

    @Override
    public void setup() {}

    @Override
    public void eval() {
      if (in.isSet == 1) {
        out.value = in.getType().getMinorType() == org.lealone.hansql.common.types.TypeProtos.MinorType.LIST ? 1 : 0;
      } else {
        out.value = 0;
      }
    }
  }

  @SuppressWarnings("unused")
  @FunctionTemplate(name = "ASSERT_MAP", scope = FunctionTemplate.FunctionScope.SIMPLE, nulls=NullHandling.INTERNAL)
  public static class CastUnionMap implements DrillSimpleFunc {

    @Param UnionHolder in;
    @Output UnionHolder out;

    @Override
    public void setup() {}

    @Override
    public void eval() {
      if (in.isSet == 1) {
        if (in.reader.getType().getMinorType() != org.lealone.hansql.common.types.TypeProtos.MinorType.MAP) {
          throw new UnsupportedOperationException("The input is not a MAP type");
        }
        out.reader = in.reader;
      } else {
        out.isSet = 0;
      }
    }
  }

  @SuppressWarnings("unused")
  @FunctionTemplate(names = {"IS_MAP", "IS_STRUCT"}, scope = FunctionTemplate.FunctionScope.SIMPLE, nulls=NullHandling.INTERNAL)
  public static class UnionIsMap implements DrillSimpleFunc {

    @Param UnionHolder in;
    @Output BitHolder out;

    @Override
    public void setup() {}

    @Override
    public void eval() {
      if (in.isSet == 1) {
        out.value = in.getType().getMinorType() == org.lealone.hansql.common.types.TypeProtos.MinorType.MAP ? 1 : 0;
      } else {
        out.value = 0;
      }
    }
  }

  @FunctionTemplate(names = {"isnotnull", "is not null"}, scope = FunctionTemplate.FunctionScope.SIMPLE, nulls = FunctionTemplate.NullHandling.INTERNAL)
  public static class IsNotNull implements DrillSimpleFunc {

    @Param UnionHolder input;
    @Output BitHolder out;

    @Override
    public void setup() { }

    @Override
    public void eval() {
      out.value = input.isSet == 1 ? 1 : 0;
    }
  }

  @FunctionTemplate(names = {"isnull", "is null"}, scope = FunctionTemplate.FunctionScope.SIMPLE, nulls = FunctionTemplate.NullHandling.INTERNAL)
  public static class IsNull implements DrillSimpleFunc {

    @Param UnionHolder input;
    @Output BitHolder out;

    @Override
    public void setup() { }

    @Override
    public void eval() {
      out.value = input.isSet == 1 ? 0 : 1;
    }
  }

}
