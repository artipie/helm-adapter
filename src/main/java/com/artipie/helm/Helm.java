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
import com.artipie.asto.Copy;
import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.fs.FileStorage;
import com.artipie.helm.metadata.IndexYaml;
import com.artipie.helm.misc.EmptyIndex;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.cactoos.list.ListOf;

/**
 * Helm repository.
 * @since 0.3
 * @todo #109:30min Refactor Helm class.
 *  Now this class is too big, therefore it should be refactored
 *  by extracting some functionality. Probably to extract some classes which
 *  would be responsible for writing info about charts to index file.
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
public interface Helm {
    /**
     * Batch update of Helm files for repository.
     * @param prefix Repository prefix
     * @return Result of completion
     */
    CompletionStage<Void> batchUpdate(Key prefix);

    /**
     * Add info to index about charts. Suppose that these charts don't exist in
     * index file, but in any case it checks the existence of
     * passed charts. In case of existence info about them in index
     * file an exception would be thrown.
     * @param charts Keys for charts which should be added to index file
     * @return Result of completion
     */
    CompletionStage<Void> add(Collection<Key> charts);

    /**
     * Remove info from index about charts.
     * @param charts Keys for charts which should be removed from index file
     * @return Result of completion
     */
    CompletionStage<Void> delete(Collection<Key> charts);

    /**
     * Implementation of {@link Helm} for abstract storage.
     * @since 0.3
     */
    @SuppressWarnings("PMD.AvoidDuplicateLiterals")
    final class Asto implements Helm {
        /**
         * Storage.
         */
        private final Storage storage;

        /**
         * Ctor.
         * @param storage Storage
         */
        Asto(final Storage storage) {
            this.storage = storage;
        }

        @Override
        public CompletionStage<Void> batchUpdate(final Key prefix) {
            throw new NotImplementedException("not implemented yet");
        }

        @Override
        public CompletionStage<Void> add(final Collection<Key> charts) {
            final AtomicReference<Key> outidx = new AtomicReference<>();
            final AtomicReference<Path> dir = new AtomicReference<>();
            return new Charts.Asto(this.storage)
                .versionsAndYamlFor(charts)
                .thenCompose(
                    pckgs -> {
                        try {
                            final String prfx = "index-";
                            dir.set(Files.createTempDirectory(prfx));
                            final Path source = Files.createTempFile(dir.get(), prfx, ".yaml");
                            final Path out = Files.createTempFile(dir.get(), prfx, "-out.yaml");
                            final Storage tmpstrg = new FileStorage(dir.get());
                            outidx.set(new Key.From(out.getFileName().toString()));
                            return this.storage.exists(IndexYaml.INDEX_YAML)
                                .thenCompose(
                                    exists -> {
                                        final CompletionStage<Content> res;
                                        if (exists) {
                                            res = this.storage.value(IndexYaml.INDEX_YAML);
                                        } else {
                                            res = CompletableFuture.completedFuture(
                                                new EmptyIndex().asContent()
                                            );
                                        }
                                        return res;
                                    }
                                ).thenCompose(
                                    cont -> tmpstrg.save(
                                        new Key.From(source.getFileName().toString()), cont
                                    )
                                ).thenApply(noth -> new ChartsWriter(this.storage))
                                .thenCompose(writer -> writer.addChartsToIndex(source, out, pckgs))
                                .thenApply(noth -> tmpstrg);
                        } catch (final IOException exc) {
                            throw new UncheckedIOException(exc);
                        }
                    }
                ).thenCompose(
                    tmpstrg -> this.moveFromTempStorageAndDelete(tmpstrg, outidx.get(), dir.get())
                );
        }

        @Override
        public CompletionStage<Void> delete(final Collection<Key> charts) {
            final CompletionStage<Void> res;
            if (charts.isEmpty()) {
                res = CompletableFuture.allOf();
            } else {
                final AtomicReference<Key> outidx = new AtomicReference<>();
                final AtomicReference<Path> dir = new AtomicReference<>();
                res = this.storage.exists(IndexYaml.INDEX_YAML)
                    .thenCompose(
                        exists -> {
                            if (exists) {
                                try {
                                    final String prfx = "index-";
                                    dir.set(Files.createTempDirectory(prfx));
                                    final Path src = Files.createTempFile(dir.get(), prfx, ".yaml");
                                    final Path out;
                                    out = Files.createTempFile(dir.get(), prfx, "-out.yaml");
                                    final Storage tmpstrg = new FileStorage(dir.get());
                                    outidx.set(new Key.From(out.getFileName().toString()));
                                    return this.storage.value(IndexYaml.INDEX_YAML)
                                        .thenCompose(
                                            cont -> tmpstrg.save(
                                                new Key.From(src.getFileName().toString()), cont
                                            )
                                        ).thenCompose(
                                            noth -> new ChartsWriter(this.storage)
                                                .delete(src, out, charts)
                                        ).thenApply(noth -> tmpstrg)
                                        .thenCompose(
                                            tmp -> this.moveFromTempStorageAndDelete(
                                                tmp, outidx.get(), dir.get()
                                            )
                                        ).thenCompose(
                                            noth -> CompletableFuture.allOf(
                                                charts.stream()
                                                    .map(this.storage::delete)
                                                    .toArray(CompletableFuture[]::new)
                                            )
                                        );
                                } catch (final IOException exc) {
                                    throw new UncheckedIOException(exc);
                                }
                            } else {
                                throw new IllegalStateException(
                                    "Failed to delete packages as index does not exist"
                                );
                            }
                        }
                    );
            }
            return res;
        }

        /**
         * Moves index file from temporary storage to real and deletes this file
         * from temporary storage.
         * @param tmpstrg Temporary storage with index file
         * @param outidx Key to index file in temporary storage
         * @param tmpdir Temporary directory
         * @return Result of completion
         */
        private CompletionStage<Void> moveFromTempStorageAndDelete(
            final Storage tmpstrg,
            final Key outidx,
            final Path tmpdir
        ) {
            return new Copy(tmpstrg, new ListOf<>(outidx)).copy(this.storage)
                .thenCompose(noth -> this.storage.move(outidx, IndexYaml.INDEX_YAML))
                .thenApply(noth -> FileUtils.deleteQuietly(tmpdir.toFile()))
                .thenCompose(ignore -> CompletableFuture.allOf());
        }
    }
}
