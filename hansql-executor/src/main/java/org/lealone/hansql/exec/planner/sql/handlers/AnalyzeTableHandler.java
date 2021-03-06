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
import java.util.ArrayList;
import java.util.List;

import org.apache.drill.shaded.guava.com.google.common.collect.Lists;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.Path;
import org.lealone.hansql.common.exceptions.UserException;
import org.lealone.hansql.common.expression.SchemaPath;
import org.lealone.hansql.common.logical.FormatPluginConfig;
import org.lealone.hansql.exec.dotdrill.DotDrillType;
import org.lealone.hansql.exec.physical.PhysicalPlan;
import org.lealone.hansql.exec.physical.base.PhysicalOperator;
import org.lealone.hansql.exec.planner.common.DrillStatsTable;
import org.lealone.hansql.exec.planner.logical.DrillAnalyzeRel;
import org.lealone.hansql.exec.planner.logical.DrillProjectRel;
import org.lealone.hansql.exec.planner.logical.DrillRel;
import org.lealone.hansql.exec.planner.logical.DrillScanRel;
import org.lealone.hansql.exec.planner.logical.DrillScreenRel;
import org.lealone.hansql.exec.planner.logical.DrillStoreRel;
import org.lealone.hansql.exec.planner.logical.DrillTable;
import org.lealone.hansql.exec.planner.logical.DrillWriterRel;
import org.lealone.hansql.exec.planner.physical.Prel;
import org.lealone.hansql.exec.planner.sql.SchemaUtilites;
import org.lealone.hansql.exec.planner.sql.parser.SqlAnalyzeTable;
import org.lealone.hansql.exec.store.AbstractSchema;
import org.lealone.hansql.exec.store.dfs.DrillFileSystem;
import org.lealone.hansql.exec.store.dfs.FileSystemPlugin;
import org.lealone.hansql.exec.store.dfs.FormatSelection;
import org.lealone.hansql.exec.store.dfs.NamedFormatPluginConfig;
import org.lealone.hansql.exec.store.parquet.ParquetFormatConfig;
import org.lealone.hansql.exec.util.Pointer;
import org.lealone.hansql.exec.work.exception.SqlExecutorSetupException;
import org.lealone.hansql.exec.work.exception.SqlUnsupportedException;
import org.lealone.hansql.optimizer.rel.RelNode;
import org.lealone.hansql.optimizer.rel.type.RelDataType;
import org.lealone.hansql.optimizer.rel.type.RelDataTypeField;
import org.lealone.hansql.optimizer.rex.RexBuilder;
import org.lealone.hansql.optimizer.rex.RexNode;
import org.lealone.hansql.optimizer.rex.RexUtil;
import org.lealone.hansql.optimizer.schema.Table;
import org.lealone.hansql.optimizer.sql.SqlIdentifier;
import org.lealone.hansql.optimizer.sql.SqlNode;
import org.lealone.hansql.optimizer.sql.SqlNodeList;
import org.lealone.hansql.optimizer.sql.SqlSelect;
import org.lealone.hansql.optimizer.sql.parser.SqlParserPos;
import org.lealone.hansql.optimizer.tools.RelConversionException;
import org.lealone.hansql.optimizer.tools.ValidationException;

