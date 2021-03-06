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
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.drill.shaded.guava.com.google.common.base.Preconditions;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.lealone.hansql.common.exceptions.UserException;
import org.lealone.hansql.exec.ExecConstants;
import org.lealone.hansql.exec.physical.PhysicalPlan;
import org.lealone.hansql.exec.planner.SqlPlanner;
import org.lealone.hansql.exec.planner.sql.SchemaUtilites;
import org.lealone.hansql.exec.planner.sql.parser.SqlCreateType;
import org.lealone.hansql.exec.planner.sql.parser.SqlSchema;
import org.lealone.hansql.exec.record.metadata.ColumnMetadata;
import org.lealone.hansql.exec.record.metadata.TupleMetadata;
import org.lealone.hansql.exec.record.metadata.schema.FsMetastoreSchemaProvider;
import org.lealone.hansql.exec.record.metadata.schema.PathSchemaProvider;
import org.lealone.hansql.exec.record.metadata.schema.SchemaContainer;
import org.lealone.hansql.exec.record.metadata.schema.SchemaProvider;
import org.lealone.hansql.exec.record.metadata.schema.parser.SchemaParsingException;
import org.lealone.hansql.exec.store.AbstractSchema;
import org.lealone.hansql.exec.store.StorageStrategy;
import org.lealone.hansql.exec.store.dfs.WorkspaceSchemaFactory;
import org.lealone.hansql.exec.util.ImpersonationUtil;
import org.lealone.hansql.optimizer.schema.Schema;
import org.lealone.hansql.optimizer.schema.SchemaPlus;
import org.lealone.hansql.optimizer.schema.Table;
import org.lealone.hansql.optimizer.sql.SqlNode;

/**
 * Parent class for CREATE / DROP / DESCRIBE SCHEMA handlers.
 * Contains common logic on how extract workspace, output error result.
 */
public abstract class SchemaHandler extends DefaultSqlHandler {

