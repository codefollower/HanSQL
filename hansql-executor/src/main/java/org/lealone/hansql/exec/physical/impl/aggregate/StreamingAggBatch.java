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
package org.lealone.hansql.exec.physical.impl.aggregate;

import static org.lealone.hansql.exec.record.RecordBatch.IterOutcome.EMIT;
import static org.lealone.hansql.exec.record.RecordBatch.IterOutcome.NONE;
import static org.lealone.hansql.exec.record.RecordBatch.IterOutcome.OK;
import static org.lealone.hansql.exec.record.RecordBatch.IterOutcome.OK_NEW_SCHEMA;
import static org.lealone.hansql.exec.record.RecordBatch.IterOutcome.STOP;

import java.io.IOException;
import java.util.List;

import org.apache.drill.shaded.guava.com.google.common.annotations.VisibleForTesting;
import org.apache.drill.shaded.guava.com.google.common.collect.Lists;
import org.lealone.hansql.common.exceptions.DrillRuntimeException;
import org.lealone.hansql.common.exceptions.UserException;
import org.lealone.hansql.common.expression.ErrorCollector;
import org.lealone.hansql.common.expression.ErrorCollectorImpl;
import org.lealone.hansql.common.expression.IfExpression;
import org.lealone.hansql.common.expression.LogicalExpression;
import org.lealone.hansql.common.logical.data.NamedExpression;
import org.lealone.hansql.common.types.TypeProtos;
import org.lealone.hansql.exec.compile.sig.GeneratorMapping;
import org.lealone.hansql.exec.compile.sig.MappingSet;
import org.lealone.hansql.exec.exception.ClassTransformationException;
import org.lealone.hansql.exec.exception.OutOfMemoryException;
import org.lealone.hansql.exec.exception.SchemaChangeException;
import org.lealone.hansql.exec.expr.ClassGenerator;
import org.lealone.hansql.exec.expr.CodeGenerator;
import org.lealone.hansql.exec.expr.DrillFuncHolderExpr;
import org.lealone.hansql.exec.expr.ExpressionTreeMaterializer;
import org.lealone.hansql.exec.expr.HoldingContainerExpression;
import org.lealone.hansql.exec.expr.ValueVectorWriteExpression;
import org.lealone.hansql.exec.expr.ClassGenerator.HoldingContainer;
import org.lealone.hansql.exec.expr.fn.FunctionGenerationHelper;
import org.lealone.hansql.exec.ops.FragmentContext;
import org.lealone.hansql.exec.physical.config.StreamingAggregate;
import org.lealone.hansql.exec.physical.impl.aggregate.StreamingAggregator.AggOutcome;
import org.lealone.hansql.exec.physical.impl.xsort.managed.ExternalSortBatch;
import org.lealone.hansql.exec.record.AbstractRecordBatch;
import org.lealone.hansql.exec.record.BatchSchema;
import org.lealone.hansql.exec.record.MaterializedField;
import org.lealone.hansql.exec.record.RecordBatch;
import org.lealone.hansql.exec.record.TypedFieldId;
import org.lealone.hansql.exec.record.VectorContainer;
import org.lealone.hansql.exec.record.VectorWrapper;
import org.lealone.hansql.exec.record.BatchSchema.SelectionVectorMode;
import org.lealone.hansql.exec.record.selection.SelectionVector2;
import org.lealone.hansql.exec.record.selection.SelectionVector4;
import org.lealone.hansql.exec.vector.AllocationHelper;
import org.lealone.hansql.exec.vector.FixedWidthVector;
import org.lealone.hansql.exec.vector.UntypedNullHolder;
import org.lealone.hansql.exec.vector.UntypedNullVector;
import org.lealone.hansql.exec.vector.ValueVector;
import org.lealone.hansql.exec.expr.TypeHelper;

import com.sun.codemodel.JExpr;
import com.sun.codemodel.JVar;
import org.lealone.hansql.exec.vector.complex.writer.BaseWriter;

