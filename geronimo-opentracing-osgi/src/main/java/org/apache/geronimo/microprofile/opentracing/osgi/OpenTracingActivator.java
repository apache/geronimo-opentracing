/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.geronimo.microprofile.opentracing.osgi;

import static java.util.Collections.singletonMap;
import static java.util.Optional.ofNullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.function.BiConsumer;

import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.ClientResponseFilter;
import javax.ws.rs.container.DynamicFeature;

import org.apache.geronimo.microprofile.opentracing.common.config.GeronimoOpenTracingConfig;
import org.apache.geronimo.microprofile.opentracing.common.impl.FinishedSpan;
import org.apache.geronimo.microprofile.opentracing.common.impl.GeronimoTracer;
import org.apache.geronimo.microprofile.opentracing.common.impl.IdGenerator;
import org.apache.geronimo.microprofile.opentracing.common.impl.ScopeManagerImpl;
import org.apache.geronimo.microprofile.opentracing.common.microprofile.client.OpenTracingClientRequestFilter;
import org.apache.geronimo.microprofile.opentracing.common.microprofile.client.OpenTracingClientResponseFilter;
import org.apache.geronimo.microprofile.opentracing.common.microprofile.server.GeronimoOpenTracingFeature;
import org.apache.geronimo.microprofile.opentracing.common.microprofile.zipkin.ZipkinConverter;
import org.apache.geronimo.microprofile.opentracing.common.microprofile.zipkin.ZipkinLogger;
import org.apache.geronimo.microprofile.opentracing.common.microprofile.zipkin.ZipkinSpan;
import org.apache.geronimo.microprofile.opentracing.common.spi.Container;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

import io.opentracing.ScopeManager;
import io.opentracing.Tracer;

public class OpenTracingActivator implements BundleActivator {
    // not sure we can avoid that cause of client side :(
    static final Map<Class<?>, Tracked<?>> INSTANCES = new HashMap<>();

    private final Collection<ServiceRegistration<?>> registrations = new ArrayList<>();
    private ZipkinLogger logger;

