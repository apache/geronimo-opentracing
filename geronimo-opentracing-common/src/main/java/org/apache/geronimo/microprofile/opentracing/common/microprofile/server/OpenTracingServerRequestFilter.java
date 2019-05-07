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
package org.apache.geronimo.microprofile.opentracing.common.microprofile.server;

import static java.util.Optional.ofNullable;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;

import org.apache.geronimo.microprofile.opentracing.common.impl.JaxRsHeaderTextMap;
import org.apache.geronimo.microprofile.opentracing.common.microprofile.client.OpenTracingClientRequestFilter;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;

public class OpenTracingServerRequestFilter implements ContainerRequestFilter {

    private final String operationName;

    private final Tracer tracer;

    private final boolean skip;

    private final boolean skipDefaultTags;

    public OpenTracingServerRequestFilter(final String operationName, final Tracer tracer,
                                          final boolean skip, final boolean skipDefaultTags) {
        this.operationName = operationName;
        this.tracer = tracer;
        this.skip = skip;
        this.skipDefaultTags = skipDefaultTags;
    }

    @Override
    public void filter(final ContainerRequestContext context) {
        if (context.getProperty(OpenTracingFilter.class.getName()) != null || skip) {
            return;
        }

        final Tracer.SpanBuilder builder = tracer.buildSpan(operationName);
        builder.withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER);
        builder.withTag("component", "jaxrs");

        ofNullable(ofNullable(tracer.activeSpan()).map(Span::context)
                .orElseGet(() -> tracer.extract(Format.Builtin.HTTP_HEADERS, new JaxRsHeaderTextMap<>(context.getHeaders()))))
                .ifPresent(builder::asChildOf);

        final Scope scope = builder.startActive(true);
        final Span span = scope.span();

        if (!skipDefaultTags) {
            Tags.HTTP_METHOD.set(span, context.getMethod());
            Tags.HTTP_URL.set(span, context.getUriInfo().getRequestUri().toASCIIString());
        }

        context.setProperty(OpenTracingFilter.class.getName(), scope);
    }
}
