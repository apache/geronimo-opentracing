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
package org.apache.geronimo.microprofile.opentracing.microprofile.client;

import static java.util.stream.Collectors.toList;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;

public class OpenTracingExecutorService implements ExecutorService {

    private final ExecutorService delegate;

    private final Tracer tracer;

    public OpenTracingExecutorService(final ExecutorService executorService, final Tracer tracer) {
        this.delegate = executorService;
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
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(final long timeout, final TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }

    @Override
    public <T> Future<T> submit(final Callable<T> task) {
        return delegate.submit(wrap(task));
    }

    @Override
    public <T> Future<T> submit(final Runnable task, final T result) {
        return delegate.submit(wrap(task), result);
    }

    @Override
    public Future<?> submit(final Runnable task) {
        return delegate.submit(wrap(task));
    }

    @Override
    public <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return delegate.invokeAll(tasks.stream().map(this::wrap).collect(toList()));
    }

    @Override
    public <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> tasks, final long timeout, final TimeUnit unit)
            throws InterruptedException {
        return delegate.invokeAll(tasks.stream().map(this::wrap).collect(toList()), timeout, unit);
    }

    @Override
    public <T> T invokeAny(final Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        return delegate.invokeAny(tasks.stream().map(this::wrap).collect(toList()));
    }

    @Override
    public <T> T invokeAny(final Collection<? extends Callable<T>> tasks, final long timeout, final TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return delegate.invokeAny(tasks.stream().map(this::wrap).collect(toList()), timeout, unit);
    }

    @Override
    public void execute(final Runnable command) {
        delegate.execute(wrap(command));
    }

    private Runnable wrap(final Runnable task) {
        return () -> {
            RuntimeException error = null;
            final Span span = before();
            try {
                task.run();
            } catch (final RuntimeException re) {
                error = re;
                throw re;
            } finally {
                after(span, error);
            }
        };
    }

    private <T> Callable<T> wrap(final Callable<T> task) {
        return () -> {
            RuntimeException error = null;
            final Span span = before();
            try {
                return task.call();
            } catch (final RuntimeException re) {
                error = re;
                throw re;
            } finally {
                after(span, error);
            }
        };
    }
}
