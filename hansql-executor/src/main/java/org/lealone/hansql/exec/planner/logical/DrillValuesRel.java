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

import org.lealone.hansql.common.JSONOptions;
import org.lealone.hansql.common.logical.data.LogicalOperator;
import org.lealone.hansql.common.logical.data.Values;
import org.lealone.hansql.exec.planner.common.DrillValuesRelBase;
import org.lealone.hansql.optimizer.plan.RelOptCluster;
import org.lealone.hansql.optimizer.plan.RelTraitSet;
import org.lealone.hansql.optimizer.rel.RelNode;
import org.lealone.hansql.optimizer.rel.type.RelDataType;
import org.lealone.hansql.optimizer.rex.RexLiteral;

/**
 * Logical Values implementation in Drill.
 */
public class DrillValuesRel extends DrillValuesRelBase implements DrillRel {

  public DrillValuesRel(RelOptCluster cluster, RelDataType rowType, List<? extends List<RexLiteral>> tuples, RelTraitSet traits) {
    super(cluster, rowType, tuples, traits);
  }

  public DrillValuesRel(RelOptCluster cluster, RelDataType rowType, List<? extends List<RexLiteral>> tuples, RelTraitSet traits, JSONOptions content) {
    super(cluster, rowType, tuples, traits, content);
  }

  @Override
  public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) {
    assert inputs.isEmpty();
    return new DrillValuesRel(getCluster(), rowType, tuples, traitSet, content);
  }

  @Override
  public LogicalOperator implement(DrillImplementor implementor) {
      return Values.builder()
          .content(content.asNode())
          .build();
  }

}
