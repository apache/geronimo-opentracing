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
package org.apache.geronimo.microprofile.opentracing.microprofile.thread;

import java.util.concurrent.Callable;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;

public class ScopePropagatingCallable<B> implements Callable<B> {

    private final Callable<B> delegate;

    private final Tracer tracer;

    public ScopePropagatingCallable(final Callable<B> delegate, final Tracer tracer) {
        this.delegate = delegate;
        this.tracer = tracer;
    }

    private Span before() {
        return tracer.activeSpan();
    }

    private void after(final Span span, final RuntimeException error) {
        if (span != null && error != null) {
            Tags.ERROR.set(span, true);
            if (error.getMessage() != null) {
                span.setTag("errorMessage", error.getMessage());
                span.setTag("errorType", error.getClass().getName());
            }
        }
    }

    @Override
    public B call() throws Exception {
        RuntimeException error = null;
        final Span span = before();
        try {
            return delegate.call();
        } catch (final RuntimeException re) {
            error = re;
            throw re;
        } finally {
            after(span, error);
        }
    }
}
