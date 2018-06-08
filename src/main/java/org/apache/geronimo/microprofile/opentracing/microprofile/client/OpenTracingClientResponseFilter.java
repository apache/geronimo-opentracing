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
package org.apache.geronimo.microprofile.opentracing.microprofile.client;

import static java.util.Optional.ofNullable;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.Priorities;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;

import io.opentracing.Scope;
import io.opentracing.tag.Tags;

@ApplicationScoped
@Priority(Priorities.HEADER_DECORATOR)
public class OpenTracingClientResponseFilter implements ClientResponseFilter {

    @Override
    public void filter(final ClientRequestContext req, final ClientResponseContext resp) {
        ofNullable(req.getProperty(OpenTracingClientRequestFilter.class.getName())).map(Scope.class::cast)
                .ifPresent(scope -> {
                    Tags.HTTP_STATUS.set(scope.span(), resp.getStatus());
                    scope.close();
                });
    }
}
