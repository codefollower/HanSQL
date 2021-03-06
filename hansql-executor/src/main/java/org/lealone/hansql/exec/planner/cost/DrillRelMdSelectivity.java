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
package org.lealone.hansql.exec.planner.cost;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.lealone.hansql.common.expression.SchemaPath;
import org.lealone.hansql.exec.physical.base.DbGroupScan;
import org.lealone.hansql.exec.physical.base.GroupScan;
import org.lealone.hansql.exec.planner.common.DrillJoinRelBase;
import org.lealone.hansql.exec.planner.common.DrillRelOptUtil;
import org.lealone.hansql.exec.planner.common.DrillScanRelBase;
import org.lealone.hansql.exec.planner.common.Histogram;
import org.lealone.hansql.exec.planner.logical.DrillScanRel;
import org.lealone.hansql.exec.planner.logical.DrillTable;
import org.lealone.hansql.exec.planner.physical.PlannerSettings;
import org.lealone.hansql.exec.planner.physical.PrelUtil;
import org.lealone.hansql.exec.planner.physical.ScanPrel;
import org.lealone.hansql.exec.util.Utilities;
import org.lealone.hansql.metastore.ColumnStatistics;
import org.lealone.hansql.metastore.ColumnStatisticsKind;
import org.lealone.hansql.metastore.TableMetadata;
import org.lealone.hansql.metastore.TableStatisticsKind;
import org.lealone.hansql.optimizer.plan.RelOptUtil;
import org.lealone.hansql.optimizer.plan.volcano.RelSubset;
import org.lealone.hansql.optimizer.rel.RelNode;
import org.lealone.hansql.optimizer.rel.core.JoinRelType;
import org.lealone.hansql.optimizer.rel.core.TableScan;
import org.lealone.hansql.optimizer.rel.metadata.ReflectiveRelMetadataProvider;
import org.lealone.hansql.optimizer.rel.metadata.RelMdSelectivity;
import org.lealone.hansql.optimizer.rel.metadata.RelMdUtil;
import org.lealone.hansql.optimizer.rel.metadata.RelMetadataProvider;
import org.lealone.hansql.optimizer.rel.metadata.RelMetadataQuery;
import org.lealone.hansql.optimizer.rex.RexBuilder;
import org.lealone.hansql.optimizer.rex.RexCall;
import org.lealone.hansql.optimizer.rex.RexInputRef;
import org.lealone.hansql.optimizer.rex.RexNode;
import org.lealone.hansql.optimizer.rex.RexUtil;
import org.lealone.hansql.optimizer.rex.RexVisitor;
import org.lealone.hansql.optimizer.rex.RexVisitorImpl;
import org.lealone.hansql.optimizer.sql.SqlKind;
import org.lealone.hansql.optimizer.util.BuiltInMethod;
import org.lealone.hansql.optimizer.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DrillRelMdSelectivity extends RelMdSelectivity {
  private static final Logger logger = LoggerFactory.getLogger(DrillRelMdSelectivity.class);

  private static final DrillRelMdSelectivity INSTANCE = new DrillRelMdSelectivity();
  public static final RelMetadataProvider SOURCE = ReflectiveRelMetadataProvider.reflectiveSource(BuiltInMethod.SELECTIVITY.method, INSTANCE);
  /*
   * For now, we are treating all LIKE predicates to have the same selectivity irrespective of the number or position
   * of wildcard characters (%). This is no different than the present Drill/Calcite behaviour w.r.t to LIKE predicates.
   * The difference being Calcite keeps the selectivity 25% whereas we keep it at 5%
   * TODO: Differentiate leading/trailing wildcard characters(%) or explore different estimation techniques e.g. LSH-based
   */
  private static final double LIKE_PREDICATE_SELECTIVITY = 0.05;

  public static final Set<SqlKind> RANGE_PREDICATE =
    EnumSet.of(
      SqlKind.LESS_THAN, SqlKind.GREATER_THAN,
      SqlKind.LESS_THAN_OR_EQUAL, SqlKind.GREATER_THAN_OR_EQUAL);

  @Override
  public Double getSelectivity(RelNode rel, RelMetadataQuery mq, RexNode predicate) {
    if (rel instanceof RelSubset && !DrillRelOptUtil.guessRows(rel)) {
      return getSubsetSelectivity((RelSubset) rel, mq, predicate);
    } else if (rel instanceof TableScan) {
      return getScanSelectivity(rel, mq, predicate);
    } else if (rel instanceof DrillJoinRelBase) {
      return getJoinSelectivity(((DrillJoinRelBase) rel), mq, predicate);
    } /*else if (rel instanceof SingleRel && !DrillRelOptUtil.guessRows(rel)) {
      return getSelectivity(((SingleRel)rel).getInput(), mq, predicate);
    }*/ else {
      return super.getSelectivity(rel, mq, predicate);
    }
  }

  private Double getSubsetSelectivity(RelSubset rel, RelMetadataQuery mq, RexNode predicate) {
    if (rel.getBest() != null) {
      return getSelectivity(rel.getBest(), mq, predicate);
    } else {
      List<RelNode> list = rel.getRelList();
      if (list != null && list.size() > 0) {
        return getSelectivity(list.get(0), mq, predicate);
      }
    }
    //TODO: Not required? return mq.getSelectivity(((RelSubset)rel).getOriginal(), predicate);
    return RelMdUtil.guessSelectivity(predicate);
  }

  private Double getScanSelectivity(RelNode rel, RelMetadataQuery mq, RexNode predicate) {
    double ROWCOUNT_UNKNOWN = -1.0;
    GroupScan scan = null;
    PlannerSettings settings = PrelUtil.getPlannerSettings(rel.getCluster().getPlanner());
    if (rel instanceof DrillScanRel) {
      scan = ((DrillScanRel) rel).getGroupScan();
    } else if (rel instanceof ScanPrel) {
      scan = ((ScanPrel) rel).getGroupScan();
    }
    if (scan != null) {
      if (settings.isStatisticsEnabled()
          && scan instanceof DbGroupScan) {
        double filterRows = ((DbGroupScan) scan).getRowCount(predicate, rel);
        double totalRows = ((DbGroupScan) scan).getRowCount(null, rel);
        if (filterRows != ROWCOUNT_UNKNOWN &&
            totalRows != ROWCOUNT_UNKNOWN && totalRows > 0) {
          return Math.min(1.0, filterRows / totalRows);
        }
      }
    }
    // Do not mess with statistics used for DBGroupScans.
    if (rel instanceof TableScan) {
      if (DrillRelOptUtil.guessRows(rel)) {
        return super.getSelectivity(rel, mq, predicate);
      }
      DrillTable table = Utilities.getDrillTable(rel.getTable());
      try {
        TableMetadata tableMetadata;
        if (table != null && (tableMetadata = table.getGroupScan().getTableMetadata()) != null
            && (boolean) TableStatisticsKind.HAS_STATISTICS.getValue(tableMetadata)) {
          List<SchemaPath> fieldNames;
          if (rel instanceof DrillScanRelBase) {
            fieldNames = ((DrillScanRelBase) rel).getGroupScan().getColumns();
          } else {
            fieldNames = rel.getRowType().getFieldNames().stream()
                .map(SchemaPath::getSimplePath)
                .collect(Collectors.toList());
          }
          return getScanSelectivityInternal(tableMetadata, predicate, fieldNames);
        }
      } catch (IOException e) {
        super.getSelectivity(rel, mq, predicate);
      }
    }
    return super.getSelectivity(rel, mq, predicate);
  }

  private double getScanSelectivityInternal(TableMetadata tableMetadata, RexNode predicate, List<SchemaPath> fieldNames) {
    double sel = 1.0;
    if ((predicate == null) || predicate.isAlwaysTrue()) {
      return sel;
    }
    for (RexNode pred : RelOptUtil.conjunctions(predicate)) {
      double orSel = 0;
      for (RexNode orPred : RelOptUtil.disjunctions(pred)) {
        if (isMultiColumnPredicate(orPred)) {
          orSel += RelMdUtil.guessSelectivity(orPred);  //CALCITE guess
        } else if (orPred.isA(SqlKind.EQUALS)) {
          orSel += computeEqualsSelectivity(tableMetadata, orPred, fieldNames);
        } else if (orPred.isA(RANGE_PREDICATE)) {
          orSel += computeRangeSelectivity(tableMetadata, orPred, fieldNames);
        } else if (orPred.isA(SqlKind.NOT_EQUALS)) {
          orSel += 1.0 - computeEqualsSelectivity(tableMetadata, orPred, fieldNames);
        } else if (orPred.isA(SqlKind.LIKE)) {
          // LIKE selectivity is 5% more than a similar equality predicate, capped at CALCITE guess
          orSel +=  Math.min(computeEqualsSelectivity(tableMetadata, orPred, fieldNames) + LIKE_PREDICATE_SELECTIVITY,
              guessSelectivity(orPred));
        } else if (orPred.isA(SqlKind.NOT)) {
          if (orPred instanceof RexCall) {
            // LIKE selectivity is 5% more than a similar equality predicate, capped at CALCITE guess
            RexNode childOp = ((RexCall) orPred).getOperands().get(0);
            if (childOp.isA(SqlKind.LIKE)) {
              orSel += 1.0 - Math.min(computeEqualsSelectivity(tableMetadata, childOp, fieldNames) + LIKE_PREDICATE_SELECTIVITY,
                      guessSelectivity(childOp));
            } else {
              orSel += 1.0 - guessSelectivity(orPred);
            }
          }
        } else if (orPred.isA(SqlKind.IS_NULL)) {
          orSel += 1.0 - computeIsNotNullSelectivity(tableMetadata, orPred, fieldNames);
        } else if (orPred.isA(SqlKind.IS_NOT_NULL)) {
          orSel += computeIsNotNullSelectivity(tableMetadata, orPred, fieldNames);
        } else {
          // Use the CALCITE guess.
          orSel += guessSelectivity(orPred);
        }
      }
      sel *= orSel;
    }
    // Cap selectivity if it exceeds 1.0
    return (sel > 1.0) ? 1.0 : sel;
  }

  private double computeEqualsSelectivity(TableMetadata tableMetadata, RexNode orPred, List<SchemaPath> fieldNames) {
    SchemaPath col = getColumn(orPred, fieldNames);
    if (col != null) {
      ColumnStatistics columnStatistics = tableMetadata != null ? tableMetadata.getColumnStatistics(col) : null;
      Double ndv = columnStatistics != null ? (Double) columnStatistics.getStatistic(ColumnStatisticsKind.NVD) : null;
      if (ndv != null) {
        return 1.00 / ndv;
      }
    }
    return guessSelectivity(orPred);
  }

  // Use histogram if available for the range predicate selectivity
  private double computeRangeSelectivity(TableMetadata tableMetadata, RexNode orPred, List<SchemaPath> fieldNames) {
    SchemaPath col = getColumn(orPred, fieldNames);
    if (col != null) {
      ColumnStatistics columnStatistics = tableMetadata != null ? tableMetadata.getColumnStatistics(col) : null;
      Histogram histogram = columnStatistics != null ? (Histogram) columnStatistics.getStatistic(ColumnStatisticsKind.HISTOGRAM) : null;
      if (histogram != null) {
        Double sel = histogram.estimatedSelectivity(orPred);
        if (sel != null) {
          return sel;
        }
      }
    }
    return guessSelectivity(orPred);
  }

  private double computeIsNotNullSelectivity(TableMetadata tableMetadata, RexNode orPred, List<SchemaPath> fieldNames) {
    SchemaPath col = getColumn(orPred, fieldNames);
    if (col != null) {
      ColumnStatistics columnStatistics = tableMetadata != null ? tableMetadata.getColumnStatistics(col) : null;
      Double nonNullCount = columnStatistics != null ? (Double) columnStatistics.getStatistic(ColumnStatisticsKind.NON_NULL_COUNT) : null;
      if (nonNullCount != null) {
        // Cap selectivity below Calcite Guess
        return Math.min(nonNullCount / (Double) TableStatisticsKind.EST_ROW_COUNT.getValue(tableMetadata),
            RelMdUtil.guessSelectivity(orPred));
      }
    }
    return guessSelectivity(orPred);
  }

  private SchemaPath getColumn(RexNode orPred, List<SchemaPath> fieldNames) {
    if (orPred instanceof RexCall) {
      int colIdx = -1;
      RexInputRef op = findRexInputRef(orPred);
      if (op != null) {
        colIdx = op.getIndex();
      }
      if (colIdx != -1 && colIdx < fieldNames.size()) {
        return fieldNames.get(colIdx);
      } else {
        if (logger.isDebugEnabled()) {
          logger.warn(String.format("No input reference $[%s] found for predicate [%s]",
                  Integer.toString(colIdx), orPred.toString()));
        }
      }
    }
    return null;
  }

  private double guessSelectivity(RexNode orPred) {
    if (logger.isDebugEnabled()) {
      logger.warn(String.format("Using guess for predicate [%s]", orPred.toString()));
    }
    //CALCITE guess
    return RelMdUtil.guessSelectivity(orPred);
  }

  private Double getJoinSelectivity(DrillJoinRelBase rel, RelMetadataQuery mq, RexNode predicate) {
    double sel = 1.0;
    // determine which filters apply to the left vs right
    RexNode leftPred, rightPred;
    JoinRelType joinType = rel.getJoinType();
    final RexBuilder rexBuilder = rel.getCluster().getRexBuilder();
    int[] adjustments = new int[rel.getRowType().getFieldCount()];

    if (DrillRelOptUtil.guessRows(rel)) {
      return super.getSelectivity(rel, mq, predicate);
    }

    if (predicate != null) {
      RexNode pred;
      List<RexNode> leftFilters = new ArrayList<>();
      List<RexNode> rightFilters = new ArrayList<>();
      List<RexNode> joinFilters = new ArrayList<>();
      List<RexNode> predList = RelOptUtil.conjunctions(predicate);

      RelOptUtil.classifyFilters(
          rel,
          predList,
          joinType,
          joinType == JoinRelType.INNER,
          !joinType.generatesNullsOnLeft(),
          !joinType.generatesNullsOnRight(),
          joinFilters,
          leftFilters,
          rightFilters);
      leftPred =
          RexUtil.composeConjunction(rexBuilder, leftFilters, true);
      rightPred =
          RexUtil.composeConjunction(rexBuilder, rightFilters, true);
      for (RelNode child : rel.getInputs()) {
        RexNode modifiedPred = null;

        if (child == rel.getLeft()) {
          pred = leftPred;
        } else {
          pred = rightPred;
        }
        if (pred != null) {
          // convert the predicate to reference the types of the children
          modifiedPred =
              pred.accept(new RelOptUtil.RexInputConverter(
              rexBuilder,
              null,
              child.getRowType().getFieldList(),
              adjustments));
        }
        sel *= mq.getSelectivity(child, modifiedPred);
      }
      sel *= RelMdUtil.guessSelectivity(RexUtil.composeConjunction(rexBuilder, joinFilters, true));
    }
    return sel;
  }

  private static RexInputRef findRexInputRef(final RexNode node) {
    try {
      RexVisitor<Void> visitor =
          new RexVisitorImpl<Void>(true) {
            public Void visitCall(RexCall call) {
              for (RexNode child : call.getOperands()) {
                child.accept(this);
              }
              return super.visitCall(call);
            }

            public Void visitInputRef(RexInputRef inputRef) {
              throw new Util.FoundOne(inputRef);
            }
          };
      node.accept(visitor);
      return null;
    } catch (Util.FoundOne e) {
      Util.swallow(e, null);
      return (RexInputRef) e.getNode();
    }
  }

  private boolean isMultiColumnPredicate(final RexNode node) {
    return findAllRexInputRefs(node).size() > 1;
  }

  private static List<RexInputRef> findAllRexInputRefs(final RexNode node) {
      List<RexInputRef> rexRefs = new ArrayList<>();
      RexVisitor<Void> visitor =
          new RexVisitorImpl<Void>(true) {
            public Void visitInputRef(RexInputRef inputRef) {
              rexRefs.add(inputRef);
              return super.visitInputRef(inputRef);
            }
          };
      node.accept(visitor);
      return rexRefs;
  }
}
