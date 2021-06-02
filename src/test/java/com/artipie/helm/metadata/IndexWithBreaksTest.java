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
package com.artipie.helm.metadata;

import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.blocking.BlockingStorage;
import com.artipie.asto.memory.InMemoryStorage;
import com.artipie.asto.test.TestResource;
import java.util.Map;
import java.util.Set;
import org.cactoos.set.SetOf;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Test for {@link Index.WithBreaks}.
 * @since 0.3
 */
final class IndexWithBreaksTest {
    @ParameterizedTest
    @CsvSource({
        "index.yaml,''",
        "index/index-four-spaces.yaml,''",
        "index.yaml,prefix"
    })
    void returnsVersionsForPackages(final String index, final String prefix) {
        final String tomcat = "tomcat";
        final String ark = "ark";
        final Key keyidx = new Key.From(new Key.From(prefix), IndexYaml.INDEX_YAML);
        final Storage storage = new InMemoryStorage();
        new BlockingStorage(storage).save(keyidx, new TestResource(index).asBytes());
        final Map<String, Set<String>> vrsns = new Index.WithBreaks(storage)
            .versionsByPackages(keyidx)
            .toCompletableFuture().join();
        MatcherAssert.assertThat(
            "Does not contain required packages",
            vrsns.keySet(),
            Matchers.containsInAnyOrder(ark, tomcat)
        );
        MatcherAssert.assertThat(
            "Parsed versions for `tomcat` are incorrect",
            vrsns.get(tomcat),
            new IsEqual<>(new SetOf<>("0.4.1"))
        );
        MatcherAssert.assertThat(
            "Parsed versions for `ark` are incorrect",
            vrsns.get(ark),
            Matchers.containsInAnyOrder("1.0.1", "1.2.0")
        );
    }
}
