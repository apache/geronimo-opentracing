/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.eclipse.microprofile.opentracing;

import java.util.ServiceLoader;
import java.util.concurrent.ExecutorService;
import javax.ws.rs.client.ClientBuilder;

/**
 * Allows a user to activate tracing on a client builder.
 */
public final class ClientTracingRegistrar {
    /**
     * @param cb the client builder to active tracing on.
     * @return the same client builder with tracing activated.
     */
    public static ClientBuilder configure(final ClientBuilder cb) {
        return ServiceLoader.load(ClientTracingRegistrarProvider.class).iterator().next().configure(cb);
    }

    /**
     * @param cb the client builder to active tracing on.
     * @param executorService the executor service the client will use.
     * @return the same client builder with tracing activated.
     */
    public static ClientBuilder configure(final ClientBuilder cb, final ExecutorService executorService) {
        return ServiceLoader.load(ClientTracingRegistrarProvider.class).iterator().next().configure(cb, executorService);
    }

    private ClientTracingRegistrar() {
        // no-op
    }
}
