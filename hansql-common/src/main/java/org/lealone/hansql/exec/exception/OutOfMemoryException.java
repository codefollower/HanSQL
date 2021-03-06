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
package org.lealone.hansql.exec.exception;

/**
 * This is thrown in various cases when Drill cannot allocate Direct Memory.
 * <b>Note: </b> This does <b>NOT</b> get thrown when we run out of heap memory.
 */
public class OutOfMemoryException extends RuntimeException {

    private static final long serialVersionUID = -6858052345185793382L;

    public OutOfMemoryException() {
        super();
    }

    public OutOfMemoryException(String message, Throwable cause, boolean enableSuppression,
            boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public OutOfMemoryException(String message, Throwable cause) {
        super(message, cause);

    }

    public OutOfMemoryException(String message) {
        super(message);

    }

    public OutOfMemoryException(Throwable cause) {
        super(cause);

    }

}