public class AnalyzeTableHandler extends DefaultSqlHandler {
  private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AnalyzeTableHandler.class);

  public AnalyzeTableHandler(SqlHandlerConfig config, Pointer<String> textPlan) {
    super(config, textPlan);
  }

  @Override
  public PhysicalPlan getPlan(SqlNode sqlNode)
      throws ValidationException, RelConversionException, IOException, SqlExecutorSetupException {
    final SqlAnalyzeTable sqlAnalyzeTable = unwrap(sqlNode, SqlAnalyzeTable.class);

    verifyNoUnsupportedFunctions(sqlAnalyzeTable);

    SqlIdentifier tableIdentifier = sqlAnalyzeTable.getTableIdentifier();
    SqlSelect scanSql = new SqlSelect(
        SqlParserPos.ZERO,              /* position */
        SqlNodeList.EMPTY,              /* keyword list */
        getColumnList(sqlAnalyzeTable), /* select list */
        tableIdentifier,                /* from */
        null,                           /* where */
        null,                           /* group by */
        null,                           /* having */
        null,                           /* windowDecls */
        null,                           /* orderBy */
        null,                           /* offset */
        null                            /* fetch */
    );

    final ConvertedRelNode convertedRelNode = validateAndConvert(rewrite(scanSql));
    final RelDataType validatedRowType = convertedRelNode.getValidatedRowType();

    final RelNode relScan = convertedRelNode.getConvertedNode();
    final String tableName = sqlAnalyzeTable.getName();
    final AbstractSchema drillSchema = SchemaUtilites.resolveToDrillSchema(
        config.getConverter().getDefaultSchema(), sqlAnalyzeTable.getSchemaPath());
    Table table = SqlHandlerUtil.getTableFromSchema(drillSchema, tableName);

    if (table == null) {
      throw UserException.validationError()
          .message("No table with given name [%s] exists in schema [%s]", tableName,
              drillSchema.getFullSchemaName())
          .build(logger);
    }

    if(! (table instanceof DrillTable)) {
      return DrillStatsTable.notSupported(context, tableName);
    }

    if (table instanceof DrillTable) {
      DrillTable drillTable = (DrillTable) table;
      final Object selection = drillTable.getSelection();
      if (!(selection instanceof FormatSelection)) {
        return DrillStatsTable.notSupported(context, tableName);
      }
      // Do not support non-parquet tables
      FormatSelection formatSelection = (FormatSelection) selection;
      FormatPluginConfig formatConfig = formatSelection.getFormat();
      if (!((formatConfig instanceof ParquetFormatConfig)
            || ((formatConfig instanceof NamedFormatPluginConfig)
                 && ((NamedFormatPluginConfig) formatConfig).name.equals("parquet")))) {
        return DrillStatsTable.notSupported(context, tableName);
      }

      FileSystemPlugin plugin = (FileSystemPlugin) drillTable.getPlugin();
      DrillFileSystem fs = new DrillFileSystem(plugin.getFormatPlugin(
          formatSelection.getFormat()).getFsConf());

      Path selectionRoot = formatSelection.getSelection().getSelectionRoot();
      if (!selectionRoot.toUri().getPath().endsWith(tableName) || !fs.getFileStatus(selectionRoot).isDirectory()) {
        return DrillStatsTable.notSupported(context, tableName);
      }
      // Do not recompute statistics, if stale
      Path statsFilePath = new Path(selectionRoot, DotDrillType.STATS.getEnding());
      if (fs.exists(statsFilePath) && !isStatsStale(fs, statsFilePath)) {
       return DrillStatsTable.notRequired(context, tableName);
      }
    }
    // Convert the query to Drill Logical plan and insert a writer operator on top.
    DrillRel drel = convertToDrel(relScan, drillSchema, tableName, sqlAnalyzeTable.getSamplePercent());
    Prel prel = convertToPrel(drel, validatedRowType);
    logAndSetTextPlan("Drill Physical", prel, logger);
    PhysicalOperator pop = convertToPop(prel);
    PhysicalPlan plan = convertToPlan(pop);
    log("Drill Plan", plan, logger);

    return plan;
  }

  /* Determines if the table was modified after computing statistics based on
   * directory/file modification timestamps
   */
  private boolean isStatsStale(DrillFileSystem fs, Path statsFilePath)
      throws IOException {
    long statsFileModifyTime = fs.getFileStatus(statsFilePath).getModificationTime();
    Path parentPath = statsFilePath.getParent();
    FileStatus directoryStatus = fs.getFileStatus(parentPath);
    // Parent directory modified after stats collection?
    return directoryStatus.getModificationTime() > statsFileModifyTime ||
        tableModified(fs, parentPath, statsFileModifyTime);
  }

  /* Determines if the table was modified after computing statistics based on
   * directory/file modification timestamps. Recursively checks sub-directories.
   */
  private boolean tableModified(DrillFileSystem fs, Path parentPath,
                                long statsModificationTime) throws IOException {
    for (final FileStatus file : fs.listStatus(parentPath)) {
      // If directory or files within it are modified
      if (file.getModificationTime() > statsModificationTime) {
        return true;
      }
      // For a directory, we should recursively check sub-directories
      if (file.isDirectory() && tableModified(fs, file.getPath(), statsModificationTime)) {
        return true;
      }
    }
    return false;
  }

  /* Generates the column list specified in the ANALYZE statement */
  private SqlNodeList getColumnList(final SqlAnalyzeTable sqlAnalyzeTable) {
    SqlNodeList columnList = sqlAnalyzeTable.getFieldList();
    if (columnList == null || columnList.size() <= 0) {
      columnList = new SqlNodeList(SqlParserPos.ZERO);
      columnList.add(new SqlIdentifier(SchemaPath.STAR_COLUMN.rootName(), SqlParserPos.ZERO));
    }
    /*final SqlNodeList columnList = new SqlNodeList(SqlParserPos.ZERO);
    final List<String> fields = sqlAnalyzeTable.getFieldNames();
    if (fields == null || fields.size() <= 0) {
      columnList.add(new SqlIdentifier(SchemaPath.STAR_COLUMN.rootName(), SqlParserPos.ZERO));
    } else {
      for(String field : fields) {
        columnList.add(new SqlIdentifier(field, SqlParserPos.ZERO));
      }
    }*/
    return columnList;
  }

  /* Converts to Drill logical plan */
  protected DrillRel convertToDrel(RelNode relNode, AbstractSchema schema, String analyzeTableName,
      double samplePercent) throws SqlUnsupportedException {
    DrillRel convertedRelNode = convertToRawDrel(relNode);

    if (convertedRelNode instanceof DrillStoreRel) {
      throw new UnsupportedOperationException();
    }

    if (convertedRelNode instanceof DrillProjectRel) {
      DrillProjectRel projectRel = (DrillProjectRel) convertedRelNode;
      DrillScanRel scanRel = findScan(projectRel);
      List<RelDataTypeField> fields = Lists.newArrayList();
      RexBuilder b = projectRel.getCluster().getRexBuilder();
      List<RexNode> projections = Lists.newArrayList();
      // Get the original scan column names - after projection pushdown they should refer to the full col names
      List<String> fieldNames = new ArrayList<>();
      List<RelDataTypeField> fieldTypes = projectRel.getRowType().getFieldList();
      for (SchemaPath colPath : scanRel.getGroupScan().getColumns()) {
        fieldNames.add(colPath.toString());
      }
      for (int i =0; i < fieldTypes.size(); i++) {
        projections.add(b.makeInputRef(projectRel, i));
      }
      // Get the projection row-types
      RelDataType newRowType = RexUtil.createStructType(projectRel.getCluster().getTypeFactory(),
              projections, fieldNames, null);
      DrillProjectRel renamedProject = DrillProjectRel.create(convertedRelNode.getCluster(),
              convertedRelNode.getTraitSet(), convertedRelNode, projections, newRowType);
      convertedRelNode = renamedProject;
    }

    final RelNode analyzeRel = new DrillAnalyzeRel(
        convertedRelNode.getCluster(), convertedRelNode.getTraitSet(), convertedRelNode, samplePercent);

    final RelNode writerRel = new DrillWriterRel(
        analyzeRel.getCluster(),
        analyzeRel.getTraitSet(),
        analyzeRel,
        schema.appendToStatsTable(analyzeTableName)
    );

    return new DrillScreenRel(writerRel.getCluster(), writerRel.getTraitSet(), writerRel);
  }

  private DrillScanRel findScan(RelNode rel) {
    if (rel instanceof DrillScanRel) {
      return (DrillScanRel) rel;
    } else {
      return findScan(rel.getInput(0));
    }
  }
  // Make sure no unsupported features in ANALYZE statement are used
  private static void verifyNoUnsupportedFunctions(final SqlAnalyzeTable analyzeTable) {
    // throw unsupported error for functions that are not yet implemented
    if (analyzeTable.getEstimate()) {
      throw UserException.unsupportedError()
          .message("Statistics estimation is not yet supported.")
          .build(logger);
    }

    if (analyzeTable.getSamplePercent() <= 0 && analyzeTable.getSamplePercent() > 100.0) {
      throw UserException.unsupportedError()
          .message("Valid sampling percent between 0-100 is not specified.")
          .build(logger);
    }
  }
}
