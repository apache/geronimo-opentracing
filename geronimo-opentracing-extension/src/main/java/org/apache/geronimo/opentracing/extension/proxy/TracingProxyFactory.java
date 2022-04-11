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
package org.apache.geronimo.opentracing.extension.proxy;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

import static java.util.Collections.emptyMap;
import static java.util.Optional.ofNullable;

public class TracingProxyFactory {
    public <T> T decorate(final Tracer tracer, final T instance) {
        return decorate(tracer, instance, emptyMap());
    }

    public <T> T decorate(final Tracer tracer, final T instance, final Map<String, String> tags) {
        final Class<?>[] interfaces = instance.getClass().getInterfaces();
        if (interfaces.length == 0) {
            throw new IllegalArgumentException("Can't determine the API to proxy: " + instance);
        }
        final Class<T> mainApi = (Class<T>) interfaces[0];
        final Class<?>[] otherApis = interfaces.length == 1 ?
                new Class<?>[0] : Stream.of(interfaces).skip(1).toArray(Class[]::new);
        return decorate(tracer, instance, mainApi, tags, otherApis);
    }

    public <T> T decorate(final Tracer tracer,
                          final T instance,
                          final Class<T> mainApi,
                          final Map<String, String> tags,
                          final Class<?>... otherApis) {
        return mainApi.cast(Proxy.newProxyInstance(
                ofNullable(Thread.currentThread().getContextClassLoader()).orElseGet(ClassLoader::getSystemClassLoader),
                Stream.concat(Stream.of(mainApi), Stream.of(otherApis)).toArray(Class[]::new),
                new TracingHandler(instance, tracer, tags)));
    }

    private static class TracingHandler implements InvocationHandler, Serializable {
        private final Object delegate;
        private final Tracer tracer;
        private final Map<String, String> tags;

        private TracingHandler(final Object delegate, final Tracer tracer, final Map<String, String> tags) {
            this.delegate = delegate;
            this.tracer = tracer;
            this.tags = tags;
        }

        @Override
        public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
            final Tracer.SpanBuilder builder = tracer.buildSpan(method.getDeclaringClass().getName() + "." + method.getName());
            builder.withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER);
            builder.withTag(Tags.COMPONENT.getKey(), "proxy");
            tags.forEach(builder::withTag);
            ofNullable(tracer.activeSpan()).ifPresent(builder::asChildOf);

            final Span span = builder.start();
            boolean doFinish = true;
            try {
                final Object result = method.invoke(delegate, args);
                if (CompletionStage.class.isInstance(result)) {
                    doFinish = false;
                    final CompletionStage<?> stage = CompletionStage.class.cast(result);
                    return stage.handle((r, e) -> {
                        try {
                            if (e != null) {
                                onError(span, e);
                                return rethrow(e);
                            }
                            return r;
                        } finally {
                            span.finish();
                        }
                    });
                }
                return result;
            } catch (final InvocationTargetException ite) {
                onError(span, ite.getTargetException());
                throw ite.getTargetException();
            } finally {
                if (doFinish) {
                    span.finish();
                }
            }
        }

        private Object rethrow(final Throwable e) {
            if (RuntimeException.class.isInstance(e)) {
                throw RuntimeException.class.cast(e);
            }
            if (Error.class.isInstance(e)) {
                throw Error.class.cast(e);
            }
            throw new IllegalStateException(e);
        }

        private void onError(final Span span, final Throwable e) {
            Tags.ERROR.set(span, true);

            final Map<String, Object> logs = new LinkedHashMap<>();
            logs.put("event", Tags.ERROR.getKey());
            logs.put("error.object", e);
            span.log(logs);
        }
    }
}
