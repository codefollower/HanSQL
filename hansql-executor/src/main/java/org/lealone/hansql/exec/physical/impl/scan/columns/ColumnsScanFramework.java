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
package org.lealone.hansql.exec.physical.impl.scan.columns;

import org.lealone.hansql.exec.physical.impl.scan.file.FileScanFramework;
import org.lealone.hansql.exec.physical.impl.scan.framework.SchemaNegotiatorImpl;

/**
 * Scan framework for a file that supports the special "columns" column.
 * <p>
 * The logic here is a bit tricky. The CSV (really, "compliant text") reader
 * operates in multiple modes. If a header is required, then the columns
 * array is not allowed. If the header is not loaded, then the columns
 * array must be used.
 * <p>
 * This class combines the above semantics with the projection list from the
 * query. It handles things like two appearances of the unadorned columns
 * identifier, the use of the columns identifier when it is not allowed, etc.
 */

public class ColumnsScanFramework extends FileScanFramework {

  public static class ColumnsScanBuilder extends FileScanBuilder {
    protected boolean requireColumnsArray;

    public void requireColumnsArray(boolean flag) {
      requireColumnsArray = flag;
    }

    @Override
    public FileScanFramework buildFileFramework() {
      return new ColumnsScanFramework(this);
    }
  }

  /**
   * Implementation of the columns array schema negotiator.
   */

  public static class ColumnsSchemaNegotiatorImpl extends FileSchemaNegotiatorImpl
          implements ColumnsSchemaNegotiator {

    public ColumnsSchemaNegotiatorImpl(ColumnsScanFramework framework) {
      super(framework);
    }

    private ColumnsScanFramework framework() {
      return (ColumnsScanFramework) framework;
    }

    @Override
    public boolean columnsArrayProjected() {
      return framework().columnsArrayManager.hasColumnsArrayColumn();
    }

    @Override
    public boolean[] projectedIndexes() {
      return framework().columnsArrayManager.elementProjection();
    }
  }

  protected ColumnsArrayManager columnsArrayManager;

  public ColumnsScanFramework(ColumnsScanBuilder builder) {
    super(builder);
  }

  @Override
  protected void configure() {
    super.configure();
    columnsArrayManager = new ColumnsArrayManager(
       ((ColumnsScanBuilder) builder).requireColumnsArray);
    builder.addParser(columnsArrayManager.projectionParser());
    builder.addResolver(columnsArrayManager.resolver());
  }

  @Override
  protected SchemaNegotiatorImpl newNegotiator() {
    return new ColumnsSchemaNegotiatorImpl(this);
  }
}
