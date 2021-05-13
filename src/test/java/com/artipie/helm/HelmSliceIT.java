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
import com.artipie.asto.blocking.BlockingStorage;
import com.artipie.asto.memory.InMemoryStorage;
import com.artipie.asto.test.TestResource;
import com.artipie.helm.http.HelmSlice;
import com.artipie.helm.metadata.IndexYamlMapping;
import com.artipie.http.rs.RsStatus;
import com.artipie.http.rs.StandardRs;
import com.artipie.vertx.VertxSliceServer;
import com.google.common.collect.Maps;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.ext.web.client.WebClient;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.hamcrest.MatcherAssert;
import org.hamcrest.collection.IsMapContaining;
import org.hamcrest.collection.IsMapWithSize;
import org.hamcrest.core.IsAnything;
import org.hamcrest.core.IsEqual;
import org.hobsoft.hamcrest.compose.ComposeMatchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;

/**
 * Push helm chart and ensure if index.yaml is generated properly.
 *
 * @since 0.2
 * @checkstyle MethodBodyCommentsCheck (500 lines)
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 * @checkstyle MagicNumberCheck (500 lines)
 * @checkstyle ExecutableStatementCountCheck (500 lines)
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
@DisabledIfSystemProperty(named = "os.name", matches = "Windows.*")
public class HelmSliceIT {

    @Test
    public void indexYamlIsCorrect(@TempDir final Path tmp) throws Exception {
        final Vertx vertx = Vertx.vertx();
        final Storage fls = new InMemoryStorage();
        final int port = rndPort();
        final VertxSliceServer server = new VertxSliceServer(
            vertx,
            new HelmSlice(fls, String.format("http://localhost:%d/", port)),
            port
        );
        final byte[] tomcat = new TestResource("tomcat-0.4.1.tgz").asBytes();
        final WebClient web = WebClient.create(vertx);
        try {
            server.start();
            final int code = web.post(port, "localhost", "/")
                .rxSendBuffer(Buffer.buffer(tomcat))
                .blockingGet()
                .statusCode();
            MatcherAssert.assertThat(
                code,
                new IsEqual<>(Integer.parseInt(RsStatus.OK.code()))
            );
            final String adapter = new String(
                new BlockingStorage(fls).value(new Key.From("index.yaml"))
            );
            LoggerFactory.getLogger(HelmSliceIT.class).info(
                "Generated by adapter index.yaml:\n{}",
                adapter
            );
            Files.write(tmp.resolve("tomcat-0.4.1.tgz"), tomcat);
            final HelmContainer helm = new HelmContainer()
                .withCommand("repo", "index", ".")
                .withWorkingDirectory("/home/")
                .withFileSystemBind(tmp.toString(), "/home");
            helm.start();
            final File index = tmp.resolve("index.yaml").toFile();
            Awaitility.await().atMost(5, TimeUnit.SECONDS).until(index::exists);
            final String expected = new String(Files.readAllBytes(index.toPath()));
            LoggerFactory.getLogger(HelmSliceIT.class).info(
                "Generated by helm index.yaml:\n{}",
                expected
            );
            MatcherAssert.assertThat(
                Maps.difference(
                    tomcatZeroEntry(expected),
                    tomcatZeroEntry(adapter)
                ).entriesDiffering(),
                ComposeMatchers.compose(new IsMapWithSize<String, Object>(new IsEqual<>(1)))
                    .and(new IsMapContaining<>(new IsEqual<>("created"), new IsAnything<>()))
            );
        } finally {
            web.close();
            server.close();
            vertx.close();
        }
    }

    /**
     * Parse and return index.yaml.
     * @param yaml The index.yaml file passed as string
     * @return The first element in entries->tomcat
     */
    private static Map<String, Object> tomcatZeroEntry(final String yaml) {
        return new IndexYamlMapping(yaml).byChart("tomcat").get(0);
    }

    /**
     * Obtain a random port.
     * @return The random port.
     */
    private static int rndPort() {
        final Vertx vertx = Vertx.vertx();
        final VertxSliceServer server = new VertxSliceServer(
            vertx,
            (line, headers, body) -> StandardRs.EMPTY
        );
        try {
            return server.start();
        } finally {
            server.stop();
            vertx.close();
        }
    }

    /**
     * Inner subclass to instantiate Helm container.
     *
     * @since 0.2
     */
    private static class HelmContainer extends GenericContainer<HelmContainer> {
        HelmContainer() {
            super("alpine/helm:2.12.1");
        }
    }
}
