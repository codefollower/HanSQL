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
package org.lealone.hansql.optimizer.rel.metadata;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

import org.lealone.hansql.optimizer.plan.RelOptTable;
import org.lealone.hansql.optimizer.plan.hep.HepRelVertex;
import org.lealone.hansql.optimizer.plan.volcano.RelSubset;
import org.lealone.hansql.optimizer.rel.RelCollation;
import org.lealone.hansql.optimizer.rel.RelCollationTraitDef;
import org.lealone.hansql.optimizer.rel.RelCollations;
import org.lealone.hansql.optimizer.rel.RelFieldCollation;
import org.lealone.hansql.optimizer.rel.RelNode;
import org.lealone.hansql.optimizer.rel.core.Calc;
import org.lealone.hansql.optimizer.rel.core.Filter;
import org.lealone.hansql.optimizer.rel.core.Join;
import org.lealone.hansql.optimizer.rel.core.JoinRelType;
import org.lealone.hansql.optimizer.rel.core.Project;
import org.lealone.hansql.optimizer.rel.core.Sort;
import org.lealone.hansql.optimizer.rel.core.SortExchange;
import org.lealone.hansql.optimizer.rel.core.TableScan;
import org.lealone.hansql.optimizer.rel.core.Values;
import org.lealone.hansql.optimizer.rel.core.Window;
import org.lealone.hansql.optimizer.rel.type.RelDataType;
import org.lealone.hansql.optimizer.rex.RexCall;
import org.lealone.hansql.optimizer.rex.RexCallBinding;
import org.lealone.hansql.optimizer.rex.RexInputRef;
import org.lealone.hansql.optimizer.rex.RexLiteral;
import org.lealone.hansql.optimizer.rex.RexNode;
import org.lealone.hansql.optimizer.rex.RexProgram;
import org.lealone.hansql.optimizer.sql.SemiJoinType;
import org.lealone.hansql.optimizer.sql.validate.SqlMonotonicity;
import org.lealone.hansql.optimizer.util.BuiltInMethod;
import org.lealone.hansql.optimizer.util.ImmutableIntList;
import org.lealone.hansql.optimizer.util.Ord;
import org.lealone.hansql.optimizer.util.Pair;
import org.lealone.hansql.optimizer.util.Util;

/**
 * RelMdCollation supplies a default implementation of
 * {@link org.lealone.hansql.optimizer.rel.metadata.RelMetadataQuery#collations}
 * for the standard logical algebra.
 */
