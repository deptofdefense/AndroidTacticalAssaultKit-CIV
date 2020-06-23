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
import org.simpleframework.xml.Namespace;

/**
 * Allows nonrectangular quadrilateral ground overlays.
 * Specifies the coordinates of the four corner points of a quadrilateral defining the overlay area. Exactly four coordinate tuples have to be provided, each consisting of floating point values for longitude and latitude. Insert a space between tuples. Do not include spaces within a tuple. The coordinates must be specified in counter-clockwise order with the first coordinate corresponding to the lower-left corner of the overlayed image. The shape described by these corners must be convex.
 * If a third value is inserted into any tuple (representing altitude) it will be ignored. Altitude is set using altitude and altitudeMode (or gx:altitudeMode) extending {@link GroundOverlay}. Allowed altitude modes are absolute, clampToGround, and clampToSeaFloor.
 */
@Namespace(prefix="gx")
public class LatLonQuad {

    /** The coordinates. */
    @Element(required=false)
    private Coordinates coordinates;

    /**
     * Gets the coordinates.
     *
     * @return the coordinates
     */
    public Coordinates getCoordinates() {
        return coordinates;
    }

    /**
     * Sets the coordinates.
     *
     * @param coordinates the new coordinates
     */
    public void setCoordinates(Coordinates coordinates) {
        this.coordinates = coordinates;
    }
}
