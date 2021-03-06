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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.drill.shaded.guava.com.google.common.base.Function;
import org.apache.drill.shaded.guava.com.google.common.base.Preconditions;
import org.apache.drill.shaded.guava.com.google.common.collect.Lists;
import org.apache.drill.shaded.guava.com.google.common.collect.Maps;
import org.lealone.hansql.common.config.DrillConfig;
import org.lealone.hansql.common.exceptions.ExecutionSetupException;
import org.lealone.hansql.common.exceptions.UserException;
import org.lealone.hansql.common.types.TypeProtos.MinorType;
import org.lealone.hansql.exec.ExecConstants;
import org.lealone.hansql.exec.compile.CodeCompiler;
import org.lealone.hansql.exec.context.DrillbitContext;
import org.lealone.hansql.exec.context.QueryProfileStoreContext;
import org.lealone.hansql.exec.context.options.FragmentOptionManager;
import org.lealone.hansql.exec.context.options.OptionList;
import org.lealone.hansql.exec.context.options.OptionManager;
import org.lealone.hansql.exec.coord.ClusterCoordinator;
import org.lealone.hansql.exec.exception.OutOfMemoryException;
import org.lealone.hansql.exec.expr.fn.FunctionImplementationRegistry;
import org.lealone.hansql.exec.expr.holders.ValueHolder;
import org.lealone.hansql.exec.memory.BufferAllocator;
import org.lealone.hansql.exec.ops.BufferManager;
import org.lealone.hansql.exec.ops.QueryContext.SqlStatementType;
import org.lealone.hansql.exec.physical.base.PhysicalOperator;
import org.lealone.hansql.exec.physical.impl.OperatorCreatorRegistry;
import org.lealone.hansql.exec.planner.PhysicalPlanReader;
import org.lealone.hansql.exec.planner.physical.PlannerSettings;
import org.lealone.hansql.exec.proto.BitControl.PlanFragment;
import org.lealone.hansql.exec.proto.CoordinationProtos.DrillbitEndpoint;
import org.lealone.hansql.exec.proto.ExecProtos.FragmentHandle;
import org.lealone.hansql.exec.proto.UserBitShared.QueryId;
import org.lealone.hansql.exec.proto.helper.QueryIdHelper;
import org.lealone.hansql.exec.session.UserClientConnection;
import org.lealone.hansql.exec.store.PartitionExplorer;
import org.lealone.hansql.exec.store.SchemaConfig;
import org.lealone.hansql.exec.testing.ExecutionControls;
import org.lealone.hansql.exec.util.ImpersonationUtil;
import org.lealone.hansql.exec.work.filter.RuntimeFilterWritable;
import org.lealone.hansql.optimizer.schema.SchemaPlus;

import io.netty.buffer.DrillBuf;

/**
 * <p>
 *   This is the core Context which implements all the Context interfaces:
 *
 *   <ul>
 *     <li>{@link FragmentContext}: A context provided to non-exchange operators.</li>
 *     <li>{@link ExchangeFragmentContext}: A context provided to exchange operators.</li>
 *     <li>{@link RootFragmentContext}: A context provided to fragment roots.</li>
 *     <li>{@link ExecutorFragmentContext}: A context used by the Drillbit.</li>
 *   </ul>
 *
 *   The interfaces above expose resources to varying degrees. They are ordered from most restrictive ({@link FragmentContext})
 *   to least restrictive ({@link ExecutorFragmentContext}).
 * </p>
 * <p>
 *   Since {@link FragmentContextImpl} implements all of the interfaces listed above, the facade pattern is used in order
 *   to cast a {@link FragmentContextImpl} object to the desired interface where-ever it is needed. The facade pattern
 *   is powerful since it allows us to easily create minimal context objects to be used in unit tests. Without
 *   the use of interfaces and the facade pattern we would have to create a complete {@link FragmentContextImpl} object
 *   to unit test any part of the code that depends on a context.
 * </p>
 * <p>
 *  <b>General guideline:</b> Use the most narrow interface for the task. For example, "internal" operators don't need visibility to the networking functionality.
 *  Using the narrow interface allows unit testing without using mocking libraries. Often, the surrounding structure already has exposed the most narrow interface. If there are
 *  opportunities to clean up older code, we can do so as needed to make testing easier.
 * </p>
 */
