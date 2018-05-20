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

import static io.opentracing.References.CHILD_OF;
import static java.util.Optional.ofNullable;

import java.util.function.Consumer;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;

import org.apache.geronimo.microprofile.opentracing.impl.HeaderTextMap;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;

@ApplicationScoped
public class OpenTracingClientRequestFilter implements ClientRequestFilter {

    @Inject
    private Tracer tracer;

    @Override
    public void filter(final ClientRequestContext context) {
        if (context.getProperty(OpenTracingClientRequestFilter.class.getName()) != null || "true"
                .equalsIgnoreCase(String.valueOf(context.getProperty("org.apache.geronimo.microprofile.opentracing.skip")))) {
            return;
        }

        final Tracer.SpanBuilder builder = tracer.buildSpan(context.getMethod());
        builder.withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT);
        builder.withTag("component", "jaxrs");

        ofNullable(SpanContext.class.cast(context.getProperty(CHILD_OF)))
                .ifPresent(parent -> builder.ignoreActiveSpan().asChildOf(parent));

        final Span span = builder.start();
        if (span == null) {
            return;
        }

        span.setOperationName(context.getUri().getPath());
        if (!"true".equalsIgnoreCase(
                String.valueOf(context.getProperty("org.apache.geronimo.microprofile.opentracing.client.skipDefaultSpanTags")))) {
            Tags.HTTP_METHOD.set(span, context.getMethod());
            Tags.HTTP_URL.set(span, context.getUri().toASCIIString());
            Tags.PEER_HOSTNAME.set(span, context.getUri().getHost());
            Tags.PEER_PORT.set(span, context.getUri().getPort());
        }
        // customization point
        ofNullable(context.getProperty("org.apache.geronimo.microprofile.opentracing.spanConsumer"))
                .ifPresent(consumer -> Consumer.class.cast(consumer).accept(span));

        tracer.inject(span.context(), Format.Builtin.HTTP_HEADERS, new HeaderTextMap<>(context.getHeaders()));
        context.setProperty(OpenTracingClientRequestFilter.class.getName(), span);

    }
}
