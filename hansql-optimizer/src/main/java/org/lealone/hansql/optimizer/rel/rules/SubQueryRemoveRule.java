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
package org.lealone.hansql.optimizer.rel.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.lealone.hansql.optimizer.plan.RelOptRule;
import org.lealone.hansql.optimizer.plan.RelOptRuleCall;
import org.lealone.hansql.optimizer.plan.RelOptRuleOperand;
import org.lealone.hansql.optimizer.plan.RelOptUtil;
import org.lealone.hansql.optimizer.rel.RelNode;
import org.lealone.hansql.optimizer.rel.core.Correlate;
import org.lealone.hansql.optimizer.rel.core.CorrelationId;
import org.lealone.hansql.optimizer.rel.core.Filter;
import org.lealone.hansql.optimizer.rel.core.Join;
import org.lealone.hansql.optimizer.rel.core.JoinRelType;
import org.lealone.hansql.optimizer.rel.core.Project;
import org.lealone.hansql.optimizer.rel.core.RelFactories;
import org.lealone.hansql.optimizer.rel.metadata.RelMetadataQuery;
import org.lealone.hansql.optimizer.rex.LogicVisitor;
import org.lealone.hansql.optimizer.rex.RexCorrelVariable;
import org.lealone.hansql.optimizer.rex.RexInputRef;
import org.lealone.hansql.optimizer.rex.RexNode;
import org.lealone.hansql.optimizer.rex.RexShuttle;
import org.lealone.hansql.optimizer.rex.RexSubQuery;
import org.lealone.hansql.optimizer.rex.RexUtil;
import org.lealone.hansql.optimizer.sql.SqlKind;
import org.lealone.hansql.optimizer.sql.fun.SqlQuantifyOperator;
import org.lealone.hansql.optimizer.sql.fun.SqlStdOperatorTable;
import org.lealone.hansql.optimizer.sql2rel.RelDecorrelator;
import org.lealone.hansql.optimizer.tools.RelBuilder;
import org.lealone.hansql.optimizer.tools.RelBuilderFactory;
import org.lealone.hansql.optimizer.util.ImmutableBitSet;
import org.lealone.hansql.optimizer.util.Pair;
import org.lealone.hansql.optimizer.util.Util;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/**
 * Transform that converts IN, EXISTS and scalar sub-queries into joins.
 *
 * <p>Sub-queries are represented by {@link RexSubQuery} expressions.
 *
 * <p>A sub-query may or may not be correlated. If a sub-query is correlated,
 * the wrapped {@link RelNode} will contain a {@link RexCorrelVariable} before
 * the rewrite, and the product of the rewrite will be a {@link Correlate}.
 * The Correlate can be removed using {@link RelDecorrelator}.
 */
public abstract class SubQueryRemoveRule extends RelOptRule {
    public static final SubQueryRemoveRule PROJECT = new SubQueryProjectRemoveRule(RelFactories.LOGICAL_BUILDER);

    public static final SubQueryRemoveRule FILTER = new SubQueryFilterRemoveRule(RelFactories.LOGICAL_BUILDER);

    public static final SubQueryRemoveRule JOIN = new SubQueryJoinRemoveRule(RelFactories.LOGICAL_BUILDER);

    /**
     * Creates a SubQueryRemoveRule.
     *
     * @param operand     root operand, must not be null
     * @param description Description, or null to guess description
     * @param relBuilderFactory Builder for relational expressions
     */
    public SubQueryRemoveRule(RelOptRuleOperand operand, RelBuilderFactory relBuilderFactory, String description) {
        super(operand, relBuilderFactory, description);
    }

    protected RexNode apply(RexSubQuery e, Set<CorrelationId> variablesSet, RelOptUtil.Logic logic, RelBuilder builder,
            int inputCount, int offset) {
        switch (e.getKind()) {
        case SCALAR_QUERY:
            return rewriteScalarQuery(e, variablesSet, builder, inputCount, offset);
        case SOME:
            return rewriteSome(e, builder);
        case IN:
            return rewriteIn(e, variablesSet, logic, builder, offset);
        case EXISTS:
            return rewriteExists(e, variablesSet, logic, builder);
        default:
            throw new AssertionError(e.getKind());
        }
    }

