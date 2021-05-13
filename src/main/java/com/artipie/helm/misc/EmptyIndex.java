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
package com.artipie.helm.misc;

import com.artipie.asto.Content;
import java.nio.charset.StandardCharsets;

/**
 * Provides empty index file.
 * @since 0.3
 */
public final class EmptyIndex {
    /**
     * Content of index file.
     */
    private final String index;

    /**
     * Ctor.
     */
    public EmptyIndex() {
        this.index = String.format(
            "apiVersion: v1\ngenerated: %s\nentries:\n",
            new DateTimeNow().asString()
        );
    }

    /**
     * Index file as content.
     * @return Index file as content.
     */
    public Content asContent() {
        return new Content.From(this.index.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Index file as string.
     * @return Index file as string.
     */
    public String asString() {
        return this.index;
    }
}
