/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lealone.hansql.optimizer.rel.rules;

import org.lealone.hansql.optimizer.plan.RelOptRule;
import org.lealone.hansql.optimizer.plan.RelOptRuleCall;
import org.lealone.hansql.optimizer.plan.RelOptRuleOperand;
import org.lealone.hansql.optimizer.rel.RelNode;
import org.lealone.hansql.optimizer.rel.core.Project;
import org.lealone.hansql.optimizer.rel.core.RelFactories;
import org.lealone.hansql.optimizer.rel.core.Sort;
import org.lealone.hansql.optimizer.tools.RelBuilderFactory;

import com.google.common.collect.ImmutableList;

/**
 * Planner rule that pushes
 * a {@link org.lealone.hansql.optimizer.rel.core.Project}
 * past a {@link org.lealone.hansql.optimizer.rel.core.Sort}.
 *
 * @see org.lealone.hansql.optimizer.rel.rules.SortProjectTransposeRule
 */
public class ProjectSortTransposeRule extends RelOptRule {
  public static final ProjectSortTransposeRule INSTANCE =
      new ProjectSortTransposeRule(Project.class, Sort.class,
          RelFactories.LOGICAL_BUILDER);

  //~ Constructors -----------------------------------------------------------

  /** Creates a ProjectSortTransposeRule. */
  private ProjectSortTransposeRule(Class<Project> projectClass,
      Class<Sort> sortClass, RelBuilderFactory relBuilderFactory) {
    this(
        operand(projectClass,
            operand(sortClass, any())),
        relBuilderFactory, null);
  }

  @Deprecated // to be removed before 2.0
  protected ProjectSortTransposeRule(RelOptRuleOperand operand) {
    this(operand, RelFactories.LOGICAL_BUILDER, null);
  }

  /** Creates a ProjectSortTransposeRule with an operand. */
  protected ProjectSortTransposeRule(RelOptRuleOperand operand,
      RelBuilderFactory relBuilderFactory, String description) {
    super(operand, relBuilderFactory, description);
  }

  //~ Methods ----------------------------------------------------------------

  public void onMatch(RelOptRuleCall call) {
    final Project project = call.rel(0);
    final Sort sort = call.rel(1);
    if (sort.getClass() != Sort.class) {
      return;
    }
    RelNode newProject =
        project.copy(
            project.getTraitSet(), ImmutableList.of(sort.getInput()));
    final Sort newSort =
        sort.copy(
            sort.getTraitSet(),
            newProject,
            sort.getCollation(),
            sort.offset,
            sort.fetch);
    call.transformTo(newSort);
  }
}

// End ProjectSortTransposeRule.java
