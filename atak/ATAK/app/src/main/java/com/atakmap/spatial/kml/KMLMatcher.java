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

package com.atakmap.spatial.kml;

import com.ekito.simpleKML.model.Coordinate;
import com.ekito.simpleKML.model.Coordinates;

import org.simpleframework.xml.transform.Matcher;
import org.simpleframework.xml.transform.Transform;

/**
 * The Class KMLMatcher. Note this class is reproduced w/out modification here from
 * com.ekito.simpleKML.Serializer.KMLMatcher in order to avoid having to modify the simple-kml.jar,
 * yet still be able to use this class to serialize partial KML to string (e.g. just a Feature or
 * just a Style) as opposed to the whole KML which is supported by com.ekito.simpleKML.Serializer
 */
public class KMLMatcher implements Matcher {

    /*
     * (non-Javadoc)
     * @see org.simpleframework.xml.transform.Matcher#match(java.lang.Class)
     */
    @SuppressWarnings("rawtypes")
    @Override
    public Transform<?> match(Class type) {
        if (type.equals(Coordinate.class))
            return new CoordinateTransformer();
        else if (type.equals(Coordinates.class))
            return new CoordinatesTransformer();
        return null;
    }

    /**
     * The Class CoordinateTransformer.
     */
    public static class CoordinateTransformer implements Transform<Coordinate> {

        /*
         * (non-Javadoc)
         * @see org.simpleframework.xml.transform.Transform#read(java.lang.String)
         */
        @Override
        public Coordinate read(String value) {
            return new Coordinate(value);
        }

        /*
         * (non-Javadoc)
         * @see org.simpleframework.xml.transform.Transform#write(java.lang.Object)
         */
        @Override
        public String write(Coordinate value) {
            return value.toString();
        }
    }

    /**
     * The Class CoordinatesTransformer.
     */
    public static class CoordinatesTransformer implements
            Transform<Coordinates> {

        /*
         * (non-Javadoc)
         * @see org.simpleframework.xml.transform.Transform#read(java.lang.String)
         */
        @Override
        public Coordinates read(String value) {
            return new Coordinates(value);
        }

        /*
         * (non-Javadoc)
         * @see org.simpleframework.xml.transform.Transform#write(java.lang.Object)
         */
        @Override
        public String write(Coordinates value) {
            return value.toString();
        }
    }
}
