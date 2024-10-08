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
package org.apache.camel.component.bean;

import java.io.ByteArrayInputStream;

import org.apache.camel.BeanScope;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BeanExplicitMethodAmbiguousTest extends ContextTestSupport {

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry jndi = super.createCamelRegistry();
        jndi.bind("dummy", new MyDummyBean());
        return jndi;
    }

    @Test
    public void testBeanExplicitMethodAmbiguous() {
        Exception e = assertThrows(Exception.class,
                () -> template.requestBody("direct:hello", "Camel"),
                "Should thrown an exception");

        AmbiguousMethodCallException cause = assertIsInstanceOf(AmbiguousMethodCallException.class, e.getCause());
        assertEquals(2, cause.getMethods().size());
    }

    @Test
    public void testBeanExplicitMethodHandler() {
        String out = template.requestBody("direct:bye", "Camel", String.class);
        assertEquals("Bye Camel", out);
    }

    @Test
    public void testBeanExplicitMethodInvocationStringBody() {
        String out = template.requestBody("direct:foo", "Camel", String.class);
        assertEquals("String", out);
    }

    @Test
    public void testBeanExplicitMethodInvocationInputStreamBody() {
        String out = template.requestBody("direct:foo", new ByteArrayInputStream("Camel".getBytes()), String.class);
        assertEquals("InputStream", out);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:hello").bean("dummy", "hello", BeanScope.Singleton);

                from("direct:bye").bean("dummy", BeanScope.Singleton);

                from("direct:foo").bean("dummy", "bar", BeanScope.Singleton);
            }
        };
    }
}
