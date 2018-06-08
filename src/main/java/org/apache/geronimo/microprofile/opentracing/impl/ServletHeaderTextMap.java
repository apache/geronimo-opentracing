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

import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.opentracing.propagation.TextMap;

public class ServletHeaderTextMap implements TextMap {

    private final HttpServletRequest request;

    private final HttpServletResponse response;

    public ServletHeaderTextMap(final HttpServletRequest request, final HttpServletResponse response) {
        this.request = request;
        this.response = response;
    }

    public HttpServletRequest getRequest() {
        return request;
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
        final Enumeration<String> iterator = request.getHeaderNames();
        return new Iterator<Map.Entry<String, String>>() {

            @Override
            public boolean hasNext() {
                return iterator.hasMoreElements();
            }

            @Override
            public Map.Entry<String, String> next() {
                final String next = iterator.nextElement();
                return new Map.Entry<String, String>() {

                    @Override
                    public String getKey() {
                        return next;
                    }

                    @Override
                    public String getValue() {
                        return String.valueOf(request.getHeader(next));
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
        this.response.setHeader(key, value);
    }
}
