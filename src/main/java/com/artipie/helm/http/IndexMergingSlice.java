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
import com.artipie.asto.SubStorage;
import com.artipie.asto.ext.PublisherAs;
import com.artipie.helm.metadata.IndexYamlMapping;
import com.artipie.http.Response;
import com.artipie.http.Slice;
import com.artipie.http.async.AsyncResponse;
import com.artipie.http.rq.RequestLineFrom;
import com.artipie.http.rs.RsStatus;
import com.artipie.http.rs.RsWithStatus;
import com.artipie.http.rs.StandardRs;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.reactivestreams.Publisher;

/**
 * Merging source index file with remote one exclusively.
 * @since 0.3
 * @checkstyle RedundantModifierCheck (500 lines)
 */
public final class IndexMergingSlice implements Slice {
    /**
     * Temp storage key.
     */
    static final Key TEMP = new Key.From(".upload");

    /**
     * Metadata pattern.
     */
    private static final Pattern META = Pattern.compile("^/index.yaml$");

    /**
     * Storage that should contain source metafile.
     */
    private final Storage storage;

    /**
     * Ctor.
     * @param storage Storage that should contain source metafile
     */
    public IndexMergingSlice(final Storage storage) {
        this.storage = storage;
    }

    @Override
    public Response response(
        final String line,
        final Iterable<Map.Entry<String, String>> headers,
        final Publisher<ByteBuffer> body
    ) {
        final RequestLineFrom rqline = new RequestLineFrom(line);
        final Matcher matcher = IndexMergingSlice.META.matcher(rqline.uri().getPath());
        final Storage temp = new SubStorage(IndexMergingSlice.TEMP, this.storage);
        final Key yaml = new Key.From("index.yaml");
        return new AsyncResponse(
            temp.save(yaml, new Content.From(body))
                .thenCompose(
                    ignored -> {
                        final CompletionStage<Response> res;
                        if (matcher.matches()) {
                            res = this.storage.exclusively(
                                yaml,
                                target -> IndexMergingSlice.checkAndMerge(target, yaml, temp)
                            );
                        } else {
                            res = CompletableFuture.completedFuture(
                                new RsWithStatus(RsStatus.BAD_REQUEST)
                            );
                        }
                        return res;
                    }
                )
            );
    }

    /**
     * Check existence of meta file in storage and merge.
     * @param storage Storage with existed meta file. To this file another one will be merged
     * @param meta Key for meta file
     * @param substrg Temporary storage with meta file which information should be added
     * @return Completable response for merge operation
     */
    private static CompletionStage<Response> checkAndMerge(
        final Storage storage, final Key meta, final Storage substrg
    ) {
        return storage.exists(meta)
            .thenCompose(
                exists -> {
                    final CompletionStage<Response> res;
                    if (exists) {
                        res = storage.value(meta)
                            .thenApply(IndexMerging::new)
                            .thenCombine(
                                substrg.value(meta),
                                IndexMerging::mergeWith
                            )
                            .thenCompose(Function.identity())
                            .thenCompose(ignore -> substrg.list(meta))
                            .thenCompose(
                                lst -> IndexMergingSlice.remove(substrg, lst)
                                    .thenApply(ignore -> StandardRs.OK)
                            );
                    } else {
                        res = CompletableFuture.completedFuture(StandardRs.NOT_FOUND);
                    }
                    return res;
                }
            );
    }

    /**
     * Delete items from storage.
     * @param asto Storage
     * @param items Keys to remove
     * @return Completable remove operation
     */
    private static CompletableFuture<Void> remove(final Storage asto, final Collection<Key> items) {
        return CompletableFuture.allOf(
            items.stream().map(asto::delete)
                .toArray(CompletableFuture[]::new)
        );
    }

    /**
     * Merging two `index.yaml` files in one file.
     * <ul>
     * <li> If source `index.yaml` does not have some charts or versions of chart,
     * `created` field for them should be updated during merge of the two indexes. </li>
     * <li> If source and remote have the same version of chart, information from
     * remote one should be taken. </li>
     * </ul>
     * @since 0.3
     */
    static final class IndexMerging {
        /**
         * Source `index.yaml` file. Another file will be merged to this one.
         */
        private final Content source;

        /**
         * Ctor.
         * @param source File `index.yaml` that will be merged
         */
        public IndexMerging(final Content source) {
            this.source = source;
        }

        /**
         * Merges passed index with source index file.
         * @param remote File `index.yaml` that will be merged
         * @return Merged `index.yaml`
         */
        public CompletionStage<Content> mergeWith(final Content remote) {
            return new PublisherAs(remote).asciiString()
                .thenApply(IndexYamlMapping::new)
                .thenCombine(
                    new PublisherAs(this.source).asciiString(),
                    (rmt, src) -> {
                        final IndexYamlMapping srcidx = new IndexYamlMapping(src);
                        for (final String name : rmt.entries().keySet()) {
                            srcidx.addChartVersions(name, rmt.byChart(name));
                        }
                        return srcidx;
                    }
                ).thenApply(IndexYamlMapping::toContent)
                .thenApply(opt -> opt.orElse(Content.EMPTY));
        }
    }
}