public class StreamingAggBatch extends AbstractRecordBatch<StreamingAggregate> {
  static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(StreamingAggBatch.class);

  protected StreamingAggregator aggregator;
  protected final RecordBatch incoming;
  private List<BaseWriter.ComplexWriter> complexWriters;
  //
  // Streaming agg can be in (a) a normal pipeline or (b) it may be in a pipeline that is part of a subquery involving
  // lateral and unnest. In case(a), the aggregator proceeds normally until it sees a group change or a NONE. If a
  // group has changed, the aggregated data is sent downstream and the aggregation continues with the next group. If
  // a NONE is seen, the aggregator completes, sends data downstream and cleans up.
  // In case (b), the aggregator behaves similar to case(a) if a group change or NONE is observed. However it will
  // also encounter a new state EMIT, every time unnest processes a new row. In this case the aggregator must complete the
  // aggregation, send out the results, AND reset to receive more data. To make the treatment of these two cases
  // similar, we define the aggregation operation in terms of data sets.
  //   Data Set = The set of data that the aggregator is currently aggregating. In a normal query, the entire data is
  //   a single data set. In the case of a Lateral subquery, every row processed by unnest is a data set.  There can,
  //   therefore, be one or more data sets in an aggregation.
  //   Data Sets may have multiple batches and may contain one or more empty batches. A data set may consist entirely
  //   of empty batches.
  //   Schema may change across Data Sets.
  //   A corner case is the case of a Data Set having many empty batches in the beginning. Such a data set may see a
  //   schema change once the first non-empty batch is received.
  //   Schema change within a Data Set is not supported.
  //
  //   We will define some states for internal management
  //
  private boolean done = false;  // END of all data
  private boolean first = true;  // Beginning of new data set. True during the build schema phase. False once the first
                                 // call to inner next is made.
  private boolean sendEmit = false; // In the case where we see an OK_NEW_SCHEMA along with the end of a data set
                                    // we send out a batch with OK_NEW_SCHEMA first, then in the next iteration,
                                    // we send out an empty batch with EMIT.
  private IterOutcome lastKnownOutcome = OK; // keep track of the outcome from the previous call to incoming.next
  private boolean firstBatchForSchema = true; // true if the current batch came in with an OK_NEW_SCHEMA
  private boolean firstBatchForDataSet = true; // true if the current batch is the first batch in a data set
  private int recordCount = 0;  // number of records output in the current data set

  private BatchSchema incomingSchema;

  /*
   * DRILL-2277, DRILL-2411: For straight aggregates without a group by clause we need to perform special handling when
   * the incoming batch is empty. In the case of the empty input into the streaming aggregate we need
   * to return a single batch with one row. For count we need to return 0 and for all other aggregate
   * functions like sum, avg etc we need to return an explicit row with NULL. Since we correctly allocate the type of
   * the outgoing vectors (required for count and nullable for other aggregate functions) all we really need to do
   * is simply set the record count to be 1 in such cases. For nullable vectors we don't need to do anything because
   * if we don't set anything the output will be NULL, however for required vectors we explicitly zero out the vector
   * since we don't zero it out while allocating it.
   *
   * We maintain some state to remember that we have done such special handling.
   */
  private boolean specialBatchSent = false;
  private static final int SPECIAL_BATCH_COUNT = 1;

  // TODO: Needs to adapt to batch sizing rather than hardcoded constant value
  private int maxOutputRowCount = ValueVector.MAX_ROW_COUNT;

  public StreamingAggBatch(StreamingAggregate popConfig, RecordBatch incoming, FragmentContext context)
    throws OutOfMemoryException {
    super(popConfig, context);
    this.incoming = incoming;

    // Sorry. Horrible hack. To release memory after an in-memory sort,
    // the External Sort normally frees in-memory sorted batches just
    // before returning NONE. But, this operator needs the batches to
    // be present, even after NONE. This call puts the ESB into the proper
    // "mode". A later call explicitly releases the batches.

    ExternalSortBatch.retainSv4OnNone(incoming);
  }