public class FragmentContextImpl extends BaseFragmentContext implements ExecutorFragmentContext {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(FragmentContextImpl.class);

    private final List<OperatorContextImpl> contexts = Lists.newLinkedList();

    private final DrillbitContext context;
    private final UserClientConnection connection; // is null if this context is for non-root fragment
    private final QueryContext queryContext; // is null if this context is for non-root fragment
    private final FragmentStats stats;
    private final BufferAllocator allocator;
    private final PlanFragment fragment;
    private final ContextInformation contextInformation;
    private final OptionManager fragmentOptions;
    private final BufferManager bufferManager;
    private ExecutorState executorState;
    private final ExecutionControls executionControls;
    private boolean enableRuntimeFilter;
    private boolean enableRFWaiting;
    private Lock lock4RF;
    private Condition condition4RF;

    private final SendingAccountor sendingAccountor = new SendingAccountor();

    private final AccountingUserConnection accountingUserConnection;
    /** Stores constants and their holders by type */
    private final Map<String, Map<MinorType, ValueHolder>> constantValueHolderCache;
    private Map<Long, RuntimeFilterWritable> rfIdentifier2RFW = new ConcurrentHashMap<>();
    private Map<Long, Boolean> rfIdentifier2fetched = new ConcurrentHashMap<>();

    /**
     * Create a FragmentContext instance for non-root fragment.
     *
     * @param dbContext DrillbitContext.
     * @param fragment Fragment implementation.
     * @param funcRegistry FunctionImplementationRegistry.
     * @throws ExecutionSetupException
     */
    public FragmentContextImpl(final DrillbitContext dbContext, final PlanFragment fragment,
            final FunctionImplementationRegistry funcRegistry) throws ExecutionSetupException {
        this(dbContext, fragment, null, null, funcRegistry);
    }

    /**
     * Create a FragmentContext instance for root fragment.
     *
     * @param dbContext DrillbitContext.
     * @param fragment Fragment implementation.
     * @param queryContext QueryContext.
     * @param connection UserClientConnection.
     * @param funcRegistry FunctionImplementationRegistry.
     * @throws ExecutionSetupException
     */
    public FragmentContextImpl(final DrillbitContext dbContext, final PlanFragment fragment,
            final QueryContext queryContext, final UserClientConnection connection,
            final FunctionImplementationRegistry funcRegistry) throws ExecutionSetupException {
        super(funcRegistry);
        this.context = dbContext;
        this.queryContext = queryContext;
        this.connection = connection;
        this.accountingUserConnection = new AccountingUserConnection(connection, sendingAccountor);
        this.fragment = fragment;
        contextInformation = new ContextInformation(fragment.getCredentials(), fragment.getContext());

        logger.debug("Getting initial memory allocation of {}", fragment.getMemInitial());
        logger.debug("Fragment max allocation: {}", fragment.getMemMax());

        final OptionList list;
        if (!fragment.hasOptionsJson() || fragment.getOptionsJson().isEmpty()) {
            list = new OptionList();
        } else {
            try {
                list = dbContext.getLpPersistence().getMapper().readValue(fragment.getOptionsJson(), OptionList.class);
            } catch (final Exception e) {
                throw new ExecutionSetupException("Failure while reading plan options.", e);
            }
        }
        fragmentOptions = new FragmentOptionManager(context.getOptionManager(), list);

        executionControls = new ExecutionControls(fragmentOptions, dbContext.getEndpoint());

        // Add the fragment context to the root allocator.
        // The QueryManager will call the root allocator to recalculate all the memory limits for all the fragments
        try {
            allocator = context.getAllocator().newChildAllocator(
                    "frag:" + QueryIdHelper.getFragmentId(fragment.getHandle()), fragment.getMemInitial(),
                    fragment.getMemMax());
            Preconditions.checkNotNull(allocator, "Unable to acuqire allocator");
        } catch (final OutOfMemoryException e) {
            throw UserException.memoryError(e)
                    .addContext("Fragment", getHandle().getMajorFragmentId() + ":" + getHandle().getMinorFragmentId())
                    .build(logger);
        } catch (final Throwable e) {
            throw new ExecutionSetupException("Failure while getting memory allocator for fragment.", e);
        }

        stats = new FragmentStats(allocator, fragment.getAssignment());
        bufferManager = new BufferManagerImpl(this.allocator);
        constantValueHolderCache = Maps.newHashMap();
        enableRuntimeFilter = this.getOptions().getOption(ExecConstants.HASHJOIN_ENABLE_RUNTIME_FILTER_KEY).bool_val;
        enableRFWaiting = this.getOptions().getOption(ExecConstants.HASHJOIN_RUNTIME_FILTER_WAITING_ENABLE_KEY).bool_val
                && enableRuntimeFilter;
        if (enableRFWaiting) {
            lock4RF = new ReentrantLock();
            condition4RF = lock4RF.newCondition();
        }
    }

