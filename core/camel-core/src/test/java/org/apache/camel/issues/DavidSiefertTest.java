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
package org.apache.camel.issues;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class DavidSiefertTest extends ContextTestSupport {
    protected static final Object expectedBody = "Some Output";

    @Test
    public void testWorks() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);
        result.expectedBodiesReceived(expectedBody);
        result.message(0).header("sample.name").isEqualTo("myValue");
        template.sendBody("direct:start", "<sample><name>value</name></sample>");
        result.assertIsSatisfied();
    }

    @Test
    public void testHeaderPredicateFails() {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.message(0).header("sample.name").isEqualTo("shouldNotMatch");
        template.sendBody("direct:start", "<sample><name>value</name></sample>");

        AssertionError e = assertThrows(AssertionError.class, result::assertIsSatisfied,
                "Should have failed this test!");

        log.info("Caught expected assertion failure: {}", e, e);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").process(new MyProcessor()).to("mock:result");
            }
        };
    }

    public static class MyProcessor implements Processor {
        @Override
        public void process(Exchange exchange) {
            exchange.getIn().getBody(String.class);

            Message output = exchange.getMessage();
            output.setHeader("sample.name", "myValue");
            output.setBody(expectedBody);
        }
    }
}
