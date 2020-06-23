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

import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.ElementListUnion;

/**
 * A container for zero or more geometry primitives associated with the same feature.
 */
public class MultiGeometry extends Geometry {

    /** The geometry list. */
    @ElementListUnion({
        @ElementList(entry="Point", inline=true, type=Point.class, required=false),
        @ElementList(entry="LineString", inline=true, type=LineString.class, required=false),
        @ElementList(entry="LinearRing", inline=true, type=LinearRing.class, required=false),
        @ElementList(entry="Polygon", inline=true, type=Polygon.class, required=false),
        @ElementList(entry="MultiGeometry", inline=true, type=MultiGeometry.class, required=false),
        @ElementList(entry="MultiTrack", inline=true, type=MultiTrack.class, required=false),
        @ElementList(entry="Model", inline=true, type=Model.class, required=false),
        @ElementList(entry="Track", inline=true, type=Track.class, required=false)
    })
    private List<Geometry> geometryList;

    /**
     * Gets the geometry list.
     *
     * @return the geometry list
     */
    public List<Geometry> getGeometryList() {
        return geometryList;
    }

    /**
     * Sets the geometry list.
     *
     * @param geometryList the new geometry list
     */
    public void setGeometryList(List<Geometry> geometryList) {
        this.geometryList = geometryList;
    }
}
