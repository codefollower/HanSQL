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

import org.apache.drill.shaded.guava.com.google.common.collect.ImmutableSet;
import org.lealone.hansql.common.expression.ExpressionPosition;
import org.lealone.hansql.common.expression.LogicalExpression;
import org.lealone.hansql.common.expression.PathSegment;
import org.lealone.hansql.common.expression.visitors.ExprVisitor;
import org.lealone.hansql.common.types.TypeProtos.MajorType;
import org.lealone.hansql.exec.record.TypedFieldId;

import java.util.Iterator;

/**
 * Wraps a value vector field to be read, providing metadata about the field.
 * Also may contain batch naming information to which this field belongs.
 * If such information is absent default namings will be used from mapping set during materialization.
 */
public class ValueVectorReadExpression implements LogicalExpression {
  static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ValueVectorReadExpression.class);

  private final TypedFieldId fieldId;
  private final BatchReference batchRef;

  public ValueVectorReadExpression(TypedFieldId tfId){
    this(tfId, null);
  }

  public ValueVectorReadExpression(TypedFieldId tfId, BatchReference batchRef){
    this.fieldId = tfId;
    this.batchRef = batchRef;
  }

  public BatchReference getBatchRef() {
    return batchRef;
  }

  public boolean hasReadPath(){
    return fieldId.hasRemainder();
  }

  public PathSegment getReadPath(){
    return fieldId.getRemainder();
  }

  public TypedFieldId getTypedFieldId(){
    return fieldId;
  }

  public boolean isSuperReader(){
    return fieldId.isHyperReader();
  }
  @Override
  public MajorType getMajorType() {
    return fieldId.getFinalType();
  }

  @Override
  public <T, V, E extends Exception> T accept(ExprVisitor<T, V, E> visitor, V value) throws E {
    if (visitor instanceof AbstractExecExprVisitor) {
      AbstractExecExprVisitor<T, V, E> abstractExecExprVisitor = (AbstractExecExprVisitor<T, V, E>) visitor;
      return abstractExecExprVisitor.visitValueVectorReadExpression(this, value);
    }
    return visitor.visitUnknown(this, value);
  }

  public TypedFieldId getFieldId() {
    return fieldId;
  }

  @Override
  public ExpressionPosition getPosition() {
    return ExpressionPosition.UNKNOWN;
  }

  @Override
  public Iterator<LogicalExpression> iterator() {
    return ImmutableSet.<LogicalExpression>of().iterator();
  }

  @Override
  public int getSelfCost() {
    return 0;  // TODO
  }

  @Override
  public int getCumulativeCost() {
    return 0; // TODO
  }

  @Override
  public String toString() {
    return "ValueVectorReadExpression [fieldId=" + fieldId + "]";
  }

}
