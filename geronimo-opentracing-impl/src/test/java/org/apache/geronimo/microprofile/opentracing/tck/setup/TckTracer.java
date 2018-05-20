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
package org.apache.geronimo.microprofile.opentracing.tck.setup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Specializes;

import org.apache.geronimo.microprofile.opentracing.impl.FinishedSpan;
import org.apache.geronimo.microprofile.opentracing.impl.GeronimoTracer;

import io.opentracing.Span;

// compat for TCK, to drop once tcks are fixed - used by reflection!!!!
@Specializes
@ApplicationScoped
public class TckTracer extends GeronimoTracer {
    private final Collection<Span> spans = new LinkedHashSet<>();

    synchronized void onSpan(@Observes final FinishedSpan span) {
        spans.add(span.getSpan());
    }

    public synchronized Iterable<Span> finishedSpans() {
        return new ArrayList<>(spans);
    }

    public synchronized void reset() {
        spans.clear();
    }
}
