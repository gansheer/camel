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
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.component.langchain4j.core.LangChain4j;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.langchain4j.core.rag.CamelContentRetrieverConstants.METADATA_DOCUMENT_ID;
import static org.apache.camel.component.langchain4j.core.rag.CamelContentRetrieverConstants.METADATA_SOURCE;
import static org.assertj.core.api.Assertions.assertThat;

public class ContentConverterTest extends CamelTestSupport {

    private Exchange exchange(Object body) {
        Exchange exchange = new DefaultExchange(context, ExchangePattern.InOut);
        exchange.getMessage().setBody(body);
        return exchange;
    }

    private static List<String> texts(List<Content> contents) {
        return contents.stream().map(c -> c.textSegment().text()).toList();
    }

    private static Map<String, Object> metadata(Content content) {
        return content.textSegment().metadata().toMap();
    }

    @Test
    public void nullBodyGivesEmptyList() {
        assertThat(ContentConverter.toContents(exchange(null), "src", null, -1, -1)).isEmpty();
    }

    @Test
    public void emptyCollectionGivesEmptyList() {
        assertThat(ContentConverter.toContents(exchange(List.of()), "src", null, -1, -1)).isEmpty();
    }

    @Test
    public void singleStringBodyGivesSingleContent() {
        List<Content> contents = ContentConverter.toContents(exchange("doc"), "src", null, -1, -1);
        assertThat(texts(contents)).containsExactly("doc");
        assertThat(metadata(contents.get(0)).get(METADATA_SOURCE)).isEqualTo("src");
    }

    @Test
    public void singleOtherBodyIsTypeConverted() {
        assertThat(texts(ContentConverter.toContents(exchange(42), "src", null, -1, -1))).containsExactly("42");
    }

    @Test
    public void stringElementsSkipBlankAndNull() {
        List<String> body = Arrays.asList("a", "", " ", null, "b");
        assertThat(texts(ContentConverter.toContents(exchange(body), "src", null, -1, -1)))
                .containsExactly("a", "b");
    }

    @Test
    public void mapElementsGiveKeyColonValueLines() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1);
        row.put("title", "Inception");

        List<Content> contents = ContentConverter.toContents(exchange(List.of(row)), "movies", null, -1, -1);

        assertThat(texts(contents)).containsExactly("id: 1\ntitle: Inception");
        assertThat(metadata(contents.get(0)).get(METADATA_DOCUMENT_ID)).isNull();
    }

    @Test
    public void mapElementDocumentIdKeepsItsType() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1);
        row.put("title", "Inception");

        List<Content> contents = ContentConverter.toContents(exchange(List.of(row)), "movies", "id", -1, -1);

        assertThat(contents.get(0).textSegment().metadata().getInteger(METADATA_DOCUMENT_ID)).isEqualTo(1);
    }

    @Test
    public void segmentAndContentElementsKeepTheirMetadata() {
        Content content = Content.from(TextSegment.from("c"), Map.of(ContentMetadata.SCORE, 0.9d));
        List<Content> contents = ContentConverter.toContents(
                exchange(List.of(TextSegment.from("seg", Metadata.from("lang", "en")), content)), "src", null, -1, -1);

        assertThat(texts(contents)).containsExactly("seg", "c");
        assertThat(contents.get(0).textSegment().metadata().getString("lang")).isEqualTo("en");
        assertThat(contents.get(1).metadata().get(ContentMetadata.SCORE)).isEqualTo(0.9d);
        assertThat(contents).allSatisfy(c -> assertThat(metadata(c).get(METADATA_SOURCE)).isEqualTo("src"));
    }

    @Test
    public void prefixedHeadersAreCopiedAndStripped() {
        Exchange exchange = exchange("doc");
        exchange.getMessage().setHeader(LangChain4j.METADATA_PREFIX + "score", 0.5d);
        exchange.getMessage().setHeader(LangChain4j.METADATA_PREFIX + "flag", true);
        exchange.getMessage().setHeader("ignored", "x");

        Metadata metadata = ContentConverter.toContents(exchange, "src", null, -1, -1).get(0).textSegment().metadata();

        assertThat(metadata.getDouble("score")).isEqualTo(0.5d);
        assertThat(metadata.getString("flag")).isEqualTo("true");
        assertThat(metadata.containsKey("ignored")).isFalse();
    }

    @Test
    public void reservedKeysWinOverHeadersAndSegment() {
        Exchange exchange = exchange(List.of(TextSegment.from("seg", Metadata.from(METADATA_SOURCE, "segment spoof"))));
        exchange.getMessage().setHeader(LangChain4j.METADATA_PREFIX + METADATA_SOURCE, "header spoof");
        exchange.getMessage().setHeader(LangChain4j.METADATA_PREFIX + METADATA_DOCUMENT_ID, "header spoof");

        Map<String, Object> metadata = metadata(ContentConverter.toContents(exchange, "src", null, -1, -1).get(0));

        assertThat(metadata.get(METADATA_SOURCE)).isEqualTo("src");
        assertThat(metadata.get(METADATA_DOCUMENT_ID)).isNull();
    }

    @Test
    public void reservedDocumentIdWinsOverHeaders() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 7);
        row.put("title", "Heat");
        Exchange exchange = exchange(List.of(row));
        exchange.getMessage().setHeader(LangChain4j.METADATA_PREFIX + METADATA_DOCUMENT_ID, "header spoof");

        Content content = ContentConverter.toContents(exchange, "movies", "id", -1, -1).get(0);

        assertThat(content.textSegment().metadata().getInteger(METADATA_DOCUMENT_ID)).isEqualTo(7);
    }

    @Test
    public void segmentDocumentIdIsKept() {
        // a route may map its own id to the reserved key (e.g. an ingest document id), unlike a response header
        TextSegment segment = TextSegment.from("policy text", Metadata.from(METADATA_DOCUMENT_ID, "expenses.md"));

        Content content = ContentConverter.toContents(exchange(List.of(segment)), "policies", null, -1, -1).get(0);

        assertThat(content.textSegment().metadata().getString(METADATA_SOURCE)).isEqualTo("policies");
        assertThat(content.textSegment().metadata().getString(METADATA_DOCUMENT_ID)).isEqualTo("expenses.md");
    }

    @Test
    public void maxResultsKeepsTheFirstItems() {
        assertThat(texts(ContentConverter.toContents(exchange(List.of("one", "two", "three")), "src", null, 2, -1)))
                .containsExactly("one", "two");
    }

    @Test
    public void maxCharsTruncatesTheCombinedText() {
        // "one" + "two" = 6 characters, so 5 keeps "one" and the first two characters of "two"
        assertThat(texts(ContentConverter.toContents(exchange(List.of("one", "two")), "src", null, -1, 5)))
                .containsExactly("one", "tw");
    }

    @Test
    public void maxCharsAtExactBoundaryKeepsEverything() {
        assertThat(texts(ContentConverter.toContents(exchange(List.of("one", "two")), "src", null, -1, 6)))
                .containsExactly("one", "two");
    }

    @Test
    public void maxCharsZeroGivesEmptyList() {
        assertThat(ContentConverter.toContents(exchange(List.of("one", "two")), "src", null, -1, 0)).isEmpty();
    }
}
