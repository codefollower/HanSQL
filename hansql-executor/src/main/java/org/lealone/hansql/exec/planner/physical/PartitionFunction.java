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

import java.util.List;

import org.lealone.hansql.common.expression.FieldReference;
import org.lealone.hansql.exec.record.VectorWrapper;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use=JsonTypeInfo.Id.CLASS, include=JsonTypeInfo.As.PROPERTY, property="@class")
public interface PartitionFunction  {

  /**
   * Return the list of FieldReferences that participate in the partitioning function
   * @return list of FieldReferences
   */
  List<FieldReference> getPartitionRefList();

  /**
   * Setup method for the partitioning function
   * @param partitionKeys a list of partition columns on which range partitioning is needed
   */
  void setup(List<VectorWrapper<?>> partitionKeys);

  /**
   * Evaluate a partitioning function for a particular row index and return the partition id
   * @param index the integer index into the partition keys vector for a specific 'row' of values
   * @param numPartitions the max number of partitions that are allowed
   * @return partition id, an integer value
   */
  int eval(int index, int numPartitions);

  /**
   * Returns a FieldReference (LogicalExpression) for the partition function
   * @return FieldReference for the partition function
   */
  FieldReference getPartitionFieldRef();

}