public class RelMdCollation
    implements MetadataHandler<BuiltInMetadata.Collation> {
  public static final RelMetadataProvider SOURCE =
      ReflectiveRelMetadataProvider.reflectiveSource(
          BuiltInMethod.COLLATIONS.method, new RelMdCollation());

  //~ Constructors -----------------------------------------------------------

  private RelMdCollation() {}

  //~ Methods ----------------------------------------------------------------

  public MetadataDef<BuiltInMetadata.Collation> getDef() {
    return BuiltInMetadata.Collation.DEF;
  }

  /** Catch-all implementation for
   * {@link BuiltInMetadata.Collation#collations()},
   * invoked using reflection, for any relational expression not
   * handled by a more specific method.
   *
   * <p>{@link org.lealone.hansql.optimizer.rel.core.Union},
   * {@link org.lealone.hansql.optimizer.rel.core.Intersect},
   * {@link org.lealone.hansql.optimizer.rel.core.Minus},
   * {@link org.lealone.hansql.optimizer.rel.core.Join},
   * {@link org.lealone.hansql.optimizer.rel.core.SemiJoin},
   * {@link org.lealone.hansql.optimizer.rel.core.Correlate}
   * do not in general return sorted results
   * (but implementations using particular algorithms may).
   *
   * @param rel Relational expression
   * @return Relational expression's collations
   *
   * @see org.lealone.hansql.optimizer.rel.metadata.RelMetadataQuery#collations(RelNode)
   */
  public ImmutableList<RelCollation> collations(RelNode rel,
      RelMetadataQuery mq) {
    return ImmutableList.of();
  }

  public ImmutableList<RelCollation> collations(Window rel,
      RelMetadataQuery mq) {
    return ImmutableList.copyOf(window(mq, rel.getInput(), rel.groups));
  }

  public ImmutableList<RelCollation> collations(Filter rel,
      RelMetadataQuery mq) {
    return mq.collations(rel.getInput());
  }

  public ImmutableList<RelCollation> collations(TableScan scan,
      RelMetadataQuery mq) {
    return ImmutableList.copyOf(table(scan.getTable()));
  }

//  public ImmutableList<RelCollation> collations(EnumerableMergeJoin join,
//      RelMetadataQuery mq) {
//    // In general a join is not sorted. But a merge join preserves the sort
//    // order of the left and right sides.
//    return ImmutableList.copyOf(
//        RelMdCollation.mergeJoin(mq, join.getLeft(), join.getRight(),
//            join.getLeftKeys(), join.getRightKeys()));
//  }
//
//  public ImmutableList<RelCollation> collations(EnumerableJoin join,
//      RelMetadataQuery mq) {
//    return ImmutableList.copyOf(
//        RelMdCollation.enumerableJoin(mq, join.getLeft(), join.getRight(), join.getJoinType()));
//  }
//
//  public ImmutableList<RelCollation> collations(EnumerableThetaJoin join,
//      RelMetadataQuery mq) {
//    return ImmutableList.copyOf(
//        RelMdCollation.enumerableThetaJoin(mq, join.getLeft(), join.getRight(),
//            join.getJoinType()));
//  }
//
//  public ImmutableList<RelCollation> collations(EnumerableCorrelate join,
//      RelMetadataQuery mq) {
//    return ImmutableList.copyOf(
//        RelMdCollation.enumerableCorrelate(mq, join.getLeft(), join.getRight(),
//            join.getJoinType()));
//  }
//
//  public ImmutableList<RelCollation> collations(EnumerableSemiJoin join,
//      RelMetadataQuery mq) {
//    return ImmutableList.copyOf(
//        RelMdCollation.enumerableSemiJoin(mq, join.getLeft(), join.getRight()));
//  }

  public ImmutableList<RelCollation> collations(Sort sort,
      RelMetadataQuery mq) {
    return ImmutableList.copyOf(
        RelMdCollation.sort(sort.getCollation()));
  }

  public ImmutableList<RelCollation> collations(SortExchange sort,
      RelMetadataQuery mq) {
    return ImmutableList.copyOf(
        RelMdCollation.sort(sort.getCollation()));
  }

  public ImmutableList<RelCollation> collations(Project project,
      RelMetadataQuery mq) {
    return ImmutableList.copyOf(
        project(mq, project.getInput(), project.getProjects()));
  }

  public ImmutableList<RelCollation> collations(Calc calc,
      RelMetadataQuery mq) {
    return ImmutableList.copyOf(calc(mq, calc.getInput(), calc.getProgram()));
  }

  public ImmutableList<RelCollation> collations(Values values,
      RelMetadataQuery mq) {
    return ImmutableList.copyOf(
        values(mq, values.getRowType(), values.getTuples()));
  }

  public ImmutableList<RelCollation> collations(HepRelVertex rel,
      RelMetadataQuery mq) {
    return mq.collations(rel.getCurrentRel());
  }

  public ImmutableList<RelCollation> collations(RelSubset rel,
      RelMetadataQuery mq) {
    return ImmutableList.copyOf(
        Objects.requireNonNull(
            rel.getTraitSet().getTraits(RelCollationTraitDef.INSTANCE)));
  }

  // Helper methods

  /** Helper method to determine a
   * {@link org.lealone.hansql.optimizer.rel.core.TableScan}'s collation. */
  public static List<RelCollation> table(RelOptTable table) {
    return table.getCollationList();
  }

  /** Helper method to determine a
   * {@link org.lealone.hansql.optimizer.rel.core.Snapshot}'s collation. */
  public static List<RelCollation> snapshot(RelMetadataQuery mq, RelNode input) {
    return mq.collations(input);
  }

  /** Helper method to determine a
   * {@link org.lealone.hansql.optimizer.rel.core.Sort}'s collation. */
  public static List<RelCollation> sort(RelCollation collation) {
    return ImmutableList.of(collation);
  }

  /** Helper method to determine a
   * {@link org.lealone.hansql.optimizer.rel.core.Filter}'s collation. */
  public static List<RelCollation> filter(RelMetadataQuery mq, RelNode input) {
    return mq.collations(input);
  }

  /** Helper method to determine a
   * limit's collation. */
  public static List<RelCollation> limit(RelMetadataQuery mq, RelNode input) {
    return mq.collations(input);
  }

  /** Helper method to determine a
   * {@link org.lealone.hansql.optimizer.rel.core.Calc}'s collation. */
  public static List<RelCollation> calc(RelMetadataQuery mq, RelNode input,
      RexProgram program) {
    return program.getCollations(mq.collations(input));
  }

  /** Helper method to determine a {@link Project}'s collation. */
  public static List<RelCollation> project(RelMetadataQuery mq,
      RelNode input, List<? extends RexNode> projects) {
    final SortedSet<RelCollation> collations = new TreeSet<>();
    final List<RelCollation> inputCollations = mq.collations(input);
    if (inputCollations == null || inputCollations.isEmpty()) {
      return ImmutableList.of();
    }
    final Multimap<Integer, Integer> targets = LinkedListMultimap.create();
    final Map<Integer, SqlMonotonicity> targetsWithMonotonicity =
        new HashMap<>();
    for (Ord<RexNode> project : Ord.<RexNode>zip(projects)) {
      if (project.e instanceof RexInputRef) {
        targets.put(((RexInputRef) project.e).getIndex(), project.i);
      } else if (project.e instanceof RexCall) {
        final RexCall call = (RexCall) project.e;
        final RexCallBinding binding =
            RexCallBinding.create(input.getCluster().getTypeFactory(), call, inputCollations);
        targetsWithMonotonicity.put(project.i, call.getOperator().getMonotonicity(binding));
      }
    }
    final List<RelFieldCollation> fieldCollations = new ArrayList<>();
  loop:
    for (RelCollation ic : inputCollations) {
      if (ic.getFieldCollations().isEmpty()) {
        continue;
      }
      fieldCollations.clear();
      for (RelFieldCollation ifc : ic.getFieldCollations()) {
        final Collection<Integer> integers = targets.get(ifc.getFieldIndex());
        if (integers.isEmpty()) {
          continue loop; // cannot do this collation
        }
        fieldCollations.add(ifc.copy(integers.iterator().next()));
      }
      assert !fieldCollations.isEmpty();
      collations.add(RelCollations.of(fieldCollations));
    }

    final List<RelFieldCollation> fieldCollationsForRexCalls =
        new ArrayList<>();
    for (Map.Entry<Integer, SqlMonotonicity> entry
        : targetsWithMonotonicity.entrySet()) {
      final SqlMonotonicity value = entry.getValue();
      switch (value) {
      case NOT_MONOTONIC:
      case CONSTANT:
        break;
      default:
        fieldCollationsForRexCalls.add(
            new RelFieldCollation(entry.getKey(),
                RelFieldCollation.Direction.of(value)));
        break;
      }
    }

    if (!fieldCollationsForRexCalls.isEmpty()) {
      collations.add(RelCollations.of(fieldCollationsForRexCalls));
    }

    return ImmutableList.copyOf(collations);
  }

  /** Helper method to determine a
   * {@link org.lealone.hansql.optimizer.rel.core.Window}'s collation.
   *
   * <p>A Window projects the fields of its input first, followed by the output
   * from each of its windows. Assuming (quite reasonably) that the
   * implementation does not re-order its input rows, then any collations of its
   * input are preserved. */
  public static List<RelCollation> window(RelMetadataQuery mq, RelNode input,
      ImmutableList<Window.Group> groups) {
    return mq.collations(input);
  }

  /** Helper method to determine a
   * {@link org.lealone.hansql.optimizer.rel.core.Values}'s collation.
   *
   * <p>We actually under-report the collations. A Values with 0 or 1 rows - an
   * edge case, but legitimate and very common - is ordered by every permutation
   * of every subset of the columns.
   *
   * <p>So, our algorithm aims to:<ul>
   *   <li>produce at most N collations (where N is the number of columns);
   *   <li>make each collation as long as possible;
   *   <li>do not repeat combinations already emitted -
   *       if we've emitted {@code (a, b)} do not later emit {@code (b, a)};
   *   <li>probe the actual values and make sure that each collation is
   *      consistent with the data
   * </ul>
   *
   * <p>So, for an empty Values with 4 columns, we would emit
   * {@code (a, b, c, d), (b, c, d), (c, d), (d)}. */
  public static List<RelCollation> values(RelMetadataQuery mq,
      RelDataType rowType, ImmutableList<ImmutableList<RexLiteral>> tuples) {
    Util.discard(mq); // for future use
    final List<RelCollation> list = new ArrayList<>();
    final int n = rowType.getFieldCount();
    final List<Pair<RelFieldCollation, Ordering<List<RexLiteral>>>> pairs =
        new ArrayList<>();
  outer:
    for (int i = 0; i < n; i++) {
      pairs.clear();
      for (int j = i; j < n; j++) {
        final RelFieldCollation fieldCollation = new RelFieldCollation(j);
        Ordering<List<RexLiteral>> comparator = comparator(fieldCollation);
        Ordering<List<RexLiteral>> ordering;
        if (pairs.isEmpty()) {
          ordering = comparator;
        } else {
          ordering = Util.last(pairs).right.compound(comparator);
        }
        pairs.add(Pair.of(fieldCollation, ordering));
        if (!ordering.isOrdered(tuples)) {
          if (j == i) {
            continue outer;
          }
          pairs.remove(pairs.size() - 1);
        }
      }
      if (!pairs.isEmpty()) {
        list.add(RelCollations.of(Pair.left(pairs)));
      }
    }
    return list;
  }

  private static Ordering<List<RexLiteral>> comparator(
      RelFieldCollation fieldCollation) {
    final int nullComparison = fieldCollation.nullDirection.nullComparison;
    final int x = fieldCollation.getFieldIndex();
    switch (fieldCollation.direction) {
    case ASCENDING:
      return new Ordering<List<RexLiteral>>() {
        public int compare(List<RexLiteral> o1, List<RexLiteral> o2) {
          final Comparable c1 = o1.get(x).getValueAs(Comparable.class);
          final Comparable c2 = o2.get(x).getValueAs(Comparable.class);
          return RelFieldCollation.compare(c1, c2, nullComparison);
        }
      };
    default:
      return new Ordering<List<RexLiteral>>() {
        public int compare(List<RexLiteral> o1, List<RexLiteral> o2) {
          final Comparable c1 = o1.get(x).getValueAs(Comparable.class);
          final Comparable c2 = o2.get(x).getValueAs(Comparable.class);
          return RelFieldCollation.compare(c2, c1, -nullComparison);
        }
      };
    }
  }

  /** Helper method to determine a {@link Join}'s collation assuming that it
   * uses a merge-join algorithm.
   *
   * <p>If the inputs are sorted on other keys <em>in addition to</em> the join
   * key, the result preserves those collations too. */
  public static List<RelCollation> mergeJoin(RelMetadataQuery mq,
      RelNode left, RelNode right,
      ImmutableIntList leftKeys, ImmutableIntList rightKeys) {
    final ImmutableList.Builder<RelCollation> builder = ImmutableList.builder();

    final ImmutableList<RelCollation> leftCollations = mq.collations(left);
    assert RelCollations.contains(leftCollations, leftKeys)
        : "cannot merge join: left input is not sorted on left keys";
    builder.addAll(leftCollations);

    final ImmutableList<RelCollation> rightCollations = mq.collations(right);
    assert RelCollations.contains(rightCollations, rightKeys)
        : "cannot merge join: right input is not sorted on right keys";
    final int leftFieldCount = left.getRowType().getFieldCount();
    for (RelCollation collation : rightCollations) {
      builder.add(RelCollations.shift(collation, leftFieldCount));
    }
    return builder.build();
  }

  /**
   * Returns the collation of {@link EnumerableJoin} based on its inputs and the join type.
   */
  public static List<RelCollation> enumerableJoin(RelMetadataQuery mq,
      RelNode left, RelNode right, JoinRelType joinType) {
    return enumerableJoin0(mq, left, right, joinType);
  }

  /**
   * Returns the collation of {@link EnumerableThetaJoin} based on its inputs and the join type.
   */
  public static List<RelCollation> enumerableThetaJoin(RelMetadataQuery mq,
      RelNode left, RelNode right, JoinRelType joinType) {
    return enumerableJoin0(mq, left, right, joinType);
  }

  public static List<RelCollation> enumerableCorrelate(RelMetadataQuery mq,
      RelNode left, RelNode right, SemiJoinType joinType) {
    // The current implementation always preserve the sort order of the left input
    return mq.collations(left);
  }

  public static List<RelCollation> enumerableSemiJoin(RelMetadataQuery mq,
      RelNode left, RelNode right) {
    // The current implementation always preserve the sort order of the left input
    return mq.collations(left);
  }

  private static List<RelCollation> enumerableJoin0(RelMetadataQuery mq,
      RelNode left, RelNode right, JoinRelType joinType) {
    // The current implementation can preserve the sort order of the left input if one of the
    // following conditions hold:
    // (i) join type is INNER or LEFT;
    // (ii) RelCollation always orders nulls last.
    final ImmutableList<RelCollation> leftCollations = mq.collations(left);
    switch (joinType) {
    case INNER:
    case LEFT:
      return leftCollations;
    case RIGHT:
    case FULL:
      for (RelCollation collation : leftCollations) {
        for (RelFieldCollation field : collation.getFieldCollations()) {
          if (!(RelFieldCollation.NullDirection.LAST == field.nullDirection)) {
            return ImmutableList.of();
          }
        }
      }
      return leftCollations;
    }
    return ImmutableList.of();
  }
}

// End RelMdCollation.java
