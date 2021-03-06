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
package org.lealone.hansql.exec.ops;

import java.util.Collection;

import org.lealone.hansql.exec.context.QueryProfileStoreContext;
import org.lealone.hansql.exec.coord.ClusterCoordinator;
import org.lealone.hansql.exec.memory.BufferAllocator;
import org.lealone.hansql.exec.physical.impl.OperatorCreatorRegistry;
import org.lealone.hansql.exec.planner.PhysicalPlanReader;
import org.lealone.hansql.exec.proto.CoordinationProtos;

/**
 * This interface represents the context that is used by a Drillbit in classes like the
 * {@link org.lealone.hansql.exec.FragmentExecutor}.
 */
public interface ExecutorFragmentContext extends RootFragmentContext {
    /**
     * Returns the root allocator for the Drillbit.
     * @return The root allocator for the Drillbit.
     */
    BufferAllocator getRootAllocator();

    PhysicalPlanReader getPlanReader();

    ClusterCoordinator getClusterCoordinator();

    CoordinationProtos.DrillbitEndpoint getForemanEndpoint();

    CoordinationProtos.DrillbitEndpoint getEndpoint();

    Collection<CoordinationProtos.DrillbitEndpoint> getBits();

    OperatorCreatorRegistry getOperatorCreatorRegistry();

    QueryProfileStoreContext getProfileStoreContext();

    boolean isUserAuthenticationEnabled();
}
