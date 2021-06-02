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

import com.artipie.helm.metadata.YamlWriter;
import java.io.IOException;

/**
 * Line writer which can process a line which should be written.
 * @since 0.3
 */
public final class LineWriter {
    /**
     * Generated tag.
     */
    static final String TAG_GENERATED = "generated:";

    /**
     * Yaml writer.
     */
    private final YamlWriter writer;

    /**
     * Ctor.
     * @param writer Yaml writer
     */
    public LineWriter(final YamlWriter writer) {
        this.writer = writer;
    }

    /**
     * Write line if it does not start with tag generated. Otherwise replaces the value
     * of tag `generated` to update time when this index was generated.
     * @param line Parsed line
     * @throws IOException In case of exception during writing
     */
    public void writeAndReplaceTagGenerated(final String line) throws IOException {
        if (line.startsWith(LineWriter.TAG_GENERATED)) {
            this.writer.writeLine(
                String.format(
                    "%s %s", LineWriter.TAG_GENERATED, new DateTimeNow().asString()
                ),
                0
            );
        } else {
            this.writer.writeLine(line, 0);
        }
    }
}