    static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SchemaHandler.class);

    SchemaHandler(SqlHandlerConfig config) {
        super(config);
    }

    WorkspaceSchemaFactory.WorkspaceSchema getWorkspaceSchema(List<String> tableSchema, String tableName) {
        SchemaPlus defaultSchema = config.getConverter().getDefaultSchema();
        AbstractSchema temporarySchema = SchemaUtilites.resolveToTemporarySchema(tableSchema, defaultSchema,
                context.getConfig());

        if (context.getSession().isTemporaryTable(temporarySchema, context.getConfig(), tableName)) {
            produceErrorResult(String.format("Indicated table [%s] is temporary table", tableName), true);
        }

        AbstractSchema drillSchema = SchemaUtilites.resolveToMutableDrillSchema(defaultSchema, tableSchema);
        Table table = SqlHandlerUtil.getTableFromSchema(drillSchema, tableName);
        if (table == null || table.getJdbcTableType() != Schema.TableType.TABLE) {
            produceErrorResult(String.format("Table [%s] was not found", tableName), true);
        }

        if (!(drillSchema instanceof WorkspaceSchemaFactory.WorkspaceSchema)) {
            produceErrorResult(String.format("Table [`%s`.`%s`] must belong to file storage plugin",
                    drillSchema.getFullSchemaName(), tableName), true);
        }

        Preconditions.checkState(drillSchema instanceof WorkspaceSchemaFactory.WorkspaceSchema);
        return (WorkspaceSchemaFactory.WorkspaceSchema) drillSchema;
    }

    PhysicalPlan produceErrorResult(String message, boolean doFail) {
        if (doFail) {
            throw UserException.validationError().message(message).build(logger);
        } else {
            return SqlPlanner.createDirectPlan(context, false, message);
        }
    }

    /**
     * CREATE SCHEMA command handler.
     */
    public static class Create extends SchemaHandler {

        public Create(SqlHandlerConfig config) {
            super(config);
        }

        @Override
        public PhysicalPlan getPlan(SqlNode sqlNode) {

            SqlSchema.Create sqlCall = ((SqlSchema.Create) sqlNode);

            String schemaString = getSchemaString(sqlCall);
            String schemaSource = sqlCall.hasTable() ? sqlCall.getTable().toString() : sqlCall.getPath();
            try {

                SchemaProvider schemaProvider;
                if (sqlCall.hasTable()) {
                    String tableName = sqlCall.getTableName();
                    WorkspaceSchemaFactory.WorkspaceSchema wsSchema = getWorkspaceSchema(sqlCall.getSchemaPath(),
                            tableName);
                    schemaProvider = new FsMetastoreSchemaProvider(wsSchema, tableName);
                } else {
                    schemaProvider = new PathSchemaProvider(new Path(sqlCall.getPath()));
                }

                if (schemaProvider.exists()) {
                    if (SqlCreateType.OR_REPLACE == sqlCall.getSqlCreateType()) {
                        schemaProvider.delete();
                    } else {
                        return produceErrorResult(String.format("Schema already exists for [%s]", schemaSource), true);
                    }
                }

                // schema file will be created with same permission as used for persistent tables
                StorageStrategy storageStrategy = new StorageStrategy(
                        context.getOption(ExecConstants.PERSISTENT_TABLE_UMASK).string_val, false);
                schemaProvider.store(schemaString, sqlCall.getProperties(), storageStrategy);
                return SqlPlanner.createDirectPlan(context, true,
                        String.format("Created schema for [%s]", schemaSource));
            } catch (SchemaParsingException e) {
                throw UserException.parseError(e).message(e.getMessage()).addContext("Schema: " + schemaString)
                        .build(logger);
            } catch (IOException e) {
                throw UserException.resourceError(e).message(e.getMessage())
                        .addContext("Error while preparing / creating schema for [%s]", schemaSource).build(logger);
            } catch (IllegalArgumentException e) {
                throw UserException.validationError(e).message(e.getMessage())
                        .addContext("Error while preparing / creating schema for [%s]", schemaSource).build(logger);
            }
        }

        /**
         * If raw schema was present in create schema command, returns schema from command,
         * otherwise loads raw schema from the given file.
         *
         * @param sqlCall sql create schema call
         * @return string representation of raw schema (column names, types and nullability)
         */
        private String getSchemaString(SqlSchema.Create sqlCall) {
            if (sqlCall.hasSchema()) {
                return sqlCall.getSchema();
            }

            Path path = new Path(sqlCall.getLoad());
            try {
                FileSystem rawFs = path.getFileSystem(new Configuration());
                FileSystem fs = ImpersonationUtil.createFileSystem(ImpersonationUtil.getProcessUserName(),
                        rawFs.getConf());

                if (!fs.exists(path)) {
                    throw UserException.resourceError()
                            .message("File with raw schema [%s] does not exist", path.toUri().getPath()).build(logger);
                }

                try (InputStream stream = fs.open(path)) {
                    return IOUtils.toString(stream);
                }

            } catch (IOException e) {
                throw UserException.resourceError(e)
                        .message("Unable to load raw schema from file %s", path.toUri().getPath()).build(logger);
            }
        }
    }

    /**
     * DROP SCHEMA command handler.
     */
    public static class Drop extends SchemaHandler {

        public Drop(SqlHandlerConfig config) {
            super(config);
        }

        @Override
        public PhysicalPlan getPlan(SqlNode sqlNode) {
            SqlSchema.Drop sqlCall = ((SqlSchema.Drop) sqlNode);

            String tableName = sqlCall.getTableName();
            WorkspaceSchemaFactory.WorkspaceSchema wsSchema = getWorkspaceSchema(sqlCall.getSchemaPath(), tableName);

            try {

                SchemaProvider schemaProvider = new FsMetastoreSchemaProvider(wsSchema, tableName);

                if (!schemaProvider.exists()) {
                    return produceErrorResult(String.format("Schema [%s] does not exist in table [%s] root directory",
                            SchemaProvider.DEFAULT_SCHEMA_NAME, sqlCall.getTable()), !sqlCall.ifExists());
                }

                schemaProvider.delete();

                return SqlPlanner.createDirectPlan(context, true,
                        String.format("Dropped schema for table [%s]", sqlCall.getTable()));

            } catch (IOException e) {
                throw UserException.resourceError(e).message(e.getMessage())
                        .addContext("Error while accessing table location or deleting schema for [%s]",
                                sqlCall.getTable())
                        .build(logger);
            }
        }
    }

    /**
     * DESCRIBE SCHEMA FOR TABLE command handler.
     */
    public static class Describe extends SchemaHandler {

        public Describe(SqlHandlerConfig config) {
            super(config);
        }

        @Override
        public PhysicalPlan getPlan(SqlNode sqlNode) {
            SqlSchema.Describe sqlCall = ((SqlSchema.Describe) sqlNode);

            String tableName = sqlCall.getTableName();
            WorkspaceSchemaFactory.WorkspaceSchema wsSchema = getWorkspaceSchema(sqlCall.getSchemaPath(), tableName);

            try {

                SchemaProvider schemaProvider = new FsMetastoreSchemaProvider(wsSchema, tableName);

                if (schemaProvider.exists()) {

                    SchemaContainer schemaContainer = schemaProvider.read();

                    String schema;
                    switch (sqlCall.getFormat()) {
                    case JSON:
                        schema = PathSchemaProvider.WRITER.writeValueAsString(schemaContainer);
                        break;
                    case STATEMENT:
                        TupleMetadata metadata = schemaContainer.getSchema();
                        StringBuilder builder = new StringBuilder("CREATE OR REPLACE SCHEMA \n");
                        builder.append("(\n");

                        builder.append(metadata.toMetadataList().stream().map(ColumnMetadata::columnString)
                                .collect(Collectors.joining(", \n")));

                        builder.append("\n) \n");

                        builder.append("FOR TABLE ").append(schemaContainer.getTable()).append(" \n");

                        Map<String, String> properties = metadata.properties();
                        if (!properties.isEmpty()) {
                            builder.append("PROPERTIES (\n");

                            builder.append(properties.entrySet().stream()
                                    .map(e -> String.format("'%s' = '%s'", e.getKey(), e.getValue()))
                                    .collect(Collectors.joining(", \n")));
                            builder.append("\n)");
                        }

                        schema = builder.toString();
                        break;
                    default:
                        throw UserException.validationError()
                                .message("Unsupported describe schema format: [%s]", sqlCall.getFormat()).build(logger);
                    }

                    return SqlPlanner.createDirectPlan(context, new SchemaResult(schema));
                }

                return SqlPlanner.createDirectPlan(context, false,
                        String.format("Schema for table [%s] is absent", sqlCall.getTable()));

            } catch (IOException e) {
                throw UserException.resourceError(e).message(e.getMessage())
                        .addContext("Error while accessing schema for table [%s]", sqlCall.getTable()).build(logger);
            }
        }

        /**
         * Wrapper to output schema in a form of table with one column named `schema`.
         */
        public static class SchemaResult {

            public String schema;

            public SchemaResult(String schema) {
                this.schema = schema;
            }
        }

    }

}
