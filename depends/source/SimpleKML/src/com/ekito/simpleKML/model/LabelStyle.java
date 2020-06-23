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

/**
 * Specifies how the name of a Feature is drawn in the 3D viewer. A custom color, color mode, and scale for the label (name) can be specified.
 */
public class LabelStyle extends ColorStyle {

    /** The scale. */
    @Element(required=false)
    private Float scale;

    /**
     * Gets the scale.
     *
     * @return the scale
     */
    public Float getScale() {
        return scale;
    }

    /**
     * Sets the scale.
     *
     * @param scale the new scale
     */
    public void setScale(Float scale) {
        this.scale = scale;
    }

}
