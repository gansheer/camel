/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.camel.test.infra.couchbase.services;

import org.apache.camel.test.infra.couchbase.common.CouchbaseProperties;

public class CouchbaseRemoteInfraService implements CouchbaseInfraService {
    @Override
    public String getConnectionString() {
        final int kvPort = 11210;
        return String.format("couchbase://%s:%d", getHostname(), kvPort);
    }

    @Override
    public String getUsername() {
        return System.getProperty(CouchbaseProperties.COUCHBASE_USERNAME, "Administrator");
    }

    @Override
    public String getPassword() {
        return System.getProperty(CouchbaseProperties.COUCHBASE_PASSWORD);
    }

    @Override
    public String getHostname() {
        return System.getProperty(CouchbaseProperties.COUCHBASE_HOSTNAME);
    }

    @Override
    public int getPort() {
        String portValue = System.getProperty(CouchbaseProperties.COUCHBASE_PORT, "8091");

        return Integer.parseInt(portValue);
    }

    @Override
    public String protocol() {
        return System.getProperty(CouchbaseProperties.COUCHBASE_PROTOCOL, "http");
    }

    @Override
    public String hostname() {
        return System.getProperty(CouchbaseProperties.COUCHBASE_HOSTNAME);
    }

    @Override
    public int port() {
        String portValue = System.getProperty(CouchbaseProperties.COUCHBASE_PORT, "8091");

        return Integer.parseInt(portValue);
    }

    @Override
    public String username() {
        return System.getProperty(CouchbaseProperties.COUCHBASE_USERNAME, "Administrator");
    }

    @Override
    public String password() {
        return System.getProperty(CouchbaseProperties.COUCHBASE_PASSWORD);
    }

    @Override
    public String bucket() {
        throw new IllegalArgumentException("CouchbaseRemoteInfraService does not support bucket creation");
    }

    @Override
    public String viewName() {
        throw new IllegalArgumentException("CouchbaseRemoteInfraService does not support view creation");
    }

    @Override
    public String designDocumentName() {
        throw new IllegalArgumentException("CouchbaseRemoteInfraService does not support design document creation");
    }

    @Override
    public void registerProperties() {
        // NO-OP
    }

    @Override
    public void initialize() {
        registerProperties();
    }

    @Override
    public void shutdown() {

    }
}
