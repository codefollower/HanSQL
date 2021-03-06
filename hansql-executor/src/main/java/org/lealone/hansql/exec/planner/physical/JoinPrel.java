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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.drill.shaded.guava.com.google.common.collect.Lists;
import org.lealone.hansql.common.exceptions.UserException;
import org.lealone.hansql.common.expression.FieldReference;
import org.lealone.hansql.common.logical.data.JoinCondition;
import org.lealone.hansql.exec.physical.impl.join.JoinUtils;
import org.lealone.hansql.exec.planner.common.DrillJoinRelBase;
import org.lealone.hansql.exec.planner.physical.visitor.PrelVisitor;
import org.lealone.hansql.optimizer.plan.RelOptCluster;
import org.lealone.hansql.optimizer.plan.RelOptUtil;
import org.lealone.hansql.optimizer.plan.RelTraitSet;
import org.lealone.hansql.optimizer.rel.RelNode;
import org.lealone.hansql.optimizer.rel.core.JoinRelType;
import org.lealone.hansql.optimizer.rel.type.RelDataType;
import org.lealone.hansql.optimizer.rel.type.RelDataTypeField;
import org.lealone.hansql.optimizer.rex.RexChecker;
import org.lealone.hansql.optimizer.rex.RexNode;
import org.lealone.hansql.optimizer.rex.RexUtil;
import org.lealone.hansql.optimizer.sql.SqlKind;
import org.lealone.hansql.optimizer.sql.type.SqlTypeName;
import org.lealone.hansql.optimizer.sql.validate.SqlValidatorUtil;
import org.lealone.hansql.optimizer.util.Litmus;
import org.lealone.hansql.optimizer.util.Pair;

/**
 *
 * Base class for MergeJoinPrel and HashJoinPrel
 *
 */
public abstract class JoinPrel extends DrillJoinRelBase implements Prel {
  private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(JoinPrel.class);

  protected final boolean isSemiJoin;
  protected JoinUtils.JoinCategory joincategory;

  public JoinPrel(RelOptCluster cluster, RelTraitSet traits, RelNode left, RelNode right, RexNode condition,
                  JoinRelType joinType) {
    this(cluster, traits, left, right, condition, joinType, false);
  }

  public JoinPrel(RelOptCluster cluster, RelTraitSet traits, RelNode left, RelNode right, RexNode condition,
      JoinRelType joinType, boolean isSemiJoin) {
    super(cluster, traits, left, right, condition, joinType);
    this.isSemiJoin = isSemiJoin;
  }

  @Override
  public <T, X, E extends Throwable> T accept(PrelVisitor<T, X, E> logicalVisitor, X value) throws E {
    return logicalVisitor.visitJoin(this, value);
  }

  @Override
  public Iterator<Prel> iterator() {
    return PrelUtil.iter(getLeft(), getRight());
  }

  /**
   * Check to make sure that the fields of the inputs are the same as the output field names.
   * If not, insert a project renaming them.
   */
  public RelNode getJoinInput(int offset, RelNode input) {
    assert uniqueFieldNames(input.getRowType());
    final List<String> fields = getRowType().getFieldNames();
    final List<String> inputFields = input.getRowType().getFieldNames();
    final List<String> outputFields;
    if (fields.size() > offset) {
      outputFields = fields.subList(offset, offset + inputFields.size());
    } else {
      outputFields = new ArrayList<>();
    }
    if (!outputFields.equals(inputFields)) {
      // Ensure that input field names are the same as output field names.
      // If there are duplicate field names on left and right, fields will get
      // lost.
      // In such case, we need insert a rename Project on top of the input.
      return rename(input, input.getRowType().getFieldList(), outputFields);
    } else {
      return input;
    }
  }

