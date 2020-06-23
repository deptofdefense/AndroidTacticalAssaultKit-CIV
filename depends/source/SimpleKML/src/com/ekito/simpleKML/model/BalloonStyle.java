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
 * Specifies how the description balloon for placemarks is drawn. The bgColor, if specified, is used as the background color of the balloon. See {@link Feature} for a diagram illustrating how the default description balloon appears in Google Earth.
 */
public class BalloonStyle extends ColorStyle {

    /** The bg color. */
    @Element(required=false)
    private String bgColor;

    /** The text color. */
    @Element(required=false)
    private String textColor;

    /** The text. */
    @Element(required=false)
    private String text;

    /** The display mode. */
    @Element(required=false)
    private String displayMode;

    /**
     * Gets the bg color.
     *
     * @return the bg color
     */
    public String getBgColor() {
        return bgColor;
    }

    /**
     * Sets the bg color.
     *
     * @param bgColor the new bg color
     */
    public void setBgColor(String bgColor) {
        this.bgColor = bgColor;
    }

    /**
     * Gets the text color.
     *
     * @return the text color
     */
    public String getTextColor() {
        return textColor;
    }

    /**
     * Sets the text color.
     *
     * @param textColor the new text color
     */
    public void setTextColor(String textColor) {
        this.textColor = textColor;
    }

    /**
     * Gets the text.
     *
     * @return the text
     */
    public String getText() {
        return text;
    }

    /**
     * Sets the text.
     *
     * @param text the new text
     */
    public void setText(String text) {
        this.text = text;
    }

    /**
     * Gets the display mode.
     *
     * @return the display mode
     */
    public String getDisplayMode() {
        return displayMode;
    }

    /**
     * Sets the display mode.
     *
     * @param displayMode the new display mode
     */
    public void setDisplayMode(String displayMode) {
        this.displayMode = displayMode;
    }
}
