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
package org.lealone.hansql.exec.planner.sql.handlers;

import org.lealone.hansql.common.exceptions.DrillRuntimeException;
import org.lealone.hansql.exec.planner.fragment.DistributionAffinity;
import org.lealone.hansql.exec.planner.logical.DrillTable;
import org.lealone.hansql.exec.planner.logical.DrillTranslatableTable;
import org.lealone.hansql.optimizer.rel.RelNode;
import org.lealone.hansql.optimizer.rel.RelShuttleImpl;
import org.lealone.hansql.optimizer.rel.core.TableScan;

import java.io.IOException;

/**
 * Visitor to scan the RelNode tree and find if it contains any Scans that require hard distribution requirements.
 */
class FindHardDistributionScans extends RelShuttleImpl {
  private boolean contains;

  /**
   * Can the given <code>relTree</code> be executed in single fragment mode? For now this returns false when the
   * <code>relTree</code> contains one or more scans with hard affinity requirements.
   *
   * @param relTree
   * @return
   */
  public static boolean canForceSingleMode(final RelNode relTree) {
    final FindHardDistributionScans hdVisitor = new FindHardDistributionScans();
    relTree.accept(hdVisitor);
    // Can't run in single fragment mode if the query contains a table which has hard distribution requirement.
    return !hdVisitor.contains();
  }

  @Override
  public RelNode visit(TableScan scan) {
    DrillTable unwrap;
    unwrap = scan.getTable().unwrap(DrillTable.class);
    if (unwrap == null) {
      DrillTranslatableTable drillTranslatableTable = scan.getTable().unwrap(DrillTranslatableTable.class);
      // For the case, when the underlying Table was obtained from Calcite,
      // it extends neither DrillTable nor DrillTranslatableTable.
      // Therefore DistributionAffinity type cannot be determined and single mode is rejected.
      if (drillTranslatableTable == null) {
        contains = true; // it rejects single mode.
        return scan;
      }
      unwrap = drillTranslatableTable.getDrillTable();
    }

    try {
      if (unwrap.getGroupScan().getDistributionAffinity() == DistributionAffinity.HARD) {
        contains = true;
      }
    } catch (final IOException e) {
      throw new DrillRuntimeException("Failed to get GroupScan from table.");
    }
    return scan;
  }

  public boolean contains() {
    return contains;
  }
}
