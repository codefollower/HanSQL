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
package org.lealone.hansql.exec.expr;

import org.lealone.hansql.common.expression.LogicalExpression;
import org.lealone.hansql.common.expression.LogicalExpressionBase;
import org.lealone.hansql.common.expression.visitors.ExprVisitor;
import org.lealone.hansql.common.types.TypeProtos;
import org.lealone.hansql.exec.expr.fn.FunctionGenerationHelper;
import org.lealone.hansql.exec.expr.stat.RowsMatch;
import org.lealone.hansql.metastore.ColumnStatistics;
import org.lealone.hansql.metastore.ColumnStatisticsKind;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Comparison predicates for metadata filter pushdown.
 */
public class ComparisonPredicate<C extends Comparable<C>> extends LogicalExpressionBase implements FilterPredicate<C> {

  private final LogicalExpression left;
  private final LogicalExpression right;

  private final BiFunction<ColumnStatistics<C>, ColumnStatistics<C>, RowsMatch> predicate;

  private ComparisonPredicate(LogicalExpression left,
                              LogicalExpression right,
                              BiFunction<ColumnStatistics<C>, ColumnStatistics<C>, RowsMatch> predicate) {
    super(left.getPosition());
    this.left = left;
    this.right = right;
    this.predicate = predicate;
  }

  @Override
  public Iterator<LogicalExpression> iterator() {
    final List<LogicalExpression> args = new ArrayList<>();
    args.add(left);
    args.add(right);
    return args.iterator();
  }

  @Override
  public <T, V, E extends Exception> T accept(ExprVisitor<T, V, E> visitor, V value) throws E {
    return visitor.visitUnknown(this, value);
  }

  /**
   * Semantics of matches() is very similar to what is implemented in Parquet library's
   * {@link org.apache.parquet.filter2.statisticslevel.StatisticsFilter} and
   * {@link org.apache.parquet.filter2.predicate.FilterPredicate}
   *
   * Main difference :
   * 1. A RangeExprEvaluator is used to compute the min/max of an expression, such as CAST function
   * of a column. CAST function could be explicitly added by Drill user (It's recommended to use CAST
   * function after DRILL-4372, if user wants to reduce planning time for limit 0 query), or implicitly
   * inserted by Drill, when the types of compare operands are not identical. Therefore, it's important
   * to allow CAST function to appear in the filter predicate.
   * 2. We do not require list of ColumnChunkMetaData to do the evaluation, while Parquet library's
   * StatisticsFilter has such requirement. Drill's ParquetTableMetaData does not maintain ColumnChunkMetaData,
   * making it impossible to directly use Parquet library's StatisticFilter in query planning time.
   * 3. We allows both sides of comparison operator to be a min/max range. As such, we support
   * expression_of(Column1)   <   expression_of(Column2),
   * where Column1 and Column2 are from same parquet table.
   */
  @Override
  public RowsMatch matches(StatisticsProvider<C> evaluator) {
    ColumnStatistics<C> leftStat = left.accept(evaluator, null);
    if (IsPredicate.isNullOrEmpty(leftStat)) {
      return RowsMatch.SOME;
    }
    ColumnStatistics<C> rightStat = right.accept(evaluator, null);
    if (IsPredicate.isNullOrEmpty(rightStat)) {
      return RowsMatch.SOME;
    }
    if (IsPredicate.isAllNulls(leftStat, evaluator.getRowCount()) || IsPredicate.isAllNulls(rightStat, evaluator.getRowCount())) {
      return RowsMatch.NONE;
    }
    if (!IsPredicate.hasNonNullValues(leftStat, evaluator.getRowCount()) || !IsPredicate.hasNonNullValues(rightStat, evaluator.getRowCount())) {
      return RowsMatch.SOME;
    }

    if (left.getMajorType().getMinorType() == TypeProtos.MinorType.VARDECIMAL) {
      /*
        to compare correctly two decimal statistics we need to ensure that min and max values have the same scale,
        otherwise adjust statistics to the highest scale
        since decimal value is stored as unscaled we need to move dot to the right on the difference between scales
       */
      int leftScale = left.getMajorType().getScale();
      int rightScale = right.getMajorType().getScale();
      if (leftScale > rightScale) {
        rightStat = adjustDecimalStatistics(rightStat, leftScale - rightScale);
      } else if (leftScale < rightScale) {
        leftStat = adjustDecimalStatistics(leftStat, rightScale - leftScale);
      }
    }
    return predicate.apply(leftStat, rightStat);
  }

