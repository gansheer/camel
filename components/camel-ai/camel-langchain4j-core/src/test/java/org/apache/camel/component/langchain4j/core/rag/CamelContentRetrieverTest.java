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

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.RuntimeCamelException;
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
import static org.awaitility.Awaitility.await;

public class CamelContentRetrieverTest extends CamelTestSupport {

    private final CountDownLatch holdStarted = new CountDownLatch(1);
    private final CountDownLatch holdRelease = new CountDownLatch(1);
    private final AtomicInteger holdCalls = new AtomicInteger();

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
                from("direct:three").setBody(constant(List.of("one", "two", "three")));
                from("direct:slow").delay(400).setBody(constant("late"));
                from("direct:hold").process(e -> {
                    holdCalls.incrementAndGet();
                    holdStarted.countDown();
                    holdRelease.await(5, TimeUnit.SECONDS);
                }).setBody(constant("held"));
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
                from("direct:spoof")
                        .setHeader(LangChain4j.METADATA_PREFIX + METADATA_SOURCE, constant("header spoof"))
                        .setHeader(LangChain4j.METADATA_PREFIX + METADATA_DOCUMENT_ID, constant("header spoof"))
                        .setBody(constant(List.of(
                                TextSegment.from("row", Metadata.from(METADATA_SOURCE, "segment spoof")))));
                from("direct:fail").throwException(new IllegalStateException("boom"));
            }
        };
    }

    private List<Content> retrieve(String uri, Query query) {
        return retrieve(uri, query, null);
    }

    private List<Content> retrieve(String uri, Query query, Consumer<CamelContentRetriever> configure) {
        CamelContentRetriever retriever = new CamelContentRetriever(context, "src", uri);
        if (configure != null) {
            configure.accept(retriever);
        }
        retriever.start();
        try {
            return retriever.retrieve(query);
        } finally {
            retriever.stop();
        }
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
        retriever.start();
        try {
            List<Content> contents = retriever.retrieve(Query.from("q"));
            assertThat(texts(contents)).containsExactly("id: 1\ntitle: Inception", "id: 2\ntitle: Heat");
            assertThat(contents).extracting(c -> c.textSegment().metadata().getInteger(METADATA_DOCUMENT_ID))
                    .containsExactly(1, 2);
        } finally {
            retriever.stop();
        }
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
    public void reservedKeysAreNotSpoofed() {
        Metadata metadata = retrieve("direct:spoof", Query.from("q")).get(0).textSegment().metadata();
        assertThat(metadata.getString(METADATA_SOURCE)).isEqualTo("src");
        assertThat(metadata.getString(METADATA_DOCUMENT_ID)).isNull();
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
    public void notStartedIsReported() {
        CamelContentRetriever retriever = new CamelContentRetriever(context, "src", "direct:string");
        assertThatThrownBy(() -> retriever.retrieve(Query.from("x")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not started");
    }

    @Test
    public void startStop() {
        CamelContentRetriever retriever = new CamelContentRetriever(context, "src", "direct:string");
        retriever.start();
        try {
            assertThat(texts(retriever.retrieve(Query.from("x")))).containsExactly("found: x");
        } finally {
            retriever.stop();
        }
        assertThatThrownBy(() -> retriever.retrieve(Query.from("y")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void addServiceStartsAndStopsWithTheContext() throws Exception {
        CamelContentRetriever retriever = new CamelContentRetriever(context, "src", "direct:string");
        context.addService(retriever);
        assertThat(texts(retriever.retrieve(Query.from("x")))).containsExactly("found: x");

        context.stop();

        // stopping the context stopped the retriever: its template and executor are gone
        assertThatThrownBy(() -> retriever.retrieve(Query.from("y")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not started");
    }

    @Test
    public void timeoutFailsByDefault() {
        assertThatThrownBy(() -> retrieve("direct:slow", Query.from("q"), r -> r.setTimeout(Duration.ofMillis(50))))
                .isInstanceOf(CamelExecutionException.class)
                .hasMessageContaining("timed out")
                .hasRootCauseInstanceOf(TimeoutException.class);
    }

    @Test
    public void timeoutReturnsEmptyWithOnErrorEmpty() {
        assertThat(retrieve("direct:slow", Query.from("q"),
                r -> r.setTimeout(Duration.ofMillis(50)).setOnError(OnError.EMPTY))).isEmpty();
    }

    @Test
    public void routeExceptionFollowsOnError() {
        assertThat(retrieve("direct:fail", Query.from("q"), r -> r.setOnError(OnError.EMPTY))).isEmpty();
        assertThatThrownBy(() -> retrieve("direct:fail", Query.from("q"), null))
                .isInstanceOf(CamelExecutionException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("boom");
    }

    @Test
    public void maxResultsCapsItems() {
        assertThat(texts(retrieve("direct:three", Query.from("q"), r -> r.setMaxResults(2))))
                .containsExactly("one", "two");
    }

    @Test
    public void maxCharsTruncatesCombinedText() {
        // "one" + "two" = 6 characters, so 5 keeps "one" and the first two characters of "two"
        assertThat(texts(retrieve("direct:three", Query.from("q"), r -> r.setMaxChars(5))))
                .containsExactly("one", "tw");
    }

    @Test
    public void invalidLimitsRejected() {
        CamelContentRetriever retriever = new CamelContentRetriever(context, "src", "direct:string");
        assertThatThrownBy(() -> retriever.setTimeout(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> retriever.setMaxResults(-2)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void invalidPoolSettingsRejected() {
        CamelContentRetriever retriever = new CamelContentRetriever(context, "src", "direct:string");
        assertThatThrownBy(() -> retriever.setConcurrency(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> retriever.setQueueCapacity(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void subMillisecondTimeout() {
        long start = System.nanoTime();
        assertThatThrownBy(() -> retrieve("direct:slow", Query.from("q"),
                r -> r.setTimeout(Duration.ofNanos(100_000))))
                .isInstanceOf(CamelExecutionException.class)
                .hasMessageContaining("timed out")
                .hasRootCauseInstanceOf(TimeoutException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
    }

    private CamelContentRetriever holdRetriever() {
        return new CamelContentRetriever(context, "hold", "direct:hold").setConcurrency(1).setQueueCapacity(1);
    }

    private Future<List<Content>> submitRetrieve(
            ExecutorService callers, AtomicReference<Thread> caller,
            CamelContentRetriever retriever, String text) {
        return callers.submit(() -> {
            caller.set(Thread.currentThread());
            return retriever.retrieve(Query.from(text));
        });
    }

    // fills the single thread and the single queue slot
    private Future<List<Content>> holdThreadAndQueue(
            CamelContentRetriever retriever, ExecutorService callers,
            AtomicReference<Thread> queuedCaller)
            throws InterruptedException {
        submitRetrieve(callers, new AtomicReference<>(), retriever, "a");
        assertThat(holdStarted.await(5, TimeUnit.SECONDS)).isTrue();
        Future<List<Content>> queued = submitRetrieve(callers, queuedCaller, retriever, "b");
        // the second call waits for a free thread only once the queue holds it
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(queuedCaller.get()).isNotNull();
            assertThat(queuedCaller.get().getState()).isEqualTo(Thread.State.WAITING);
        });
        return queued;
    }

    @Test
    public void saturatedExecutorFollowsOnError() throws Exception {
        CamelContentRetriever retriever = holdRetriever();
        retriever.start();
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            Future<List<Content>> queued = holdThreadAndQueue(retriever, callers, new AtomicReference<>());

            assertThatThrownBy(() -> retriever.retrieve(Query.from("c")))
                    .isInstanceOf(CamelExecutionException.class)
                    .hasMessageContaining("was rejected")
                    .hasRootCauseInstanceOf(RejectedExecutionException.class);

            holdRelease.countDown();
            assertThat(texts(queued.get(5, TimeUnit.SECONDS))).containsExactly("held");
        } finally {
            holdRelease.countDown();
            callers.shutdownNow();
            retriever.stop();
        }
    }

    @Test
    public void stopCompletesQueuedRetrievals() throws Exception {
        CamelContentRetriever retriever = holdRetriever();
        retriever.start();
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            Future<List<Content>> queued = holdThreadAndQueue(retriever, callers, new AtomicReference<>());

            retriever.stop();

            // the exchange that never reached the route is completed by the retriever and follows on-error
            assertThatThrownBy(() -> queued.get(5, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasRootCauseInstanceOf(RejectedExecutionException.class);
            assertThatThrownBy(() -> retriever.retrieve(Query.from("c")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not started");
        } finally {
            holdRelease.countDown();
            callers.shutdownNow();
        }
    }

    @Test
    public void queuedCallIsSkippedAfterItsTimeout() throws Exception {
        // room for "b" and "c" in the queue, so "c" is never rejected while "b" waits to be skipped
        CamelContentRetriever retriever = new CamelContentRetriever(context, "hold", "direct:hold")
                .setConcurrency(1).setQueueCapacity(2)
                .setTimeout(Duration.ofMillis(500)).setOnError(OnError.EMPTY);
        retriever.start();
        try {
            // times out while the route keeps holding the only thread
            assertThat(retriever.retrieve(Query.from("a"))).isEmpty();
            assertThat(holdStarted.await(5, TimeUnit.SECONDS)).isTrue();
            // waits in the queue and times out there
            assertThat(retriever.retrieve(Query.from("b"))).isEmpty();

            holdRelease.countDown();
            // queued behind "b": once it answers, the freed thread has already skipped "b"
            assertThat(texts(retriever.retrieve(Query.from("c")))).containsExactly("held");
            assertThat(holdCalls).hasValue(2);
        } finally {
            holdRelease.countDown();
            retriever.stop();
        }
    }

    @Test
    public void configurationIsRejectedOnceStarted() {
        CamelContentRetriever retriever = new CamelContentRetriever(context, "src", "direct:string");
        retriever.start();
        try {
            assertThatThrownBy(() -> retriever.setTimeout(Duration.ofSeconds(1)))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> retriever.setConcurrency(4)).isInstanceOf(IllegalStateException.class);
        } finally {
            retriever.stop();
        }
        retriever.setConcurrency(4);
    }

    @Test
    public void unknownEndpointFailsStart() {
        CamelContentRetriever retriever = new CamelContentRetriever(context, "src", "unknown-scheme:x");
        assertThatThrownBy(retriever::start).isInstanceOf(RuntimeCamelException.class);
        assertThatThrownBy(() -> retriever.retrieve(Query.from("q")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not started");
    }

    @Test
    public void toStringSanitizesUri() {
        assertThat(new CamelContentRetriever(context, "kb", "http://host/search?password=secret"))
                .hasToString("CamelContentRetriever[kb -> http://host/search?password=xxxxxx]");
    }

}
