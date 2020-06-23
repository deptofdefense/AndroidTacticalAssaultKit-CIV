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
 * Specifies 0 or more {@link Alias} elements, each of which is a mapping for the texture file path from the original Collada file to the KML or KMZ file that contains the {@link Model}. This element allows you to move and rename texture files without having to update the original Collada file that references those textures. One {@link ResourceMap} element can contain multiple mappings from different (source) Collada files into the same (target) KMZ file.
 */
public class ResourceMap {
    
    /** The alias. */
    @ElementList(entry="Alias",inline=true,required=false)
    private List<Alias> alias;

    /**
     * Gets the alias.
     *
     * @return the alias
     */
    public List<Alias> getAlias() {
        return alias;
    }

    /**
     * Sets the alias.
     *
     * @param alias the new alias
     */
    public void setAlias(List<Alias> alias) {
        this.alias = alias;
    }
}
