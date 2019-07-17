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

import static java.util.stream.Collectors.joining;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.fail;

import java.util.ArrayList;
import java.util.Collection;

import org.apache.geronimo.microprofile.opentracing.common.config.GeronimoOpenTracingConfig;
import org.apache.geronimo.microprofile.opentracing.common.impl.FinishedSpan;
import org.apache.geronimo.microprofile.opentracing.common.impl.GeronimoTracer;
import org.apache.geronimo.microprofile.opentracing.common.impl.IdGenerator;
import org.apache.geronimo.microprofile.opentracing.common.impl.ScopeManagerImpl;
import org.apache.geronimo.microprofile.opentracing.common.impl.SpanImpl;
import org.testng.annotations.Test;

public class TracingProxyFactoryTest {
    @Test
    public void proxy() {
        final Collection<FinishedSpan> spans = new ArrayList<>();
        final GeronimoOpenTracingConfig config = (value, def) -> def;
        IdGenerator generator = new IdGenerator();
        generator.setConfig(config);
        generator.init();
        final GeronimoTracer tracer = new GeronimoTracer();
        tracer.setConfig(config);
        tracer.setIdGenerator(generator);
        tracer.setScopeManager(new ScopeManagerImpl());
        tracer.setFinishedSpanEvent(spans::add);
        tracer.init();

        final Api wrapped = new TracingProxyFactory()
                .decorate(tracer, new Api() {
                    @Override
                    public String ok() {
                        return "yeah";
                    }

                    @Override
                    public void error() {
                        throw new IllegalStateException("expected error");
                    }

                    @Override
                    public String foo(final String bar) {
                        return "other/" + bar;
                    }
                });
        {
            assertEquals("yeah", wrapped.ok());
            assertSpan(spans, "org.apache.geronimo.opentracing.extension.proxy.TracingProxyFactoryTest$Api.ok");
            spans.clear();
        }
        {
            assertEquals("other/something", wrapped.foo("something"));
            assertSpan(spans, "org.apache.geronimo.opentracing.extension.proxy.TracingProxyFactoryTest$Api.foo");
            spans.clear();
        }
        {
            try {
                wrapped.error();
                fail();
            } catch (final IllegalStateException ise) {
                // no-op
            }
            assertSpan(spans, "org.apache.geronimo.opentracing.extension.proxy.TracingProxyFactoryTest$Api.error");
            final SpanImpl span = toSpanImpl(spans);
            assertEquals(Boolean.TRUE, span.getTags().get("error"));
            assertEquals(
                    "error.object=java.lang.IllegalStateException: expected error\nevent=error",
                    span.getLogs().stream()
                            .map(SpanImpl.Log::getFields)
                            .flatMap(m -> m.entrySet().stream())
                            .map(it -> it.getKey() + "=" + it.getValue())
                            .sorted()
                            .collect(joining("\n")));
            spans.clear();
        }
    }

    private void assertSpan(final Collection<FinishedSpan> spans, final String operation) {
        assertEquals(1, spans.size());
        final SpanImpl span = toSpanImpl(spans);
        assertEquals(operation, span.getName());
    }

    private SpanImpl toSpanImpl(final Collection<FinishedSpan> spans) {
        return SpanImpl.class.cast(spans.iterator().next().getSpan());
    }

    public interface Api {
        String ok();
        void error();
        String foo(String bar);
    }
}
