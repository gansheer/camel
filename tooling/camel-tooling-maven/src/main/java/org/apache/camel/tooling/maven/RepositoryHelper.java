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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class RepositoryHelper {

    public static final String EXTRA_REPOS_PROPERTY = "camel.extra.repos";
    public static final String EXTRA_REPOS_DEFAULT_VALUE_PROPERTY = "camel.default.extra.repos.default.value";

    private RepositoryHelper() {
    }

    public record RepositorySpec(String id, String url, boolean snapshot) {
    }

    /**
     * Parses a comma-separated repository string into a list of {@link RepositorySpec}. Supports both plain URLs and
     * {@code id=url} format. When a {@link RepositoryResolver} is provided, short names (e.g. {@code "atlassian"}) are
     * resolved to URLs.
     */
    public static List<RepositorySpec> parseRepositories(String repos, RepositoryResolver resolver) {
        if (repos == null || repos.isBlank()) {
            return List.of();
        }
        List<RepositorySpec> result = new ArrayList<>();
        AtomicInteger counter = new AtomicInteger(1);
        for (String repo : repos.split("\\s*,\\s*")) {
            if (repo.isBlank()) {
                continue;
            }
            RepositorySpec spec = parseOne(repo, counter, resolver);
            if (spec != null) {
                result.add(spec);
            }
        }
        return result;
    }

    /**
     * Parses a comma-separated repository string without short-name resolution.
     */
    public static List<RepositorySpec> parseRepositories(String repos) {
        return parseRepositories(repos, null);
    }

    /**
     * Loads extra repositories from system properties {@value #EXTRA_REPOS_PROPERTY} and
     * {@value #EXTRA_REPOS_DEFAULT_VALUE_PROPERTY}.
     */
    public static List<RepositorySpec> loadExtraRepositories(RepositoryResolver resolver) {
        String value = System.getProperty(EXTRA_REPOS_PROPERTY, System.getProperty(EXTRA_REPOS_DEFAULT_VALUE_PROPERTY));
        return parseRepositories(value, resolver);
    }

    /**
     * Loads extra repositories from system properties without short-name resolution.
     */
    public static List<RepositorySpec> loadExtraRepositories() {
        return loadExtraRepositories(null);
    }

    private static RepositorySpec parseOne(String repo, AtomicInteger counter, RepositoryResolver resolver) {
        String id;
        String url;

        int eqIdx = repo.indexOf('=');
        if (eqIdx > 0 && eqIdx < repo.length() - 1 && !repo.startsWith("http")) {
            id = repo.substring(0, eqIdx);
            url = repo.substring(eqIdx + 1);
        } else {
            id = null;
            url = repo;
        }

        if (resolver != null) {
            String resolved = resolver.resolveRepository(url);
            if (resolved != null && !resolved.isBlank()) {
                url = resolved;
            }
        }

        if (id == null) {
            id = "custom" + counter.getAndIncrement();
        }

        boolean snapshot = url.contains("snapshots");
        return new RepositorySpec(id, url, snapshot);
    }
}
