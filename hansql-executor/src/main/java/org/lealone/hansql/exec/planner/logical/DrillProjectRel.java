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

import java.util.List;

import org.apache.drill.shaded.guava.com.google.common.collect.Lists;
import org.lealone.hansql.common.logical.data.LogicalOperator;
import org.lealone.hansql.common.logical.data.NamedExpression;
import org.lealone.hansql.common.logical.data.Project;
import org.lealone.hansql.exec.planner.common.DrillProjectRelBase;
import org.lealone.hansql.exec.planner.torel.ConversionContext;
import org.lealone.hansql.optimizer.plan.RelOptCluster;
import org.lealone.hansql.optimizer.plan.RelTraitSet;
import org.lealone.hansql.optimizer.rel.InvalidRelException;
import org.lealone.hansql.optimizer.rel.RelNode;
import org.lealone.hansql.optimizer.rel.type.RelDataType;
import org.lealone.hansql.optimizer.rel.type.RelDataTypeField;
import org.lealone.hansql.optimizer.rel.type.RelDataTypeFieldImpl;
import org.lealone.hansql.optimizer.rel.type.RelRecordType;
import org.lealone.hansql.optimizer.rex.RexNode;
import org.lealone.hansql.optimizer.sql.type.SqlTypeName;

/**
 * Project implemented in Drill.
 */
public class DrillProjectRel extends DrillProjectRelBase implements DrillRel {
  protected DrillProjectRel(RelOptCluster cluster, RelTraitSet traits, RelNode child, List<? extends RexNode> exps,
      RelDataType rowType) {
    super(DRILL_LOGICAL, cluster, traits, child, exps, rowType);
  }


  @Override
  public org.lealone.hansql.optimizer.rel.core.Project copy(RelTraitSet traitSet, RelNode input, List<RexNode> exps, RelDataType rowType) {
    return new DrillProjectRel(getCluster(), traitSet, input, exps, rowType);
  }


  @Override
  public LogicalOperator implement(DrillImplementor implementor) {
    LogicalOperator inputOp = implementor.visitChild(this, 0, getInput());
    Project.Builder builder = Project.builder();
    builder.setInput(inputOp);
    for (NamedExpression e: this.getProjectExpressions(implementor.getContext())) {
      builder.addExpr(e);
    }
    return builder.build();
  }

  public static DrillProjectRel convert(Project project, ConversionContext context) throws InvalidRelException{
    RelNode input = context.toRel(project.getInput());
    List<RelDataTypeField> fields = Lists.newArrayList();
    List<RexNode> exps = Lists.newArrayList();
    for(NamedExpression expr : project.getSelections()){
      fields.add(new RelDataTypeFieldImpl(expr.getRef().getRootSegment().getPath(), fields.size(), context.getTypeFactory().createSqlType(SqlTypeName.ANY) ));
      exps.add(context.toRex(expr.getExpr()));
    }
    return new DrillProjectRel(context.getCluster(), context.getLogicalTraits(), input, exps, new RelRecordType(fields));
  }

  /** provide a public method to create an instance of DrillProjectRel.
   *
   * @param cluster
   * @param traits
   * @param child
   * @param exps
   * @param rowType
   * @return new instance of DrillProjectRel
   */
  public static DrillProjectRel create(RelOptCluster cluster, RelTraitSet traits, RelNode child, List<? extends RexNode> exps,
                                       RelDataType rowType) {
    return new DrillProjectRel(cluster, traits, child, exps, rowType);
  }
}
