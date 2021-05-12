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

import java.io.BufferedWriter;
import java.io.IOException;
import org.apache.commons.lang3.StringUtils;

/**
 * Yaml writer with specified indent.
 * @since 0.3
 */
public final class YamlWriter {
    /**
     * Required indent.
     */
    private final int indnt;

    /**
     * Buffered writer.
     */
    private final BufferedWriter writer;

    /**
     * Ctor.
     * @param writer Writer
     * @param indent Required indent in index file
     */
    public YamlWriter(final BufferedWriter writer, final int indent) {
        this.writer = writer;
        this.indnt = indent;
    }

    /**
     * Obtains indent.
     * @return Indent.
     */
    public int indent() {
        return this.indnt;
    }

    /**
     * Write data.
     * @param data Data which should be written
     * @throws IOException In case of error during writing.
     */
    public void write(final String data) throws IOException {
        this.writer.write(data);
    }

    /**
     * Write data with spaces at the beginning.
     * @param data Data which should be written
     * @param space Number of spaces at the beginning
     * @throws IOException In case of error during writing.
     */
    public void writeWithSpace(final String data, final int space) throws IOException {
        this.write(String.format("%s%s", StringUtils.repeat(' ', space), data));
    }

    /**
     * Write new line.
     * @throws IOException In case of error during writing.
     */
    public void newLine() throws IOException {
        this.writer.newLine();
    }
}
