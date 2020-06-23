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

import org.simpleframework.xml.Attribute;

/**
 * Specifies the position within the Icon that is "anchored" to the {@link Point} specified in the {@link Placemark}. The x and y values can be specified in three different ways: as pixels ("pixels"), as fractions of the icon ("fraction"), or as inset pixels ("insetPixels"), which is an offset in pixels from the upper right corner of the icon. The x and y positions can be specified in different ways�for example, x can be in pixels and y can be a fraction. The origin of the coordinate system is in the lower left corner of the icon.
 */
public class HotSpot {

    /** The x. */
    @Attribute(required=false)
    private Double x;

    /** The y. */
    @Attribute(required=false)
    private Double y;

    /** The xunits. */
    @Attribute(required=false)
    private String xunits;

    /** The yunits. */
    @Attribute(required=false)
    private String yunits;

    /**
     * Gets the x.
     *
     * @return the x
     */
    public Double getX() {
        return x;
    }

    /**
     * Sets the x.
     *
     * @param x the new x
     */
    public void setX(Double x) {
        this.x = x;
    }

    /**
     * Gets the y.
     *
     * @return the y
     */
    public Double getY() {
        return y;
    }

    /**
     * Sets the y.
     *
     * @param y the new y
     */
    public void setY(Double y) {
        this.y = y;
    }

    /**
     * Gets the xunits.
     *
     * @return the xunits
     */
    public String getXunits() {
        return xunits;
    }

    /**
     * Sets the xunits.
     *
     * @param xunits the new xunits
     */
    public void setXunits(String xunits) {
        this.xunits = xunits;
    }

    /**
     * Gets the yunits.
     *
     * @return the yunits
     */
    public String getYunits() {
        return yunits;
    }

    /**
     * Sets the yunits.
     *
     * @param yunits the new yunits
     */
    public void setYunits(String yunits) {
        this.yunits = yunits;
    }
}
