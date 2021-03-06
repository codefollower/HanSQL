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

import org.lealone.hansql.optimizer.plan.Convention;
import org.lealone.hansql.optimizer.plan.RelOptRule;
import org.lealone.hansql.optimizer.plan.RelOptRuleCall;
import org.lealone.hansql.optimizer.plan.RelTraitSet;
import org.lealone.hansql.optimizer.rel.RelNode;
import org.lealone.hansql.optimizer.rel.core.Window;

public class DrillWindowRule extends RelOptRule {
  public static final RelOptRule INSTANCE = new DrillWindowRule();

  private DrillWindowRule() {
    super(RelOptHelper.some(Window.class, Convention.NONE, RelOptHelper.any(RelNode.class)),
        DrillRelFactories.LOGICAL_BUILDER, "DrillWindowRule");
  }

  @Override
  public void onMatch(RelOptRuleCall call) {
    final Window window = call.rel(0);
    final RelNode input = call.rel(1);
    final RelTraitSet traits = window.getTraitSet().plus(DrillRel.DRILL_LOGICAL).simplify();
    final RelNode convertedInput = convert(input, traits);
    call.transformTo(
        new DrillWindowRel(
            window.getCluster(),
            traits,
            convertedInput,
            window.constants,
            window.getRowType(),
            window.groups));
  }
}
