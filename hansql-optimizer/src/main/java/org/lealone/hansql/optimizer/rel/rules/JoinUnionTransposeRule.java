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

import java.util.ArrayList;
import java.util.List;

import org.lealone.hansql.optimizer.plan.RelOptRule;
import org.lealone.hansql.optimizer.plan.RelOptRuleCall;
import org.lealone.hansql.optimizer.plan.RelOptRuleOperand;
import org.lealone.hansql.optimizer.rel.RelNode;
import org.lealone.hansql.optimizer.rel.core.Join;
import org.lealone.hansql.optimizer.rel.core.RelFactories;
import org.lealone.hansql.optimizer.rel.core.SetOp;
import org.lealone.hansql.optimizer.rel.core.Union;
import org.lealone.hansql.optimizer.tools.RelBuilderFactory;

/**
 * Planner rule that pushes a
 * {@link org.lealone.hansql.optimizer.rel.core.Join}
 * past a non-distinct {@link org.lealone.hansql.optimizer.rel.core.Union}.
 */
public class JoinUnionTransposeRule extends RelOptRule {
  public static final JoinUnionTransposeRule LEFT_UNION =
      new JoinUnionTransposeRule(
          operand(Join.class,
              operand(Union.class, any()),
              operand(RelNode.class, any())),
          RelFactories.LOGICAL_BUILDER,
          "JoinUnionTransposeRule(Union-Other)");

  public static final JoinUnionTransposeRule RIGHT_UNION =
      new JoinUnionTransposeRule(
          operand(Join.class,
              operand(RelNode.class, any()),
              operand(Union.class, any())),
          RelFactories.LOGICAL_BUILDER,
          "JoinUnionTransposeRule(Other-Union)");

  /**
   * Creates a JoinUnionTransposeRule.
   *
   * @param operand           root operand, must not be null
   * @param description       Description, or null to guess description
   * @param relBuilderFactory Builder for relational expressions
   */
  public JoinUnionTransposeRule(RelOptRuleOperand operand,
      RelBuilderFactory relBuilderFactory, String description) {
    super(operand, relBuilderFactory, description);
  }

  public void onMatch(RelOptRuleCall call) {
    final Join join = call.rel(0);
    final Union unionRel;
    RelNode otherInput;
    boolean unionOnLeft;
    if (call.rel(1) instanceof Union) {
      unionRel = call.rel(1);
      otherInput = call.rel(2);
      unionOnLeft = true;
    } else {
      otherInput = call.rel(1);
      unionRel = call.rel(2);
      unionOnLeft = false;
    }
    if (!unionRel.all) {
      return;
    }
    if (!join.getVariablesSet().isEmpty()) {
      return;
    }
    // The UNION ALL cannot be on the null generating side
    // of an outer join (otherwise we might generate incorrect
    // rows for the other side for join keys which lack a match
    // in one or both branches of the union)
    if (unionOnLeft) {
      if (join.getJoinType().generatesNullsOnLeft()) {
        return;
      }
    } else {
      if (join.getJoinType().generatesNullsOnRight()) {
        return;
      }
    }
    List<RelNode> newUnionInputs = new ArrayList<>();
    for (RelNode input : unionRel.getInputs()) {
      RelNode joinLeft;
      RelNode joinRight;
      if (unionOnLeft) {
        joinLeft = input;
        joinRight = otherInput;
      } else {
        joinLeft = otherInput;
        joinRight = input;
      }
      newUnionInputs.add(
          join.copy(
              join.getTraitSet(),
              join.getCondition(),
              joinLeft,
              joinRight,
              join.getJoinType(),
              join.isSemiJoinDone()));
    }
    final SetOp newUnionRel =
        unionRel.copy(unionRel.getTraitSet(), newUnionInputs, true);
    call.transformTo(newUnionRel);
  }
}

// End JoinUnionTransposeRule.java
