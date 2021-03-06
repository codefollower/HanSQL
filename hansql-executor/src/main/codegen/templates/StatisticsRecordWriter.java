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
<@pp.dropOutputFile />
<@pp.changeOutputFile name="org/lealone/hansql/exec/store/StatisticsRecordWriter.java" />
<#include "/@includes/license.ftl" />

package org.lealone.hansql.exec.store;

import org.lealone.hansql.exec.record.VectorAccessible;
import org.lealone.hansql.exec.store.EventBasedRecordWriter.FieldConverter;
import org.lealone.hansql.exec.vector.complex.reader.FieldReader;

import java.io.IOException;
import java.util.Map;

/*
 * This class is generated using freemarker and the ${.template_name} template.
 */

/** StatisticsRecordWriter interface. */
public interface StatisticsRecordWriter {

  /**
   * Initialize the writer.
   *
   * @param writerOptions Contains key, value pair of settings.
   * @throws IOException
   */
  void init(Map<String, String> writerOptions) throws IOException;

  /**
   * Update the schema in RecordWriter. Called at least once before starting writing the records.
   * @param batch
   * @throws IOException
   */
  void updateSchema(VectorAccessible batch) throws IOException;

  /**
   * Check if the writer should start a new partition, and if so, start a new partition
   */
  public void checkForNewPartition(int index);

  /**
   * Returns if the writer is a blocking writer i.e. consumes all input before writing it out
   * @return TRUE, if writer is blocking. FALSE, otherwise
   */
  boolean isBlockingWriter();

  /**
   * Called before starting writing fields in a record.
   * @throws IOException
   */
  void startStatisticsRecord() throws IOException;

  <#list vv.types as type>
  <#list type.minor as minor>
  <#list vv.modes as mode>
  /** Add the field value given in <code>valueHolder</code> at the given column number <code>fieldId</code>. */
  public FieldConverter getNew${mode.prefix}${minor.class}Converter(int fieldId, String fieldName, FieldReader reader);

  </#list>
  </#list>
  </#list>

  /**
   * Called after adding all fields in a particular statistics record are added using
   * add{TypeHolder}(fieldId, TypeHolder) methods.
   * @throws IOException
   */
  void endStatisticsRecord() throws IOException;
  /**
   * For a blocking writer, called after processing all the records to flush out the writes
   * @throws IOException
   */
  void flushBlockingWriter() throws IOException;
  void abort() throws IOException;
  void cleanup() throws IOException;
}