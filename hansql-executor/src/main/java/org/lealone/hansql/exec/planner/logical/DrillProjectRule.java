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
import org.lealone.hansql.optimizer.rel.core.Project;
import org.lealone.hansql.optimizer.rel.logical.LogicalProject;

/**
 * Rule that converts a {@link org.lealone.hansql.optimizer.rel.logical.LogicalProject} to a Drill "project" operation.
 */
public class DrillProjectRule extends RelOptRule {
  public static final RelOptRule INSTANCE = new DrillProjectRule();

  private DrillProjectRule() {
    super(RelOptHelper.any(LogicalProject.class, Convention.NONE),
        DrillRelFactories.LOGICAL_BUILDER, "DrillProjectRule");
  }

  @Override
  public void onMatch(RelOptRuleCall call) {
    final Project project = call.rel(0);
    final RelNode input = project.getInput();
    final RelTraitSet traits = project.getTraitSet().plus(DrillRel.DRILL_LOGICAL);
    final RelNode convertedInput = convert(input, input.getTraitSet().plus(DrillRel.DRILL_LOGICAL).simplify());
    call.transformTo(new DrillProjectRel(
        project.getCluster(), traits, convertedInput, project.getProjects(), project.getRowType()));
  }
}
