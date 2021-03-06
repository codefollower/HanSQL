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

import java.util.Iterator;
import java.util.List;

import org.lealone.hansql.exec.planner.common.DrillUnionRelBase;
import org.lealone.hansql.exec.planner.physical.visitor.PrelVisitor;
import org.lealone.hansql.optimizer.plan.RelOptCluster;
import org.lealone.hansql.optimizer.plan.RelTraitSet;
import org.lealone.hansql.optimizer.rel.InvalidRelException;
import org.lealone.hansql.optimizer.rel.RelNode;

public abstract class UnionPrel extends DrillUnionRelBase implements Prel{

  public UnionPrel(RelOptCluster cluster, RelTraitSet traits, List<RelNode> inputs, boolean all,
      boolean checkCompatibility) throws InvalidRelException {
    super(cluster, traits, inputs, all, checkCompatibility);
  }

  @Override
  public <T, X, E extends Throwable> T accept(PrelVisitor<T, X, E> logicalVisitor, X value) throws E {
    return logicalVisitor.visitPrel(this, value);
  }

  @Override
  public Iterator<Prel> iterator() {
    return PrelUtil.iter(this.getInputs());
  }

  @Override
  public boolean needsFinalColumnReordering() {
    return false;
  }

}
