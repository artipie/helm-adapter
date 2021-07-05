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

import com.artipie.asto.Storage;
import com.artipie.asto.memory.InMemoryStorage;
import com.artipie.asto.test.TestResource;
import com.artipie.helm.http.HelmSlice;
import com.artipie.helm.test.ContentOfIndex;
import com.artipie.http.auth.Authentication;
import com.artipie.http.auth.JoinedPermissions;
import com.artipie.http.auth.Permissions;
import com.artipie.http.misc.RandomFreePort;
import com.artipie.http.rs.RsStatus;
import com.artipie.http.slice.LoggingSlice;
import com.artipie.vertx.VertxSliceServer;
import com.google.common.io.ByteStreams;
import io.vertx.reactivex.core.Vertx;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;

/**
 * Ensure that helm command line tool is compatible with this adapter.
 *
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 * @since 0.2
 */
@DisabledIfSystemProperty(named = "os.name", matches = "Windows.*")
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
final class HelmSliceIT {
    /**
     * Vert instance.
     */
    private static final Vertx VERTX = Vertx.vertx();

    /**
     * Chart name.
     */
    private static final String CHART = "tomcat-0.4.1.tgz";

    /**
     * Username.
     */
    private static final String USER = "alice";

    /**
     * User password.
     */
    private static final String PSWD = "123";

    /**
     * The helm container.
     */
    private HelmSliceIT.HelmContainer cntn;

    /**
     * Test container url.
     */
    private String url;

    /**
     * The server.
     */
    private VertxSliceServer server;

    /**
     * Port.
     */
    private int port;

    /**
     * URL connection.
     */
    private HttpURLConnection con;

    /**
     * Storage.
     */
    private Storage storage;

    @BeforeEach
    void setUp() {
        this.storage = new InMemoryStorage();
    }

    @AfterAll
    static void tearDownAll() {
        HelmSliceIT.VERTX.close();
    }

    @AfterEach
    void tearDown() {
        this.con.disconnect();
        this.cntn.stop();
        this.server.close();
    }

    @Test
    void indexYamlIsCreated() throws Exception {
        this.init(true);
        this.con = this.putToLocalhost(true);
        MatcherAssert.assertThat(
            "Response status is not 200",
            this.con.getResponseCode(),
            new IsEqual<>(Integer.parseInt(RsStatus.OK.code()))
        );
        MatcherAssert.assertThat(
            "Generated index does not contain required chart",
            new ContentOfIndex(this.storage).index()
                .byChartAndVersion("tomcat", "0.4.1")
                .isPresent(),
            new IsEqual<>(true)
        );
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void helmRepoAddAndUpdateWorks(final boolean anonymous) throws Exception {
        final String hostport = this.init(anonymous);
        this.con = this.putToLocalhost(anonymous);
        MatcherAssert.assertThat(
            "Response status is 200",
            this.con.getResponseCode(),
            new IsEqual<>(Integer.parseInt(RsStatus.OK.code()))
        );
        exec(
            "helm", "init",
            "--stable-repo-url",
            String.format(
                "http://%s:%s@%s",
                HelmSliceIT.USER, HelmSliceIT.PSWD,
                hostport
            ),
            "--client-only", "--debug"
        );
        MatcherAssert.assertThat(
            "Chart repository was added",
            this.helmRepoAdd(anonymous, "chartrepo"),
            new IsEqual<>(true)
        );
        MatcherAssert.assertThat(
            "helm repo update is successful",
            exec("helm", "repo", "update"),
            new IsEqual<>(true)
        );
    }

    private String init(final boolean anonymous) {
        this.port = new RandomFreePort().get();
        final String hostport = String.format("host.testcontainers.internal:%d/", this.port);
        this.url = String.format("http://%s", hostport);
        Testcontainers.exposeHostPorts(this.port);
        if (anonymous) {
            this.server = new VertxSliceServer(
                HelmSliceIT.VERTX,
                new LoggingSlice(new HelmSlice(this.storage, this.url)),
                this.port
            );
        } else {
            this.server = new VertxSliceServer(
                HelmSliceIT.VERTX,
                new LoggingSlice(
                    new HelmSlice(
                        this.storage,
                        this.url,
                        new JoinedPermissions(
                            new Permissions.Single(HelmSliceIT.USER, "download"),
                            new Permissions.Single(HelmSliceIT.USER, "upload")
                        ),
                        new Authentication.Single(
                            HelmSliceIT.USER, HelmSliceIT.PSWD
                        )
                    )
                ),
                this.port
            );
        }
        this.cntn = new HelmSliceIT.HelmContainer()
            .withCreateContainerCmdModifier(
                cmd -> cmd.withEntrypoint("/bin/sh").withCmd("-c", "while sleep 3600; do :; done")
            );
        this.server.start();
        this.cntn.start();
        return hostport;
    }

    private boolean helmRepoAdd(final boolean anonymous, final String chartrepo) throws Exception {
        final List<String> cmdlst = new ArrayList<>(
            Arrays.asList("helm", "repo", "add", chartrepo, this.url)
        );
        if (!anonymous) {
            cmdlst.add("--username");
            cmdlst.add(HelmSliceIT.USER);
            cmdlst.add("--password");
            cmdlst.add(HelmSliceIT.PSWD);
        }
        final String[] cmdarr = cmdlst.toArray(new String[0]);
        return this.exec(cmdarr);
    }

    private HttpURLConnection putToLocalhost(final boolean anonymous) throws IOException {
        final HttpURLConnection conn = (HttpURLConnection) new URL(
            String.format(
                "http://localhost:%d/%s", this.port, HelmSliceIT.CHART
            )
        ).openConnection();
        conn.setRequestMethod("PUT");
        conn.setDoOutput(true);
        if (!anonymous) {
            Authenticator.setDefault(
                new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(
                            HelmSliceIT.USER, HelmSliceIT.PSWD.toCharArray()
                        );
                    }
                }
            );
        }
        ByteStreams.copy(
            new ByteArrayInputStream(
                new TestResource(HelmSliceIT.CHART).asBytes()
            ),
            conn.getOutputStream()
        );
        return conn;
    }

    private boolean exec(final String... cmd) throws IOException, InterruptedException {
        final String joined = String.join(" ", cmd);
        LoggerFactory.getLogger(HelmSliceIT.class).info("Executing:\n{}", joined);
        final Container.ExecResult exec = this.cntn.execInContainer(cmd);
        LoggerFactory.getLogger(HelmSliceIT.class)
            .info("STDOUT:\n{}\nSTDERR:\n{}", exec.getStdout(), exec.getStderr());
        final int code = exec.getExitCode();
        if (code != 0) {
            LoggerFactory.getLogger(HelmSliceIT.class)
                .error("'{}' failed with {} code", joined, code);
        }
        return code == 0;
    }

    /**
     * Inner subclass to instantiate Helm container.
     *
     * @since 0.2
     */
    private static class HelmContainer extends
        GenericContainer<HelmSliceIT.HelmContainer> {
        HelmContainer() {
            super("alpine/helm:2.12.1");
        }
    }
}
