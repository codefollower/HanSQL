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
package org.lealone.hansql.common.logical.data;

import java.util.Iterator;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.drill.shaded.guava.com.google.common.collect.Iterators;
import org.lealone.hansql.common.logical.UnexpectedOperatorType;

/**
 * SimpleOperator is an operator that has one inputs at most.
 */
public abstract class SingleInputOperator extends LogicalOperatorBase {

  private LogicalOperator input;

  @JsonProperty("input")
  public LogicalOperator getInput() {
    return input;
  }

  @JsonProperty(value="input", required=true)
  public void setInput(LogicalOperator input) {
    if (input instanceof SinkOperator) {
      throw new UnexpectedOperatorType("You have set the input of a sink node of type ["+input.getClass().getSimpleName()+ "] as the input for another node of type ["+this.getClass().getSimpleName()+ "].  This is invalid.");
    }
    this.input = input;
    input.registerAsSubscriber(this);
  }

  @Override
  public Iterator<LogicalOperator> iterator() {
    return Iterators.singletonIterator(input);
  }

}
