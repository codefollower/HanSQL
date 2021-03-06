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
package org.lealone.hansql.exec.store.parquet;

import org.apache.drill.shaded.guava.com.google.common.base.Stopwatch;
import org.lealone.hansql.common.expression.LogicalExpression;
import org.lealone.hansql.common.expression.ValueExpressions;
import org.lealone.hansql.exec.expr.FilterPredicate;
import org.lealone.hansql.exec.ops.OptimizerRulesContext;
import org.lealone.hansql.exec.physical.base.AbstractGroupScanWithMetadata;
import org.lealone.hansql.exec.planner.common.DrillRelOptUtil;
import org.lealone.hansql.exec.planner.logical.DrillOptiq;
import org.lealone.hansql.exec.planner.logical.DrillParseContext;
import org.lealone.hansql.exec.planner.logical.RelOptHelper;
import org.lealone.hansql.exec.planner.physical.FilterPrel;
import org.lealone.hansql.exec.planner.physical.PrelUtil;
import org.lealone.hansql.exec.planner.physical.ProjectPrel;
import org.lealone.hansql.exec.planner.physical.ScanPrel;
import org.lealone.hansql.exec.store.StoragePluginOptimizerRule;
import org.lealone.hansql.optimizer.plan.RelOptRule;
import org.lealone.hansql.optimizer.plan.RelOptRuleCall;
import org.lealone.hansql.optimizer.plan.RelOptRuleOperand;
import org.lealone.hansql.optimizer.plan.RelOptUtil;
import org.lealone.hansql.optimizer.rel.RelNode;
import org.lealone.hansql.optimizer.rex.RexNode;
import org.lealone.hansql.optimizer.rex.RexUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public abstract class ParquetPushDownFilter extends StoragePluginOptimizerRule {

  private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ParquetPushDownFilter.class);

  private static final Collection<String> BANNED_OPERATORS;

  static {
    BANNED_OPERATORS = new ArrayList<>(1);
    BANNED_OPERATORS.add("flatten");
  }

  public static RelOptRule getFilterOnProject(OptimizerRulesContext optimizerRulesContext) {
    return new ParquetPushDownFilter(
        RelOptHelper.some(FilterPrel.class, RelOptHelper.some(ProjectPrel.class, RelOptHelper.any(ScanPrel.class))),
        "ParquetPushDownFilter:Filter_On_Project", optimizerRulesContext) {

      @Override
      public boolean matches(RelOptRuleCall call) {
        final ScanPrel scan = call.rel(2);
        if (scan.getGroupScan() instanceof AbstractParquetGroupScan) {
          return super.matches(call);
        }
        return false;
      }

      @Override
      public void onMatch(RelOptRuleCall call) {
        final FilterPrel filterRel = call.rel(0);
        final ProjectPrel projectRel = call.rel(1);
        final ScanPrel scanRel = call.rel(2);
        doOnMatch(call, filterRel, projectRel, scanRel);
      }

    };
  }

  public static StoragePluginOptimizerRule getFilterOnScan(OptimizerRulesContext optimizerContext) {
    return new ParquetPushDownFilter(
        RelOptHelper.some(FilterPrel.class, RelOptHelper.any(ScanPrel.class)),
        "ParquetPushDownFilter:Filter_On_Scan", optimizerContext) {

      @Override
      public boolean matches(RelOptRuleCall call) {
        final ScanPrel scan = call.rel(1);
        if (scan.getGroupScan() instanceof AbstractParquetGroupScan) {
          return super.matches(call);
        }
        return false;
      }

      @Override
      public void onMatch(RelOptRuleCall call) {
        final FilterPrel filterRel = call.rel(0);
        final ScanPrel scanRel = call.rel(1);
        doOnMatch(call, filterRel, null, scanRel);
      }
    };
  }

  // private final boolean useNewReader;
  protected final OptimizerRulesContext optimizerContext;

  private ParquetPushDownFilter(RelOptRuleOperand operand, String id, OptimizerRulesContext optimizerContext) {
    super(operand, id);
    this.optimizerContext = optimizerContext;
  }

  protected void doOnMatch(RelOptRuleCall call, FilterPrel filter, ProjectPrel project, ScanPrel scan) {
    AbstractParquetGroupScan groupScan = (AbstractParquetGroupScan) scan.getGroupScan();
    if (groupScan.getFilter() != null && !groupScan.getFilter().equals(ValueExpressions.BooleanExpression.TRUE)) {
      return;
    }

    RexNode condition;
    if (project == null) {
      condition = filter.getCondition();
    } else {
      // get the filter as if it were below the projection.
      condition = RelOptUtil.pushPastProject(filter.getCondition(), project);
    }

    if (condition == null || condition.isAlwaysTrue()) {
      return;
    }

    // get a conjunctions of the filter condition. For each conjunction, if it refers to ITEM or FLATTEN expression
    // then we could not pushed down. Otherwise, it's qualified to be pushed down.
    final List<RexNode> predList = RelOptUtil.conjunctions(RexUtil.toCnf(filter.getCluster().getRexBuilder(), condition));

    final List<RexNode> qualifiedPredList = new ArrayList<>();

    // list of predicates which cannot be converted to parquet filter predicate
    List<RexNode> nonConvertedPredList = new ArrayList<>();

    for (RexNode pred : predList) {
      if (DrillRelOptUtil.findOperators(pred, Collections.emptyList(), BANNED_OPERATORS) == null) {
        LogicalExpression drillPredicate = DrillOptiq.toDrill(
            new DrillParseContext(PrelUtil.getPlannerSettings(call.getPlanner())), scan, pred);

        // checks whether predicate may be used for filter pushdown
        FilterPredicate parquetFilterPredicate =
            groupScan.getFilterPredicate(drillPredicate,
                optimizerContext,
                optimizerContext.getFunctionRegistry(), optimizerContext.getPlannerSettings().getOptions(), false);
        // collects predicates that contain unsupported for filter pushdown expressions
        // to build filter with them
        if (parquetFilterPredicate == null) {
          nonConvertedPredList.add(pred);
        }
        qualifiedPredList.add(pred);
      } else {
        nonConvertedPredList.add(pred);
      }
    }

    final RexNode qualifiedPred = RexUtil.composeConjunction(filter.getCluster().getRexBuilder(), qualifiedPredList, true);

    if (qualifiedPred == null) {
      return;
    }

    LogicalExpression conditionExp = DrillOptiq.toDrill(
        new DrillParseContext(PrelUtil.getPlannerSettings(call.getPlanner())), scan, qualifiedPred);


    Stopwatch timer = logger.isDebugEnabled() ? Stopwatch.createStarted() : null;
    AbstractGroupScanWithMetadata newGroupScan = groupScan.applyFilter(conditionExp, optimizerContext,
        optimizerContext.getFunctionRegistry(), optimizerContext.getPlannerSettings().getOptions());
    if (timer != null) {
      logger.debug("Took {} ms to apply filter on parquet row groups. ", timer.elapsed(TimeUnit.MILLISECONDS));
      timer.stop();
    }

    // For the case when newGroupScan wasn't created, the old one may
    // fully match the filter for the case when row group pruning did not happen.
    if (newGroupScan == null) {
      if (groupScan.isMatchAllMetadata()) {
        RelNode child = project == null ? scan : project;
        // If current row group fully matches filter,
        // but row group pruning did not happen, remove the filter.
        if (nonConvertedPredList.isEmpty()) {
          call.transformTo(child);
        } else if (nonConvertedPredList.size() == predList.size()) {
          // None of the predicates participated in filter pushdown.
          return;
        } else {
          // If some of the predicates weren't used in the filter, creates new filter with them
          // on top of current scan. Excludes the case when all predicates weren't used in the filter.
          call.transformTo(filter.copy(filter.getTraitSet(), child,
              RexUtil.composeConjunction(
                  filter.getCluster().getRexBuilder(),
                  nonConvertedPredList,
                  true)));
        }
      }
      return;
    }

    RelNode newNode = new ScanPrel(scan.getCluster(), scan.getTraitSet(), newGroupScan, scan.getRowType(), scan.getTable());

    if (project != null) {
      newNode = project.copy(project.getTraitSet(), Collections.singletonList(newNode));
    }

    if (newGroupScan.isMatchAllMetadata()) {
      // creates filter from the expressions which can't be pushed to the scan
      if (!nonConvertedPredList.isEmpty()) {
        newNode = filter.copy(filter.getTraitSet(), newNode,
            RexUtil.composeConjunction(
                filter.getCluster().getRexBuilder(),
                nonConvertedPredList,
                true));
      }
      call.transformTo(newNode);
      return;
    }

    final RelNode newFilter = filter.copy(filter.getTraitSet(), Collections.singletonList(newNode));
    call.transformTo(newFilter);
  }
}
