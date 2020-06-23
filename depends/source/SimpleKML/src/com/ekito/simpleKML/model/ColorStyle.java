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
 * This is an abstract element and cannot be used directly in a KML file. It provides elements for specifying the color and color mode of extended style types.
 */
public abstract class ColorStyle extends Object {

    /** The color. */
    @Element(required=false)
    private String color;
    
    /** The color mode. */
    @Element(required=false)
    private String colorMode;

    /**
     * Gets the color.
     *
     * @return the color
     */
    public String getColor() {
        return color;
    }

    /**
     * Sets the color.
     *
     * @param color the new color
     */
    public void setColor(String color) {
        this.color = color;
    }

    /**
     * Gets the color mode.
     *
     * @return the color mode
     */
    public String getColorMode() {
        return colorMode;
    }

    /**
     * Sets the color mode.
     *
     * @param colorMode the new color mode
     */
    public void setColorMode(String colorMode) {
        this.colorMode = colorMode;
    }
}
