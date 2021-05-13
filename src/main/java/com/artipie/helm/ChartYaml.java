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

import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/**
 * The Chart.yaml file.
 *
 * @since 0.2
 */
@SuppressWarnings("unchecked")
public final class ChartYaml {

    /**
     * Mapping for fields from index.yaml file.
     */
    private final Map<String, Object> mapping;

    /**
     * Ctor.
     * @param yaml Yaml for entry of chart (one specific version)
     */
    public ChartYaml(final String yaml) {
        this((Map<String, Object>) new Yaml().load(yaml));
    }

    /**
     * Ctor.
     * @param mapfromyaml Mapping of fields for chart (one specific version)
     */
    public ChartYaml(final Map<String, Object> mapfromyaml) {
        this.mapping = mapfromyaml;
    }

    /**
     * Obtain a name of the chart.
     * @return Name of the chart.
     */
    public String name() {
        return (String) this.mapping.get("name");
    }

    /**
     * Obtain a version of the chart.
     * @return Version of the chart.
     */
    public String version() {
        return (String) this.mapping.get("version");
    }

    /**
     * Return Chart.yaml fields.
     * @return The fields.
     */
    public Map<String, Object> fields() {
        return this.mapping;
    }

    /**
     * Obtain a list of urls of the chart.
     * @return Urls of the chart.
     */
    public List<String> urls() {
        return (List<String>) this.mapping.get("urls");
    }
}
