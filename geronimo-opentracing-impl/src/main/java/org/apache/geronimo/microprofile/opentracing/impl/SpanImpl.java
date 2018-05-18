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

public class SpanImpl implements Span {

    private final Collection<ReferenceImpl> references;

    private final Map<String, Object> tags;

    private final Consumer<Span> onFinish;

    private final SpanContextImpl context;

    private final long startTimestamp;

    private final Object parentId;

    private String operationName;

    private long finishTimestamp;

    private final Collection<Log> logs = new ArrayList<>();

    public SpanImpl(final String operationName, final long startTimestamp, final Collection<ReferenceImpl> references,
            final Map<String, Object> tags, final Consumer<Span> onFinish, final SpanContextImpl context, final Object parentId) {
        this.operationName = operationName;
        this.startTimestamp = startTimestamp;
        this.references = references;
        this.tags = tags;
        this.context = context;
        this.parentId = parentId;
        this.onFinish = onFinish;
    }

    @Override
    public Span log(final long timestampMicroseconds, final Map<String, ?> fields) {
        synchronized (logs) {
            logs.add(new Log(timestampMicroseconds, fields));
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

    @Deprecated // TCK compat
    public long startMicros() {
        return startTimestamp;
    }

    @Deprecated // TCK compat
    public long finishMicros() {
        return finishTimestamp;
    }

    @Deprecated // TCK compat
    public String operationName() {
        return operationName;
    }

    @Deprecated // TCK compat
    public Object parentId() {
        return parentId;
    }

    @Deprecated // TCK compat
    public Map<String, Object> tags() {
        return tags;
    }

    @Deprecated // TCK compat
    public Collection<Log> logEntries() {
        return logs;
    }

    public static class Log {

        private final long timestampMicros;

        private final Map<String, ?> fields;

        private Log(long timestampMicros, Map<String, ?> fields) {
            this.timestampMicros = timestampMicros;
            this.fields = fields;
        }

        public long getTimestampMicros() {
            return timestampMicros;
        }

        public Map<String, ?> getFields() {
            return fields;
        }

        @Deprecated // TCK compat
        public Map<String, ?> fields() {
            return getFields();
        }
    }
}
