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
package org.apache.camel.model;

import java.util.Iterator;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

public class ProcessorDefinitionHelperTest extends ContextTestSupport {

    @Test
    public void testFilterTypeInOutputs() {
        RouteDefinition route = context.getRouteDefinitions().get(0);

        Iterator<ProcessorDefinition> it
                = ProcessorDefinitionHelper.filterTypeInOutputs(route.getOutputs(), ProcessorDefinition.class).iterator();
        assertNotNull(it);

        assertThat(it.next().getId()).matches("choice[0-9]+");
        assertEquals("foo", it.next().getId());
        assertEquals("bar", it.next().getId());
        assertEquals("baz", it.next().getId());
        assertFalse(it.hasNext());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").choice().when(header("foo")).id("whenfoo").to("mock:foo").id("foo").when(header("bar"))
                        .id("whenbar").to("mock:bar").id("bar").otherwise()
                        .to("mock:baz").id("baz");
            }
        };
    }

}
