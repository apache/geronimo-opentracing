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

import static java.util.Optional.ofNullable;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;

import io.opentracing.Scope;
import io.opentracing.tag.Tags;

@Priority(Priorities.HEADER_DECORATOR)
public class OpenTracingServerResponseFilter implements ContainerResponseFilter {

    @Override
    public void filter(final ContainerRequestContext req, final ContainerResponseContext resp) {
        ofNullable(req.getProperty(OpenTracingFilter.class.getName())).map(Scope.class::cast)
                .ifPresent(scope -> Tags.HTTP_STATUS.set(scope.span(), resp.getStatus()));
    }
}
