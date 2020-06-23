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
 * A Style defines an addressable style group that can be referenced by StyleMaps and Features. Styles affect how Geometry is presented in the 3D viewer and how Features appear in the Places panel of the List view. Shared styles are collected in a {@link Document} and must have an id defined for them so that they can be referenced by the individual Features that use them.
 */
public class Style extends StyleSelector {

    /** The icon style. */
    @Element(name="IconStyle",required=false)
    private IconStyle iconStyle;
    
    /** The label style. */
    @Element(name="LabelStyle",required=false)
    private LabelStyle labelStyle;
    
    /** The line style. */
    @Element(name="LineStyle",required=false)
    private LineStyle lineStyle;

    /** The poly style. */
    @Element(name="PolyStyle",required=false)
    private PolyStyle polyStyle;

    /** The balloon style. */
    @Element(name="BalloonStyle",required=false)
    private BalloonStyle balloonStyle;

    /** The list style. */
    @Element(name="ListStyle",required=false)
    private ListStyle listStyle;

    /**
     * Gets the icon style.
     *
     * @return the icon style
     */
    public IconStyle getIconStyle() {
        return iconStyle;
    }

    /**
     * Sets the icon style.
     *
     * @param iconStyle the new icon style
     */
    public void setIconStyle(IconStyle iconStyle) {
        this.iconStyle = iconStyle;
    }

    /**
     * Gets the label style.
     *
     * @return the label style
     */
    public LabelStyle getLabelStyle() {
        return labelStyle;
    }

    /**
     * Sets the label style.
     *
     * @param labelStyle the new label style
     */
    public void setLabelStyle(LabelStyle labelStyle) {
        this.labelStyle = labelStyle;
    }

    /**
     * Gets the line style.
     *
     * @return the line style
     */
    public LineStyle getLineStyle() {
        return lineStyle;
    }

    /**
     * Sets the line style.
     *
     * @param lineStyle the new line style
     */
    public void setLineStyle(LineStyle lineStyle) {
        this.lineStyle = lineStyle;
    }

    /**
     * Gets the poly style.
     *
     * @return the poly style
     */
    public PolyStyle getPolyStyle() {
        return polyStyle;
    }

    /**
     * Sets the poly style.
     *
     * @param polyStyle the new poly style
     */
    public void setPolyStyle(PolyStyle polyStyle) {
        this.polyStyle = polyStyle;
    }

    /**
     * Gets the balloon style.
     *
     * @return the balloon style
     */
    public BalloonStyle getBalloonStyle() {
        return balloonStyle;
    }

    /**
     * Sets the balloon style.
     *
     * @param balloonStyle the new balloon style
     */
    public void setBalloonStyle(BalloonStyle balloonStyle) {
        this.balloonStyle = balloonStyle;
    }

    /**
     * Gets the list style.
     *
     * @return the list style
     */
    public ListStyle getListStyle() {
        return listStyle;
    }

    /**
     * Sets the list style.
     *
     * @param listStyle the new list style
     */
    public void setListStyle(ListStyle listStyle) {
        this.listStyle = listStyle;
    }
}
