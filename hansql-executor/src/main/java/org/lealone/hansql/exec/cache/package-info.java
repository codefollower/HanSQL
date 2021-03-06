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
/**
 * Distributed cache for syncing state and data between Drillbits.
 *
 * The distributed cache defined in this package can be used to make data
 * available to all of the nodes in a Drill cluster. This is useful in cases
 * where most of the work of a given operation can be separated into independent
 * tasks, but some common information must be available to and mutable by all of
 * the nodes currently involved in the operation.
 */
package org.lealone.hansql.exec.cache;