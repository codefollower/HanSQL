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
package org.lealone.hansql.exec.store;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.apache.drill.shaded.guava.com.google.common.collect.ImmutableSet;
import org.lealone.hansql.common.JSONOptions;
import org.lealone.hansql.common.expression.SchemaPath;
import org.lealone.hansql.common.logical.FormatPluginConfig;
import org.lealone.hansql.exec.context.DrillbitContext;
import org.lealone.hansql.exec.context.options.SessionOptionManager;
import org.lealone.hansql.exec.ops.OptimizerRulesContext;
import org.lealone.hansql.exec.physical.base.AbstractGroupScan;
import org.lealone.hansql.exec.physical.base.MetadataProviderManager;
import org.lealone.hansql.exec.planner.PlannerPhase;
import org.lealone.hansql.exec.store.dfs.FormatPlugin;
import org.lealone.hansql.optimizer.plan.RelOptRule;

/**
 * Abstract class for StorePlugin implementations.
 * See StoragePlugin for description of the interface intent and its methods.
 */
public abstract class AbstractStoragePlugin implements StoragePlugin {

  protected final DrillbitContext context;
  private final String name;

  protected AbstractStoragePlugin(DrillbitContext inContext, String inName) {
    this.context = inContext;
    this.name = inName == null ? null : inName.toLowerCase();
  }

  @Override
  public boolean supportsRead() {
    return false;
  }

  @Override
  public boolean supportsWrite() {
    return false;
  }

  /**
   * @deprecated Marking for deprecation in next major version release. Use
   *             {@link #getOptimizerRules(org.lealone.hansql.exec.ops.OptimizerRulesContext, org.lealone.hansql.exec.planner.PlannerPhase)}
   */
  @Override
  @Deprecated
  public Set<? extends RelOptRule> getOptimizerRules(OptimizerRulesContext optimizerContext) {
    return ImmutableSet.of();
  }

  /**
   * @deprecated Marking for deprecation in next major version release. Use
   *             {@link #getOptimizerRules(org.lealone.hansql.exec.ops.OptimizerRulesContext, org.lealone.hansql.exec.planner.PlannerPhase)}
   */
  @Deprecated
  public Set<? extends RelOptRule> getLogicalOptimizerRules(OptimizerRulesContext optimizerContext) {
    return ImmutableSet.of();
  }

  /**
   * @deprecated Marking for deprecation in next major version release. Use
   *             {@link #getOptimizerRules(org.lealone.hansql.exec.ops.OptimizerRulesContext, org.lealone.hansql.exec.planner.PlannerPhase)}
   */
  @Deprecated
  public Set<? extends RelOptRule> getPhysicalOptimizerRules(OptimizerRulesContext optimizerRulesContext) {
    // To be backward compatible, by default call the getOptimizerRules() method.
    return getOptimizerRules(optimizerRulesContext);
  }

  /**
   *
   * Note: Move this method to {@link StoragePlugin} interface in next major version release.
   */
  public Set<? extends RelOptRule> getOptimizerRules(OptimizerRulesContext optimizerContext, PlannerPhase phase) {
    switch (phase) {
    case LOGICAL_PRUNE_AND_JOIN:
    case LOGICAL_PRUNE:
    case PARTITION_PRUNING:
      return getLogicalOptimizerRules(optimizerContext);
    case PHYSICAL:
      return getPhysicalOptimizerRules(optimizerContext);
    case LOGICAL:
    case JOIN_PLANNING:
    default:
      return ImmutableSet.of();
    }
  }

  @Override
  public AbstractGroupScan getPhysicalScan(String userName, JSONOptions selection, SessionOptionManager options) throws IOException {
    return getPhysicalScan(userName, selection);
  }

  @Override
  public AbstractGroupScan getPhysicalScan(String userName, JSONOptions selection, SessionOptionManager options, MetadataProviderManager metadataProviderManager) throws IOException {
    return getPhysicalScan(userName, selection, options);
  }

  @Override
  public AbstractGroupScan getPhysicalScan(String userName, JSONOptions selection) throws IOException {
    return getPhysicalScan(userName, selection, AbstractGroupScan.ALL_COLUMNS);
  }

  @Override
  public AbstractGroupScan getPhysicalScan(String userName, JSONOptions selection, List<SchemaPath> columns, SessionOptionManager options) throws IOException {
    return getPhysicalScan(userName, selection, columns);
  }

  @Override
  public AbstractGroupScan getPhysicalScan(String userName, JSONOptions selection, List<SchemaPath> columns, SessionOptionManager options, MetadataProviderManager metadataProviderManager) throws IOException {
    return getPhysicalScan(userName, selection, columns, options);
  }

  @Override
  public AbstractGroupScan getPhysicalScan(String userName, JSONOptions selection, List<SchemaPath> columns) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void start() throws IOException { }

  @Override
  public void close() throws Exception { }

  @Override
  public FormatPlugin getFormatPlugin(FormatPluginConfig config) {
    throw new UnsupportedOperationException(String.format("%s doesn't support format plugins", getClass().getName()));
  }

  @Override
  public String getName() {
    return name;
  }

  public DrillbitContext getContext() {
    return context;
  }

}
