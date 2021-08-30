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
package org.lealone.hansql.engine.operator;

import org.lealone.db.result.LocalResult;
import org.lealone.sql.operator.OperatorFactoryBase;
import org.lealone.sql.query.Select;

public class OlapOperatorFactory extends OperatorFactoryBase {

    public OlapOperatorFactory() {
        super("olap");
    }

    @Override
    public OlapOperator createOperator(Select select) {
        return new OlapOperator(select, new LocalResult());
    }

    @Override
    public OlapOperator createOperator(Select select, LocalResult localResult) {
        return new OlapOperator(select, localResult);
    }

}
