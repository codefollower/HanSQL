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
import org.lealone.hansql.exec.planner.cost.DrillCostBase.DrillCostFactory;
import org.lealone.hansql.exec.planner.physical.PrelUtil;
import org.lealone.hansql.optimizer.plan.RelOptCluster;
import org.lealone.hansql.optimizer.plan.RelOptCost;
import org.lealone.hansql.optimizer.plan.RelOptPlanner;
import org.lealone.hansql.optimizer.plan.RelTraitSet;
import org.lealone.hansql.optimizer.rel.RelNode;
import org.lealone.hansql.optimizer.rel.RelWriter;
import org.lealone.hansql.optimizer.rel.SingleRel;
import org.lealone.hansql.optimizer.rel.metadata.RelMetadataQuery;
import org.lealone.hansql.optimizer.rex.RexLiteral;
import org.lealone.hansql.optimizer.rex.RexNode;

/**
 * Base class for logical and physical Limits implemented in Drill
 */
public abstract class DrillLimitRelBase extends SingleRel implements DrillRelNode {
  protected RexNode offset;
  protected RexNode fetch;
  private boolean pushDown;  // whether limit has been pushed past its child.
                             // Limit is special in that when it's pushed down, the original LIMIT still remains.
                             // Once the limit is pushed down, this flag will be TRUE for the original LIMIT
                             // and be FALSE for the pushed down LIMIT.
                             // This flag will prevent optimization rules to fire in a loop.

  public DrillLimitRelBase(RelOptCluster cluster, RelTraitSet traitSet, RelNode child, RexNode offset, RexNode fetch) {
    this(cluster, traitSet, child, offset, fetch, false);
  }

  public DrillLimitRelBase(RelOptCluster cluster, RelTraitSet traitSet, RelNode child, RexNode offset, RexNode fetch, boolean pushDown) {
    super(cluster, traitSet, child);
    this.offset = offset;
    this.fetch = fetch;
    this.pushDown = pushDown;
  }

  public RexNode getOffset() {
    return this.offset;
  }

  public RexNode getFetch() {
    return this.fetch;
  }

  @Override
  public RelOptCost computeSelfCost(RelOptPlanner planner, RelMetadataQuery mq) {
    if(PrelUtil.getSettings(getCluster()).useDefaultCosting()) {
      return super.computeSelfCost(planner, mq).multiplyBy(.1);
    }

    double numRows = estimateRowCount(mq);
    double cpuCost = DrillCostBase.COMPARE_CPU_COST * numRows;
    DrillCostFactory costFactory = (DrillCostFactory)planner.getCostFactory();
    return costFactory.makeCost(numRows, cpuCost, 0, 0);
  }

  @Override
  public RelWriter explainTerms(RelWriter pw) {
    super.explainTerms(pw);
    pw.itemIf("offset", offset, offset != null);
    pw.itemIf("fetch", fetch, fetch != null);
    return pw;
  }

  @Override
  public double estimateRowCount(RelMetadataQuery mq) {
    int off = offset != null? RexLiteral.intValue(offset): 0;

    if (fetch == null) {
      // If estimated rowcount is less than offset return 0
      return Math.max(0, getInput().estimateRowCount(mq) - off);
    } else {
      int f = RexLiteral.intValue(fetch);
      return off + f;
    }
  }

  public boolean isPushDown() {
    return this.pushDown;
  }

}
