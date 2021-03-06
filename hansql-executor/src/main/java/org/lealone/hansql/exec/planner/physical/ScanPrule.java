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
package org.lealone.hansql.exec.planner.physical;

import org.lealone.hansql.exec.physical.base.GroupScan;
import org.lealone.hansql.exec.planner.fragment.DistributionAffinity;
import org.lealone.hansql.exec.planner.logical.DrillScanRel;
import org.lealone.hansql.exec.planner.logical.RelOptHelper;
import org.lealone.hansql.optimizer.plan.RelOptRule;
import org.lealone.hansql.optimizer.plan.RelOptRuleCall;
import org.lealone.hansql.optimizer.plan.RelTraitSet;

public class ScanPrule extends Prule{
  public static final RelOptRule INSTANCE = new ScanPrule();

  public ScanPrule() {
    super(RelOptHelper.any(DrillScanRel.class), "Prel.ScanPrule");
  }

  @Override
  public void onMatch(RelOptRuleCall call) {
    final DrillScanRel scan = (DrillScanRel) call.rel(0);

    GroupScan groupScan = scan.getGroupScan();

    DrillDistributionTrait partition =
        (groupScan.getMaxParallelizationWidth() > 1 || groupScan.getDistributionAffinity() == DistributionAffinity.HARD)
            ? DrillDistributionTrait.RANDOM_DISTRIBUTED : DrillDistributionTrait.SINGLETON;

    final RelTraitSet traits = scan.getTraitSet().plus(Prel.DRILL_PHYSICAL).plus(partition);

    final ScanPrel newScan = new ScanPrel(scan.getCluster(), traits, groupScan, scan.getRowType(), scan.getTable());

    call.transformTo(newScan);
  }
}
