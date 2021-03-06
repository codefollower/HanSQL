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

import java.util.List;
import java.util.stream.Collectors;

import org.lealone.hansql.optimizer.plan.RelOptPredicateList;
import org.lealone.hansql.optimizer.plan.RelOptRule;
import org.lealone.hansql.optimizer.plan.RelOptRuleCall;
import org.lealone.hansql.optimizer.rel.RelCollationTraitDef;
import org.lealone.hansql.optimizer.rel.RelCollations;
import org.lealone.hansql.optimizer.rel.RelFieldCollation;
import org.lealone.hansql.optimizer.rel.RelNode;
import org.lealone.hansql.optimizer.rel.core.RelFactories;
import org.lealone.hansql.optimizer.rel.core.Sort;
import org.lealone.hansql.optimizer.rel.metadata.RelMetadataQuery;
import org.lealone.hansql.optimizer.rex.RexBuilder;

/**
 * Planner rule that removes keys from a
 * a {@link org.lealone.hansql.optimizer.rel.core.Sort} if those keys are known to be
 * constant, or removes the entire Sort if all keys are constant.
 *
 * <p>Requires {@link RelCollationTraitDef}.
 */
public class SortRemoveConstantKeysRule extends RelOptRule {
  public static final SortRemoveConstantKeysRule INSTANCE =
      new SortRemoveConstantKeysRule();

  private SortRemoveConstantKeysRule() {
    super(
        operand(Sort.class, any()),
        RelFactories.LOGICAL_BUILDER, "SortRemoveConstantKeysRule");
  }

  @Override public void onMatch(RelOptRuleCall call) {
    final Sort sort = call.rel(0);
    final RelMetadataQuery mq = call.getMetadataQuery();
    final RelNode input = sort.getInput();
    final RelOptPredicateList predicates = mq.getPulledUpPredicates(input);
    if (predicates == null) {
      return;
    }

    final RexBuilder rexBuilder = sort.getCluster().getRexBuilder();
    final List<RelFieldCollation> collationsList =
        sort.getCollation().getFieldCollations().stream()
            .filter(fc ->
                !predicates.constantMap.containsKey(
                    rexBuilder.makeInputRef(input, fc.getFieldIndex())))
            .collect(Collectors.toList());

    if (collationsList.size() == sort.collation.getFieldCollations().size()) {
      return;
    }

    // No active collations. Remove the sort completely
    if (collationsList.isEmpty() && sort.offset == null && sort.fetch == null) {
      call.transformTo(input);
      call.getPlanner().setImportance(sort, 0.0);
      return;
    }

    final Sort result =
        sort.copy(sort.getTraitSet(), input, RelCollations.of(collationsList));
    call.transformTo(result);
    call.getPlanner().setImportance(sort, 0.0);
  }
}

// End SortRemoveConstantKeysRule.java
