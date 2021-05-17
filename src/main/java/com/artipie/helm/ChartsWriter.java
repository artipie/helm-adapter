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
import com.artipie.helm.metadata.IndexYaml;
import com.artipie.helm.metadata.IndexYamlMapping;
import com.artipie.helm.metadata.ParsedChartName;
import com.artipie.helm.metadata.YamlWriter;
import com.artipie.helm.misc.DateTimeNow;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Writer of index file which combines source existed index file and add
 * information about passed charts.
 * @since 0.3
 * @checkstyle CyclomaticComplexityCheck (500 lines)
 * @checkstyle ExecutableStatementCountCheck (500 lines)
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 * @checkstyle NestedIfDepthCheck (500 lines)
 * @checkstyle NPathComplexityCheck (500 lines)
 * @todo #109:30min Extract classes for different operations.
 *  Now this class encapsulates logic for several operations. And it is quite large.
 *  It would be better to divide this class in separate classes for each
 *  operation. Probably some other types of refactoring is possible here
 *  for simplification.
 */
@SuppressWarnings({"PMD.AvoidDuplicateLiterals", "PMD.AssignmentInOperand"})
final class ChartsWriter {
    /**
     * Versions.
     */
    static final String VRSNS = "version:";

    /**
     * Entries.
     */
    static final String ENTRS = "entries:";

    /**
     * Generate.
     */
    static final String TAG_GENERATED = "generated:";

    /**
     * Storage.
     */
    private final Storage storage;

    /**
     * Ctor.
     * @param storage Storage
     */
    ChartsWriter(final Storage storage) {
        this.storage = storage;
    }

