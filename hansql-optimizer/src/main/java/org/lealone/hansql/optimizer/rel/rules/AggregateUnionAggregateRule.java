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

import org.lealone.hansql.optimizer.plan.RelOptRule;
import org.lealone.hansql.optimizer.plan.RelOptRuleCall;
import org.lealone.hansql.optimizer.rel.RelNode;
import org.lealone.hansql.optimizer.rel.core.Aggregate;
import org.lealone.hansql.optimizer.rel.core.RelFactories;
import org.lealone.hansql.optimizer.rel.core.Union;
import org.lealone.hansql.optimizer.rel.logical.LogicalAggregate;
import org.lealone.hansql.optimizer.rel.logical.LogicalUnion;
import org.lealone.hansql.optimizer.tools.RelBuilder;
import org.lealone.hansql.optimizer.tools.RelBuilderFactory;

/**
 * Planner rule that matches
 * {@link org.lealone.hansql.optimizer.rel.core.Aggregate}s beneath a
 * {@link org.lealone.hansql.optimizer.rel.core.Union} and pulls them up, so
 * that a single
 * {@link org.lealone.hansql.optimizer.rel.core.Aggregate} removes duplicates.
 *
 * <p>This rule only handles cases where the
 * {@link org.lealone.hansql.optimizer.rel.core.Union}s
 * still have only two inputs.
 */
public class AggregateUnionAggregateRule extends RelOptRule {
  /** Instance that matches an {@code Aggregate} as the left input of
   * {@code Union}. */
  public static final AggregateUnionAggregateRule AGG_ON_FIRST_INPUT =
      new AggregateUnionAggregateRule(LogicalAggregate.class, LogicalUnion.class,
          LogicalAggregate.class, RelNode.class, RelFactories.LOGICAL_BUILDER,
          "AggregateUnionAggregateRule:first-input-agg");

  /** Instance that matches an {@code Aggregate} as the right input of
   * {@code Union}. */
  public static final AggregateUnionAggregateRule AGG_ON_SECOND_INPUT =
      new AggregateUnionAggregateRule(LogicalAggregate.class, LogicalUnion.class,
          RelNode.class, LogicalAggregate.class, RelFactories.LOGICAL_BUILDER,
          "AggregateUnionAggregateRule:second-input-agg");

  /** Instance that matches an {@code Aggregate} as either input of
   * {@link Union}.
   *
   * <p>Because it matches {@link RelNode} for each input of {@code Union}, it
   * will create O(N ^ 2) matches, which may cost too much during the popMatch
   * phase in VolcanoPlanner. If efficiency is a concern, we recommend that you
   * use {@link #AGG_ON_FIRST_INPUT} and {@link #AGG_ON_SECOND_INPUT} instead. */
  public static final AggregateUnionAggregateRule INSTANCE =
      new AggregateUnionAggregateRule(LogicalAggregate.class,
          LogicalUnion.class, RelNode.class, RelNode.class,
          RelFactories.LOGICAL_BUILDER, "AggregateUnionAggregateRule");

  //~ Constructors -----------------------------------------------------------

  /**
   * Creates a AggregateUnionAggregateRule.
   */
  public AggregateUnionAggregateRule(Class<? extends Aggregate> aggregateClass,
      Class<? extends Union> unionClass,
      Class<? extends RelNode> firstUnionInputClass,
      Class<? extends RelNode> secondUnionInputClass,
      RelBuilderFactory relBuilderFactory,
      String desc) {
    super(
        operandJ(aggregateClass, null, Aggregate::isSimple,
            operand(unionClass,
                operand(firstUnionInputClass, any()),
                operand(secondUnionInputClass, any()))),
        relBuilderFactory, desc);
  }

  @Deprecated // to be removed before 2.0
  public AggregateUnionAggregateRule(Class<? extends Aggregate> aggregateClass,
      RelFactories.AggregateFactory aggregateFactory,
      Class<? extends Union> unionClass,
      RelFactories.SetOpFactory setOpFactory) {
    this(aggregateClass, unionClass, RelNode.class, RelNode.class,
        RelBuilder.proto(aggregateFactory, setOpFactory),
        "AggregateUnionAggregateRule");
  }

  //~ Methods ----------------------------------------------------------------

  public void onMatch(RelOptRuleCall call) {
    final Aggregate topAggRel = call.rel(0);
    final Union union = call.rel(1);

    // If distincts haven't been removed yet, defer invoking this rule
    if (!union.all) {
      return;
    }

    final RelBuilder relBuilder = call.builder();
    final Aggregate bottomAggRel;
    if (call.rel(3) instanceof Aggregate) {
      // Aggregate is the second input
      bottomAggRel = call.rel(3);
      relBuilder.push(call.rel(2))
          .push(call.rel(3).getInput(0));
    } else if (call.rel(2) instanceof Aggregate) {
      // Aggregate is the first input
      bottomAggRel = call.rel(2);
      relBuilder.push(call.rel(2).getInput(0))
          .push(call.rel(3));
    } else {
      return;
    }

    // Only pull up aggregates if they are there just to remove distincts
    if (!topAggRel.getAggCallList().isEmpty()
        || !bottomAggRel.getAggCallList().isEmpty()) {
      return;
    }

    relBuilder.union(true);
    relBuilder.aggregate(relBuilder.groupKey(topAggRel.getGroupSet()),
        topAggRel.getAggCallList());
    call.transformTo(relBuilder.build());
  }
}

// End AggregateUnionAggregateRule.java
