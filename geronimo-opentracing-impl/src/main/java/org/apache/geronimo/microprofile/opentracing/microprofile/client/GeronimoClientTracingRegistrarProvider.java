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
package org.apache.geronimo.microprofile.opentracing.microprofile.client;

import java.util.concurrent.ExecutorService;

import javax.enterprise.inject.spi.CDI;
import javax.ws.rs.client.ClientBuilder;

import org.eclipse.microprofile.opentracing.ClientTracingRegistrarProvider;

import io.opentracing.Tracer;

public class GeronimoClientTracingRegistrarProvider implements ClientTracingRegistrarProvider {

    private final OpenTracingClientRequestFilter requestFilter;

    private final OpenTracingClientResponseFilter responseFilter;

    private final Tracer tracer;

    public GeronimoClientTracingRegistrarProvider() {
        final CDI<Object> cdi = CDI.current();
        requestFilter = cdi.select(OpenTracingClientRequestFilter.class).get();
        responseFilter = cdi.select(OpenTracingClientResponseFilter.class).get();
        tracer = cdi.select(Tracer.class).get();
    }

    @Override
    public ClientBuilder configure(final ClientBuilder builder) {
        return builder.register(requestFilter).register(responseFilter);
    }

    @Override
    public ClientBuilder configure(final ClientBuilder builder, final ExecutorService executorService) {
        final ExecutorService executor = wrapExecutor(executorService);
        return configure(builder).property("executorService" /* cxf */, executor);
    }

    private ExecutorService wrapExecutor(final ExecutorService executorService) {
        return new OpenTracingExecutorService(executorService, tracer);
    }
}
