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
package org.apache.geronimo.microprofile.opentracing.impl;

import static java.util.Optional.ofNullable;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.ws.rs.core.MultivaluedMap;

import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;

@ApplicationScoped
public class GeronimoTracer implements Tracer {

    @Inject
    private ScopeManager scopeManager;

    @Inject
    private IdGenerator idGenerator;

    @Inject
    private Event<FinishedSpan> finishedSpanEvent;

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
        return new SpanBuilderImpl(this, span -> finishedSpanEvent.fire(new FinishedSpan(span)), operationName, idGenerator);
    }

    @Override
    public <C> void inject(final SpanContext spanContext, final Format<C> format, final C carrier) {
        if (!TextMap.class.isInstance(carrier)) {
            throw new IllegalArgumentException("Only TextMap are supported");
        }
        final TextMap textMap = TextMap.class.cast(carrier);
        final SpanContextImpl context = SpanContextImpl.class.cast(spanContext);
        textMap.put("traceid", String.valueOf(context.getTraceId()));
        textMap.put("spanid", String.valueOf(context.getSpanId()));
        context.getBaggageItems().forEach((k, v) -> textMap.put("baggage-" + k, v));
    }

    @Override
    public <C> SpanContext extract(final Format<C> format, final C carrier) {
        if (HeaderTextMap.class.isInstance(carrier)) {
            final MultivaluedMap<String, ?> map = HeaderTextMap.class.cast(carrier).getMap();
            final String traceid = (String) map.getFirst("traceid");
            final String spanid = (String) map.getFirst("spanid");
            if (traceid != null && spanid != null) {
                return new SpanContextImpl(traceid, spanid, map.keySet().stream().filter(it -> it.startsWith("baggage-"))
                        .collect(toMap(identity(), k -> String.valueOf(map.getFirst(k)))));
            }
            return null;
        }
        if (!TextMap.class.isInstance(carrier)) {
            throw new IllegalArgumentException("Only TextMap are supported");
        }
        final Iterator<Map.Entry<String, String>> textMap = TextMap.class.cast(carrier).iterator();
        String traceId = null;
        String spanId = null;
        final Map<String, String> baggages = new HashMap<>();
        while (textMap.hasNext()) {
            final Map.Entry<String, String> next = textMap.next();
            if (next.getKey().startsWith("baggage-")) {
                baggages.put(next.getKey(), next.getValue());
            } else if ("spanid".equals(next.getKey())) {
                spanId = next.getValue();
            } else if ("traceid".equals(next.getKey())) {
                traceId = next.getValue();
            }
        }
        if (traceId != null && spanId != null) {
            return new SpanContextImpl(traceId, spanId, baggages);
        }
        return null;
    }
}
