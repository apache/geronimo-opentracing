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
package org.apache.geronimo.microprofile.opentracing.common.microprofile.zipkin;

import java.util.List;
import java.util.Map;

public class ZipkinSpan {
    private Object traceId;
    private Object parentId;
    private Object id;
    private String name;
    private String kind;
    private Long timestamp;
    private Long duration;
    private ZipkinEndpoint localEndpoint;
    private ZipkinEndpoint remoteEndpoint;
    private List<ZipkinAnnotation> annotations;
    private List<ZipkinBinaryAnnotation> binaryAnnotations;
    private Map<String, String> tags;
    private Boolean debug;
    private Boolean shared;

    public Object getTraceId() {
        return traceId;
    }

    public void setTraceId(final Object traceId) {
        this.traceId = traceId;
    }

    public Object getParentId() {
        return parentId;
    }

    public void setParentId(final Object parentId) {
        this.parentId = parentId;
    }

    public Object getId() {
        return id;
    }

    public void setId(final Object id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(final Long timestamp) {
        this.timestamp = timestamp;
    }

    public Long getDuration() {
        return duration;
    }

    public void setDuration(final Long duration) {
        this.duration = duration;
    }

    public List<ZipkinAnnotation> getAnnotations() {
        return annotations;
    }

    public void setAnnotations(final List<ZipkinAnnotation> annotations) {
        this.annotations = annotations;
    }

    public List<ZipkinBinaryAnnotation> getBinaryAnnotations() {
        return binaryAnnotations;
    }

    public void setBinaryAnnotations(final List<ZipkinBinaryAnnotation> binaryAnnotations) {
        this.binaryAnnotations = binaryAnnotations;
    }

    public Boolean getDebug() {
        return debug;
    }

    public void setDebug(final Boolean debug) {
        this.debug = debug;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(final String kind) {
        this.kind = kind;
    }

    public ZipkinEndpoint getLocalEndpoint() {
        return localEndpoint;
    }

    public void setLocalEndpoint(final ZipkinEndpoint localEndpoint) {
        this.localEndpoint = localEndpoint;
    }

    public ZipkinEndpoint getRemoteEndpoint() {
        return remoteEndpoint;
    }

    public void setRemoteEndpoint(final ZipkinEndpoint remoteEndpoint) {
        this.remoteEndpoint = remoteEndpoint;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public void setTags(final Map<String, String> tags) {
        this.tags = tags;
    }

    public Boolean getShared() {
        return shared;
    }

    public void setShared(final Boolean shared) {
        this.shared = shared;
    }

    public static class ZipkinAnnotation {
        private long timestamp;
        private String value;

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(final long timestamp) {
            this.timestamp = timestamp;
        }

        public String getValue() {
            return value;
        }

        public void setValue(final String value) {
            this.value = value;
        }
    }

    public static class ZipkinBinaryAnnotation {
        private String key;
        private int type;
        private Object value;

        public String getKey() {
            return key;
        }

        public void setKey(final String key) {
            this.key = key;
        }

        public int getType() {
            return type;
        }

        public void setType(final int type) {
            this.type = type;
        }

        public Object getValue() {
            return value;
        }

        public void setValue(final Object value) {
            this.value = value;
        }
    }

    public static class ZipkinEndpoint {
        private String serviceName;
        private String ipv4;
        private String ipv6;
        private int port;

        public String getServiceName() {
            return serviceName;
        }

        public void setServiceName(final String serviceName) {
            this.serviceName = serviceName;
        }

        public String getIpv4() {
            return ipv4;
        }

        public void setIpv4(final String ipv4) {
            this.ipv4 = ipv4;
        }

        public String getIpv6() {
            return ipv6;
        }

        public void setIpv6(final String ipv6) {
            this.ipv6 = ipv6;
        }

        public int getPort() {
            return port;
        }

        public void setPort(final int port) {
            this.port = port;
        }
    }
}
