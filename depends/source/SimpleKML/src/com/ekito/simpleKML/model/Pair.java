/**
 * Copyright 2012 Ekito - http://www.ekito.fr/
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.ekito.simpleKML.model;

import org.simpleframework.xml.Element;

/**
 * Defines a key/value pair that maps a mode (normal or highlight) to the predefined styleUrl.
 */
public class Pair extends Object {

    /** The key. */
    @Element(required=false)
    private String key;

    /** The style url. */
    @Element(required=false)
    private String styleUrl;

    /** The style. */
    @Element(name="Style", required=false)
    private Style style;

    /**
     * Gets the key.
     *
     * @return the key
     */
    public String getKey() {
        return key;
    }

    /**
     * Sets the key.
     *
     * @param key the new key
     */
    public void setKey(String key) {
        this.key = key;
    }

    /**
     * Gets the style url.
     *
     * @return the style url
     */
    public String getStyleUrl() {
        return styleUrl;
    }

    /**
     * Sets the style url.
     *
     * @param styleUrl the new style url
     */
    public void setStyleUrl(String styleUrl) {
        this.styleUrl = styleUrl;
    }

    /**
     * Gets the style.
     *
     * @return the style
     */
    public Style getStyle() {
        return style;
    }

    /**
     * Sets the style.
     *
     * @param style the new style
     */
    public void setStyle(Style style) {
        this.style = style;
    }
}
