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
package org.apache.camel.tooling.maven;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryHelperTest {

    @Test
    void parseIdUrlFormat() {
        List<RepositoryHelper.RepositorySpec> specs
                = RepositoryHelper.parseRepositories("atlassian=https://packages.atlassian.com/maven-external/");

        assertEquals(1, specs.size());
        assertEquals("atlassian", specs.get(0).id());
        assertEquals("https://packages.atlassian.com/maven-external/", specs.get(0).url());
        assertFalse(specs.get(0).snapshot());
    }

    @Test
    void parsePlainUrl() {
        List<RepositoryHelper.RepositorySpec> specs
                = RepositoryHelper.parseRepositories("https://packages.atlassian.com/maven-external/");

        assertEquals(1, specs.size());
        assertEquals("custom1", specs.get(0).id());
        assertEquals("https://packages.atlassian.com/maven-external/", specs.get(0).url());
    }

    @Test
    void parseMultiple() {
        List<RepositoryHelper.RepositorySpec> specs = RepositoryHelper.parseRepositories(
                "repo1=https://repo1.example.com/maven2,repo2=https://repo2.example.com/releases");

        assertEquals(2, specs.size());
        assertEquals("repo1", specs.get(0).id());
        assertEquals("https://repo1.example.com/maven2", specs.get(0).url());
        assertEquals("repo2", specs.get(1).id());
        assertEquals("https://repo2.example.com/releases", specs.get(1).url());
    }

    @Test
    void parseMixedFormats() {
        List<RepositoryHelper.RepositorySpec> specs = RepositoryHelper.parseRepositories(
                "atlassian=https://packages.atlassian.com/maven-external/,https://other.repo/releases");

        assertEquals(2, specs.size());
        assertEquals("atlassian", specs.get(0).id());
        assertEquals("https://packages.atlassian.com/maven-external/", specs.get(0).url());
        assertEquals("custom1", specs.get(1).id());
        assertEquals("https://other.repo/releases", specs.get(1).url());
    }

    @Test
    void parseSnapshotDetection() {
        List<RepositoryHelper.RepositorySpec> specs
                = RepositoryHelper.parseRepositories("apache-snap=https://repository.apache.org/snapshots");

        assertEquals(1, specs.size());
        assertTrue(specs.get(0).snapshot());
    }

    @Test
    void parseNullAndEmpty() {
        assertTrue(RepositoryHelper.parseRepositories(null).isEmpty());
        assertTrue(RepositoryHelper.parseRepositories("").isEmpty());
        assertTrue(RepositoryHelper.parseRepositories("  ").isEmpty());
    }

    @Test
    void parseWithResolver() {
        DefaultRepositoryResolver resolver = new DefaultRepositoryResolver();
        resolver.build();

        List<RepositoryHelper.RepositorySpec> specs = RepositoryHelper.parseRepositories("atlassian", resolver);

        assertEquals(1, specs.size());
        assertEquals("custom1", specs.get(0).id());
        assertEquals("https://packages.atlassian.com/maven-external", specs.get(0).url());
    }

    @Test
    void loadExtraRepositoriesWhenNotSet() {
        String original = System.getProperty(RepositoryHelper.EXTRA_REPOS_PROPERTY);
        String originalDefault = System.getProperty(RepositoryHelper.EXTRA_REPOS_DEFAULT_VALUE_PROPERTY);
        try {
            System.clearProperty(RepositoryHelper.EXTRA_REPOS_PROPERTY);
            System.clearProperty(RepositoryHelper.EXTRA_REPOS_DEFAULT_VALUE_PROPERTY);

            assertTrue(RepositoryHelper.loadExtraRepositories().isEmpty());
        } finally {
            if (original != null) {
                System.setProperty(RepositoryHelper.EXTRA_REPOS_PROPERTY, original);
            }
            if (originalDefault != null) {
                System.setProperty(RepositoryHelper.EXTRA_REPOS_DEFAULT_VALUE_PROPERTY, originalDefault);
            }
        }
    }
}
