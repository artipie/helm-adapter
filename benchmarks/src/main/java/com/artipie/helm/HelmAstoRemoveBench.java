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
import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.memory.InMemoryStorage;
import com.artipie.asto.test.TestResource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.cactoos.scalar.Unchecked;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Benchmark for {@link com.artipie.helm.Helm.Asto#delete}.
 * @since 0.3
 * @checkstyle MagicNumberCheck (500 lines)
 * @checkstyle DesignForExtensionCheck (500 lines)
 * @checkstyle JavadocMethodCheck (500 lines)
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5)
@Measurement(iterations = 20)
public class HelmAstoRemoveBench {
    /**
     * Benchmark directory.
     */
    private static final String BENCH_DIR = System.getenv("BENCH_DIR");

    /**
     * Key for index file.
     */
    private static final Key INDEX = new Key.From("index.yaml");

    /**
     * Collection of keys of files which should be removed.
     */
    private Set<Key> todelete;

    /**
     * Implementation of storage for benchmarks.
     */
    private BenchStorage storage;

    /**
     * Content of index file.
     */
    private byte[] index;

    @Setup
    public void setup() throws IOException {
        if (HelmAstoRemoveBench.BENCH_DIR == null) {
            throw new IllegalStateException("BENCH_DIR environment variable must be set");
        }
        this.todelete = Stream.of(
            "moodle-7.2.8.tgz",
            "msoms-0.2.2.tgz",
            "mssql-linux-0.11.4.tgz",
            "rethinkdb-1.1.4.tgz",
            "spring-cloud-data-flow-2.8.1.tgz",
            "quassel-0.2.13.tgz",
            "rocketchat-2.0.10.tgz",
            "oauth2-proxy-1.0.0.tgz", "oauth2-proxy-1.0.1.tgz", "oauth2-proxy-1.1.0.tgz",
            "prometheus-11.11.0.tgz", "prometheus-11.11.1.tgz",
            "parse-6.2.16.tgz", "parse-7.0.0.tgz", "parse-7.1.0.tgz"
        ).map(Key.From::new)
        .collect(Collectors.toSet());
        this.storage = new BenchStorage();
        try (Stream<Path> files = Files.list(Paths.get(HelmAstoRemoveBench.BENCH_DIR))) {
            files.forEach(
                file -> {
                    final byte[] bytes = new Unchecked<>(() -> Files.readAllBytes(file)).value();
                    if (file.getFileName().toString().equals(HelmAstoRemoveBench.INDEX.string())) {
                        this.index = bytes;
                    }
                    this.storage.save(
                        new Key.From(file.getFileName().toString()),
                        new Content.From(bytes)
                    ).join();
                }
            );
        }
    }

    @Setup(Level.Invocation)
    public void setupInvocation() {
        this.storage.reset().toCompletableFuture().join();
        this.storage.save(HelmAstoRemoveBench.INDEX, new Content.From(this.index)).join();
    }

    @Benchmark
    public void run() {
        new Helm.Asto(this.storage)
            .delete(this.todelete, Key.ROOT)
            .toCompletableFuture().join();
    }

    /**
     * Main.
     * @param args CLI args
     * @throws RunnerException On benchmark failure
     */
    public static void main(final String... args) throws RunnerException {
        new Runner(
            new OptionsBuilder()
                .include(HelmAstoRemoveBench.class.getSimpleName())
                .forks(1)
                .build()
        ).run();
    }
}
