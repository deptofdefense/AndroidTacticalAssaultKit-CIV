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
 * Defines the virtual camera that views the scene. This element defines the position of the camera relative to the Earth's surface as well as the viewing direction of the camera. The camera position is defined by longitude, latitude, altitude, and either altitudeMode or gx:altitudeMode. The viewing direction of the camera is defined by heading, tilt, and roll. {@link Camera} can be a child element of any Feature or of {@link NetworkLinkControl}. A parent element cannot contain both a {@link Camera} and a {@link LookAt} at the same time.
 * {@link Camera} provides full six-degrees-of-freedom control over the view, so you can position the Camera in space and then rotate it around the X, Y, and Z axes. Most importantly, you can tilt the camera view so that you're looking above the horizon into the sky.
 * {@link Camera} can also contain a {@link TimePrimitive} (gx:TimeSpan or gx:TimeStamp). Time values in Camera affect historical imagery, sunlight, and the display of time-stamped features. For more information, read Time with AbstractViews in the Time and Animation chapter of the Developer's Guide.
 */
public class Camera extends AbstractView {
    
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

    /** The roll. */
    @Element(required=false)
    private Double roll;

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
     * Gets the roll.
     *
     * @return the roll
     */
    public Double getRoll() {
        return roll;
    }

    /**
     * Sets the roll.
     *
     * @param roll the new roll
     */
    public void setRoll(Double roll) {
        this.roll = roll;
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
