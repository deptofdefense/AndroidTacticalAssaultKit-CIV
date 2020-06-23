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
 * This element draws an image overlay draped onto the terrain. The href child of {@link Icon} specifies the image to be used as the overlay. This file can be either on a local file system or on a web server. If this element is omitted or contains no href, a rectangle is drawn using the color and LatLonBox bounds defined by the ground overlay.
 */
public class GroundOverlay extends Overlay {

    /** The altitude. */
    @Element(required=false)
    private Double altitude;
    
    /** The altitude mode. */
    @Element(required=false)
    private String altitudeMode;

    /** The lat lon box. */
    @Element(name="LatLonBox",required=false)
    private LatLonBox latLonBox;

    /** The lat lon quad. */
    @Element(name="LatLonQuad",required=false)
    private LatLonQuad latLonQuad;

    /**
     * Gets the altitude.
     *
     * @return the altitude
     */
    public Double getAltitude() {
        return altitude;
    }

    /**
     * Sets the altitude.
     *
     * @param altitude the new altitude
     */
    public void setAltitude(Double altitude) {
        this.altitude = altitude;
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
     * Gets the lat lon box.
     *
     * @return the lat lon box
     */
    public LatLonBox getLatLonBox() {
        return latLonBox;
    }

    /**
     * Sets the lat lon box.
     *
     * @param latLonBox the new lat lon box
     */
    public void setLatLonBox(LatLonBox latLonBox) {
        this.latLonBox = latLonBox;
    }

    /**
     * Gets the lat lon quad.
     *
     * @return the lat lon quad
     */
    public LatLonQuad getLatLonQuad() {
        return latLonQuad;
    }

    /**
     * Sets the lat lon quad.
     *
     * @param latLonQuad the new lat lon quad
     */
    public void setLatLonQuad(LatLonQuad latLonQuad) {
        this.latLonQuad = latLonQuad;
    }
}
