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
package org.lealone.hansql.optimizer.sql2rel;

import org.lealone.hansql.optimizer.rel.RelHomogeneousShuttle;
import org.lealone.hansql.optimizer.rel.RelNode;
import org.lealone.hansql.optimizer.rel.core.CorrelationId;
import org.lealone.hansql.optimizer.rex.RexCorrelVariable;
import org.lealone.hansql.optimizer.rex.RexFieldAccess;
import org.lealone.hansql.optimizer.rex.RexNode;
import org.lealone.hansql.optimizer.rex.RexShuttle;
import org.lealone.hansql.optimizer.rex.RexSubQuery;

/**
 * Shuttle that finds references to a given {@link CorrelationId} within a tree
 * of {@link RelNode}s.
 */
public abstract class CorrelationReferenceFinder extends RelHomogeneousShuttle {
    private final MyRexVisitor rexVisitor;

    /** Creates CorrelationReferenceFinder. */
    protected CorrelationReferenceFinder() {
        rexVisitor = new MyRexVisitor(this);
    }

    protected abstract RexNode handle(RexFieldAccess fieldAccess);

    @Override
    public RelNode visit(RelNode other) {
        RelNode next = super.visit(other);
        return next.accept(rexVisitor);
    }

    /**
     * Replaces alternative names of correlation variable to its canonical name.
     */
    private static class MyRexVisitor extends RexShuttle {
        private final CorrelationReferenceFinder finder;

        private MyRexVisitor(CorrelationReferenceFinder finder) {
            this.finder = finder;
        }

        @Override
        public RexNode visitFieldAccess(RexFieldAccess fieldAccess) {
            if (fieldAccess.getReferenceExpr() instanceof RexCorrelVariable) {
                return finder.handle(fieldAccess);
            }
            return super.visitFieldAccess(fieldAccess);
        }

        @Override
        public RexNode visitSubQuery(RexSubQuery subQuery) {
            final RelNode r = subQuery.getRel().accept(finder); // look inside sub-queries
            if (r != subQuery.getRel()) {
                subQuery = subQuery.clone(r);
            }
            return super.visitSubQuery(subQuery);
        }
    }
}

// End CorrelationReferenceFinder.java