  @Override
  public int getRecordCount() {
    if (done || aggregator == null) {
      return 0;
    }
    return recordCount;
  }

  @Override
  public VectorContainer getOutgoingContainer() {
    return this.container;
  }

  @Override
  public void buildSchema() throws SchemaChangeException {
    IterOutcome outcome = next(incoming);
    switch (outcome) {
      case NONE:
        state = BatchState.DONE;
        container.buildSchema(SelectionVectorMode.NONE);
        return;
      case OUT_OF_MEMORY:
        state = BatchState.OUT_OF_MEMORY;
        return;
      case STOP:
        state = BatchState.STOP;
        return;
      default:
        break;
    }

    this.incomingSchema = incoming.getSchema();
    if (!createAggregator()) {
      state = BatchState.DONE;
    }
    for (final VectorWrapper<?> w : container) {
      w.getValueVector().allocateNew();
    }

    if (complexWriters != null) {
      container.buildSchema(SelectionVectorMode.NONE);
    }
  }

  @Override
  public IterOutcome innerNext() {

    // if a special batch has been sent, we have no data in the incoming so exit early
    if (done || specialBatchSent) {
      assert (!sendEmit); // if special batch sent with emit then flag will not be set
      return NONE;
    }

    // We sent an OK_NEW_SCHEMA and also encountered the end of a data set. So we need to send
    // an EMIT with an empty batch now
    if (sendEmit) {
      first = false; // first is set only in the case when we see a NONE after an empty first (and only) batch
      sendEmit = false;
      firstBatchForDataSet = true;
      firstBatchForSchema = false;
      recordCount = 0;
      specialBatchSent = false;
      return EMIT;
    }

    // this is only called on the first batch. Beyond this, the aggregator manages batches.
    if (aggregator == null || first) {
      if (first && incoming.getRecordCount() > 0) {
        first = false;
        lastKnownOutcome = OK_NEW_SCHEMA;
      } else {
        lastKnownOutcome = next(incoming);
      }
      logger.debug("Next outcome of {}", lastKnownOutcome);
      switch (lastKnownOutcome) {
        case NONE:

          if (first && popConfig.getKeys().size() == 0) {
            // if we have a straight aggregate and empty input batch, we need to handle it in a different way
            // Wewant to produce the special batch only if we got a NONE as the first outcome after
            // OK_NEW_SCHEMA. If we get a NONE immediately after we see an EMIT, then we have already handled
            // the case of the empty batch
            constructSpecialBatch();
            // set state to indicate the fact that we have sent a special batch and input is empty
            specialBatchSent = true;
            // If outcome is NONE then we send the special batch in the first iteration and the NONE
            // outcome in the next iteration. If outcome is EMIT, we can send the special
            // batch and the EMIT outcome at the same time.
            return IterOutcome.OK;
          }
          // else fall thru
        case OUT_OF_MEMORY:
        case NOT_YET:
        case STOP:
          return lastKnownOutcome;
        case OK_NEW_SCHEMA:
          if (!createAggregator()) {
            done = true;
            return IterOutcome.STOP;
          }
          firstBatchForSchema = true;
          break;
        case EMIT:
          // if we get an EMIT with an empty batch as the first (and therefore only) batch
          // we have to do the special handling
          if (firstBatchForDataSet && popConfig.getKeys().size() == 0 && incoming.getRecordCount() == 0) {
            constructSpecialBatch();
            // If outcome is NONE then we send the special batch in the first iteration and the NONE
            // outcome in the next iteration. If outcome is EMIT, we can send the special
            // batch and the EMIT outcome at the same time. (unless the finalOutcome is OK_NEW_SCHEMA)
            return  getFinalOutcome();
          }
          // else fall thru
        case OK:
          break;
        default:
          throw new IllegalStateException(String.format("unknown outcome %s", lastKnownOutcome));
      }
    } else {
      // If this is not the first batch and previous batch is fully processed with no error condition or NONE is not
      // seen then it will call next() on upstream to get new batch. Otherwise just process the previous incoming batch
      if ( lastKnownOutcome != NONE && firstBatchForDataSet && !aggregator.isDone()
        && aggregator.previousBatchProcessed()) {
        lastKnownOutcome = incoming.next();
        if (!first ) {
          //Setup needs to be called again. During setup, generated code saves a reference to the vectors
          // pointed to by the incoming batch so that the de-referencing of the vector wrappers to get to
          // the vectors  does not have to be done at each call to eval. However, after an EMIT is seen,
          // the vectors are replaced and the reference to the old vectors is no longer valid
          try {
            aggregator.setup(oContext, incoming, this, maxOutputRowCount);
          } catch (SchemaChangeException e) {
            UserException.Builder exceptionBuilder = UserException.functionError(e)
                .message("A Schema change exception occured in calling setup() in generated code.");
            throw exceptionBuilder.build(logger);
          }
        }
      }
    }
    AggOutcome aggOutcome = aggregator.doWork(lastKnownOutcome);
    recordCount = aggregator.getOutputCount();
    container.setRecordCount(recordCount);
    logger.debug("Aggregator response {}, records {}", aggOutcome, aggregator.getOutputCount());
    // get the returned IterOutcome from aggregator and based on AggOutcome and returned IterOutcome update the
    // lastKnownOutcome below. For example: if AggOutcome is RETURN_AND_RESET then lastKnownOutcome is always set to
    // EMIT
    IterOutcome returnOutcome = aggregator.getOutcome();
    switch (aggOutcome) {
      case CLEANUP_AND_RETURN:
        if (!first) {
          container.zeroVectors();
        }
        done = true;
        ExternalSortBatch.releaseBatches(incoming);
        return returnOutcome;
      case RETURN_AND_RESET:
        //WE could have got a string of batches, all empty, until we hit an emit
        if (firstBatchForDataSet && popConfig.getKeys().size() == 0 && recordCount == 0) {
          // if we have a straight aggregate and empty input batch, we need to handle it in a different way
          constructSpecialBatch();
          // If outcome is NONE then we send the special batch in the first iteration and the NONE
          // outcome in the next iteration. If outcome is EMIT, we can send the special
          // batch and the EMIT outcome at the same time.
          return getFinalOutcome();
        }
        firstBatchForDataSet = true;
        firstBatchForSchema = false;
        if(first) {
          first = false;
        }
        // Since AggOutcome is RETURN_AND_RESET and returned IterOutcome is OK_NEW_SCHEMA from Aggregator that means it
        // has seen first batch with OK_NEW_SCHEMA and then last batch with EMIT outcome. In that case if all the input
        // batch is processed to produce output batch it need to send and empty batch with EMIT outcome in subsequent
        // next call.
        if(returnOutcome == OK_NEW_SCHEMA) {
          sendEmit = (aggregator == null) || aggregator.previousBatchProcessed();
        }
        // Release external sort batches after EMIT is seen
        ExternalSortBatch.releaseBatches(incoming);
        lastKnownOutcome = EMIT;
        return returnOutcome;
      case RETURN_OUTCOME:
        // In case of complex writer expression, vectors would be added to batch run-time.
        // We have to re-build the schema.
        if (complexWriters != null) {
          container.buildSchema(SelectionVectorMode.NONE);
        }
        if (returnOutcome == IterOutcome.NONE ) {
          lastKnownOutcome = NONE;
          // we will set the 'done' flag in the next call to innerNext and use the lastKnownOutcome
          // to determine whether we should set the flag or not.
          // This is so that if someone calls getRecordCount in between calls to innerNext, we will
          // return the correct record count (if the done flag is set, we will return 0).
          if (first) {
            first = false;
            return OK_NEW_SCHEMA;
          } else {
            return OK;
          }
        } else if (returnOutcome == OK && first) {
          lastKnownOutcome = OK_NEW_SCHEMA;
          returnOutcome = OK_NEW_SCHEMA;
        }
        first = false;
        return returnOutcome;
      case UPDATE_AGGREGATOR:
        // We could get this either between data sets or within a data set.
        // If the former, we can handle the change and so need to update the aggregator and
        // continue. If the latter, we cannot (currently) handle the schema change, so throw
        // and exception
        // This case is not tested since there are no unit tests for this and there is no support
        // from the sort operator for this case
        if (returnOutcome == EMIT) {
          createAggregator();
          lastKnownOutcome = EMIT;
          return OK_NEW_SCHEMA;
        } else {
          context.getExecutorState().fail(UserException.unsupportedError().message(SchemaChangeException
              .schemaChanged("Streaming aggregate does not support schema changes", incomingSchema,
                  incoming.getSchema()).getMessage()).build(logger));
          close();
          killIncoming(false);
          lastKnownOutcome = STOP;
          return IterOutcome.STOP;
        }
      default:
        throw new IllegalStateException(String.format("Unknown state %s.", aggOutcome));
    }
  }

