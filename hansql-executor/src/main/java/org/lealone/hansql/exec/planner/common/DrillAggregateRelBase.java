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

import org.lealone.hansql.exec.expr.holders.IntHolder;
import org.lealone.hansql.exec.ExecConstants;
import org.lealone.hansql.exec.planner.cost.DrillCostBase;
import org.lealone.hansql.exec.planner.cost.DrillCostBase.DrillCostFactory;
import org.lealone.hansql.exec.planner.physical.PrelUtil;
import org.lealone.hansql.optimizer.plan.RelOptCluster;
import org.lealone.hansql.optimizer.plan.RelOptCost;
import org.lealone.hansql.optimizer.plan.RelOptPlanner;
import org.lealone.hansql.optimizer.plan.RelTraitSet;
import org.lealone.hansql.optimizer.rel.RelNode;
import org.lealone.hansql.optimizer.rel.core.Aggregate;
import org.lealone.hansql.optimizer.rel.core.AggregateCall;
import org.lealone.hansql.optimizer.rel.metadata.RelMetadataQuery;
import org.lealone.hansql.optimizer.util.ImmutableBitSet;

import java.util.List;


/**
 * Base class for logical and physical Aggregations implemented in Drill
 */
public abstract class DrillAggregateRelBase extends Aggregate implements DrillRelNode {

  public DrillAggregateRelBase(RelOptCluster cluster, RelTraitSet traits, RelNode child, boolean indicator,
      ImmutableBitSet groupSet, List<ImmutableBitSet> groupSets, List<AggregateCall> aggCalls) {
    super(cluster, traits, child, indicator, groupSet, groupSets, aggCalls);
  }

  /**
   * Estimate cost of hash agg. Called by DrillAggregateRel.computeSelfCost() and HashAggPrel.computeSelfCost()
  */
  protected RelOptCost computeHashAggCost(RelOptPlanner planner, RelMetadataQuery mq) {
    if(PrelUtil.getSettings(getCluster()).useDefaultCosting()) {
      return super.computeSelfCost(planner, mq).multiplyBy(.1);
    }
    RelNode child = this.getInput();
    double inputRows = mq.getRowCount(child);

    int numGroupByFields = this.getGroupCount();
    int numAggrFields = this.aggCalls.size();
    // cpu cost of hashing each grouping key
    double cpuCost = DrillCostBase.HASH_CPU_COST * numGroupByFields * inputRows;
    // add cpu cost for computing the aggregate functions
    cpuCost += DrillCostBase.FUNC_CPU_COST * numAggrFields * inputRows;
    double diskIOCost = 0; // assume in-memory for now until we enforce operator-level memory constraints

    // TODO: use distinct row count
    // + hash table template stuff
    double factor = PrelUtil.getPlannerSettings(planner).getOptions()
        .getOption(ExecConstants.HASH_AGG_TABLE_FACTOR_KEY).float_val;
    long fieldWidth = PrelUtil.getPlannerSettings(planner).getOptions()
        .getOption(ExecConstants.AVERAGE_FIELD_WIDTH_KEY).num_val;

    // table + hashValues + links
    double memCost =
        (
            (fieldWidth * numGroupByFields) +
                IntHolder.WIDTH +
                IntHolder.WIDTH
        ) * inputRows * factor;

    DrillCostFactory costFactory = (DrillCostFactory) planner.getCostFactory();
    return costFactory.makeCost(inputRows, cpuCost, diskIOCost, 0 /* network cost */, memCost);

  }

  protected RelOptCost computeLogicalAggCost(RelOptPlanner planner, RelMetadataQuery mq) {
    // Similar to Join cost estimation, use HashAgg cost during the logical planning.
    return computeHashAggCost(planner, mq);
  }

  @Override
  public double estimateRowCount(RelMetadataQuery mq) {
    // Get the number of distinct group-by key values present in the input
    if (!DrillRelOptUtil.guessRows(this)) {
      return mq.getRowCount(this);
    } else {
      return super.estimateRowCount(mq);
    }
  }
}
