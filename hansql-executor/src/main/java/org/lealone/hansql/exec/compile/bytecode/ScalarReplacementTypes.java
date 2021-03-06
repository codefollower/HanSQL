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
package org.lealone.hansql.exec.compile.bytecode;

import org.lealone.hansql.exec.expr.holders.BigIntHolder;
import org.lealone.hansql.exec.expr.holders.BitHolder;
import org.lealone.hansql.exec.expr.holders.DateHolder;
import org.lealone.hansql.exec.expr.holders.Decimal18Holder;
import org.lealone.hansql.exec.expr.holders.Decimal28DenseHolder;
import org.lealone.hansql.exec.expr.holders.Decimal28SparseHolder;
import org.lealone.hansql.exec.expr.holders.Decimal38DenseHolder;
import org.lealone.hansql.exec.expr.holders.Decimal38SparseHolder;
import org.lealone.hansql.exec.expr.holders.Decimal9Holder;
import org.lealone.hansql.exec.expr.holders.Float4Holder;
import org.lealone.hansql.exec.expr.holders.Float8Holder;
import org.lealone.hansql.exec.expr.holders.IntHolder;
import org.lealone.hansql.exec.expr.holders.IntervalDayHolder;
import org.lealone.hansql.exec.expr.holders.IntervalHolder;
import org.lealone.hansql.exec.expr.holders.IntervalYearHolder;
import org.lealone.hansql.exec.expr.holders.NullableBigIntHolder;
import org.lealone.hansql.exec.expr.holders.NullableBitHolder;
import org.lealone.hansql.exec.expr.holders.NullableDateHolder;
import org.lealone.hansql.exec.expr.holders.NullableDecimal18Holder;
import org.lealone.hansql.exec.expr.holders.NullableDecimal28DenseHolder;
import org.lealone.hansql.exec.expr.holders.NullableDecimal28SparseHolder;
import org.lealone.hansql.exec.expr.holders.NullableDecimal38DenseHolder;
import org.lealone.hansql.exec.expr.holders.NullableDecimal38SparseHolder;
import org.lealone.hansql.exec.expr.holders.NullableDecimal9Holder;
import org.lealone.hansql.exec.expr.holders.NullableFloat4Holder;
import org.lealone.hansql.exec.expr.holders.NullableFloat8Holder;
import org.lealone.hansql.exec.expr.holders.NullableIntHolder;
import org.lealone.hansql.exec.expr.holders.NullableIntervalDayHolder;
import org.lealone.hansql.exec.expr.holders.NullableIntervalHolder;
import org.lealone.hansql.exec.expr.holders.NullableIntervalYearHolder;
import org.lealone.hansql.exec.expr.holders.NullableTimeHolder;
import org.lealone.hansql.exec.expr.holders.NullableTimeStampHolder;
import org.lealone.hansql.exec.expr.holders.NullableVarBinaryHolder;
import org.lealone.hansql.exec.expr.holders.NullableVarCharHolder;
import org.lealone.hansql.exec.expr.holders.NullableVarDecimalHolder;
import org.lealone.hansql.exec.expr.holders.TimeHolder;
import org.lealone.hansql.exec.expr.holders.TimeStampHolder;
import org.lealone.hansql.exec.expr.holders.VarBinaryHolder;
import org.lealone.hansql.exec.expr.holders.VarCharHolder;

import org.apache.drill.shaded.guava.com.google.common.collect.ImmutableSet;
import org.lealone.hansql.exec.expr.holders.VarDecimalHolder;

/**
 * Reference list of classes we will perform scalar replacement on.
 */
public class ScalarReplacementTypes {
  // This class only has static utilities.
  private ScalarReplacementTypes() {
  }

  static {
    Class<?>[] classList = {
        BitHolder.class,
        IntHolder.class,
        BigIntHolder.class,
        Float4Holder.class,
        Float8Holder.class,
        Decimal9Holder.class,
        Decimal18Holder.class,
        Decimal28SparseHolder.class,
        Decimal28DenseHolder.class,
        Decimal38SparseHolder.class,
        Decimal38DenseHolder.class,
        IntervalHolder.class,
        IntervalDayHolder.class,
        IntervalYearHolder.class,
        DateHolder.class,
        TimeHolder.class,
        TimeStampHolder.class,
        VarCharHolder.class,
        VarBinaryHolder.class,
        VarDecimalHolder.class,
        NullableBitHolder.class,
        NullableIntHolder.class,
        NullableBigIntHolder.class,
        NullableFloat4Holder.class,
        NullableFloat8Holder.class,
        NullableVarCharHolder.class,
        NullableVarBinaryHolder.class,
        NullableVarDecimalHolder.class,
        NullableDecimal9Holder.class,
        NullableDecimal18Holder.class,
        NullableDecimal28SparseHolder.class,
        NullableDecimal28DenseHolder.class,
        NullableDecimal38SparseHolder.class,
        NullableDecimal38DenseHolder.class,
        NullableIntervalHolder.class,
        NullableIntervalDayHolder.class,
        NullableIntervalYearHolder.class,
        NullableDateHolder.class,
        NullableTimeHolder.class,
        NullableTimeStampHolder.class,
    };

    CLASSES = ImmutableSet.copyOf(classList);
  }

  public static final ImmutableSet<Class<?>> CLASSES;
 
  /**
   * Determine if a class is a holder class.
   *
   * @param className the name of the class
   * @return true if the class belongs to the CLASSES set.
   */
  public static boolean isHolder(final String className) {
    try {
      final Class<?> clazz = Class.forName(className);
      return CLASSES.contains(clazz);
    } catch (ClassNotFoundException e) {
      // do nothing
    }

    return false;
  }
}
