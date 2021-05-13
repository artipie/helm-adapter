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
import com.artipie.helm.ChartYaml;
import com.artipie.helm.metadata.IndexYamlMapping;
import com.artipie.http.Headers;
import com.artipie.http.hm.RsHasStatus;
import com.artipie.http.hm.SliceHasResponse;
import com.artipie.http.rq.RequestLine;
import com.artipie.http.rq.RqMethod;
import com.artipie.http.rs.RsStatus;
import com.google.common.base.Throwables;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.IsEqual;
import org.hamcrest.core.IsInstanceOf;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Test case for {@link DownloadIndexSlice}.
 * @since 0.3
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
final class DownloadIndexSliceTest {
    /**
     * Storage.
     */
    private Storage storage;

    @BeforeEach
    void init() {
        this.storage = new InMemoryStorage();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://central.artipie.com/", "http://central.artipie.com"})
    void returnsOkAndUpdateEntriesUrlsForBaseWithOrWithoutTrailingSlash(final String base) {
        final AtomicReference<String> cbody = new AtomicReference<>();
        final AtomicReference<RsStatus> cstatus = new AtomicReference<>();
        new TestResource("index.yaml").saveTo(this.storage);
        new DownloadIndexSlice(base, this.storage)
            .response(
                new RequestLine(RqMethod.GET, "/index.yaml").toString(),
                Headers.EMPTY,
                Content.EMPTY
            ).send(
                (status, headers, body) -> {
                    cbody.set(new PublisherAs(body).asciiString().toCompletableFuture().join());
                    cstatus.set(status);
                    return CompletableFuture.allOf();
                }
            ).toCompletableFuture().join();
        MatcherAssert.assertThat(
            "Returned OK",
            cstatus.get(),
            new IsEqual<>(RsStatus.OK)
        );
        MatcherAssert.assertThat(
            "Uri was corrected modified",
            new ChartYaml(
                new IndexYamlMapping(cbody.get())
                    .byChart("tomcat").get(0)
            ).urls().get(0),
            new IsEqual<>(String.format("%s/tomcat-0.4.1.tgz", base.replaceAll("/$", "")))
        );
    }

    @Test
    void returnsBadRequest() {
        MatcherAssert.assertThat(
            new DownloadIndexSlice("http://localhost:8080", this.storage),
            new SliceHasResponse(
                new RsHasStatus(RsStatus.BAD_REQUEST),
                new RequestLine(RqMethod.GET, "/bad/request")
            )
        );
    }

    @Test
    void returnsNotFound() {
        MatcherAssert.assertThat(
            new DownloadIndexSlice("http://localhost:8080", this.storage),
            new SliceHasResponse(
                new RsHasStatus(RsStatus.NOT_FOUND),
                new RequestLine(RqMethod.GET, "/index.yaml")
            )
        );
    }

    @Test
    void throwsMalformedUrlExceptionForInvalidBase() {
        final String base = "withoutschemelocalhost:8080";
        final Throwable thr = Assertions.assertThrows(
            IllegalStateException.class,
            () -> new DownloadIndexSlice(base, this.storage)
        );
        MatcherAssert.assertThat(
            Throwables.getRootCause(thr),
            new IsInstanceOf(MalformedURLException.class)
        );
    }

    @Test
    void throwsExceptionForInvalidUriFromIndexYaml() {
        final String base = "http://localhost:8080";
        final AtomicReference<Throwable> exc = new AtomicReference<>();
        new TestResource("index/invalid_uri.yaml")
            .saveTo(this.storage, new Key.From("index.yaml"));
        new DownloadIndexSlice(base, this.storage)
            .response(
                new RequestLine(RqMethod.GET, "/index.yaml").toString(),
                Headers.EMPTY,
                Content.EMPTY
            ).send((status, headers, body) -> CompletableFuture.completedFuture(null))
            .handle(
                (res, thr) -> {
                    exc.set(thr);
                    return CompletableFuture.allOf();
                }
            ).toCompletableFuture().join();
        MatcherAssert.assertThat(
            Throwables.getRootCause(exc.get()),
            new IsInstanceOf(URISyntaxException.class)
        );
    }
}
