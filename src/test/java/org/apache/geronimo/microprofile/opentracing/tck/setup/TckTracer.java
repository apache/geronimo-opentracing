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
package org.apache.geronimo.microprofile.opentracing.tck.setup;

import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Specializes;

import org.apache.geronimo.microprofile.opentracing.impl.FinishedSpan;
import org.apache.geronimo.microprofile.opentracing.impl.GeronimoTracer;
import org.apache.geronimo.microprofile.opentracing.impl.SpanContextImpl;
import org.apache.geronimo.microprofile.opentracing.impl.SpanImpl;

import io.opentracing.Span;
import io.opentracing.SpanContext;

// compat for TCK which assume the MockTracer impl for validations
@Specializes
@ApplicationScoped
public class TckTracer extends GeronimoTracer {
    private final Collection<Span> spans = new LinkedHashSet<>();

    synchronized void onSpan(@Observes final FinishedSpan span) {
        spans.add(span.getSpan());
    }

    public synchronized Iterable<Span> finishedSpans() {
        return new ArrayList<>(spans);
    }

    public synchronized void reset() {
        spans.clear();
    }

    @Override
    protected SpanContextImpl newContext(final Object traceId, final Object parentSpanId,
                                         final Object spanId, final Map<String, String> baggages) {
        return new TckSpanContext(traceId, parentSpanId, spanId, baggages);
    }

    @Override
    protected Span processNewSpan(final SpanImpl span) {
        return new TckSpan(span);
    }

    public static class TckSpan extends SpanImpl {
        private final SpanImpl delegate;

        public TckSpan(final SpanImpl delegate) {
            super(delegate.getName(), delegate.getTimestamp(), delegate.getReferences(), delegate.getTags(), ignored -> {}, SpanContextImpl.class.cast(delegate.context()));
            this.delegate = delegate;
        }

        @Override
        public Span log(final long timestampMicroseconds, final Map<String, ?> fields) {
            return delegate.log(timestampMicroseconds, fields);
        }

        @Override
        public SpanContext context() {
            return delegate.context();
        }

        @Override
        public Span log(final long timestampMicroseconds, final String event) {
            return delegate.log(timestampMicroseconds, event);
        }

        @Override
        public void finish() {
            delegate.finish();
        }

        @Override
        public void finish(final long finishMicros) {
            delegate.finish(finishMicros);
        }

        @Override
        public Span setTag(final String key, final String value) {
            return delegate.setTag(key, value);
        }

        @Override
        public Span setTag(final String key, final boolean value) {
            return delegate.setTag(key, value);
        }

        @Override
        public Span setTag(final String key, final Number value) {
            return delegate.setTag(key, value);
        }

        @Override
        public Span log(final Map<String, ?> fields) {
            return delegate.log(fields);
        }

        @Override
        public Span log(final String event) {
            return delegate.log(event);
        }

        @Override
        public Span setBaggageItem(final String key, final String value) {
            return delegate.setBaggageItem(key, value);
        }

        @Override
        public String getBaggageItem(final String key) {
            return delegate.getBaggageItem(key);
        }

        @Override
        public Span setOperationName(final String operationName) {
            return delegate.setOperationName(operationName);
        }

        public long startMicros() {
            return delegate.getTimestamp();
        }

        public long finishMicros() {
            return delegate.getTimestamp() + delegate.getDuration();
        }

        public String operationName() {
            return delegate.getName();
        }

        public Object parentId() {
            return delegate.getParentId() == null ? 0L : Long.parseLong(delegate.getParentId().toString());
        }

        public Map<String, Object> tags() {
            return delegate.getTags();
        }

        public Collection<SpanImpl.Log> logEntries() {
            return delegate.getLogs().stream()
                    .map(l -> new TckLog(l.getTimestampMicros(), l.getFields()))
                    .collect(toList());
        }
    }

    public static class TckLog extends SpanImpl.Log {
        public TckLog(final long timestampMicros, final Map<String, ?> fields) {
            super(timestampMicros, fields);
        }

        public Map<String, ?> fields() {
            return getFields();
        }
    }

    public static class TckSpanContext extends SpanContextImpl {
        private TckSpanContext(final Object traceId, final Object parentSpanId, final Object spanId, final Map<String, String> baggages) {
            super(traceId, parentSpanId, spanId, baggages);
        }

        public Object traceId() {
            final Object traceId = getTraceId();
            return traceId == null ? 0L : Long.parseLong(traceId.toString());
        }

        public Object parentSpanId() {
            final Object spanId = getParentSpanId();
            return spanId == null ? 0L : Long.parseLong(spanId.toString());
        }

        public Object spanId() {
            final Object spanId = getSpanId();
            return spanId == null ? 0L : Long.parseLong(spanId.toString());
        }
    }
}
