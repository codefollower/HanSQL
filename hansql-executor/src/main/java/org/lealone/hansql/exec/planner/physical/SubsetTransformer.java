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

import org.apache.drill.shaded.guava.com.google.common.collect.Sets;
import org.lealone.hansql.optimizer.plan.ConventionTraitDef;
import org.lealone.hansql.optimizer.plan.RelOptRule;
import org.lealone.hansql.optimizer.plan.RelOptRuleCall;
import org.lealone.hansql.optimizer.plan.RelTrait;
import org.lealone.hansql.optimizer.plan.RelTraitSet;
import org.lealone.hansql.optimizer.plan.volcano.RelSubset;
import org.lealone.hansql.optimizer.rel.RelNode;

import java.util.Set;

public abstract class SubsetTransformer<T extends RelNode, E extends Exception> {
  static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SubsetTransformer.class);

  public abstract RelNode convertChild(T current, RelNode child) throws E;

  public boolean forceConvert() {
    return false;
  }

  private final RelOptRuleCall call;

  public SubsetTransformer(RelOptRuleCall call) {
    this.call = call;
  }

  public RelTraitSet newTraitSet(RelTrait... traits) {
    RelTraitSet set = call.getPlanner().emptyTraitSet();
    for (RelTrait t : traits) {
      set = set.plus(t);
    }
    return set;

  }

  public boolean go(T n, RelNode candidateSet) throws E {
    if ( !(candidateSet instanceof RelSubset) ) {
      return false;
    }

    boolean transform = false;
    Set<RelNode> transformedRels = Sets.newIdentityHashSet();
    Set<RelTraitSet> traitSets = Sets.newHashSet();

    //1, get all the target traitsets from candidateSet's rel list,
    for (RelNode rel : ((RelSubset)candidateSet).getRelList()) {
      if (isPhysical(rel)) {
        final RelTraitSet relTraitSet = rel.getTraitSet();
        if ( !traitSets.contains(relTraitSet) ) {
          traitSets.add(relTraitSet);
          logger.trace("{}.convertChild get traitSet {}", this.getClass().getSimpleName(), relTraitSet);
        }
      }
    }

    //2, convert the candidateSet to targeted taitSets
    if (traitSets.size() == 0 && forceConvert()) {
      RelNode out = convertChild(n, null);
      if (out != null) {
        call.transformTo(out);
        return true;
      }
      return false;
    }

    for (RelTraitSet traitSet: traitSets) {
      RelNode newRel = RelOptRule.convert(candidateSet, traitSet.simplify());
      if(transformedRels.contains(newRel)) {
        continue;
      }
      transformedRels.add(newRel);

      logger.trace("{}.convertChild to convert NODE {} ,AND {}", this.getClass().getSimpleName(), n, newRel);
      RelNode out = convertChild(n, newRel);

      //RelNode out = convertChild(n, rel);
      if (out != null) {
        call.transformTo(out);
        transform = true;
      }
    }

    return transform;
  }

  private boolean isPhysical(RelNode n){
    return n.getTraitSet().getTrait(ConventionTraitDef.INSTANCE).equals(Prel.DRILL_PHYSICAL);
  }

  private boolean isDefaultDist(RelNode n) {
    return n.getTraitSet().getTrait(DrillDistributionTraitDef.INSTANCE).equals(DrillDistributionTrait.DEFAULT);
  }

}
