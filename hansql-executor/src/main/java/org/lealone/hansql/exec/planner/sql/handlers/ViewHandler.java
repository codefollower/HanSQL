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

import java.io.IOException;

import org.lealone.hansql.common.exceptions.UserException;
import org.lealone.hansql.exec.dotdrill.View;
import org.lealone.hansql.exec.ops.QueryContext;
import org.lealone.hansql.exec.physical.PhysicalPlan;
import org.lealone.hansql.exec.planner.SqlPlanner;
import org.lealone.hansql.exec.planner.sql.SchemaUtilites;
import org.lealone.hansql.exec.planner.sql.parser.SqlCreateView;
import org.lealone.hansql.exec.planner.sql.parser.SqlDropView;
import org.lealone.hansql.exec.store.AbstractSchema;
import org.lealone.hansql.exec.store.dfs.FileSelection;
import org.lealone.hansql.exec.work.exception.SqlExecutorSetupException;
import org.lealone.hansql.optimizer.rel.RelNode;
import org.lealone.hansql.optimizer.rel.type.RelDataType;
import org.lealone.hansql.optimizer.schema.Schema;
import org.lealone.hansql.optimizer.schema.SchemaPlus;
import org.lealone.hansql.optimizer.schema.Table;
import org.lealone.hansql.optimizer.sql.SqlNode;
import org.lealone.hansql.optimizer.tools.RelConversionException;
import org.lealone.hansql.optimizer.tools.ValidationException;

public abstract class ViewHandler extends DefaultSqlHandler {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ViewHandler.class);

    protected QueryContext context;

    public ViewHandler(SqlHandlerConfig config) {
        super(config);
        this.context = config.getContext();
    }

    /** Handler for Create View DDL command */
    public static class CreateView extends ViewHandler {

        public CreateView(SqlHandlerConfig config) {
            super(config);
        }

        @Override
        public PhysicalPlan getPlan(SqlNode sqlNode)
                throws ValidationException, RelConversionException, IOException, SqlExecutorSetupException {
            SqlCreateView createView = unwrap(sqlNode, SqlCreateView.class);

            final String newViewName = FileSelection.removeLeadingSlash(createView.getName());

            // Disallow temporary tables usage in view definition
            config.getConverter().disallowTemporaryTables();
            // Store the viewSql as view def SqlNode is modified as part of the resolving the new table definition
            // below.
            final String viewSql = createView.getQuery().toString();
            final ConvertedRelNode convertedRelNode = validateAndConvert(createView.getQuery());
            final RelDataType validatedRowType = convertedRelNode.getValidatedRowType();
            final RelNode queryRelNode = convertedRelNode.getConvertedNode();

            final RelNode newViewRelNode = SqlHandlerUtil.resolveNewTableRel(true, createView.getFieldNames(),
                    validatedRowType, queryRelNode);

            final SchemaPlus defaultSchema = context.getNewDefaultSchema();
            final AbstractSchema drillSchema = SchemaUtilites.resolveToMutableDrillSchema(defaultSchema,
                    createView.getSchemaPath());

            final View view = new View(newViewName, viewSql, newViewRelNode.getRowType(),
                    SchemaUtilites.getSchemaPathAsList(defaultSchema));
            final String schemaPath = drillSchema.getFullSchemaName();

            // check view creation possibility
            if (!checkViewCreationPossibility(drillSchema, createView, context)) {
                return SqlPlanner.createDirectPlan(context, false,
                        String.format("A table or view with given name [%s] already exists in schema [%s]",
                                view.getName(), schemaPath));
            }

            final boolean replaced = drillSchema.createView(view);
            final String summary = String.format("View '%s' %s successfully in '%s' schema", newViewName,
                    replaced ? "replaced" : "created", drillSchema.getFullSchemaName());

            return SqlPlanner.createDirectPlan(context, true, summary);
        }

        /**
         * Validates if view can be created in indicated schema:
         * checks if object (persistent / temporary table) with the same name exists
         * or if view with the same name exists but replace flag is not set
         * or if object with the same name exists but if not exists flag is set.
         *
         * @param drillSchema schema where views will be created
         * @param view create view call
         * @param context query context
         * @return if view can be created in indicated schema
         * @throws UserException if view cannot be created in indicated schema and no duplicate check requested
         */
        private boolean checkViewCreationPossibility(AbstractSchema drillSchema, SqlCreateView view,
                QueryContext context) {
            final String schemaPath = drillSchema.getFullSchemaName();
            final String viewName = view.getName();
            final Table table = SqlHandlerUtil.getTableFromSchema(drillSchema, viewName);

            final boolean isTable = (table != null && table.getJdbcTableType() != Schema.TableType.VIEW)
                    || context.getSession().isTemporaryTable(drillSchema, context.getConfig(), viewName);
            final boolean isView = (table != null && table.getJdbcTableType() == Schema.TableType.VIEW);

            switch (view.getSqlCreateType()) {
            case SIMPLE:
                if (isTable) {
                    throw UserException.validationError()
                            .message("A non-view table with given name [%s] already exists in schema [%s]", viewName,
                                    schemaPath)
                            .build(logger);
                } else if (isView) {
                    throw UserException.validationError()
                            .message("A view with given name [%s] already exists in schema [%s]", viewName, schemaPath)
                            .build(logger);
                }
                break;
            case OR_REPLACE:
                if (isTable) {
                    throw UserException.validationError()
                            .message("A non-view table with given name [%s] already exists in schema [%s]", viewName,
                                    schemaPath)
                            .build(logger);
                }
                break;
            case IF_NOT_EXISTS:
                if (isTable || isView) {
                    return false;
                }
                break;
            }
            return true;
        }

    }

    /** Handler for Drop View [If Exists] DDL command. */
    public static class DropView extends ViewHandler {
        public DropView(SqlHandlerConfig config) {
            super(config);
        }

        @Override
        public PhysicalPlan getPlan(SqlNode sqlNode) throws IOException, SqlExecutorSetupException {
            SqlDropView dropView = unwrap(sqlNode, SqlDropView.class);
            final String viewName = FileSelection.removeLeadingSlash(dropView.getName());
            final AbstractSchema drillSchema = SchemaUtilites.resolveToMutableDrillSchema(context.getNewDefaultSchema(),
                    dropView.getSchemaPath());

            final String schemaPath = drillSchema.getFullSchemaName();

            final Table viewToDrop = SqlHandlerUtil.getTableFromSchema(drillSchema, viewName);
            if (dropView.checkViewExistence()) {
                if (viewToDrop == null || viewToDrop.getJdbcTableType() != Schema.TableType.VIEW) {
                    return SqlPlanner.createDirectPlan(context, false,
                            String.format("View [%s] not found in schema [%s].", viewName, schemaPath));
                }
            } else {
                if (viewToDrop != null && viewToDrop.getJdbcTableType() != Schema.TableType.VIEW) {
                    throw UserException.validationError()
                            .message("[%s] is not a VIEW in schema [%s]", viewName, schemaPath).build(logger);
                } else if (viewToDrop == null) {
                    throw UserException.validationError()
                            .message("Unknown view [%s] in schema [%s].", viewName, schemaPath).build(logger);
                }
            }

            SqlHandlerUtil.dropViewFromSchema(drillSchema, viewName);

            return SqlPlanner.createDirectPlan(context, true,
                    String.format("View [%s] deleted successfully from schema [%s].", viewName, schemaPath));
        }
    }
}
