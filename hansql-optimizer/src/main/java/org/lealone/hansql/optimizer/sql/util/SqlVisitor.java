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
package org.lealone.hansql.optimizer.sql.util;

import org.lealone.hansql.optimizer.sql.SqlCall;
import org.lealone.hansql.optimizer.sql.SqlDataTypeSpec;
import org.lealone.hansql.optimizer.sql.SqlDynamicParam;
import org.lealone.hansql.optimizer.sql.SqlIdentifier;
import org.lealone.hansql.optimizer.sql.SqlIntervalQualifier;
import org.lealone.hansql.optimizer.sql.SqlLiteral;
import org.lealone.hansql.optimizer.sql.SqlNode;
import org.lealone.hansql.optimizer.sql.SqlNodeList;
import org.lealone.hansql.optimizer.sql.SqlOperator;

/**
 * Visitor class, follows the
 * {@link org.lealone.hansql.optimizer.util.Glossary#VISITOR_PATTERN visitor pattern}.
 *
 * <p>The type parameter <code>R</code> is the return type of each <code>
 * visit()</code> method. If the methods do not need to return a value, use
 * {@link Void}.
 *
 * @see SqlBasicVisitor
 * @see SqlNode#accept(SqlVisitor)
 * @see SqlOperator#acceptCall
 *
 * @param <R> Return type
 */
public interface SqlVisitor<R> {
  //~ Methods ----------------------------------------------------------------

  /**
   * Visits a literal.
   *
   * @param literal Literal
   * @see SqlLiteral#accept(SqlVisitor)
   */
  R visit(SqlLiteral literal);

  /**
   * Visits a call to a {@link SqlOperator}.
   *
   * @param call Call
   * @see SqlCall#accept(SqlVisitor)
   */
  R visit(SqlCall call);

  /**
   * Visits a list of {@link SqlNode} objects.
   *
   * @param nodeList list of nodes
   * @see SqlNodeList#accept(SqlVisitor)
   */
  R visit(SqlNodeList nodeList);

  /**
   * Visits an identifier.
   *
   * @param id identifier
   * @see SqlIdentifier#accept(SqlVisitor)
   */
  R visit(SqlIdentifier id);

  /**
   * Visits a datatype specification.
   *
   * @param type datatype specification
   * @see SqlDataTypeSpec#accept(SqlVisitor)
   */
  R visit(SqlDataTypeSpec type);

  /**
   * Visits a dynamic parameter.
   *
   * @param param Dynamic parameter
   * @see SqlDynamicParam#accept(SqlVisitor)
   */
  R visit(SqlDynamicParam param);

  /**
   * Visits an interval qualifier
   *
   * @param intervalQualifier Interval qualifier
   * @see SqlIntervalQualifier#accept(SqlVisitor)
   */
  R visit(SqlIntervalQualifier intervalQualifier);
}

// End SqlVisitor.java
