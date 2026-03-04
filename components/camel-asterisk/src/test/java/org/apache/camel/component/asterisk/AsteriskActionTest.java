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

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.asteriskjava.manager.action.ExtensionStateAction;
import org.asteriskjava.manager.action.ManagerAction;
import org.asteriskjava.manager.action.QueueStatusAction;
import org.asteriskjava.manager.action.SipPeersAction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AsteriskActionTest {

    @Test
    void testQueueStatusAction() {
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        ManagerAction action = AsteriskAction.QUEUE_STATUS.apply(exchange);

        assertNotNull(action);
        assertTrue(action instanceof QueueStatusAction);
    }

    @Test
    void testSipPeersAction() {
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        ManagerAction action = AsteriskAction.SIP_PEERS.apply(exchange);

        assertNotNull(action);
        assertTrue(action instanceof SipPeersAction);
    }

    @Test
    void testExtensionStateAction() {
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.getIn().setHeader(AsteriskConstants.EXTENSION, "1234");
        exchange.getIn().setHeader(AsteriskConstants.CONTEXT, "default");

        ManagerAction action = AsteriskAction.EXTENSION_STATE.apply(exchange);

        assertNotNull(action);
        assertTrue(action instanceof ExtensionStateAction);
    }

    @Test
    void testExtensionStateActionWithNullHeaders() {
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());

        ManagerAction action = AsteriskAction.EXTENSION_STATE.apply(exchange);

        assertNotNull(action);
        assertTrue(action instanceof ExtensionStateAction);
    }
}
