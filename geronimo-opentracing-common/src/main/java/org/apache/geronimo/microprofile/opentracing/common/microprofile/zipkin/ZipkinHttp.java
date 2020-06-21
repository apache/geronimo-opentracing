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
package org.apache.geronimo.microprofile.opentracing.common.microprofile.zipkin;

import static java.util.Collections.singletonList;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static javax.ws.rs.client.Entity.entity;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Logger;
import java.util.stream.Stream;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;

import org.apache.geronimo.microprofile.opentracing.common.config.GeronimoOpenTracingConfig;
import org.apache.geronimo.microprofile.opentracing.common.spi.Listener;
import org.eclipse.microprofile.opentracing.ClientTracingRegistrar;

// experimental
public class ZipkinHttp implements Listener<ZipkinSpan> {

    private GeronimoOpenTracingConfig config;

    private Jsonb jsonb;

    private BlockingQueue<ZipkinSpan> spans;
    private Client client;
    private String collector;
    private ScheduledExecutorService executor;
    private ScheduledFuture<?> scheduledTask;
    private int maxSpansPerBulk;
    private int maxSpansIteration;

    public void setConfig(final GeronimoOpenTracingConfig config) {
        this.config = config;
    }

    public void setJsonb(final Jsonb jsonb) {
        this.jsonb = jsonb;
    }

    public void init() {
        if (jsonb == null) {
            jsonb = JsonbBuilder.create();
        }
        final int capacity = Integer.parseInt(
                config.read("span.converter.zipkin.http.bufferSize", "1000000"));
        maxSpansPerBulk = Integer.parseInt(
                config.read("span.converter.zipkin.http.maxSpansPerBulk", "250"));
        maxSpansIteration = Integer.parseInt(
                config.read("span.converter.zipkin.http.maxSpansIteration", "-1"));
        collector = config.read("span.converter.zipkin.http.collector", null);
        if (collector == null) {
            return;
        }

        final long delay = Long.parseLong(
                config.read("span.converter.zipkin.http.bulkSendInterval", "60000"));
        if (delay < 0) {
            logger().severe("No span.converter.zipkin.http.bulkSendInterval configured, skipping");
            collector = null; // to skip anything
            return;
        }
        if (delay > 0) {
            executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    final Thread thread = new Thread(r, getClass().getName() + "-executor");
                    thread.setPriority(Thread.NORM_PRIORITY);
                    thread.setDaemon(false);
                    return thread;
                }
            });
            scheduledTask = executor.scheduleAtFixedRate(this::onEmit, delay, delay, MILLISECONDS);
            spans = new ArrayBlockingQueue<>(capacity);
        } else { // == 0 => immediate send
            spans = null;
        }
        final ClientBuilder clientBuilder = ClientBuilder.newBuilder()
                .connectTimeout(Long.parseLong(config.read("span.converter.zipkin.http.connectTimeout", "30000")), MILLISECONDS)
                .readTimeout(Long.parseLong(config.read("span.converter.zipkin.http.readTimeout", "30000")), MILLISECONDS);
        ofNullable(config.read("span.converter.zipkin.http.providers", null))
                .ifPresent(providers -> Stream.of(providers.split(","))
                    .map(String::trim)
                    .map(it -> {
                        try {
                            return Thread.currentThread().getContextClassLoader().loadClass(it)
                                    .getConstructor().newInstance();
                        } catch (final Exception e) {
                            throw new IllegalArgumentException(e);
                        }
                    })
                    .forEach(clientBuilder::register));
        if (Boolean.parseBoolean(config.read("span.converter.zipkin.http.selfTrace", "false"))) {
            ClientTracingRegistrar.configure(clientBuilder);
        }
        client = clientBuilder.build();
        logger().info("Zipkin http sender configured");
    }

    private Logger logger() {
        return Logger.getLogger("org.apache.geronimo.opentracing.zipkin.http");
    }

    public Jsonb getJsonb() {
        return jsonb;
    }

    public void destroy() {
        try {
            jsonb.close();
        } catch (final Exception e) {
            // no-op
        }
        scheduledTask.cancel(true);
        executor.shutdownNow();
        try {
            executor.awaitTermination(1, MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void onEvent(final ZipkinSpan zipkinSpan) {
        if (collector != null) {
            if (spans == null) {
                doSend(singletonList(zipkinSpan));
            } else {
                spans.add(zipkinSpan);
            }
        }
    }

    private void onEmit() {
        final int size = this.spans.size();
        final List<ZipkinSpan> copy = new ArrayList<>(size <= 0 ? maxSpansPerBulk : Math.min(size, maxSpansPerBulk));
        int toSend = maxSpansIteration <= 0 ? size : Math.min(size, maxSpansIteration);
        while (toSend > 0) {
            this.spans.drainTo(copy, Math.min(toSend, maxSpansPerBulk));
            if (copy.isEmpty()) {
                break;
            }
            doSend(copy);
            toSend -= copy.size();
            copy.clear();

        }
    }

    private void doSend(final List<ZipkinSpan> copy) {
        final Response result = client.target(collector)
                .request()
                .post(entity(copy, APPLICATION_JSON_TYPE));
        if (result.getStatus() >= 300) {
            // todo: better handling but at least log them to not loose them completely or explode in memory
            throw new IllegalStateException("Can't send to zipkin: " + copy);
        }
    }
}
