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
package org.lealone.hansql.optimizer.rex;

import java.util.Collection;

import org.lealone.hansql.optimizer.rel.type.RelDataType;
import org.lealone.hansql.optimizer.sql.SqlKind;

/**
 * Row expression.
 *
 * <p>Every row-expression has a type.
 * (Compare with {@link org.lealone.hansql.optimizer.sql.SqlNode}, which is created before
 * validation, and therefore types may not be available.)
 *
 * <p>Some common row-expressions are: {@link RexLiteral} (constant value),
 * {@link RexVariable} (variable), {@link RexCall} (call to operator with
 * operands). Expressions are generally created using a {@link RexBuilder}
 * factory.</p>
 *
 * <p>All sub-classes of RexNode are immutable.</p>
 */
public interface RexNode {

    // ~ Methods ----------------------------------------------------------------

    RelDataType getType();

    /**
     * Returns the kind of node this is.
     *
     * @return Node kind, never null
     */
    SqlKind getKind();

    boolean isA(SqlKind kind);

    boolean isA(Collection<SqlKind> kinds);

    /**
     * Returns whether this expression always returns true. (Such as if this
     * expression is equal to the literal <code>TRUE</code>.)
     */
    boolean isAlwaysTrue();

    /**
     * Returns whether this expression always returns false. (Such as if this
     * expression is equal to the literal <code>FALSE</code>.)
     */
    boolean isAlwaysFalse();

    /**
     * Accepts a visitor, dispatching to the right overloaded
     * {@link RexVisitor#visitInputRef visitXxx} method.
     *
     * <p>Also see {@link RexUtil#apply(RexVisitor, java.util.List, RexNode)},
     * which applies a visitor to several expressions simultaneously.
     */
    <R> R accept(RexVisitor<R> visitor);

    /**
     * Accepts a visitor with a payload, dispatching to the right overloaded
     * {@link RexBiVisitor#visitInputRef(RexInputRef, Object)} visitXxx} method.
     */
    <R, P> R accept(RexBiVisitor<R, P> visitor, P arg);

    /** {@inheritDoc}
     *
     * <p>Every node must implement {@link #equals} based on its content
     */
    @Override
    boolean equals(Object obj);

    /** {@inheritDoc}
     *
     * <p>Every node must implement {@link #hashCode} consistent with
     * {@link #equals}
     */
    @Override
    int hashCode();
}

// End RexNode.java
