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
package org.lealone.hansql.exec.physical.config;

import java.util.List;

import org.lealone.hansql.common.expression.LogicalExpression;
import org.lealone.hansql.exec.physical.MinorFragmentEndpoint;
import org.lealone.hansql.exec.physical.base.AbstractSender;
import org.lealone.hansql.exec.physical.base.PhysicalOperator;
import org.lealone.hansql.exec.physical.base.PhysicalVisitor;
import org.lealone.hansql.exec.physical.impl.partitionsender.Partitioner;
import org.lealone.hansql.exec.proto.UserBitShared.CoreOperatorType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("hash-partition-sender")
public class HashPartitionSender extends AbstractSender {
  static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(HashPartitionSender.class);

  private final LogicalExpression expr;
  private final int outgoingBatchSize;

  @JsonCreator
  public HashPartitionSender(@JsonProperty("receiver-major-fragment") int oppositeMajorFragmentId,
                             @JsonProperty("child") PhysicalOperator child,
                             @JsonProperty("expr") LogicalExpression expr,
                             @JsonProperty("destinations") List<MinorFragmentEndpoint> endpoints,
                             @JsonProperty("outgoingBatchSize") int outgoingBatchSize) {
    super(oppositeMajorFragmentId, child, endpoints);
    this.expr = expr;
    this.outgoingBatchSize = outgoingBatchSize;
  }

  public HashPartitionSender(int oppositeMajorFragmentId,
                             PhysicalOperator child,
                             LogicalExpression expr,
                             List<MinorFragmentEndpoint> endpoints) {
    this(oppositeMajorFragmentId, child, expr, endpoints, Partitioner.DEFAULT_RECORD_BATCH_SIZE);
  }

  @Override
  protected PhysicalOperator getNewWithChild(PhysicalOperator child) {
    return new HashPartitionSender(oppositeMajorFragmentId, child, expr,
        destinations, outgoingBatchSize);
  }

  public LogicalExpression getExpr() {
    return expr;
  }

  public int getOutgoingBatchSize() {
    return outgoingBatchSize;
  }

  @Override
  public <T, X, E extends Throwable> T accept(PhysicalVisitor<T, X, E> physicalVisitor, X value) throws E {
    return physicalVisitor.visitHashPartitionSender(this, value);
  }

  @Override
  public int getOperatorType() {
    return CoreOperatorType.HASH_PARTITION_SENDER_VALUE;
  }

}
