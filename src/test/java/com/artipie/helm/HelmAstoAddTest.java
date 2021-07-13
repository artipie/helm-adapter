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
import com.artipie.asto.memory.InMemoryStorage;
import com.artipie.asto.test.TestResource;
import com.artipie.helm.metadata.IndexYaml;
import com.artipie.helm.metadata.IndexYamlMapping;
import com.artipie.helm.test.ContentOfIndex;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;
import org.cactoos.list.ListOf;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.hamcrest.core.IsEqual;
import org.hamcrest.core.StringContains;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Test for {@link Helm.Asto#add(Collection, Key)}.
 * @since 0.3
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
@SuppressWarnings({"PMD.AvoidDuplicateLiterals", "PMD.TooManyMethods"})
final class HelmAstoAddTest {
    /**
     * Storage.
     */
    private Storage storage;

    @BeforeEach
    void setUp() {
        this.storage = new InMemoryStorage();
    }

    @ParameterizedTest
    @ValueSource(strings = {"index-one-ark.yaml", "index-one-ark-four-spaces.yaml"})
    void addInfoAboutNewVersionOfPackageAndNewPackage(final String yaml) throws IOException {
        final String tomcat = "tomcat-0.4.1.tgz";
        final String ark = "ark-1.2.0.tgz";
        new TestResource(tomcat).saveTo(this.storage);
        new TestResource(ark).saveTo(this.storage);
        this.saveSourceIndex(yaml);
        this.addFilesToIndex(Key.ROOT, tomcat, ark);
        final IndexYamlMapping index = new ContentOfIndex(this.storage).index();
        MatcherAssert.assertThat(
            "Some packages were missed",
            index.entries().keySet(),
            Matchers.containsInAnyOrder("tomcat", "ark")
        );
        MatcherAssert.assertThat(
            "Contains not one version for chart `tomcat`",
            index.byChart("tomcat").size(),
            new IsEqual<>(1)
        );
        MatcherAssert.assertThat(
            "Versions of chart `ark` are incorrect",
            index.byChart("ark").stream().map(
                entry -> entry.get("version")
            ).collect(Collectors.toList()),
            Matchers.containsInAnyOrder("1.0.1", "1.2.0")
        );
        HelmAstoAddTest.assertTmpDirWasRemoved();
    }

    @ParameterizedTest
    @ValueSource(strings = {"index-one-ark.yaml", "index-one-ark-four-spaces.yaml"})
    void addInfoAboutNewPackageAndContainsAllFields(final String yaml) throws IOException {
        final String tomcat = "tomcat-0.4.1.tgz";
        new TestResource(tomcat).saveTo(this.storage);
        this.saveSourceIndex(yaml);
        this.addFilesToIndex(Key.ROOT, tomcat);
        final IndexYamlMapping index = new ContentOfIndex(this.storage).index();
        MatcherAssert.assertThat(
            "Contains not one version for chart `tomcat`",
            index.byChart("tomcat").size(),
            new IsEqual<>(1)
        );
        MatcherAssert.assertThat(
            index.byChart("tomcat").get(0).keySet(),
            Matchers.containsInAnyOrder(
                "maintainers", "appVersion", "urls", "apiVersion", "created",
                "icon", "name", "digest", "description", "version", "home"
            )
        );
        HelmAstoAddTest.assertTmpDirWasRemoved();
    }

    @ParameterizedTest
    @ValueSource(strings = {"index-one-ark.yaml", "index-one-ark-four-spaces.yaml"})
    void addInfoAboutNewVersion(final String yaml) throws IOException {
        final String ark = "ark-1.2.0.tgz";
        new TestResource(ark).saveTo(this.storage);
        this.saveSourceIndex(yaml);
        this.addFilesToIndex(Key.ROOT, ark);
        final IndexYamlMapping index = new ContentOfIndex(this.storage).index();
        MatcherAssert.assertThat(
            "Existed version is absent",
            index.byChartAndVersion("ark", "1.0.1").isPresent(),
            new IsEqual<>(true)
        );
        MatcherAssert.assertThat(
            "New version was not added",
            index.byChartAndVersion("ark", "1.2.0").isPresent(),
            new IsEqual<>(true)
        );
        HelmAstoAddTest.assertTmpDirWasRemoved();
    }

    @Test
    void addInfoAboutPackageWhenSourceIndexIsAbsent() throws IOException {
        final String ark = "ark-1.0.1.tgz";
        new TestResource(ark).saveTo(this.storage);
        this.addFilesToIndex(Key.ROOT, ark);
        MatcherAssert.assertThat(
            new ContentOfIndex(this.storage).index()
                .byChartAndVersion("ark", "1.0.1")
                .isPresent(),
            new IsEqual<>(true)
        );
        HelmAstoAddTest.assertTmpDirWasRemoved();
    }

    @Disabled
    @Test
    void failsToAddInfoAboutExistedVersion() throws IOException {
        final String ark = "ark-1.0.1.tgz";
        new TestResource(ark).saveTo(this.storage);
        this.saveSourceIndex("index-one-ark.yaml");
        final CompletionException exc = Assertions.assertThrows(
            CompletionException.class,
            () -> this.addFilesToIndex(Key.ROOT, ark)
        );
        MatcherAssert.assertThat(
            exc.getMessage(),
            new StringContains("Failed to write to index `ark` with version `1.0.1`")
        );
        HelmAstoAddTest.assertTmpDirWasRemoved();
    }

    @Test
    void addToIndexForNestedFolder() throws IOException {
        final Key prefix = new Key.From("nested");
        final String tomcat = "tomcat-0.4.1.tgz";
        final Key fulltomcat = new Key.From(prefix, tomcat);
        final String extra = "ark-1.2.0.tgz";
        new TestResource(tomcat).saveTo(this.storage, fulltomcat);
        new TestResource(extra).saveTo(this.storage);
        final Key keyidx = new Key.From(prefix, IndexYaml.INDEX_YAML);
        new TestResource("index/index-one-ark.yaml").saveTo(this.storage, keyidx);
        this.addFilesToIndex(prefix, fulltomcat.string());
        final IndexYamlMapping index = new ContentOfIndex(this.storage).index(keyidx);
        MatcherAssert.assertThat(
            "Some packages were missed",
            index.entries().keySet(),
            Matchers.containsInAnyOrder("tomcat", "ark")
        );
        MatcherAssert.assertThat(
            "Added chart is not added",
            index.byChartAndVersion("tomcat", "0.4.1").isPresent(),
            new IsEqual<>(true)
        );
        MatcherAssert.assertThat(
            "Version of chart `ark` is incorrect",
            index.byChart("ark").stream().map(
                entry -> entry.get("version")
            ).collect(Collectors.toList()),
            new IsEqual<>(new ListOf<>("1.0.1"))
        );
        HelmAstoAddTest.assertTmpDirWasRemoved();
    }

    @Test
    void failsToAddWithIncorrectPrefix() throws IOException {
        final Key prefix = new Key.From("prefix");
        final Key toadd = new Key.From("wrong", "tomcat-0.4.1.tgz");
        new TestResource("index/index-one-ark.yaml")
            .saveTo(this.storage, new Key.From(prefix, IndexYaml.INDEX_YAML));
        final Throwable thr = Assertions.assertThrows(
            CompletionException.class,
            () -> this.addFilesToIndex(prefix, toadd.string())
        );
        MatcherAssert.assertThat(
            thr.getCause().getMessage(),
            new StringContains("does not start with prefix")
        );
        HelmAstoAddTest.assertTmpDirWasRemoved();
    }

    private void addFilesToIndex(final Key indexpath, final String... files) {
        final Collection<Key> keys = Arrays.stream(files)
            .map(Key.From::new)
            .collect(Collectors.toList());
        new Helm.Asto(this.storage)
            .add(keys, indexpath)
            .toCompletableFuture().join();
    }

    private void saveSourceIndex(final String name) {
        this.storage.save(
            IndexYaml.INDEX_YAML,
            new Content.From(
                new TestResource(String.format("index/%s", name)).asBytes()
            )
        ).join();
    }

    private static void assertTmpDirWasRemoved() throws IOException {
        final Path systemtemp = Paths.get(System.getProperty("java.io.tmpdir"));
        MatcherAssert.assertThat(
            "Temp dir for indexes was not removed",
            Files.list(systemtemp)
                .noneMatch(path -> path.getFileName().toString().startsWith("index-")),
            new IsEqual<>(true)
        );
    }
}
