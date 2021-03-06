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
package org.lealone.hansql.exec.store.dfs;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.lealone.hansql.common.expression.SchemaPath;
import org.lealone.hansql.common.logical.FormatPluginConfig;
import org.lealone.hansql.common.logical.StoragePluginConfig;
import org.lealone.hansql.exec.context.DrillbitContext;
import org.lealone.hansql.exec.context.options.OptionManager;
import org.lealone.hansql.exec.physical.base.AbstractGroupScan;
import org.lealone.hansql.exec.physical.base.AbstractWriter;
import org.lealone.hansql.exec.physical.base.MetadataProviderManager;
import org.lealone.hansql.exec.physical.base.PhysicalOperator;
import org.lealone.hansql.exec.planner.common.DrillStatsTable.TableStatistics;
import org.lealone.hansql.exec.store.StoragePluginOptimizerRule;

/**
 * Similar to a storage engine but built specifically to work within a FileSystem context.
 */
public interface FormatPlugin {

  boolean supportsRead();

  boolean supportsWrite();

  /**
   * Indicates whether this FormatPlugin supports auto-partitioning for CTAS statements
   * @return true if auto-partitioning is supported
   */
  boolean supportsAutoPartitioning();

  FormatMatcher getMatcher();

  AbstractWriter getWriter(PhysicalOperator child, String location, List<String> partitionColumns) throws IOException;

  Set<StoragePluginOptimizerRule> getOptimizerRules();

  AbstractGroupScan getGroupScan(String userName, FileSelection selection, List<SchemaPath> columns) throws IOException;

  default AbstractGroupScan getGroupScan(String userName, FileSelection selection, List<SchemaPath> columns, OptionManager options) throws IOException {
    return getGroupScan(userName, selection, columns);
  }

  default AbstractGroupScan getGroupScan(String userName, FileSelection selection, List<SchemaPath> columns, MetadataProviderManager metadataProviderManager) throws IOException {
    return getGroupScan(userName, selection, columns);
  }

  default AbstractGroupScan getGroupScan(String userName, FileSelection selection, List<SchemaPath> columns, OptionManager options, MetadataProviderManager metadataProvider) throws IOException {
    return getGroupScan(userName, selection, columns, metadataProvider);
  }

  boolean supportsStatistics();

  TableStatistics readStatistics(FileSystem fs, Path statsTablePath) throws IOException;

  void writeStatistics(TableStatistics statistics, FileSystem fs, Path statsTablePath) throws IOException;

  FormatPluginConfig getConfig();
  StoragePluginConfig getStorageConfig();
  Configuration getFsConf();
  DrillbitContext getContext();
  String getName();
}
