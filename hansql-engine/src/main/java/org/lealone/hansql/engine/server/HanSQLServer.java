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
package org.lealone.hansql.engine.server;

import java.util.Map;

import org.lealone.common.logging.Logger;
import org.lealone.common.logging.LoggerFactory;
import org.lealone.db.LealoneDatabase;
import org.lealone.hansql.common.config.DrillConfig;
import org.lealone.hansql.engine.HanEngine;
import org.lealone.hansql.exec.store.StoragePlugin;
import org.lealone.net.AsyncConnectionManager;
import org.lealone.server.TcpServer;

public class HanSQLServer extends TcpServer implements AsyncConnectionManager {

    private static final Logger logger = LoggerFactory.getLogger(HanSQLServer.class);

    private HanEngine hanEngine;

    public HanEngine getHanEngine() {
        return hanEngine;
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @Override
    public String getType() {
        return HanSQLServerEngine.NAME;
    }

    @Override
    public synchronized void start() {
        if (isStarted())
            return;
        super.start();

        try {
            startHanEngine();
        } catch (Exception e) {
            logger.error("Failed to start engine", e);
        }
    }

    @Override
    public synchronized void stop() {
        if (isStopped())
            return;
        super.stop();
        hanEngine.shutdown();
    }

    private void startHanEngine() throws Exception {
        // ?????????org.apache.calcite.rel.metadata.JaninoRelMetadataProvider???????????????
        // System.setProperty("calcite.debug", "true");

        DrillConfig drillConfig = DrillConfig.create();
        hanEngine = new HanEngine(drillConfig);
        hanEngine.setHostName(getHost());
        hanEngine.run();

        for (Map.Entry<String, StoragePlugin> e : hanEngine.getStoragePluginRegistry()) {
            LealoneDatabase.addUnsupportedSchema(e.getKey());
        }
    }
}
