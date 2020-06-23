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
 * Specifies where the top, bottom, right, and left sides of a bounding box for the ground overlay are aligned.
 */
public class LatLonBox {

    /** The north. */
    @Element(required=false)
    private String north;

    /** The south. */
    @Element(required=false)
    private String south;

    /** The east. */
    @Element(required=false)
    private String east;

    /** The west. */
    @Element(required=false)
    private String west;

    /** The rotation. */
    @Element(required=false)
    private Float rotation;

    /**
     * Gets the north.
     *
     * @return the north
     */
    public String getNorth() {
        return north;
    }

    /**
     * Sets the north.
     *
     * @param north the new north
     */
    public void setNorth(String north) {
        this.north = north;
    }

    /**
     * Gets the south.
     *
     * @return the south
     */
    public String getSouth() {
        return south;
    }

    /**
     * Sets the south.
     *
     * @param south the new south
     */
    public void setSouth(String south) {
        this.south = south;
    }

    /**
     * Gets the east.
     *
     * @return the east
     */
    public String getEast() {
        return east;
    }

    /**
     * Sets the east.
     *
     * @param east the new east
     */
    public void setEast(String east) {
        this.east = east;
    }

    /**
     * Gets the west.
     *
     * @return the west
     */
    public String getWest() {
        return west;
    }

    /**
     * Sets the west.
     *
     * @param west the new west
     */
    public void setWest(String west) {
        this.west = west;
    }

    /**
     * Gets the rotation.
     *
     * @return the rotation
     */
    public Float getRotation() {
        return rotation;
    }

    /**
     * Sets the rotation.
     *
     * @param rotation the new rotation
     */
    public void setRotation(Float rotation) {
        this.rotation = rotation;
    }
}