  private void allocateComplexWriters() {
    // Allocate the complex writers before processing the incoming batch
    if (complexWriters != null) {
      for (final BaseWriter.ComplexWriter writer : complexWriters) {
        writer.allocate();
      }
    }
  }

  /**
   * Method is invoked when we have a straight aggregate (no group by expression) and our input is empty.
   * In this case we construct an outgoing batch with record count as 1. For the nullable vectors we don't set anything
   * as we want the output to be NULL. For the required vectors (only for count()) we set the value to be zero since
   * we don't zero out our buffers initially while allocating them.
   */
  private void constructSpecialBatch() {
    int exprIndex = 0;
    for (final VectorWrapper<?> vw: container) {
      final ValueVector vv = vw.getValueVector();
      AllocationHelper.allocateNew(vv, SPECIAL_BATCH_COUNT);
      vv.getMutator().setValueCount(SPECIAL_BATCH_COUNT);
      if (vv.getField().getType().getMode() == TypeProtos.DataMode.REQUIRED) {
        if (vv instanceof FixedWidthVector) {
          /*
           * The only case we should have a required vector in the aggregate is for count function whose output is
           * always a FixedWidthVector (BigIntVector). Zero out the vector.
           */
          ((FixedWidthVector) vv).zeroVector();
        } else {
          /*
           * If we are in this else block it means that we have a required vector which is of variable length. We
           * should not be here, raising an error since we have set the record count to be 1 and not cleared the
           * buffer
           */
          throw new DrillRuntimeException("FixedWidth vectors is the expected output vector type. " +
              "Corresponding expression: " + popConfig.getExprs().get(exprIndex).toString());
        }
      }
      exprIndex++;
    }
    container.setRecordCount(SPECIAL_BATCH_COUNT);
    recordCount = SPECIAL_BATCH_COUNT;
  }

