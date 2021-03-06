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

import org.lealone.hansql.exec.planner.logical.DrillDirectScanRel;
import org.lealone.hansql.exec.planner.logical.RelOptHelper;
import org.lealone.hansql.optimizer.plan.RelOptRule;
import org.lealone.hansql.optimizer.plan.RelOptRuleCall;
import org.lealone.hansql.optimizer.plan.RelTraitSet;

public class DirectScanPrule extends Prule {

  public static final RelOptRule INSTANCE = new DirectScanPrule();

  public DirectScanPrule() {
    super(RelOptHelper.any(DrillDirectScanRel.class), "Prel.DirectScanPrule");
  }

  @Override
  public void onMatch(RelOptRuleCall call) {
    final DrillDirectScanRel scan = call.rel(0);
    final RelTraitSet traits = scan.getTraitSet().plus(Prel.DRILL_PHYSICAL);

    final DirectScanPrel newScan = new DirectScanPrel(scan.getCluster(), traits, scan.getGroupScan(), scan.getRowType());
    call.transformTo(newScan);
  }
}
