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
import org.simpleframework.xml.ElementUnion;

/**
 * Base class for {@link Change}, {@link Create} and {@link Delete}
 */
public abstract class UpdateProcess {

    /** The feature. */
    @ElementUnion({
        @Element(name="Document", type=Document.class, required=false),
        @Element(name="Folder", type=Folder.class, required=false),
        @Element(name="NetworkLink", type=NetworkLink.class, required=false),
        @Element(name="Placemark", type=Placemark.class, required=false),
        @Element(name="GroundOverlay", type=GroundOverlay.class, required=false),
        @Element(name="PhotoOverlay", type=PhotoOverlay.class, required=false),
        @Element(name="ScreenOverlay", type=ScreenOverlay.class, required=false)
    })
    private Feature feature;

    /**
     * Gets the feature.
     *
     * @return the feature
     */
    public Feature getFeature() {
        return feature;
    }

    /**
     * Sets the feature.
     *
     * @param feature the new feature
     */
    public void setFeature(Feature feature) {
        this.feature = feature;
    }
}