  /**
   * Creates a new Aggregator based on the current schema. If setup fails, this method is responsible for cleaning up
   * and informing the context of the failure state, as well is informing the upstream operators.
   *
   * @return true if the aggregator was setup successfully. false if there was a failure.
   */
  private boolean createAggregator() {
    logger.debug("Creating new aggregator.");
    try {
      stats.startSetup();
      this.aggregator = createAggregatorInternal();
      return true;
    } catch (SchemaChangeException | ClassTransformationException | IOException ex) {
      context.getExecutorState().fail(ex);
      container.clear();
      incoming.kill(false);
      return false;
    } finally {
      stats.stopSetup();
    }
  }

  public void addComplexWriter(final BaseWriter.ComplexWriter writer) {
    complexWriters.add(writer);
  }

  protected StreamingAggregator createAggregatorInternal() throws SchemaChangeException, ClassTransformationException, IOException{
    ClassGenerator<StreamingAggregator> cg = CodeGenerator.getRoot(StreamingAggTemplate.TEMPLATE_DEFINITION, context.getOptions());
    cg.getCodeGenerator().plainJavaCapable(true);
    // Uncomment out this line to debug the generated code.
    //cg.getCodeGenerator().saveCodeForDebugging(true);
    container.clear();

    LogicalExpression[] keyExprs = new LogicalExpression[popConfig.getKeys().size()];
    LogicalExpression[] valueExprs = new LogicalExpression[popConfig.getExprs().size()];
    TypedFieldId[] keyOutputIds = new TypedFieldId[popConfig.getKeys().size()];

    ErrorCollector collector = new ErrorCollectorImpl();

    for (int i = 0; i < keyExprs.length; i++) {
      final NamedExpression ne = popConfig.getKeys().get(i);
      final LogicalExpression expr = ExpressionTreeMaterializer.materialize(ne.getExpr(), incoming, collector,context.getFunctionRegistry() );
      if (expr == null) {
        continue;
      }
      keyExprs[i] = expr;
      final MaterializedField outputField = MaterializedField.create(ne.getRef().getLastSegment().getNameSegment().getPath(),
                                                                      expr.getMajorType());
      final ValueVector vector = TypeHelper.getNewVector(outputField, oContext.getAllocator());
      keyOutputIds[i] = container.add(vector);
    }

    for (int i = 0; i < valueExprs.length; i++) {
      final NamedExpression ne = popConfig.getExprs().get(i);
      final LogicalExpression expr = ExpressionTreeMaterializer.materialize(ne.getExpr(), incoming, collector, context.getFunctionRegistry());
      if (expr instanceof IfExpression) {
        throw UserException.unsupportedError(new UnsupportedOperationException("Union type not supported in aggregate functions")).build(logger);
      }
      if (expr == null) {
        continue;
      }

      /* Populate the complex writers for complex exprs */
      if (expr instanceof DrillFuncHolderExpr &&
          ((DrillFuncHolderExpr) expr).getHolder().isComplexWriterFuncHolder()) {
        // Need to process ComplexWriter function evaluation.
        // Lazy initialization of the list of complex writers, if not done yet.
        if (complexWriters == null) {
          complexWriters = Lists.newArrayList();
        } else {
          complexWriters.clear();
        }
        // The reference name will be passed to ComplexWriter, used as the name of the output vector from the writer.
        ((DrillFuncHolderExpr) expr).getFieldReference(ne.getRef());
        MaterializedField field = MaterializedField.create(ne.getRef().getAsNamePart().getName(), UntypedNullHolder.TYPE);
        container.add(new UntypedNullVector(field, container.getAllocator()));
        valueExprs[i] = expr;
      } else {
        final MaterializedField outputField = MaterializedField.create(ne.getRef().getLastSegment().getNameSegment().getPath(),
            expr.getMajorType());
        ValueVector vector = TypeHelper.getNewVector(outputField, oContext.getAllocator());
        TypedFieldId id = container.add(vector);
        valueExprs[i] = new ValueVectorWriteExpression(id, expr, true);
      }
    }

    if (collector.hasErrors()) {
      throw new SchemaChangeException("Failure while materializing expression. " + collector.toErrorString());
    }

    setupIsSame(cg, keyExprs);
    setupIsSameApart(cg, keyExprs);
    addRecordValues(cg, valueExprs);
    outputRecordKeys(cg, keyOutputIds, keyExprs);
    outputRecordKeysPrev(cg, keyOutputIds, keyExprs);

    cg.getBlock("resetValues")._return(JExpr.TRUE);
    getIndex(cg);

    container.buildSchema(SelectionVectorMode.NONE);
    StreamingAggregator agg = context.getImplementationClass(cg);
    agg.setup(oContext, incoming, this, maxOutputRowCount);
    allocateComplexWriters();
    return agg;
  }

