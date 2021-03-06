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
package org.lealone.hansql.exec.store.parquet;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.lealone.hansql.exec.store.RecordWriter;
import org.apache.drill.shaded.guava.com.google.common.base.Stopwatch;
import org.apache.drill.shaded.guava.com.google.common.collect.ImmutableSet;
import org.apache.drill.shaded.guava.com.google.common.collect.Lists;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.format.converter.ParquetMetadataConverter;
import org.apache.parquet.hadoop.ParquetFileWriter;
import org.lealone.hansql.common.exceptions.ExecutionSetupException;
import org.lealone.hansql.common.expression.SchemaPath;
import org.lealone.hansql.common.logical.StoragePluginConfig;
import org.lealone.hansql.exec.ExecConstants;
import org.lealone.hansql.exec.context.DrillbitContext;
import org.lealone.hansql.exec.context.options.OptionManager;
import org.lealone.hansql.exec.exception.OutOfMemoryException;
import org.lealone.hansql.exec.ops.FragmentContext;
import org.lealone.hansql.exec.physical.base.AbstractFileGroupScan;
import org.lealone.hansql.exec.physical.base.AbstractWriter;
import org.lealone.hansql.exec.physical.base.MetadataProviderManager;
import org.lealone.hansql.exec.physical.base.PhysicalOperator;
import org.lealone.hansql.exec.physical.base.SchemalessScan;
import org.lealone.hansql.exec.physical.impl.WriterRecordBatch;
import org.lealone.hansql.exec.planner.common.DrillStatsTable;
import org.lealone.hansql.exec.planner.common.DrillStatsTable.TableStatistics;
import org.lealone.hansql.exec.planner.logical.DrillTable;
import org.lealone.hansql.exec.planner.logical.DynamicDrillTable;
import org.lealone.hansql.exec.proto.ExecProtos.FragmentHandle;
import org.lealone.hansql.exec.record.RecordBatch;
import org.lealone.hansql.exec.store.SchemaConfig;
import org.lealone.hansql.exec.store.StoragePluginOptimizerRule;
import org.lealone.hansql.exec.store.dfs.BasicFormatMatcher;
import org.lealone.hansql.exec.store.dfs.DrillFileSystem;
import org.lealone.hansql.exec.store.dfs.FileSelection;
import org.lealone.hansql.exec.store.dfs.FileSystemPlugin;
import org.lealone.hansql.exec.store.dfs.FormatMatcher;
import org.lealone.hansql.exec.store.dfs.FormatPlugin;
import org.lealone.hansql.exec.store.dfs.FormatSelection;
import org.lealone.hansql.exec.store.dfs.MagicString;
import org.lealone.hansql.exec.store.dfs.MetadataContext;
import org.lealone.hansql.exec.store.parquet.metadata.Metadata;
import org.lealone.hansql.exec.store.parquet.metadata.ParquetTableMetadataDirs;
import org.lealone.hansql.exec.util.DrillFileSystemUtil;

public class ParquetFormatPlugin implements FormatPlugin {

