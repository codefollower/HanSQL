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
package org.lealone.hansql.exec.planner.logical;

import java.util.List;

import org.lealone.hansql.common.logical.data.LogicalOperator;
import org.lealone.hansql.common.logical.data.Union;
import org.lealone.hansql.exec.planner.common.DrillUnionRelBase;
import org.lealone.hansql.exec.planner.torel.ConversionContext;
import org.lealone.hansql.optimizer.plan.RelOptCluster;
import org.lealone.hansql.optimizer.plan.RelOptCost;
import org.lealone.hansql.optimizer.plan.RelOptPlanner;
import org.lealone.hansql.optimizer.plan.RelTraitSet;
import org.lealone.hansql.optimizer.rel.InvalidRelException;
import org.lealone.hansql.optimizer.rel.RelNode;
import org.lealone.hansql.optimizer.rel.metadata.RelMetadataQuery;
import org.lealone.hansql.optimizer.util.Ord;

/**
 * Union implemented in Drill.
 */
public class DrillUnionRel extends DrillUnionRelBase implements DrillRel {
  /** Creates a DrillUnionRel. */
  public DrillUnionRel(RelOptCluster cluster, RelTraitSet traits,
      List<RelNode> inputs, boolean all, boolean checkCompatibility) throws InvalidRelException {
    super(cluster, traits, inputs, all, checkCompatibility);
  }

  @Override
  public DrillUnionRel copy(RelTraitSet traitSet, List<RelNode> inputs,
      boolean all) {
    try {
      return new DrillUnionRel(getCluster(), traitSet, inputs, all,
          false /* don't check compatibility during copy */);
    } catch (InvalidRelException e) {
      throw new AssertionError(e);
    }
  }

  @Override
  public RelOptCost computeSelfCost(RelOptPlanner planner, RelMetadataQuery mq) {
    // divide cost by two to ensure cheaper than EnumerableDrillRel
    return super.computeSelfCost(planner, mq).multiplyBy(.5);
  }

  @Override
  public LogicalOperator implement(DrillImplementor implementor) {
    Union.Builder builder = Union.builder();
    for (Ord<RelNode> input : Ord.zip(inputs)) {
      builder.addInput(implementor.visitChild(this, input.i, input.e));
    }
    builder.setDistinct(!all);
    return builder.build();
  }

  public static DrillUnionRel convert(Union union, ConversionContext context) throws InvalidRelException{
    throw new UnsupportedOperationException();
  }
}
