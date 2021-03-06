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

import java.util.List;

import org.lealone.hansql.optimizer.plan.Convention;
import org.lealone.hansql.optimizer.plan.RelOptCluster;
import org.lealone.hansql.optimizer.plan.RelTraitSet;
import org.lealone.hansql.optimizer.rel.RelInput;
import org.lealone.hansql.optimizer.rel.RelNode;
import org.lealone.hansql.optimizer.rel.RelShuttle;
import org.lealone.hansql.optimizer.rel.core.Minus;

/**
 * Sub-class of {@link org.lealone.hansql.optimizer.rel.core.Minus}
 * not targeted at any particular engine or calling convention.
 */
public final class LogicalMinus extends Minus {
  //~ Constructors -----------------------------------------------------------

  /**
   * Creates a LogicalMinus.
   *
   * <p>Use {@link #create} unless you know what you're doing.
   */
  public LogicalMinus(RelOptCluster cluster, RelTraitSet traitSet,
      List<RelNode> inputs, boolean all) {
    super(cluster, traitSet, inputs, all);
  }

  @Deprecated // to be removed before 2.0
  public LogicalMinus(RelOptCluster cluster, List<RelNode> inputs,
      boolean all) {
    this(cluster, cluster.traitSetOf(Convention.NONE), inputs, all);
  }


  /**
   * Creates a LogicalMinus by parsing serialized output.
   */
  public LogicalMinus(RelInput input) {
    super(input);
  }

  /** Creates a LogicalMinus. */
  public static LogicalMinus create(List<RelNode> inputs, boolean all) {
    final RelOptCluster cluster = inputs.get(0).getCluster();
    final RelTraitSet traitSet = cluster.traitSetOf(Convention.NONE);
    return new LogicalMinus(cluster, traitSet, inputs, all);
  }

  //~ Methods ----------------------------------------------------------------

  @Override public LogicalMinus copy(RelTraitSet traitSet, List<RelNode> inputs,
      boolean all) {
    assert traitSet.containsIfApplicable(Convention.NONE);
    return new LogicalMinus(getCluster(), traitSet, inputs, all);
  }

  @Override public RelNode accept(RelShuttle shuttle) {
    return shuttle.visit(this);
  }
}

// End LogicalMinus.java
