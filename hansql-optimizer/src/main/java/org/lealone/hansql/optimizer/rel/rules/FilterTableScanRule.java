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
import org.lealone.hansql.optimizer.plan.RelOptTable;
import org.lealone.hansql.optimizer.rel.core.Filter;
import org.lealone.hansql.optimizer.rel.core.RelFactories;
import org.lealone.hansql.optimizer.rel.core.TableScan;
import org.lealone.hansql.optimizer.rex.RexNode;
import org.lealone.hansql.optimizer.rex.RexUtil;
import org.lealone.hansql.optimizer.schema.FilterableTable;
import org.lealone.hansql.optimizer.tools.RelBuilderFactory;
import org.lealone.hansql.optimizer.util.ImmutableIntList;
import org.lealone.hansql.optimizer.util.mapping.Mapping;
import org.lealone.hansql.optimizer.util.mapping.Mappings;

import com.google.common.collect.ImmutableList;

/**
 * Planner rule that converts
 * a {@link org.lealone.hansql.optimizer.rel.core.Filter}
 * on a {@link org.lealone.hansql.optimizer.rel.core.TableScan}
 * of a {@link org.lealone.hansql.optimizer.schema.FilterableTable}
 * or a {@link org.apache.calcite.schema.ProjectableFilterableTable}
 * to a {@link org.apache.calcite.interpreter.Bindables.BindableTableScan}.
 *
 * <p>The {@link #INTERPRETER} variant allows an intervening
 * {@link org.apache.calcite.adapter.enumerable.EnumerableInterpreter}.
 *
 * @see org.apache.calcite.rel.rules.ProjectTableScanRule
 */
public abstract class FilterTableScanRule extends RelOptRule {
  @SuppressWarnings("Guava")
  @Deprecated // to be removed before 2.0
  public static final com.google.common.base.Predicate<TableScan> PREDICATE =
      FilterTableScanRule::test;

//  /** Rule that matches Filter on TableScan. */
//  public static final FilterTableScanRule INSTANCE =
//      new FilterTableScanRule(
//          operand(Filter.class,
//              operandJ(TableScan.class, null, FilterTableScanRule::test,
//                  none())),
//          RelFactories.LOGICAL_BUILDER,
//          "FilterTableScanRule") {
//        public void onMatch(RelOptRuleCall call) {
//          final Filter filter = call.rel(0);
//          final TableScan scan = call.rel(1);
//          apply(call, filter, scan);
//        }
//      };
//
//  /** Rule that matches Filter on EnumerableInterpreter on TableScan. */
//  public static final FilterTableScanRule INTERPRETER =
//      new FilterTableScanRule(
//          operand(Filter.class,
//              operand(EnumerableInterpreter.class,
//                  operandJ(TableScan.class, null, FilterTableScanRule::test,
//                      none()))),
//          RelFactories.LOGICAL_BUILDER,
//          "FilterTableScanRule:interpreter") {
//        public void onMatch(RelOptRuleCall call) {
//          final Filter filter = call.rel(0);
//          final TableScan scan = call.rel(2);
//          apply(call, filter, scan);
//        }
//      };

  //~ Constructors -----------------------------------------------------------

  @Deprecated // to be removed before 2.0
  protected FilterTableScanRule(RelOptRuleOperand operand, String description) {
    this(operand, RelFactories.LOGICAL_BUILDER, description);
  }

  /** Creates a FilterTableScanRule. */
  protected FilterTableScanRule(RelOptRuleOperand operand,
      RelBuilderFactory relBuilderFactory, String description) {
    super(operand, relBuilderFactory, description);
  }

  //~ Methods ----------------------------------------------------------------

  public static boolean test(TableScan scan) {
    // We can only push filters into a FilterableTable or
    // ProjectableFilterableTable.
    final RelOptTable table = scan.getTable();
    return table.unwrap(FilterableTable.class) != null;
  }

//  protected void apply(RelOptRuleCall call, Filter filter, TableScan scan) {
//    final ImmutableIntList projects;
//    final ImmutableList.Builder<RexNode> filters = ImmutableList.builder();
//    if (scan instanceof Bindables.BindableTableScan) {
//      final Bindables.BindableTableScan bindableScan =
//          (Bindables.BindableTableScan) scan;
//      filters.addAll(bindableScan.filters);
//      projects = bindableScan.projects;
//    } else {
//      projects = scan.identity();
//    }
//
//    final Mapping mapping = Mappings.target(projects,
//        scan.getTable().getRowType().getFieldCount());
//    filters.add(
//        RexUtil.apply(mapping, filter.getCondition()));
//
//    call.transformTo(
//        Bindables.BindableTableScan.create(scan.getCluster(), scan.getTable(),
//            filters.build(), projects));
//  }
}

// End FilterTableScanRule.java
