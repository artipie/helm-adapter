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

import com.artipie.asto.Remaining;
import com.artipie.asto.Storage;
import com.artipie.http.Response;
import com.artipie.http.Slice;
import com.artipie.http.async.AsyncResponse;
import com.artipie.http.rs.RsStatus;
import com.artipie.http.rs.RsWithStatus;
import com.artipie.http.rs.StandardRs;
import io.reactivex.Flowable;
import io.reactivex.Single;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import org.reactivestreams.Publisher;

/**
 * A Slice which accept archived charts, save them into a storage and trigger index.yml reindexing.
 * @since 0.2
 * @checkstyle MethodBodyCommentsCheck (500 lines)
 */
@SuppressWarnings({"PMD.UnusedPrivateField", "PMD.SingularField", "PMD.UnusedFormalParameter"})
public final class PushChartSlice implements Slice {

    /**
     * The Storage.
     */
    private final Storage storage;

    /**
     * The base path for urls.
     */
    private final String base;

    /**
     * Ctor.
     * @param storage The storage.
     * @param base The base path of urls field.
     */
    public PushChartSlice(final Storage storage, final String base) {
        this.storage = storage;
        this.base = base;
    }

    @Override
    public Response response(final String line,
        final Iterable<Map.Entry<String, String>> headers,
        final Publisher<ByteBuffer> body) {
        return new AsyncResponse(this.response(body));
    }

    /**
     * Convert buffers into a byte array.
     * @param bufs The list of buffers.
     * @return The byte array.
     */
    static byte[] bufsToByteArr(final List<ByteBuffer> bufs) {
        final Integer size = bufs.stream()
            .map(Buffer::remaining)
            .reduce(Integer::sum)
            .orElse(0);
        final byte[] bytes = new byte[size];
        int pos = 0;
        for (final ByteBuffer buf : bufs) {
            final byte[] tocopy = new Remaining(buf).bytes();
            System.arraycopy(tocopy, 0, bytes, pos, tocopy.length);
            pos += tocopy.length;
        }
        return bytes;
    }

    /**
     * The reactive response.
     * @param body The body
     * @return The response
     */
    private Single<Response> response(final Publisher<ByteBuffer> body) {
        return memory(body).flatMapCompletable(
            arch -> arch.save(this.storage).flatMapCompletable(
                key -> new IndexYaml(this.storage, this.base).update(arch)
            )
        ).andThen(Single.just(new RsWithStatus(StandardRs.EMPTY, RsStatus.OK)));
    }

    /**
     * Loads bytes into the memory.
     * @param body The body.
     * @return Bytes in a single byte array
     */
    private static Single<TgzArchive> memory(final Publisher<ByteBuffer> body) {
        return Flowable.fromPublisher(body)
            .toList()
            .map(bufs -> new TgzArchive(bufsToByteArr(bufs)));
    }
}
