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
package org.lealone.hansql.exec.planner.index;

import java.util.Map;
import java.util.Set;

import org.apache.drill.shaded.guava.com.google.common.collect.ImmutableMap;
import org.apache.drill.shaded.guava.com.google.common.collect.Maps;
import org.apache.drill.shaded.guava.com.google.common.collect.Sets;
import org.lealone.hansql.common.expression.LogicalExpression;
import org.lealone.hansql.exec.planner.logical.DrillOptiq;
import org.lealone.hansql.exec.planner.logical.DrillParseContext;
import org.lealone.hansql.exec.planner.physical.PrelUtil;
import org.lealone.hansql.optimizer.rel.RelNode;
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
import org.lealone.hansql.optimizer.rex.RexVisitorImpl;
import org.lealone.hansql.optimizer.sql.SqlKind;
import org.lealone.hansql.optimizer.sql.type.SqlTypeName;

/**
 * The filter expressions that could be indexed
 * Other than SchemaPaths, which represent columns of a table and could be indexed,
 * we consider only function expressions, and specifically, CAST function.
 * To judge if an expression is indexable, we check these:
 * 1, this expression should be one operand of a comparison operator, one of SqlKind.COMPARISON:
 *      IN, EQUALS, NOT_EQUALS, LESS_THAN, GREATER_THAN, GREATER_THAN_OR_EQUAL, LESS_THAN_OR_EQUAL
 * 2, the expression tree should contain at least one inputRef (which means this expression is a
 *     computation on top of at least one column), and if we have more than one indexable expressions
 *     are found from operands of comparison operator, we should not take any expression as indexable.
 *
 * 3, (LIMIT to one level function) the expression is a function call, and no nested function call underneath, except ITEM
 * 4, (LIMIT to CAST), the function call is a CAST
 */
public class IndexableExprMarker extends RexVisitorImpl<Boolean> {

    // map of rexNode->converted LogicalExpression
    final Map<RexNode, LogicalExpression> desiredExpressions = Maps.newHashMap();

    // the expressions in equality comparison
    final Map<RexNode, LogicalExpression> equalityExpressions = Maps.newHashMap();

    // the expression found in non-equality comparison
    final Map<RexNode, LogicalExpression> notInEquality = Maps.newHashMap();

    // for =(cast(a.b as VARCHAR(len)), 'abcd'), if the 'len' is less than the max length of casted field on index
    // table,
    // we want to rewrite it to LIKE(cast(a.b as VARCHAR(len)), 'abcd%')
    // map equalOnCastChar: key is the equal operator, value is the operand (cast(a.b as VARCHAR(10)),
    final Map<RexNode, LogicalExpression> equalOnCastChar = Maps.newHashMap();

    final private RelNode inputRel;

    // flag current recursive call state: whether we are on a direct operand of comparison operator
    boolean directCompareOp = false;

    RexCall contextCall = null;

    DrillParseContext parserContext;

    public IndexableExprMarker(RelNode inputRel) {
        super(true);
        this.inputRel = inputRel;
        parserContext = new DrillParseContext(PrelUtil.getPlannerSettings(inputRel.getCluster()));
    }

    public Map<RexNode, LogicalExpression> getIndexableExpression() {
        return ImmutableMap.copyOf(desiredExpressions);
    }

    public Map<RexNode, LogicalExpression> getEqualOnCastChar() {
        return ImmutableMap.copyOf(equalOnCastChar);
    }

    /**
     * return the expressions that were only in equality condition _and_ only once. ( a.b = 'value' )
     * @return
     */
    public Set<LogicalExpression> getExpressionsOnlyInEquality() {

        Set<LogicalExpression> onlyInEquality = Sets.newHashSet();

        Set<LogicalExpression> notInEqSet = Sets.newHashSet();

        Set<LogicalExpression> inEqMoreThanOnce = Sets.newHashSet();

        notInEqSet.addAll(notInEquality.values());

        for (LogicalExpression expr : equalityExpressions.values()) {
            // only process expr that is not in any non-equality condition(!notInEqSet.contains)
            if (!notInEqSet.contains(expr)) {

                // expr appear in two and more equality conditions should be ignored too
                if (inEqMoreThanOnce.contains(expr)) {
                    continue;
                }

                // we already have recorded this expr in equality condition, move it to inEqMoreThanOnce
                if (onlyInEquality.contains(expr)) {
                    inEqMoreThanOnce.add(expr);
                    onlyInEquality.remove(expr);
                    continue;
                }

                // finally we could take this expr
                onlyInEquality.add(expr);
            }
        }
        return onlyInEquality;
    }

