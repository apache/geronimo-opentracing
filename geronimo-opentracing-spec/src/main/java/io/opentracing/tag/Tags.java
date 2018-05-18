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
package io.opentracing.tag;

public final class Tags {

    public static final String SPAN_KIND_SERVER = "server";
    public static final String SPAN_KIND_CLIENT = "client";
    public static final String SPAN_KIND_PRODUCER = "producer";
    public static final String SPAN_KIND_CONSUMER = "consumer";
    public static final StringTag HTTP_URL = new StringTag("http.url");
    public static final IntTag HTTP_STATUS = new IntTag("http.status_code");
    public static final StringTag HTTP_METHOD = new StringTag("http.method");
    public static final IntOrStringTag PEER_HOST_IPV4 = new IntOrStringTag("peer.ipv4");
    public static final StringTag PEER_HOST_IPV6 = new StringTag("peer.ipv6");
    public static final StringTag PEER_SERVICE = new StringTag("peer.service");
    public static final StringTag PEER_HOSTNAME = new StringTag("peer.hostname");
    public static final IntTag PEER_PORT = new IntTag("peer.port");
    public static final IntTag SAMPLING_PRIORITY = new IntTag("sampling.priority");
    public static final StringTag SPAN_KIND = new StringTag("span.kind");
    public static final StringTag COMPONENT = new StringTag("component");
    public static final BooleanTag ERROR = new BooleanTag("error");
    public static final StringTag DB_TYPE = new StringTag("db.type");
    public static final StringTag DB_INSTANCE = new StringTag("db.instance");
    public static final StringTag DB_USER = new StringTag("db.user");
    public static final StringTag DB_STATEMENT = new StringTag("db.statement");
    public static final StringTag MESSAGE_BUS_DESTINATION = new StringTag("message_bus.destination");

    private Tags() {
        // no-op
    }
}
