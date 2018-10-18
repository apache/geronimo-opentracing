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

import javax.enterprise.inject.spi.CDI;

import org.apache.geronimo.microprofile.opentracing.common.spi.Container;

public class CdiContainer implements Container {
    private final CDI<Object> cdi;

    public CdiContainer() {
        cdi = CDI.current();
    }

    @Override
    public <T> T lookup(final Class<T> type) {
        return cdi.select(type).get();
    }
}
