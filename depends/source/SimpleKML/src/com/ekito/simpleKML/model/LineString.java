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
import org.simpleframework.xml.Namespace;

/**
 * Defines a connected set of line segments. Use {@link LineStyle} to specify the color, color mode, and width of the line. When a {@link LineString} is extruded, the line is extended to the ground, forming a polygon that looks somewhat like a wall or fence. For extruded LineStrings, the line itself uses the current {@link LineStyle}, and the extrusion uses the current {@link PolyStyle}.
 */
public class LineString extends Geometry {
    
    /** The altitude offset. */
    @Element(required=false)
    @Namespace(prefix="gx")
    private Integer altitudeOffset;

    /** The extrude. */
    @Element(required=false)
    private String extrude;         // boolean 0/1 false/true

    /** The tessellate. */
    @Element(required=false)
    private String tessellate;      // boolean 0/1 false/true

    /** The altitude mode. */
    @Element(required=false)
    private String altitudeMode;

    /** The draw order. */
    @Element(required=false)
    @Namespace(prefix="gx")
    private Integer drawOrder;
    
    /** The coordinates. */
    @Element(required=false)
    private Coordinates coordinates;

    /**
     * Gets the altitude offset.
     *
     * @return the altitude offset
     */
    public Integer getAltitudeOffset() {
        return altitudeOffset;
    }

    /**
     * Sets the altitude offset.
     *
     * @param altitudeOffset the new altitude offset
     */
    public void setAltitudeOffset(Integer altitudeOffset) {
        this.altitudeOffset = altitudeOffset;
    }

    /**
     * Gets the extrude.
     *
     * @return the extrude
     */
    public Boolean getExtrude() {
        if (extrude != null)
             return BooleanUtil.valueOf(extrude);
        else
             return Boolean.FALSE;
    }

    /**
     * Sets the extrude.
     *
     * @param extrude the new extrude
     */
    public void setExtrude(Boolean extrude) {
        if (extrude != null) 
             this.extrude = extrude.toString();
        else 
             this.extrude = null;
    }

    /**
     * Sets the extrude.
     *
     * @param extrude the new extrude
     */
    public void setExtrude(Integer extrude) {
        if (extrude != null) 
             this.extrude = Boolean.toString(extrude == 1);
        else 
            this.extrude = null;
    }

    /**
     * Gets the tessellate.
     *
     * @return the tessellate
     */
    public Boolean getTessellate() {
        if (tessellate != null)
            return BooleanUtil.valueOf(tessellate);
        else 
            return Boolean.FALSE;
    }

    /**
     * Sets the tessellate.
     *
     * @param tessellate the new tessellate
     */
    public void setTessellate(Boolean tessellate) {
        if (tessellate != null) 
             this.tessellate = tessellate.toString();
        else 
             this.tessellate = null;
    }

    /**
     * Sets the tessellate.
     *
     * @param tessellate the new tessellate
     */
    public void setTessellate(Integer tessellate) {
        if (tessellate != null) 
            this.tessellate = Boolean.toString(tessellate == 1);
        else 
            this.tessellate = null;
    }

    /**

    /**
     * Gets the altitude mode.
     *
     * @return the altitude mode
     */
    public String getAltitudeMode() {
        return altitudeMode;
    }

    /**
     * Sets the altitude mode.
     *
     * @param altitudeMode the new altitude mode
     */
    public void setAltitudeMode(String altitudeMode) {
        this.altitudeMode = altitudeMode;
    }

    /**
     * Gets the draw order.
     *
     * @return the draw order
     */
    public Integer getDrawOrder() {
        return drawOrder;
    }

    /**
     * Sets the draw order.
     *
     * @param drawOrder the new draw order
     */
    public void setDrawOrder(Integer drawOrder) {
        this.drawOrder = drawOrder;
    }

    /**
     * Gets the coordinates.
     *
     * @return the coordinates
     */
    public Coordinates getCoordinates() {
        return coordinates;
    }

    /**
     * Sets the coordinates.
     *
     * @param coordinates the new coordinates
     */
    public void setCoordinates(Coordinates coordinates) {
        this.coordinates = coordinates;
    }
}