  private final GeneratorMapping IS_SAME = GeneratorMapping.create("setupInterior", "isSame", null, null);
  private final MappingSet IS_SAME_I1 = new MappingSet("index1", null, IS_SAME, IS_SAME);
  private final MappingSet IS_SAME_I2 = new MappingSet("index2", null, IS_SAME, IS_SAME);

  protected void setupIsSame(ClassGenerator<StreamingAggregator> cg, LogicalExpression[] keyExprs) {
    cg.setMappingSet(IS_SAME_I1);
    for (final LogicalExpression expr : keyExprs) {
      // first, we rewrite the evaluation stack for each side of the comparison.
      cg.setMappingSet(IS_SAME_I1);
      final HoldingContainer first = cg.addExpr(expr, ClassGenerator.BlkCreateMode.FALSE);
      cg.setMappingSet(IS_SAME_I2);
      final HoldingContainer second = cg.addExpr(expr, ClassGenerator.BlkCreateMode.FALSE);

      final LogicalExpression fh =
          FunctionGenerationHelper
          .getOrderingComparatorNullsHigh(first, second, context.getFunctionRegistry());
      final HoldingContainer out = cg.addExpr(fh, ClassGenerator.BlkCreateMode.FALSE);
      cg.getEvalBlock()._if(out.getValue().ne(JExpr.lit(0)))._then()._return(JExpr.FALSE);
    }
    cg.getEvalBlock()._return(JExpr.TRUE);
  }

