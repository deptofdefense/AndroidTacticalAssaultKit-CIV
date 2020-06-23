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
package com.ekito.simpleKML;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;

import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.stream.InputNode;
import org.simpleframework.xml.stream.OutputNode;
import org.simpleframework.xml.transform.Matcher;
import org.simpleframework.xml.transform.Transform;

import com.ekito.simpleKML.model.Coordinate;
import com.ekito.simpleKML.model.Coordinates;
import com.ekito.simpleKML.model.Kml;

/**
 * The Serializer interface is used to represent objects that can serialize and deserialize objects from and to KML. This exposes several read method that can read and write from and to multiple sources.
 */
public class Serializer {
    
    org.simpleframework.xml.Serializer serializer;
    
    /**
     * Instantiates a new serializer.
     */
    public Serializer() {
        serializer = new Persister(new KMLMatcher());
    }

    /**
     * This read method will read the contents of the XML document from the provided source and populate the object with the values deserialized.
     *
     * @param source the source
     * @return the kml
     * @throws Exception the exception
     */
    public Kml read(File source) throws Exception {
        return serializer.read(Kml.class, source, false);
    }
    
    /**
     * This read method will read the contents of the XML document from the provided source and populate the object with the values deserialized.
     *
     * @param source the source
     * @return the kml
     * @throws Exception the exception
     */
    public Kml read(InputNode source) throws Exception {
        return serializer.read(Kml.class, source, false);
    }
    
    /**
     * This read method will read the contents of the XML document from the provided source and populate the object with the values deserialized.
     *
     * @param source the source
     * @return the kml
     * @throws Exception the exception
     */
    public Kml read(InputStream source) throws Exception {
        return serializer.read(Kml.class, source, false);
    }
    
    /**
     * This read method will read the contents of the XML document from the provided source and populate the object with the values deserialized.
     *
     * @param source the source
     * @return the kml
     * @throws Exception the exception
     */
    public Kml read(Reader source) throws Exception {
        return serializer.read(Kml.class, source, false);
    }
    
    /**
     * This read method will read the contents of the XML document from the provided source and populate the object with the values deserialized.
     *
     * @param source the source
     * @return the kml
     * @throws Exception the exception
     */
    public Kml read(String source) throws Exception {
        return serializer.read(Kml.class, source, false);
    }
    
    /**
     * This write method will traverse the provided object checking for field annotations in order to compose the KML data.
     *
     * @param source the source
     * @param out the out
     * @return the file
     * @throws Exception the exception
     */
    public File write(Kml source, File out) throws Exception {
        serializer.write(source, out);
        return out;
    }
    
    /**
     * This write method will traverse the provided object checking for field annotations in order to compose the KML data.
     *
     * @param source the source
     * @param out the out
     * @return the output node
     * @throws Exception the exception
     */
    public OutputNode write(Kml source, OutputNode out) throws Exception {
        serializer.write(source, out);
        return out;
    }
    
    /**
     * This write method will traverse the provided object checking for field annotations in order to compose the KML data.
     *
     * @param source the source
     * @param out the out
     * @return the output stream
     * @throws Exception the exception
     */
    public OutputStream write(Kml source, OutputStream out) throws Exception {
        serializer.write(source, out);
        return out;
    }
    
    /**
     * This write method will traverse the provided object checking for field annotations in order to compose the KML data.
     *
     * @param source the source
     * @param out the out
     * @return the writer
     * @throws Exception the exception
     */
    public Writer write(Kml source, Writer out) throws Exception {
        serializer.write(source, out);
        return out;
    }

    /**
     * The Class KMLMatcher.
     */
    private class KMLMatcher implements Matcher {

        /* (non-Javadoc)
         * @see org.simpleframework.xml.transform.Matcher#match(java.lang.Class)
         */
        @SuppressWarnings("rawtypes")
        @Override
        public Transform<?> match(Class type) throws Exception {
            if (type.equals(Coordinate.class))            return new CoordinateTransformer();
            else if (type.equals(Coordinates.class))    return new CoordinatesTransformer();
            return null;
        }
        
        /**
         * The Class CoordinateTransformer.
         */
        public class CoordinateTransformer implements Transform<Coordinate> {
            
            /* (non-Javadoc)
             * @see org.simpleframework.xml.transform.Transform#read(java.lang.String)
             */
            @Override
            public Coordinate read(String value) throws Exception {
                return new Coordinate(value);
            }
            
            /* (non-Javadoc)
             * @see org.simpleframework.xml.transform.Transform#write(java.lang.Object)
             */
            @Override
            public String write(Coordinate value) throws Exception {
                return value.toString();
            }
        }

        /**
         * The Class CoordinatesTransformer.
         */
        public class CoordinatesTransformer implements Transform<Coordinates> {
            
            /* (non-Javadoc)
             * @see org.simpleframework.xml.transform.Transform#read(java.lang.String)
             */
            @Override
            public Coordinates read(String value) throws Exception {
                return new Coordinates(value);
            }
            
            /* (non-Javadoc)
             * @see org.simpleframework.xml.transform.Transform#write(java.lang.Object)
             */
            @Override
            public String write(Coordinates value) throws Exception {
                return value.toString();
            }
        }
    }
}
