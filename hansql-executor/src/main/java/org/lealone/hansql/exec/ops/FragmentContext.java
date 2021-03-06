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
package org.lealone.hansql.exec.ops;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.drill.shaded.guava.com.google.common.annotations.VisibleForTesting;
import org.lealone.hansql.common.config.DrillConfig;
import org.lealone.hansql.exec.compile.CodeCompiler;
import org.lealone.hansql.exec.context.options.OptionManager;
import org.lealone.hansql.exec.exception.ClassTransformationException;
import org.lealone.hansql.exec.expr.ClassGenerator;
import org.lealone.hansql.exec.expr.CodeGenerator;
import org.lealone.hansql.exec.expr.fn.FunctionLookupContext;
import org.lealone.hansql.exec.memory.BufferAllocator;
import org.lealone.hansql.exec.ops.QueryContext.SqlStatementType;
import org.lealone.hansql.exec.physical.base.PhysicalOperator;
import org.lealone.hansql.exec.proto.ExecProtos;
import org.lealone.hansql.exec.proto.UserBitShared.QueryId;
import org.lealone.hansql.exec.testing.ExecutionControls;
import org.lealone.hansql.exec.work.filter.RuntimeFilterWritable;
import org.lealone.hansql.optimizer.schema.SchemaPlus;

import io.netty.buffer.DrillBuf;

/**
 * Provides the resources required by a non-exchange operator to execute.
 */
public interface FragmentContext extends UdfUtilities, AutoCloseable {
  /**
   * Returns the UDF registry.
   * @return the UDF registry
   */
  FunctionLookupContext getFunctionRegistry();

  /**
   * Returns a read-only version of the session options.
   * @return the session options
   */
  OptionManager getOptions();

  boolean isImpersonationEnabled();

  /**
   * Generates code for a class given a {@link ClassGenerator},
   * and returns a single instance of the generated class. (Note
   * that the name is a misnomer, it would be better called
   * <tt>getImplementationInstance</tt>.)
   *
   * @param cg the class generator
   * @return an instance of the generated class
   */
  <T> T getImplementationClass(final ClassGenerator<T> cg)
      throws ClassTransformationException, IOException;

  /**
   * Generates code for a class given a {@link CodeGenerator},
   * and returns a single instance of the generated class. (Note
   * that the name is a misnomer, it would be better called
   * <tt>getImplementationInstance</tt>.)
   *
   * @param cg the code generator
   * @return an instance of the generated class
   */
  <T> T getImplementationClass(final CodeGenerator<T> cg)
      throws ClassTransformationException, IOException;

  /**
   * Generates code for a class given a {@link ClassGenerator}, and returns the
   * specified number of instances of the generated class. (Note that the name
   * is a misnomer, it would be better called
   * <tt>getImplementationInstances</tt>.)
   *
   * @param cg the class generator
   * @return list of instances of the generated class
   */
  <T> List<T> getImplementationClass(final ClassGenerator<T> cg, final int instanceCount)
      throws ClassTransformationException, IOException;

  /**
   * Returns the statement type (e.g. SELECT, CTAS, ANALYZE) from the query context.
   * @return query statement type {@link SqlStatementType}, if known.
   */
  public SqlStatementType getSQLStatementType();

  /**
   * Get this node's identity.
   * @return A DrillbitEndpoint object.
   */
  <T> List<T> getImplementationClass(final CodeGenerator<T> cg, final int instanceCount)
      throws ClassTransformationException, IOException;

  /**
   * Return the set of execution controls used to inject faults into running
   * code for testing.
   *
   * @return the execution controls
   */
  ExecutionControls getExecutionControls();

  /**
   * Returns the Drill configuration for this run. Note that the config is
   * global and immutable.
   *
   * @return the Drill configuration
   */
  DrillConfig getConfig();

  CodeCompiler getCompiler();

  ExecutorService getScanDecodeExecutor();

  ExecutorService getScanExecutor();

  ExecutorService getExecutor();

  ExecutorState getExecutorState();

  BufferAllocator getNewChildAllocator(final String operatorName,
                                       final int operatorId,
                                       final long initialReservation,
                                       final long maximumReservation);

  ExecProtos.FragmentHandle getHandle();

  BufferAllocator getAllocator();

  /**
   * @return ID {@link java.util.UUID} of the current query
   */
  public QueryId getQueryId();

  /**
   * @return The string representation of the ID {@link java.util.UUID} of the current query
   */
  public String getQueryIdString();

  OperatorContext newOperatorContext(PhysicalOperator popConfig);

  OperatorContext newOperatorContext(PhysicalOperator popConfig, OperatorStats stats);

  SchemaPlus getFullRootSchema();

  String getQueryUserName();

  String getFragIdString();

  DrillBuf replace(DrillBuf old, int newSize);

  @Override
  DrillBuf getManagedBuffer();

  DrillBuf getManagedBuffer(int size);

  @Override
  void close();
  /**
   * add a RuntimeFilter when the RuntimeFilter receiver belongs to the same MinorFragment
   * @param runtimeFilter
   */
  public void addRuntimeFilter(RuntimeFilterWritable runtimeFilter);

  public RuntimeFilterWritable getRuntimeFilter(long rfIdentifier);

  /**
   * get the RuntimeFilter with a blocking wait, if the waiting option is enabled
   * @param rfIdentifier
   * @param maxWaitTime
   * @param timeUnit
   * @return the RFW or null
   */
  public RuntimeFilterWritable getRuntimeFilter(long rfIdentifier, long maxWaitTime, TimeUnit timeUnit);

  interface ExecutorState {
    /**
     * Tells individual operations whether they should continue. In some cases, an external event (typically cancellation)
     * will mean that the fragment should prematurely exit execution. Long running operations should check this every so
     * often so that Drill is responsive to cancellation operations.
     *
     * @return False if the action should terminate immediately, true if everything is okay.
     */
    boolean shouldContinue();

    /**
     * Inform the executor if a exception occurs and fragment should be failed.
     *
     * @param t
     *          The exception that occurred.
     */
    void fail(final Throwable t);

    @VisibleForTesting
    @Deprecated
    boolean isFailed();

    @VisibleForTesting
    @Deprecated
    Throwable getFailureCause();
  }
}