  /**
   * Creates decimal statistics where min and max values are re-created using given scale.
   *
   * @param statistics statistics that needs to be adjusted
   * @param scale adjustment scale
   * @return adjusted statistics
   */
  @SuppressWarnings("unchecked")
  private ColumnStatistics<C> adjustDecimalStatistics(ColumnStatistics<C> statistics, int scale) {
    BigInteger min = new BigDecimal((BigInteger) statistics.getStatistic(ColumnStatisticsKind.MIN_VALUE))
        .setScale(scale, RoundingMode.HALF_UP).unscaledValue();
    BigInteger max = new BigDecimal((BigInteger) statistics.getStatistic(ColumnStatisticsKind.MAX_VALUE))
        .setScale(scale, RoundingMode.HALF_UP).unscaledValue();

    return new StatisticsProvider.MinMaxStatistics(min, max, Comparator.nullsFirst(Comparator.naturalOrder()));
  }

  /**
   * If one rowgroup contains some null values, change the RowsMatch.ALL into RowsMatch.SOME (null values should be discarded by filter)
   */
  private static RowsMatch checkNull(ColumnStatistics leftStat, ColumnStatistics rightStat) {
    return !IsPredicate.hasNoNulls(leftStat) || !IsPredicate.hasNoNulls(rightStat) ? RowsMatch.SOME : RowsMatch.ALL;
  }

  /**
   * EQ (=) predicate
   */
  private static <C extends Comparable<C>> LogicalExpression createEqualPredicate(
      LogicalExpression left, LogicalExpression right) {
    return new ComparisonPredicate<C>(left, right, (leftStat, rightStat) -> {
      Comparator<C> valueComparator = leftStat.getValueComparator();

      // compare left max and right min
      int leftToRightComparison = valueComparator.compare(getMaxValue(leftStat), getMinValue(rightStat));
      // compare right max and left min
      int rightToLeftComparison = valueComparator.compare(getMaxValue(rightStat), getMinValue(leftStat));

      // if both comparison results are equal to 0 and both statistics have no nulls,
      // it means that min and max values in each statistics are the same and match each other,
      // return that all rows match the condition
      if (leftToRightComparison == 0 && rightToLeftComparison == 0 && IsPredicate.hasNoNulls(leftStat) && IsPredicate.hasNoNulls(rightStat)) {
        return RowsMatch.ALL;
      }

      // if at least one comparison result is negative, it means that none of the rows match the condition
      return leftToRightComparison < 0 || rightToLeftComparison < 0 ? RowsMatch.NONE : RowsMatch.SOME;
    }) {
      @Override
      public String toString() {
        return left + " = " + right;
      }
    };
  }

  /**
   * GT (>) predicate.
   */
  private static <C extends Comparable<C>> LogicalExpression createGTPredicate(
      LogicalExpression left, LogicalExpression right) {
    return new ComparisonPredicate<C>(left, right, (leftStat, rightStat) -> {
      Comparator<C> valueComparator = leftStat.getValueComparator();
      if (valueComparator.compare(getMaxValue(leftStat), getMinValue(rightStat)) <= 0) {
        return RowsMatch.NONE;
      }
      return valueComparator.compare(getMinValue(leftStat), getMaxValue(rightStat)) > 0 ? checkNull(leftStat, rightStat) : RowsMatch.SOME;
    });
  }

  static <C> C getMaxValue(ColumnStatistics<C> leftStat) {
    return leftStat.getValueStatistic(ColumnStatisticsKind.MAX_VALUE);
  }

