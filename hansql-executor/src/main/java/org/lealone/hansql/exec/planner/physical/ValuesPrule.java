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

import org.lealone.hansql.exec.planner.logical.DrillRelFactories;
import org.lealone.hansql.exec.planner.logical.DrillValuesRel;
import org.lealone.hansql.exec.planner.logical.RelOptHelper;
import org.lealone.hansql.optimizer.plan.RelOptRule;
import org.lealone.hansql.optimizer.plan.RelOptRuleCall;

public class ValuesPrule extends RelOptRule {

  public static final ValuesPrule INSTANCE = new ValuesPrule();

  private ValuesPrule() {
    super(RelOptHelper.any(DrillValuesRel.class), DrillRelFactories.LOGICAL_BUILDER, "Prel.ValuesPrule");
  }

  @Override
  public void onMatch(final RelOptRuleCall call) {
    final DrillValuesRel rel = call.rel(0);
    call.transformTo(new ValuesPrel(rel.getCluster(), rel.getRowType(), rel.getTuples(),
        rel.getTraitSet().plus(Prel.DRILL_PHYSICAL), rel.getContent()));
  }

}
