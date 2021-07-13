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

import com.artipie.ArtipieException;
import com.artipie.asto.ArtipieIOException;
import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.helm.metadata.Index;
import com.artipie.helm.metadata.ParsedChartName;
import com.artipie.helm.metadata.YamlWriter;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Remove writer of info about charts from index file.
 * @since 0.3
 * @checkstyle CyclomaticComplexityCheck (500 lines)
 * @checkstyle ExecutableStatementCountCheck (500 lines)
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 * @checkstyle NestedIfDepthCheck (500 lines)
 * @checkstyle NPathComplexityCheck (500 lines)
 */
public interface RemoveWriter {
    /**
     * Rewrites source index file avoiding writing down info about charts which
     * contains in charts collection. If passed for deletion chart does bot exist
     * in index file, an exception should be thrown. It processes source file by
     * reading batch of versions for each chart from source index. Then versions
     * which should not be deleted from file are rewritten to new index file.
     * @param source Path to temporary file with index
     * @param out Path to temporary file in which new index would be written
     * @param todelete Collection with charts with specified versions which should be deleted
     * @return Result of completion
     */
    CompletionStage<Void> delete(Path source, Path out, Map<String, Set<String>> todelete);

    /**
     * Implementation of {@link RemoveWriter} for abstract storage.
     * @since 0.3
     */
    final class Asto implements RemoveWriter {
        /**
         * Versions.
         */
        static final String VRSNS = "version:";

        /**
         * Entries.
         */
        static final String ENTRS = "entries:";

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
        @SuppressWarnings({
            "PMD.AssignmentInOperand",
            "PMD.AvoidDeeplyNestedIfStmts",
            "PMD.NPathComplexity"
        })
        public CompletionStage<Void> delete(
            final Path source,
            final Path out,
            final Map<String, Set<String>> todelete
        ) {
            return new Index.WithBreaks(this.storage)
                .versionsByPackages(new Key.From(source.getFileName().toString()))
                .thenCompose(
                    fromidx -> {
                        checkExistenceChartsToDelete(fromidx, todelete);
                        return CompletableFuture.allOf();
                    }
                ).thenCompose(
                    noth ->  {
                        try (
                            BufferedReader br = new BufferedReader(
                                new InputStreamReader(Files.newInputStream(source))
                            );
                            BufferedWriter bufw = new BufferedWriter(
                                new OutputStreamWriter(Files.newOutputStream(out))
                            )
                        ) {
                            String line;
                            boolean entrs = false;
                            String name = null;
                            final List<String> lines = new ArrayList<>(2);
                            YamlWriter writer = new YamlWriter(bufw, 2);
                            while ((line = br.readLine()) != null) {
                                final String trimmed = line.trim();
                                final int posspace = lastPosOfSpaceInBegin(line);
                                if (!entrs) {
                                    entrs = trimmed.equals(Asto.ENTRS);
                                }
                                if (entrs && new ParsedChartName(line).valid()) {
                                    if (name == null) {
                                        writer = new YamlWriter(bufw, posspace);
                                    }
                                    if (posspace == writer.indent()) {
                                        if (name != null) {
                                            writeIfNotContainInDeleted(lines, todelete, writer);
                                        }
                                        name = trimmed.replace(":", "");
                                    }
                                }
                                if (entrs && name != null && posspace == 0) {
                                    entrs = false;
                                    writeIfNotContainInDeleted(lines, todelete, writer);
                                }
                                if (entrs && name != null) {
                                    lines.add(line);
                                }
                                if (lines.isEmpty()) {
                                    writer.writeAndReplaceTagGenerated(line);
                                }
                            }
                        } catch (final IOException exc) {
                            throw new ArtipieIOException(exc);
                        }
                        return CompletableFuture.allOf();
                    }
                );
        }

