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

import org.lealone.hansql.exec.expr.fn.FunctionImplementationRegistry;
import org.lealone.hansql.exec.planner.DrillRelBuilder;
import org.lealone.hansql.exec.planner.physical.PrelFactories;
import org.lealone.hansql.optimizer.plan.RelOptRule;
import org.lealone.hansql.optimizer.plan.RelOptRuleCall;
import org.lealone.hansql.optimizer.plan.RelOptUtil;
import org.lealone.hansql.optimizer.rel.RelNode;
import org.lealone.hansql.optimizer.rel.core.Project;
import org.lealone.hansql.optimizer.rel.core.RelFactories.ProjectFactory;
import org.lealone.hansql.optimizer.rel.logical.LogicalProject;
import org.lealone.hansql.optimizer.rex.RexCall;
import org.lealone.hansql.optimizer.rex.RexNode;
import org.lealone.hansql.optimizer.rex.RexUtil;
import org.lealone.hansql.optimizer.sql.SqlKind;
import org.lealone.hansql.optimizer.tools.RelBuilder;
import org.lealone.hansql.optimizer.util.Permutation;

import java.util.ArrayList;
import java.util.List;

/**
 * Rule for merging two projects provided the projects aren't projecting identical sets of
 * input references.
 *
 * NOTE: This rules does not extend the Calcite ProjectMergeRule
 * because of CALCITE-2223. Once, fixed this rule be changed accordingly. Please see DRILL-6501.
 */
public class DrillMergeProjectRule extends RelOptRule {

  private FunctionImplementationRegistry functionRegistry;
  private static DrillMergeProjectRule INSTANCE = null;
  private final boolean force;

  public static DrillMergeProjectRule getInstance(boolean force, ProjectFactory pFactory,
      FunctionImplementationRegistry functionRegistry) {
    if (INSTANCE == null) {
      INSTANCE = new DrillMergeProjectRule(force, pFactory, functionRegistry);
    }
    return INSTANCE;
  }

  private DrillMergeProjectRule(boolean force, ProjectFactory pFactory,
      FunctionImplementationRegistry functionRegistry) {

    super(operand(LogicalProject.class,
        operand(LogicalProject.class, any())),
        DrillRelBuilder.proto(pFactory),
        "DrillMergeProjectRule" + (force ? ":force_mode" : ""));
    this.force = force;
    this.functionRegistry = functionRegistry;
  }

  @Override
  public boolean matches(RelOptRuleCall call) {
    Project topProject = call.rel(0);
    Project bottomProject = call.rel(1);

    // We have a complex output type do not fire the merge project rule
    if (checkComplexOutput(topProject) || checkComplexOutput(bottomProject)) {
      return false;
    }

    return true;
  }

  private boolean checkComplexOutput(Project project) {
    for (RexNode expr: project.getChildExps()) {
      if (expr instanceof RexCall) {
        if (functionRegistry.isFunctionComplexOutput(((RexCall) expr).getOperator().getName())) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public void onMatch(RelOptRuleCall call) {
    final Project topProject = call.rel(0);
    final Project bottomProject = call.rel(1);
    final RelBuilder relBuilder = call.builder();

    // If one or both projects are permutations, short-circuit the complex logic
    // of building a RexProgram.
    final Permutation topPermutation = topProject.getPermutation();
    if (topPermutation != null) {
      if (topPermutation.isIdentity()) {
        // Let ProjectRemoveRule handle this.
        return;
      }
      final Permutation bottomPermutation = bottomProject.getPermutation();
      if (bottomPermutation != null) {
        if (bottomPermutation.isIdentity()) {
          // Let ProjectRemoveRule handle this.
          return;
        }
        final Permutation product = topPermutation.product(bottomPermutation);
        relBuilder.push(bottomProject.getInput());
        relBuilder.project(relBuilder.fields(product),
            topProject.getRowType().getFieldNames());
        call.transformTo(relBuilder.build());
        return;
      }
    }

    // If we're not in force mode and the two projects reference identical
    // inputs, then return and let ProjectRemoveRule replace the projects.
    if (!force) {
      if (RexUtil.isIdentity(topProject.getProjects(),
          topProject.getInput().getRowType())) {
        return;
      }
    }

    final List<RexNode> pushedProjects =
        RelOptUtil.pushPastProject(topProject.getProjects(), bottomProject);
    final List<RexNode> newProjects = simplifyCast(pushedProjects);
    final RelNode input = bottomProject.getInput();
    if (RexUtil.isIdentity(newProjects, input.getRowType())) {
      if (force
          || input.getRowType().getFieldNames()
          .equals(topProject.getRowType().getFieldNames())) {
        call.transformTo(input);
        return;
      }
    }

    // replace the two projects with a combined projection
    relBuilder.push(bottomProject.getInput());
    relBuilder.project(newProjects, topProject.getRowType().getFieldNames());
    call.transformTo(relBuilder.build());
  }

  public static List<RexNode> simplifyCast(List<RexNode> projectExprs) {
    final List<RexNode> list = new ArrayList<>();
    for (RexNode rex: projectExprs) {
      if (rex.getKind() == SqlKind.CAST) {
        RexNode operand = ((RexCall) rex).getOperands().get(0);
        while (operand.getKind() == SqlKind.CAST
            && operand.getType().equals(rex.getType())) {
          rex = operand;
          operand = ((RexCall) rex).getOperands().get(0);
        }

      }
      list.add(rex);
    }
    return list;
  }

  /**
   * The purpose of the replace() method is to allow the caller to replace a 'top' and 'bottom' project with
   * a single merged project with the assumption that caller knows exactly the semantics/correctness of merging
   * the two projects. This is not applying the full fledged DrillMergeProjectRule.
   * @param topProject
   * @param bottomProject
   * @return new project after replacement
   */
  public static Project replace(Project topProject, Project bottomProject) {
    final List<RexNode> newProjects =
        RelOptUtil.pushPastProject(topProject.getProjects(), bottomProject);

    // replace the two projects with a combined projection
    if (topProject instanceof DrillProjectRel) {
      RelNode newProjectRel = DrillRelFactories.DRILL_LOGICAL_PROJECT_FACTORY.createProject(
          bottomProject.getInput(), newProjects,
          topProject.getRowType().getFieldNames());

      return (Project) newProjectRel;
    }
    else {
      RelNode newProjectRel = PrelFactories.PROJECT_FACTORY.createProject(
          bottomProject.getInput(), newProjects,
          topProject.getRowType().getFieldNames());

      return (Project) newProjectRel;
    }
  }

}
