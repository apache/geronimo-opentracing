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
package org.apache.geronimo.microprofile.opentracing.microprofile.zipkin;

import static java.util.Collections.emptyList;
import static java.util.Locale.ROOT;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.apache.geronimo.microprofile.opentracing.config.GeronimoOpenTracingConfig;
import org.apache.geronimo.microprofile.opentracing.impl.FinishedSpan;
import org.apache.geronimo.microprofile.opentracing.impl.IdGenerator;
import org.apache.geronimo.microprofile.opentracing.impl.SpanImpl;

import io.opentracing.Span;
import io.opentracing.tag.Tags;

// when writing this observer, opentracing has no standard propagation nor exchange format
// so falling back on zipkin
@ApplicationScoped
public class ZipkinConverter {

    @Inject
    private Event<ZipkinSpan> zipkinSpanEvent;

    @Inject
    private GeronimoOpenTracingConfig config;

    @Inject
    private IdGenerator idGenerator;

    private String serviceName;

    @PostConstruct
    private void init() {
        serviceName = config.read("zipkin.serviceName", getHostName() + "_" + getPid());
    }

    public void onSpan(@Observes final FinishedSpan finishedSpan) {
        final Span from = finishedSpan.getSpan();
        if (!SpanImpl.class.isInstance(from)) {
            throw new IllegalStateException("Unsupported span type: " + from + ", maybe check your configuration");
        }
        zipkinSpanEvent.fire(toZipkin(SpanImpl.class.cast(from)));
    }

    private String getPid() {
        return ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
    }