    @Override
    public void start(final BundleContext context) {
        INSTANCES.put(Container.class, new Tracked<>(context, Container.class, this::register));
        INSTANCES.put(Tracer.class, new Tracked<>(context, Tracer.class, this::register));
        INSTANCES.put(GeronimoOpenTracingConfig.class, new Tracked<>(context, GeronimoOpenTracingConfig.class, this::register));
        INSTANCES.put(ScopeManager.class, new Tracked<>(context, ScopeManager.class, this::register));
        INSTANCES.put(OpenTracingClientRequestFilter.class, new Tracked<>(context, OpenTracingClientRequestFilter.class, this::register));
        INSTANCES.put(OpenTracingClientResponseFilter.class, new Tracked<>(context, OpenTracingClientResponseFilter.class, this::register));
        INSTANCES.put(EventAdmin.class, new Tracked<>(context, EventAdmin.class, this::register));
        INSTANCES.put(ConfigurationAdmin.class, new Tracked<>(context, ConfigurationAdmin.class, this::register));

        final OSGiContainer container = new OSGiContainer();

        final GeronimoOpenTracingConfig config = new ConfigAdminOpenTracingConfig();
        final ScopeManager scopeManager = new ScopeManagerImpl();

        final IdGenerator idGenerator = new IdGenerator();
        idGenerator.setConfig(config);
        idGenerator.init();

        final GeronimoTracer tracer = new GeronimoTracer();
        tracer.setConfig(config);
        tracer.setIdGenerator(idGenerator);
        tracer.setScopeManager(scopeManager);
        tracer.setFinishedSpanEvent(span -> ofNullable(container.lookup(EventAdmin.class)).ifPresent(ea ->
                ea.sendEvent(new Event("geronimo/microprofile/opentracing/finishedSpan", singletonMap("span", span)))));
        tracer.init();

        final ZipkinConverter zipkinConverter = new ZipkinConverter();
        zipkinConverter.setConfig(config);
        zipkinConverter.setIdGenerator(idGenerator);
        zipkinConverter.setZipkinSpanEvent(span -> ofNullable(container.lookup(EventAdmin.class)).ifPresent(ea ->
                ea.sendEvent(new Event("geronimo/microprofile/opentracing/zipkinSpan", singletonMap("span", span)))));
        zipkinConverter.init();

        logger = new ZipkinLogger();
        logger.setConfig(config);
        logger.init();

        final OpenTracingClientRequestFilter requestFilter = new OpenTracingClientRequestFilter();
        requestFilter.setTracer(tracer);
        requestFilter.setConfig(config);
        requestFilter.init();

        final OpenTracingClientResponseFilter responseFilter = new OpenTracingClientResponseFilter();

        final GeronimoOpenTracingFeature tracingFeature = new GeronimoOpenTracingFeature();
        tracingFeature.setConfig(config);
        tracingFeature.setContainer(container);
        tracingFeature.setTracer(tracer);

        registrations.add(context.registerService(GeronimoOpenTracingConfig.class, config, new Hashtable<>()));
        registrations.add(context.registerService(Container.class, container, new Hashtable<>()));
        registrations.add(context.registerService(IdGenerator.class, idGenerator, new Hashtable<>()));
        registrations.add(context.registerService(ScopeManager.class, scopeManager, new Hashtable<>()));
        registrations.add(context.registerService(Tracer.class, tracer, new Hashtable<>()));
        registrations.add(context.registerService(ClientRequestFilter.class, requestFilter, newJaxRsExtensionProps()));
        registrations.add(context.registerService(ClientResponseFilter.class, responseFilter, newJaxRsExtensionProps()));
        registrations.add(context.registerService(DynamicFeature.class, tracingFeature, newJaxRsExtensionProps()));
        registrations.add(context.registerService(EventHandler.class,
                event -> zipkinConverter.onEvent(FinishedSpan.class.cast(event.getProperty("span"))),
                newEventHandlerProps("geronimo/microprofile/opentracing/finishedSpan")));
        registrations.add(context.registerService(EventHandler.class,
                event -> logger.onEvent(ZipkinSpan.class.cast(event.getProperty("span"))),
                newEventHandlerProps("geronimo/microprofile/opentracing/zipkinSpan")));
    }

    @Override
    public void stop(final BundleContext context) {
        INSTANCES.values().forEach(ServiceTracker::close);
        INSTANCES.clear();
        registrations.forEach(ServiceRegistration::unregister);
        logger.destroy();
    }

    private Dictionary<String, Object> newEventHandlerProps(final String topic) {
        final Dictionary<String, Object> props = new Hashtable<>();
        props.put(EventConstants.EVENT_TOPIC, topic);
        return props;
    }

    private Dictionary<String, Object> newJaxRsExtensionProps() {
        final Dictionary<String, Object> props = new Hashtable<>();
        props.put("osgi.jaxrs.extension", "true");
        return props;
    }

    private void register(final Class<?> tClass, final Object t) {
        final Tracked tracked = INSTANCES.get(tClass);
        tracked.instance = t;
    }

    public static class Tracked<T> extends ServiceTracker<T, T> implements ServiceTrackerCustomizer<T, T> {
        private volatile T instance;

        private Tracked(final BundleContext context, final Class<T> clazz, final BiConsumer<Class<T>, T> onInstance) {
            super(context, clazz, new ServiceTrackerCustomizer<T, T>() {
                @Override
                public T addingService(final ServiceReference<T> reference) {
                    final T service = context.getService(reference);
                    onInstance.accept(clazz, service);
                    return service;
                }

                @Override
                public void modifiedService(final ServiceReference<T> reference, final T service) {
                    addingService(reference);
                }

                @Override
                public void removedService(final ServiceReference<T> reference, final T service) {
                    addingService(reference);
                }
            });
        }

        T getInstance() {
            return instance;
        }
    }
}
