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

// TODO: Auto-generated Javadoc
/**
 * A Document is a container for features and styles. This element is required if your KML file uses shared styles. It is recommended that you use shared styles, which require the following steps:
 * 
 * <ol>
 *         <li>Define all Styles in a Document. Assign a unique ID to each Style.</li>
 *         <li>Within a given Feature or StyleMap, reference the Style's ID using a styleUrl element.</li>
 * </ol>
 * Note that shared styles are not inherited by the Features in the Document.
 */
public class Document extends Feature {

    /** The feature list. */
    @ElementListUnion({
        @ElementList(entry="Document", inline=true, type=Document.class, required=false),
        @ElementList(entry="Folder", inline=true, type=Folder.class, required=false),
        @ElementList(entry="NetworkLink", inline=true, type=NetworkLink.class, required=false),
        @ElementList(entry="Placemark", inline=true, type=Placemark.class, required=false),
        @ElementList(entry="GroundOverlay", inline=true, type=GroundOverlay.class, required=false),
        @ElementList(entry="PhotoOverlay", inline=true, type=PhotoOverlay.class, required=false),
        @ElementList(entry="ScreenOverlay", inline=true, type=ScreenOverlay.class, required=false)
    })
    private List<Feature> featureList;
    
    /** The schema list. */
    @ElementList(entry="Schema", inline=true, required=false)
    private List<Schema> schemaList;

    /**
     * Gets the feature list.
     *
     * @return the feature list
     */
    public List<Feature> getFeatureList() {
        return featureList;
    }

    /**
     * Sets the feature list.
     *
     * @param featureList the new feature list
     */
    public void setFeatureList(List<Feature> featureList) {
        this.featureList = featureList;
    }

    /**
     * Gets the schema list.
     *
     * @return the schema list
     */
    public List<Schema> getSchemaList() {
        return schemaList;
    }

    /**
     * Sets the schema list.
     *
     * @param schemaList the new schema list
     */
    public void setSchemaList(List<Schema> schemaList) {
        this.schemaList = schemaList;
    }

}
