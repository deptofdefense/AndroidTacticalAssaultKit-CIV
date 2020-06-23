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
 * A multi-track element is used to combine multiple track elements into a single conceptual unit. For example, suppose you collect GPS data for a day's bike ride that includes several rest stops and a stop for lunch. Because of the interruptions in time, one bike ride might appear as four different tracks when the times and positions are plotted. Grouping these gx:{@link Track} elements into one gx:MultiTrack container causes them to be displayed in Google Earth as sections of a single path. When the icon reaches the end of one segment, it moves to the beginning of the next segment. The gx:interpolate element specifies whether to stop at the end of one track and jump immediately to the start of the next one, or to interpolate the missing values between the two tracks.
 */
@Namespace(prefix="gx")
public class MultiTrack extends Geometry {

    /** The altitude mode. */
    @Element(required=false)
    private String altitudeMode;

    /** The interpolate. */
    @Element(required=false)
    @Namespace(prefix="gx")
    private Integer interpolate;
    
    /** The track list. */
    @ElementList(entry="Track",inline=true,required=false)
    private List<Track> trackList;

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
     * Gets the interpolate.
     *
     * @return the interpolate
     */
    public Integer getInterpolate() {
        return interpolate;
    }

    /**
     * Sets the interpolate.
     *
     * @param interpolate the new interpolate
     */
    public void setInterpolate(Integer interpolate) {
        this.interpolate = interpolate;
    }

    /**
     * Gets the track list.
     *
     * @return the track list
     */
    public List<Track> getTrackList() {
        return trackList;
    }

    /**
     * Sets the track list.
     *
     * @param trackList the new track list
     */
    public void setTrackList(List<Track> trackList) {
        this.trackList = trackList;
    }
}
