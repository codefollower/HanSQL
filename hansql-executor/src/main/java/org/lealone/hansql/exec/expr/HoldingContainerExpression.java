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
package org.lealone.hansql.exec.expr;

import java.util.Collections;
import java.util.Iterator;

import org.lealone.hansql.common.expression.ExpressionPosition;
import org.lealone.hansql.common.expression.LogicalExpression;
import org.lealone.hansql.common.expression.visitors.ExprVisitor;
import org.lealone.hansql.common.types.TypeProtos.MajorType;
import org.lealone.hansql.exec.expr.ClassGenerator.HoldingContainer;

public class HoldingContainerExpression implements LogicalExpression{
  static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(HoldingContainerExpression.class);

  final HoldingContainer container;

  public HoldingContainerExpression(HoldingContainer container) {
    this.container = container;
  }

  @Override
  public Iterator<LogicalExpression> iterator() {
    return Collections.emptyIterator();
  }

  @Override
  public MajorType getMajorType() {
    return container.getMajorType();
  }

  @Override
  public <T, V, E extends Exception> T accept(ExprVisitor<T, V, E> visitor, V value) throws E {
    return visitor.visitUnknown(this, value);
  }


  public HoldingContainer getContainer() {
    return container;
  }

  @Override
  public ExpressionPosition getPosition() {
    return ExpressionPosition.UNKNOWN;
  }

  @Override
  public int getSelfCost() {
    return 0;  // TODO
  }

  @Override
  public int getCumulativeCost() {
    return 0; // TODO
  }

}
