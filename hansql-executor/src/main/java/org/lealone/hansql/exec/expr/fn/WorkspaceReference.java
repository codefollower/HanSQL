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
package org.lealone.hansql.exec.expr.fn;

import org.apache.drill.shaded.guava.com.google.common.base.Preconditions;
import org.lealone.hansql.common.types.Types;
import org.lealone.hansql.common.types.TypeProtos.MajorType;

public class WorkspaceReference {

  private final Class<?> type;
  private final String name;
  private final boolean inject;
  private MajorType majorType;

  public WorkspaceReference(Class<?> type, String name, boolean inject) {
    Preconditions.checkNotNull(type);
    Preconditions.checkNotNull(name);
    this.type = type;
    this.name = name;
    this.inject = inject;
  }

  void setMajorType(MajorType majorType) {
    this.majorType = majorType;
  }

  public String getName() {
    return name;
  }

  public boolean isInject() {
    return inject;
  }

  public Class<?> getType() {
    return type;
  }

  public MajorType getMajorType() {
    return majorType;
  }

  @Override
  public String toString() {
    return "WorkspaceReference [type= " + type +", major type=" + Types.toString(majorType) + ", name=" + name + "]";
  }

}