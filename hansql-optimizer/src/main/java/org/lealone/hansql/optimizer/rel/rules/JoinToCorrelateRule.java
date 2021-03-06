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

import java.util.function.Function;

import org.lealone.hansql.optimizer.plan.Contexts;
import org.lealone.hansql.optimizer.plan.RelOptCluster;
import org.lealone.hansql.optimizer.plan.RelOptRule;
import org.lealone.hansql.optimizer.plan.RelOptRuleCall;
import org.lealone.hansql.optimizer.rel.RelNode;
import org.lealone.hansql.optimizer.rel.core.CorrelationId;
import org.lealone.hansql.optimizer.rel.core.Join;
import org.lealone.hansql.optimizer.rel.core.RelFactories;
import org.lealone.hansql.optimizer.rel.core.SemiJoin;
import org.lealone.hansql.optimizer.rel.logical.LogicalCorrelate;
import org.lealone.hansql.optimizer.rel.logical.LogicalJoin;
import org.lealone.hansql.optimizer.rex.RexBuilder;
import org.lealone.hansql.optimizer.rex.RexInputRef;
import org.lealone.hansql.optimizer.rex.RexNode;
import org.lealone.hansql.optimizer.rex.RexShuttle;
import org.lealone.hansql.optimizer.sql.SemiJoinType;
import org.lealone.hansql.optimizer.tools.RelBuilder;
import org.lealone.hansql.optimizer.tools.RelBuilderFactory;
import org.lealone.hansql.optimizer.util.ImmutableBitSet;
import org.lealone.hansql.optimizer.util.Util;

/**
 * Rule that converts a {@link org.lealone.hansql.optimizer.rel.core.Join}
 * into a {@link org.lealone.hansql.optimizer.rel.logical.LogicalCorrelate}, which can
 * then be implemented using nested loops.
 *
 * <p>For example,</p>
 *
 * <blockquote><code>select * from emp join dept on emp.deptno =
 * dept.deptno</code></blockquote>
 *
 * <p>becomes a Correlator which restarts LogicalTableScan("DEPT") for each
 * row read from LogicalTableScan("EMP").</p>
 *
 * <p>This rule is not applicable if for certain types of outer join. For
 * example,</p>
 *
 * <blockquote><code>select * from emp right join dept on emp.deptno =
 * dept.deptno</code></blockquote>
 *
 * <p>would require emitting a NULL emp row if a certain department contained no
 * employees, and Correlator cannot do that.</p>
 */
public class JoinToCorrelateRule extends RelOptRule {

  /**
   * Function to extract the {@link org.lealone.hansql.optimizer.sql.SemiJoinType} parameter
   * for the creation of the {@link org.lealone.hansql.optimizer.rel.logical.LogicalCorrelate}
   */
  private final Function<Join, SemiJoinType> semiJoinTypeExtractor;

  //~ Static fields/initializers ---------------------------------------------

  /**
   * Rule that converts a {@link org.lealone.hansql.optimizer.rel.logical.LogicalJoin}
   * into a {@link org.lealone.hansql.optimizer.rel.logical.LogicalCorrelate}
   */
  public static final JoinToCorrelateRule JOIN =
      new JoinToCorrelateRule(LogicalJoin.class, RelFactories.LOGICAL_BUILDER,
              "JoinToCorrelateRule", join -> SemiJoinType.of(join.getJoinType()));

  @Deprecated // to be removed (should use JOIN instead), kept for backwards compatibility
  public static final JoinToCorrelateRule INSTANCE = JOIN;

  /**
   * Rule that converts a {@link org.lealone.hansql.optimizer.rel.core.SemiJoin}
   * into a {@link org.lealone.hansql.optimizer.rel.logical.LogicalCorrelate}
   */
  public static final JoinToCorrelateRule SEMI =
      new JoinToCorrelateRule(SemiJoin.class, RelFactories.LOGICAL_BUILDER,
              "SemiJoinToCorrelateRule", join -> SemiJoinType.SEMI);

  //~ Constructors -----------------------------------------------------------

  /**
   * Creates a rule that converts a {@link org.lealone.hansql.optimizer.rel.logical.LogicalJoin}
   * into a {@link org.lealone.hansql.optimizer.rel.logical.LogicalCorrelate}
   */
  public JoinToCorrelateRule(RelBuilderFactory relBuilderFactory) {
    this(LogicalJoin.class, relBuilderFactory, null, join -> SemiJoinType.of(join.getJoinType()));
  }

  @Deprecated // to be removed before 2.0
  protected JoinToCorrelateRule(RelFactories.FilterFactory filterFactory) {
    this(RelBuilder.proto(Contexts.of(filterFactory)));
  }

  /**
   * Creates a JoinToCorrelateRule for a certain sub-class of
   * {@link org.lealone.hansql.optimizer.rel.core.Join} to be transformed into a
   * {@link org.lealone.hansql.optimizer.rel.logical.LogicalCorrelate}
   * @param clazz Class of relational expression to match (must not be null)
   * @param relBuilderFactory Builder for relational expressions
   * @param description Description, or null to guess description
   * @param semiJoinTypeExtractor Function to get the {@link org.lealone.hansql.optimizer.sql.SemiJoinType}
   *                              for the {@link org.lealone.hansql.optimizer.rel.logical.LogicalCorrelate}
   */
  private JoinToCorrelateRule(Class<? extends Join> clazz,
                             RelBuilderFactory relBuilderFactory,
                             String description,
                             Function<Join, SemiJoinType> semiJoinTypeExtractor) {
    super(operand(clazz, any()), relBuilderFactory, description);
    this.semiJoinTypeExtractor = semiJoinTypeExtractor;
  }

  //~ Methods ----------------------------------------------------------------

  public boolean matches(RelOptRuleCall call) {
    Join join = call.rel(0);
    switch (join.getJoinType()) {
    case INNER:
    case LEFT:
      return true;
    case FULL:
    case RIGHT:
      return false;
    default:
      throw Util.unexpected(join.getJoinType());
    }
  }

  public void onMatch(RelOptRuleCall call) {
    assert matches(call);
    final Join join = call.rel(0);
    RelNode right = join.getRight();
    final RelNode left = join.getLeft();
    final int leftFieldCount = left.getRowType().getFieldCount();
    final RelOptCluster cluster = join.getCluster();
    final RexBuilder rexBuilder = cluster.getRexBuilder();
    final RelBuilder relBuilder = call.builder();
    final CorrelationId correlationId = cluster.createCorrel();
    final RexNode corrVar =
        rexBuilder.makeCorrel(left.getRowType(), correlationId);
    final ImmutableBitSet.Builder requiredColumns = ImmutableBitSet.builder();

    // Replace all references of left input with FieldAccess(corrVar, field)
    final RexNode joinCondition = join.getCondition().accept(new RexShuttle() {
      @Override public RexNode visitInputRef(RexInputRef input) {
        int field = input.getIndex();
        if (field >= leftFieldCount) {
          return rexBuilder.makeInputRef(input.getType(),
              input.getIndex() - leftFieldCount);
        }
        requiredColumns.set(field);
        return rexBuilder.makeFieldAccess(corrVar, field);
      }
    });

    relBuilder.push(right).filter(joinCondition);

    RelNode newRel =
        LogicalCorrelate.create(left,
            relBuilder.build(),
            correlationId,
            requiredColumns.build(),
            semiJoinTypeExtractor.apply(join));
    call.transformTo(newRel);
  }
}

// End JoinToCorrelateRule.java