    private String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (final UnknownHostException e) {
            return "server";
        }
    }

    private ZipkinSpan toZipkin(final SpanImpl span) {
        final ZipkinSpan.ZipkinEndpoint endpoint = toEndpoint(span);

        final ZipkinSpan zipkin = new ZipkinSpan();
        if (idGenerator.isCounter()) {
            zipkin.setParentId(asLong(span.getParentId()));
            zipkin.setTraceId(asLong(span.getTraceId()));
            zipkin.setId(asLong(span.getId()));
        } else {
            zipkin.setParentId(span.getParentId());
            zipkin.setTraceId(span.getTraceId());
            zipkin.setId(span.getId());
        }
        zipkin.setName(span.getName());
        zipkin.setKind(ofNullable(span.getKind()).map(s -> s.toUpperCase(ROOT)).orElse(null));
        zipkin.setTimestamp(span.getTimestamp());
        zipkin.setDuration(span.getDuration());
        zipkin.setAnnotations(toAnnotations(span));
        zipkin.setBinaryAnnotations(toBinaryAnnotations(span.getTags()));
        zipkin.setTags(span.getTags().entrySet().stream().filter(e -> !Tags.SPAN_KIND.getKey().equalsIgnoreCase(e.getKey()))
                .collect(toMap(Map.Entry::getKey, e -> String.valueOf(e.getValue()))));

        if (Tags.SPAN_KIND_CLIENT.equals(String.valueOf(span.getTags().get(Tags.SPAN_KIND.getKey())))) {
            zipkin.setRemoteEndpoint(endpoint);
        } else { // server
            zipkin.setLocalEndpoint(endpoint);
        }

        return zipkin;
    }

    private long asLong(final Object value) {
        if (value == null) {
            return 0;
        }
        if (Long.class.isInstance(value)) {
            return Long.class.cast(value);
        }
        return Long.valueOf(String.valueOf(value));
    }

    private ZipkinSpan.ZipkinEndpoint toEndpoint(final SpanImpl span) {
        final Map<String, Object> tags = span.getTags();
        switch (String.valueOf(tags.get(Tags.SPAN_KIND.getKey()))) {
            case Tags.SPAN_KIND_CLIENT: {
                String ipv4 = (String) tags.get(Tags.PEER_HOST_IPV4.getKey());
                String ipv6 = (String) tags.get(Tags.PEER_HOST_IPV6.getKey());
                if (ipv4 == null && ipv6 == null && tags.containsKey(Tags.PEER_HOSTNAME.getKey())) {
                    try {
                        final String hostAddress = InetAddress.getByName(tags.get(Tags.PEER_HOSTNAME.getKey()).toString())
                                .getHostAddress();
                        if (hostAddress.contains("::")) {
                            ipv6 = hostAddress;
                        } else {
                            ipv4 = hostAddress;
                        }
                    } catch (final UnknownHostException e) {
                        // no-op
                    }
                }

                final Integer port = (Integer) tags.get(Tags.PEER_PORT.getKey());

                final ZipkinSpan.ZipkinEndpoint endpoint = new ZipkinSpan.ZipkinEndpoint();
                endpoint.setServiceName(serviceName);
                endpoint.setIpv4(ipv4);
                endpoint.setIpv6(ipv6);
                endpoint.setPort(port == null ? 0 : port);

                return endpoint;
            }
            case Tags.SPAN_KIND_SERVER: {
                final String url = (String) tags.get(Tags.HTTP_URL.getKey());
                String ipv4 = null;
                String ipv6 = null;
                Integer port = null;
                if (url != null) {
                    try {
                        final URL asUrl = new URL(url);
                        port = asUrl.getPort();

                        final String host = asUrl.getHost();
                        final String hostAddress = host.contains(":") ? host : InetAddress.getByName(host).getHostAddress();
                        if (hostAddress.contains("::")) {
                            ipv6 = hostAddress;
                        } else {
                            ipv4 = hostAddress;
                        }
                    } catch (final UnknownHostException | MalformedURLException e) {
                        // no-op
                    }
                }

                final ZipkinSpan.ZipkinEndpoint endpoint = new ZipkinSpan.ZipkinEndpoint();
                endpoint.setServiceName(serviceName);
                endpoint.setIpv4(ipv4);
                endpoint.setIpv6(ipv6);
                endpoint.setPort(port == null ? 0 : port);

                return endpoint;
            }
            default:
                return null;
        }
    }

    private List<ZipkinSpan.ZipkinAnnotation> toAnnotations(final SpanImpl span) {
        final Map<String, Object> tags = span.getTags();
        final List<ZipkinSpan.ZipkinAnnotation> annotations = new ArrayList<>(2);
        switch (String.valueOf(tags.get(Tags.SPAN_KIND.getKey()))) {
            case Tags.SPAN_KIND_CLIENT: {
                {
                    final ZipkinSpan.ZipkinAnnotation clientSend = new ZipkinSpan.ZipkinAnnotation();
                    clientSend.setValue("cs");
                    clientSend.setTimestamp(span.getTimestamp());
                    annotations.add(clientSend);
                }
                {
                    final ZipkinSpan.ZipkinAnnotation clientReceived = new ZipkinSpan.ZipkinAnnotation();
                    clientReceived.setValue("cr");
                    clientReceived.setTimestamp(span.getTimestamp() + span.getDuration());
                    annotations.add(clientReceived);
                }
                return annotations;
            }
            case Tags.SPAN_KIND_SERVER: {
                {
                    final ZipkinSpan.ZipkinAnnotation serverReceived = new ZipkinSpan.ZipkinAnnotation();
                    serverReceived.setValue("sr");
                    serverReceived.setTimestamp(span.getTimestamp());
                    annotations.add(serverReceived);
                }
                {

                    final ZipkinSpan.ZipkinAnnotation serverSend = new ZipkinSpan.ZipkinAnnotation();
                    serverSend.setValue("ss");
                    serverSend.setTimestamp(span.getTimestamp() + span.getDuration());
                    annotations.add(serverSend);
                }
                return annotations;
            }
            default:
                return emptyList();
        }
    }

    private List<ZipkinSpan.ZipkinBinaryAnnotation> toBinaryAnnotations(final Map<String, Object> tags) {
        return tags.entrySet().stream().map(tag -> {
            final ZipkinSpan.ZipkinBinaryAnnotation annotations = new ZipkinSpan.ZipkinBinaryAnnotation();
            annotations.setType(findAnnotationType(tag.getValue()));
            annotations.setKey(tag.getKey());
            annotations.setValue(tag.getValue());
            return annotations;
        }).collect(toList());
    }

    private int findAnnotationType(final Object value) {
        if (String.class.isInstance(value)) {
            return 6;
        }
        if (Double.class.isInstance(value)) {
            return 5;
        }
        if (Long.class.isInstance(value)) {
            return 4;
        }
        if (Integer.class.isInstance(value)) {
            return 3;
        }
        if (Short.class.isInstance(value)) {
            return 2;
        }
        if (Byte.class.isInstance(value)) {
            return 1;
        }
        return 6; // todo?
    }
}
