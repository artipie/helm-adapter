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
import com.artipie.asto.fs.FileStorage;
import com.artipie.asto.test.TestResource;
import com.artipie.helm.metadata.IndexYaml;
import com.artipie.helm.metadata.IndexYamlMapping;
import com.artipie.helm.test.ContentOfIndex;
import com.jcabi.log.Logger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.hamcrest.core.IsEqual;
import org.hamcrest.core.IsInstanceOf;
import org.hamcrest.core.StringContains;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test for {@link AddWriter.Asto}.
 * @since 0.3
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
final class AddWriterAstoTest {
    /**
     * Temporary directory for all tests.
     */
    private Path dir;

    /**
     * Key to source index file.
     */
    private Key source;

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
        this.dir = Files.createTempDirectory("");
        final String prfx = "index-";
        this.source = new Key.From(IndexYaml.INDEX_YAML.string());
        this.out = Files.createTempFile(this.dir, prfx, "-out.yaml");
        this.storage = new FileStorage(this.dir);
    }

    @AfterEach
    void tearDown() {
        try {
            FileUtils.cleanDirectory(this.dir.toFile());
            Files.deleteIfExists(this.dir);
        } catch (final IOException ex) {
            Logger.error(this, "Failed to clean directory %[exception]s", ex);
        }
    }

    @Test
    void writesToIndexAboutNewChart() {
        final String tomcat = "tomcat-0.4.1.tgz";
        new TestResource("index/index-one-ark.yaml")
            .saveTo(this.storage, IndexYaml.INDEX_YAML);
        final Map<String, Set<Pair<String, ChartYaml>>> pckgs = packagesWithTomcat(tomcat);
        new AddWriter.Asto(this.storage)
            .add(this.source, this.out, pckgs)
            .toCompletableFuture().join();
        final IndexYamlMapping index = new ContentOfIndex(this.storage).index(this.pathToIndex());
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
    void failsToWriteInfoAboutExistedVersion() {
        final String tomcat = "tomcat-0.4.1.tgz";
        new TestResource("index.yaml")
            .saveTo(this.storage, IndexYaml.INDEX_YAML);
        final Map<String, Set<Pair<String, ChartYaml>>> pckgs = packagesWithTomcat(tomcat);
        final CompletionException exc = Assertions.assertThrows(
            CompletionException.class,
            () -> new AddWriter.Asto(this.storage)
                .add(this.source, this.out, pckgs)
                .toCompletableFuture().join()
        );
        MatcherAssert.assertThat(
            "Wrong message of handmade exception",
            exc.getMessage(),
            new StringContains("Failed to write to index `tomcat` with version `0.4.1`")
        );
    }

    @Test
    void addChartsTrustfully() {
        final SortedSet<Key> charts = new TreeSet<>(Key.CMP_STRING);
        Stream.of(
            "tomcat-0.4.1.tgz", "ark-1.0.1.tgz", "ark-1.2.0.tgz"
        ).map(Key.From::new)
        .forEach(charts::add);
        charts.forEach(chart -> new TestResource(chart.string()).saveTo(this.storage));
        new AddWriter.Asto(this.storage)
            .addTrustfully(this.out, charts)
            .toCompletableFuture().join();
        final IndexYamlMapping index = new ContentOfIndex(this.storage).index(this.pathToIndex());
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
            "Ark 1.0.1 is absent",
            index.byChartAndVersion("ark", "1.0.1").isPresent(),
            new IsEqual<>(true)
        );
        MatcherAssert.assertThat(
            "Ark 1.2.0 is absent",
            index.byChartAndVersion("ark", "1.2.0").isPresent(),
            new IsEqual<>(true)
        );
    }

    @Test
    void failsToAddTrustfullyWhenPackageIsAbsent() {
        final SortedSet<Key> charts = new TreeSet<>(Key.CMP_STRING);
        charts.add(new Key.From("absent-archive.tgz"));
        final Throwable thr = Assertions.assertThrows(
            CompletionException.class,
            () -> new AddWriter.Asto(this.storage)
                .addTrustfully(this.out, charts)
                .toCompletableFuture().join()
        );
        MatcherAssert.assertThat(
            thr.getCause(),
            new IsInstanceOf(ValueNotFoundException.class)
        );
    }

    private Key pathToIndex() {
        return new Key.From(this.out.getFileName().toString());
    }

    private static Map<String, Set<Pair<String, ChartYaml>>> packagesWithTomcat(final String path) {
        final Map<String, Set<Pair<String, ChartYaml>>> pckgs = new HashMap<>();
        final Set<Pair<String, ChartYaml>> entries = new HashSet<>();
        entries.add(
            new ImmutablePair<>(
                "0.4.1", new TgzArchive(new TestResource(path).asBytes()).chartYaml()
            )
        );
        pckgs.put("tomcat", entries);
        return pckgs;
    }
}
