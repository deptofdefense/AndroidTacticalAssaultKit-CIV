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
 * Specifies how icons for point Placemarks are drawn, both in the Places panel and in the 3D viewer of Google Earth. The {@link Icon} element specifies the icon image. The scale element specifies the x, y scaling of the icon. The color specified in the color element of {@link IconStyle} is blended with the color of the {@link Icon}.
 */
public class IconStyle extends ColorStyle {

    /** The scale. */
    @Element(required=false)
    private Float scale;

    /** The heading. */
    @Element(required=false)
    private Float heading;

    /** The icon. */
    @Element(name="Icon", required=false)
    private Icon icon;

    /** The hot spot. */
    @Element(required=false)
    private HotSpot hotSpot;

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

    /**
     * Gets the heading.
     *
     * @return the heading
     */
    public Float getHeading() {
        return heading;
    }

    /**
     * Sets the heading.
     *
     * @param heading the new heading
     */
    public void setHeading(Float heading) {
        this.heading = heading;
    }

    /**
     * Gets the icon.
     *
     * @return the icon
     */
    public Icon getIcon() {
        return icon;
    }

    /**
     * Sets the icon.
     *
     * @param icon the new icon
     */
    public void setIcon(Icon icon) {
        this.icon = icon;
    }

    /**
     * Gets the hot spot.
     *
     * @return the hot spot
     */
    public HotSpot getHotSpot() {
        return hotSpot;
    }

    /**
     * Sets the hot spot.
     *
     * @param hotSpot the new hot spot
     */
    public void setHotSpot(HotSpot hotSpot) {
        this.hotSpot = hotSpot;
    }
}
