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
package org.lealone.hansql.exec.physical.config;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.lealone.hansql.common.JSONOptions;
import org.lealone.hansql.common.exceptions.ExecutionSetupException;
import org.lealone.hansql.exec.physical.base.AbstractBase;
import org.lealone.hansql.exec.physical.base.Leaf;
import org.lealone.hansql.exec.physical.base.PhysicalOperator;
import org.lealone.hansql.exec.physical.base.PhysicalVisitor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Values extends AbstractBase implements Leaf {

  @SuppressWarnings("unused")
  private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Values.class);

  private final JSONOptions content;

  @JsonCreator
  public Values(@JsonProperty("content") JSONOptions content){
    this.content = content;
  }

  public JSONOptions getContent(){
    return content;
  }

  @Override
  public <T, X, E extends Throwable> T accept(PhysicalVisitor<T, X, E> physicalVisitor, X value) throws E {
    return physicalVisitor.visitValues(this, value);
  }

  @Override
  public PhysicalOperator getNewWithChildren(List<PhysicalOperator> children) throws ExecutionSetupException {
    assert children.isEmpty();
    return new Values(content);
  }

  @Override
  public int getOperatorType() {
    // TODO: DRILL-6643: this implementation should be revisited
    return -1;
  }

  @Override
  public Iterator<PhysicalOperator> iterator() {
    return Collections.emptyIterator();
  }

}
