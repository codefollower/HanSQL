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

/**
 * Reference to a range of columns.
 *
 * <p>This construct is used only during the process of translating a
 * {@link org.lealone.hansql.optimizer.sql.SqlNode SQL} tree to a
 * {@link org.lealone.hansql.optimizer.rel.RelNode rel}/{@link RexNode rex}
 * tree. <em>Regular {@link RexNode rex} trees do not contain this
 * construct.</em></p>
 *
 * <p>While translating a join of EMP(EMPNO, ENAME, DEPTNO) to DEPT(DEPTNO2,
 * DNAME) we create <code>RexRangeRef(DeptType,3)</code> to represent the pair
 * of columns (DEPTNO2, DNAME) which came from DEPT. The type has 2 columns, and
 * therefore the range represents columns {3, 4} of the input.</p>
 *
 * <p>Suppose we later create a reference to the DNAME field of this
 * RexRangeRef; it will return a <code>{@link RexInputRef}(5,Integer)</code>,
 * and the {@link org.lealone.hansql.optimizer.rex.RexRangeRef} will disappear.</p>
 */
public interface RexRangeRef extends RexNode {

    int getOffset();

}
