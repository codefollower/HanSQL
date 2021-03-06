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
package org.lealone.hansql.exec.store.dfs;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.hadoop.fs.Path;
import org.lealone.hansql.common.logical.FormatPluginConfig;

public class FormatSelection {
  static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(FormatSelection.class);

  private FormatPluginConfig format;
  private FileSelection selection;

  public FormatSelection() {}

  @JsonCreator
  public FormatSelection(@JsonProperty("format") FormatPluginConfig format, @JsonProperty("files") List<Path> files){
    this.format = format;
    this.selection = FileSelection.create(null, files, null);
  }

  public FormatSelection(FormatPluginConfig format, FileSelection selection) {
    this.format = format;
    this.selection = selection;
  }

  @JsonProperty("format")
  public FormatPluginConfig getFormat(){
    return format;
  }

  @JsonProperty("files")
  public List<Path> getAsFiles(){
    return selection.getFiles();
  }

  @JsonIgnore
  public FileSelection getSelection(){
    return selection;
  }

  @JsonIgnore
  public boolean supportDirPruning() {
    return selection.supportDirPrunig();
  }
}