  static <C> C getMinValue(ColumnStatistics<C> leftStat) {
    return leftStat.getValueStatistic(ColumnStatisticsKind.MIN_VALUE);
  }

  /**
   * GE (>=) predicate.
   */
  private static <C extends Comparable<C>> LogicalExpression createGEPredicate(
    LogicalExpression left, LogicalExpression right) {
    return new ComparisonPredicate<C>(left, right, (leftStat, rightStat) -> {
      Comparator<C> valueComparator = leftStat.getValueComparator();
      if (valueComparator.compare(getMaxValue(leftStat), getMinValue(rightStat)) < 0) {
        return RowsMatch.NONE;
      }
      return valueComparator.compare(getMinValue(leftStat), getMaxValue(rightStat)) >= 0 ? checkNull(leftStat, rightStat) : RowsMatch.SOME;
    });
  }

  /**
   * LT (<) predicate.
   */
  private static <C extends Comparable<C>> LogicalExpression createLTPredicate(
      LogicalExpression left, LogicalExpression right) {
    return new ComparisonPredicate<C>(left, right, (leftStat, rightStat) -> {
      Comparator<C> valueComparator = leftStat.getValueComparator();
      if (valueComparator.compare(getMaxValue(rightStat), getMinValue(leftStat)) <= 0) {
        return RowsMatch.NONE;
      }
      return valueComparator.compare(getMaxValue(leftStat), getMinValue(rightStat)) < 0 ? checkNull(leftStat, rightStat) : RowsMatch.SOME;
    });
  }

  /**
   * LE (<=) predicate.
   */
  private static <C extends Comparable<C>> LogicalExpression createLEPredicate(
      LogicalExpression left, LogicalExpression right) {
    return new ComparisonPredicate<C>(left, right, (leftStat, rightStat) -> {
      Comparator<C> valueComparator = leftStat.getValueComparator();
      if (valueComparator.compare(getMaxValue(rightStat), getMinValue(leftStat)) < 0) {
        return RowsMatch.NONE;
      }
      return valueComparator.compare(getMaxValue(leftStat), getMinValue(rightStat)) <= 0 ? checkNull(leftStat, rightStat) : RowsMatch.SOME;
    });
  }

  /**
   * NE (!=) predicate.
   */
  private static <C extends Comparable<C>> LogicalExpression createNEPredicate(
      LogicalExpression left, LogicalExpression right) {
    return new ComparisonPredicate<C>(left, right, (leftStat, rightStat) -> {
      Comparator<C> valueComparator = leftStat.getValueComparator();
      if (valueComparator.compare(getMaxValue(leftStat), getMinValue(rightStat)) < 0
          || valueComparator.compare(getMaxValue(rightStat), getMinValue(leftStat)) < 0) {
        return checkNull(leftStat, rightStat);
      }
      return valueComparator.compare(getMaxValue(leftStat), getMaxValue(rightStat)) == 0
          && valueComparator.compare(getMinValue(leftStat), getMinValue(rightStat)) == 0 ? RowsMatch.NONE : RowsMatch.SOME;
    });
  }

  public static <C extends Comparable<C>> LogicalExpression createComparisonPredicate(
      String function, LogicalExpression left, LogicalExpression right) {
    switch (function) {
      case FunctionGenerationHelper.EQ:
        return ComparisonPredicate.<C>createEqualPredicate(left, right);
      case FunctionGenerationHelper.GT:
        return ComparisonPredicate.<C>createGTPredicate(left, right);
      case FunctionGenerationHelper.GE:
        return ComparisonPredicate.<C>createGEPredicate(left, right);
      case FunctionGenerationHelper.LT:
        return ComparisonPredicate.<C>createLTPredicate(left, right);
      case FunctionGenerationHelper.LE:
        return ComparisonPredicate.<C>createLEPredicate(left, right);
      case FunctionGenerationHelper.NE:
        return ComparisonPredicate.<C>createNEPredicate(left, right);
      default:
        return null;
    }
  }
}
