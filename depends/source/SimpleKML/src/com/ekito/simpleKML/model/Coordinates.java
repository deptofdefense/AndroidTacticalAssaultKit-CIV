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

import java.util.ArrayList;

/**
 * A list of Tuples consisting of floating point values for longitude, latitude, and altitude (in that order). Longitude and latitude values are in degrees, where
 * longitude ≥ −180 and <= 180
 * latitude ≥ −90 and ≤ 90
 * altitude values (optional) are in meters above sea level
 */
public class Coordinates {

    /** The list. */
    private ArrayList<Coordinate> list;

    /**
     * Instantiates a new coordinates.
     *
     * @param raw the raw
     */
    public Coordinates(String raw) {
        this.list = new ArrayList<Coordinate>();
        String[] rawPoints = raw.replaceFirst("^\\s+", "").split("\\s+");
        String[] coords;
        for (String rawPoint : rawPoints) {
            coords = rawPoint.split("(?<!\\\\),");
            Double altitude = coords.length>2? Double.parseDouble(coords[2]) : null;
            this.list.add(new Coordinate(Double.parseDouble(coords[0]),Double.parseDouble(coords[1]),altitude));
        }
    }

    /**
     * Gets the list.
     *
     * @return the list
     */
    public ArrayList<Coordinate> getList() {
        return list;
    }

    /**
     * Sets the list.
     *
     * @param list the new list
     */
    public void setList(ArrayList<Coordinate> list) {
        this.list = list;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {

        String result = "";
        for (Coordinate coord : list) {
            result += coord.toString();
        }

        return result;
    }
}
