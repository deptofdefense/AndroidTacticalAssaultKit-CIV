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
 * A bounding box that describes an area of interest defined by geographic coordinates and altitudes.
 */
public class LatLonAltBox {

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

    /** The min altitude. */
    @Element(required=false)
    private Double minAltitude;

    /** The max altitude. */
    @Element(required=false)
    private Double maxAltitude;

    /** The altitude mode. */
    @Element(required=false)
    private String altitudeMode;

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
     * Gets the min altitude.
     *
     * @return the min altitude
     */
    public Double getMinAltitude() {
        return minAltitude;
    }

    /**
     * Sets the min altitude.
     *
     * @param minAltitude the new min altitude
     */
    public void setMinAltitude(Double minAltitude) {
        this.minAltitude = minAltitude;
    }

    /**
     * Gets the max altitude.
     *
     * @return the max altitude
     */
    public Double getMaxAltitude() {
        return maxAltitude;
    }

    /**
     * Sets the max altitude.
     *
     * @param maxAltitude the new max altitude
     */
    public void setMaxAltitude(Double maxAltitude) {
        this.maxAltitude = maxAltitude;
    }

    /**
     * Gets the altitude mode.
     *
     * @return the altitude mode
     */
    public String getAltitudeMode() {
        return altitudeMode;
    }

    /**
     * Sets the altitude mode.
     *
     * @param altitudeMode the new altitude mode
     */
    public void setAltitudeMode(String altitudeMode) {
        this.altitudeMode = altitudeMode;
    }
}
