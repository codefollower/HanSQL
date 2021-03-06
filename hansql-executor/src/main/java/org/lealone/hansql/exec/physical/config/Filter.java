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

import org.lealone.hansql.common.expression.LogicalExpression;
import org.lealone.hansql.exec.physical.base.AbstractSingle;
import org.lealone.hansql.exec.physical.base.PhysicalOperator;
import org.lealone.hansql.exec.physical.base.PhysicalVisitor;
import org.lealone.hansql.exec.proto.UserBitShared.CoreOperatorType;
import org.lealone.hansql.exec.record.BatchSchema.SelectionVectorMode;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("filter")
public class Filter extends AbstractSingle {

  static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Filter.class);

  private final LogicalExpression expr;
  private final float selectivity;

  @JsonCreator
  public Filter(@JsonProperty("child") PhysicalOperator child, @JsonProperty("expr") LogicalExpression expr, @JsonProperty("selectivity") float selectivity) {
    super(child);
    this.expr = expr;
    this.selectivity = selectivity;
  }

  public LogicalExpression getExpr() {
    return expr;
  }

  @Override
  public <T, X, E extends Throwable> T accept(PhysicalVisitor<T, X, E> physicalVisitor, X value) throws E{
    return physicalVisitor.visitFilter(this, value);
  }

  @Override
  protected PhysicalOperator getNewWithChild(PhysicalOperator child) {
    return new Filter(child, expr, selectivity);
  }

  @Override
  public SelectionVectorMode getSVMode() {
    if (child.getSVMode().equals(SelectionVectorMode.FOUR_BYTE)) {
      return SelectionVectorMode.FOUR_BYTE;
    } else {
      return SelectionVectorMode.TWO_BYTE;
    }
  }

  @Override
  public int getOperatorType() {
    return CoreOperatorType.FILTER_VALUE;
  }


}
