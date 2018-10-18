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
import io.opentracing.ScopeManager;
import io.opentracing.Span;

// @ApplicationScoped
public class ScopeManagerImpl implements ScopeManager {

    private final ThreadLocal<Scope> current = new ThreadLocal<>();

    @Override
    public Scope activate(final Span span, final boolean finishSpanOnClose) {
        final Thread thread = Thread.currentThread();
        final Scope oldScope = current.get();
        final ScopeImpl newScope = new ScopeImpl(() -> {
            if (Thread.currentThread() == thread) {
                current.set(oldScope);
            } // else error?
        }, span, finishSpanOnClose);
        current.set(newScope);
        return newScope;
    }

    @Override
    public Scope active() {
        final Scope scope = current.get();
        if (scope == null) {
            current.remove();
        }
        return scope;
    }

    public void clear() {
        current.remove();
    }
}
