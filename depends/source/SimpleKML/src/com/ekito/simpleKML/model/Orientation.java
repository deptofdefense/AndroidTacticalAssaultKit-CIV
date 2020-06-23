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
 * Describes rotation of a 3D model's coordinate system to position the object in Google Earth.
 */
public class Orientation {

    /** The heading. */
    @Element
    private Float heading;

    /** The tilt. */
    @Element
    private Float tilt;

    /** The roll. */
    @Element
    private Float roll;

    /**
     * Gets the heading.
     *
     * @return the heading
     */
    public Float getHeading() {
        return heading;
    }

    /**
     * Sets the heading.
     *
     * @param heading the new heading
     */
    public void setHeading(Float heading) {
        this.heading = heading;
    }

    /**
     * Gets the tilt.
     *
     * @return the tilt
     */
    public Float getTilt() {
        return tilt;
    }

    /**
     * Sets the tilt.
     *
     * @param tilt the new tilt
     */
    public void setTilt(Float tilt) {
        this.tilt = tilt;
    }

    /**
     * Gets the roll.
     *
     * @return the roll
     */
    public Float getRoll() {
        return roll;
    }

    /**
     * Sets the roll.
     *
     * @param roll the new roll
     */
    public void setRoll(Float roll) {
        this.roll = roll;
    }
}
