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
package org.lealone.hansql.exec.coord;

import java.util.Collection;

import org.lealone.hansql.exec.coord.store.TransientStore;
import org.lealone.hansql.exec.coord.store.TransientStoreConfig;
import org.lealone.hansql.exec.proto.CoordinationProtos.DrillbitEndpoint;
import org.lealone.hansql.exec.proto.CoordinationProtos.DrillbitEndpoint.State;

/**
 * Pluggable interface built to manage cluster coordination. Allows Drillbit or DrillClient to register its capabilities
 * as well as understand other node's existence and capabilities.
 **/
public abstract class ClusterCoordinator implements AutoCloseable {
    /**
     * Start the cluster coordinator. Millis to wait is
     *
     * @param millisToWait
     *          The maximum time to wait before throwing an exception if the
     *          cluster coordination service has not successfully started. Use 0
     *          to wait indefinitely.
     * @throws Exception
     */
    public abstract void start(long millisToWait) throws Exception;

    public abstract RegistrationHandle register(DrillbitEndpoint data);

    public abstract void unregister(RegistrationHandle handle);

    /**
     * Get a collection of available Drillbit endpoints, Thread-safe.
     * Could be slightly out of date depending on refresh policy.
     *
     * @return A collection of available endpoints.
     */
    public abstract Collection<DrillbitEndpoint> getAvailableEndpoints();

    /**
     * Get a collection of ONLINE drillbit endpoints by excluding the drillbits
     * that are in QUIESCENT state (drillbits that are shutting down). Primarily used by the planner
     * to plan queries only on ONLINE drillbits and used by the client during initial connection
     * phase to connect to a drillbit (foreman)
     * @return A collection of ONLINE endpoints
     */

    public abstract Collection<DrillbitEndpoint> getOnlineEndPoints();

    public abstract RegistrationHandle update(RegistrationHandle handle, State state);

    public interface RegistrationHandle {
        /**
         * Get the drillbit endpoint associated with the registration handle
         * @return drillbit endpoint
         */
        public abstract DrillbitEndpoint getEndPoint();

        public abstract void setEndPoint(DrillbitEndpoint endpoint);
    }

    /**
     * Returns a {@link TransientStore store} instance with the given {@link TransientStoreConfig configuration}.
     *
     * Note that implementor might cache the instance so new instance creation is not guaranteed.
     *
     * @param config  store configuration
     * @param <V>  value type for this store
     */
    public abstract <V> TransientStore<V> getOrCreateTransientStore(TransientStoreConfig<V> config);

    public boolean isDrillbitInState(DrillbitEndpoint endpoint, DrillbitEndpoint.State state) {
        return (!endpoint.hasState() || endpoint.getState().equals(state));
    }

}
