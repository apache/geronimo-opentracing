/*
 * Copyright 2015-2018 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.geronimo.microprofile.opentracing.tck.setup;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import okio.GzipSource;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import zipkin2.Callback;
import zipkin2.DependencyLink;
import zipkin2.Span;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.collector.Collector;
import zipkin2.collector.CollectorMetrics;
import zipkin2.collector.InMemoryCollectorMetrics;
import zipkin2.internal.Nullable;
import zipkin2.internal.Platform;
import zipkin2.junit.HttpFailure;
import zipkin2.storage.InMemoryStorage;
import zipkin2.storage.StorageComponent;

import static okhttp3.mockwebserver.SocketPolicy.KEEP_OPEN;

/**
 * Starts up a local Zipkin server, listening for http requests on {@link #httpUrl}.
 *
 * <p>This can be used to test instrumentation. For example, you can POST spans directly to this
 * server.
 *
 * <p>See http://openzipkin.github.io/zipkin-api/#/
 */
public final class ZipkinRule implements TestRule {
    private final InMemoryStorage storage = InMemoryStorage.newBuilder().build();
    private final InMemoryCollectorMetrics metrics = new InMemoryCollectorMetrics();
    private final MockWebServer server = new MockWebServer();
    private final BlockingQueue<MockResponse> failureQueue = new LinkedBlockingQueue<>();
    private final AtomicInteger receivedSpanBytes = new AtomicInteger();

    public ZipkinRule() {
        Dispatcher dispatcher =
                new Dispatcher() {
                    final ZipkinDispatcher successDispatch = new ZipkinDispatcher(storage, metrics, server);

                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        MockResponse maybeFailure = failureQueue.poll();
                        if (maybeFailure != null) return maybeFailure;
                        MockResponse result = successDispatch.dispatch(request);
                        if (request.getMethod().equals("POST")) {
                            receivedSpanBytes.addAndGet((int) request.getBodySize());
                        }
                        return result;
                    }

                    @Override
                    public MockResponse peek() {
                        MockResponse maybeFailure = failureQueue.peek();
                        if (maybeFailure != null) return maybeFailure.clone();
                        return new MockResponse().setSocketPolicy(KEEP_OPEN);
                    }
                };
        server.setDispatcher(dispatcher);
    }

    /** Use this to connect. The zipkin v1 interface will be under "/api/v1" */
    public String httpUrl() {
        return String.format("http://%s:%s", server.getHostName(), server.getPort());
    }

    /** Use this to see how many requests you've sent to any zipkin http endpoint. */
    public int httpRequestCount() {
        return server.getRequestCount();
    }

    /** Use this to see how many spans or serialized bytes were collected on the http endpoint. */
    public InMemoryCollectorMetrics collectorMetrics() {
        return metrics;
    }

    /** Retrieves all traces this zipkin server has received. */
    public List<List<Span>> getTraces() {
        return storage.spanStore().getTraces();
    }

    /** Retrieves a trace by ID which zipkin server has received, or null if not present. */
    @Nullable
    public List<Span> getTrace(String traceId) {
        try {
            return storage.spanStore().getTrace(traceId).execute();
        } catch (IOException e) {
            throw Platform.get().assertionError("I/O exception in in-memory storage", e);
        }
    }

    /** Retrieves all service links between traces this zipkin server has received. */
    public List<DependencyLink> getDependencies() {
        return storage.spanStore().getDependencies();
    }

    /**
     * Used to manually start the server.
     *
     * @param httpPort choose 0 to select an available port
     */
    public void start(int httpPort) throws IOException {
        server.start(httpPort);
    }

    /** Used to manually stop the server. */
    public void shutdown() throws IOException {
        server.shutdown();
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return server.apply(base, description);
    }

    final class ZipkinDispatcher extends Dispatcher {
        private final Collector consumer;
        private final CollectorMetrics metrics;
        private final MockWebServer server;

        ZipkinDispatcher(StorageComponent storage, CollectorMetrics metrics, MockWebServer server) {
            this.consumer = Collector.newBuilder(getClass()).storage(storage).metrics(metrics).build();
            this.metrics = metrics;
            this.server = server;
        }

        @Override
        public MockResponse dispatch(RecordedRequest request) {
            HttpUrl url = server.url(request.getPath());
            if (request.getMethod().equals("POST")) {
                String type = request.getHeader("Content-Type");
                if (url.encodedPath().equals("/api/v1/spans")) {
                    SpanBytesDecoder decoder =
                            type != null && type.contains("/x-thrift")
                                    ? SpanBytesDecoder.THRIFT
                                    : SpanBytesDecoder.JSON_V1;

                    return acceptSpans(request, decoder);
                } else if (url.encodedPath().equals("/api/v2/spans")) {
                    SpanBytesDecoder decoder =
                            type != null && type.contains("/x-protobuf")
                                    ? SpanBytesDecoder.PROTO3
                                    : SpanBytesDecoder.JSON_V2;

                    return acceptSpans(request, decoder);
                }
            } else { // unsupported method
                return new MockResponse().setResponseCode(405);
            }
            return new MockResponse().setResponseCode(404);
        }

        MockResponse acceptSpans(RecordedRequest request, SpanBytesDecoder decoder) {
            metrics.incrementMessages();
            byte[] body = request.getBody().readByteArray();

            // check for the presence of binaryAnnotations, which should not be present in V2.
            // @see ZipkinHttpCollector.testForUnexpectedFormat
            if (SpanBytesDecoder.JSON_V2.equals(decoder) &&
                    new String(body).contains("\"binaryAnnotations\"")) {
                final MockResponse mockResponse = new MockResponse();
                mockResponse.setResponseCode(400);
                mockResponse.setBody("Expected a JSON_V2 encoded list, but received: JSON_V1\n");
                return mockResponse;
            }

            String encoding = request.getHeader("Content-Encoding");
            if (encoding != null && encoding.contains("gzip")) {
                try {
                    Buffer result = new Buffer();
                    GzipSource source = new GzipSource(new Buffer().write(body));
                    while (source.read(result, Integer.MAX_VALUE) != -1) ;
                    body = result.readByteArray();
                } catch (IOException e) {
                    metrics.incrementMessagesDropped();
                    return new MockResponse().setResponseCode(400).setBody("Cannot gunzip spans");
                }
            }

            final MockResponse result = new MockResponse();
            consumer.acceptSpans(
                    body,
                    decoder,
                    new Callback<Void>() {
                        @Override
                        public void onSuccess(Void value) {
                            result.setResponseCode(202);
                        }

                        @Override
                        public void onError(Throwable t) {
                            String message = t.getMessage();
                            result.setBody(message).setResponseCode(message.startsWith("Cannot store") ? 500 : 400);
                        }
                    });
            return result;
        }
    }

}
