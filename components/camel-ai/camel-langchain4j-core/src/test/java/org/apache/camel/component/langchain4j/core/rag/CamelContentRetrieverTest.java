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
package org.apache.camel.component.langchain4j.core.rag;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.langchain4j.core.LangChain4j;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.langchain4j.core.rag.CamelContentRetrieverConstants.METADATA_DOCUMENT_ID;
import static org.apache.camel.component.langchain4j.core.rag.CamelContentRetrieverConstants.METADATA_SOURCE;
import static org.apache.camel.component.langchain4j.core.rag.CamelContentRetrieverConstants.QUERY_CHAT_MEMORY_ID;
import static org.apache.camel.component.langchain4j.core.rag.CamelContentRetrieverConstants.QUERY_PARAM_PREFIX;
import static org.apache.camel.component.langchain4j.core.rag.CamelContentRetrieverConstants.QUERY_USER_MESSAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CamelContentRetrieverTest extends CamelTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:string").setBody(simple("found: ${body}"));
                from("direct:strings").setBody(constant(List.of("a", " ", "b")));
                from("direct:empty").setBody(constant(List.of()));
                from("direct:null").setBody(constant(null));
                from("direct:maps").process(e -> {
                    Map<String, Object> row1 = new LinkedHashMap<>();
                    row1.put("id", 1);
                    row1.put("title", "Inception");
                    Map<String, Object> row2 = new LinkedHashMap<>();
                    row2.put("id", 2);
                    row2.put("title", "Heat");
                    e.getMessage().setBody(List.of(row1, row2));
                });
                from("direct:segments").setBody(constant(List.of(
                        TextSegment.from("seg", Metadata.from("lang", "en")),
                        Content.from("content"))));
                from("direct:other").setBody(constant(List.of(42, new StringBuilder("sb"))));
                from("direct:headers")
                        .setHeader(LangChain4j.METADATA_PREFIX + "score", constant(0.5d))
                        .setHeader(LangChain4j.METADATA_PREFIX + "flag", constant(true))
                        .setHeader("ignored", constant("x"))
                        // Arrays.asList keeps missing properties as null elements, which the converter skips
                        .setBody(constant("doc"));
                from("direct:echoProperties").process(e -> e.getMessage().setBody(Arrays.asList(
                        e.getProperty(QUERY_USER_MESSAGE, String.class),
                        e.getProperty(QUERY_CHAT_MEMORY_ID, String.class),
                        e.getProperty(QUERY_PARAM_PREFIX + "customerId", String.class),
                        String.valueOf(e.getMessage().getHeaders().keySet().stream()
                                .anyMatch(k -> k.startsWith("langchain4j.query."))))));
                from("direct:fail").throwException(new IllegalStateException("boom"));
            }
        };
    }

    private List<Content> retrieve(String uri, Query query) {
        return new CamelContentRetriever(context, "src", uri).retrieve(query);
    }

    private static List<String> texts(List<Content> contents) {
        return contents.stream().map(c -> c.textSegment().text()).toList();
    }

    @Test
    public void stringBody() {
        List<Content> contents = retrieve("direct:string", Query.from("orders"));
        assertThat(texts(contents)).containsExactly("found: orders");
        assertThat(contents.get(0).textSegment().metadata().getString(METADATA_SOURCE)).isEqualTo("src");
    }

    @Test
    public void stringListSkipsBlank() {
        assertThat(texts(retrieve("direct:strings", Query.from("q")))).containsExactly("a", "b");
    }

    @Test
    public void emptyAndNullBodies() {
        assertThat(retrieve("direct:empty", Query.from("q"))).isEmpty();
        assertThat(retrieve("direct:null", Query.from("q"))).isEmpty();
    }

    @Test
    public void mapListOneContentPerRow() {
        CamelContentRetriever retriever = new CamelContentRetriever(context, "movies", "direct:maps").setDocumentIdKey("id");
        List<Content> contents = retriever.retrieve(Query.from("q"));
        assertThat(texts(contents)).containsExactly("id: 1\ntitle: Inception", "id: 2\ntitle: Heat");
        assertThat(contents).extracting(c -> c.textSegment().metadata().getInteger(METADATA_DOCUMENT_ID))
                .containsExactly(1, 2);
    }

    @Test
    public void segmentsAndContentsKeepMetadata() {
        List<Content> contents = retrieve("direct:segments", Query.from("q"));
        assertThat(texts(contents)).containsExactly("seg", "content");
        assertThat(contents.get(0).textSegment().metadata().getString("lang")).isEqualTo("en");
        assertThat(contents).allSatisfy(c -> assertThat(c.textSegment().metadata().getString(METADATA_SOURCE))
                .isEqualTo("src"));
    }

    @Test
    public void otherElementsUseTypeConverter() {
        assertThat(texts(retrieve("direct:other", Query.from("q")))).containsExactly("42", "sb");
    }

    @Test
    public void metadataHeadersCopied() {
        Metadata metadata = retrieve("direct:headers", Query.from("q")).get(0).textSegment().metadata();
        assertThat(metadata.getDouble("score")).isEqualTo(0.5d);
        assertThat(metadata.getString("flag")).isEqualTo("true");
        assertThat(metadata.containsKey("ignored")).isFalse();
    }

    @Test
    public void queryMetadataMappedToProperties() {
        InvocationParameters parameters = new InvocationParameters();
        parameters.put("customerId", "c-42");
        Query query = Query.from("rewritten", dev.langchain4j.rag.query.Metadata.builder()
                .chatMessage(UserMessage.from("original"))
                .invocationContext(InvocationContext.builder()
                        .chatMemoryId("mem-1")
                        .invocationParameters(parameters)
                        .build())
                .build());

        assertThat(texts(retrieve("direct:echoProperties", query)))
                .containsExactly("original", "mem-1", "c-42", "false");
    }

    @Test
    public void queryWithoutMetadata() {
        assertThat(texts(retrieve("direct:echoProperties", Query.from("q")))).containsExactly("false");
    }

    @Test
    public void routeExceptionPropagated() {
        assertThatThrownBy(() -> retrieve("direct:fail", Query.from("q")))
                .isInstanceOf(CamelExecutionException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("boom");
    }

    @Test
    public void startStop() {
        CamelContentRetriever retriever = new CamelContentRetriever(context, "src", "direct:string");
        retriever.start();
        assertThat(texts(retriever.retrieve(Query.from("x")))).containsExactly("found: x");
        retriever.stop();
        assertThat(texts(retriever.retrieve(Query.from("y")))).containsExactly("found: y");
    }

    @Test
    public void toStringSanitizesUri() {
        assertThat(new CamelContentRetriever(context, "kb", "http://host/search?password=secret"))
                .hasToString("CamelContentRetriever[kb -> http://host/search?password=xxxxxx]");
    }

}
