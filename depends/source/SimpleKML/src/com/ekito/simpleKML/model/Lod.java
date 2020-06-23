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
 * Lod is an abbreviation for Level of Detail. Lod describes the size of the projected region on the screen that is required in order for the region to be considered "active." Also specifies the size of the pixel ramp used for fading in (from transparent to opaque) and fading out (from opaque to transparent). See diagram below for a visual representation of these parameters.
 */
public class Lod {

    /** The min lod pixels. */
    @Element(required=false)
    private Float minLodPixels;

    /** The max lod pixels. */
    @Element(required=false)
    private Float maxLodPixels;

    /** The min fade extent. */
    @Element(required=false)
    private Float minFadeExtent;

    /** The max fade extent. */
    @Element(required=false)
    private Float maxFadeExtent;

    /**
     * Gets the min lod pixels.
     *
     * @return the min lod pixels
     */
    public Float getMinLodPixels() {
        return minLodPixels;
    }

    /**
     * Sets the min lod pixels.
     *
     * @param minLodPixels the new min lod pixels
     */
    public void setMinLodPixels(Float minLodPixels) {
        this.minLodPixels = minLodPixels;
    }

    /**
     * Gets the max lod pixels.
     *
     * @return the max lod pixels
     */
    public Float getMaxLodPixels() {
        return maxLodPixels;
    }

    /**
     * Sets the max lod pixels.
     *
     * @param maxLodPixels the new max lod pixels
     */
    public void setMaxLodPixels(Float maxLodPixels) {
        this.maxLodPixels = maxLodPixels;
    }

    /**
     * Gets the min fade extent.
     *
     * @return the min fade extent
     */
    public Float getMinFadeExtent() {
        return minFadeExtent;
    }

    /**
     * Sets the min fade extent.
     *
     * @param minFadeExtent the new min fade extent
     */
    public void setMinFadeExtent(Float minFadeExtent) {
        this.minFadeExtent = minFadeExtent;
    }

    /**
     * Gets the max fade extent.
     *
     * @return the max fade extent
     */
    public Float getMaxFadeExtent() {
        return maxFadeExtent;
    }

    /**
     * Sets the max fade extent.
     *
     * @param maxFadeExtent the new max fade extent
     */
    public void setMaxFadeExtent(Float maxFadeExtent) {
        this.maxFadeExtent = maxFadeExtent;
    }
}
