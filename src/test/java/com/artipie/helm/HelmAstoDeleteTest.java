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

import com.artipie.asto.Content;
import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.ValueNotFoundException;
import com.artipie.asto.ext.PublisherAs;
import com.artipie.asto.memory.InMemoryStorage;
import com.artipie.asto.test.TestResource;
import com.artipie.helm.metadata.IndexYaml;
import com.artipie.helm.metadata.IndexYamlMapping;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.IsEqual;
import org.hamcrest.core.IsInstanceOf;
import org.hamcrest.core.StringContains;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Test for {@link Helm.Asto#delete(Collection, Key)}.
 * @since 0.3
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
final class HelmAstoDeleteTest {
    /**
     * Storage.
     */
    private Storage storage;

    @BeforeEach
    void setUp() {
        this.storage = new InMemoryStorage();
    }

    @ParameterizedTest
    @ValueSource(strings = {"tomcat-0.4.1.tgz", "ark-1.2.0.tgz"})
    void throwsExceptionWhenChartOrVersionIsAbsentInIndex(final String chart) {
        this.saveSourceIndex("index/index-one-ark.yaml");
        new TestResource(chart).saveTo(this.storage);
        final Throwable thr = Assertions.assertThrows(
            CompletionException.class,
            () -> this.delete(Key.ROOT, chart)
        );
        MatcherAssert.assertThat(
            thr.getCause().getMessage(),
            new StringContains("Failed to delete package")
        );
    }

    @Test
    void throwsExceptionWhenChartPassedButIndexNotExist() {
        final String chart = "tomcat-0.4.1.tgz";
        new TestResource(chart).saveTo(this.storage);
        final Throwable thr = Assertions.assertThrows(
            CompletionException.class,
            () -> this.delete(Key.ROOT, chart)
        );
        MatcherAssert.assertThat(
            thr.getCause().getMessage(),
            new StringContains("Failed to delete package")
        );
    }

    @Test
    void throwsExceptionWhenKeyNotExist() {
        final String chart = "not-exist.tgz";
        this.storage.save(IndexYaml.INDEX_YAML, Content.EMPTY);
        final Throwable thr = Assertions.assertThrows(
            CompletionException.class,
            () -> this.delete(Key.ROOT, chart)
        );
        MatcherAssert.assertThat(
            thr.getCause(),
            new IsInstanceOf(ValueNotFoundException.class)
        );
    }

    @Test
    void deletesChartFromIndexAndItsArchive() {
        final String tomcat = "tomcat-0.4.1.tgz";
        final String arkone = "ark-1.0.1.tgz";
        final String arktwo = "ark-1.2.0.tgz";
        Stream.of(tomcat, arkone, arktwo)
            .forEach(chart -> new TestResource(chart).saveTo(this.storage));
        this.saveSourceIndex("index.yaml");
        this.delete(Key.ROOT, arkone);
        MatcherAssert.assertThat(
            "Removed chart is not removed",
            this.indexFromStrg(IndexYaml.INDEX_YAML)
                .byChartAndVersion("ark", "1.0.1")
            .isPresent(),
            new IsEqual<>(false)
        );
        MatcherAssert.assertThat(
            "Archive of removed chart remained",
            this.storage.exists(new Key.From(arkone)).join(),
            new IsEqual<>(false)
        );
    }

    @Test
    void deletesChartFromNestedFolder() {
        final String ark = "ark-1.0.1.tgz";
        final Key full = new Key.From("nested", "ark-1.0.1.tgz");
        this.storage.save(
            IndexYaml.INDEX_YAML,
            new Content.From(
                new TestResource("index.yaml").asBytes()
            )
        ).join();
        new TestResource(ark).saveTo(this.storage, full);
        this.delete(Key.ROOT, full.string());
        MatcherAssert.assertThat(
            "Removed chart is not removed",
            this.indexFromStrg(IndexYaml.INDEX_YAML)
                .byChartAndVersion("ark", "1.0.1")
                .isPresent(),
            new IsEqual<>(false)
        );
        MatcherAssert.assertThat(
            "Archive of removed chart remained",
            this.storage.exists(full).join(),
            new IsEqual<>(false)
        );
    }

    @Test
    void deletesFromIndexFileWithPrefix() {
        final Key prefix = new Key.From("prefix");
        final Key keyidx = new Key.From(prefix, IndexYaml.INDEX_YAML);
        final String ark = "ark-1.0.1.tgz";
        final Key full = new Key.From(prefix, "ark-1.0.1.tgz");
        this.storage.save(
            keyidx,
            new Content.From(
                new TestResource("index.yaml").asBytes()
            )
        ).join();
        new TestResource(ark).saveTo(this.storage, new Key.From(prefix, ark));
        this.delete(prefix, full.string());
        MatcherAssert.assertThat(
            "Removed chart is not removed",
            this.indexFromStrg(keyidx)
                .byChartAndVersion("ark", "1.0.1")
                .isPresent(),
            new IsEqual<>(false)
        );
        MatcherAssert.assertThat(
            "Archive of removed chart remained",
            this.storage.exists(full).join(),
            new IsEqual<>(false)
        );
    }

    private void delete(final Key prefix, final String... charts) {
        final Collection<Key> keys = Arrays.stream(charts)
            .map(Key.From::new)
            .collect(Collectors.toList());
        new Helm.Asto(this.storage).delete(keys, prefix).toCompletableFuture().join();
    }

    private IndexYamlMapping indexFromStrg(final Key path) {
        return new IndexYamlMapping(
            new PublisherAs(
                this.storage.value(path).join()
            ).asciiString().toCompletableFuture().join()
        );
    }

    private void saveSourceIndex(final String path) {
        this.storage.save(
            IndexYaml.INDEX_YAML,
            new Content.From(
                new TestResource(path).asBytes()
            )
        ).join();
    }
}
