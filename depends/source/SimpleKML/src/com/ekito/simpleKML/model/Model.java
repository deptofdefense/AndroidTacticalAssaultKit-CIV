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
 * A 3D object described in a COLLADA file (referenced in the {@link Link} tag). COLLADA files have a .dae file extension. Models are created in their own coordinate space and then located, positioned, and scaled in Google Earth. See the "Topics in KML" page on Models for more detail.
 *
 * Google Earth supports the COLLADA common profile, with the following exceptions:
 * <ul>
 * <li>Google Earth supports only triangles and lines as primitive types. The maximum number of triangles allowed is 21845.</li>
 * <li>Google Earth does not support animation or skinning.</li>
 * <li>Google Earth does not support external geometry references.</li>
 * </ul>
 */
public class Model extends Geometry {

    /** The altitude mode. */
    @Element(required=false)
    private String altitudeMode;

    /** The location. */
    @Element(name="Location",required=false)
    private Location location;

    /** The orientation. */
    @Element(name="Orientation",required=false)
    private Location orientation;

    /** The scale. */
    @Element(name="Scale",required=false)
    private Location scale;

    /** The link. */
    @Element(name="Link",required=false)
    private Location link;

    /** The resource map. */
    @Element(name="ResourceMap",required=false)
    private ResourceMap resourceMap;

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
     * Gets the location.
     *
     * @return the location
     */
    public Location getLocation() {
        return location;
    }

    /**
     * Sets the location.
     *
     * @param location the new location
     */
    public void setLocation(Location location) {
        this.location = location;
    }

    /**
     * Gets the orientation.
     *
     * @return the orientation
     */
    public Location getOrientation() {
        return orientation;
    }

    /**
     * Sets the orientation.
     *
     * @param orientation the new orientation
     */
    public void setOrientation(Location orientation) {
        this.orientation = orientation;
    }

    /**
     * Gets the scale.
     *
     * @return the scale
     */
    public Location getScale() {
        return scale;
    }

    /**
     * Sets the scale.
     *
     * @param scale the new scale
     */
    public void setScale(Location scale) {
        this.scale = scale;
    }

    /**
     * Gets the link.
     *
     * @return the link
     */
    public Location getLink() {
        return link;
    }

    /**
     * Sets the link.
     *
     * @param link the new link
     */
    public void setLink(Location link) {
        this.link = link;
    }

    /**
     * Gets the resource map.
     *
     * @return the resource map
     */
    public ResourceMap getResourceMap() {
        return resourceMap;
    }

    /**
     * Sets the resource map.
     *
     * @param resourceMap the new resource map
     */
    public void setResourceMap(ResourceMap resourceMap) {
        this.resourceMap = resourceMap;
    }
}
