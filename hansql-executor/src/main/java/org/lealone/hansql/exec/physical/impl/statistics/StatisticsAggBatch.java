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
package org.lealone.hansql.exec.physical.impl.statistics;

import com.sun.codemodel.JExpr;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.lealone.hansql.exec.expr.TypeHelper;
import org.apache.drill.shaded.guava.com.google.common.collect.Lists;
import org.lealone.hansql.common.expression.FunctionCallFactory;
import org.lealone.hansql.common.expression.LogicalExpression;
import org.lealone.hansql.common.expression.SchemaPath;
import org.lealone.hansql.common.expression.ValueExpressions;
import org.lealone.hansql.common.types.TypeProtos;
import org.lealone.hansql.exec.context.options.OptionManager;
import org.lealone.hansql.exec.exception.ClassTransformationException;
import org.lealone.hansql.exec.exception.OutOfMemoryException;
import org.lealone.hansql.exec.exception.SchemaChangeException;
import org.lealone.hansql.exec.expr.ClassGenerator;
import org.lealone.hansql.exec.expr.CodeGenerator;
import org.lealone.hansql.exec.expr.ValueVectorWriteExpression;
import org.lealone.hansql.exec.ops.FragmentContext;
import org.lealone.hansql.exec.physical.base.PhysicalOperatorUtil;
import org.lealone.hansql.exec.physical.config.StatisticsAggregate;
import org.lealone.hansql.exec.physical.impl.aggregate.StreamingAggBatch;
import org.lealone.hansql.exec.physical.impl.aggregate.StreamingAggTemplate;
import org.lealone.hansql.exec.physical.impl.aggregate.StreamingAggregator;
import org.lealone.hansql.exec.planner.common.DrillStatsTable;
import org.lealone.hansql.exec.record.MaterializedField;
import org.lealone.hansql.exec.record.RecordBatch;
import org.lealone.hansql.exec.record.TypedFieldId;
import org.lealone.hansql.exec.record.BatchSchema.SelectionVectorMode;
import org.lealone.hansql.exec.store.ColumnExplorer;
import org.lealone.hansql.exec.vector.ValueVector;
import org.lealone.hansql.exec.vector.complex.FieldIdUtil;
import org.lealone.hansql.exec.vector.complex.MapVector;

/*
 * TODO: This needs cleanup. Currently the key values are constants and we compare the constants
 * for every record. Seems unnecessary.
 *
 * Example input and output:
 * Schema of incoming batch: region_id (VARCHAR), sales_city (VARCHAR), cnt (BIGINT)
 * Schema of outgoing batch:
 *    "columns"       : MAP - Column names
 *       "region_id"  : VARCHAR
 *       "sales_city" : VARCHAR
 *       "cnt"        : VARCHAR
 *    "statscount" : MAP
 *       "region_id"  : BIGINT - statscount(region_id) - aggregation over all values of region_id
 *                      in incoming batch
 *       "sales_city" : BIGINT - statscount(sales_city)
 *       "cnt"        : BIGINT - statscount(cnt)
 *    "nonnullstatcount" : MAP
 *       "region_id"  : BIGINT - nonnullstatcount(region_id)
 *       "sales_city" : BIGINT - nonnullstatcount(sales_city)
 *       "cnt"        : BIGINT - nonnullstatcount(cnt)
 *   .... another map for next stats function ....
 */

public class StatisticsAggBatch extends StreamingAggBatch {
  // List of statistics functions e.g. rowcount, ndv output by StatisticsAggBatch
  private List<String> functions;
  // List of implicit columns for which we do NOT want to compute statistics
  private Map<String, ColumnExplorer.ImplicitFileColumns> implicitFileColumnsMap;

  public StatisticsAggBatch(StatisticsAggregate popConfig, RecordBatch incoming,
      FragmentContext context) throws OutOfMemoryException {
    super(popConfig, incoming, context);
    // Get the list from the physical operator configuration
    functions = popConfig.getFunctions();
    implicitFileColumnsMap = ColumnExplorer.initImplicitFileColumns(context.getOptions());
  }

