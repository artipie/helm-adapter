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
package com.artipie.helm;

import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.ValueNotFoundException;
import com.artipie.asto.ext.PublisherAs;
import com.artipie.asto.fs.FileStorage;
import com.artipie.asto.test.TestResource;
import com.artipie.helm.metadata.IndexYaml;
import com.artipie.helm.metadata.IndexYamlMapping;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.hamcrest.core.IsEqual;
import org.hamcrest.core.IsInstanceOf;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Test for {@link ChartsWriter}.
 * @since 0.3
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
final class ChartsWriterTest {
    /**
     * Temporary directory for all tests.
     * @checkstyle VisibilityModifierCheck (3 lines)
     */
    @TempDir
    Path dir;

    /**
     * Path to source index file.
     */
    private Path source;

    /**
     * Path for index file where it will rewritten.
     */
    private Path out;

    /**
     * Storage.
     */
    private Storage storage;

    @BeforeEach
    void setUp() throws IOException {
        final String prfx = "index-";
        this.source = new File(
            Paths.get(this.dir.toString(), IndexYaml.INDEX_YAML.string()).toString()
        ).toPath();
        this.out = Files.createTempFile(this.dir, prfx, "-out.yaml");
        this.storage = new FileStorage(this.dir);
    }

    @Test
    void writesToIndexAboutNewChart() {
        final String tomcat = "tomcat-0.4.1.tgz";
        new TestResource("index/index-one-ark.yaml")
            .saveTo(this.storage, IndexYaml.INDEX_YAML);
        final Map<String, Set<Pair<String, ChartYaml>>> pckgs = new HashMap<>();
        final Set<Pair<String, ChartYaml>> entries = new HashSet<>();
        entries.add(
            new ImmutablePair<>(
                "0.4.1", new TgzArchive(new TestResource(tomcat).asBytes()).chartYaml()
            )
        );
        pckgs.put("tomcat", entries);
        new ChartsWriter(this.storage)
            .addChartsToIndex(this.source, this.out, pckgs)
            .toCompletableFuture().join();
        final IndexYamlMapping index = this.indexFromStrg();
        MatcherAssert.assertThat(
            "Written charts are wrong",
            index.entries().keySet(),
            Matchers.containsInAnyOrder("tomcat", "ark")
        );
        MatcherAssert.assertThat(
            "Tomcat is absent",
            index.byChartAndVersion("tomcat", "0.4.1").isPresent(),
            new IsEqual<>(true)
        );
        MatcherAssert.assertThat(
            "Ark is absent",
            index.byChartAndVersion("ark", "1.0.1").isPresent(),
            new IsEqual<>(true)
        );
    }

    @Test
    void deletesOneOfManyVersionOfChart() {
        final String chart = "ark-1.0.1.tgz";
        new TestResource("index.yaml")
            .saveTo(this.storage, new Key.From(this.source.getFileName().toString()));
        new TestResource(chart).saveTo(this.storage);
        this.delete(chart);
        final IndexYamlMapping index = this.indexFromStrg();
        MatcherAssert.assertThat(
            "Removed version exists",
            index.byChartAndVersion("ark", "1.0.1").isPresent(),
            new IsEqual<>(false)
        );
        MatcherAssert.assertThat(
            "Extra version of chart was deleted",
            index.byChartAndVersion("ark", "1.2.0").isPresent(),
            new IsEqual<>(true)
        );
        MatcherAssert.assertThat(
            "Extra chart was deleted",
            index.byChartAndVersion("tomcat", "0.4.1").isPresent(),
            new IsEqual<>(true)
        );
    }

    @Test
    void deletesAllVersionOfChart() {
        final String arkone = "ark-1.0.1.tgz";
        final String arktwo = "ark-1.2.0.tgz";
        new TestResource("index.yaml")
            .saveTo(this.storage, new Key.From(this.source.getFileName().toString()));
        new TestResource(arkone).saveTo(this.storage);
        new TestResource(arktwo).saveTo(this.storage);
        this.delete(arkone, arktwo);
        final IndexYamlMapping index = this.indexFromStrg();
        MatcherAssert.assertThat(
            "Removed versions exist",
            index.byChart("ark").isEmpty(),
            new IsEqual<>(true)
        );
        MatcherAssert.assertThat(
            "Extra chart was deleted",
            index.byChartAndVersion("tomcat", "0.4.1").isPresent(),
            new IsEqual<>(true)
        );
    }

    @Test
    void failsToDeleteAbsentChartIfTgzIsAbsent() {
        new TestResource("index.yaml")
            .saveTo(this.storage, new Key.From(this.source.getFileName().toString()));
        final Throwable thr = Assertions.assertThrows(
            CompletionException.class,
            () -> this.delete("notExist")
        );
        MatcherAssert.assertThat(
            thr.getCause(),
            new IsInstanceOf(ValueNotFoundException.class)
        );
    }

    @Test
    void failsToDeleteAbsentInIndexChart() {
        final String chart = "tomcat-0.4.1.tgz";
        new TestResource("index/index-one-ark.yaml")
            .saveTo(this.storage, new Key.From(this.source.getFileName().toString()));
        new TestResource(chart).saveTo(this.storage);
        final Throwable thr = Assertions.assertThrows(
            CompletionException.class,
            () -> this.delete(chart)
        );
        MatcherAssert.assertThat(
            thr.getCause(),
            new IsInstanceOf(IllegalStateException.class)
        );
    }

    @Test
    void deleteLastChartFromIndex() {
        final String chart = "ark-1.0.1.tgz";
        new TestResource("index/index-one-ark.yaml")
            .saveTo(this.storage, new Key.From(this.source.getFileName().toString()));
        new TestResource(chart).saveTo(this.storage);
        this.delete(chart);
        MatcherAssert.assertThat(
            this.indexFromStrg().entries().isEmpty(),
            new IsEqual<>(true)
        );
    }

    private void delete(final String... charts) {
        final Collection<Key> keys = Arrays.stream(charts)
            .map(Key.From::new)
            .collect(Collectors.toList());
        new ChartsWriter(this.storage)
            .delete(this.source, this.out, keys)
            .toCompletableFuture().join();
    }

    private IndexYamlMapping indexFromStrg() {
        return new IndexYamlMapping(
            new PublisherAs(
                this.storage.value(
                    new Key.From(this.out.getFileName().toString())
                ).join()
            ).asciiString()
            .toCompletableFuture().join()
        );
    }
}
