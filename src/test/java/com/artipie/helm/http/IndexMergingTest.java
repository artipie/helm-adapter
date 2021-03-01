/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020 artipie.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.artipie.helm.http;

import com.artipie.asto.ext.PublisherAs;
import com.artipie.asto.test.TestResource;
import com.artipie.helm.metadata.IndexYamlMapping;
import java.util.stream.Collectors;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test cases for {@link IndexMergingSlice.IndexMerging}.
 * @since 0.2
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
final class IndexMergingTest {
    /**
     * Source `index.yaml`.
     */
    private IndexYamlMapping source;

    @BeforeEach
    void setUp() {
        this.source = this.index("merge/source/index.yaml");
    }

    @Test
    void mergedFileContainsRequiredCharts() {
        final IndexYamlMapping target = this.index("merge/output/index.yaml");
        MatcherAssert.assertThat(
            this.mergedIndex().entries().keySet(),
            Matchers.containsInAnyOrder(target.entries().keySet().toArray())
        );
    }

    @Test
    void tomcatContainsBothRequiredVersions() {
        final String chart = "tomcat";
        final IndexYamlMapping target = this.index("merge/output/index.yaml");
        MatcherAssert.assertThat(
            this.mergedIndex().byChart(chart).stream()
                .map(entry -> (String) entry.get("version"))
                .collect(Collectors.toList()),
            Matchers.containsInAnyOrder(
                target.byChart(chart).stream()
                    .map(entry -> (String) entry.get("version"))
                    .toArray(String[]::new)
            )
        );
    }

    @Test
    void datetimeWasUpdated() {
        final String chart = "tomcat";
        final String version = "0.1.0";
        final String created = "created";
        final String old = (String) this.source
            .byChartAndVersion(chart, version)
            .get().get(created);
        final String updated = (String) this.mergedIndex()
            .byChartAndVersion(chart, version)
            .get().get(created);
        MatcherAssert.assertThat(
            old.equals(updated),
            new IsEqual<>(false)
        );
    }

    @Test
    void rightEntryWasSelectedWhenVersionExistsInBoth() {
        final String chart = "tomcat";
        final String version = "0.1.0";
        final String descr = "description";
        final IndexYamlMapping target = this.index("merge/output/index.yaml");
        MatcherAssert.assertThat(
            this.mergedIndex()
                .byChartAndVersion(chart, version)
                .get().get(descr),
            new IsEqual<>(
                target.byChartAndVersion(chart, version)
                    .get().get(descr)
            )
        );
    }

    private IndexYamlMapping index(final String path) {
        return new IndexYamlMapping(
            new String(
                new TestResource(path).asBytes()
            )
        );
    }

    private IndexYamlMapping mergedIndex() {
        final IndexYamlMapping remote = this.index("merge/remote/index.yaml");
        return new IndexYamlMapping(
            new PublisherAs(
                new IndexMergingSlice.IndexMerging(this.source.toContent().get())
                    .mergeWith(remote.toContent().get())
                    .toCompletableFuture().join()
            ).asciiString()
            .toCompletableFuture().join()
        );
    }
}
