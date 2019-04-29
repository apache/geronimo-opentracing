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


import io.opentracing.tag.Tags;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.eclipse.microprofile.opentracing.tck.OpenTracingBaseTests;
import org.eclipse.microprofile.opentracing.tck.OpenTracingSkipPatternTests;
import org.eclipse.microprofile.opentracing.tck.application.WildcardClassService;
import org.eclipse.microprofile.opentracing.tck.tracer.TestSpan;
import org.eclipse.microprofile.opentracing.tck.tracer.TestSpanTree;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.testng.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Rule;
import org.testng.annotations.Test;
import zipkin2.junit.ZipkinRule;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.Response;
import java.util.Collections;

public class BasicZipkinTest extends Arquillian {

    @Rule
    private ZipkinRule zipkin = new ZipkinRule();

    @Deployment
    public static WebArchive createDeployment() {
        return ShrinkWrap.create(WebArchive.class)
                .addClasses(SimpleService.class);
    }

    /**
     * Test that server endpoint is adding standard tags
     */
    @Test
    @RunAsClient
    private void testWildcard() throws InterruptedException {


        /*

        Response response = executeRemoteWebServiceRaw("wildcard/10/foo",
                "getFoo/ten", Response.Status.OK);
        response.close();

        TestSpanTree spans = executeRemoteWebServiceTracerTree();

        TestSpanTree expectedTree = new TestSpanTree(
                new TestSpanTree.TreeNode<>(
                        new TestSpan(
                                getOperationName(
                                        Tags.SPAN_KIND_SERVER,
                                        HttpMethod.GET,
                                        WildcardClassService.class,
                                        getEndpointMethod(WildcardClassService.class, WildcardClassService.REST_FOO_PATH)
                                ),
                                getExpectedSpanTags(
                                        Tags.SPAN_KIND_SERVER,
                                        HttpMethod.GET,
                                        "wildcard/10/foo",
                                        "getFoo/ten",
                                        null,
                                        Response.Status.OK.getStatusCode(),
                                        JAXRS_COMPONENT
                                ),
                                Collections.emptyList()
                        )
                )
        );
        assertEqualTrees(spans, expectedTree);

        */
    }


}