    @Override
    public Boolean visitInputRef(RexInputRef rexInputRef) {
        return directCompareOp;
    }

    public boolean containInputRef(RexNode rex) {
        if (rex instanceof RexInputRef) {
            return true;
        }
        if ((rex instanceof RexCall) && "ITEM".equals(((RexCall) rex).getOperator().getName())) {
            return true;
        }
        // TODO: use a visitor search recursively for inputRef, if found one return true
        return false;
    }

    public boolean operandsAreIndexable(RexCall call) {
        SqlKind kind = call.getKind();
        boolean kindIsRight = (SqlKind.COMPARISON.contains(kind) || kind == SqlKind.LIKE || kind == SqlKind.SIMILAR);

        if (!kindIsRight) {
            return false;
        }

        int inputReference = 0;
        for (RexNode operand : call.getOperands()) {
            // if for this operator, there are two operands and more have inputRef, which means it is something like:
            // a.b = a.c, instead of a.b ='hello', so this cannot apply index
            if (containInputRef(operand)) {
                inputReference++;
                if (inputReference >= 2) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public Boolean visitCall(RexCall call) {
        if (call.getKind() == SqlKind.NOT || call.getKind() == SqlKind.NOT_EQUALS || call.getKind() == SqlKind.NOT_IN) {
            // Conditions under NOT are not indexable
            return false;
        }
        if (operandsAreIndexable(call)) {
            for (RexNode operand : call.getOperands()) {
                directCompareOp = true;
                contextCall = call;
                boolean markIt = operand.accept(this);
                directCompareOp = false;
                contextCall = null;
                if (markIt) {
                    LogicalExpression expr = DrillOptiq.toDrill(parserContext, inputRel, operand);
                    desiredExpressions.put(operand, expr);
                    if (call.getKind() == SqlKind.EQUALS) {
                        equalityExpressions.put(operand, expr);
                    } else {
                        notInEquality.put(operand, expr);
                    }
                }
            }
            return false;
        }

        // now we are handling a call directly under comparison e.g. <([call], literal)
        if (directCompareOp) {
            // if it is an item, or CAST function
            if ("ITEM".equals(call.getOperator().getName())) {
                return directCompareOp;
            } else if (call.getKind() == SqlKind.CAST) {
                // For now, we care only direct CAST: CAST's operand is a field(schemaPath),
                // either ITEM call(nested name) or inputRef

                // cast as char/varchar in equals function
                if (contextCall != null && contextCall.getKind() == SqlKind.EQUALS
                        && (call.getType().getSqlTypeName() == SqlTypeName.CHAR
                                || call.getType().getSqlTypeName() == SqlTypeName.VARCHAR)) {
                    equalOnCastChar.put(contextCall, DrillOptiq.toDrill(parserContext, inputRel, call));
                }

                RexNode castOp = call.getOperands().get(0);
                if (castOp instanceof RexInputRef) {
                    return true;
                }
                if ((castOp instanceof RexCall) && ("ITEM".equals(((RexCall) castOp).getOperator().getName()))) {
                    return true;
                }
            }
        }

        for (RexNode operand : call.getOperands()) {
            operand.accept(this);
        }
        return false;
    }

    @Override
    public Boolean visitLocalRef(RexLocalRef localRef) {
        return false;
    }

    @Override
    public Boolean visitLiteral(RexLiteral literal) {
        return false;
    }

    @Override
    public Boolean visitOver(RexOver over) {
        return false;
    }

    @Override
    public Boolean visitCorrelVariable(RexCorrelVariable correlVariable) {
        return false;
    }

    @Override
    public Boolean visitDynamicParam(RexDynamicParam dynamicParam) {
        return false;
    }

    @Override
    public Boolean visitRangeRef(RexRangeRef rangeRef) {
        return false;
    }

    @Override
    public Boolean visitFieldAccess(RexFieldAccess fieldAccess) {
        final RexNode expr = fieldAccess.getReferenceExpr();
        return expr.accept(this);
    }
}
