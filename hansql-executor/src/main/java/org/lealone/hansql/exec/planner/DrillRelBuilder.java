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
package org.lealone.hansql.exec.planner;

import org.lealone.hansql.optimizer.plan.Context;
import org.lealone.hansql.optimizer.plan.Contexts;
import org.lealone.hansql.optimizer.plan.RelOptCluster;
import org.lealone.hansql.optimizer.plan.RelOptSchema;
import org.lealone.hansql.optimizer.rel.RelNode;
import org.lealone.hansql.optimizer.rel.core.RelFactories;
import org.lealone.hansql.optimizer.tools.RelBuilder;
import org.lealone.hansql.optimizer.tools.RelBuilderFactory;
import org.lealone.hansql.optimizer.util.Util;

public class DrillRelBuilder extends RelBuilder {
  private final RelFactories.FilterFactory filterFactory;

  protected DrillRelBuilder(Context context, RelOptCluster cluster, RelOptSchema relOptSchema) {
    super(context, cluster, relOptSchema);
    this.filterFactory =
        Util.first(context.unwrap(RelFactories.FilterFactory.class),
            RelFactories.DEFAULT_FILTER_FACTORY);
  }

  /**
   * Original method {@link RelBuilder#empty} returns empty values rel.
   * In the order to preserve data row types, filter with false predicate is created.
   */
  @Override
  public RelBuilder empty() {
    // pops the frame from the stack and returns its relational expression
    RelNode relNode = build();

    // creates filter with false in the predicate
    final RelNode filter = filterFactory.createFilter(relNode, cluster.getRexBuilder().makeLiteral(false));
    push(filter);

    return this;
  }

  /** Creates a {@link RelBuilderFactory}, a partially-created DrillRelBuilder.
   * Just add a {@link RelOptCluster} and a {@link RelOptSchema} */
  public static RelBuilderFactory proto(final Context context) {
    return new RelBuilderFactory() {
      public RelBuilder create(RelOptCluster cluster, RelOptSchema schema) {
        return new DrillRelBuilder(context, cluster, schema);
      }
    };
  }

  /** Creates a {@link RelBuilderFactory} that uses a given set of factories. */
  public static RelBuilderFactory proto(Object... factories) {
    return proto(Contexts.of(factories));
  }

  /**
   * Disables combining of consecutive {@link org.lealone.hansql.optimizer.rel.core.Project} nodes.
   * See comments under CALCITE-2470 for details.
   * @return false
   */
  @Override
  protected boolean shouldMergeProject() {
    return false;
  }
}
