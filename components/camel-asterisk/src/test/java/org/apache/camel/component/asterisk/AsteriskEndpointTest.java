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
package org.apache.camel.component.asterisk;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AsteriskEndpointTest extends CamelTestSupport {

    @Test
    void testEndpointConfiguration() throws Exception {
        AsteriskComponent component = new AsteriskComponent();
        component.setCamelContext(context);

        AsteriskEndpoint endpoint = new AsteriskEndpoint(
                "asterisk://myVoIP?hostname=localhost&username=admin&password=secret&action=QUEUE_STATUS",
                component);
        endpoint.setName("myVoIP");
        endpoint.setHostname("localhost");
        endpoint.setUsername("admin");
        endpoint.setPassword("secret");
        endpoint.setAction(AsteriskAction.QUEUE_STATUS);

        assertEquals("myVoIP", endpoint.getName());
        assertEquals("localhost", endpoint.getHostname());
        assertEquals("admin", endpoint.getUsername());
        assertEquals("secret", endpoint.getPassword());
        assertEquals(AsteriskAction.QUEUE_STATUS, endpoint.getAction());
    }

    @Test
    void testServiceLocation() throws Exception {
        AsteriskComponent component = new AsteriskComponent();
        component.setCamelContext(context);

        AsteriskEndpoint endpoint = new AsteriskEndpoint(
                "asterisk://myVoIP?hostname=192.168.1.100&username=admin&password=secret",
                component);
        endpoint.setHostname("192.168.1.100");
        endpoint.setUsername("admin");

        assertEquals("192.168.1.100", endpoint.getServiceUrl());
        assertEquals("voip", endpoint.getServiceProtocol());
        assertNotNull(endpoint.getServiceMetadata());
        assertEquals("admin", endpoint.getServiceMetadata().get("username"));
    }

    @Test
    void testCreateProducer() throws Exception {
        AsteriskComponent component = new AsteriskComponent();
        component.setCamelContext(context);

        AsteriskEndpoint endpoint = new AsteriskEndpoint(
                "asterisk://myVoIP?hostname=localhost&username=admin&password=secret&action=QUEUE_STATUS",
                component);
        endpoint.setHostname("localhost");
        endpoint.setUsername("admin");
        endpoint.setPassword("secret");
        endpoint.setAction(AsteriskAction.QUEUE_STATUS);

        Producer producer = endpoint.createProducer();
        assertNotNull(producer);
        assertTrue(producer instanceof AsteriskProducer);
    }

    @Test
    void testCreateConsumer() throws Exception {
        AsteriskComponent component = new AsteriskComponent();
        component.setCamelContext(context);

        AsteriskEndpoint endpoint = new AsteriskEndpoint(
                "asterisk://myVoIP?hostname=localhost&username=admin&password=secret",
                component);
        endpoint.setHostname("localhost");
        endpoint.setUsername("admin");
        endpoint.setPassword("secret");

        Processor processor = Mockito.mock(Processor.class);
        Consumer consumer = endpoint.createConsumer(processor);

        assertNotNull(consumer);
        assertTrue(consumer instanceof AsteriskConsumer);
    }

    @Test
    void testEndpointValidation() throws Exception {
        AsteriskComponent component = new AsteriskComponent();
        component.setCamelContext(context);

        AsteriskEndpoint endpoint = new AsteriskEndpoint(
                "asterisk://myVoIP",
                component);

        // Should fail because hostname is not set
        assertThrows(IllegalArgumentException.class, () -> {
            endpoint.init();
        });
    }

    @Test
    void testEndpointValidationWithHostnameOnly() throws Exception {
        AsteriskComponent component = new AsteriskComponent();
        component.setCamelContext(context);

        AsteriskEndpoint endpoint = new AsteriskEndpoint(
                "asterisk://myVoIP?hostname=localhost",
                component);
        endpoint.setHostname("localhost");

        // Should fail because username is not set
        assertThrows(IllegalArgumentException.class, () -> {
            endpoint.init();
        });
    }

    @Test
    void testEndpointValidationSuccess() throws Exception {
        AsteriskComponent component = new AsteriskComponent();
        component.setCamelContext(context);

        AsteriskEndpoint endpoint = new AsteriskEndpoint(
                "asterisk://myVoIP?hostname=localhost&username=admin&password=secret",
                component);
        endpoint.setHostname("localhost");
        endpoint.setUsername("admin");
        endpoint.setPassword("secret");

        // Should succeed with all required parameters
        endpoint.init();

        assertEquals("localhost", endpoint.getHostname());
        assertEquals("admin", endpoint.getUsername());
        assertEquals("secret", endpoint.getPassword());
    }
}
