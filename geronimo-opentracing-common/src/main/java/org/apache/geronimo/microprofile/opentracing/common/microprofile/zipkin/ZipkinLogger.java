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

import java.util.logging.Logger;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

import org.apache.geronimo.microprofile.opentracing.common.config.GeronimoOpenTracingConfig;
import org.apache.geronimo.microprofile.opentracing.common.spi.Listener;

// this allows to integrate with any backend using appenders.
// @ApplicationScoped
public class ZipkinLogger implements Listener<ZipkinSpan> {

    private final Logger spanLogger = Logger.getLogger("org.apache.geronimo.opentracing.zipkin");

    private GeronimoOpenTracingConfig config;

    private Jsonb jsonb;

    private boolean wrapAsList;

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
        wrapAsList = Boolean.parseBoolean(config.read("span.converter.zipkin.logger.wrapAsList", "true"));
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
    }

    @Override
    public void onEvent(final ZipkinSpan zipkinSpan) {
        final String json = jsonb.toJson(zipkinSpan);
        spanLogger.info(wrapAsList ? '[' + json + ']' : json);
    }
}