    /**
     * TODO: Remove this constructor when removing the SimpleRootExec (DRILL-2097). This is kept only to avoid modifying
     * the long list of test files.
     */
    public FragmentContextImpl(DrillbitContext dbContext, PlanFragment fragment, UserClientConnection connection,
            FunctionImplementationRegistry funcRegistry) throws ExecutionSetupException {
        this(dbContext, fragment, null, connection, funcRegistry);
    }

    @Override
    public OptionManager getOptions() {
        return fragmentOptions;
    }

    @Override
    public PhysicalPlanReader getPlanReader() {
        return context.getPlanReader();
    }

    @Override
    public ClusterCoordinator getClusterCoordinator() {
        return context.getClusterCoordinator();
    }

    @Override
    public QueryProfileStoreContext getProfileStoreContext() {
        return context.getProfileStoreContext();
    }

    @Override
    public void setExecutorState(final ExecutorState executorState) {
        Preconditions.checkArgument(this.executorState == null, "ExecutorState can only be set once.");
        this.executorState = executorState;
    }

    public void fail(final Throwable cause) {
        executorState.fail(cause);
    }

    @Override
    public SchemaPlus getFullRootSchema() {
        if (queryContext == null) {
            fail(new UnsupportedOperationException(
                    "Schema tree can only be created in root fragment. " + "This is a non-root fragment."));
            return null;
        }

        final boolean isImpersonationEnabled = isImpersonationEnabled();
        // If impersonation is enabled, we want to view the schema as query user and suppress authorization errors. As
        // for
        // InfoSchema purpose we want to show tables the user has permissions to list or query. If impersonation is
        // disabled view the schema as Drillbit process user and throw authorization errors to client.
        SchemaConfig schemaConfig = SchemaConfig
                .newBuilder(isImpersonationEnabled ? queryContext.getQueryUserName()
                        : ImpersonationUtil.getProcessUserName(), queryContext)
                .setIgnoreAuthErrors(isImpersonationEnabled).build();

        return queryContext.getFullRootSchema(schemaConfig);
    }

    @Override
    public FragmentStats getStats() {
        return stats;
    }

    @Override
    public Collection<DrillbitEndpoint> getBits() {
        return context.getBits();
    }

    @Override
    public ContextInformation getContextInformation() {
        return contextInformation;
    }

    @Override
    public DrillbitEndpoint getForemanEndpoint() {
        return fragment.getForeman();
    }

    @Override
    public DrillbitEndpoint getEndpoint() {
        return context.getEndpoint();
    }

    @Override
    public OperatorCreatorRegistry getOperatorCreatorRegistry() {
        return context.getOperatorCreatorRegistry();
    }

    @Override
    public ExecutorService getScanDecodeExecutor() {
        return context.getScanDecodeExecutor();
    }

