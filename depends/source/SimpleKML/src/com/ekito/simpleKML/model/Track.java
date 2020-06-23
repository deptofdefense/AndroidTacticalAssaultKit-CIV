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

import java.util.List;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Namespace;

/**
 * A track describes how an object moves through the world over a given time period. This feature allows you to create one visible object in Google Earth (either a {@link Point} icon or a {@link Model}) that encodes multiple positions for the same object for multiple times. In Google Earth, the time slider allows the user to move the view through time, which animates the position of the object.
 * 
 * A gx:MultiTrack element is used to collect multiple tracks into one conceptual unit with one associated icon (or {@link Model}) that moves along the track. This feature is useful if you have multiple tracks for the same real-world object. The gx:interpolate Boolean element of a gx:{@link MultiTrack} specifies whether to interpolate between the tracks in a multi-track. If this value is 0, then the point or {@link Model} stops at the end of one track and jumps to the start of the next one. (For example, if you want a single placemark to represent your travels on two days, and your GPS unit was turned off for four hours during this period, you would want to show a discontinuity between the points where the unit was turned off and then on again.) If the value for gx:interpolate is 1, the values between the end of the first track and the beginning of the next track are interpolated so that the track appears as one continuous path.
 */
@Namespace(prefix="gx")
public class Track extends Geometry {

    /** The altitude mode. */
    @Element(required=false)
    private String altitudeMode;

    /** The when. */
    //BYOUNG Bugzilla #2907 - support list of "when"
    @ElementList(entry="when", inline=true, type=String.class, required=false)
    private List<String> when;

    /** The coord. */
    //BYOUNG Bugzilla #2907 - support list of "gx:coord"
    @ElementList(entry="coord", inline=true, type=String.class, required=false)
    @Namespace(prefix="gx")
    private List<String> coord;    

    /** The angles. */
    //BYOUNG Bugzilla #2907 - support list of "gx:angles"
    @ElementList(entry="angles", inline=true, type=String.class, required=false)
    @Namespace(prefix="gx")
    private List<String> angles;

    /** The model. */
    @Element(name="Model",required=false)
    private Model model;
    
    /** The extended data. */
    @Element(name="ExtendedData",required=false)
    private ExtendedData extendedData;

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
     * Gets the when.
     *
     * @return the when
     */
    public List<String> getWhen() {
        return when;
    }

    /**
     * Sets the when.
     *
     * @param when the new when
     */
    public void setWhen(List<String> when) {
        this.when = when;
    }

    /**
     * Gets the coord.
     *
     * @return the coord
     */
    public List<String> getCoord() {
        return coord;
    }

    /**
     * Sets the coord.
     *
     * @param coord the new coord
     */
    public void setCoord(List<String> coord) {
        this.coord = coord;
    }

    /**
     * Gets the angles.
     *
     * @return the angles
     */
    public List<String> getAngles() {
        return angles;
    }

    /**
     * Sets the angles.
     *
     * @param angles the new angles
     */
    public void setAngles(List<String> angles) {
        this.angles = angles;
    }

    /**
     * Gets the model.
     *
     * @return the model
     */
    public Model getModel() {
        return model;
    }

    /**
     * Sets the model.
     *
     * @param model the new model
     */
    public void setModel(Model model) {
        this.model = model;
    }

    /**
     * Gets the extended data.
     *
     * @return the extended data
     */
    public ExtendedData getExtendedData() {
        return extendedData;
    }

    /**
     * Sets the extended data.
     *
     * @param extendedData the new extended data
     */
    public void setExtendedData(ExtendedData extendedData) {
        this.extendedData = extendedData;
    }
}
