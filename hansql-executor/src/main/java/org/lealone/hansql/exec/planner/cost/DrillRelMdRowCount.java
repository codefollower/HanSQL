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
package org.lealone.hansql.exec.planner.cost;

import java.io.IOException;

import org.lealone.hansql.exec.planner.common.DrillLimitRelBase;
import org.lealone.hansql.exec.planner.common.DrillRelOptUtil;
import org.lealone.hansql.exec.planner.logical.DrillTable;
import org.lealone.hansql.exec.planner.physical.PlannerSettings;
import org.lealone.hansql.exec.planner.physical.PrelUtil;
import org.lealone.hansql.exec.util.Utilities;
import org.lealone.hansql.metastore.TableStatisticsKind;
import org.lealone.hansql.optimizer.rel.RelNode;
import org.lealone.hansql.optimizer.rel.SingleRel;
import org.lealone.hansql.optimizer.rel.core.Aggregate;
import org.lealone.hansql.optimizer.rel.core.Filter;
import org.lealone.hansql.optimizer.rel.core.Join;
import org.lealone.hansql.optimizer.rel.core.Project;
import org.lealone.hansql.optimizer.rel.core.Sort;
import org.lealone.hansql.optimizer.rel.core.TableScan;
import org.lealone.hansql.optimizer.rel.core.Union;
import org.lealone.hansql.optimizer.rel.metadata.ReflectiveRelMetadataProvider;
import org.lealone.hansql.optimizer.rel.metadata.RelMdRowCount;
import org.lealone.hansql.optimizer.rel.metadata.RelMetadataProvider;
import org.lealone.hansql.optimizer.rel.metadata.RelMetadataQuery;
import org.lealone.hansql.optimizer.util.BuiltInMethod;
import org.lealone.hansql.optimizer.util.ImmutableBitSet;


public class DrillRelMdRowCount extends RelMdRowCount{
  private static final DrillRelMdRowCount INSTANCE = new DrillRelMdRowCount();

  public static final RelMetadataProvider SOURCE = ReflectiveRelMetadataProvider.reflectiveSource(BuiltInMethod.ROW_COUNT.method, INSTANCE);

  @Override
  public Double getRowCount(Aggregate rel, RelMetadataQuery mq) {
    ImmutableBitSet groupKey = ImmutableBitSet.range(rel.getGroupCount());

    if (groupKey.isEmpty()) {
      return 1.0;
    } else {
      return super.getRowCount(rel, mq);
    }
  }

  public double getRowCount(DrillLimitRelBase rel, RelMetadataQuery mq) {
    return rel.estimateRowCount(mq);
  }

  @Override
  public Double getRowCount(Union rel, RelMetadataQuery mq) {
    return rel.estimateRowCount(mq);
  }

  @Override
  public Double getRowCount(Project rel, RelMetadataQuery mq) {
    return rel.estimateRowCount(mq);
  }

  @Override
  public Double getRowCount(Sort rel, RelMetadataQuery mq) {
    return rel.estimateRowCount(mq);
  }

  @Override
  public Double getRowCount(SingleRel rel, RelMetadataQuery mq) {
    return rel.estimateRowCount(mq);
  }

  @Override
  public Double getRowCount(Join rel, RelMetadataQuery mq) {
    return rel.estimateRowCount(mq);
  }

  @Override
  public Double getRowCount(RelNode rel, RelMetadataQuery mq) {
    if (rel instanceof TableScan) {
      return getRowCountInternal((TableScan)rel, mq);
    }
    return super.getRowCount(rel, mq);
  }

  @Override
  public Double getRowCount(Filter rel, RelMetadataQuery mq) {
    // Need capped selectivity estimates. See the Filter getRows() method
    return rel.getRows();
  }

  private Double getRowCountInternal(TableScan rel, RelMetadataQuery mq) {
    DrillTable table = Utilities.getDrillTable(rel.getTable());
    PlannerSettings settings = PrelUtil.getSettings(rel.getCluster());
    // If guessing, return selectivity from RelMDRowCount
    if (DrillRelOptUtil.guessRows(rel)) {
      return super.getRowCount(rel, mq);
    }
    // Return rowcount from statistics, if available. Otherwise, delegate to parent.
    try {
      if (table != null
          && table.getGroupScan().getTableMetadata() != null
          && (boolean) TableStatisticsKind.HAS_STATISTICS.getValue(table.getGroupScan().getTableMetadata())
          /* For GroupScan rely on accurate count from the scan, if available, instead of
           * statistics since partition pruning/filter pushdown might have occurred.
           * e.g. ParquetGroupScan returns accurate rowcount. The other way would be to
           * iterate over the rowgroups present in the GroupScan to compute the rowcount.
           */
          && !(table.getGroupScan().getScanStats(settings).getGroupScanProperty().hasExactRowCount())) {
        return (Double) TableStatisticsKind.EST_ROW_COUNT.getValue(table.getGroupScan().getTableMetadata());
      }
    } catch (IOException ex) {
      return super.getRowCount(rel, mq);
    }
    return super.getRowCount(rel, mq);
  }
}
