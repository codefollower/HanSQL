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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.lealone.hansql.exec.memory.BufferAllocator;
import org.lealone.hansql.exec.proto.CoordinationProtos.DrillbitEndpoint;
import org.lealone.hansql.exec.proto.UserBitShared.MinorFragmentProfile;

/**
 * Holds statistics of a particular (minor) fragment.
 */
public class FragmentStats {
//  private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(FragmentStats.class);

  private Map<ImmutablePair<Integer, Integer>, OperatorStats> operators = new LinkedHashMap<>();
  private final long startTime;
  private final DrillbitEndpoint endpoint;
  private final BufferAllocator allocator;

  public FragmentStats(BufferAllocator allocator, DrillbitEndpoint endpoint) {
    this.startTime = System.currentTimeMillis();
    this.endpoint = endpoint;
    this.allocator = allocator;
  }

  public void addMetricsToStatus(MinorFragmentProfile.Builder prfB) {
    prfB.setStartTime(startTime);
    prfB.setMaxMemoryUsed(allocator.getPeakMemoryAllocation());
    prfB.setEndTime(System.currentTimeMillis());
    prfB.setEndpoint(endpoint);
    for(Entry<ImmutablePair<Integer, Integer>, OperatorStats> o : operators.entrySet()){
      prfB.addOperatorProfile(o.getValue().getProfile());
    }
  }

  /**
   * Creates a new holder for operator statistics within this holder for fragment statistics.
   *
   * @param profileDef operator profile definition
   * @param allocator the allocator being used
   * @return a new operator statistics holder
   */
  public OperatorStats newOperatorStats(final OpProfileDef profileDef, final BufferAllocator allocator) {
    final OperatorStats stats = new OperatorStats(profileDef, allocator);
    if(profileDef.operatorType != -1) {
      @SuppressWarnings("unused")
      OperatorStats existingStatsHolder = addOperatorStats(stats);
    }
    return stats;
  }

  public OperatorStats addOperatorStats(OperatorStats stats) {
    return operators.put(new ImmutablePair<>(stats.operatorId, stats.operatorType), stats);
  }

}