  private final GeneratorMapping IS_SAME_PREV_INTERNAL_BATCH_READ = GeneratorMapping.create("isSamePrev", "isSamePrev", null, null); // the internal batch changes each time so we need to redo setup.
  private final GeneratorMapping IS_SAME_PREV = GeneratorMapping.create("setupInterior", "isSamePrev", null, null);
  private final MappingSet ISA_B1 = new MappingSet("b1Index", null, "b1", null, IS_SAME_PREV_INTERNAL_BATCH_READ, IS_SAME_PREV_INTERNAL_BATCH_READ);
  private final MappingSet ISA_B2 = new MappingSet("b2Index", null, "incoming", null, IS_SAME_PREV, IS_SAME_PREV);

  protected void setupIsSameApart(ClassGenerator<StreamingAggregator> cg, LogicalExpression[] keyExprs) {
    cg.setMappingSet(ISA_B1);
    for (final LogicalExpression expr : keyExprs) {
      // first, we rewrite the evaluation stack for each side of the comparison.
      cg.setMappingSet(ISA_B1);
      final HoldingContainer first = cg.addExpr(expr, ClassGenerator.BlkCreateMode.FALSE);
      cg.setMappingSet(ISA_B2);
      final HoldingContainer second = cg.addExpr(expr, ClassGenerator.BlkCreateMode.FALSE);

      final LogicalExpression fh =
          FunctionGenerationHelper
          .getOrderingComparatorNullsHigh(first, second, context.getFunctionRegistry());
      final HoldingContainer out = cg.addExpr(fh, ClassGenerator.BlkCreateMode.FALSE);
      cg.getEvalBlock()._if(out.getValue().ne(JExpr.lit(0)))._then()._return(JExpr.FALSE);
    }
    cg.getEvalBlock()._return(JExpr.TRUE);
  }

  private final GeneratorMapping EVAL_INSIDE = GeneratorMapping.create("setupInterior", "addRecord", null, null);
  private final GeneratorMapping EVAL_OUTSIDE = GeneratorMapping.create("setupInterior", "outputRecordValues", "resetValues", "cleanup");
  private final MappingSet EVAL = new MappingSet("index", "outIndex", "incoming", "outgoing", EVAL_INSIDE, EVAL_OUTSIDE, EVAL_INSIDE);

  protected void addRecordValues(ClassGenerator<StreamingAggregator> cg, LogicalExpression[] valueExprs) {
    cg.setMappingSet(EVAL);
    for (final LogicalExpression ex : valueExprs) {
      cg.addExpr(ex);
    }
  }

  private final MappingSet RECORD_KEYS = new MappingSet(GeneratorMapping.create("setupInterior", "outputRecordKeys", null, null));

  protected void outputRecordKeys(ClassGenerator<StreamingAggregator> cg, TypedFieldId[] keyOutputIds, LogicalExpression[] keyExprs) {
    cg.setMappingSet(RECORD_KEYS);
    for (int i = 0; i < keyExprs.length; i++) {
      cg.addExpr(new ValueVectorWriteExpression(keyOutputIds[i], keyExprs[i], true));
    }
  }

