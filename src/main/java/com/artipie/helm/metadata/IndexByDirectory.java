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

import com.artipie.asto.Content;
import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.ext.PublisherAs;
import com.artipie.helm.TgzArchive;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * Generate `index.yaml` file  given a directory containing packaged charts.
 * See <a href="https://v2.helm.sh/docs/helm/#helm-repo-index">repo index</a>.
 * @since 0.2
 */
public class IndexByDirectory {
    /**
     * Storage.
     */
    private final Storage storage;

    /**
     * Directory.
     */
    private final Key directory;

    /**
     * Base url.
     */
    private final Optional<String> baseurl;

    /**
     * Ctor.
     * @param storage Storage
     * @param directory Directory from which packaged charts are taken
     * @param baseurl Absolute url to the charts
     */
    public IndexByDirectory(
        final Storage storage,
        final Key directory,
        final Optional<String> baseurl
    ) {
        this.storage = storage;
        this.directory = directory;
        this.baseurl = baseurl;
    }

    /**
     * Obtains generated `index.yaml`.
     * @return Bytes of generated `index.yaml`,
     *  empty in case of absence of packaged charts.
     */
    public CompletionStage<Optional<Content>> value() {
        return this.storage
            .list(this.directory)
            .thenCompose(
                keys -> {
                    final IndexYamlMapping index = new IndexYamlMapping();
                    return CompletableFuture.allOf(
                        keys.stream()
                            .map(
                                key -> key
                                    .string()
                                    .substring(this.directory.string().length() + 1)
                            )
                            .map(key -> key.split("/"))
                            .filter(parts -> parts.length == 1 && parts[0].endsWith(".tgz"))
                            .map(parts -> parts[0])
                            .map(this.extractMetadata(index))
                            .toArray(CompletableFuture[]::new)
                    ).thenApply(nothing -> index);
                }
            )
            .thenApply(IndexYamlMapping::toContent);
    }

    /**
     * Add metadata to the index.
     * @param index Index to be updated.
     * @return A function.
     */
    private Function<String, CompletableFuture<Void>> extractMetadata(
        final IndexYamlMapping index
    ) {
        return key ->
            this.storage.value(new Key.From(this.directory, key))
                .thenApply(PublisherAs::new)
                .thenCompose(PublisherAs::bytes)
                .thenApply(TgzArchive::new)
                .thenAccept(
                    tgz -> index.addChartVersions(
                        tgz.chartYaml().name(),
                        new ArrayList<>(
                            Collections.singletonList(
                                tgz.metadata(this.baseurl)
                            )
                        )
                    )
                );
    }

}
