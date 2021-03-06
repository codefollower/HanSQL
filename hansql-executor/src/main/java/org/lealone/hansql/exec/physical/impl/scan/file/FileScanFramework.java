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
package org.lealone.hansql.exec.physical.impl.scan.file;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapred.FileSplit;
import org.lealone.hansql.common.exceptions.UserException;
import org.lealone.hansql.exec.physical.impl.scan.file.FileMetadataManager.FileMetadataOptions;
import org.lealone.hansql.exec.physical.impl.scan.framework.ManagedReader;
import org.lealone.hansql.exec.physical.impl.scan.framework.ManagedScanFramework;
import org.lealone.hansql.exec.physical.impl.scan.framework.SchemaNegotiator;
import org.lealone.hansql.exec.physical.impl.scan.framework.SchemaNegotiatorImpl;
import org.lealone.hansql.exec.physical.impl.scan.framework.ShimBatchReader;
import org.lealone.hansql.exec.store.dfs.DrillFileSystem;
import org.lealone.hansql.exec.store.dfs.easy.FileWork;

/**
 * The file scan framework adds into the scan framework support for implicit
 * reading from DFS splits (a file and a block). Since this framework is
 * file-based, it also adds support for file metadata (AKA implicit columns.
 * The file scan framework brings together a number of components:
 * <ul>
 * <li>The set of options defined by the base framework.</li>
 * <li>The set of files and/or blocks to read.</li>
 * <li>The file system configuration to use for working with the files
 * or blocks.</li>
 * <li>The factory class to create a reader for each of the files or blocks
 * defined above. (Readers are created one-by-one as files are read.)</li>
 * <li>Options as defined by the base class.</li>
 * </ul>
 * <p>
 * The framework iterates over file descriptions, creating readers at the
 * moment they are needed. This allows simpler logic because, at the point of
 * reader creation, we have a file system, context and so on.
 * <p>
 * @See {AbstractScanFramework} for details.
 */

public class FileScanFramework extends ManagedScanFramework {

  private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(FileScanFramework.class);

  /**
   * The file schema negotiator adds no behavior at present, but is
   * created as a placeholder anticipating the need for file-specific
   * behavior later. Readers are expected to use an instance of this
   * class so that their code need not change later if/when we add new
   * methods. For example, perhaps we want to specify an assumed block
   * size for S3 files, or want to specify behavior if the file no longer
   * exists. Those are out of scope of this first round of changes which
   * focus on schema.
   */

  public interface FileSchemaNegotiator extends SchemaNegotiator {
  }

  /**
   * Implementation of the file-level schema negotiator. At present, no
   * file-specific features exist. This class shows, however, where we would
   * add such features.
   */

  public static class FileSchemaNegotiatorImpl extends SchemaNegotiatorImpl
      implements FileSchemaNegotiator {

    public FileSchemaNegotiatorImpl(ManagedScanFramework framework) {
      super(framework);
    }
  }

  /**
   * Options for a file-based scan.
   */

  public static class FileScanBuilder extends ScanFrameworkBuilder {
    private List<? extends FileWork> files;
    private Configuration fsConf;
    private FileMetadataOptions metadataOptions = new FileMetadataOptions();

    public void setConfig(Configuration fsConf) {
      this.fsConf = fsConf;
    }

    public void setFiles(List<? extends FileWork> files) {
      this.files = files;
    }

    public FileMetadataOptions metadataOptions() { return metadataOptions; }

    public FileScanFramework buildFileFramework() {
      return new FileScanFramework(this);
    }
  }

  public abstract static class FileReaderFactory implements ReaderFactory {

    protected FileScanFramework fileFramework;

    @Override
    public void bind(ManagedScanFramework baseFramework) {
      this.fileFramework = (FileScanFramework) baseFramework;
    }

    @Override
    public ManagedReader<? extends SchemaNegotiator> next() {
      FileSplit split = fileFramework.nextSplit();
      if (split == null) {
        return null;
      }
      return newReader(split);
    }

    protected DrillFileSystem fileSystem() { return fileFramework.dfs; }

    public abstract ManagedReader<? extends FileSchemaNegotiator> newReader(FileSplit split);
  }

  private FileMetadataManager metadataManager;
  private DrillFileSystem dfs;
  private List<FileSplit> spilts = new ArrayList<>();
  private Iterator<FileSplit> splitIter;
  private FileSplit currentSplit;

  public FileScanFramework(FileScanBuilder builder) {
    super(builder);
    assert builder.files != null;
    assert builder.fsConf != null;
  }

  public FileScanBuilder options() {
    return (FileScanBuilder) builder;
  }

  @Override
  protected void configure() {
    super.configure();
    FileScanBuilder options = options();

    // Create the Drill file system.

    try {
      dfs = context.newFileSystem(options.fsConf);
    } catch (IOException e) {
      throw UserException.dataReadError(e)
        .addContext("Failed to create FileSystem")
        .build(logger);
    }

    // Prepare the list of files. We need the list of paths up
    // front to compute the maximum partition. Then, we need to
    // iterate over the splits to create readers on demand.

    List<Path> paths = new ArrayList<>();
    for (FileWork work : options.files) {
      Path path = dfs.makeQualified(work.getPath());
      paths.add(path);
      FileSplit split = new FileSplit(path, work.getStart(), work.getLength(), new String[]{""});
      spilts.add(split);
    }
    splitIter = spilts.iterator();

    // Create the metadata manager to handle file metadata columns
    // (so-called implicit columns and partition columns.)

    options.metadataOptions().setFiles(paths);
    metadataManager = new FileMetadataManager(
        context.getFragmentContext().getOptions(),
        options.metadataOptions());
    builder.withMetadata(metadataManager);
  }

  protected FileSplit nextSplit() {
    if (! splitIter.hasNext()) {
      currentSplit = null;
      return null;
    }
    currentSplit = splitIter.next();

    // Tell the metadata manager about the current file so it can
    // populate the metadata columns, if requested.

    metadataManager.startFile(currentSplit.getPath());
    return currentSplit;
  }

  @Override
  protected SchemaNegotiatorImpl newNegotiator() {
    return new FileSchemaNegotiatorImpl(this);
  }

  @Override
  public boolean open(ShimBatchReader shimBatchReader) {
    try {
      return super.open(shimBatchReader);
    } catch (UserException e) {
      throw e;
    } catch (Exception e) {
      throw UserException.executionError(e)
        .addContext("File", currentSplit.getPath().toString())
        .build(logger);
    }
  }
}
