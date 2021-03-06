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
package org.lealone.hansql.exec.util;

import java.util.Collection;

import org.apache.drill.shaded.guava.com.google.common.base.Preconditions;
import org.apache.drill.shaded.guava.com.google.common.base.Predicate;
import org.apache.drill.shaded.guava.com.google.common.collect.Iterables;
import org.lealone.hansql.common.expression.PathSegment;
import org.lealone.hansql.common.expression.SchemaPath;
import org.lealone.hansql.exec.expr.fn.impl.DateUtility;
import org.lealone.hansql.exec.ops.FragmentContext;
import org.lealone.hansql.exec.planner.logical.DrillTable;
import org.lealone.hansql.exec.planner.logical.DrillTranslatableTable;
import org.lealone.hansql.exec.proto.ExecProtos;
import org.lealone.hansql.exec.proto.BitControl.QueryContextInformation;
import org.lealone.hansql.exec.proto.helper.QueryIdHelper;
import org.lealone.hansql.optimizer.plan.RelOptTable;
import org.lealone.hansql.optimizer.rex.RexLiteral;

public class Utilities {

  public static final String COL_NULL_ERROR = "Columns cannot be null. Use star column to select all fields.";

  public static String getFileNameForQueryFragment(FragmentContext context, String location, String tag) {
     /*
     * From the context, get the query id, major fragment id, minor fragment id. This will be used as the file name to
     * which we will dump the incoming buffer data
     */
    ExecProtos.FragmentHandle handle = context.getHandle();

    String qid = QueryIdHelper.getQueryId(handle.getQueryId());

    int majorFragmentId = handle.getMajorFragmentId();
    int minorFragmentId = handle.getMinorFragmentId();

    String fileName = String.format("%s//%s_%s_%s_%s", location, qid, majorFragmentId, minorFragmentId, tag);

    return fileName;
  }

  /**
   * Create {@link org.lealone.hansql.exec.proto.BitControl.QueryContextInformation} with given <i>defaultSchemaName</i>. Rest of the members of the
   * QueryContextInformation is derived from the current state of the process.
   *
   * @param defaultSchemaName
   * @param sessionId
   * @return A {@link org.lealone.hansql.exec.proto.BitControl.QueryContextInformation} with given <i>defaultSchemaName</i>.
   */
  public static QueryContextInformation createQueryContextInfo(final String defaultSchemaName,
      final String sessionId) {
    final long queryStartTime = System.currentTimeMillis();
    final int timeZone = DateUtility.getIndex(System.getProperty("user.timezone"));
    return QueryContextInformation.newBuilder()
        .setDefaultSchemaName(defaultSchemaName)
        .setQueryStartTime(queryStartTime)
        .setTimeZone(timeZone)
        .setSessionId(sessionId)
        .build();
  }

  /**
   * Read the manifest file and get the Drill version number
   * @return The Drill version.
   */
  public static String getDrillVersion() {
      String v = Utilities.class.getPackage().getImplementationVersion();
      return v;
  }

  /**
   * Return true if list of schema path has star column.
   * @param projected
   * @return True if the list of {@link org.lealone.hansql.common.expression.SchemaPath}s has star column.
   */
  public static boolean isStarQuery(Collection<SchemaPath> projected) {
    return Iterables.tryFind(Preconditions.checkNotNull(projected, COL_NULL_ERROR), new Predicate<SchemaPath>() {
      @Override
      public boolean apply(SchemaPath path) {
        return Preconditions.checkNotNull(path).equals(SchemaPath.STAR_COLUMN);
      }
    }).isPresent();
  }

  /**
   * Gets {@link DrillTable}, either wrapped in RelOptTable, or DrillTranslatableTable.
   *
   * @param table table instance
   * @return Drill table
   */
  public static DrillTable getDrillTable(RelOptTable table) {
    DrillTable drillTable = table.unwrap(DrillTable.class);
    if (drillTable == null) {
      drillTable = table.unwrap(DrillTranslatableTable.class).getDrillTable();
    }
    return drillTable;
  }

  /**
   * Converts literal into path segment based on its type.
   * For unsupported types, returns null.
   *
   * @param literal literal
   * @return new path segment, null otherwise
   */
  public static PathSegment convertLiteral(RexLiteral literal) {
    switch (literal.getType().getSqlTypeName()) {
      case CHAR:
        return new PathSegment.NameSegment(RexLiteral.stringValue(literal));
      case INTEGER:
        return new PathSegment.ArraySegment(RexLiteral.intValue(literal));
      default:
        return null;
    }
  }
}
