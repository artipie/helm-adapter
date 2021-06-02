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

/**
 * Encapsulates parsed chart name for validation.
 * @since 0.3
 */
public final class ParsedChartName {
    /**
     * Entries.
     */
    private static final String ENTRS = "entries:";

    /**
     * Chart name.
     */
    private final String name;

    /**
     * Ctor.
     * @param name Parsed from file with breaks chart name
     */
    public ParsedChartName(final String name) {
        this.name = name;
    }

    /**
     * Validates chart name.
     * @return True if parsed chart name is valid, false otherwise.
     */
    public boolean valid() {
        final String trimmed = this.name.trim();
        return trimmed.endsWith(":")
            && !trimmed.equals(ParsedChartName.ENTRS)
            && trimmed.charAt(0) != '-';
    }
}
