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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.hamcrest.FeatureMatcher;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.hamcrest.collection.IsEmptyCollection;
import org.hamcrest.core.AllOf;
import org.hamcrest.core.Every;
import org.hamcrest.core.IsEqual;
import org.hamcrest.core.IsInstanceOf;
import org.hamcrest.core.IsNot;
import org.hamcrest.core.StringContains;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link IndexMergingSlice}.
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 * @since 0.3
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
final class IndexMergingSliceTest {
    /**
     * Storage for tests.
     */
    private Storage storage;

    @BeforeEach
    void setUp() {
        this.storage = new InMemoryStorage();
    }

    @Test
    void returnsNotFoundWhenSourceMetaFileIsAbsent() {
        final byte[] body = new TestResource("merge/remote.yaml").asBytes();
        MatcherAssert.assertThat(
            "Returns NOT_FOUND status",
            new IndexMergingSlice(this.storage),
            new SliceHasResponse(
                new RsHasStatus(RsStatus.NOT_FOUND),
                new RequestLine("PUT", "/index.yaml"),
                Headers.EMPTY,
                new Content.From(body)
            )
        );
    }

    @Test
    void mergeIndexesCorrectly() {
        final byte[] body = new TestResource("merge/remote.yaml").asBytes();
        final String meta = "index.yaml";
        new TestResource("merge/source.yaml").saveTo(this.storage, new Key.From(meta));
        final IndexYamlMapping target = new IndexYamlMapping(
            new String(
                new TestResource("merge/output.yaml").asBytes()
            )
        );
        MatcherAssert.assertThat(
            "Returns OK status",
            new IndexMergingSlice(this.storage),
            new SliceHasResponse(
                new RsHasStatus(RsStatus.OK),
                new RequestLine("PUT", String.format("/%s", meta)),
                Headers.EMPTY,
                new Content.From(body)
            )
        );
        final IndexYamlMapping res = new IndexYamlMapping(
            new PublisherAs(this.storage.value(new Key.From(meta)).join())
                .asciiString()
                .toCompletableFuture().join()
        );
        MatcherAssert.assertThat(
            "Contains all entries",
            res.entries().keySet(),
            Matchers.containsInAnyOrder(target.entries().keySet().toArray())
        );
        MatcherAssert.assertThat(
            "Contains all versions for tomcat",
            res.byChart("tomcat").size(),
            new IsEqual<>(target.byChart("tomcat").size())
        );
    }

    @Test
    void returnsOkAndRemoveFileFromTempLocation() {
        final byte[] body = new TestResource("merge/remote.yaml").asBytes();
        final String meta = "index.yaml";
        new TestResource("merge/source.yaml").saveTo(this.storage, new Key.From(meta));
        MatcherAssert.assertThat(
            "Returns OK status",
            new IndexMergingSlice(this.storage),
            new SliceHasResponse(
                new RsHasStatus(RsStatus.OK),
                new RequestLine("PUT", String.format("/%s", meta)),
                Headers.EMPTY,
                new Content.From(body)
            )
        );
        MatcherAssert.assertThat(
            "Removes files from temp location",
            this.storage.list(IndexMergingSlice.TEMP).join(),
            new IsEmptyCollection<>()
        );
    }

    // @checkstyle MagicNumberCheck (70 lines)
    // @checkstyle IllegalCatchCheck (70 lines)
    // @checkstyle ExecutableStatementCountCheck (70 lines)
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    @RepeatedTest(10)
    void throwsExceptionWhenMergingDoneSimultaneously() {
        final int count = 3;
        final CountDownLatch latch = new CountDownLatch(count);
        final List<CompletableFuture<Void>> tasks = new ArrayList<>(count);
        for (int number = 0; number < count; number += 1) {
            final CompletableFuture<Void> future = new CompletableFuture<>();
            tasks.add(future);
            new Thread(
                () -> {
                    try {
                        latch.countDown();
                        latch.await();
                        new IndexMergingSlice(this.storage)
                            .response(
                                new RequestLine(RqMethod.PUT, "/index.yaml").toString(),
                                Headers.EMPTY,
                                new Content.From(new TestResource("merge/remote.yaml").asBytes())
                            ).send(
                                (status, headers, body) -> CompletableFuture.allOf()
                            ).toCompletableFuture().join();
                        future.complete(null);
                    } catch (final Exception exception) {
                        future.completeExceptionally(exception);
                    }
                }
            ).start();
        }
        final List<Throwable> failures = tasks.stream().flatMap(
            task -> {
                Stream<Throwable> result;
                try {
                    task.join();
                    result = Stream.empty();
                } catch (final RuntimeException ex) {
                    result = Stream.of(ex.getCause());
                }
                return result;
            }
        ).collect(Collectors.toList());
        MatcherAssert.assertThat(
            "Some updates failed",
            failures,
            new IsNot<>(new IsEmptyCollection<>())
        );
        MatcherAssert.assertThat(
            "All failure due to concurrent lock access",
            failures,
            new Every<>(
                new AllOf<>(
                    Arrays.asList(
                        new IsInstanceOf(IllegalStateException.class),
                        new FeatureMatcher<Throwable, String>(
                            new StringContains("Failed to acquire lock."),
                            "an exception with message",
                            "message"
                        ) {
                            @Override
                            protected String featureValueOf(final Throwable obj) {
                                return obj.getMessage();
                            }
                        }
                    )
                )
            )
        );
        MatcherAssert.assertThat(
            "Storage has no locks",
            this.storage.list(Key.ROOT)
                .join().stream()
                .noneMatch(key -> key.string().contains("lock")),
            new IsEqual<>(true)
        );
    }
}
