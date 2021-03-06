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
package org.lealone.hansql.exec.vector.accessor;

/**
 * Position information about a writer used during vector overflow.
 */

public interface WriterPosition {

  /**
   * Position within the vector of the first value for the current row.
   * Note that this is always the first value for the row, even for a
   * writer deeply nested within a hierarchy of arrays. (The first
   * position for the current array is not exposed in this API.)
   *
   * @return the vector offset of the first value for the current
   * row
   */

  int rowStartIndex();

  /**
   * Return the last write position in the vector. This may be the
   * same as the writer index position (if the vector was written at
   * that point), or an earlier point. In either case, this value
   * points to the last valid value in the vector.
   *
   * @return index of the last valid value in the vector
   */

  int lastWriteIndex();

  /**
   * Current write index for the writer. This is the global
   * array location for arrays, same as the row index for top-level
   * columns.
   *
   * @return current write index
   */

  int writeIndex();
}
