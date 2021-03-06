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
package org.lealone.hansql.exec.planner.common;

import org.lealone.hansql.exec.planner.cost.DrillCostBase;
import org.lealone.hansql.optimizer.plan.RelOptCluster;
import org.lealone.hansql.optimizer.plan.RelOptCost;
import org.lealone.hansql.optimizer.plan.RelOptPlanner;
import org.lealone.hansql.optimizer.plan.RelTraitSet;
import org.lealone.hansql.optimizer.rel.AbstractRelNode;
import org.lealone.hansql.optimizer.rel.metadata.RelMetadataQuery;
import org.lealone.hansql.optimizer.rex.RexNode;

public class DrillUnnestRelBase extends AbstractRelNode implements DrillRelNode {

  final protected RexNode ref;
  final public static String IMPLICIT_COLUMN = DrillRelOptUtil.IMPLICIT_COLUMN;

  public DrillUnnestRelBase(RelOptCluster cluster, RelTraitSet traitSet, RexNode ref) {
    super(cluster, traitSet);
    this.ref = ref;
  }

  @Override
  public RelOptCost computeSelfCost(RelOptPlanner planner, RelMetadataQuery mq) {

    double rowCount = mq.getRowCount(this);
    // Attribute small cost for projecting simple fields. In reality projecting simple columns in not free and
    // this allows projection pushdown/project-merge rules to kick-in thereby eliminating unneeded columns from
    // the projection.
    double cpuCost = DrillCostBase.BASE_CPU_COST * rowCount * this.getRowType().getFieldCount();

    DrillCostBase.DrillCostFactory costFactory = (DrillCostBase.DrillCostFactory) planner.getCostFactory();
    return costFactory.makeCost(rowCount, cpuCost, 0, 0);
  }

  public RexNode getRef() {
    return this.ref;
  }
}