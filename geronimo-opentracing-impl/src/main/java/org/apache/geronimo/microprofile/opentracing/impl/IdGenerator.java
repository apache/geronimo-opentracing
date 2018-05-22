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
package org.apache.geronimo.microprofile.opentracing.impl;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.geronimo.microprofile.opentracing.config.GeronimoOpenTracingConfig;

@ApplicationScoped
public class IdGenerator {
    @Inject
    private GeronimoOpenTracingConfig config;

    private Supplier<Object> delegate;
    private boolean counter;

    @PostConstruct
    private void createDelegate() {
        final String type = config.read("id.generator", "counter");
        counter = "counter".equalsIgnoreCase(type);
        switch (type) {
            case "counter":
                delegate = new Supplier<Object>() {
                    private final AtomicLong counter = new AtomicLong();

                    @Override
                    public Object get() {
                        return counter.incrementAndGet();
                    }
                };
                break;
            case "uuid":
                delegate = () -> UUID.randomUUID().toString();
                break;
            case "hex": // limited to 16 for the length cause of zipkin (see span decoder)
            default:
                delegate = new Supplier<Object>() {
                    private final Random random = new Random(System.nanoTime());
                    private final String constantPart = config.read("id.generator.hex.prefix", "");

                    @Override
                    public Object get() {
                        final StringBuilder sb = new StringBuilder(16).append(constantPart);
                        for (int i = 0; i < 16 - constantPart.length(); i++) {
                            sb.append(Integer.toHexString(random.nextInt()));
                        }
                        return sb.toString();
                    }
                };
        }
    }

    public boolean isCounter() {
        return counter;
    }

    public Object next() {
        return delegate.get();
    }
}
