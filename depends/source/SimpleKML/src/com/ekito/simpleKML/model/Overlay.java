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
 * This is an abstract element and cannot be used directly in a KML file. {@link Overlay} is the base type for image overlays drawn on the planet surface or on the screen. {@link Icon} specifies the image to use and can be configured to reload images based on a timer or by camera changes. This element also includes specifications for stacking order of multiple overlays and for adding color and transparency values to the base image.
 */
public class Overlay extends Feature {
    
    /** The color. */
    @Element(required=false)
    private String color;

    /** The draw order. */
    @Element(required=false)
    private Integer drawOrder;
    
    /** The icon. */
    @Element(name="Icon", required=false)
    private Icon icon;

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
     * Gets the draw order.
     *
     * @return the draw order
     */
    public Integer getDrawOrder() {
        return drawOrder;
    }

    /**
     * Sets the draw order.
     *
     * @param drawOrder the new draw order
     */
    public void setDrawOrder(Integer drawOrder) {
        this.drawOrder = drawOrder;
    }

    /**
     * Gets the icon.
     *
     * @return the icon
     */
    public Icon getIcon() {
        return icon;
    }

    /**
     * Sets the icon.
     *
     * @param icon the new icon
     */
    public void setIcon(Icon icon) {
        this.icon = icon;
    }
}
