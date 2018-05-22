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
package org.apache.geronimo.microprofile.opentracing.microprofile.server;

import static java.util.Optional.ofNullable;

import java.util.Optional;
import java.util.stream.Stream;

import javax.enterprise.context.Dependent;
import javax.enterprise.inject.spi.CDI;
import javax.inject.Inject;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.Path;
import javax.ws.rs.container.DynamicFeature;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.FeatureContext;
import javax.ws.rs.ext.Provider;

import org.apache.geronimo.microprofile.opentracing.config.GeronimoOpenTracingConfig;
import org.eclipse.microprofile.opentracing.Traced;

import io.opentracing.Tracer;

@Provider
@Dependent
public class GeronimoOpenTracingFeature implements DynamicFeature {

    @Inject
    private Tracer tracer;

    @Inject
    private GeronimoOpenTracingConfig config;

    @Override
    public void configure(final ResourceInfo resourceInfo, final FeatureContext context) {
        if (tracer == null) { // configured instead of scanned
            CDI<Object> cdi = CDI.current();
            tracer = cdi.select(Tracer.class).get();
            config = cdi.select(GeronimoOpenTracingConfig.class).get();
        }

        final Optional<Traced> traced = ofNullable(ofNullable(resourceInfo.getResourceMethod().getAnnotation(Traced.class))
                .orElseGet(() -> resourceInfo.getResourceClass().getAnnotation(Traced.class)));
        if (!traced.map(Traced::value).orElse(true)) {
            return;
        }

        final String operationName = traced.map(Traced::operationName).filter(v -> !v.trim().isEmpty()).orElseGet(() -> {
            final boolean usePath = Boolean.parseBoolean(config.read("server.filter.request.operationName.usePath", "false"));
            if (usePath) {
                final String classPath = ofNullable(resourceInfo.getResourceClass().getAnnotation(Path.class)).map(Path::value)
                        .orElse("");
                final String methodPath = ofNullable(resourceInfo.getResourceMethod().getAnnotation(Path.class)).map(Path::value)
                        .orElse("");
                return getHttpMethod(resourceInfo) + ':' + classPath
                        + (!classPath.isEmpty() && !methodPath.isEmpty() && !classPath.endsWith("/") ? "/" : "") + methodPath;
            }
            return buildDefaultName(resourceInfo);
        });
        context.register(new OpenTracingServerResponseFilter())
                .register(new OpenTracingServerRequestFilter(operationName, tracer,
                        Boolean.parseBoolean(config.read(
                                "server.filter.request.skip." + resourceInfo.getResourceClass().getName() + "_"
                                        + resourceInfo.getResourceMethod().getName(),
                                config.read("server.filter.request.skip", "false"))),
                        Boolean.parseBoolean(config.read("server.filter.request.skipDefaultTags", "false"))));
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
