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
import com.artipie.asto.memory.InMemoryStorage;
import com.artipie.asto.test.TestResource;
import com.artipie.helm.metadata.IndexYaml;
import com.artipie.helm.metadata.IndexYamlMapping;
import com.artipie.helm.test.ContentOfIndex;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.cactoos.set.SetOf;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Test for {@link Helm.Asto#reindex(Key)}.
 * @since 0.3
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 * @todo #113:30min Fix reindex operation.
 *  For some cases (about 1-2 of 1000) these tests fail with NPE when
 *  it tries to get entries of a new index. It looks like index does not have
 *  time to copy from temporary written index file to the source one.
 *  It is necessary to address this problem and enable tests.
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
final class HelmAstoReindexTest {
    /**
     * Storage.
     */
    private Storage storage;

    @BeforeEach
    void setUp() {
        this.storage = new InMemoryStorage();
    }

    @Disabled
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void reindexFromRootDirectory(final boolean withindex) throws IOException {
        Stream.of(
            "tomcat-0.4.1.tgz", "ark-1.0.1.tgz", "ark-1.2.0.tgz"
        ).forEach(tgz -> new TestResource(tgz).saveTo(this.storage));
        if (withindex) {
            new TestResource("index/index-one-ark.yaml").saveTo(this.storage, IndexYaml.INDEX_YAML);
        }
        new Helm.Asto(this.storage).reindex(Key.ROOT).toCompletableFuture().join();
        MatcherAssert.assertThat(
            "Index file is absent",
            this.storage.exists(IndexYaml.INDEX_YAML).join(),
            new IsEqual<>(true)
        );
        final IndexYamlMapping index = new ContentOfIndex(this.storage).index();
        MatcherAssert.assertThat(
            "Written charts are wrong",
            index.entries().keySet(),
            Matchers.containsInAnyOrder("tomcat", "ark")
        );
        HelmAstoReindexTest.assertTmpDirWasRemoved();
    }

    @Disabled
    @Test
    void reindexWithSomePrefix() throws IOException {
        final Key prfx = new Key.From("prefix");
        Stream.of("ark-1.0.1.tgz", "ark-1.2.0.tgz")
            .forEach(tgz -> new TestResource(tgz).saveTo(this.storage, new Key.From(prfx, tgz)));
        new Helm.Asto(this.storage).reindex(prfx).toCompletableFuture().join();
        final Key keyidx = new Key.From(prfx, IndexYaml.INDEX_YAML);
        MatcherAssert.assertThat(
            "Index file is absent",
            this.storage.exists(keyidx).join(),
            new IsEqual<>(true)
        );
        final IndexYamlMapping index = new ContentOfIndex(this.storage).index(keyidx);
        MatcherAssert.assertThat(
            "Written charts are wrong",
            index.entries().keySet(),
            new IsEqual<>(new SetOf<String>("ark"))
        );
        HelmAstoReindexTest.assertTmpDirWasRemoved();
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
