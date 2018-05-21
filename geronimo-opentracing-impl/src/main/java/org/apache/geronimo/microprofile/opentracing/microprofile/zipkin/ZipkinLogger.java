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

import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;

import org.apache.geronimo.microprofile.opentracing.config.GeronimoOpenTracingConfig;

// this allows to integrate with any backend using appenders.
@ApplicationScoped
public class ZipkinLogger {

    private final Logger logger = Logger.getLogger(ZipkinLogger.class.getName());

    @Inject
    private GeronimoOpenTracingConfig config;

    private Jsonb jsonb;

    private boolean wrapAsList;

    @PostConstruct
    private void init() {
        jsonb = JsonbBuilder.create();
        wrapAsList = Boolean.parseBoolean(config.read("span.converter.zipkin.logger.wrapAsList", "true"));
    }

    @PreDestroy
    private void destroy() {
        try {
            jsonb.close();
        } catch (final Exception e) {
            // no-op
        }
    }

    public void onZipkinSpan(@Observes final ZipkinSpan zipkinSpan) {
        final String json = jsonb.toJson(zipkinSpan);
        logger.info(wrapAsList ? '[' + json + ']' : json);
    }
}
