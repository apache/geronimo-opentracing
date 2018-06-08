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

import static java.util.Collections.singletonMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.tag.Tags;

public class SpanImpl implements Span {

    private final Collection<ReferenceImpl> references;

    private final Map<String, Object> tags;

    private final Consumer<SpanImpl> onFinish;

    private final SpanContextImpl context;

    private final long startTimestamp;

    private String operationName;

    private long finishTimestamp;

    private final Collection<Log> logs = new ArrayList<>();

    public SpanImpl(final String operationName, final long startTimestamp, final Collection<ReferenceImpl> references,
                    final Map<String, Object> tags, final Consumer<SpanImpl> onFinish, final SpanContextImpl context) {
        this.operationName = operationName;
        this.startTimestamp = startTimestamp;
        this.references = references;
        this.tags = tags;
        this.context = context;
        this.onFinish = onFinish;
    }

    @Override
    public Span log(final long timestampMicroseconds, final Map<String, ?> fields) {
        final Log log = new Log(timestampMicroseconds, fields);
        synchronized (logs) {
            logs.add(log);
        }
        return this;
    }

    @Override
    public SpanContext context() {
        return context;
    }

    @Override
    public Span log(final long timestampMicroseconds, final String event) {
        return log(singletonMap("event", event));
    }

    @Override
    public void finish() {
        finish(TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis()));
    }

    @Override
    public void finish(final long finishMicros) {
        if (finishTimestamp != 0) {
            return;
        }
        finishTimestamp = finishMicros;
        onFinish.accept(this);
    }

    @Override
    public Span setTag(final String key, final String value) {
        tags.put(key, value);
        return this;
    }

    @Override
    public Span setTag(final String key, final boolean value) {
        tags.put(key, value);
        return this;
    }

    @Override
    public Span setTag(final String key, final Number value) {
        tags.put(key, value);
        return this;
    }

    @Override
    public Span log(final Map<String, ?> fields) {
        return log(startTimestamp, fields);
    }

    @Override
    public Span log(final String event) {
        return log(startTimestamp, event);
    }

    @Override
    public Span setBaggageItem(final String key, final String value) {
        context.getBaggageItems().put(key, value);
        return this;
    }

    @Override
    public String getBaggageItem(final String key) {
        return context.getBaggageItems().get(key);
    }

    @Override
    public Span setOperationName(final String operationName) {
        this.operationName = operationName;
        return this;
    }

    @Override
    public String toString() {
        return "SpanImpl{" +
                " id=" + context.getSpanId() +
                ", operationName='" + operationName + '\'' +
                ", references=" + references +
                ", tags=" + tags +
                ", startTimestamp=" + startTimestamp +
                ", finishTimestamp=" + finishTimestamp +
                ", logs=" + logs +
                '}';
    }

    public Object getId() {
        return context.getSpanId();
    }

    public Object getTraceId() {
        return context.getTraceId();
    }

    public Object getParentId() {
        return context.getParentSpanId();
    }

    public String getName() {
        return operationName;
    }

    public long getTimestamp() {
        return startTimestamp;
    }

    public long getDuration() {
        return finishTimestamp - startTimestamp;
    }

    public String getKind() {
        return tags.entrySet().stream().filter(it -> Tags.SPAN_KIND.getKey().equals(it.getKey()))
                .findFirst().map(Map.Entry::getValue)
                .map(String::valueOf).orElse(null);
    }

    public Map<String, Object> getTags() {
        return tags;
    }

    public Collection<Log> getLogs() {
        return logs;
    }

    public Collection<ReferenceImpl> getReferences() {
        return references;
    }

    public static class Log {

        private final long timestampMicros;

        private final Map<String, ?> fields;

        public Log(long timestampMicros, Map<String, ?> fields) {
            this.timestampMicros = timestampMicros;
            this.fields = fields;
        }

        public long getTimestampMicros() {
            return timestampMicros;
        }

        public Map<String, ?> getFields() {
            return fields;
        }
    }
}
