/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lealone.hansql.engine.index;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

import org.lealone.db.Database;
import org.lealone.db.LealoneDatabase;
import org.lealone.db.index.Index;
import org.lealone.db.index.standard.StandardSecondaryIndex;
import org.lealone.db.schema.Schema;
import org.lealone.db.table.Column;
import org.lealone.db.table.Table;
import org.lealone.hansql.common.expression.LogicalExpression;
import org.lealone.hansql.common.expression.SchemaPath;
import org.lealone.hansql.engine.storage.LealoneGroupScan;
import org.lealone.hansql.engine.storage.LealoneScanSpec;
import org.lealone.hansql.exec.physical.base.AbstractDbGroupScan;
import org.lealone.hansql.exec.physical.base.GroupScan;
import org.lealone.hansql.exec.planner.common.DrillScanRelBase;
import org.lealone.hansql.exec.planner.index.CollationContext;
import org.lealone.hansql.exec.planner.index.IndexCollection;
import org.lealone.hansql.exec.planner.index.IndexDescriptor;
import org.lealone.hansql.exec.planner.index.IndexDiscoverBase;
import org.lealone.hansql.exec.planner.index.IndexDefinition.IndexType;
import org.lealone.hansql.exec.planner.logical.DrillTable;
import org.lealone.hansql.optimizer.rel.RelFieldCollation.NullDirection;

public class LealoneIndexDiscover extends IndexDiscoverBase {

    LealoneGroupScan gs;

    public LealoneIndexDiscover(GroupScan inScan, DrillScanRelBase inScanPrel) {
        super((AbstractDbGroupScan) inScan, inScanPrel);
        gs = (LealoneGroupScan) inScan;
    }

    @Override
    @SuppressWarnings("unchecked")
    public IndexCollection getTableIndex(String tableName) {
        LealoneScanSpec spec = gs.getLealoneScanSpec();
        Database db = LealoneDatabase.getInstance().getDatabase(spec.getDbName());
        Schema schema = db.getSchema(null, spec.getSchemaName());
        Table table = schema.getTableOrView(null, spec.getTableName());
        HashSet<LealoneIndexDescriptor> indexes = new HashSet<>();
        for (Index index : table.getIndexes()) {
            if (!(index instanceof StandardSecondaryIndex))
                continue;
            ArrayList<LogicalExpression> indexCols = new ArrayList<>();
            for (Column c : index.getColumns()) {
                indexCols.add(SchemaPath.getSimplePath(c.getName()));
            }
            CollationContext cc = new CollationContext(Collections.EMPTY_MAP, Collections.EMPTY_LIST);
            IndexType type = IndexType.NATIVE_SECONDARY_INDEX;
            LealoneIndexDescriptor d = new LealoneIndexDescriptor(table, gs, indexCols, cc, indexCols,
                    Collections.EMPTY_LIST, index.getName(), table.getName(), type, NullDirection.FIRST);
            indexes.add(d);
        }
        return new LealoneIndexCollection(getOriginalScanRel(), indexes);
    }

    @Override
    public DrillTable getNativeDrillTable(IndexDescriptor idxDesc) {
        return null;
    }

}
