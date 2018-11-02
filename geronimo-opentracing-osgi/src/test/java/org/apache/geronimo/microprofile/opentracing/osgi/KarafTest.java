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
package org.apache.geronimo.microprofile.opentracing.osgi;

import static java.util.Objects.requireNonNull;
import static org.apache.ziplock.JarLocation.jarLocation;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.ops4j.pax.exam.CoreOptions.bundle;
import static org.ops4j.pax.exam.CoreOptions.maven;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.systemPackage;
import static org.ops4j.pax.exam.CoreOptions.url;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.features;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.karafDistributionConfiguration;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.keepRuntimeFolder;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Dictionary;
import java.util.Hashtable;

import javax.inject.Inject;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.ops4j.pax.exam.util.Filter;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;

import io.opentracing.Tracer;

@Ignore("event admin setup is not yet right so the test can't pass")
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class KarafTest {
    @Inject
    private BundleContext bc;

    @Inject
    @Filter("(objectClass=org.osgi.service.event.EventAdmin)")
    private EventAdmin eventAdmin;

    @Inject
    @Filter("(objectClass=io.opentracing.Tracer)")
    private Tracer tracer;

    @Configuration
    public Option[] config() throws MalformedURLException {
        final File testClasses = jarLocation(KarafTest.class);
        final String projectVersion = System.getProperty("project.version", "1.0.1-SNAPSHOT");
        final URL testFeature = requireNonNull(new File(testClasses, "features.xml").toURI().toURL());
        return options(
                karafDistributionConfiguration()
                    .frameworkUrl(maven()
                            .groupId("org.apache.karaf")
                            .artifactId("apache-karaf")
                            .version(System.getProperty("karaf.version", "4.2.1"))
                            .type("tar.gz"))
                    .unpackDirectory(new File("target/karaf"))
                    .useDeployFolder(false)
                    .runEmbedded(true),
                keepRuntimeFolder(),
                features(url(testFeature.toExternalForm()), "test"),
                bundle(new File(testClasses, "../../../geronimo-opentracing-common/target/geronimo-opentracing-common-" + projectVersion + ".jar").toURI().toURL().toExternalForm()),
                bundle(new File(testClasses, "../geronimo-opentracing-osgi-" + projectVersion + ".jar").toURI().toURL().toExternalForm())
        );
    }

    @Test
    public void checkBusGetSpans() {
        assertNotNull(tracer);

        final Dictionary<String, Object> props = new Hashtable<>();
        props.put(EventConstants.EVENT_TOPIC, "geronimo/microprofile/opentracing/zipkinSpan");
        final Collection<Event> events = new ArrayList<>();
        final ServiceRegistration<EventHandler> registration = bc.registerService(EventHandler.class, event -> {
            synchronized (events) {
                events.add(event);
            }
        }, props);
        tracer.buildSpan("test").start().finish();
        registration.unregister();

        assertEquals(1, events.size());
    }
}
