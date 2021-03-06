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
package org.lealone.hansql.exec.store.ischema;

import org.apache.drill.shaded.guava.com.google.common.collect.ImmutableList;
import org.lealone.hansql.common.expression.LogicalExpression;
import org.lealone.hansql.exec.physical.base.GroupScan;
import org.lealone.hansql.exec.planner.logical.DrillOptiq;
import org.lealone.hansql.exec.planner.logical.DrillParseContext;
import org.lealone.hansql.exec.planner.logical.RelOptHelper;
import org.lealone.hansql.exec.planner.physical.FilterPrel;
import org.lealone.hansql.exec.planner.physical.PrelUtil;
import org.lealone.hansql.exec.planner.physical.ProjectPrel;
import org.lealone.hansql.exec.planner.physical.ScanPrel;
import org.lealone.hansql.exec.store.StoragePluginOptimizerRule;
import org.lealone.hansql.optimizer.plan.RelOptRuleCall;
import org.lealone.hansql.optimizer.plan.RelOptRuleOperand;
import org.lealone.hansql.optimizer.rel.RelNode;
import org.lealone.hansql.optimizer.rex.RexNode;

public abstract class InfoSchemaPushFilterIntoRecordGenerator extends StoragePluginOptimizerRule {

  public static final StoragePluginOptimizerRule IS_FILTER_ON_PROJECT =
      new InfoSchemaPushFilterIntoRecordGenerator(
          RelOptHelper.some(FilterPrel.class, RelOptHelper.some(ProjectPrel.class, RelOptHelper.any(ScanPrel.class))),
          "InfoSchemaPushFilterIntoRecordGenerator:Filter_On_Project") {

        @Override
        public boolean matches(RelOptRuleCall call) {
          final ScanPrel scan = (ScanPrel) call.rel(2);
          GroupScan groupScan = scan.getGroupScan();
          return groupScan instanceof InfoSchemaGroupScan;
        }

        @Override
        public void onMatch(RelOptRuleCall call) {
          final FilterPrel filterRel = (FilterPrel) call.rel(0);
          final ProjectPrel projectRel = (ProjectPrel) call.rel(1);
          final ScanPrel scanRel = call.rel(2);
          doMatch(call, scanRel, projectRel, filterRel);
        }
      };

  public static final StoragePluginOptimizerRule IS_FILTER_ON_SCAN =
      new InfoSchemaPushFilterIntoRecordGenerator(RelOptHelper.some(FilterPrel.class, RelOptHelper.any(ScanPrel.class)),
          "InfoSchemaPushFilterIntoRecordGenerator:Filter_On_Scan") {

        @Override
        public boolean matches(RelOptRuleCall call) {
          final ScanPrel scan = (ScanPrel) call.rel(1);
          GroupScan groupScan = scan.getGroupScan();
          return groupScan instanceof InfoSchemaGroupScan;
        }

        @Override
        public void onMatch(RelOptRuleCall call) {
          final FilterPrel filterRel = (FilterPrel) call.rel(0);
          final ScanPrel scanRel = (ScanPrel) call.rel(1);
          doMatch(call, scanRel, null, filterRel);
        }
      };

  private InfoSchemaPushFilterIntoRecordGenerator(RelOptRuleOperand operand, String id) {
    super(operand, id);
  }

  protected void doMatch(RelOptRuleCall call, ScanPrel scan, ProjectPrel project, FilterPrel filter) {
    final RexNode condition = filter.getCondition();

    InfoSchemaGroupScan groupScan = (InfoSchemaGroupScan)scan.getGroupScan();
    if (groupScan.isFilterPushedDown()) {
      return;
    }

    LogicalExpression conditionExp =
        DrillOptiq.toDrill(new DrillParseContext(PrelUtil.getPlannerSettings(call.getPlanner())), project != null ? project : scan, condition);
    InfoSchemaFilterBuilder filterBuilder = new InfoSchemaFilterBuilder(conditionExp);
    InfoSchemaFilter infoSchemaFilter = filterBuilder.build();
    if (infoSchemaFilter == null) {
      return; //no filter pushdown ==> No transformation.
    }

    final InfoSchemaGroupScan newGroupsScan = new InfoSchemaGroupScan(groupScan.getTable(), infoSchemaFilter);
    newGroupsScan.setFilterPushedDown(true);

    RelNode input = new ScanPrel(scan.getCluster(), filter.getTraitSet(), newGroupsScan, scan.getRowType(), scan.getTable());
    if (project != null) {
      input = project.copy(project.getTraitSet(), input, project.getProjects(), filter.getRowType());
    }

    if (filterBuilder.isAllExpressionsConverted()) {
      // Filter can be removed as all expressions in the filter are converted and pushed to scan
      call.transformTo(input);
    } else {
      call.transformTo(filter.copy(filter.getTraitSet(), ImmutableList.of(input)));
    }
  }
}
