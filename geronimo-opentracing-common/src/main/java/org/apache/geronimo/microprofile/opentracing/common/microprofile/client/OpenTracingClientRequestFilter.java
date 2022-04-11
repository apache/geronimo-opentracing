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

import static io.opentracing.References.CHILD_OF;
import static java.util.Optional.ofNullable;

import java.util.function.Consumer;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;

import org.apache.geronimo.microprofile.opentracing.common.config.GeronimoOpenTracingConfig;
import org.apache.geronimo.microprofile.opentracing.common.impl.JaxRsHeaderTextMap;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;

// @ApplicationScoped
public class OpenTracingClientRequestFilter implements ClientRequestFilter {

    private Tracer tracer;
    private GeronimoOpenTracingConfig config;

    private boolean skip;
    private boolean skipDefaultTags;
    private boolean skipPeerTags;

    public void init() {
        skip = Boolean.parseBoolean(config.read("client.filter.request.skip", "false"));
        skipDefaultTags = Boolean.parseBoolean(config.read("client.filter.request.skipDefaultTags", "false"));
        skipPeerTags = Boolean.parseBoolean(config.read("client.filter.request.skipPeerTags", "false"));
    }

    public void setTracer(final Tracer tracer) {
        this.tracer = tracer;
    }

    public void setConfig(final GeronimoOpenTracingConfig config) {
        this.config = config;
    }

    @Override
    public void filter(final ClientRequestContext context) {
        if (context.getProperty(OpenTracingClientRequestFilter.class.getName()) != null || skip) {
            return;
        }

        final Tracer.SpanBuilder builder = tracer.buildSpan(context.getMethod());
        builder.withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT);
        builder.withTag("component", "jaxrs");

        ofNullable(SpanContext.class.cast(context.getProperty(CHILD_OF)))
                .ifPresent(parent -> builder.ignoreActiveSpan().asChildOf(parent));

        final Span span = builder.start();
        if (!skipDefaultTags) {
            Tags.HTTP_METHOD.set(span, context.getMethod());
            Tags.HTTP_URL.set(span, context.getUri().toASCIIString());
        }
        if (!skipPeerTags) {
            final String host = context.getUri().getHost();
            Tags.PEER_HOSTNAME.set(span, host);
            Tags.PEER_PORT.set(span, context.getUri().getPort());
        }
        // customization point
        ofNullable(context.getProperty("org.apache.geronimo.microprofile.opentracing.spanConsumer"))
                .ifPresent(consumer -> Consumer.class.cast(consumer).accept(span));

        tracer.inject(span.context(), Format.Builtin.HTTP_HEADERS, new JaxRsHeaderTextMap<>(context.getHeaders()));
        context.setProperty(OpenTracingClientRequestFilter.class.getName(), span);

    }
}
