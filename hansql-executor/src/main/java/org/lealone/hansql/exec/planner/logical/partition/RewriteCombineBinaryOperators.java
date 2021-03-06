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
package org.lealone.hansql.exec.planner.logical.partition;

import org.apache.drill.shaded.guava.com.google.common.collect.ImmutableList;
import org.apache.drill.shaded.guava.com.google.common.collect.Lists;
import org.lealone.hansql.optimizer.plan.RelOptUtil;
import org.lealone.hansql.optimizer.rel.type.RelDataType;
import org.lealone.hansql.optimizer.rex.RexBuilder;
import org.lealone.hansql.optimizer.rex.RexCall;
import org.lealone.hansql.optimizer.rex.RexCorrelVariable;
import org.lealone.hansql.optimizer.rex.RexDynamicParam;
import org.lealone.hansql.optimizer.rex.RexFieldAccess;
import org.lealone.hansql.optimizer.rex.RexInputRef;
import org.lealone.hansql.optimizer.rex.RexLiteral;
import org.lealone.hansql.optimizer.rex.RexLocalRef;
import org.lealone.hansql.optimizer.rex.RexNode;
import org.lealone.hansql.optimizer.rex.RexOver;
import org.lealone.hansql.optimizer.rex.RexRangeRef;
import org.lealone.hansql.optimizer.rex.RexUtil;
import org.lealone.hansql.optimizer.rex.RexVisitorImpl;
import org.lealone.hansql.optimizer.sql.SqlKind;
import org.lealone.hansql.optimizer.sql.SqlOperator;

import java.util.List;

/**
 * Rewrites an expression tree, replacing chained OR and AND operators with a single N-ary operator
 *
 * e.g.
 *
 * OR(A, OR(B, C)) ---> OR(A, B, C)
 */
 public class RewriteCombineBinaryOperators extends RexVisitorImpl<RexNode> {

  RexBuilder builder;
  public RewriteCombineBinaryOperators(boolean deep, RexBuilder builder) {
       super(deep);
       this.builder = builder;
     }

  @Override
  public RexNode visitInputRef(RexInputRef inputRef) {
    return inputRef;
  }

  @Override
  public RexNode visitLiteral(RexLiteral literal) {
    return literal;
  }

  @Override
  public RexNode visitOver(RexOver over) {
    return over;
  }

  @Override
  public RexNode visitCorrelVariable(RexCorrelVariable correlVariable) {
    return correlVariable;
  }

  @Override
  public RexNode visitCall(RexCall call) {
    SqlOperator op = call.getOperator();
    SqlKind kind = op.getKind();
    RelDataType type = call.getType();
    if (kind == SqlKind.AND) {
      List<RexNode> conjuncts = Lists.newArrayList();
      for (RexNode child : call.getOperands()) {
        conjuncts.addAll(RelOptUtil.conjunctions(child.accept(this)));
      }
      return RexUtil.composeConjunction(builder, conjuncts, true);
    }
    if (kind == SqlKind.OR) {
      List<RexNode> disjuncts = Lists.newArrayList();
      for (RexNode child : call.getOperands()) {
        disjuncts.addAll(RelOptUtil.disjunctions(child.accept(this)));
      }
      return RexUtil.composeDisjunction(builder, disjuncts, true);
    }
    return builder.makeCall(type, op, visitChildren(call));
  }

  private List<RexNode> visitChildren(RexCall call) {
    List<RexNode> children = Lists.newArrayList();
    for (RexNode child : call.getOperands()) {
      children.add(child.accept(this));
    }
    return ImmutableList.copyOf(children);
  }

  @Override
  public RexNode visitDynamicParam(RexDynamicParam dynamicParam) {
    return dynamicParam;
  }

  @Override
  public RexNode visitRangeRef(RexRangeRef rangeRef) {
    return rangeRef;
  }

  @Override
  public RexNode visitFieldAccess(RexFieldAccess fieldAccess) {
    return fieldAccess;
  }

  @Override
  public RexNode visitLocalRef(RexLocalRef localRef) {
    return localRef;
  }
}