  /*
   * Returns whether the given column is an implicit column
   */
  private boolean isImplicitFileOrPartitionColumn(MaterializedField mf, OptionManager optionManager) {
    return implicitFileColumnsMap.get(SchemaPath.getSimplePath(mf.getName()).toString()) != null ||
       ColumnExplorer.isPartitionColumn(optionManager, SchemaPath.getSimplePath(mf.getName()));
  }

  /*
   * Create the field id for the value vector corresponding to the materialized expression
   */
  private TypedFieldId createVVFieldId(LogicalExpression mle, String name, MapVector parent) {
    Class<? extends ValueVector> vvc =
            TypeHelper.getValueVectorClass(mle.getMajorType().getMinorType(),
                    mle.getMajorType().getMode());
    ValueVector vv = parent.addOrGet(name, mle.getMajorType(), vvc);
    TypedFieldId pfid = container.getValueVectorId(SchemaPath.getSimplePath(parent.getField().getName()));
    assert pfid.getFieldIds().length == 1;
    TypedFieldId.Builder builder = TypedFieldId.newBuilder();
    builder.addId(pfid.getFieldIds()[0]);
    TypedFieldId id =
        FieldIdUtil.getFieldIdIfMatches(parent, builder, true,
            SchemaPath.getSimplePath(vv.getField().getName()).getRootSegment());
    return id;
  }

  /*
   * Creates the key column within the parent value vector
   */
  private void createNestedKeyColumn(MapVector parent, String name, LogicalExpression expr,
      List<LogicalExpression> keyExprs, List<TypedFieldId> keyOutputIds)
          throws SchemaChangeException {
    LogicalExpression mle = PhysicalOperatorUtil.materializeExpression(expr, incoming, context);
    TypedFieldId id = createVVFieldId(mle, name, parent);
    keyExprs.add(mle);
    keyOutputIds.add(id);
  }

  /*
   * Creates the value vector within the parent value vector. The map vector key is
   * is the column name and value is the statistic expression e.g. "salary" : NDV(emp.salary)
   */
  private void addMapVector(String name, MapVector parent, LogicalExpression expr,
      List<LogicalExpression> valueExprs) throws SchemaChangeException {
    LogicalExpression mle = PhysicalOperatorUtil.materializeExpression(expr, incoming, context);
    TypedFieldId id = createVVFieldId(mle, name, parent);
    valueExprs.add(new ValueVectorWriteExpression(id, mle, true));
  }

  /*
   * Generates the code for the statistics aggregate which is subclassed from StreamingAggregator
   */
  private StreamingAggregator codegenAggregator(List<LogicalExpression> keyExprs,
      List<LogicalExpression> valueExprs, List<TypedFieldId> keyOutputIds)
          throws SchemaChangeException, ClassTransformationException, IOException {

    ClassGenerator<StreamingAggregator> cg = CodeGenerator.getRoot(StreamingAggTemplate.TEMPLATE_DEFINITION, context.getOptions());
    cg.getCodeGenerator().plainJavaCapable(true);
    // Uncomment out this line to debug the generated code.
    // cg.getCodeGenerator().saveCodeForDebugging(true);

    LogicalExpression[] keyExprsArray = new LogicalExpression[keyExprs.size()];
    LogicalExpression[] valueExprsArray = new LogicalExpression[valueExprs.size()];
    TypedFieldId[] keyOutputIdsArray = new TypedFieldId[keyOutputIds.size()];

    keyExprs.toArray(keyExprsArray);
    valueExprs.toArray(valueExprsArray);
    keyOutputIds.toArray(keyOutputIdsArray);

    setupIsSame(cg, keyExprsArray);
    setupIsSameApart(cg, keyExprsArray);
    addRecordValues(cg, valueExprsArray);
    outputRecordKeys(cg, keyOutputIdsArray, keyExprsArray);
    outputRecordKeysPrev(cg, keyOutputIdsArray, keyExprsArray);

    cg.getBlock("resetValues")._return(JExpr.TRUE);
    getIndex(cg);

    container.buildSchema(SelectionVectorMode.NONE);
    StreamingAggregator agg = context.getImplementationClass(cg);
    agg.setup(oContext, incoming, this, ValueVector.MAX_ROW_COUNT);
    return agg;
  }

