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
import org.simpleframework.xml.Namespace;

/**
 * BYOUNG Bugzilla #2916
 * https://developers.google.com/kml/documentation/kmlreference#gxtrack
 * http://googlegeodevelopers.blogspot.com/2010/07/making-tracks-new-kml-extensions-in.html 
 * 
 * <ExtendedData>
        <SchemaData schemaUrl="#schema">
          <gx:SimpleArrayData name="cadence">
            <gx:value>86</gx:value>
            <gx:value>103</gx:value>
            <gx:value>108</gx:value>
            <gx:value>113</gx:value>
            <gx:value>113</gx:value>
            <gx:value>113</gx:value>
            <gx:value>113</gx:value>
          </gx:SimpleArrayData>
          ...
 **/
@Namespace(prefix="gx")
public class SimpleArrayData{

    /** The name. */
    @Attribute(required=true)
    private String name;

    /** The simple array. */
    @ElementList(entry="value", inline=true, type=String.class, required=true)
    @Namespace(prefix="gx")
    private List<String> value;    

    /**
     * Gets name.
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
     * Gets the when.
     *
     * @return the value
     */
    public List<String> getValue() {
        return value;
    }

    /**
     * Sets the value.
     *
     * @param when the new value
     */
    public void setValue(List<String> value) {
        this.value = value;
    }
}
