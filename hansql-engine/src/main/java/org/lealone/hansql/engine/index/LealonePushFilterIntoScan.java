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
package org.lealone.hansql.engine.index;

import org.apache.drill.shaded.guava.com.google.common.collect.ImmutableList;
import org.lealone.hansql.common.expression.LogicalExpression;
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
import org.lealone.hansql.optimizer.plan.RelOptUtil;
import org.lealone.hansql.optimizer.rel.RelNode;
import org.lealone.hansql.optimizer.rex.RexNode;

public abstract class LealonePushFilterIntoScan extends StoragePluginOptimizerRule {

    private LealonePushFilterIntoScan(RelOptRuleOperand operand, String description) {
        super(operand, description);
    }

    public static final StoragePluginOptimizerRule FILTER_ON_SCAN = new LealonePushFilterIntoScan(
            RelOptHelper.some(FilterPrel.class, RelOptHelper.any(ScanPrel.class)),
            "MapRDBPushFilterIntoScan:Filter_On_Scan") {

        @Override
        public void onMatch(RelOptRuleCall call) {
            final FilterPrel filter = call.rel(0);
            final ScanPrel scan = call.rel(1);

            final RexNode condition = filter.getCondition();

            if (scan.getGroupScan() instanceof LealoneIndexGroupScan) {
                LealoneIndexGroupScan groupScan = (LealoneIndexGroupScan) scan.getGroupScan();
                doPushFilterIntoGroupScan(call, filter, null, scan, groupScan, condition);
            }
        }

        @Override
        public boolean matches(RelOptRuleCall call) {
            final ScanPrel scan = (ScanPrel) call.rel(1);
            if (scan.getGroupScan() instanceof LealoneIndexGroupScan) {
                return super.matches(call);
            }
            return false;
        }
    };

    public static final StoragePluginOptimizerRule FILTER_ON_PROJECT = new LealonePushFilterIntoScan(
            RelOptHelper.some(FilterPrel.class, RelOptHelper.some(ProjectPrel.class, RelOptHelper.any(ScanPrel.class))),
            "MapRDBPushFilterIntoScan:Filter_On_Project") {

        @Override
        public void onMatch(RelOptRuleCall call) {
            final FilterPrel filter = call.rel(0);
            final ProjectPrel project = call.rel(1);
            final ScanPrel scan = call.rel(2);

            // convert the filter to one that references the child of the project
            final RexNode condition = RelOptUtil.pushPastProject(filter.getCondition(), project);

            if (scan.getGroupScan() instanceof LealoneIndexGroupScan) {
                LealoneIndexGroupScan groupScan = (LealoneIndexGroupScan) scan.getGroupScan();
                doPushFilterIntoGroupScan(call, filter, project, scan, groupScan, condition);
            }
        }

        @Override
        public boolean matches(RelOptRuleCall call) {
            final ScanPrel scan = call.rel(2);
            if (scan.getGroupScan() instanceof LealoneIndexGroupScan) {
                return super.matches(call);
            }
            return false;
        }
    };

    @SuppressWarnings("unused")
    protected void doPushFilterIntoGroupScan(final RelOptRuleCall call, final FilterPrel filter,
            final ProjectPrel project, final ScanPrel scan, final LealoneIndexGroupScan groupScan,
            final RexNode condition) {

        // if (groupScan.isFilterPushedDown()) {
        // /*
        // * The rule can get triggered again due to the transformed "scan => filter" sequence
        // * created by the earlier execution of this rule when we could not do a complete
        // * conversion of Optiq Filter's condition to HBase Filter. In such cases, we rely upon
        // * this flag to not do a re-processing of the rule on the already transformed call.
        // */
        // return;
        // }

        final LogicalExpression conditionExp = DrillOptiq
                .toDrill(new DrillParseContext(PrelUtil.getPlannerSettings(call.getPlanner())), scan, condition);
        // final MapRDBFilterBuilder maprdbFilterBuilder = new MapRDBFilterBuilder(groupScan, conditionExp);
        // final HBaseScanSpec newScanSpec = maprdbFilterBuilder.parseTree();
        // if (newScanSpec == null) {
        // return; // no filter pushdown ==> No transformation.
        // }

        // Pass tableStats from old groupScan so we do not go and fetch stats (an expensive operation) again from MapR
        // DB client.
        final LealoneIndexGroupScan newGroupsScan = new LealoneIndexGroupScan(groupScan);
        newGroupsScan.setFilterPushedDown(true);

        final ScanPrel newScanPrel = new ScanPrel(scan.getCluster(), filter.getTraitSet(), newGroupsScan,
                scan.getRowType(), scan.getTable());

        // Depending on whether is a project in the middle, assign either scan or copy of project to childRel.
        final RelNode childRel = project == null ? newScanPrel
                : project.copy(project.getTraitSet(), ImmutableList.of((RelNode) newScanPrel));

        // if (maprdbFilterBuilder.isAllExpressionsConverted()) {
        // /*
        // * Since we could convert the entire filter condition expression into an HBase filter,
        // * we can eliminate the filter operator altogether.
        // */
        // call.transformTo(childRel);
        // } else {
        // call.transformTo(filter.copy(filter.getTraitSet(), ImmutableList.of(childRel)));
        // }
    }

}
