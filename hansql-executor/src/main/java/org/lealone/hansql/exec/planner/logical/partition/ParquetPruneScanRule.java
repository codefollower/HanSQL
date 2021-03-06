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
package org.lealone.hansql.exec.planner.logical.partition;

import org.lealone.hansql.exec.ops.OptimizerRulesContext;
import org.lealone.hansql.exec.physical.base.GroupScan;
import org.lealone.hansql.exec.planner.ParquetPartitionDescriptor;
import org.lealone.hansql.exec.planner.PartitionDescriptor;
import org.lealone.hansql.exec.planner.logical.DrillFilterRel;
import org.lealone.hansql.exec.planner.logical.DrillProjectRel;
import org.lealone.hansql.exec.planner.logical.DrillScanRel;
import org.lealone.hansql.exec.planner.logical.RelOptHelper;
import org.lealone.hansql.exec.planner.physical.PlannerSettings;
import org.lealone.hansql.exec.planner.physical.PrelUtil;
import org.lealone.hansql.exec.store.parquet.AbstractParquetGroupScan;
import org.lealone.hansql.optimizer.plan.RelOptRule;
import org.lealone.hansql.optimizer.plan.RelOptRuleCall;
import org.lealone.hansql.optimizer.rel.core.TableScan;

public class ParquetPruneScanRule {

  public static RelOptRule getFilterOnProjectParquet(OptimizerRulesContext optimizerRulesContext) {
    return new PruneScanRule(
        RelOptHelper.some(DrillFilterRel.class, RelOptHelper.some(DrillProjectRel.class, RelOptHelper.any(DrillScanRel.class))),
        "PruneScanRule:Filter_On_Project_Parquet",
        optimizerRulesContext) {

      @Override
      public PartitionDescriptor getPartitionDescriptor(PlannerSettings settings, TableScan scanRel) {
        return new ParquetPartitionDescriptor(settings, (DrillScanRel) scanRel);
      }

      @Override
      public boolean matches(RelOptRuleCall call) {
        final DrillScanRel scan = call.rel(2);
        GroupScan groupScan = scan.getGroupScan();
        // this rule is applicable only for parquet based partition pruning
        if (PrelUtil.getPlannerSettings(scan.getCluster().getPlanner()).isHepPartitionPruningEnabled()) {
          return groupScan instanceof AbstractParquetGroupScan && groupScan.supportsPartitionFilterPushdown() && !scan.partitionFilterPushdown();
        } else {
          return groupScan instanceof AbstractParquetGroupScan && groupScan.supportsPartitionFilterPushdown();
        }
      }

      @Override
      public void onMatch(RelOptRuleCall call) {
        final DrillFilterRel filterRel =  call.rel(0);
        final DrillProjectRel projectRel = call.rel(1);
        final DrillScanRel scanRel =  call.rel(2);
        doOnMatch(call, filterRel, projectRel, scanRel);
      }
    };
  }

  public static RelOptRule getFilterOnScanParquet(OptimizerRulesContext optimizerRulesContext) {
    return new PruneScanRule(
        RelOptHelper.some(DrillFilterRel.class, RelOptHelper.any(DrillScanRel.class)),
        "PruneScanRule:Filter_On_Scan_Parquet", optimizerRulesContext) {

      @Override
      public PartitionDescriptor getPartitionDescriptor(PlannerSettings settings, TableScan scanRel) {
        return new ParquetPartitionDescriptor(settings, (DrillScanRel) scanRel);
      }

      @Override
      public boolean matches(RelOptRuleCall call) {
        final DrillScanRel scan = call.rel(1);
        GroupScan groupScan = scan.getGroupScan();
        // this rule is applicable only for parquet based partition pruning
        if (PrelUtil.getPlannerSettings(scan.getCluster().getPlanner()).isHepPartitionPruningEnabled()) {
          return groupScan instanceof AbstractParquetGroupScan && groupScan.supportsPartitionFilterPushdown() && !scan.partitionFilterPushdown();
        } else {
          return groupScan instanceof AbstractParquetGroupScan && groupScan.supportsPartitionFilterPushdown();
        }
      }

      @Override
      public void onMatch(RelOptRuleCall call) {
        final DrillFilterRel filterRel = call.rel(0);
        final DrillScanRel scanRel = call.rel(1);
        doOnMatch(call, filterRel, null, scanRel);
      }
    };
  }
}
