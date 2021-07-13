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

import com.artipie.helm.misc.DateTimeNow;
import java.io.BufferedWriter;
import java.io.IOException;
import org.apache.commons.lang3.StringUtils;

/**
 * Yaml writer with specified indent.
 * @since 0.3
 */
public final class YamlWriter {
    /**
     * Generated tag.
     */
    static final String TAG_GENERATED = "generated:";

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
     * Write data and a new line.
     * @param data Data which should be written
     * @param xindendt How many times the minimum value of indent should be increased?
     * @throws IOException In case of error during writing.
     */
    public void writeLine(final String data, final int xindendt) throws IOException {
        this.writer.write(
            String.format(
                "%s%s",
                StringUtils.repeat(' ', xindendt * this.indnt),
                data
            )
        );
        this.writer.newLine();
    }

    /**
     * Write line if it does not start with tag generated. Otherwise replaces the value
     * of tag `generated` to update time when this index was generated.
     * @param line Parsed line
     * @throws IOException In case of exception during writing
     */
    public void writeAndReplaceTagGenerated(final String line) throws IOException {
        if (line.startsWith(YamlWriter.TAG_GENERATED)) {
            this.writeLine(
                String.format(
                    "%s %s", YamlWriter.TAG_GENERATED, new DateTimeNow().asString()
                ),
                0
            );
        } else {
            this.writeLine(line, 0);
        }
    }
}
