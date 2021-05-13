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

import com.artipie.asto.Content;
import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.ext.PublisherAs;
import com.artipie.asto.memory.InMemoryStorage;
import com.artipie.asto.test.TestResource;
import com.artipie.helm.metadata.IndexYamlMapping;
import com.artipie.http.Headers;
import com.artipie.http.hm.RsHasStatus;
import com.artipie.http.hm.SliceHasResponse;
import com.artipie.http.rq.RequestLine;
import com.artipie.http.rq.RqMethod;
import com.artipie.http.rs.RsStatus;
import org.cactoos.list.ListOf;
import org.cactoos.set.SetOf;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link PushChartSlice}.
 * @since 0.4
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
final class PushChartSliceTest {
    /**
     * Storage for tests.
     */
    private Storage storage;

    @BeforeEach
    void setUp() {
        this.storage = new InMemoryStorage();
    }

    @Test
    void shouldNotUpdateAfterUpload() {
        final String tgz = "ark-1.0.1.tgz";
        MatcherAssert.assertThat(
            "Wrong status, expected OK",
            new PushChartSlice(this.storage),
            new SliceHasResponse(
                new RsHasStatus(RsStatus.OK),
                new RequestLine(RqMethod.GET, "/?updateIndex=false"),
                Headers.EMPTY,
                new Content.From(new TestResource(tgz).asBytes())
            )
        );
        MatcherAssert.assertThat(
            "Index was generated",
            this.storage.list(Key.ROOT).join(),
            new IsEqual<>(new ListOf<Key>(new Key.From(tgz)))
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"/?updateIndex=true", "/"})
    void shouldUpdateIndexAfterUpload(final String uri) {
        final String tgz = "ark-1.0.1.tgz";
        MatcherAssert.assertThat(
            "Wrong status, expected OK",
            new PushChartSlice(this.storage),
            new SliceHasResponse(
                new RsHasStatus(RsStatus.OK),
                new RequestLine(RqMethod.GET, uri),
                Headers.EMPTY,
                new Content.From(new TestResource(tgz).asBytes())
            )
        );
        MatcherAssert.assertThat(
            "Index was not updated",
            new IndexYamlMapping(
                new PublisherAs(this.storage.value(new Key.From("index.yaml")).join())
                    .asciiString()
                    .toCompletableFuture().join()
            ).entries().keySet(),
            new IsEqual<>(new SetOf<>("ark"))
        );
    }
}
