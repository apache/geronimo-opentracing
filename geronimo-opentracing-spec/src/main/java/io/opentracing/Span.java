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

import java.util.Map;

public interface Span {

    /**
     * @return the context related to this span.
     */
    SpanContext context();

    Span setTag(String key, String value);

    Span setTag(String key, boolean value);

    Span setTag(String key, Number value);

    Span log(Map<String, ?> fields);

    Span log(long timestampMicroseconds, Map<String, ?> fields);

    Span log(String event);

    Span log(long timestampMicroseconds, String event);

    Span setBaggageItem(String key, String value);

    String getBaggageItem(String key);

    Span setOperationName(String operationName);

    void finish();

    void finish(long finishMicros);
}
