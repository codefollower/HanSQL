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
package org.lealone.hansql.engine.storage;

import java.util.LinkedList;
import java.util.List;

import org.apache.drill.shaded.guava.com.google.common.base.Preconditions;
import org.lealone.hansql.common.exceptions.ExecutionSetupException;
import org.lealone.hansql.engine.storage.LealoneSubScan.LealoneSubScanSpec;
import org.lealone.hansql.exec.ops.ExecutorFragmentContext;
import org.lealone.hansql.exec.physical.impl.BatchCreator;
import org.lealone.hansql.exec.physical.impl.ScanBatch;
import org.lealone.hansql.exec.record.RecordBatch;
import org.lealone.hansql.exec.store.RecordReader;

public class LealoneScanBatchCreator implements BatchCreator<LealoneSubScan> {
    @Override
    public ScanBatch getBatch(ExecutorFragmentContext context, LealoneSubScan config, List<RecordBatch> children)
            throws ExecutionSetupException {
        Preconditions.checkArgument(children.isEmpty());
        List<RecordReader> readers = new LinkedList<>();
        // List<SchemaPath> columns = null;

        for (LealoneSubScanSpec scanSpec : config.getTabletScanSpecList()) {
            try {
                // if ((columns = scanSpec.getColumns())==null) {
                // columns = GroupScan.ALL_COLUMNS;
                // }

                RecordReader reader = new LealoneRecordReader(context, scanSpec.getScanSpec(), config, null);
                readers.add(reader);
            } catch (Exception e1) {
                throw new ExecutionSetupException(e1);
            }
        }
        return new ScanBatch(config, context, readers);
    }
}
