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

import org.apache.drill.shaded.guava.com.google.common.collect.Lists;
import org.lealone.hansql.common.expression.ExpressionPosition;
import org.lealone.hansql.common.expression.FieldReference;
import org.lealone.hansql.common.expression.FunctionCall;
import org.lealone.hansql.common.expression.LogicalExpression;
import org.lealone.hansql.common.expression.ValueExpressions;
import org.lealone.hansql.common.logical.data.NamedExpression;
import org.lealone.hansql.common.logical.data.Order;
import org.lealone.hansql.exec.physical.base.PhysicalOperator;
import org.lealone.hansql.exec.physical.config.WindowPOP;
import org.lealone.hansql.exec.planner.common.DrillWindowRelBase;
import org.lealone.hansql.exec.planner.logical.DrillOptiq;
import org.lealone.hansql.exec.planner.logical.DrillParseContext;
import org.lealone.hansql.exec.planner.physical.visitor.PrelVisitor;
import org.lealone.hansql.exec.record.BatchSchema;
import org.lealone.hansql.optimizer.plan.RelOptCluster;
import org.lealone.hansql.optimizer.plan.RelTraitSet;
import org.lealone.hansql.optimizer.rel.RelFieldCollation;
import org.lealone.hansql.optimizer.rel.RelNode;
import org.lealone.hansql.optimizer.rel.core.AggregateCall;
import org.lealone.hansql.optimizer.rel.type.RelDataType;
import org.lealone.hansql.optimizer.rel.type.RelDataTypeField;
import org.lealone.hansql.optimizer.rex.RexLiteral;
import org.lealone.hansql.optimizer.util.BitSets;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static org.apache.drill.shaded.guava.com.google.common.base.Preconditions.checkState;

public class WindowPrel extends DrillWindowRelBase implements Prel {
  public WindowPrel(RelOptCluster cluster,
                    RelTraitSet traits,
                    RelNode child,
                    List<RexLiteral> constants,
                    RelDataType rowType,
                    Group window) {
    super(cluster, traits, child, constants, rowType, Collections.singletonList(window));
  }

  @Override
  public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) {
    final RelDataType copiedRowType = deriveCopiedRowTypeFromInput(sole(inputs));
    return new WindowPrel(getCluster(), traitSet, sole(inputs), constants, copiedRowType, groups.get(0));
  }

  @Override
  public PhysicalOperator getPhysicalOperator(PhysicalPlanCreator creator) throws IOException {
    Prel child = (Prel) this.getInput();

    PhysicalOperator childPOP = child.getPhysicalOperator(creator);

    final List<String> childFields = getInput().getRowType().getFieldNames();

    // We don't support distinct partitions
    checkState(groups.size() == 1, "Only one window is expected in WindowPrel");

    Group window = groups.get(0);
    List<NamedExpression> withins = Lists.newArrayList();
    List<NamedExpression> aggs = Lists.newArrayList();
    List<Order.Ordering> orderings = Lists.newArrayList();

    for (int group : BitSets.toIter(window.keys)) {
      FieldReference fr = new FieldReference(childFields.get(group), ExpressionPosition.UNKNOWN);
      withins.add(new NamedExpression(fr, fr));
    }

    for (AggregateCall aggCall : window.getAggregateCalls(this)) {
      FieldReference ref = new FieldReference(aggCall.getName());
      LogicalExpression expr = toDrill(aggCall, childFields);
      aggs.add(new NamedExpression(expr, ref));
    }

    for (RelFieldCollation fieldCollation : window.orderKeys.getFieldCollations()) {
      orderings.add(new Order.Ordering(fieldCollation.getDirection(), new FieldReference(childFields.get(fieldCollation.getFieldIndex())), fieldCollation.nullDirection));
    }

    WindowPOP windowPOP = new WindowPOP(
        childPOP,
        withins,
        aggs,
        orderings,
        window.isRows,
        WindowPOP.newBound(window.lowerBound),
        WindowPOP.newBound(window.upperBound));

    creator.addMetadata(this, windowPOP);
    return windowPOP;
  }

  protected LogicalExpression toDrill(AggregateCall call, List<String> fn) {
    DrillParseContext context = new DrillParseContext(PrelUtil.getSettings(getCluster()));

    List<LogicalExpression> args = Lists.newArrayList();
    for (Integer i : call.getArgList()) {
      final int indexInConstants = i - fn.size();
      if (i < fn.size()) {
        args.add(new FieldReference(fn.get(i)));
      } else {
        final RexLiteral constant = constants.get(indexInConstants);
        LogicalExpression expr = DrillOptiq.toDrill(context, getInput(), constant);
        args.add(expr);
      }
    }

    // for count(1).
    if (args.isEmpty()) {
      args.add(new ValueExpressions.LongExpression(1l));
    }

    return new FunctionCall(call.getAggregation().getName().toLowerCase(), args, ExpressionPosition.UNKNOWN);
  }

  @Override
  public <T, X, E extends Throwable> T accept(PrelVisitor<T, X, E> logicalVisitor, X value) throws E {
    return logicalVisitor.visitPrel(this, value);
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
    return false;
  }

  @Override
  public Iterator<Prel> iterator() {
    return PrelUtil.iter(getInput());
  }

  /**
   * Derive rowType for the copied WindowPrel based on input.
   * When copy() is called, the input might be different from the current one's input.
   * We have to use the new input's field in the copied WindowPrel.
   */
  private RelDataType deriveCopiedRowTypeFromInput(final RelNode input) {
    final RelDataType inputRowType = input.getRowType();
    final RelDataType windowRowType = this.getRowType();

    final List<RelDataTypeField> fieldList = new ArrayList<>(inputRowType.getFieldList());
    final int inputFieldCount = inputRowType.getFieldCount();
    final int windowFieldCount = windowRowType.getFieldCount();

    for (int i = inputFieldCount; i < windowFieldCount; i++) {
      fieldList.add(windowRowType.getFieldList().get(i));
    }

    final RelDataType rowType = this.getCluster().getRexBuilder().getTypeFactory().createStructType(fieldList);

    return rowType;
  }

}
