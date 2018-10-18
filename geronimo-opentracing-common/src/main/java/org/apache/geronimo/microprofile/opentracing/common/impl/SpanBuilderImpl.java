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

import static java.util.stream.Collectors.toMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

import io.opentracing.References;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;

public class SpanBuilderImpl implements Tracer.SpanBuilder {

    private final GeronimoTracer tracer;

    private final Consumer<SpanImpl> onFinish;

    private final String operationName;

    private final Collection<ReferenceImpl> references = new ArrayList<>();

    private final Map<String, Object> tags = new HashMap<>();

    private final IdGenerator idGenerator;

    private boolean ignoreActiveSpan;

    private long timestamp = -1;

    public SpanBuilderImpl(final GeronimoTracer tracer,
                           final Consumer<SpanImpl> onFinish, final String operationName,
                           final IdGenerator idGenerator) {
        this.tracer = tracer;
        this.onFinish = onFinish;
        this.operationName = operationName;
        this.idGenerator = idGenerator;
    }

    @Override
    public Tracer.SpanBuilder asChildOf(final SpanContext parent) {
        return addReference(References.CHILD_OF, parent);
    }

    @Override
    public Tracer.SpanBuilder asChildOf(final Span parent) {
        if (parent == null) {
            return this;
        }
        return asChildOf(parent.context());
    }

    @Override
    public Tracer.SpanBuilder addReference(final String referenceType, final SpanContext referencedContext) {
        references.add(new ReferenceImpl(referenceType, SpanContextImpl.class.cast(referencedContext)));
        return this;
    }

    @Override
    public Tracer.SpanBuilder ignoreActiveSpan() {
        this.ignoreActiveSpan = true;
        return this;
    }

    @Override
    public Tracer.SpanBuilder withTag(final String key, final String value) {
        tags.put(key, value);
        return this;
    }

    @Override
    public Tracer.SpanBuilder withTag(final String key, final boolean value) {
        tags.put(key, value);
        return this;
    }

    @Override
    public Tracer.SpanBuilder withTag(final String key, final Number value) {
        tags.put(key, value);
        return this;
    }

    @Override
    public Tracer.SpanBuilder withStartTimestamp(final long microseconds) {
        this.timestamp = microseconds;
        return this;
    }

    @Override
    public Scope startActive(final boolean finishSpanOnClose) {
        return tracer.scopeManager().activate(startManual(), finishSpanOnClose);
    }

    @Override
    public Span startManual() {
        if (timestamp < 0) {
            timestamp = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis());
        }
        if (!ignoreActiveSpan && references.stream().noneMatch(it -> it.getType().equalsIgnoreCase(References.CHILD_OF))) {
            final Span span = tracer.activeSpan();
            if (span != null) {
                addReference(References.CHILD_OF, span.context());
            }
        }
        final ReferenceImpl parent = references.stream().filter(it -> References.CHILD_OF.equals(it.getType())).findFirst()
                .orElseGet(() -> references.isEmpty() ? null : references.iterator().next());
        final Map<String, String> baggages = references.stream()
                .flatMap(r -> StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(r.getValue().baggageItems().iterator(), Spliterator.IMMUTABLE),
                        false))
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
        final SpanContextImpl context = parent == null ?
                tracer.newContext(idGenerator.next(), null, idGenerator.next(), baggages) :
                tracer.newContext(parent.getValue().getTraceId(), parent.getValue().getSpanId(), idGenerator.next(), baggages);
        return new SpanImpl(operationName, timestamp, references, tags, onFinish, context);
    }

    @Override
    public Span start() {
        return startManual();
    }
}