    /**
     * Add info about charts to index. If index contains a chart with the same
     * version, the exception will be generated. It has the next implementation.
     * Read index file line by line. If we are in the `entries:` section, we will check
     * whether the line is a name of chart (e.g. line has correct indent and ends
     * with colon). It copy source index file line by line and if the line with
     * version is met, the existence of this version in packages would be checked
     * to avoid adding existed package. If the new name of chart is met, it will
     * write remained versions from packages. When we read next line after end of
     * `entries:` section from source index, we write info about remained charts
     * in packages.
     * @param source Path to temporary file with index
     * @param out Path to temporary file in which new index would be written
     * @param pckgs Packages collection which contains info about passed packages for
     *  adding to index file. There is a version and chart yaml for each package.
     * @return Result of completion
     */
    public CompletionStage<Void> addChartsToIndex(
        final Path source,
        final Path out,
        final Map<String, Set<Pair<String, ChartYaml>>> pckgs
    ) {
        return this.storage.exists(IndexYaml.INDEX_YAML)
            .thenCompose(this::versionsByPckgs)
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
                        while ((line = br.readLine()) != null) {
                            final String trimmed = line.trim();
                            final int lastposspace = ChartsWriter.lastPosOfSpaceInBegin(line);
                            if (!entrs) {
                                entrs = trimmed.equals(ChartsWriter.ENTRS);
                            }
                            if (entrs && new ParsedChartName(line).valid()) {
                                if (name == null) {
                                    writer = new YamlWriter(bufw, lastposspace);
                                }
                                if (lastposspace == writer.indent()) {
                                    ChartsWriter.writeRemainedVersionsOfChart(name, pckgs, writer);
                                    name = trimmed.replace(":", "");
                                }
                            }
                            if (entrs) {
                                ChartsWriter.throwIfVersionExists(trimmed, name, pckgs);
                            }
                            if (entrs && name != null && lastposspace == 0) {
                                ChartsWriter.writeRemainedVersionsOfChart(name, pckgs, writer);
                                ChartsWriter.writeRemainedChartsAfterCopyIndex(pckgs, writer);
                                entrs = false;
                            }
                            ChartsWriter.writeAndReplaceTagGenerated(line, writer);
                        }
                        if (entrs) {
                            ChartsWriter.writeRemainedChartsAfterCopyIndex(pckgs, writer);
                        }
                    } catch (final IOException exc) {
                        throw new UncheckedIOException(exc);
                    }
                    return CompletableFuture.allOf();
                }
            );
    }

    /**
     * Rewrites source index file avoiding writing down info about charts which
     * contains in charts collection. If passed for deletion chart does bot exist
     * in index file, an exception will be thrown. It processes source file by
     * reading batch of versions for each chart from source index. Then versions
     * which should not be deleted from file are rewritten to new index file.
     * @param source Path to temporary file with index
     * @param out Path to temporary file in which new index would be written
     * @param charts Collection with keys for charts which should be deleted
     * @return Result of completion
     */
    @SuppressWarnings({"PMD.AvoidDeeplyNestedIfStmts", "PMD.NPathComplexity"})
    public CompletionStage<Void> delete(
        final Path source,
        final Path out,
        final Collection<Key> charts
    ) {
        return new Charts.Asto(this.storage)
            .versionsFor(charts)
            .thenCombine(
                this.storage.exists(IndexYaml.INDEX_YAML).thenCompose(this::versionsByPckgs),
                (todelete, fromidx) -> {
                    checkExistenceChartsToDelete(fromidx, todelete);
                    return todelete;
                }
            ).thenCompose(
                pckgs -> this.storage.exists(IndexYaml.INDEX_YAML)
                    .thenCompose(this::versionsByPckgs)
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
                                final List<String> lines = new ArrayList<>(2);
                                YamlWriter writer = new YamlWriter(bufw, 2);
                                while ((line = br.readLine()) != null) {
                                    final String trimmed = line.trim();
                                    final int posspace = ChartsWriter.lastPosOfSpaceInBegin(line);
                                    if (!entrs) {
                                        entrs = trimmed.equals(ChartsWriter.ENTRS);
                                    }
                                    if (entrs && new ParsedChartName(line).valid()) {
                                        if (name == null) {
                                            writer = new YamlWriter(bufw, posspace);
                                        }
                                        if (posspace == writer.indent()) {
                                            if (name != null) {
                                                writeIfNotContainInDeleted(lines, pckgs, writer);
                                            }
                                            name = trimmed.replace(":", "");
                                        }
                                    }
                                    if (entrs && name != null && posspace == 0) {
                                        entrs = false;
                                        writeIfNotContainInDeleted(lines, pckgs, writer);
                                    }
                                    if (entrs && name != null) {
                                        lines.add(line);
                                    }
                                    if (lines.isEmpty()) {
                                        writeAndReplaceTagGenerated(line, writer);
                                    }
                                }
                            } catch (final IOException exc) {
                                throw new UncheckedIOException(exc);
                            }
                            return CompletableFuture.allOf();
                        }
                    )
            );
    }

    /**
     * Write line if it does not start with tag generated. Otherwise replaces the value
     * of tag `generated` to update time when this index was generated.
     * @param line Parsed line
     * @param writer Writer
     * @throws IOException In case of exception during writing
     */
    private static void writeAndReplaceTagGenerated(final String line, final YamlWriter writer)
        throws IOException {
        if (line.startsWith(ChartsWriter.TAG_GENERATED)) {
            writer.writeLine(
                String.format(
                    "%s %s", ChartsWriter.TAG_GENERATED, new DateTimeNow().asString()
                ),
                0
            );
        } else {
            writer.writeLine(line, 0);
        }
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
                throw new IllegalStateException(
                    String.format(
                        "Failed to delete package `%s` as it is absent in index", pckg
                    )
                );
            }
            for (final String vrsn : todelete.get(pckg)) {
                if (!fromidx.get(pckg).contains(vrsn)) {
                    // @checkstyle LineLengthCheck (3 lines)
                    throw new IllegalStateException(
                        String.format(
                            "Failed to delete package `%s` with version `%s` as it is absent in index",
                            pckg,
                            vrsn
                        )
                    );
                }
            }
        }
    }

    /**
     * Obtains versions by packages from source index file or empty collection in case of
     * absence source index file.
     * @param exists Does source index file exist?
     * @return Versions by packages.
     */
    private CompletionStage<Map<String, Set<String>>> versionsByPckgs(final boolean exists) {
        final CompletionStage<Map<String, Set<String>>> res;
        if (exists) {
            res = new Index.WithBreaks(this.storage).versionsByPackages();
        } else {
            res = CompletableFuture.completedFuture(new HashMap<>());
        }
        return res;
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
        if (trimmed.startsWith(ChartsWriter.VRSNS)) {
            final String vers = trimmed.replace(ChartsWriter.VRSNS, "").trim();
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
                line -> line.trim().startsWith(ChartsWriter.VRSNS)
            ).map(line -> line.replace(ChartsWriter.VRSNS, ""))
            .map(String::trim)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Couldn't find version for deletion"));
        }
    }
}