    @Override
    public ExecutorService getScanExecutor() {
        return context.getScanExecutor();
    }

    /**
     * The FragmentHandle for this Fragment
     * @return FragmentHandle
     */
    @Override
    public FragmentHandle getHandle() {
        return fragment.getHandle();
    }

    @Override
    public String getFragIdString() {
        final FragmentHandle handle = getHandle();
        final String frag = handle != null ? handle.getMajorFragmentId() + ":" + handle.getMinorFragmentId() : "0:0";
        return frag;
    }

    @Override
    public boolean isUserAuthenticationEnabled() {
        // TODO(DRILL-2097): Until SimpleRootExec tests are removed, we need to consider impersonation disabled if there
        // is
        // no config
        if (getConfig() == null) {
            return false;
        }

        return getConfig().getBoolean(ExecConstants.USER_AUTHENTICATION_ENABLED);
    }

    @Override
    public void addRuntimeFilter(RuntimeFilterWritable runtimeFilter) {
        long rfIdentifier = runtimeFilter.getRuntimeFilterBDef().getRfIdentifier();
        // if the RF was sent directly from the HJ nodes, we don't need to retain the buffer again
        // as the RuntimeFilterReporter has already retained the buffer
        rfIdentifier2fetched.put(rfIdentifier, false);
        rfIdentifier2RFW.put(rfIdentifier, runtimeFilter);
        if (enableRFWaiting) {
            lock4RF.lock();
            try {
                condition4RF.signal();
            } catch (Exception e) {
                logger.info("fail to signal the waiting thread.", e);
            } finally {
                lock4RF.unlock();
            }
        }
    }

    @Override
    public RuntimeFilterWritable getRuntimeFilter(long rfIdentifier) {
        RuntimeFilterWritable runtimeFilterWritable = rfIdentifier2RFW.get(rfIdentifier);
        if (runtimeFilterWritable != null) {
            rfIdentifier2fetched.put(rfIdentifier, true);
        }
        return runtimeFilterWritable;
    }

    @Override
    public RuntimeFilterWritable getRuntimeFilter(long rfIdentifier, long maxWaitTime, TimeUnit timeUnit) {
        if (rfIdentifier2RFW.get(rfIdentifier) != null) {
            return getRuntimeFilter(rfIdentifier);
        }
        if (enableRFWaiting) {
            lock4RF.lock();
            try {
                if (rfIdentifier2RFW.get(rfIdentifier) == null) {
                    condition4RF.await(maxWaitTime, timeUnit);
                }
            } catch (InterruptedException e) {
                logger.info("Condition was interrupted", e);
            } finally {
                lock4RF.unlock();
            }
        }
        return getRuntimeFilter(rfIdentifier);
    }

    /**
     * Get this fragment's allocator.
     * @return the allocator
     */
    @Override
    @Deprecated
    public BufferAllocator getAllocator() {
        if (allocator == null) {
            logger.debug("Fragment: " + getFragIdString() + " Allocator is NULL");
        }
        return allocator;
    }

    @Override
    public BufferAllocator getRootAllocator() {
        return context.getAllocator();
    }

    @Override
    public BufferAllocator getNewChildAllocator(final String operatorName, final int operatorId,
            final long initialReservation, final long maximumReservation) throws OutOfMemoryException {
        return allocator.newChildAllocator(
                "op:" + QueryIdHelper.getFragmentId(fragment.getHandle()) + ":" + operatorId + ":" + operatorName,
                initialReservation, maximumReservation);
    }

    public boolean isOverMemoryLimit() {
        return allocator.isOverLimit();
    }

    @Override
    public CodeCompiler getCompiler() {
        return context.getCompiler();
    }

    @Override
    public AccountingUserConnection getUserDataTunnel() {
        Preconditions.checkState(connection != null, "Only Root fragment can get UserDataTunnel");
        return accountingUserConnection;
    }