    /**
     * Rewrites a scalar sub-query into an
     * {@link org.lealone.hansql.optimizer.rel.core.Aggregate}.
     *
     * @param e            IN sub-query to rewrite
     * @param variablesSet A set of variables used by a relational
     *                     expression of the specified RexSubQuery
     * @param builder      Builder
     * @param offset       Offset to shift {@link RexInputRef}
     *
     * @return Expression that may be used to replace the RexSubQuery
     */
    private RexNode rewriteScalarQuery(RexSubQuery e, Set<CorrelationId> variablesSet, RelBuilder builder,
            int inputCount, int offset) {
        builder.push(e.getRel());
        final RelMetadataQuery mq = e.getRel().getCluster().getMetadataQuery();
        final Boolean unique = mq.areColumnsUnique(builder.peek(), ImmutableBitSet.of());
        if (unique == null || !unique) {
            builder.aggregate(builder.groupKey(),
                    builder.aggregateCall(SqlStdOperatorTable.SINGLE_VALUE, builder.field(0)));
        }
        builder.join(JoinRelType.LEFT, builder.literal(true), variablesSet);
        return field(builder, inputCount, offset);
    }

    /**
     * Rewrites a SOME sub-query into a {@link Join}.
     *
     * @param e            SOME sub-query to rewrite
     * @param builder      Builder
     *
     * @return Expression that may be used to replace the RexSubQuery
     */
    private RexNode rewriteSome(RexSubQuery e, RelBuilder builder) {
        // Most general case, where the left and right keys might have nulls, and
        // caller requires 3-valued logic return.
        //
        // select e.deptno, e.deptno < some (select deptno from emp) as v
        // from emp as e
        //
        // becomes
        //
        // select e.deptno,
        // case
        // when q.c = 0 then false // sub-query is empty
        // when (e.deptno < q.m) is true then true
        // when q.c > q.d then unknown // sub-query has at least one null
        // else e.deptno < q.m
        // end as v
        // from emp as e
        // cross join (
        // select max(deptno) as m, count(*) as c, count(deptno) as d
        // from emp) as q
        //
        final SqlQuantifyOperator op = (SqlQuantifyOperator) e.getOperator();
        builder.push(e.getRel())
                .aggregate(builder.groupKey(),
                        op.comparisonKind == SqlKind.GREATER_THAN || op.comparisonKind == SqlKind.GREATER_THAN_OR_EQUAL
                                ? builder.min("m", builder.field(0))
                                : builder.max("m", builder.field(0)),
                        builder.count(false, "c"), builder.count(false, "d", builder.field(0)))
                .as("q").join(JoinRelType.INNER);
        return builder.call(SqlStdOperatorTable.CASE,
                builder.call(SqlStdOperatorTable.EQUALS, builder.field("q", "c"), builder.literal(0)),
                builder.literal(false),
                builder.call(SqlStdOperatorTable.IS_TRUE,
                        builder.call(RelOptUtil.op(op.comparisonKind, null), e.getOperands().get(0),
                                builder.field("q", "m"))),
                builder.literal(true),
                builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field("q", "c"), builder.field("q", "d")),
                builder.literal(null),
                builder.call(RelOptUtil.op(op.comparisonKind, null), e.getOperands().get(0), builder.field("q", "m")));
    }

    /**
     * Rewrites an EXISTS RexSubQuery into a {@link Join}.
     *
     * @param e            EXISTS sub-query to rewrite
     * @param variablesSet A set of variables used by a relational
     *                     expression of the specified RexSubQuery
     * @param logic        Logic for evaluating
     * @param builder      Builder
     *
     * @return Expression that may be used to replace the RexSubQuery
     */
    private RexNode rewriteExists(RexSubQuery e, Set<CorrelationId> variablesSet, RelOptUtil.Logic logic,
            RelBuilder builder) {
        builder.push(e.getRel());

        builder.project(builder.alias(builder.literal(true), "i"));
        switch (logic) {
        case TRUE:
            // Handles queries with single EXISTS in filter condition:
            // select e.deptno from emp as e
            // where exists (select deptno from emp)
            builder.aggregate(builder.groupKey(0));
            builder.as("dt");
            builder.join(JoinRelType.INNER, builder.literal(true), variablesSet);
            return builder.literal(true);
        default:
            builder.distinct();
        }

        builder.as("dt");

        builder.join(JoinRelType.LEFT, builder.literal(true), variablesSet);

        return builder.isNotNull(Util.last(builder.fields()));
    }

    /**
     * Rewrites an IN RexSubQuery into a {@link Join}.
     *
     * @param e            IN sub-query to rewrite
     * @param variablesSet A set of variables used by a relational
     *                     expression of the specified RexSubQuery
     * @param logic        Logic for evaluating
     * @param builder      Builder
     * @param offset       Offset to shift {@link RexInputRef}
     *
     * @return Expression that may be used to replace the RexSubQuery
     */
    private RexNode rewriteIn(RexSubQuery e, Set<CorrelationId> variablesSet, RelOptUtil.Logic logic,
            RelBuilder builder, int offset) {
        // Most general case, where the left and right keys might have nulls, and
        // caller requires 3-valued logic return.
        //
        // select e.deptno, e.deptno in (select deptno from emp)
        // from emp as e
        //
        // becomes
        //
        // select e.deptno,
        // case
        // when ct.c = 0 then false
        // when e.deptno is null then null
        // when dt.i is not null then true
        // when ct.ck < ct.c then null
        // else false
        // end
        // from emp as e
        // left join (
        // (select count(*) as c, count(deptno) as ck from emp) as ct
        // cross join (select distinct deptno, true as i from emp)) as dt
        // on e.deptno = dt.deptno
        //
        // If keys are not null we can remove "ct" and simplify to
        //
        // select e.deptno,
        // case
        // when dt.i is not null then true
        // else false
        // end
        // from emp as e
        // left join (select distinct deptno, true as i from emp) as dt
        // on e.deptno = dt.deptno
        //
        // We could further simplify to
        //
        // select e.deptno,
        // dt.i is not null
        // from emp as e
        // left join (select distinct deptno, true as i from emp) as dt
        // on e.deptno = dt.deptno
        //
        // but have not yet.
        //
        // If the logic is TRUE we can just kill the record if the condition
        // evaluates to FALSE or UNKNOWN. Thus the query simplifies to an inner
        // join:
        //
        // select e.deptno,
        // true
        // from emp as e
        // inner join (select distinct deptno from emp) as dt
        // on e.deptno = dt.deptno
        //

        builder.push(e.getRel());
        final List<RexNode> fields = new ArrayList<>(builder.fields());

        // for the case when IN has only literal operands, it may be handled
        // in the simpler way:
        //
        // select e.deptno, 123456 in (select deptno from emp)
        // from emp as e
        //
        // becomes
        //
        // select e.deptno,
        // case
        // when dt.c IS NULL THEN FALSE
        // when e.deptno IS NULL THEN NULL
        // when dt.cs IS FALSE THEN NULL
        // when dt.cs IS NOT NULL THEN TRUE
        // else false
        // end
        // from emp AS e
        // cross join (
        // select distinct deptno is not null as cs, count(*) as c
        // from emp
        // where deptno = 123456 or deptno is null or e.deptno is null
        // order by cs desc limit 1) as dt
        //

        boolean allLiterals = RexUtil.allLiterals(e.getOperands());
        final List<RexNode> expressionOperands = new ArrayList<>(e.getOperands());

        final List<RexNode> keyIsNulls = e.getOperands().stream().filter(operand -> operand.getType().isNullable())
                .map(builder::isNull).collect(Collectors.toList());

        if (allLiterals) {
            final List<RexNode> conditions = Pair.zip(expressionOperands, fields).stream()
                    .map(pair -> builder.equals(pair.left, pair.right)).collect(Collectors.toList());
            switch (logic) {
            case TRUE:
            case TRUE_FALSE:
                builder.filter(conditions);
                builder.project(builder.alias(builder.literal(true), "cs"));
                builder.distinct();
                break;
            default:
                List<RexNode> isNullOpperands = fields.stream().map(builder::isNull).collect(Collectors.toList());
                // uses keyIsNulls conditions in the filter to avoid empty results
                isNullOpperands.addAll(keyIsNulls);
                builder.filter(builder.or(builder.and(conditions), builder.or(isNullOpperands)));
                RexNode project = builder.and(fields.stream().map(builder::isNotNull).collect(Collectors.toList()));
                builder.project(builder.alias(project, "cs"));

                if (variablesSet.isEmpty()) {
                    builder.aggregate(builder.groupKey(builder.field("cs")), builder.count(false, "c"));

                    // sorts input with desc order since we are interested
                    // only in the case when one of the values is true.
                    // When true value is absent then we are interested
                    // only in false value.
                    builder.sortLimit(0, 1,
                            ImmutableList.of(builder.call(SqlStdOperatorTable.DESC, builder.field("cs"))));
                } else {
                    builder.distinct();
                }
            }
            // clears expressionOperands and fields lists since
            // all expressions were used in the filter
            expressionOperands.clear();
            fields.clear();
        } else {
            switch (logic) {
            case TRUE:
                builder.aggregate(builder.groupKey(fields));
                break;
            case TRUE_FALSE_UNKNOWN:
            case UNKNOWN_AS_TRUE:
                // Builds the cross join
                builder.aggregate(builder.groupKey(), builder.count(false, "c"),
                        builder.count(builder.fields()).as("ck"));
                builder.as("ct");
                if (!variablesSet.isEmpty()) {
                    builder.join(JoinRelType.LEFT, builder.literal(true), variablesSet);
                } else {
                    builder.join(JoinRelType.INNER, builder.literal(true), variablesSet);
                }
                offset += 2;
                builder.push(e.getRel());
                // fall through
            default:
                fields.add(builder.alias(builder.literal(true), "i"));
                builder.project(fields);
                builder.distinct();
            }
        }

        builder.as("dt");
        int refOffset = offset;
        final List<RexNode> conditions = Pair.zip(expressionOperands, builder.fields()).stream()
                .map(pair -> builder.equals(pair.left, RexUtil.shift(pair.right, refOffset)))
                .collect(Collectors.toList());
        switch (logic) {
        case TRUE:
            builder.join(JoinRelType.INNER, builder.and(conditions), variablesSet);
            return builder.literal(true);
        }
        // Now the left join
        builder.join(JoinRelType.LEFT, builder.and(conditions), variablesSet);

        final ImmutableList.Builder<RexNode> operands = ImmutableList.builder();
        Boolean b = true;
        switch (logic) {
        case TRUE_FALSE_UNKNOWN:
            b = null;
            // fall through
        case UNKNOWN_AS_TRUE:
            if (allLiterals) {
                // Considers case when right side of IN is empty
                // for the case of non-correlated sub-queries
                if (variablesSet.isEmpty()) {
                    operands.add(builder.isNull(builder.field("c")), builder.literal(false));
                }
                operands.add(builder.equals(builder.field("cs"), builder.literal(false)), builder.literal(b));
            } else {
                operands.add(builder.equals(builder.field("ct", "c"), builder.literal(0)), builder.literal(false));
            }
            break;
        }

        if (!keyIsNulls.isEmpty()) {
            operands.add(builder.or(keyIsNulls), builder.literal(null));
        }

        if (allLiterals) {
            operands.add(builder.isNotNull(builder.field("cs")), builder.literal(true));
        } else {
            operands.add(builder.isNotNull(Util.last(builder.fields())), builder.literal(true));
        }

        if (!allLiterals) {
            switch (logic) {
            case TRUE_FALSE_UNKNOWN:
            case UNKNOWN_AS_TRUE:
                operands.add(builder.call(SqlStdOperatorTable.LESS_THAN, builder.field("ct", "ck"),
                        builder.field("ct", "c")), builder.literal(b));
            }
        }
        operands.add(builder.literal(false));
        return builder.call(SqlStdOperatorTable.CASE, operands.build());
    }

    /** Returns a reference to a particular field, by offset, across several
     * inputs on a {@link RelBuilder}'s stack. */
    private RexInputRef field(RelBuilder builder, int inputCount, int offset) {
        for (int inputOrdinal = 0;;) {
            final RelNode r = builder.peek(inputCount, inputOrdinal);
            if (offset < r.getRowType().getFieldCount()) {
                return builder.field(inputCount, inputOrdinal, offset);
            }
            ++inputOrdinal;
            offset -= r.getRowType().getFieldCount();
        }
    }

    /** Returns a list of expressions that project the first {@code fieldCount}
     * fields of the top input on a {@link RelBuilder}'s stack. */
    private static List<RexNode> fields(RelBuilder builder, int fieldCount) {
        final List<RexNode> projects = new ArrayList<>();
        for (int i = 0; i < fieldCount; i++) {
            projects.add(builder.field(i));
        }
        return projects;
    }

    /** Rule that converts sub-queries from project expressions into
     * {@link Correlate} instances. */
    public static class SubQueryProjectRemoveRule extends SubQueryRemoveRule {
        public SubQueryProjectRemoveRule(RelBuilderFactory relBuilderFactory) {
            super(operandJ(Project.class, null, RexUtil.SubQueryFinder::containsSubQuery, any()), relBuilderFactory,
                    "SubQueryRemoveRule:Project");
        }

        @Override
        public void onMatch(RelOptRuleCall call) {
            final Project project = call.rel(0);
            final RelBuilder builder = call.builder();
            final RexSubQuery e = RexUtil.SubQueryFinder.find(project.getProjects());
            assert e != null;
            final RelOptUtil.Logic logic = LogicVisitor.find(RelOptUtil.Logic.TRUE_FALSE_UNKNOWN, project.getProjects(),
                    e);
            builder.push(project.getInput());
            final int fieldCount = builder.peek().getRowType().getFieldCount();
            final RexNode target = apply(e, ImmutableSet.of(), logic, builder, 1, fieldCount);
            final RexShuttle shuttle = new ReplaceSubQueryShuttle(e, target);
            builder.project(shuttle.apply(project.getProjects()), project.getRowType().getFieldNames());
            call.transformTo(builder.build());
        }
    }

    /** Rule that converts a sub-queries from filter expressions into
     * {@link Correlate} instances. */
    public static class SubQueryFilterRemoveRule extends SubQueryRemoveRule {
        public SubQueryFilterRemoveRule(RelBuilderFactory relBuilderFactory) {
            super(operandJ(Filter.class, null, RexUtil.SubQueryFinder::containsSubQuery, any()), relBuilderFactory,
                    "SubQueryRemoveRule:Filter");
        }

        @Override
        public void onMatch(RelOptRuleCall call) {
            final Filter filter = call.rel(0);
            final RelBuilder builder = call.builder();
            builder.push(filter.getInput());
            int count = 0;
            RexNode c = filter.getCondition();
            while (true) {
                final RexSubQuery e = RexUtil.SubQueryFinder.find(c);
                if (e == null) {
                    assert count > 0;
                    break;
                }
                ++count;
                final RelOptUtil.Logic logic = LogicVisitor.find(RelOptUtil.Logic.TRUE, ImmutableList.of(c), e);
                final Set<CorrelationId> variablesSet = RelOptUtil.getVariablesUsed(e.getRel());
                final RexNode target = apply(e, variablesSet, logic, builder, 1,
                        builder.peek().getRowType().getFieldCount());
                final RexShuttle shuttle = new ReplaceSubQueryShuttle(e, target);
                c = c.accept(shuttle);
            }
            builder.filter(c);
            builder.project(fields(builder, filter.getRowType().getFieldCount()));
            call.transformTo(builder.build());
        }
    }

    /** Rule that converts sub-queries from join expressions into
     * {@link Correlate} instances. */
    public static class SubQueryJoinRemoveRule extends SubQueryRemoveRule {
        public SubQueryJoinRemoveRule(RelBuilderFactory relBuilderFactory) {
            super(operandJ(Join.class, null, RexUtil.SubQueryFinder::containsSubQuery, any()), relBuilderFactory,
                    "SubQueryRemoveRule:Join");
        }

        @Override
        public void onMatch(RelOptRuleCall call) {
            final Join join = call.rel(0);
            final RelBuilder builder = call.builder();
            final RexSubQuery e = RexUtil.SubQueryFinder.find(join.getCondition());
            assert e != null;
            final RelOptUtil.Logic logic = LogicVisitor.find(RelOptUtil.Logic.TRUE,
                    ImmutableList.of(join.getCondition()), e);
            builder.push(join.getLeft());
            builder.push(join.getRight());
            final int fieldCount = join.getRowType().getFieldCount();
            final RexNode target = apply(e, ImmutableSet.of(), logic, builder, 2, fieldCount);
            final RexShuttle shuttle = new ReplaceSubQueryShuttle(e, target);
            builder.join(join.getJoinType(), shuttle.apply(join.getCondition()));
            builder.project(fields(builder, join.getRowType().getFieldCount()));
            call.transformTo(builder.build());
        }
    }

    /** Shuttle that replaces occurrences of a given
     * {@link org.lealone.hansql.optimizer.rex.RexSubQuery} with a replacement
     * expression. */
    private static class ReplaceSubQueryShuttle extends RexShuttle {
        private final RexSubQuery subQuery;
        private final RexNode replacement;

        ReplaceSubQueryShuttle(RexSubQuery subQuery, RexNode replacement) {
            this.subQuery = subQuery;
            this.replacement = replacement;
        }

        @Override
        public RexNode visitSubQuery(RexSubQuery subQuery) {
            return subQuery.equals(this.subQuery) ? replacement : subQuery;
        }
    }
}

// End SubQueryRemoveRule.java
