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

import java.util.List;

import org.lealone.hansql.common.config.DrillConfig;
import org.lealone.hansql.common.exceptions.UserException;
import org.lealone.hansql.exec.physical.PhysicalPlan;
import org.lealone.hansql.exec.planner.SqlPlanner;
import org.lealone.hansql.exec.planner.sql.SchemaUtilites;
import org.lealone.hansql.exec.planner.sql.parser.SqlDropTable;
import org.lealone.hansql.exec.session.UserSession;
import org.lealone.hansql.exec.store.AbstractSchema;
import org.lealone.hansql.exec.store.dfs.FileSelection;
import org.lealone.hansql.optimizer.schema.Schema;
import org.lealone.hansql.optimizer.schema.SchemaPlus;
import org.lealone.hansql.optimizer.schema.Table;
import org.lealone.hansql.optimizer.sql.SqlNode;

// SqlHandler for dropping a table.
public class DropTableHandler extends DefaultSqlHandler {

    private static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(DropTableHandler.class);

    public DropTableHandler(SqlHandlerConfig config) {
        super(config);
    }

    /**
     * Function resolves the schema and invokes the drop method
     * (while IF EXISTS statement is used function invokes the drop method only if table exists).
     * Raises an exception if the schema is immutable.
     *
     * @param sqlNode - SqlDropTable (SQL parse tree of drop table [if exists] query)
     * @return - Single row indicating drop succeeded or table is not found while IF EXISTS statement is used,
     * raise exception otherwise
     */
    @Override
    public PhysicalPlan getPlan(SqlNode sqlNode) {
        SqlDropTable dropTableNode = ((SqlDropTable) sqlNode);
        String originalTableName = FileSelection.removeLeadingSlash(dropTableNode.getName());
        SchemaPlus defaultSchema = config.getConverter().getDefaultSchema();
        List<String> tableSchema = dropTableNode.getSchema();
        DrillConfig drillConfig = context.getConfig();
        UserSession session = context.getSession();

        AbstractSchema temporarySchema = SchemaUtilites.resolveToTemporarySchema(tableSchema, defaultSchema,
                drillConfig);
        boolean isTemporaryTable = session.isTemporaryTable(temporarySchema, drillConfig, originalTableName);

        if (isTemporaryTable) {
            session.removeTemporaryTable(temporarySchema, originalTableName, drillConfig);
        } else {
            AbstractSchema drillSchema = SchemaUtilites.resolveToMutableDrillSchema(defaultSchema, tableSchema);
            Table tableToDrop = SqlHandlerUtil.getTableFromSchema(drillSchema, originalTableName);
            if (tableToDrop == null || tableToDrop.getJdbcTableType() != Schema.TableType.TABLE) {
                if (dropTableNode.checkTableExistence()) {
                    return SqlPlanner.createDirectPlan(context, false,
                            String.format("Table [%s] not found", originalTableName));
                } else {
                    throw UserException.validationError().message("Table [%s] not found", originalTableName)
                            .build(logger);
                }
            }
            SqlHandlerUtil.dropTableFromSchema(drillSchema, originalTableName);
        }

        String message = String.format("%s [%s] dropped", isTemporaryTable ? "Temporary table" : "Table",
                originalTableName);
        logger.info(message);
        return SqlPlanner.createDirectPlan(context, true, message);
    }

}
