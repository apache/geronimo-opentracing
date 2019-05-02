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


import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.arquillian.testng.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.ClassLoaderAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Ignore;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;
import zipkin2.Span;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.net.URL;
import java.util.List;

@Ignore
public class BasicZipkinTest extends Arquillian {

    @Deployment
    public static WebArchive createDeployment() {
        return ShrinkWrap.create(WebArchive.class)
                .addClasses(SimpleService.class)
                .addAsWebInfResource(new ClassLoaderAsset("test-beans.xml"), "beans.xml")
                .addAsServiceProvider(javax.enterprise.inject.spi.Extension.class, UseGeronimoTracerExtension.class);
    }

    private ZipkinRule zipkin;

    @ArquillianResource
    private URL serviceUrl;

    @BeforeMethod
    public void configure() {


    }

    @BeforeSuite
    public void setup() {
        zipkin = new ZipkinRule();
        System.setProperty("geronimo.opentracing.span.converter.zipkin.sender", "http");
        System.setProperty("geronimo.opentracing.span.converter.zipkin.http.collector", zipkin.httpUrl() + "/api/v2/spans");
        System.setProperty("geronimo.opentracing.span.converter.zipkin.http.bulkSendInterval", "6000");
        System.setProperty("geronimo.opentracing.span.converter.zipkin.http.maxSpansPerBulk", "1");
        System.setProperty("geronimo.opentracing.span.converter.zipkin.http.maxSpansIteration","1");
        System.setProperty("geronimo.opentracing.span.converter.zipkin.http.bufferSize","1");
        System.setProperty("geronimo.opentracing.span.converter.zipkin.http.useV2","true");

    }


    /**
     * Test that server endpoint is adding standard tags
     */
    @Test
    @RunAsClient
    public void testSimpleService() throws Exception {
        System.out.println(zipkin.httpUrl());

        Client client = ClientBuilder.newClient();
        String url = serviceUrl.toExternalForm() + "hello";

        WebTarget target = client.target(url);
        Response response = target.request().get();
        if (response.getStatus() != 200) {
            String unexpectedResponse = response.readEntity(String.class);
            Assert.fail("Expected HTTP response code 200 but received " + response.getStatus() + "; Response: " + unexpectedResponse);
        }

        Thread.sleep(10000);

        final List<List<Span>> traces = zipkin.getTraces();
        Assert.assertTrue(traces.size() > 0, "Expected some traces");
    }


}
