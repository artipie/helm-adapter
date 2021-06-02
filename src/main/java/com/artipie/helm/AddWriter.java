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
import com.artipie.helm.metadata.Index;
import com.artipie.helm.metadata.IndexYamlMapping;
import com.artipie.helm.metadata.ParsedChartName;
import com.artipie.helm.metadata.YamlWriter;
import com.artipie.helm.misc.LineWriter;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Add writer of info about charts to index file.
 * @since 0.3
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 * @checkstyle CyclomaticComplexityCheck (500 lines)
 * @checkstyle ExecutableStatementCountCheck (500 lines)
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
interface AddWriter {
    /**
     * Add info about charts to index. If index contains a chart with the same
     * version, the exception should be generated.
     * @param source Path to temporary file with index
     * @param out Path to temporary file in which new index would be written
     * @param pckgs Packages collection which contains info about passed packages for
     *  adding to index file. There is a version and chart yaml for each package.
     * @return Result of completion
     */
    CompletionStage<Void> add(
        Path source,
        Path out,
        Map<String, Set<Pair<String, ChartYaml>>> pckgs
    );

    /**
     * Implementation of {@link AddWriter} for abstract storage.
     * @since 0.3
     */
    final class Asto implements AddWriter {
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

        // @checkstyle NoJavadocForOverriddenMethodsCheck (15 lines)
        // @checkstyle JavadocParameterOrderCheck (15 lines)
        /**
         * It has the next implementation.
         * Read index file line by line. If we are in the `entries:` section, we will check
         * whether the line is a name of chart (e.g. line has correct indent and ends
         * with colon). It copy source index file line by line and if the line with
         * version is met, the existence of this version in packages would be checked
         * to avoid adding existed package. If the new name of chart is met, it will
         * write remained versions from packages. When we read next line after end of
         * `entries:` section from source index, we write info about remained charts
         * in packages.
         */
        @Override
        @SuppressWarnings("PMD.AssignmentInOperand")
        public CompletionStage<Void> add(
            final Path source,
            final Path out,
            final Map<String, Set<Pair<String, ChartYaml>>> pckgs
        ) {
            return new Index.WithBreaks(this.storage)
                .versionsByPackages(new Key.From(source.getFileName().toString()))
                .thenCompose(
                    vrsns -> {
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
                            YamlWriter writer = new YamlWriter(bufw, 2);
                            LineWriter linewrtr = new LineWriter(writer);
                            while ((line = br.readLine()) != null) {
                                final String trimmed = line.trim();
                                final int lastposspace = lastPosOfSpaceInBegin(line);
                                if (!entrs) {
                                    entrs = trimmed.equals(Asto.ENTRS);
                                }
                                if (entrs && new ParsedChartName(line).valid()) {
                                    if (name == null) {
                                        writer = new YamlWriter(bufw, lastposspace);
                                        linewrtr = new LineWriter(writer);
                                    }
                                    if (lastposspace == writer.indent()) {
                                        writeRemainedVersionsOfChart(name, pckgs, writer);
                                        name = trimmed.replace(":", "");
                                    }
                                }
                                if (entrs) {
                                    throwIfVersionExists(trimmed, name, pckgs);
                                }
                                if (entrs && name != null && lastposspace == 0) {
                                    writeRemainedVersionsOfChart(name, pckgs, writer);
                                    writeRemainedChartsAfterCopyIndex(pckgs, writer);
                                    entrs = false;
                                }
                                linewrtr.writeAndReplaceTagGenerated(line);
                            }
                            if (entrs) {
                                writeRemainedChartsAfterCopyIndex(pckgs, writer);
                            }
                        } catch (final IOException exc) {
                            throw new UncheckedIOException(exc);
                        }
                        return CompletableFuture.allOf();
                    }
                );
        }

        /**
         * Generates an exception if version of chart which contains in trimmed
         * line exists in packages.
         * @param trimmed Trimmed line from index file
         * @param name Name of chart
         * @param pckgs Packages collection which contains info about passed packages for
         *  adding to index file. There is a version and chart yaml for each package.
         */
        private static void throwIfVersionExists(
            final String trimmed,
            final String name,
            final Map<String, Set<Pair<String, ChartYaml>>> pckgs
        ) {
            if (trimmed.startsWith(Asto.VRSNS)) {
                final String vers = trimmed.replace(Asto.VRSNS, "").trim();
                if (pckgs.containsKey(name) && pckgs.get(name).stream().anyMatch(
                    pair -> pair.getLeft().equals(vers)
                )) {
                    throw new IllegalStateException(
                        String.format("Failed to write to index `%s` with version `%s`", name, vers)
                    );
                }
            }
        }

        /**
         * Write remained versions of passed chart in collection in case of their existence.
         * @param name Chart name for which remained versions are checked
         * @param pckgs Packages collection which contains info about passed packages for
         *  adding to index file. There is a version and chart yaml for each package.
         * @param writer Yaml writer
         * @throws IOException In case of exception during writing
         */
        private static void writeRemainedVersionsOfChart(
            final String name,
            final Map<String, Set<Pair<String, ChartYaml>>> pckgs,
            final YamlWriter writer
        ) throws IOException {
            if (name != null && pckgs.containsKey(name)) {
                for (final Pair<String, ChartYaml> pair : pckgs.get(name)) {
                    writer.writeLine("-", 2);
                    final String str = new IndexYamlMapping(pair.getRight().fields()).toString();
                    for (final String entry : str.split("[\\n\\r]+")) {
                        // @checkstyle MagicNumberCheck (1 line)
                        writer.writeLine(entry, 3);
                    }
                }
                pckgs.remove(name);
            }
        }

        /**
         * Write remained versions for all charts in collection in case of their existence.
         * @param pckgs Packages collection which contains info about passed packages for
         *  adding to index file. There is a version and chart yaml for each package.
         * @param writer Yaml writer
         */
        private static void writeRemainedChartsAfterCopyIndex(
            final Map<String, Set<Pair<String, ChartYaml>>> pckgs,
            final YamlWriter writer
        ) {
            pckgs.forEach(
                (chart, pairs) -> {
                    try {
                        writer.writeLine(String.format("%s:", chart), 1);
                        for (final Pair<String, ChartYaml> pair : pairs) {
                            writer.writeLine("- ", 2);
                            final String yaml;
                            yaml = new IndexYamlMapping(pair.getRight().fields()).toString();
                            final String[] lines = yaml.split("[\\n\\r]+");
                            for (final String line : lines) {
                                // @checkstyle MagicNumberCheck (1 line)
                                writer.writeLine(line, 3);
                            }
                        }
                    } catch (final IOException exc) {
                        throw  new UncheckedIOException(exc);
                    }
                }
            );
            pckgs.clear();
        }

        /**
         * Obtains last position of space from beginning before meeting any character.
         * @param line Text line
         * @return Last position of space from beginning before meeting any character.
         */
        private static int lastPosOfSpaceInBegin(final String line) {
            return line.length() - line.replaceAll("^\\s*", "").length();
        }
    }
}
