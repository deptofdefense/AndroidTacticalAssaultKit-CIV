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

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.ElementList;

/**
 * Specifies a custom KML schema that is used to add custom data to KML Features. The "id" attribute is required and must be unique within the KML file. {@link Schema} is always a child of {@link Document}.
 */
public class Schema extends Object {

    /** The name. */
    @Attribute(required=false)
    private String name;
    
    /** The simple field list. */
    @ElementList(entry="SimpleField", inline=true, required=false)
    private List<SimpleField> simpleFieldList;

    /**
     * Gets the name.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name.
     *
     * @param name the new name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the simple field list.
     *
     * @return the simple field list
     */
    public List<SimpleField> getSimpleFieldList() {
        return simpleFieldList;
    }

    /**
     * Sets the simple field list.
     *
     * @param simpleFieldList the new simple field list
     */
    public void setSimpleFieldList(List<SimpleField> simpleFieldList) {
        this.simpleFieldList = simpleFieldList;
    }
}
