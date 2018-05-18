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

import java.util.Map;

import io.opentracing.SpanContext;

public class SpanContextImpl implements SpanContext {

    private final Object traceId;

    private final Object spanId;

    private final Map<String, String> baggageItems;

    public SpanContextImpl(final Object traceId, final Object spanId, final Map<String, String> baggageItems) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.baggageItems = baggageItems;
    }

    public Map<String, String> getBaggageItems() {
        return baggageItems;
    }

    public Object getTraceId() {
        return traceId;
    }

    public Object getSpanId() {
        return spanId;
    }

    @Override
    public Iterable<Map.Entry<String, String>> baggageItems() {
        return baggageItems.entrySet();
    }

    @Deprecated // TCK
    public Object traceId() {
        return getTraceId();
    }

    @Deprecated // TCK
    public Object spanId() {
        return getSpanId();
    }
}
