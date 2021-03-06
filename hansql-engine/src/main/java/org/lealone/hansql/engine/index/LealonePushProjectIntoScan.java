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

import java.util.List;

import org.apache.drill.shaded.guava.com.google.common.collect.Lists;
import org.lealone.hansql.common.exceptions.DrillRuntimeException;
import org.lealone.hansql.exec.planner.common.DrillRelOptUtil;
import org.lealone.hansql.exec.planner.logical.RelOptHelper;
import org.lealone.hansql.exec.planner.physical.Prel;
import org.lealone.hansql.exec.planner.physical.ProjectPrel;
import org.lealone.hansql.exec.planner.physical.ScanPrel;
import org.lealone.hansql.exec.store.StoragePluginOptimizerRule;
import org.lealone.hansql.exec.util.Utilities;
import org.lealone.hansql.optimizer.plan.RelOptRuleCall;
import org.lealone.hansql.optimizer.plan.RelOptRuleOperand;
import org.lealone.hansql.optimizer.plan.RelTrait;
import org.lealone.hansql.optimizer.plan.RelTraitSet;
import org.lealone.hansql.optimizer.rel.RelCollation;
import org.lealone.hansql.optimizer.rel.rules.ProjectRemoveRule;
import org.lealone.hansql.optimizer.rel.type.RelDataType;
import org.lealone.hansql.optimizer.rel.type.RelDataTypeField;
import org.lealone.hansql.optimizer.rex.RexNode;

/**
 * Push a physical Project into Scan. Currently, this rule is only doing projection pushdown for MapRDB-JSON tables
 * since it was needed for the secondary index feature which only applies to Json tables.
 * For binary tables, note that the DrillPushProjectIntoScanRule is still applicable during the logical
 * planning phase.
 */
public abstract class LealonePushProjectIntoScan extends StoragePluginOptimizerRule {
    static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(LealonePushProjectIntoScan.class);

    private LealonePushProjectIntoScan(RelOptRuleOperand operand, String description) {
        super(operand, description);
    }

    public static final StoragePluginOptimizerRule PROJECT_ON_SCAN = new LealonePushProjectIntoScan(
            RelOptHelper.some(ProjectPrel.class, RelOptHelper.any(ScanPrel.class)),
            "MapRDBPushProjIntoScan:Proj_On_Scan") {
        @Override
        public void onMatch(RelOptRuleCall call) {
            final ProjectPrel project = call.rel(0);
            final ScanPrel scan = call.rel(1);

            if (scan.getGroupScan() instanceof LealoneIndexGroupScan) {
                LealoneIndexGroupScan groupScan = (LealoneIndexGroupScan) scan.getGroupScan();

                doPushProjectIntoGroupScan(call, project, scan, groupScan);
            }
        }

        @Override
        public boolean matches(RelOptRuleCall call) {
            final ScanPrel scan = call.rel(1);

            // See class level comments above for why only JsonGroupScan is considered
            if (scan.getGroupScan() instanceof LealoneIndexGroupScan) {
                return super.matches(call);
            }
            return false;
        }
    };

    protected void doPushProjectIntoGroupScan(RelOptRuleCall call, ProjectPrel project, ScanPrel scan,
            LealoneIndexGroupScan groupScan) {
        try {

            DrillRelOptUtil.ProjectPushInfo columnInfo = DrillRelOptUtil.getFieldsInformation(scan.getRowType(),
                    project.getProjects());
            if (columnInfo == null || Utilities.isStarQuery(columnInfo.getFields())
                    || !groupScan.canPushdownProjects(columnInfo.getFields())) {
                return;
            }
            RelTraitSet newTraits = call.getPlanner().emptyTraitSet();
            // Clear out collation trait
            for (RelTrait trait : scan.getTraitSet()) {
                if (!(trait instanceof RelCollation)) {
                    newTraits.plus(trait);
                }
            }
            final ScanPrel newScan = new ScanPrel(scan.getCluster(), newTraits.plus(Prel.DRILL_PHYSICAL),
                    groupScan.clone(columnInfo.getFields()),
                    columnInfo.createNewRowType(project.getInput().getCluster().getTypeFactory()), scan.getTable());

            List<RexNode> newProjects = Lists.newArrayList();
            for (RexNode n : project.getChildExps()) {
                newProjects.add(n.accept(columnInfo.getInputReWriter()));
            }

            final ProjectPrel newProj = new ProjectPrel(project.getCluster(),
                    project.getTraitSet().plus(Prel.DRILL_PHYSICAL), newScan, newProjects, project.getRowType());

            if (ProjectRemoveRule.isTrivial(newProj) &&
            // the old project did not involve any column renaming
                    sameRowTypeProjectionsFields(project.getRowType(), newScan.getRowType())) {
                call.transformTo(newScan);
            } else {
                call.transformTo(newProj);
            }
        } catch (Exception e) {
            throw new DrillRuntimeException(e);
        }
    }

    private boolean sameRowTypeProjectionsFields(RelDataType oldRowType, RelDataType newRowType) {
        for (RelDataTypeField oldField : oldRowType.getFieldList()) {
            String oldProjName = oldField.getName();
            boolean match = false;
            for (RelDataTypeField newField : newRowType.getFieldList()) {
                if (oldProjName.equals(newField.getName())) {
                    match = true;
                    break;
                }
            }
            if (!match) {
                return false;
            }
        }
        return true;
    }
}
