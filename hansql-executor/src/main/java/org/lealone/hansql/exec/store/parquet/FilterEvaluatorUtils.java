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
package org.lealone.hansql.exec.store.parquet;

import org.lealone.hansql.common.expression.ErrorCollector;
import org.lealone.hansql.common.expression.ErrorCollectorImpl;
import org.lealone.hansql.common.expression.LogicalExpression;
import org.lealone.hansql.common.expression.SchemaPath;
import org.lealone.hansql.common.expression.visitors.AbstractExprVisitor;
import org.lealone.hansql.common.types.TypeProtos;
import org.lealone.hansql.exec.compile.sig.ConstantExpressionIdentifier;
import org.lealone.hansql.exec.context.options.OptionManager;
import org.lealone.hansql.exec.expr.ExpressionTreeMaterializer;
import org.lealone.hansql.exec.expr.FilterBuilder;
import org.lealone.hansql.exec.expr.FilterPredicate;
import org.lealone.hansql.exec.expr.StatisticsProvider;
import org.lealone.hansql.exec.expr.fn.FunctionLookupContext;
import org.lealone.hansql.exec.expr.stat.RowsMatch;
import org.lealone.hansql.exec.ops.FragmentContext;
import org.lealone.hansql.exec.ops.UdfUtilities;
import org.lealone.hansql.exec.record.metadata.ColumnMetadata;
import org.lealone.hansql.exec.record.metadata.SchemaPathUtils;
import org.lealone.hansql.exec.record.metadata.TupleMetadata;
import org.lealone.hansql.exec.store.parquet.metadata.MetadataBase;
import org.lealone.hansql.metastore.ColumnStatistics;
import org.lealone.hansql.metastore.RowGroupMetadata;
import org.lealone.hansql.metastore.TableStatisticsKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FilterEvaluatorUtils {
  private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(FilterEvaluatorUtils.class);

  private FilterEvaluatorUtils() {
  }

  @SuppressWarnings("RedundantTypeArguments")
  public static RowsMatch evalFilter(LogicalExpression expr, MetadataBase.ParquetTableMetadataBase footer,
                                     int rowGroupIndex, OptionManager options, FragmentContext fragmentContext) {
    // Specifies type arguments explicitly to avoid compilation error caused by JDK-8066974
    List<SchemaPath> schemaPathsInExpr = new ArrayList<>(
            expr.<Set<SchemaPath>, Void, RuntimeException>accept(new FieldReferenceFinder(), null));

    RowGroupMetadata rowGroupMetadata = new ArrayList<>(ParquetTableMetadataUtils.getRowGroupsMetadata(footer).values()).get(rowGroupIndex);
    Map<SchemaPath, ColumnStatistics> columnsStatistics = rowGroupMetadata.getColumnsStatistics();
    columnsStatistics = ParquetTableMetadataUtils.addImplicitColumnsStatistics(columnsStatistics,
        schemaPathsInExpr, Collections.emptyList(), options, rowGroupMetadata.getLocation(), true);

    return matches(expr, columnsStatistics, rowGroupMetadata.getSchema(), (Long) TableStatisticsKind.ROW_COUNT.getValue(rowGroupMetadata),
        fragmentContext, fragmentContext.getFunctionRegistry());
  }

  public static RowsMatch matches(LogicalExpression expr, Map<SchemaPath, ColumnStatistics> columnsStatistics,
      TupleMetadata schema, long rowCount, UdfUtilities udfUtilities, FunctionLookupContext functionImplementationRegistry) {
    ErrorCollector errorCollector = new ErrorCollectorImpl();

    LogicalExpression materializedFilter = ExpressionTreeMaterializer.materializeFilterExpr(
        expr,
        schema,
        errorCollector, functionImplementationRegistry);

    if (errorCollector.hasErrors()) {
      logger.error("{} error(s) encountered when materialize filter expression : {}",
          errorCollector.getErrorCount(), errorCollector.toErrorString());
      return RowsMatch.SOME;
    }

    Set<LogicalExpression> constantBoundaries = ConstantExpressionIdentifier.getConstantExpressionSet(materializedFilter);
    FilterPredicate parquetPredicate = FilterBuilder.buildFilterPredicate(
        materializedFilter, constantBoundaries, udfUtilities, true);

    return matches(parquetPredicate, columnsStatistics, rowCount);
  }

  public static RowsMatch matches(FilterPredicate parquetPredicate,
                                  Map<SchemaPath, ColumnStatistics> columnsStatistics,
                                  long rowCount, TupleMetadata fileMetadata, Set<SchemaPath> schemaPathsInExpr) {
    RowsMatch temp = matches(parquetPredicate, columnsStatistics, rowCount);
    return temp == RowsMatch.ALL && isRepeated(schemaPathsInExpr, fileMetadata) ? RowsMatch.SOME : temp;
  }

  @SuppressWarnings("unchecked")
  public static RowsMatch matches(FilterPredicate predicate, Map<SchemaPath, ColumnStatistics> columnsStatistics, long rowCount) {
    if (predicate != null) {
      StatisticsProvider rangeExprEvaluator = new StatisticsProvider(columnsStatistics, rowCount);
      return predicate.matches(rangeExprEvaluator);
    }
    return RowsMatch.SOME;
  }

  private static boolean isRepeated(Set<SchemaPath> fields, TupleMetadata fileMetadata) {
    for (SchemaPath field : fields) {
      ColumnMetadata columnMetadata = SchemaPathUtils.getColumnMetadata(field, fileMetadata);
      TypeProtos.MajorType fieldType = columnMetadata != null ? columnMetadata.majorType() : null;
      if (fieldType != null && fieldType.getMode() == TypeProtos.DataMode.REPEATED) {
        return true;
      }
    }
    return false;
  }

  /**
   * Search through a LogicalExpression, finding all internal schema path references and returning them in a set.
   */
  public static class FieldReferenceFinder extends AbstractExprVisitor<Set<SchemaPath>, Void, RuntimeException> {
    @Override
    public Set<SchemaPath> visitSchemaPath(SchemaPath path, Void value) {
      Set<SchemaPath> set = new HashSet<>();
      set.add(path);
      return set;
    }

    @Override
    public Set<SchemaPath> visitUnknown(LogicalExpression e, Void value) {
      Set<SchemaPath> paths = new HashSet<>();
      for (LogicalExpression ex : e) {
        paths.addAll(ex.accept(this, null));
      }
      return paths;
    }
  }
}
