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

/**
 * The {@link ExtendedData} element offers three techniques for adding custom data to a KML {@link Feature} ({@link NetworkLink}, {@link Placemark}, {@link GroundOverlay}, {@link PhotoOverlay}, {@link ScreenOverlay}, {@link Document}, {@link Folder}). These techniques are
 * 
 * <ul>
 * <li>Adding untyped data/value pairs using the {@link Data} element (basic)</li>
 * <li>Declaring new typed fields using the {@link Schema} element and then instancing them using the {@link SchemaData} element (advanced)</li>
 * <li>Referring to XML elements defined in other namespaces by referencing the external namespace within the KML file (basic)</li>
 * </ul>
 * These techniques can be combined within a single KML file or Feature for different pieces of data.
 */
public class ExtendedData {
    
    /** The schema data list. */
    @ElementList(entry="SchemaData", inline=true, required=false)
    private List<SchemaData> schemaDataList;

    /** The data list. */
    @ElementList(entry="Data", inline=true, required=false)
    private List<Data> dataList;
    
    // TODO find a way to include <namespace_prefix:other>

    /**
     * Gets the schema data list.
     *
     * @return the schema data list
     */
    public List<SchemaData> getSchemaDataList() {
        return schemaDataList;
    }

    /**
     * Sets the schema data list.
     *
     * @param schemaDataList the new schema data list
     */
    public void setSchemaDataList(List<SchemaData> schemaDataList) {
        this.schemaDataList = schemaDataList;
    }

    /**
     * Gets the data list.
     *
     * @return the data list
     */
    public List<Data> getDataList() {
        return dataList;
    }

    /**
     * Sets the data list.
     *
     * @param dataList the new data list
     */
    public void setDataList(List<Data> dataList) {
        this.dataList = dataList;
    }
}
