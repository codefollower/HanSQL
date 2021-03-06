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
package org.lealone.hansql.optimizer.rel.logical;

import org.lealone.hansql.optimizer.config.CalciteSystemProperty;
import org.lealone.hansql.optimizer.plan.Convention;
import org.lealone.hansql.optimizer.plan.RelOptCluster;
import org.lealone.hansql.optimizer.plan.RelTraitSet;
import org.lealone.hansql.optimizer.rel.RelInput;
import org.lealone.hansql.optimizer.rel.RelNode;
import org.lealone.hansql.optimizer.rel.RelShuttle;
import org.lealone.hansql.optimizer.rel.core.Correlate;
import org.lealone.hansql.optimizer.rel.core.CorrelationId;
import org.lealone.hansql.optimizer.sql.SemiJoinType;
import org.lealone.hansql.optimizer.util.ImmutableBitSet;
import org.lealone.hansql.optimizer.util.Litmus;

/**
 * A relational operator that performs nested-loop joins.
 *
 * <p>It behaves like a kind of {@link org.lealone.hansql.optimizer.rel.core.Join},
 * but works by setting variables in its environment and restarting its
 * right-hand input.
 *
 * <p>A LogicalCorrelate is used to represent a correlated query. One
 * implementation strategy is to de-correlate the expression.
 *
 * @see org.lealone.hansql.optimizer.rel.core.CorrelationId
 */
public final class LogicalCorrelate extends Correlate {
  //~ Instance fields --------------------------------------------------------

  //~ Constructors -----------------------------------------------------------

  /**
   * Creates a LogicalCorrelate.
   * @param cluster      cluster this relational expression belongs to
   * @param left         left input relational expression
   * @param right        right input relational expression
   * @param correlationId variable name for the row of left input
   * @param requiredColumns Required columns
   * @param joinType     join type
   */
  public LogicalCorrelate(
      RelOptCluster cluster,
      RelTraitSet traitSet,
      RelNode left,
      RelNode right,
      CorrelationId correlationId,
      ImmutableBitSet requiredColumns,
      SemiJoinType joinType) {
    super(
        cluster,
        traitSet,
        left,
        right,
        correlationId,
        requiredColumns,
        joinType);
    assert !CalciteSystemProperty.DEBUG.value() || isValid(Litmus.THROW, null);
  }

  @Deprecated // to be removed before 2.0
  public LogicalCorrelate(RelOptCluster cluster, RelNode left, RelNode right,
      CorrelationId correlationId,
      ImmutableBitSet requiredColumns, SemiJoinType joinType) {
    this(cluster, cluster.traitSetOf(Convention.NONE), left, right,
        correlationId, requiredColumns, joinType);
  }

  /**
   * Creates a LogicalCorrelate by parsing serialized output.
   */
  public LogicalCorrelate(RelInput input) {
    this(input.getCluster(), input.getTraitSet(), input.getInputs().get(0),
        input.getInputs().get(1),
        new CorrelationId((Integer) input.get("correlationId")),
        input.getBitSet("requiredColumns"),
        input.getEnum("joinType", SemiJoinType.class));
  }

  /** Creates a LogicalCorrelate. */
  public static LogicalCorrelate create(RelNode left, RelNode right,
      CorrelationId correlationId, ImmutableBitSet requiredColumns,
      SemiJoinType joinType) {
    final RelOptCluster cluster = left.getCluster();
    final RelTraitSet traitSet = cluster.traitSetOf(Convention.NONE);
    return new LogicalCorrelate(cluster, traitSet, left, right, correlationId,
        requiredColumns, joinType);
  }

  //~ Methods ----------------------------------------------------------------

  @Override public LogicalCorrelate copy(RelTraitSet traitSet,
      RelNode left, RelNode right, CorrelationId correlationId,
      ImmutableBitSet requiredColumns, SemiJoinType joinType) {
    assert traitSet.containsIfApplicable(Convention.NONE);
    return new LogicalCorrelate(getCluster(), traitSet, left, right,
        correlationId, requiredColumns, joinType);
  }

  @Override public RelNode accept(RelShuttle shuttle) {
    return shuttle.visit(this);
  }
}

// End LogicalCorrelate.java
