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

import static org.ops4j.pax.exam.CoreOptions.maven;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.url;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.features;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.karafDistributionConfiguration;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.keepRuntimeFolder;
import static org.testng.Assert.assertNotNull;

import java.io.File;
import java.net.MalformedURLException;

import javax.inject.Inject;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

import io.opentracing.Tracer;

@Ignore("likely need more love to work")
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class KarafTest {
    @Inject
    private Tracer tracer;

    @Configuration
    public Option[] config() throws MalformedURLException {
        final String karafVersion = "4.2.1";
        return options(
                karafDistributionConfiguration()
                    .frameworkUrl(maven()
                            .groupId("org.apache.karaf")
                            .artifactId("apache-karaf")
                            .version(karafVersion)
                            .type("tar.gz"))
                    .unpackDirectory(new File("target/karaf"))
                    .useDeployFolder(false)
                    .runEmbedded(true),
                keepRuntimeFolder(),
                features(url(new File("src/test/resources/features.xml").toURI().toURL().toExternalForm()), "test")
        );
    }

    @Test
    public void ensureServicesAreRegistered() {
        assertNotNull(tracer);
    }
}
