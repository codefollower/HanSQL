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
package org.lealone.hansql.exec.expr.fn.impl;

import io.netty.buffer.DrillBuf;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class SqlPatternComplexMatcher implements SqlPatternMatcher {
  private final Matcher matcher;
  private final CharSequenceWrapper charSequenceWrapper;

  public SqlPatternComplexMatcher(String patternString) {
    charSequenceWrapper = new CharSequenceWrapper();
    matcher = Pattern.compile(patternString).matcher(charSequenceWrapper);
  }

  @Override
  public int match(int start, int end, DrillBuf drillBuf) {
    charSequenceWrapper.setBuffer(start, end, drillBuf);
    matcher.reset();
    return matcher.matches() ? 1 : 0;
  }

}
