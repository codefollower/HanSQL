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

import org.lealone.hansql.exec.planner.DrillRelBuilder;
import org.lealone.hansql.optimizer.plan.Contexts;
import org.lealone.hansql.optimizer.plan.ConventionTraitDef;
import org.lealone.hansql.optimizer.plan.RelOptRuleCall;
import org.lealone.hansql.optimizer.rel.core.Aggregate;
import org.lealone.hansql.optimizer.rel.core.Filter;
import org.lealone.hansql.optimizer.rel.core.RelFactories;
import org.lealone.hansql.optimizer.rel.rules.FilterAggregateTransposeRule;
import org.lealone.hansql.optimizer.tools.RelBuilderFactory;

public class DrillFilterAggregateTransposeRule extends FilterAggregateTransposeRule{

  // Since Calcite's default FilterAggregateTransposeRule would match Filter on top of Aggregate, it potentially will match Rels with mixed CONVENTION trait.
  // Here override match method, such that the rule matches with Rel in the same CONVENTION.

  public static final FilterAggregateTransposeRule INSTANCE = new DrillFilterAggregateTransposeRule(
      DrillRelBuilder.proto(Contexts.of(RelFactories.DEFAULT_FILTER_FACTORY)));

  public static final FilterAggregateTransposeRule DRILL_LOGICAL_INSTANCE = new DrillFilterAggregateTransposeRule(
      DrillRelBuilder.proto(DrillRelFactories.DRILL_LOGICAL_FILTER_FACTORY, DrillRelFactories.DRILL_LOGICAL_AGGREGATE_FACTORY));

  private DrillFilterAggregateTransposeRule(RelBuilderFactory relBuilderFactory) {
    super(Filter.class, relBuilderFactory, Aggregate.class);
  }

  @Override
  public boolean matches(RelOptRuleCall call) {
    final Filter filter = call.rel(0);
    final Aggregate aggregate = call.rel(1);
    return filter.getTraitSet().getTrait(ConventionTraitDef.INSTANCE)
        == aggregate.getTraitSet().getTrait(ConventionTraitDef.INSTANCE);
  }

}
