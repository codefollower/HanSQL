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
package org.lealone.hansql.exec.cache;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.Externalizable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Classes that can be put in the Distributed Cache must implement this interface.
 */
public interface DrillSerializable extends Externalizable{
  static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(DrillSerializable.class);
  public void read(DataInput input) throws IOException;
  public void readFromStream(InputStream input) throws IOException;
  public void write(DataOutput output) throws IOException;
  public void writeToStream(OutputStream output) throws IOException;
}