    @Override
    public OperatorContext newOperatorContext(PhysicalOperator popConfig, OperatorStats stats)
            throws OutOfMemoryException {
        OperatorContextImpl context = new OperatorContextImpl(popConfig, this, stats);
        contexts.add(context);
        return context;
    }

    @Override
    public OperatorContext newOperatorContext(PhysicalOperator popConfig) throws OutOfMemoryException {
        OperatorContextImpl context = new OperatorContextImpl(popConfig, this);
        contexts.add(context);
        return context;
    }

    @Override
    public DrillConfig getConfig() {
        return context.getConfig();
    }

    @Override
    public ExecutorState getExecutorState() {
        return executorState;
    }

    @Override
    public ExecutionControls getExecutionControls() {
        return executionControls;
    }

    @Override
    public String getQueryUserName() {
        return fragment.getCredentials().getUserName();
    }

    @Override
    public QueryId getQueryId() {
        return fragment.getHandle().getQueryId();
    }

    @Override
    public String getQueryIdString() {
        return QueryIdHelper.getQueryId(getQueryId());
    }

    @Override
    public boolean isImpersonationEnabled() {
        // TODO(DRILL-2097): Until SimpleRootExec tests are removed, we need to consider impersonation disabled if there
        // is
        // no config
        if (getConfig() == null) {
            return false;
        }

        return getConfig().getBoolean(ExecConstants.IMPERSONATION_ENABLED);
    }

    @Override
    public void close() {
        waitForSendComplete();

        // Close the buffers before closing the operators; this is needed as buffer ownership
        // is attached to the receive operators.
        // suppressingClose(buffers);
        closeNotConsumedRFWs();
        // close operator context
        for (OperatorContextImpl opContext : contexts) {
            suppressingClose(opContext);
        }
        suppressingClose(bufferManager);
        suppressingClose(allocator);
    }

    private void suppressingClose(final AutoCloseable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (final Exception e) {
            fail(e);
        }
    }

    @Override
    public PartitionExplorer getPartitionExplorer() {
        throw new UnsupportedOperationException(String.format("The partition explorer interface can only be used "
                + "in functions that can be evaluated at planning time. Make sure that the %s configuration "
                + "option is set to true.", PlannerSettings.CONSTANT_FOLDING.getOptionName()));
    }

    @Override
    public ValueHolder getConstantValueHolder(String value, MinorType type,
            Function<DrillBuf, ValueHolder> holderInitializer) {
        if (!constantValueHolderCache.containsKey(value)) {
            constantValueHolderCache.put(value, Maps.<MinorType, ValueHolder> newHashMap());
        }

        Map<MinorType, ValueHolder> holdersByType = constantValueHolderCache.get(value);
        ValueHolder valueHolder = holdersByType.get(type);
        if (valueHolder == null) {
            valueHolder = holderInitializer.apply(getManagedBuffer());
            holdersByType.put(type, valueHolder);
        }
        return valueHolder;
    }

    @Override
    public ExecutorService getExecutor() {
        return context.getExecutor();
    }

    @Override
    public void waitForSendComplete() {
        sendingAccountor.waitForSendComplete();
    }

    public boolean isBuffersDone() {
        return false;
    }

    @Override
    protected BufferManager getBufferManager() {
        return bufferManager;
    }

    private void closeNotConsumedRFWs() {
        for (RuntimeFilterWritable runtimeFilterWritable : rfIdentifier2RFW.values()) {
            long rfIdentifier = runtimeFilterWritable.getRuntimeFilterBDef().getRfIdentifier();
            boolean fetchedByOperator = rfIdentifier2fetched.get(rfIdentifier);
            if (!fetchedByOperator) {
                // if the RF hasn't been consumed by the operator, we have to released it one more time.
                runtimeFilterWritable.close();
            }
        }
    }

    @Override
    public SqlStatementType getSQLStatementType() {
        Preconditions.checkNotNull(queryContext,
                "Statement type is only valid for root fragment." + " Calling from non-root fragment");
        return queryContext.getSQLStatementType();
    }
}
