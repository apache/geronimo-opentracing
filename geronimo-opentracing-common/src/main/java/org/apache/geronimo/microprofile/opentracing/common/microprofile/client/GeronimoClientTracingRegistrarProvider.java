/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.geronimo.microprofile.opentracing.common.microprofile.client;

import java.util.concurrent.ExecutorService;

import jakarta.ws.rs.client.ClientBuilder;

import org.apache.geronimo.microprofile.opentracing.common.microprofile.thread.OpenTracingExecutorService;
import org.apache.geronimo.microprofile.opentracing.common.spi.Container;
import org.eclipse.microprofile.opentracing.ClientTracingRegistrarProvider;

import io.opentracing.Tracer;

public class GeronimoClientTracingRegistrarProvider implements ClientTracingRegistrarProvider {

    private final OpenTracingClientRequestFilter requestFilter;

    private final OpenTracingClientResponseFilter responseFilter;

    private final Tracer tracer;

    public GeronimoClientTracingRegistrarProvider() {
        final Container container = Container.get();
        requestFilter = container.lookup(OpenTracingClientRequestFilter.class);
        responseFilter = container.lookup(OpenTracingClientResponseFilter.class);
        tracer = container.lookup(Tracer.class);
    }

    @Override
    public ClientBuilder configure(final ClientBuilder builder) {
        return configure(builder, new SyncExecutor());
    }

    @Override
    public ClientBuilder configure(final ClientBuilder builder, final ExecutorService executorService) {
        if (builder.getConfiguration().getInstances().stream().anyMatch(it -> requestFilter == it)) {
            return builder;
        }
        return builder.register(requestFilter).register(responseFilter)
                .executorService(wrapExecutor(executorService));
    }

    private ExecutorService wrapExecutor(final ExecutorService executorService) {
        if (OpenTracingExecutorService.class.isInstance(executorService)) {
            return executorService;
        }
        return new OpenTracingExecutorService(executorService, tracer);
    }
}
