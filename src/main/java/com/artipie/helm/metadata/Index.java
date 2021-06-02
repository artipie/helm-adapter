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

import com.artipie.asto.FailedCompletionStage;
import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.fs.FileStorage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Reader of `index.yaml` file which does not read the entire file into memory.
 * @since 0.3
 * @todo #112:90min Replace parser with reactive version to avoid copying index
 *  file to temp storage. This parser should be replaced with converter from Publisher#ByteBuffer
 *  to another Publisher#ByteBuffer which is splitted by breaks (based on implementation
 *  of org.reactivestreams.Processor)
 *  @checkstyle CyclomaticComplexityCheck (500 lines)
 */
public interface Index {
    /**
     * Obtains versions for packages which exist in the index file.
     * @param idxpath Path to index file
     * @return Map where key is a package name, value is represented versions.
     */
    CompletionStage<Map<String, Set<String>>> versionsByPackages(Key idxpath);

    /**
     * Reader of `index.yaml` which contains break lines.
     * This file looks like:
     * <pre>
     * apiVersion: v1
     * entries:
     *   ark:
     *   - apiVersion: v1
     *     version: 0.1.0
     * </pre>
     * @since 0.3
     */
    @SuppressWarnings("PMD.CyclomaticComplexity")
    final class WithBreaks implements Index {
        /**
         * Versions.
         */
        static final String VRSNS = "version:";

        /**
         * Entries.
         */
        static final String ENTRS = "entries:";

        /**
         * Storage with index file.
         */
        private final Storage storage;

        /**
         * Ctor.
         * @param storage Storage file
         */
        public WithBreaks(final Storage storage) {
            this.storage = storage;
        }

        @Override
        public CompletionStage<Map<String, Set<String>>> versionsByPackages(final Key idx) {
            return this.storage.exists(idx)
                .thenCompose(
                    exists -> {
                        CompletionStage<Map<String, Set<String>>> res;
                        if (exists) {
                            try {
                                final String prefix = "index-";
                                final Path tmp = Files.createTempDirectory(prefix);
                                final Path file = Files.createTempFile(tmp, prefix, ".yaml");
                                res = this.storage.value(idx)
                                    .thenCompose(
                                        cont -> new FileStorage(tmp).save(
                                            new Key.From(file.getFileName().toString()), cont
                                        )
                                    ).thenApply(ignore -> WithBreaks.versionsByPckgs(file));
                            } catch (final IOException exc) {
                                res = new FailedCompletionStage<>(exc);
                            }
                        } else {
                            res = CompletableFuture.completedFuture(new HashMap<>());
                        }
                        return res;
                    }
                );
        }

        /**
         * Obtains last position of space from beginning before meeting any character.
         * @param line Text line
         * @return Last position of space from beginning before meeting any character.
         */
        private static int lastPosOfSpaceInBegin(final String line) {
            return line.length() - line.replaceAll("^\\s*", "").length();
        }

        /**
         * Parses index file and extracts versions for packages. The general idea of this parser
         * is next. All info about charts is located in `entries:` section. When we enter
         * in this section (e. g. read a line which is equal to `entries`), we started to
         * search a string which ends with colon and has required indent (usually it is
         * equal to 2). This string represents chart name. We've read such string, we
         * started to read info about saved versions for this chart. When we meet
         * a line which starts with `version:`, the version in map by chart name as key
         * is added.
         * @param file Path to file
         * @return Parsed versions of packages from index file.
         */
        @SuppressWarnings("PMD.AssignmentInOperand")
        private static Map<String, Set<String>> versionsByPckgs(final Path file) {
            try (
                BufferedReader br = new BufferedReader(
                    new InputStreamReader(Files.newInputStream(file))
                )
            ) {
                String line;
                boolean entrs = false;
                String name = null;
                int indent = 2;
                final Map<String, Set<String>> vrns = new HashMap<>();
                while ((line = br.readLine()) != null) {
                    final String trimmed = line.trim();
                    if (!entrs) {
                        entrs = trimmed.equals(WithBreaks.ENTRS);
                    }
                    if (new ParsedChartName(line).valid()) {
                        if (name == null) {
                            indent = WithBreaks.lastPosOfSpaceInBegin(line);
                        }
                        if (WithBreaks.lastPosOfSpaceInBegin(line) == indent) {
                            name = trimmed.replace(":", "");
                            vrns.put(name, new HashSet<>());
                        }
                    }
                    if (entrs && trimmed.startsWith(WithBreaks.VRSNS)) {
                        vrns.get(name).add(
                            line.replace(WithBreaks.VRSNS, "").trim()
                        );
                    }
                }
                return vrns;
            } catch (final IOException exc) {
                throw new UncheckedIOException(exc);
            }
        }
    }
}
