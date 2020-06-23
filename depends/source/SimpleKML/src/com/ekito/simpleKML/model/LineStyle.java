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
 * Specifies the drawing style (color, color mode, and line width) for all line geometry. Line geometry includes the outlines of outlined polygons and the extruded "tether" of {@link Placemark} icons (if extrusion is enabled).
 */
public class LineStyle extends ColorStyle {

    /** The width. */
    @Element(required=false)
    private Float width;

    /** The outer color. */
    @Element(required=false)
    @Namespace(prefix="gx")
    private String outerColor;

    /** The outer width. */
    @Element(required=false)
    @Namespace(prefix="gx")
    private Float outerWidth;

    /** The physical width. */
    @Element(required=false)
    @Namespace(prefix="gx")
    private Float physicalWidth;

    /** The label visibility. */
    @Element(required=false)
    @Namespace(prefix="gx")
    private String labelVisibility;      // boolean 0/1 false/true

    /**
     * Gets the width.
     *
     * @return the width
     */
    public Float getWidth() {
        return width;
    }

    /**
     * Sets the width.
     *
     * @param width the new width
     */
    public void setWidth(Float width) {
        this.width = width;
    }

    /**
     * Gets the outer color.
     *
     * @return the outer color
     */
    public String getOuterColor() {
        return outerColor;
    }

    /**
     * Sets the outer color.
     *
     * @param outerColor the new outer color
     */
    public void setOuterColor(String outerColor) {
        this.outerColor = outerColor;
    }

    /**
     * Gets the outer width.
     *
     * @return the outer width
     */
    public Float getOuterWidth() {
        return outerWidth;
    }

    /**
     * Sets the outer width.
     *
     * @param outerWidth the new outer width
     */
    public void setOuterWidth(Float outerWidth) {
        this.outerWidth = outerWidth;
    }

    /**
     * Gets the physical width.
     *
     * @return the physical width
     */
    public Float getPhysicalWidth() {
        return physicalWidth;
    }

    /**
     * Sets the physical width.
     *
     * @param physicalWidth the new physical width
     */
    public void setPhysicalWidth(Float physicalWidth) {
        this.physicalWidth = physicalWidth;
    }

    /**
     * Gets the label visibility.
     *
     * @return the label visibility
     */
    public Boolean getLabelVisibility() {
        if (labelVisibility != null) 
            return BooleanUtil.valueOf(labelVisibility);
        else 
            return Boolean.FALSE;
    }

    /**
     * Sets the label visibility.
     *
     * @param labelVisibility the new label visibility
     */
    public void setLabelVisibility(Boolean labelVisibility) {
        if (labelVisibility != null) 
            this.labelVisibility = labelVisibility.toString();
        else 
            this.labelVisibility = null;
    }

    /**
     * Sets the label visibility.
     *
     * @param labelVisibility the new label visibility
     */
    public void setLabelVisibility(Integer labelVisibility) {
        if (labelVisibility != null) 
            this.labelVisibility = Boolean.toString(labelVisibility == 1);
        else 
            this.labelVisibility = null;
    }

}