  private RelNode rename(RelNode input, List<RelDataTypeField> inputFields, List<String> outputFieldNames) {
    if (outputFieldNames.size() == 0) {
      return input;
    }
    List<RexNode> exprs = Lists.newArrayList();

    for (RelDataTypeField field : inputFields) {
      RexNode expr = input.getCluster().getRexBuilder().makeInputRef(field.getType(), field.getIndex());
      exprs.add(expr);
    }

    RelDataType rowType = RexUtil.createStructType(input.getCluster().getTypeFactory(),
        exprs, outputFieldNames, null);

    ProjectPrel proj = new ProjectPrel(input.getCluster(), input.getTraitSet(), input, exprs, rowType);

    return proj;
  }

  @Override
  public boolean needsFinalColumnReordering() {
    return true;
  }

  /**
   * Build the list of join conditions for this join.
   * A join condition is built only for equality and IS NOT DISTINCT FROM comparisons. The difference is:
   * null == null is FALSE whereas null IS NOT DISTINCT FROM null is TRUE
   * For a use case of the IS NOT DISTINCT FROM comparison, see
   * {@link org.lealone.hansql.optimizer.rel.rules.AggregateRemoveRule}
   * @param conditions populated list of join conditions
   * @param leftFields join fields from the left input
   * @param rightFields join fields from the right input
   */
  protected void buildJoinConditions(List<JoinCondition> conditions,
      List<String> leftFields,
      List<String> rightFields,
      List<Integer> leftKeys,
      List<Integer> rightKeys) {
    List<RexNode> conjuncts = RelOptUtil.conjunctions(this.getCondition());
    short i=0;

    for (Pair<Integer, Integer> pair : Pair.zip(leftKeys, rightKeys)) {
      final RexNode conditionExpr = conjuncts.get(i++);
      final SqlKind kind  = conditionExpr.getKind();
      if (kind != SqlKind.EQUALS && kind != SqlKind.IS_NOT_DISTINCT_FROM) {
        throw UserException.unsupportedError()
            .message("Unsupported comparator in join condition %s", conditionExpr)
            .build(logger);
      }

      conditions.add(new JoinCondition(kind.toString(),
          FieldReference.getWithQuotedRef(leftFields.get(pair.left)),
          FieldReference.getWithQuotedRef(rightFields.get(pair.right))));
    }
  }

  public boolean isSemiJoin() {
    return isSemiJoin;
  }

  /* A Drill physical rel which is semi join will have output row type with fields from only
     left side of the join. Calcite's join rel expects to have the output row type from
     left and right side of the join. This function is overloaded to not throw exceptions for
     a Drill semi join physical rel.
   */
  @Override public boolean isValid(Litmus litmus, Context context) {
    if (!this.isSemiJoin && !super.isValid(litmus, context)) {
      return false;
    }
    if (getRowType().getFieldCount()
            != getSystemFieldList().size()
            + left.getRowType().getFieldCount()
            + (this.isSemiJoin ? 0 : right.getRowType().getFieldCount())) {
      return litmus.fail("field count mismatch");
    }
    if (condition != null) {
      if (condition.getType().getSqlTypeName() != SqlTypeName.BOOLEAN) {
        return litmus.fail("condition must be boolean: {}",
                condition.getType());
      }
      // The input to the condition is a row type consisting of system
      // fields, left fields, and right fields. Very similar to the
      // output row type, except that fields have not yet been made due
      // due to outer joins.
      RexChecker checker =
              new RexChecker(
                      getCluster().getTypeFactory().builder()
                              .addAll(getSystemFieldList())
                              .addAll(getLeft().getRowType().getFieldList())
                              .addAll(getRight().getRowType().getFieldList())
                              .build(),
                      context, litmus);
      condition.accept(checker);
      if (checker.getFailureCount() > 0) {
        return litmus.fail(checker.getFailureCount()
                + " failures in condition " + condition);
      }
    }
    return litmus.succeed();
  }

  @Override public RelDataType deriveRowType() {
    if (isSemiJoin) {
      return SqlValidatorUtil.deriveJoinRowType(
              left.getRowType(),
              null,
              this.joinType,
              getCluster().getTypeFactory(),
              null,
              new ArrayList<>());
    } else {
      return super.deriveRowType();
    }
  }
}
