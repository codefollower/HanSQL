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
package org.lealone.hansql.exec.record;

import org.lealone.hansql.exec.physical.base.LateralContract;

public interface TableFunctionContract {

  /**
   * sets the incoming record batch for the operator. The innerNext() method requires one of the setIncoming methods
   * to be called before it is called.
   * @param incoming
   */
  void setIncoming(RecordBatch incoming);

  /**
   * sets the incoming Lateral Contract for the operator. The innerNext() method requires one of the setIncoming methods
   * to be called before it is called.
   * @param incoming
   */
  void setIncoming(LateralContract incoming);

}