        /**
         * Writes info about all versions of chart to new index if chart with specified
         * name and version does not exist in collection of charts which should be removed
         * from index file.
         * @param lines Parsed lines
         * @param pckgs Charts which should be removed
         * @param writer Writer
         * @throws IOException In case of exception during writing
         */
        private static void writeIfNotContainInDeleted(
            final List<String> lines,
            final Map<String, Set<String>> pckgs,
            final YamlWriter writer
        ) throws IOException {
            final ChartVersions items = new ChartVersions(lines);
            final String name = items.name().trim().replace(":", "");
            final Map<String, List<String>> vrsns = items.versions();
            boolean recordedname = false;
            if (pckgs.containsKey(name)) {
                for (final String vers : vrsns.keySet()) {
                    if (!pckgs.get(name).contains(vers)) {
                        if (!recordedname) {
                            recordedname = true;
                            writer.writeLine(items.name(), 0);
                        }
                        final List<String> entry = vrsns.get(vers);
                        for (final String line : entry) {
                            writer.writeLine(line, 0);
                        }
                    }
                }
            } else {
                for (final String line : lines) {
                    writer.writeLine(line, 0);
                }
            }
            lines.clear();
        }

        /**
         * Checks whether all charts with specified versions exist in index file,
         * in case of absence one of them an exception will be thrown.
         * @param fromidx Charts with specified versions from index file
         * @param todelete Charts with specified versions which should be deleted
         */
        private static void checkExistenceChartsToDelete(
            final Map<String, Set<String>> fromidx,
            final Map<String, Set<String>> todelete
        ) {
            for (final String pckg : todelete.keySet()) {
                if (!fromidx.containsKey(pckg)) {
                    throw new ArtipieException(
                        new IllegalStateException(
                            String.format(
                                "Failed to delete package `%s` as it is absent in index", pckg
                            )
                        )
                    );
                }
                for (final String vrsn : todelete.get(pckg)) {
                    if (!fromidx.get(pckg).contains(vrsn)) {
                        // @checkstyle LineLengthCheck (5 lines)
                        throw new ArtipieException(
                            new IllegalStateException(
                                String.format(
                                    "Failed to delete package `%s` with version `%s` as it is absent in index",
                                    pckg,
                                    vrsn
                                )
                            )
                        );
                    }
                }
            }
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
         * Extracts versions for chart from passed parsed lines.
         * @since 0.3
         */
        private static final class ChartVersions {
            /**
             * Parsed lines.
             */
            private final List<String> lines;

            /**
             * First line should contain name of chart. It is important that
             * these lines are not trimmed.
             * @param lines Parsed lines from index file
             */
            ChartVersions(final List<String> lines) {
                this.lines = lines;
            }

            /**
             * Extracts versions from parsed lines. It is necessary because one chart can
             * have many versions and parsed lines contain all of them.
             * @return Map with info from index file for each version of chart.
             */
            public Map<String, List<String>> versions() {
                final Map<String, List<String>> vrsns = new HashMap<>();
                final char dash = '-';
                if (this.lines.size() > 1) {
                    final int indent = this.lines.get(1).indexOf(dash);
                    final List<String> tmp = new ArrayList<>(2);
                    for (int idx = 1; idx < this.lines.size(); idx += 1) {
                        if (this.lines.get(idx).charAt(indent) == dash && !tmp.isEmpty()) {
                            vrsns.put(version(tmp), new ArrayList<>(tmp));
                            tmp.clear();
                        }
                        tmp.add(this.lines.get(idx));
                    }
                    vrsns.put(version(tmp), new ArrayList<>(tmp));
                }
                return vrsns;
            }

            /**
             * Obtains name of chart.
             * @return Name of chart.
             */
            public String name() {
                if (!this.lines.isEmpty()) {
                    return this.lines.get(0);
                }
                throw new IllegalStateException("Failed to get name as there are no lines");
            }

            /**
             * Extracts version from parsed lines.
             * @param entry Parsed lines from index with version
             * @return Version from parsed lines.
             */
            private static String version(final List<String> entry) {
                return entry.stream().filter(
                    line -> line.trim().startsWith(Asto.VRSNS)
                ).map(line -> line.replace(Asto.VRSNS, ""))
                    .map(String::trim)
                    .findFirst()
                    .orElseThrow(
                        () -> new IllegalStateException("Couldn't find version for deletion")
                    );
            }
        }
    }
}
