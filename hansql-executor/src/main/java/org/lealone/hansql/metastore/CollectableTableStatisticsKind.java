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
package org.lealone.hansql.metastore;

import java.util.Collection;

/**
 * This class represents kinds of table statistics which may be received as a union
 * of other statistics, for example row count may be received as a sum of row counts
 * of underlying metadata parts.
 */
public interface CollectableTableStatisticsKind extends StatisticsKind {

  /**
   * Returns table statistics value received by collecting specified {@link ColumnStatistics}.
   *
   * @param statistics list of {@link ColumnStatistics} instances to be collected
   * @return column statistics value received by collecting specified {@link ColumnStatistics}
   */
  Object mergeStatistics(Collection<? extends BaseMetadata> statistics);
}
