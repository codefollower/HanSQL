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

import org.lealone.hansql.common.expression.SchemaPath;
import org.lealone.hansql.exec.physical.base.PhysicalOperator;
import org.lealone.hansql.exec.physical.config.UnnestPOP;
import org.lealone.hansql.exec.planner.common.DrillUnnestRelBase;
import org.lealone.hansql.exec.planner.physical.visitor.PrelVisitor;
import org.lealone.hansql.exec.record.BatchSchema;
import org.lealone.hansql.optimizer.plan.RelOptCluster;
import org.lealone.hansql.optimizer.plan.RelTraitSet;
import org.lealone.hansql.optimizer.rel.RelNode;
import org.lealone.hansql.optimizer.rel.type.RelDataType;
import org.lealone.hansql.optimizer.rel.type.RelDataTypeFactory;
import org.lealone.hansql.optimizer.rel.type.RelDataTypeField;
import org.lealone.hansql.optimizer.rex.RexFieldAccess;
import org.lealone.hansql.optimizer.rex.RexNode;
import org.lealone.hansql.optimizer.rex.RexShuttle;
import org.lealone.hansql.optimizer.sql.type.SqlTypeName;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class UnnestPrel extends DrillUnnestRelBase implements Prel {

  protected final UnnestPOP unnestPOP;

  public UnnestPrel(RelOptCluster cluster, RelTraitSet traits,
                    RelDataType rowType, RexNode ref) {
    super(cluster, traits, ref);
    this.unnestPOP = new UnnestPOP(null, SchemaPath.getSimplePath(((RexFieldAccess)ref).getField().getName()), DrillUnnestRelBase.IMPLICIT_COLUMN);
    this.rowType = rowType;
  }

  @Override
  public Iterator<Prel> iterator() {
    return Collections.emptyIterator();
  }

  @Override
  public <T, X, E extends Throwable> T accept(PrelVisitor<T, X, E> visitor, X value) throws E {
    return visitor.visitUnnest(this, value);
  }

  @Override
  public PhysicalOperator getPhysicalOperator(PhysicalPlanCreator creator)
      throws IOException {
    return creator.addMetadata(this, unnestPOP);
  }

  @Override
  public BatchSchema.SelectionVectorMode[] getSupportedEncodings() {
    return BatchSchema.SelectionVectorMode.DEFAULT;
  }

  @Override
  public BatchSchema.SelectionVectorMode getEncoding() {
    return BatchSchema.SelectionVectorMode.NONE;
  }

  @Override
  public boolean needsFinalColumnReordering() {
    return true;
  }

  public Class<?> getParentClass() {
    return LateralJoinPrel.class;
  }

  @Override
  public RelNode accept(RexShuttle shuttle) {
    RexNode ref = shuttle.apply(this.ref);
    if (this.ref == ref) {
      return this;
    }
    return new UnnestPrel(getCluster(), traitSet, rowType, ref);
  }

  @Override
  public Prel prepareForLateralUnnestPipeline(List<RelNode> children) {
    RelDataTypeFactory typeFactory = this.getCluster().getTypeFactory();
    List<String> fieldNames = new ArrayList<>();
    List<RelDataType> fieldTypes = new ArrayList<>();

    fieldNames.add(IMPLICIT_COLUMN);
    fieldTypes.add(typeFactory.createSqlType(SqlTypeName.INTEGER));

    for (RelDataTypeField field : this.rowType.getFieldList()) {
      fieldNames.add(field.getName());
      fieldTypes.add(field.getType());
    }

    RelDataType newRowType = typeFactory.createStructType(fieldTypes, fieldNames);
    return new UnnestPrel(this.getCluster(), this.getTraitSet(), newRowType, ref);
  }
}
