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

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.ElementListUnion;

/**
 * Specifies an addition, change, or deletion to KML data that has already been loaded using the specified URL. The targetHref specifies the .kml or .kmz file whose data (within Google Earth) is to be modified. {@link Update} is always contained in a {@link NetworkLinkControl}. Furthermore, the file containing the {@link NetworkLinkControl} must have been loaded by a {@link NetworkLink}.
 */
public class Update {

    /** The target href. */
    @Element(required=true)
    private String targetHref;

    /** The crud list. */
    @ElementListUnion({
        @ElementList(entry="Change", inline=true, type=Change.class),
        @ElementList(entry="Create", inline=true, type=Create.class),
        @ElementList(entry="Delete", inline=true, type=Delete.class),
    })
    private List<UpdateProcess> crudList;

    /**
     * Gets the target href.
     *
     * @return the target href
     */
    public String getTargetHref() {
        return targetHref;
    }

    /**
     * Sets the target href.
     *
     * @param targetHref the new target href
     */
    public void setTargetHref(String targetHref) {
        this.targetHref = targetHref;
    }

    /**
     * Gets the crud list.
     *
     * @return the crud list
     */
    public List<UpdateProcess> getCrudList() {
        return crudList;
    }

    /**
     * Sets the crud list.
     *
     * @param crudList the new crud list
     */
    public void setCrudList(List<UpdateProcess> crudList) {
        this.crudList = crudList;
    }
}
