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
package org.lealone.hansql.optimizer.rel.rules;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;

import org.lealone.hansql.optimizer.plan.RelOptRule;
import org.lealone.hansql.optimizer.plan.RelOptRuleCall;
import org.lealone.hansql.optimizer.plan.RelOptUtil;
import org.lealone.hansql.optimizer.rel.RelNode;
import org.lealone.hansql.optimizer.rel.core.Correlate;
import org.lealone.hansql.optimizer.rel.core.Filter;
import org.lealone.hansql.optimizer.rel.core.JoinRelType;
import org.lealone.hansql.optimizer.rel.core.RelFactories;
import org.lealone.hansql.optimizer.rex.RexBuilder;
import org.lealone.hansql.optimizer.rex.RexNode;
import org.lealone.hansql.optimizer.rex.RexUtil;
import org.lealone.hansql.optimizer.tools.RelBuilder;
import org.lealone.hansql.optimizer.tools.RelBuilderFactory;

/**
 * Planner rule that pushes a {@link Filter} above a {@link Correlate} into the
 * inputs of the Correlate.
 */
public class FilterCorrelateRule extends RelOptRule {

  public static final FilterCorrelateRule INSTANCE =
      new FilterCorrelateRule(RelFactories.LOGICAL_BUILDER);

  //~ Constructors -----------------------------------------------------------

  /**
   * Creates a FilterCorrelateRule.
   */
  public FilterCorrelateRule(RelBuilderFactory builderFactory) {
    super(
        operand(Filter.class,
            operand(Correlate.class, RelOptRule.any())),
        builderFactory, "FilterCorrelateRule");
  }

  /**
   * Creates a FilterCorrelateRule with an explicit root operand and
   * factories.
   */
  @Deprecated // to be removed before 2.0
  public FilterCorrelateRule(RelFactories.FilterFactory filterFactory,
      RelFactories.ProjectFactory projectFactory) {
    this(RelBuilder.proto(filterFactory, projectFactory));
  }

  //~ Methods ----------------------------------------------------------------

  public void onMatch(RelOptRuleCall call) {
    final Filter filter = call.rel(0);
    final Correlate corr = call.rel(1);

    final List<RexNode> aboveFilters =
        RelOptUtil.conjunctions(filter.getCondition());

    final List<RexNode> leftFilters = new ArrayList<>();
    final List<RexNode> rightFilters = new ArrayList<>();

    // Try to push down above filters. These are typically where clause
    // filters. They can be pushed down if they are not on the NULL
    // generating side.
    RelOptUtil.classifyFilters(
        corr,
        aboveFilters,
        JoinRelType.INNER,
        false,
        !corr.getJoinType().toJoinType().generatesNullsOnLeft(),
        !corr.getJoinType().toJoinType().generatesNullsOnRight(),
        aboveFilters,
        leftFilters,
        rightFilters);

    if (leftFilters.isEmpty()
        && rightFilters.isEmpty()) {
      // no filters got pushed
      return;
    }

    // Create Filters on top of the children if any filters were
    // pushed to them.
    final RexBuilder rexBuilder = corr.getCluster().getRexBuilder();
    final RelBuilder relBuilder = call.builder();
    final RelNode leftRel =
        relBuilder.push(corr.getLeft()).filter(leftFilters).build();
    final RelNode rightRel =
        relBuilder.push(corr.getRight()).filter(rightFilters).build();

    // Create the new Correlate
    RelNode newCorrRel =
        corr.copy(corr.getTraitSet(), ImmutableList.of(leftRel, rightRel));

    call.getPlanner().onCopy(corr, newCorrRel);

    if (!leftFilters.isEmpty()) {
      call.getPlanner().onCopy(filter, leftRel);
    }
    if (!rightFilters.isEmpty()) {
      call.getPlanner().onCopy(filter, rightRel);
    }

    // Create a Filter on top of the join if needed
    relBuilder.push(newCorrRel);
    relBuilder.filter(
        RexUtil.fixUp(rexBuilder, aboveFilters,
            RelOptUtil.getFieldTypeList(relBuilder.peek().getRowType())));

    call.transformTo(relBuilder.build());
  }
}

// End FilterCorrelateRule.java