  private final GeneratorMapping PREVIOUS_KEYS_OUT = GeneratorMapping.create("setupInterior", "outputRecordKeysPrev", null, null);
  private final MappingSet RECORD_KEYS_PREV_OUT = new MappingSet("previousIndex", "outIndex", "previous", "outgoing", PREVIOUS_KEYS_OUT, PREVIOUS_KEYS_OUT);

  private final GeneratorMapping PREVIOUS_KEYS = GeneratorMapping.create("outputRecordKeysPrev", "outputRecordKeysPrev", null, null);
  private final MappingSet RECORD_KEYS_PREV = new MappingSet("previousIndex", "outIndex", "previous", null, PREVIOUS_KEYS, PREVIOUS_KEYS);

  protected void outputRecordKeysPrev(ClassGenerator<StreamingAggregator> cg, TypedFieldId[] keyOutputIds, LogicalExpression[] keyExprs) {
    cg.setMappingSet(RECORD_KEYS_PREV);

    for (int i = 0; i < keyExprs.length; i++) {
      // IMPORTANT: there is an implicit assertion here that the TypedFieldIds for the previous batch and the current batch are the same.  This is possible because InternalBatch guarantees this.
      logger.debug("Writing out expr {}", keyExprs[i]);
      cg.rotateBlock();
      cg.setMappingSet(RECORD_KEYS_PREV);
      final HoldingContainer innerExpression = cg.addExpr(keyExprs[i], ClassGenerator.BlkCreateMode.FALSE);
      cg.setMappingSet(RECORD_KEYS_PREV_OUT);
      cg.addExpr(new ValueVectorWriteExpression(keyOutputIds[i], new HoldingContainerExpression(innerExpression), true), ClassGenerator.BlkCreateMode.FALSE);
    }
  }

  protected void getIndex(ClassGenerator<StreamingAggregator> g) {
    switch (incoming.getSchema().getSelectionVectorMode()) {
    case FOUR_BYTE: {
      JVar var = g.declareClassField("sv4_", g.getModel()._ref(SelectionVector4.class));
      g.getBlock("setupInterior").assign(var, JExpr.direct("incoming").invoke("getSelectionVector4"));
      g.getBlock("getVectorIndex")._return(var.invoke("get").arg(JExpr.direct("recordIndex")));
      return;
    }
    case NONE: {
      g.getBlock("getVectorIndex")._return(JExpr.direct("recordIndex"));
      return;
    }
    case TWO_BYTE: {
      JVar var = g.declareClassField("sv2_", g.getModel()._ref(SelectionVector2.class));
      g.getBlock("setupInterior").assign(var, JExpr.direct("incoming").invoke("getSelectionVector2"));
      g.getBlock("getVectorIndex")._return(var.invoke("getIndex").arg(JExpr.direct("recordIndex")));
      return;
    }

    default:
      throw new IllegalStateException();
    }
  }

  private IterOutcome getFinalOutcome() {
    IterOutcome outcomeToReturn;

    if (firstBatchForDataSet) {
      firstBatchForDataSet = false;
    }
    if (firstBatchForSchema) {
      outcomeToReturn = OK_NEW_SCHEMA;
      sendEmit = true;
      firstBatchForSchema = false;
    } else if (lastKnownOutcome == EMIT) {
      firstBatchForDataSet = true;
      outcomeToReturn = EMIT;
    } else {
      outcomeToReturn = (recordCount == 0) ? NONE : OK;
    }
    return outcomeToReturn;
  }

  @Override
  public void close() {
    super.close();
  }

  @Override
  protected void killIncoming(boolean sendUpstream) {
    incoming.kill(sendUpstream);
  }

  @Override
  public void dump() {
    logger.error("StreamingAggBatch[container={}, popConfig={}, aggregator={}, incomingSchema={}]", container, popConfig, aggregator, incomingSchema);
  }

  @VisibleForTesting
  public void setMaxOutputRowCount(int maxOutputRowCount) {
    this.maxOutputRowCount = maxOutputRowCount;
  }
}
