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
 * Defines a virtual camera that is associated with any element derived from {@link Feature}. The LookAt element positions the "camera" in relation to the object that is being viewed. In Google Earth, the view "flies to" this LookAt viewpoint when the user double-clicks an item in the Places panel or double-clicks an icon in the 3D viewer.
 */
public class LookAt extends AbstractView {

    /** The longitude. */
    @Element(required=false)
    private Double longitude;

    /** The latitude. */
    @Element(required=false)
    private Double latitude;

    /** The altitude. */
    @Element(required=false)
    private Double altitude;

    /** The heading. */
    @Element(required=false)
    private Double heading;

    /** The tilt. */
    @Element(required=false)
    private Double tilt;

    /** The range. */
    @Element(required=false)
    private Double range;

    /** The altitude mode. */
    @Element(required=false)
    private String altitudeMode;

    /**
     * Gets the longitude.
     *
     * @return the longitude
     */
    public Double getLongitude() {
        return longitude;
    }

    /**
     * Sets the longitude.
     *
     * @param longitude the new longitude
     */
    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    /**
     * Gets the latitude.
     *
     * @return the latitude
     */
    public Double getLatitude() {
        return latitude;
    }

    /**
     * Sets the latitude.
     *
     * @param latitude the new latitude
     */
    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

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
     * Gets the heading.
     *
     * @return the heading
     */
    public Double getHeading() {
        return heading;
    }

    /**
     * Sets the heading.
     *
     * @param heading the new heading
     */
    public void setHeading(Double heading) {
        this.heading = heading;
    }

    /**
     * Gets the tilt.
     *
     * @return the tilt
     */
    public Double getTilt() {
        return tilt;
    }

    /**
     * Sets the tilt.
     *
     * @param tilt the new tilt
     */
    public void setTilt(Double tilt) {
        this.tilt = tilt;
    }

    /**
     * Gets the range.
     *
     * @return the range
     */
    public Double getRange() {
        return range;
    }

    /**
     * Sets the range.
     *
     * @param range the new range
     */
    public void setRange(Double range) {
        this.range = range;
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
