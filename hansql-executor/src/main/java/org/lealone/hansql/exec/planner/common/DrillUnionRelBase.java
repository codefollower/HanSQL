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
package org.lealone.hansql.exec.planner.common;

import java.util.List;

import org.lealone.hansql.optimizer.plan.RelOptCluster;
import org.lealone.hansql.optimizer.plan.RelTraitSet;
import org.lealone.hansql.optimizer.rel.InvalidRelException;
import org.lealone.hansql.optimizer.rel.RelNode;
import org.lealone.hansql.optimizer.rel.core.Union;
import org.lealone.hansql.optimizer.rel.type.RelDataType;

/**
 * Base class for logical and physical Union implemented in Drill
 */
public abstract class DrillUnionRelBase extends Union implements DrillRelNode {

  public DrillUnionRelBase(RelOptCluster cluster, RelTraitSet traits,
      List<RelNode> inputs, boolean all, boolean checkCompatibility) throws InvalidRelException {
    super(cluster, traits, inputs, all);
    if (checkCompatibility &&
        !this.isCompatible(false /* don't compare names */, true /* allow substrings */)) {
      throw new InvalidRelException("Input row types of the Union are not compatible.");
    }
  }

  public boolean isCompatible(boolean compareNames, boolean allowSubstring) {
    RelDataType unionType = getRowType();
    for (RelNode input : getInputs()) {
      if (! DrillRelOptUtil.areRowTypesCompatible(
          input.getRowType(), unionType, compareNames, allowSubstring)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean isDistinct() {
    return !this.all;
  }

}
