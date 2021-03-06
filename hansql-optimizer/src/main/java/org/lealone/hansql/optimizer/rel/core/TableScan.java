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
package org.lealone.hansql.optimizer.rel.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.lealone.hansql.optimizer.plan.RelOptCluster;
import org.lealone.hansql.optimizer.plan.RelOptCost;
import org.lealone.hansql.optimizer.plan.RelOptPlanner;
import org.lealone.hansql.optimizer.plan.RelOptTable;
import org.lealone.hansql.optimizer.plan.RelTraitSet;
import org.lealone.hansql.optimizer.rel.AbstractRelNode;
import org.lealone.hansql.optimizer.rel.RelCollation;
import org.lealone.hansql.optimizer.rel.RelInput;
import org.lealone.hansql.optimizer.rel.RelNode;
import org.lealone.hansql.optimizer.rel.RelShuttle;
import org.lealone.hansql.optimizer.rel.RelWriter;
import org.lealone.hansql.optimizer.rel.metadata.RelMetadataQuery;
import org.lealone.hansql.optimizer.rel.type.RelDataType;
import org.lealone.hansql.optimizer.rel.type.RelDataTypeField;
import org.lealone.hansql.optimizer.rex.RexBuilder;
import org.lealone.hansql.optimizer.rex.RexNode;
import org.lealone.hansql.optimizer.tools.RelBuilder;
import org.lealone.hansql.optimizer.util.ImmutableBitSet;
import org.lealone.hansql.optimizer.util.ImmutableIntList;

/**
 * Relational operator that returns the contents of a table.
 */
public abstract class TableScan extends AbstractRelNode {
  //~ Instance fields --------------------------------------------------------

  /**
   * The table definition.
   */
  protected final RelOptTable table;

  //~ Constructors -----------------------------------------------------------

  protected TableScan(RelOptCluster cluster, RelTraitSet traitSet,
      RelOptTable table) {
    super(cluster, traitSet);
    this.table = table;
    if (table.getRelOptSchema() != null) {
      cluster.getPlanner().registerSchema(table.getRelOptSchema());
    }
  }

  /**
   * Creates a TableScan by parsing serialized output.
   */
  protected TableScan(RelInput input) {
    this(input.getCluster(), input.getTraitSet(), input.getTable("table"));
  }

  //~ Methods ----------------------------------------------------------------

  @Override public double estimateRowCount(RelMetadataQuery mq) {
    return table.getRowCount();
  }

  @Override public RelOptTable getTable() {
    return table;
  }

  @SuppressWarnings("deprecation")
  @Override public List<RelCollation> getCollationList() {
    return table.getCollationList();
  }

  @Override public RelOptCost computeSelfCost(RelOptPlanner planner,
      RelMetadataQuery mq) {
    double dRows = table.getRowCount();
    double dCpu = dRows + 1; // ensure non-zero cost
    double dIo = 0;
    return planner.getCostFactory().makeCost(dRows, dCpu, dIo);
  }

  @Override public RelDataType deriveRowType() {
    return table.getRowType();
  }

  /** Returns an identity projection for the given table. */
  public static ImmutableIntList identity(RelOptTable table) {
    return ImmutableIntList.identity(table.getRowType().getFieldCount());
  }

  /** Returns an identity projection. */
  public ImmutableIntList identity() {
    return identity(table);
  }

  @Override public RelWriter explainTerms(RelWriter pw) {
    return super.explainTerms(pw)
        .item("table", table.getQualifiedName());
  }

  /**
   * Projects a subset of the fields of the table, and also asks for "extra"
   * fields that were not included in the table's official type.
   *
   * <p>The default implementation assumes that tables cannot do either of
   * these operations, therefore it adds a {@link Project} that projects
   * {@code NULL} values for the extra fields, using the
   * {@link RelBuilder#project(Iterable)} method.
   *
   * <p>Sub-classes, representing table types that have these capabilities,
   * should override.</p>
   *
   * @param fieldsUsed  Bitmap of the fields desired by the consumer
   * @param extraFields Extra fields, not advertised in the table's row-type,
   *                    wanted by the consumer
   * @param relBuilder Builder used to create a Project
   * @return Relational expression that projects the desired fields
   */
  public RelNode project(ImmutableBitSet fieldsUsed,
      Set<RelDataTypeField> extraFields,
      RelBuilder relBuilder) {
    final int fieldCount = getRowType().getFieldCount();
    if (fieldsUsed.equals(ImmutableBitSet.range(fieldCount))
        && extraFields.isEmpty()) {
      return this;
    }
    final List<RexNode> exprList = new ArrayList<>();
    final List<String> nameList = new ArrayList<>();
    final RexBuilder rexBuilder = getCluster().getRexBuilder();
    final List<RelDataTypeField> fields = getRowType().getFieldList();

    // Project the subset of fields.
    for (int i : fieldsUsed) {
      RelDataTypeField field = fields.get(i);
      exprList.add(rexBuilder.makeInputRef(this, i));
      nameList.add(field.getName());
    }

    // Project nulls for the extra fields. (Maybe a sub-class table has
    // extra fields, but we don't.)
    for (RelDataTypeField extraField : extraFields) {
      exprList.add(
          rexBuilder.ensureType(
              extraField.getType(),
              rexBuilder.constantNull(),
              true));
      nameList.add(extraField.getName());
    }

    return relBuilder.push(this).project(exprList, nameList).build();
  }

  @Override public RelNode accept(RelShuttle shuttle) {
    return shuttle.visit(this);
  }
}

// End TableScan.java
