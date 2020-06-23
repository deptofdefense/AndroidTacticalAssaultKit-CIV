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
 * A geographic location defined by longitude, latitude, and (optional) altitude. When a {@link Point} is contained by a {@link Placemark}, the point itself determines the position of the {@link Placemark}'s name and icon. When a {@link Point} is extruded, it is connected to the ground with a line. This "tether" uses the current {@link LineStyle}.
 */
public class Point extends Geometry {

    /** The extrude. */
    @Element(required=false)
    private String extrude;      // boolean 0/1 false/true

    /** The altitude mode. */
    @Element(required=false)
    private String altitudeMode;
    
    /** The coordinates. */
    @Element(required=false)
    private Coordinate coordinates;

    /**
     * Gets the extrude.
     *
     * @return the extrude
     */
    public Boolean getExtrude() {
        if (extrude != null) 
             return BooleanUtil.valueOf(extrude);
        else 
             return Boolean.FALSE;
    }

    /**
     * Sets the extrude.
     *
     * @param extrude the new extrude
     */
    public void setExtrude(Boolean extrude) {
        if (extrude != null)
            this.extrude = extrude.toString();
        else 
            this.extrude = null;
    }

    /**
     * Sets the extrude.
     *
     * @param extrude the new extrude
     */
    public void setExtrude(Integer extrude) {
        if (extrude != null)
             this.extrude = Boolean.toString(extrude == 1);
        else 
            this.extrude = null;
        
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

    /**
     * Gets the coordinates.
     *
     * @return the coordinates
     */
    public Coordinate getCoordinates() {
        return coordinates;
    }

    /**
     * Sets the coordinates.
     *
     * @param coordinates the new coordinates
     */
    public void setCoordinates(Coordinate coordinates) {
        this.coordinates = coordinates;
    }
}
