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
import com.artipie.asto.ext.PublisherAs;
import com.artipie.asto.fs.FileStorage;
import com.artipie.asto.test.TestResource;
import com.artipie.helm.metadata.IndexYamlMapping;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Test;

/**
 * Test for {@link ChartsWriter}.
 * @since 0.3
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
final class ChartsWriterTest {

    @Test
    void writesToIndexAboutNewChart() throws IOException {
        final String tomcat = "tomcat-0.4.1.tgz";
        final String prfx = "index-";
        final Path dir = Files.createTempDirectory(prfx);
        final Path source = Files.createTempFile(dir, prfx, ".yaml");
        final Path out = Files.createTempFile(dir, prfx, "-out.yaml");
        final Storage strg = new FileStorage(dir);
        new TestResource("index/index-one-ark.yaml")
            .saveTo(strg, new Key.From(source.getFileName().toString()));
        final Map<String, Set<Pair<String, ChartYaml>>> pckgs = new HashMap<>();
        final Set<Pair<String, ChartYaml>> entries = new HashSet<>();
        entries.add(
            new ImmutablePair<>(
                "0.4.1", new TgzArchive(new TestResource(tomcat).asBytes()).chartYaml()
            )
        );
        pckgs.put("tomcat", entries);
        new ChartsWriter(strg)
            .addChartsToIndex(source, out, pckgs)
            .toCompletableFuture().join();
        final IndexYamlMapping index = new IndexYamlMapping(
            new PublisherAs(strg.value(new Key.From(out.getFileName().toString())).join())
                .asciiString()
                .toCompletableFuture().join()
        );
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
}
