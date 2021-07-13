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
import com.artipie.asto.Remaining;
import com.artipie.asto.Storage;
import com.artipie.http.misc.TokenizerFlatProc;
import hu.akarnokd.rxjava2.interop.FlowableInterop;
import io.reactivex.Flowable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reader of `index.yaml` file which does not read the entire file into memory.
 * @since 0.3
 * @todo #112:90min Replace parser with reactive version to avoid copying index
 *  file to temp storage. This parser should be replaced with converter from Publisher#ByteBuffer
 *  to another Publisher#ByteBuffer which is splitted by breaks (based on implementation
 *  of org.reactivestreams.Processor)
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
                        final CompletionStage<Map<String, Set<String>>> res;
                        if (exists) {
                            res = this.versionsByPckgs(idx);
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
         * @param idx Key of index file
         * @return Parsed versions of packages from index file.
         */
        @SuppressWarnings("PMD.AssignmentInOperand")
        private CompletionStage<Map<String, Set<String>>> versionsByPckgs(final Key idx) {
            final TokenizerFlatProc target = new TokenizerFlatProc("\n");
            final AtomicInteger indent = new AtomicInteger(0);
            final Map<String, Set<String>> vrns = new ConcurrentHashMap<>();
            final AtomicBoolean inentries = new AtomicBoolean(false);
            final String empty = "";
            return this.storage.value(idx).thenAccept(
                cont -> cont.subscribe(target)
            ).thenCompose(
                noth -> Flowable.fromPublisher(target)
                    .map(buf -> new String(new Remaining(buf).bytes()))
                    .scan(
                        empty,
                        (name, curr) -> {
                            final String res;
                            final int pos = WithBreaks.lastPosOfSpaceInBegin(curr);
                            if (pos > 0 && indent.get() == 0) {
                                indent.set(pos);
                            }
                            if (curr.startsWith(WithBreaks.ENTRS)) {
                                inentries.set(true);
                            } else if (pos == 0) {
                                inentries.set(false);
                            }
                            if (new ParsedChartName(curr).valid() && pos == indent.get()) {
                                res = curr.replace(":", "").trim();
                            } else if (inentries.get()) {
                                if (curr.trim().startsWith(WithBreaks.VRSNS)) {
                                    final Set<String> existed = vrns.computeIfAbsent(
                                        name, none -> new HashSet<>()
                                    );
                                    existed.add(curr.replace(WithBreaks.VRSNS, "").trim());
                                    vrns.put(name, existed);
                                }
                                res = name;
                            } else {
                                res = empty;
                            }
                            return res;
                        }
                    )
                .to(FlowableInterop.last())
                .thenApply(ignore -> vrns)
            );
        }
    }
}
