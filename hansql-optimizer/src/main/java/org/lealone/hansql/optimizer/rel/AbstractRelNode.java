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
package org.lealone.hansql.optimizer.rel;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.lealone.hansql.optimizer.plan.Convention;
import org.lealone.hansql.optimizer.plan.ConventionTraitDef;
import org.lealone.hansql.optimizer.plan.RelOptCluster;
import org.lealone.hansql.optimizer.plan.RelOptCost;
import org.lealone.hansql.optimizer.plan.RelOptPlanner;
import org.lealone.hansql.optimizer.plan.RelOptQuery;
import org.lealone.hansql.optimizer.plan.RelOptTable;
import org.lealone.hansql.optimizer.plan.RelOptUtil;
import org.lealone.hansql.optimizer.plan.RelTrait;
import org.lealone.hansql.optimizer.plan.RelTraitSet;
import org.lealone.hansql.optimizer.rel.core.CorrelationId;
import org.lealone.hansql.optimizer.rel.metadata.Metadata;
import org.lealone.hansql.optimizer.rel.metadata.MetadataFactory;
import org.lealone.hansql.optimizer.rel.metadata.RelMetadataQuery;
import org.lealone.hansql.optimizer.rel.type.RelDataType;
import org.lealone.hansql.optimizer.rex.RexNode;
import org.lealone.hansql.optimizer.rex.RexShuttle;
import org.lealone.hansql.optimizer.sql.SqlExplainLevel;
import org.lealone.hansql.optimizer.util.ImmutableBitSet;
import org.lealone.hansql.optimizer.util.Litmus;
import org.lealone.hansql.optimizer.util.Pair;
import org.lealone.hansql.optimizer.util.Util;
import org.lealone.hansql.optimizer.util.trace.CalciteTrace;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base class for every relational expression ({@link RelNode}).
 */
public abstract class AbstractRelNode implements RelNode {
  //~ Static fields/initializers ---------------------------------------------

  /** Generator for {@link #id} values. */
  private static final AtomicInteger NEXT_ID = new AtomicInteger(0);

  private static final Logger LOGGER = CalciteTrace.getPlannerTracer();

  //~ Instance fields --------------------------------------------------------

  /**
   * Description, consists of id plus digest.
   */
  private String desc;

  /**
   * Cached type of this relational expression.
   */
  protected RelDataType rowType;

  /**
   * A short description of this relational expression's type, inputs, and
   * other properties. The string uniquely identifies the node; another node
   * is equivalent if and only if it has the same value. Computed by
   * {@link #computeDigest}, assigned by {@link #onRegister}, returned by
   * {@link #getDigest()}.
   *
   * @see #desc
   */
  protected String digest;

  private final RelOptCluster cluster;

  /**
   * unique id of this object -- for debugging
   */
  protected final int id;

  /**
   * The RelTraitSet that describes the traits of this RelNode.
   */
  protected RelTraitSet traitSet;

  //~ Constructors -----------------------------------------------------------

  /**
   * Creates an <code>AbstractRelNode</code>.
   */
  public AbstractRelNode(RelOptCluster cluster, RelTraitSet traitSet) {
    super();
    assert cluster != null;
    this.cluster = cluster;
    this.traitSet = traitSet;
    this.id = NEXT_ID.getAndIncrement();
    this.digest = getRelTypeName() + "#" + id;
    this.desc = digest;
    LOGGER.trace("new {}", digest);
  }

  //~ Methods ----------------------------------------------------------------

