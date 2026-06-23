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
package org.apache.geronimo.microprofile.opentracing.microprofile.cdi;

import static java.util.Collections.singletonMap;
import static java.util.Objects.requireNonNull;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Intercepted;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

import org.eclipse.microprofile.opentracing.Traced;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;

@Traced
@Interceptor
@Priority(Interceptor.Priority.LIBRARY_BEFORE)
public class TracedInterceptor implements Serializable {
    @Inject
    private Tracer tracer;

    @Inject
    @Intercepted
    private Bean<?> bean;

    @Inject
    private BeanManager beanManager;

    private transient ConcurrentMap<Method, Meta> metas = new ConcurrentHashMap<>();

    @AroundInvoke
    public Object trace(final InvocationContext context) throws Exception {
        final Method method = context.getMethod();
        Meta meta = metas.get(method);
        if (meta == null) {
            final AnnotatedType<?> annotatedType = beanManager.createAnnotatedType(bean.getBeanClass());
            final Traced traced = requireNonNull(annotatedType.getMethods().stream()
                    .filter(m -> m.getJavaMember().equals(method))
                    .findFirst().map(m -> m.getAnnotation(Traced.class))
                    .orElseGet(() -> annotatedType.getAnnotation(Traced.class)), "no @Traced found on " + method);
            meta = new Meta(
                    traced.value(),
                    Optional.of(traced.operationName())
                            .filter(v -> !v.isEmpty())
                            .orElseGet(() -> method.getDeclaringClass().getName() + "." + method.getName()));
            metas.putIfAbsent(method, meta); // no big deal to not use the same meta instance
        }
        if (!meta.traced) {
            return context.proceed();
        }

        final Tracer.SpanBuilder spanBuilder = tracer.buildSpan(meta.operationName);
        final Span parent = tracer.scopeManager().activeSpan();
        if (parent != null) {
            spanBuilder.asChildOf(parent);
        }
        Span span = null;
        try {
            span = spanBuilder.start();
            return context.proceed();
        } catch (final RuntimeException re) {
            if (span != null) {
                Tags.ERROR.set(span, true);
                final Map<String, Object> logs = new LinkedHashMap<>();
                logs.put("event", Tags.ERROR.getKey());
                logs.put("error.object", re);
                span.log(logs);
            }
            throw re;
        } finally {
            if (span != null) {
                span.finish();
            }
        }
    }

    private static class Meta {
        private final boolean traced;
        private final String operationName;

        private Meta(final boolean traced, final String name) {
            this.traced = traced;
            this.operationName = name;
        }
    }
}