  @Override
  protected StreamingAggregator createAggregatorInternal()
      throws SchemaChangeException, ClassTransformationException, IOException {
    List<LogicalExpression> keyExprs = Lists.newArrayList();
    List<LogicalExpression> valueExprs = Lists.newArrayList();
    List<TypedFieldId> keyOutputIds = Lists.newArrayList();
    String [] colMeta = new String [] {Statistic.COLNAME, Statistic.COLTYPE};
    container.clear();
    // Generate the `column` map containing the columns in the incoming schema. Ignore
    // the implicit columns
    for (String col : colMeta) {
      MapVector parent = new MapVector(col, oContext.getAllocator(), null);
      container.add(parent);
      for (MaterializedField mf : incoming.getSchema()) {
        LogicalExpression expr;
        if (col.equals(colMeta[0])) {
          expr = ValueExpressions.getChar(SchemaPath.getSimplePath(mf.getName()).toString(), 0);
        } else {
          expr = ValueExpressions.getChar(DrillStatsTable.getMapper().writeValueAsString(mf.getType()), 0);
        }
        // Ignore implicit columns
        if (!isImplicitFileOrPartitionColumn(mf, incoming.getContext().getOptions())) {
          createNestedKeyColumn(
              parent,
              SchemaPath.getSimplePath(mf.getName()).toString(),
              expr,
              keyExprs,
              keyOutputIds
          );
        }
      }
    }
    // Iterate over the list of statistics and generate a MAP whose key is the column
    // and the value is the statistic for the column e.g.
    // NDV <<"employee_id" : 500>, <"salary" : 10>> represents a MAP of NDVs (# distinct values)
    // employee NDV = 500, salary NDV = 10
    for (String func : functions) {
      MapVector parent = new MapVector(func, oContext.getAllocator(), null);
      container.add(parent);

      for (MaterializedField mf : incoming.getSchema()) {
        // Check stats collection is only being done for supported data-types. Complex types
        // such as MAP, LIST are not supported!
        if (isColMinorTypeValid(mf) && !isImplicitFileOrPartitionColumn(mf, incoming.getContext().getOptions())) {
          List<LogicalExpression> args = Lists.newArrayList();
          args.add(SchemaPath.getSimplePath(mf.getName()));
          LogicalExpression call = FunctionCallFactory.createExpression(func, args);
          addMapVector(SchemaPath.getSimplePath(mf.getName()).toString(), parent, call, valueExprs);
        }
      }
    }
    // Now generate the code for the statistics aggregate
    return codegenAggregator(keyExprs, valueExprs, keyOutputIds);
  }

  private boolean isColMinorTypeValid(MaterializedField mf) throws UnsupportedOperationException {
    String mTypeStr = null;
    if (mf.getType().getMinorType() == TypeProtos.MinorType.GENERIC_OBJECT) {
      mTypeStr = "GENERIC OBJECT";
    } else if (mf.getType().getMinorType() == TypeProtos.MinorType.LATE) {
      mTypeStr = "LATE";
    }else if (mf.getType().getMinorType() == TypeProtos.MinorType.LIST) {
      mTypeStr = "LIST";
    } else if (mf.getType().getMinorType() == TypeProtos.MinorType.MAP) {
      mTypeStr = "MAP";
    } else if (mf.getType().getMinorType() == TypeProtos.MinorType.UNION) {
      mTypeStr = "UNION";
    }
    if (mTypeStr != null) {
      return false;
      //throw new UnsupportedOperationException(String.format("Column %s has data-type %s which is not supported",
      //    mf.getName(), mTypeStr));
    } else {
      return true;
    }
  }
}
