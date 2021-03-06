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

import org.lealone.hansql.common.logical.data.LogicalOperator;
import org.lealone.hansql.exec.planner.common.DrillFilterRelBase;
import org.lealone.hansql.exec.planner.torel.ConversionContext;
import org.lealone.hansql.optimizer.plan.RelOptCluster;
import org.lealone.hansql.optimizer.plan.RelTraitSet;
import org.lealone.hansql.optimizer.rel.InvalidRelException;
import org.lealone.hansql.optimizer.rel.RelNode;
import org.lealone.hansql.optimizer.rex.RexNode;


public class DrillFilterRel extends DrillFilterRelBase implements DrillRel {
  public DrillFilterRel(RelOptCluster cluster, RelTraitSet traits, RelNode child, RexNode condition) {
    super(DRILL_LOGICAL, cluster, traits, child, condition);
  }

  @Override
  public org.lealone.hansql.optimizer.rel.core.Filter copy(RelTraitSet traitSet, RelNode input, RexNode condition) {
    return new DrillFilterRel(getCluster(), traitSet, input, condition);
  }

  @Override
  public LogicalOperator implement(DrillImplementor implementor) {
    final LogicalOperator input = implementor.visitChild(this, 0, getInput());
    org.lealone.hansql.common.logical.data.Filter f = new org.lealone.hansql.common.logical.data.Filter(getFilterExpression(implementor.getContext()));
    f.setInput(input);
    return f;
  }

  public static DrillFilterRel convert(org.lealone.hansql.common.logical.data.Filter filter, ConversionContext context) throws InvalidRelException{
    RelNode input = context.toRel(filter.getInput());
    return new DrillFilterRel(context.getCluster(), context.getLogicalTraits(), input, context.toRex(filter.getExpr()));
  }

  public static DrillFilterRel create(RelNode child, RexNode condition) {
    return new DrillFilterRel(child.getCluster(), child.getTraitSet().plus(DRILL_LOGICAL), child, condition);
  }

}
