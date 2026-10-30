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

import java.util.List;
import java.util.Map;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Metadata;
import dev.langchain4j.rag.query.router.DefaultQueryRouter;
import dev.langchain4j.rag.query.router.LanguageModelQueryRouter;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.langchain4j.core.rag.CamelContentRetrieverConstants.METADATA_SOURCE;
import static org.apache.camel.component.langchain4j.core.rag.CamelContentRetrieverConstants.QUERY_PARAM_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;

public class CamelContentRetrieverAugmentorTest extends CamelTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:orders")
                        .setBody(simple("Order 1001 of ${exchangeProperty[" + QUERY_PARAM_PREFIX + "customerId]} is delayed"));
                from("direct:kb")
                        .setBody(constant(List.of("Delayed orders can be cancelled for a full refund")));
            }
        };
    }

    private static AugmentationRequest request(String text, String customerId) {
        InvocationParameters parameters = new InvocationParameters();
        parameters.put("customerId", customerId);
        UserMessage message = UserMessage.from(text);
        return new AugmentationRequest(
                message, Metadata.builder()
                        .chatMessage(message)
                        .invocationContext(InvocationContext.builder().invocationParameters(parameters).build())
                        .build());
    }

    private static DefaultContentInjector injector() {
        return DefaultContentInjector.builder().metadataKeysToInclude(List.of(METADATA_SOURCE)).build();
    }

    @Test
    public void allSourcesInjected() {
        ContentRetriever orders = new CamelContentRetriever(context, "orders", "direct:orders");
        ContentRetriever kb = new CamelContentRetriever(context, "kb", "direct:kb");
        RetrievalAugmentor augmentor = DefaultRetrievalAugmentor.builder()
                .queryRouter(new DefaultQueryRouter(orders, kb))
                .contentInjector(injector())
                .build();

        AugmentationResult result = augmentor.augment(request("Where is my order?", "c-42"));

        String prompt = ((UserMessage) result.chatMessage()).singleText();
        assertThat(prompt)
                .contains("Order 1001 of c-42 is delayed")
                .contains("Delayed orders can be cancelled")
                .contains(METADATA_SOURCE + ": orders")
                .contains(METADATA_SOURCE + ": kb");
    }

    @Test
    public void languageModelRouterPicksSource() {
        ContentRetriever orders = new CamelContentRetriever(context, "orders", "direct:orders");
        ContentRetriever kb = new CamelContentRetriever(context, "kb", "direct:kb");
        // ids follow map iteration order, so the stub answers with the id whose description matches
        Map<ContentRetriever, String> catalog = Map.of(
                orders, "The customer's recent orders",
                kb, "Support articles about refunds");
        ChatModel router = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest chatRequest) {
                String prompt = ((UserMessage) chatRequest.messages().get(chatRequest.messages().size() - 1)).singleText();
                String id = prompt.lines()
                        .filter(l -> l.contains("Support articles"))
                        .map(l -> l.substring(0, l.indexOf(':')).trim())
                        .findFirst().orElseThrow();
                return ChatResponse.builder().aiMessage(AiMessage.from(id)).build();
            }
        };
        RetrievalAugmentor augmentor = DefaultRetrievalAugmentor.builder()
                .queryRouter(LanguageModelQueryRouter.builder().chatModel(router).retrieverToDescription(catalog).build())
                .contentInjector(injector())
                .build();

        AugmentationResult result = augmentor.augment(request("Can I get a refund?", "c-42"));

        assertThat(result.contents()).hasSize(1);
        assertThat(result.contents().get(0).textSegment().metadata().getString(METADATA_SOURCE)).isEqualTo("kb");
    }
}
