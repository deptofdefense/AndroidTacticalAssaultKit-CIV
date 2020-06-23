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
 * A region contains a bounding box ({@link LatLonAltBox}) that describes an area of interest defined by geographic coordinates and altitudes. In addition, a Region contains an LOD (level of detail) extent ({@link Lod}) that defines a validity range of the associated Region in terms of projected screen size. A Region is said to be "active" when the bounding box is within the user's view and the LOD requirements are met. Objects associated with a Region are drawn only when the Region is active. When the <viewRefreshMode> is onRegion, the Link or Icon is loaded only when the Region is active. See the "Topics in KML" page on Regions for more details. In a Container or {@link NetworkLink} hierarchy, this calculation uses the Region that is the closest ancestor in the hierarchy.
 */
public class Region extends Object {
    
    /** The lat lon alt box. */
    @Element(name="LatLonAltBox",required=false)
    private LatLonAltBox latLonAltBox;

    /** The lod. */
    @Element(name="Lod",required=false)
    private Lod lod;

    /**
     * Gets the lat lon alt box.
     *
     * @return the lat lon alt box
     */
    public LatLonAltBox getLatLonAltBox() {
        return latLonAltBox;
    }

    /**
     * Sets the lat lon alt box.
     *
     * @param latLonAltBox the new lat lon alt box
     */
    public void setLatLonAltBox(LatLonAltBox latLonAltBox) {
        this.latLonAltBox = latLonAltBox;
    }

    /**
     * Gets the lod.
     *
     * @return the lod
     */
    public Lod getLod() {
        return lod;
    }

    /**
     * Sets the lod.
     *
     * @param lod the new lod
     */
    public void setLod(Lod lod) {
        this.lod = lod;
    }
}
