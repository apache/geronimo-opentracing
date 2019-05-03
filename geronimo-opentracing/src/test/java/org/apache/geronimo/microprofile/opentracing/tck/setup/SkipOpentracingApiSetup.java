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

import org.jboss.arquillian.container.spi.event.container.BeforeDeploy;
import org.jboss.arquillian.core.api.annotation.Observes;
import org.jboss.arquillian.core.spi.LoadableExtension;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.Filters;
import org.jboss.shrinkwrap.api.Node;

import java.util.Collection;

// the tck put the opentracing-api in the war but not our impl, let's fix it by using the apploader api jar!
public class SkipOpentracingApiSetup implements LoadableExtension {

    @Override
    public void register(final ExtensionBuilder builder) {
        builder.observer(Impl.class);
    }

    public static class Impl {

        public void clean(@Observes final BeforeDeploy beforeDeploy) {
            final Archive<?> archive = beforeDeploy.getDeployment().getArchive();
            final Collection<Node> opentracingApi = archive.getContent(Filters.include("\\/WEB-INF\\/lib\\/opentracing\\-api\\-.*\\.jar")).values();
            if (opentracingApi != null && !opentracingApi.isEmpty()) {
                archive.delete(opentracingApi.iterator().next().getPath());
            }
        }
    }
}