  public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) {
    // Note that empty set equals empty set, so relational expressions
    // with zero inputs do not generally need to implement their own copy
    // method.
    if (getInputs().equals(inputs)
        && traitSet == getTraitSet()) {
      return this;
    }
    throw new AssertionError("Relational expression should override copy. "
        + "Class=[" + getClass()
        + "]; traits=[" + getTraitSet()
        + "]; desired traits=[" + traitSet
        + "]");
  }

  protected static <T> T sole(List<T> collection) {
    assert collection.size() == 1;
    return collection.get(0);
  }

  @SuppressWarnings("deprecation")
  public List<RexNode> getChildExps() {
    return ImmutableList.of();
  }

  public final RelOptCluster getCluster() {
    return cluster;
  }

  public final Convention getConvention() {
    return traitSet.getTrait(ConventionTraitDef.INSTANCE);
  }

  public RelTraitSet getTraitSet() {
    return traitSet;
  }

  public String getCorrelVariable() {
    return null;
  }

  @SuppressWarnings("deprecation")
  public boolean isDistinct() {
    final RelMetadataQuery mq = RelMetadataQuery.instance();
    return Boolean.TRUE.equals(mq.areRowsUnique(this));
  }

  @SuppressWarnings("deprecation")
  public boolean isKey(ImmutableBitSet columns) {
    final RelMetadataQuery mq = RelMetadataQuery.instance();
    return Boolean.TRUE.equals(mq.areColumnsUnique(this, columns));
  }

  public int getId() {
    return id;
  }

  public RelNode getInput(int i) {
    List<RelNode> inputs = getInputs();
    return inputs.get(i);
  }

  @SuppressWarnings("deprecation")
  public final RelOptQuery getQuery() {
    return getCluster().getQuery();
  }

  public void register(RelOptPlanner planner) {
    Util.discard(planner);
  }

  public final String getRelTypeName() {
    String cn = getClass().getName();
    int i = cn.length();
    while (--i >= 0) {
      if (cn.charAt(i) == '$' || cn.charAt(i) == '.') {
        return cn.substring(i + 1);
      }
    }
    return cn;
  }

  public boolean isValid(Litmus litmus, Context context) {
    return litmus.succeed();
  }

  @SuppressWarnings("deprecation")
  public boolean isValid(boolean fail) {
    return isValid(Litmus.THROW, null);
  }

  /** @deprecated Use {@link RelMetadataQuery#collations(RelNode)} */
  @Deprecated // to be removed before 2.0
  public List<RelCollation> getCollationList() {
    return ImmutableList.of();
  }

  public final RelDataType getRowType() {
    if (rowType == null) {
      rowType = deriveRowType();
      assert rowType != null : this;
    }
    return rowType;
  }

  protected RelDataType deriveRowType() {
    // This method is only called if rowType is null, so you don't NEED to
    // implement it if rowType is always set.
    throw new UnsupportedOperationException();
  }

  public RelDataType getExpectedInputRowType(int ordinalInParent) {
    return getRowType();
  }

  public List<RelNode> getInputs() {
    return Collections.emptyList();
  }

  @SuppressWarnings("deprecation")
  public final double getRows() {
    return estimateRowCount(RelMetadataQuery.instance());
  }

  public double estimateRowCount(RelMetadataQuery mq) {
    return 1.0;
  }

  @SuppressWarnings("deprecation")
  public final Set<String> getVariablesStopped() {
    return CorrelationId.names(getVariablesSet());
  }

  public Set<CorrelationId> getVariablesSet() {
    return ImmutableSet.of();
  }

  public void collectVariablesUsed(Set<CorrelationId> variableSet) {
    // for default case, nothing to do
  }

  public void collectVariablesSet(Set<CorrelationId> variableSet) {
  }

  public void childrenAccept(RelVisitor visitor) {
    List<RelNode> inputs = getInputs();
    for (int i = 0; i < inputs.size(); i++) {
      visitor.visit(inputs.get(i), i, this);
    }
  }

  public RelNode accept(RelShuttle shuttle) {
    // Call fall-back method. Specific logical types (such as LogicalProject
    // and LogicalJoin) have their own RelShuttle.visit methods.
    return shuttle.visit(this);
  }

  public RelNode accept(RexShuttle shuttle) {
    return this;
  }

  @SuppressWarnings("deprecation")
  public final RelOptCost computeSelfCost(RelOptPlanner planner) {
    return computeSelfCost(planner, RelMetadataQuery.instance());
  }

  public RelOptCost computeSelfCost(RelOptPlanner planner,
      RelMetadataQuery mq) {
    // by default, assume cost is proportional to number of rows
    double rowCount = mq.getRowCount(this);
    double bytesPerRow = 1;
    return planner.getCostFactory().makeCost(rowCount, rowCount, 0);
  }

  public final <M extends Metadata> M metadata(Class<M> metadataClass,
      RelMetadataQuery mq) {
    final MetadataFactory factory = cluster.getMetadataFactory();
    final M metadata = factory.query(this, mq, metadataClass);
    assert metadata != null
        : "no provider found (rel=" + this + ", m=" + metadataClass
        + "); a backstop provider is recommended";
    // Usually the metadata belongs to the rel that created it. RelSubset and
    // HepRelVertex are notable exceptions, so disable the assert. It's not
    // worth the performance hit to override this method for them.
    //   assert metadata.rel() == this : "someone else's metadata";
    return metadata;
  }

  public void explain(RelWriter pw) {
    explainTerms(pw).done(this);
  }

  /**
   * Describes the inputs and attributes of this relational expression.
   * Each node should call {@code super.explainTerms}, then call the
   * {@link org.lealone.hansql.optimizer.rel.externalize.RelWriterImpl#input(String, RelNode)}
   * and
   * {@link org.lealone.hansql.optimizer.rel.externalize.RelWriterImpl#item(String, Object)}
   * methods for each input and attribute.
   *
   * @param pw Plan writer
   * @return Plan writer for fluent-explain pattern
   */
  public RelWriter explainTerms(RelWriter pw) {
    return pw;
  }

  public RelNode onRegister(RelOptPlanner planner) {
    List<RelNode> oldInputs = getInputs();
    List<RelNode> inputs = new ArrayList<>(oldInputs.size());
    for (final RelNode input : oldInputs) {
      RelNode e = planner.ensureRegistered(input, null);
      if (e != input) {
        // TODO: change 'equal' to 'eq', which is stronger.
        assert RelOptUtil.equal(
            "rowtype of rel before registration",
            input.getRowType(),
            "rowtype of rel after registration",
            e.getRowType(),
            Litmus.THROW);
      }
      inputs.add(e);
    }
    RelNode r = this;
    if (!Util.equalShallow(oldInputs, inputs)) {
      r = copy(getTraitSet(), inputs);
    }
    r.recomputeDigest();
    assert r.isValid(Litmus.THROW, null);
    return r;
  }

  public String recomputeDigest() {
    String tempDigest = computeDigest();
    assert tempDigest != null : "computeDigest() should be non-null";

    this.desc = "rel#" + id + ":" + tempDigest;
    this.digest = tempDigest;
    return this.digest;
  }

  public void replaceInput(
      int ordinalInParent,
      RelNode p) {
    throw new UnsupportedOperationException("replaceInput called on " + this);
  }

  public String toString() {
    return desc;
  }

  public final String getDescription() {
    return desc;
  }

  public final String getDigest() {
    return digest;
  }

  public RelOptTable getTable() {
    return null;
  }

  /**
   * Computes the digest. Does not modify this object.
   *
   * @return Digest
   */
  protected String computeDigest() {
    RelDigestWriter rdw = new RelDigestWriter();
    explain(rdw);
    return rdw.digest;
  }

  /**
   * A writer object used exclusively for computing the digest of a RelNode.
   *
   * <p>The writer is meant to be used only for computing a single digest and then thrown away.
   * After calling {@link #done(RelNode)} the writer should be used only to obtain the computed
   * {@link #digest}. Any other action is prohibited.</p>
   *
   */
  private static final class RelDigestWriter implements RelWriter {

    private final List<Pair<String, Object>> values = new ArrayList<>();

    String digest = null;

    @Override public void explain(final RelNode rel, final List<Pair<String, Object>> valueList) {
      throw new IllegalStateException("Should not be called for computing digest");
    }

    @Override public SqlExplainLevel getDetailLevel() {
      return SqlExplainLevel.DIGEST_ATTRIBUTES;
    }

    @Override public RelWriter item(String term, Object value) {
      values.add(Pair.of(term, value));
      return this;
    }

    @Override public RelWriter done(RelNode node) {
      StringBuilder sb = new StringBuilder();
      sb.append(node.getRelTypeName());

      for (RelTrait trait : node.getTraitSet()) {
        sb.append('.');
        sb.append(trait.toString());
      }

      sb.append('(');
      int j = 0;
      for (Pair<String, Object> value : values) {
        if (j++ > 0) {
          sb.append(',');
        }
        sb.append(value.left);
        sb.append('=');
        if (value.right instanceof RelNode) {
          RelNode input = (RelNode) value.right;
          sb.append(input.getRelTypeName());
          sb.append('#');
          sb.append(input.getId());
        } else {
          sb.append(value.right);
        }
      }
      sb.append(')');
      digest = sb.toString();
      return this;
    }
  }
}

// End AbstractRelNode.java
