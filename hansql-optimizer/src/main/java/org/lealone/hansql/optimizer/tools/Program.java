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
package org.lealone.hansql.optimizer.tools;

import org.lealone.hansql.optimizer.plan.RelOptPlanner;
import org.lealone.hansql.optimizer.plan.RelTraitSet;
import org.lealone.hansql.optimizer.rel.RelNode;

/**
 * Program that transforms a relational expression into another relational
 * expression.
 *
 * <p>A planner is a sequence of programs, each of which is sometimes called
 * a "phase".
 * The most typical program is an invocation of the volcano planner with a
 * particular {@link org.lealone.hansql.optimizer.tools.RuleSet}.</p>
 */
public interface Program {
    RelNode run(RelOptPlanner planner, RelNode rel, RelTraitSet requiredOutputTraits);
}

// End Program.java
