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
package org.apache.geronimo.microprofile.opentracing.common.impl;

import static java.util.Collections.list;
import static java.util.Optional.ofNullable;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MultivaluedMap;

import org.apache.geronimo.microprofile.opentracing.common.config.GeronimoOpenTracingConfig;
import org.apache.geronimo.microprofile.opentracing.common.spi.Bus;

import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;

public class GeronimoTracer implements Tracer {
    private ScopeManager scopeManager;
    private IdGenerator idGenerator;
    private Bus<FinishedSpan> finishedSpanEvent;
    private GeronimoOpenTracingConfig config;

    private String parentSpanIdHeader;
    private String spanIdHeader;
    private String traceIdHeader;
    private String baggageHeaderPrefix;

    public void init() {
        parentSpanIdHeader = config.read("propagation.headers.parentSpanId", "X-B3-ParentSpanId");
        spanIdHeader = config.read("propagation.headers.spanId", "X-B3-SpanId");
        traceIdHeader = config.read("propagation.headers.traceId", "X-B3-TraceId");
        baggageHeaderPrefix = config.read("propagation.headers.baggagePrefix", "baggage-");
    }

    public void setScopeManager(final ScopeManager scopeManager) {
        this.scopeManager = scopeManager;
    }

    public void setIdGenerator(final IdGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }

    public void setFinishedSpanEvent(final Bus<FinishedSpan> finishedSpanEvent) {
        this.finishedSpanEvent = finishedSpanEvent;
    }

    public void setConfig(final GeronimoOpenTracingConfig config) {
        this.config = config;
    }

    @Override
    public ScopeManager scopeManager() {
        return scopeManager;
    }

    @Override
    public Span activeSpan() {
        return ofNullable(scopeManager.active()).map(Scope::span).orElse(null);
    }

    @Override
    public SpanBuilder buildSpan(final String operationName) {
        return new SpanBuilderImpl(
                this,
                span -> finishedSpanEvent.fire(new FinishedSpan(processNewSpan(span))), operationName, idGenerator);
    }

    @Override
    public <C> void inject(final SpanContext spanContext, final Format<C> format, final C carrier) {
        if (!TextMap.class.isInstance(carrier)) {
            throw new IllegalArgumentException("Only TextMap are supported");
        }
        final TextMap textMap = TextMap.class.cast(carrier);
        final SpanContextImpl context = SpanContextImpl.class.cast(spanContext);
        textMap.put(traceIdHeader, String.valueOf(context.getTraceId()));
        textMap.put(spanIdHeader, String.valueOf(context.getSpanId()));
        context.getBaggageItems().forEach((k, v) -> textMap.put(baggageHeaderPrefix + k, v));
    }

    @Override
    public <C> SpanContext extract(final Format<C> format, final C carrier) {
        if (JaxRsHeaderTextMap.class.isInstance(carrier)) {
            final MultivaluedMap<String, ?> map = JaxRsHeaderTextMap.class.cast(carrier).getMap();
            final String traceid = (String) map.getFirst(traceIdHeader);
            final String spanid = (String) map.getFirst(spanIdHeader);
            final String parentspanid = (String) map.getFirst(parentSpanIdHeader);
            if (traceid != null && spanid != null) {
                return newContext(traceid, parentspanid, spanid, map.keySet().stream().filter(it -> it.startsWith(baggageHeaderPrefix))
                        .collect(toMap(identity(), k -> String.valueOf(map.getFirst(k)))));
            }
            return null;
        }
        if (ServletHeaderTextMap.class.isInstance(carrier)) {
            final HttpServletRequest req = ServletHeaderTextMap.class.cast(carrier).getRequest();
            final String traceid = req.getHeader(traceIdHeader);
            final String spanid = req.getHeader(spanIdHeader);
            final String parentspanid = req.getHeader(parentSpanIdHeader);
            if (traceid != null && spanid != null) {
                return newContext(traceid, parentspanid, spanid, list(req.getHeaderNames()).stream()
                        .filter(it -> it.startsWith(baggageHeaderPrefix))
                        .collect(toMap(identity(), k -> String.valueOf(req.getHeader(k)))));
            }
            return null;
        }
        if (!TextMap.class.isInstance(carrier)) {
            throw new IllegalArgumentException("Only TextMap are supported");
        }
        final Iterator<Map.Entry<String, String>> textMap = TextMap.class.cast(carrier).iterator();
        String traceId = null;
        String spanId = null;
        String parentSpanId = null;
        final Map<String, String> baggages = new HashMap<>();
        while (textMap.hasNext()) {
            final Map.Entry<String, String> next = textMap.next();
            if (next.getKey().startsWith(baggageHeaderPrefix)) {
                baggages.put(next.getKey(), next.getValue());
            } else if (spanIdHeader.equals(next.getKey())) {
                spanId = next.getValue();
            } else if (traceIdHeader.equals(next.getKey())) {
                traceId = next.getValue();
            } else if (parentSpanIdHeader.equals(next.getKey())) {
                parentSpanId = next.getValue();
            }
        }
        if (traceId != null && spanId != null) {
            return newContext(traceId, parentSpanId, spanId, baggages);
        }
        return null;
    }

    protected Span processNewSpan(final SpanImpl span) {
        return span;
    }

    protected SpanContextImpl newContext(final Object traceId, final Object parentSpanId,
                                         final Object spanId, final Map<String, String> baggages) {
        return new SpanContextImpl(traceId, parentSpanId, spanId, baggages);
    }
}
