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
package org.apache.geronimo.microprofile.opentracing.common.impl;

import io.opentracing.Scope;
import io.opentracing.Span;

public class ScopeImpl implements Scope {

    private final Span span;

    private final boolean finishOnClose;

    private final Runnable onClose;

    public ScopeImpl(final Runnable onClose, final Span span) {
        this.onClose = onClose;
        this.span = span;
        this.finishOnClose = true;
    }

    @Override
    public void close() {
        try {
            if (finishOnClose) {
                span.finish();
            }
        } finally {
            if (onClose != null) {
                onClose.run();
            }
        }
    }

    public Span span() {
        return span;
    }
}
