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
package io.opentracing;

import io.opentracing.propagation.Format;

public interface Tracer {

    /**
     * @return current {@link ScopeManager}.
     */
    ScopeManager scopeManager();

    /**
     * @return current {@link Span}.
     */
    Span activeSpan();

    /**
     * @return a new span builder.
     */
    SpanBuilder buildSpan(String operationName);

    /**
     * Inject a span context into a carrier.
     */
    <C> void inject(SpanContext spanContext, Format<C> format, C carrier);

    /**
     * @return the span context from the carrier.
     */
    <C> SpanContext extract(Format<C> format, C carrier);

    interface SpanBuilder {

        /**
         * @return this span builder after having added CHILD_OFF reference.
         */
        SpanBuilder asChildOf(SpanContext parent);

        /**
         * @return this span builder after having added CHILD_OFF reference from the parent.
         */
        SpanBuilder asChildOf(Span parent);

        /**
         * @return this span builder after having added a reference.
         */
        SpanBuilder addReference(String referenceType, SpanContext referencedContext);

        /**
         * @return this span builder flag to ignore implicit CHILD_OFF reference.
         */
        SpanBuilder ignoreActiveSpan();

        SpanBuilder withTag(String key, String value);

        SpanBuilder withTag(String key, boolean value);

        SpanBuilder withTag(String key, Number value);

        SpanBuilder withStartTimestamp(long microseconds);

        /**
         * @return a new scope registered in the scope manager.
         */
        Scope startActive(boolean finishSpanOnClose);

        /**
         * @deprecated replaced by {@link #start} and {@link #startActive}.
         */
        @Deprecated
        Span startManual();

        /**
         * @return a new scope not registered in the manager.
         */
        Span start();
    }
}
