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
package org.apache.geronimo.microprofile.opentracing.common.microprofile.server;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.container.DynamicFeature;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.FeatureContext;

import org.apache.geronimo.microprofile.opentracing.common.config.GeronimoOpenTracingConfig;
import org.apache.geronimo.microprofile.opentracing.common.spi.Container;
import org.eclipse.microprofile.opentracing.Traced;

import io.opentracing.Tracer;

// @Provider - don't let it be scanned by EE containers like tomee, it is in CDI module anyway!
// @Dependent
public class GeronimoOpenTracingFeature implements DynamicFeature {
    private Tracer tracer;
    private GeronimoOpenTracingConfig config;
    private Container container;
    private Collection<Pattern> skipPatterns;
    private String globalFilterRequestSkip;
    private boolean globalIgnoreMetadataResources;

    public void setTracer(final Tracer tracer) {
        this.tracer = tracer;
    }

    public void setConfig(final GeronimoOpenTracingConfig config) {
        this.config = config;
        this.globalFilterRequestSkip = config.read("server.filter.request.skip", "false");
        this.globalIgnoreMetadataResources = Boolean.parseBoolean(config.read("server.filter.request.ignoreMetadaResources", "true"));
    }

    public void setContainer(final Container container) {
        this.container = container;
    }

    @Override
    public void configure(final ResourceInfo resourceInfo, final FeatureContext context) {
        if ((tracer == null || config == null) && container == null) {
            container = Container.get();
        }
        if (tracer == null) { // configured instead of scanned
            tracer = container.lookup(Tracer.class);
        }
        if (config == null) {
            config = container.lookup(GeronimoOpenTracingConfig.class);
        }
        if (skipPatterns == null) {
            skipPatterns = ofNullable(config.read("mp.opentracing.server.skip-pattern", null))
                .map(it -> Stream.of(it.split("\\|")).map(String::trim).filter(p -> !p.isEmpty()).map(Pattern::compile).collect(toList()))
                .orElseGet(Collections::emptyList);
        }

        final Optional<Traced> traced = ofNullable(ofNullable(resourceInfo.getResourceMethod().getAnnotation(Traced.class))
                .orElseGet(() -> resourceInfo.getResourceClass().getAnnotation(Traced.class)));
        if (!traced.map(Traced::value).orElse(true)) {
            return;
        }
        final String path = Stream.of(
                ofNullable(resourceInfo.getResourceClass().getAnnotation(Path.class)).map(Path::value).orElse(""),
                ofNullable(resourceInfo.getResourceMethod().getAnnotation(Path.class)).map(Path::value).orElse(""))
                .map(it -> it.equals("/") ? "" : it)
                .map(it -> it.substring(it.startsWith("/") ? 1 : 0, it.endsWith("/") ? it.length() - 1 : it.length()))
                .filter(it -> !it.isEmpty())
                .collect(joining("/", "/", ""));
        if (skipPatterns.stream().anyMatch(it -> it.matcher(path).matches())) {
            return;
        }

        final String operationName = traced.map(Traced::operationName).filter(v -> !v.trim().isEmpty()).orElseGet(() -> {
            if (Boolean.parseBoolean(config.read("server.filter.request.operationName.usePath", "false"))) {
                return getHttpMethod(resourceInfo) + ':' + getMethodPath(resourceInfo);
            } else if ("http-path".equals(config.read("mp.opentracing.server.operation-name-provider", null))) {
                return getMethodPath(resourceInfo);
            }
            return buildDefaultName(resourceInfo);
        });
        context.register(new OpenTracingServerResponseFilter())
                .register(new OpenTracingServerRequestFilter(operationName, tracer,
                        shouldSkip(resourceInfo),
                        Boolean.parseBoolean(config.read("server.filter.request.skipDefaultTags", "false"))));
    }

    private boolean shouldSkip(final ResourceInfo resourceInfo) {
        if (Boolean.parseBoolean(config.read(
                "server.filter.request.skip." + resourceInfo.getResourceClass().getName() + "_" + resourceInfo.getResourceMethod().getName(),
                config.read("server.filter.request.skip." + resourceInfo.getResourceClass().getName(),
                        globalFilterRequestSkip)))) {
            return true;
        }
        return globalIgnoreMetadataResources && isMetadataResource(resourceInfo.getResourceClass().getName());
    }

    private boolean isMetadataResource(final String name) {
        return name.startsWith("org.apache.geronimo.microprofile.openapi.jaxrs.") ||
                name.startsWith("org.apache.geronimo.microprofile.metrics.jaxrs.") ||
                name.startsWith("org.apache.geronimo.microprofile.impl.health.jaxrs.") ||
                name.startsWith("org.apache.geronimo.microprofile.reporter.storage.front.") ||
                name.startsWith("org.microprofileext.openapi.swaggerui.");
    }

    private String getMethodPath(final ResourceInfo resourceInfo) {
        final String classPath = ofNullable(resourceInfo.getResourceClass().getAnnotation(Path.class))
                .map(Path::value).orElse("");
        final String methodPath = ofNullable(resourceInfo.getResourceMethod().getAnnotation(Path.class))
                .map(Path::value).orElse("");
        return Stream.of(classPath, methodPath)
                     .map(it -> it.substring(it.startsWith("/") ? 1 : 0, it.length() - (it.endsWith("/") ? 1 : 0)))
                     .filter(it -> !it.isEmpty())
                     .collect(joining("/", "/", ""));
    }

    private String buildDefaultName(final ResourceInfo resourceInfo) {
        return getHttpMethod(resourceInfo) + ':' + resourceInfo.getResourceClass().getName() + "."
                + resourceInfo.getResourceMethod().getName();
    }

    private String getHttpMethod(final ResourceInfo resourceInfo) {
        return Stream.of(resourceInfo.getResourceMethod().getAnnotations())
                .filter(a -> a.annotationType().isAnnotationPresent(HttpMethod.class)).findFirst()
                .map(a -> a.annotationType().getAnnotation(HttpMethod.class).value()).orElse("");
    }
}
