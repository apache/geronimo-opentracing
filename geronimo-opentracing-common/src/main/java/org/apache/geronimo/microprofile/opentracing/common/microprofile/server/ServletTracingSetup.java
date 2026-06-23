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

import java.util.EnumSet;
import java.util.Set;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterRegistration;
import jakarta.servlet.ServletContainerInitializer;
import jakarta.servlet.ServletContext;

import org.apache.geronimo.microprofile.opentracing.common.config.GeronimoOpenTracingConfig;

public class ServletTracingSetup implements ServletContainerInitializer {
    @Override
    public void onStartup(final Set<Class<?>> c, final ServletContext ctx) {
        final GeronimoOpenTracingConfig config  = GeronimoOpenTracingConfig.create();
        if (!"true".equalsIgnoreCase(config.read("filter.active", "true"))) {
            return;
        }
        final FilterRegistration.Dynamic opentracing = ctx.addFilter("opentracing", OpenTracingFilter.class);
        opentracing.setAsyncSupported(true);
        opentracing.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), false, "/*");
    }
}
