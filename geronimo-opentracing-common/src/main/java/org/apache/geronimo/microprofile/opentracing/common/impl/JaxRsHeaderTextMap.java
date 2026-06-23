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

import java.util.Iterator;
import java.util.Map;

import jakarta.ws.rs.core.MultivaluedMap;

import io.opentracing.propagation.TextMap;

public class JaxRsHeaderTextMap<T> implements TextMap {

    private final MultivaluedMap<String, T> headers;

    public JaxRsHeaderTextMap(final MultivaluedMap<String, T> headers) {
        this.headers = headers;
    }

    public MultivaluedMap<String, ?> getMap() {
        return headers;
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
        final Iterator<String> iterator = headers.keySet().iterator();
        return new Iterator<>() {

            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public Map.Entry<String, String> next() {
                final String next = iterator.next();
                return new Map.Entry<>() {

                    @Override
                    public String getKey() {
                        return next;
                    }

                    @Override
                    public String getValue() {
                        return String.valueOf(headers.getFirst(next));
                    }

                    @Override
                    public String setValue(final String value) {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        };
    }

    @Override
    public void put(final String key, final String value) {
        this.headers.putSingle(key, (T) value);
    }
}