  private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ParquetFormatPlugin.class);

  public static final ParquetMetadataConverter parquetMetadataConverter = new ParquetMetadataConverter();

  private static final String DEFAULT_NAME = "parquet";

  private static final List<Pattern> PATTERNS = Lists.newArrayList(
      Pattern.compile(".*\\.parquet$"),
      Pattern.compile(".*/" + ParquetFileWriter.PARQUET_METADATA_FILE));
  private static final List<MagicString> MAGIC_STRINGS = Lists.newArrayList(new MagicString(0, ParquetFileWriter.MAGIC));

  private final DrillbitContext context;
  private final Configuration fsConf;
  private final ParquetFormatMatcher formatMatcher;
  private final ParquetFormatConfig config;
  private final StoragePluginConfig storageConfig;
  private final String name;

  public ParquetFormatPlugin(String name, DrillbitContext context, Configuration fsConf,
      StoragePluginConfig storageConfig){
    this(name, context, fsConf, storageConfig, new ParquetFormatConfig());
  }

  public ParquetFormatPlugin(String name, DrillbitContext context, Configuration fsConf,
      StoragePluginConfig storageConfig, ParquetFormatConfig formatConfig){
    this.context = context;
    this.config = formatConfig;
    this.formatMatcher = new ParquetFormatMatcher(this, config);
    this.storageConfig = storageConfig;
    this.fsConf = fsConf;
    this.name = name == null ? DEFAULT_NAME : name;
  }

  @Override
  public Configuration getFsConf() {
    return fsConf;
  }

  @Override
  public ParquetFormatConfig getConfig() {
    return config;
  }

  public DrillbitContext getContext() {
    return this.context;
  }

  @Override
  public boolean supportsRead() {
    return true;
  }

  @Override
  public Set<StoragePluginOptimizerRule> getOptimizerRules() {
    return ImmutableSet.of();
  }

  @Override
  public AbstractWriter getWriter(PhysicalOperator child, String location, List<String> partitionColumns) throws IOException {
    return new ParquetWriter(child, location, partitionColumns, this);
  }

  public RecordWriter getRecordWriter(FragmentContext context, ParquetWriter writer) throws IOException, OutOfMemoryException {
    Map<String, String> options = new HashMap<>();

    options.put("location", writer.getLocation());

    FragmentHandle handle = context.getHandle();
    String fragmentId = String.format("%d_%d", handle.getMajorFragmentId(), handle.getMinorFragmentId());
    options.put("prefix", fragmentId);

    options.put(ExecConstants.PARQUET_BLOCK_SIZE, context.getOptions().getOption(ExecConstants.PARQUET_BLOCK_SIZE).num_val.toString());
    options.put(ExecConstants.PARQUET_WRITER_USE_SINGLE_FS_BLOCK,
      context.getOptions().getOption(ExecConstants.PARQUET_WRITER_USE_SINGLE_FS_BLOCK).bool_val.toString());
    options.put(ExecConstants.PARQUET_PAGE_SIZE, context.getOptions().getOption(ExecConstants.PARQUET_PAGE_SIZE).num_val.toString());
    options.put(ExecConstants.PARQUET_DICT_PAGE_SIZE, context.getOptions().getOption(ExecConstants.PARQUET_DICT_PAGE_SIZE).num_val.toString());

    options.put(ExecConstants.PARQUET_WRITER_COMPRESSION_TYPE,
        context.getOptions().getOption(ExecConstants.PARQUET_WRITER_COMPRESSION_TYPE).string_val);

    options.put(ExecConstants.PARQUET_WRITER_ENABLE_DICTIONARY_ENCODING,
        context.getOptions().getOption(ExecConstants.PARQUET_WRITER_ENABLE_DICTIONARY_ENCODING).bool_val.toString());

    options.put(ExecConstants.PARQUET_WRITER_LOGICAL_TYPE_FOR_DECIMALS,
        context.getOptions().getOption(ExecConstants.PARQUET_WRITER_LOGICAL_TYPE_FOR_DECIMALS).string_val);

    options.put(ExecConstants.PARQUET_WRITER_USE_PRIMITIVE_TYPES_FOR_DECIMALS,
        context.getOptions().getOption(ExecConstants.PARQUET_WRITER_USE_PRIMITIVE_TYPES_FOR_DECIMALS).bool_val.toString());

    RecordWriter recordWriter = new ParquetRecordWriter(context, writer);
    recordWriter.init(options);

    return recordWriter;
  }

  public WriterRecordBatch getWriterBatch(FragmentContext context, RecordBatch incoming, ParquetWriter writer)
          throws ExecutionSetupException {
    try {
      return new WriterRecordBatch(writer, incoming, context, getRecordWriter(context, writer));
    } catch(IOException e) {
      throw new ExecutionSetupException(String.format("Failed to create the WriterRecordBatch. %s", e.getMessage()), e);
    }
  }

  @Override
  public AbstractFileGroupScan getGroupScan(String userName, FileSelection selection, List<SchemaPath> columns) throws IOException {
    return getGroupScan(userName, selection, columns, (OptionManager) null);
  }

  @Override
  public AbstractFileGroupScan getGroupScan(String userName, FileSelection selection, List<SchemaPath> columns, OptionManager options) throws IOException {
    return getGroupScan(userName, selection, columns, options, null);
  }

  @Override
  public AbstractFileGroupScan getGroupScan(String userName, FileSelection selection,
      List<SchemaPath> columns, OptionManager options, MetadataProviderManager metadataProviderManager) throws IOException {
    ParquetReaderConfig readerConfig = ParquetReaderConfig.builder()
        .withFormatConfig(getConfig())
        .withOptions(options)
        .build();
    ParquetGroupScan parquetGroupScan = new ParquetGroupScan(userName, selection, this, columns, readerConfig, metadataProviderManager);
    if (parquetGroupScan.getEntries().isEmpty()) {
      // If ParquetGroupScan does not contain any entries, it means selection directories are empty and
      // metadata cache files are invalid, return schemaless scan
      return new SchemalessScan(userName, parquetGroupScan.getSelectionRoot());
    }
    return parquetGroupScan;
  }

  @Override
  public boolean supportsStatistics() {
    return true;
  }

  @Override
  public TableStatistics readStatistics(FileSystem fs, Path statsTablePath) throws IOException {
    Stopwatch timer = Stopwatch.createStarted();
    ObjectMapper mapper = DrillStatsTable.getMapper();
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    FSDataInputStream is = fs.open(statsTablePath);
    TableStatistics statistics = mapper.readValue((InputStream) is, TableStatistics.class);
    logger.info("Took {} ms to read statistics from {} format plugin", timer.elapsed(TimeUnit.MILLISECONDS), name);
    timer.stop();
    return statistics;
  }

  @Override
  public void writeStatistics(TableStatistics statistics, FileSystem fs, Path statsTablePath) throws IOException {
    throw new UnsupportedOperationException("unimplemented");
  }

  @Override
  public StoragePluginConfig getStorageConfig() {
    return storageConfig;
  }

  public String getName(){
    return name;
  }

  @Override
  public boolean supportsWrite() {
    return false;
  }

  @Override
  public boolean supportsAutoPartitioning() {
    return true;
  }


  @Override
  public FormatMatcher getMatcher() {
    return formatMatcher;
  }

  private static class ParquetFormatMatcher extends BasicFormatMatcher {

    private final ParquetFormatConfig formatConfig;

    ParquetFormatMatcher(ParquetFormatPlugin plugin, ParquetFormatConfig formatConfig) {
      super(plugin, PATTERNS, MAGIC_STRINGS);
      this.formatConfig = formatConfig;
    }

    @Override
    public boolean supportDirectoryReads() {
      return true;
    }

    @Override
    public DrillTable isReadable(DrillFileSystem fs, FileSelection selection,
        FileSystemPlugin fsPlugin, String storageEngineName, SchemaConfig schemaConfig)
        throws IOException {
      if (selection.containsDirectories(fs)) {
        Path dirMetaPath = new Path(selection.getSelectionRoot(), Metadata.METADATA_DIRECTORIES_FILENAME);
        // check if the metadata 'directories' file exists; if it does, there is an implicit assumption that
        // the directory is readable since the metadata 'directories' file cannot be created otherwise.  Note
        // that isDirReadable() does a similar check with the metadata 'cache' file.
        if (fs.exists(dirMetaPath)) {
          // create a metadata context that will be used for the duration of the query for this table
          MetadataContext metaContext = new MetadataContext();

          ParquetReaderConfig readerConfig = ParquetReaderConfig.builder().withFormatConfig(formatConfig).build();
          ParquetTableMetadataDirs mDirs = Metadata.readMetadataDirs(fs, dirMetaPath, metaContext, readerConfig);
          if (mDirs != null && mDirs.getDirectories().size() > 0) {
            metaContext.setDirectories(mDirs.getDirectories());
            FileSelection dirSelection = FileSelection.createFromDirectories(mDirs.getDirectories(), selection,
                selection.getSelectionRoot() /* cacheFileRoot initially points to selectionRoot */);
            dirSelection.setExpandedPartial();
            dirSelection.setMetaContext(metaContext);

            return new DynamicDrillTable(fsPlugin, storageEngineName, schemaConfig.getUserName(),
                new FormatSelection(plugin.getConfig(), dirSelection));
          }
        }
        if (isDirReadable(fs, selection.getFirstPath(fs))) {
          return new DynamicDrillTable(fsPlugin, storageEngineName, schemaConfig.getUserName(),
              new FormatSelection(plugin.getConfig(), selection));
        }
      }
      /*if (!super.supportDirectoryReads() && selection.containsDirectories(fs)) {
        return null;
      }*/
      return super.isReadable(fs, selection, fsPlugin, storageEngineName, schemaConfig);
    }

    private Path getOldMetadataPath(FileStatus dir) {
      return new Path(dir.getPath(), Metadata.OLD_METADATA_FILENAME);
    }

    /**
     * Check if the metadata cache files exist
     * @param dir the path of the directory
     * @param fs
     * @return true if both file metadata and summary cache file exist
     * @throws IOException in case of problems during accessing files
     */
    private boolean metaDataFileExists(FileSystem fs, FileStatus dir) throws IOException {
      boolean fileExists = true;
      for (String metaFileName : Metadata.CURRENT_METADATA_FILENAMES) {
        Path path = new Path(dir.getPath(), metaFileName);
        if (!fs.exists(path)) {
          fileExists = false;
        }
      }
      if (fileExists) {
        return true;
      } else {
        // Check if the older version of metadata file exists
        if (fs.exists(getOldMetadataPath(dir))) {
          return true;
        }
      }
      return false;
    }

    boolean isDirReadable(DrillFileSystem fs, FileStatus dir) {
      Path p = new Path(dir.getPath(), ParquetFileWriter.PARQUET_METADATA_FILE);
      try {
        if (fs.exists(p)) {
          return true;
        } else {

          if (metaDataFileExists(fs, dir)) {
            return true;
          }
          List<FileStatus> statuses = DrillFileSystemUtil.listFiles(fs, dir.getPath(), false);
          return !statuses.isEmpty() && super.isFileReadable(fs, statuses.get(0));
        }
      } catch (IOException e) {
        logger.info("Failure while attempting to check for Parquet metadata file.", e);
        return false;
      }
    }
  }
}
