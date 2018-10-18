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

import java.lang.reflect.Type;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.WithAnnotations;
import javax.enterprise.inject.spi.configurator.AnnotatedTypeConfigurator;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.Path;

import org.apache.geronimo.microprofile.opentracing.common.config.GeronimoOpenTracingConfig;
import org.apache.geronimo.microprofile.opentracing.common.microprofile.thread.OpenTracingExecutorService;
import org.apache.geronimo.microprofile.opentracing.microprofile.zipkin.CdiZipkinConverter;
import org.apache.geronimo.microprofile.opentracing.microprofile.zipkin.CdiZipkinLogger;
import org.eclipse.microprofile.opentracing.Traced;

public class OpenTracingExtension implements Extension {

    private GeronimoOpenTracingConfig config;

    private boolean useZipkin;
    private boolean useZipkinLogger;

    void onStart(@Observes final BeforeBeanDiscovery beforeBeanDiscovery) {
        config = GeronimoOpenTracingConfig.create();
        useZipkin = Boolean.parseBoolean(config.read("span.converter.zipkin.active", "true"));
        useZipkinLogger = useZipkin && Boolean.parseBoolean(config.read("span.converter.zipkin.logger.active", "true"));
    }

    void zipkinConverterToggle(@Observes final ProcessAnnotatedType<CdiZipkinConverter> onZipkinConverter) {
        if (!useZipkin) {
            onZipkinConverter.veto();
        }
    }

    void zipkinLoggerToggle(@Observes final ProcessAnnotatedType<CdiZipkinLogger> onZipkinLogger) {
        if (!useZipkinLogger) {
            onZipkinLogger.veto();
        }
    }

    <T> void removeTracedFromJaxRsEndpoints(@Observes @WithAnnotations(Traced.class) final ProcessAnnotatedType<T> pat) {
        if (isJaxRs(pat.getAnnotatedType())) { // we have filters with more accurate timing
            final AnnotatedTypeConfigurator<T> configurator = pat.configureAnnotatedType();
            configurator.remove(it -> it.annotationType() == Traced.class);
            configurator.methods().stream().filter(m -> isJaxRs(m.getAnnotated()))
                    .forEach(m -> m.remove(it -> it.annotationType() == Traced.class));
        }
    }

    <T> void instrumentExecutorServices(@Observes final ProcessAnnotatedType<T> pat) {
        final Set<Type> typeClosure = pat.getAnnotatedType().getTypeClosure();
        if (typeClosure.contains(ExecutorService.class) && !typeClosure.contains(OpenTracingExecutorService.class)) {
            pat.configureAnnotatedType().add(TracedExecutorService.Literal.INSTANCE);
        }
    }

    void addConfigAsBean(@Observes final AfterBeanDiscovery afterBeanDiscovery) {
        afterBeanDiscovery.addBean().id(OpenTracingExtension.class.getName() + "#" + GeronimoOpenTracingConfig.class.getName())
                .beanClass(GeronimoOpenTracingConfig.class).types(GeronimoOpenTracingConfig.class, Object.class)
                .qualifiers(Default.Literal.INSTANCE, Any.Literal.INSTANCE).scope(ApplicationScoped.class)
                .createWith(ctx -> config);
    }

    private <T> boolean isJaxRs(final AnnotatedType<T> annotatedType) {
        return annotatedType.getAnnotations().stream().anyMatch(it -> it.annotationType() == Path.class)
                || annotatedType.getMethods().stream().anyMatch(this::isJaxRs);
    }

    private <T> boolean isJaxRs(final AnnotatedMethod<? super T> m) {
        return m.getAnnotations().stream().anyMatch(it -> it.annotationType().isAnnotationPresent(HttpMethod.class));
    }
}
