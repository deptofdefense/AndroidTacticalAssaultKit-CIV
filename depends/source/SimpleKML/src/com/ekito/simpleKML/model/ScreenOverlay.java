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
 * This element draws an image overlay fixed to the screen. Sample uses for ScreenOverlays are compasses, logos, and heads-up displays. ScreenOverlay sizing is determined by the size element. Positioning of the overlay is handled by mapping a point in the image specified by overlayXY to a point on the screen specified by screenXY. Then the image is rotated by rotation degrees about a point relative to the screen specified by rotationXY.
 * 
 * The href child of {@link Icon} specifies the image to be used as the overlay. This file can be either on a local file system or on a web server. If this element is omitted or contains no href, a rectangle is drawn using the color and size defined by the screen overlay.
 */
public class ScreenOverlay extends Overlay {

    /** The overlay xy. */
    @Element(required=false)
    private HotSpot overlayXY;

    /** The screen xy. */
    @Element(required=false)
    private HotSpot screenXY;

    /** The rotation xy. */
    @Element(required=false)
    private HotSpot rotationXY;

    /** The size. */
    @Element(required=false)
    private HotSpot size;

    /** The rotation. */
    @Element(required=false)
    private Float rotation;

    /**
     * Gets the overlay xy.
     *
     * @return the overlay xy
     */
    public HotSpot getOverlayXY() {
        return overlayXY;
    }

    /**
     * Sets the overlay xy.
     *
     * @param overlayXY the new overlay xy
     */
    public void setOverlayXY(HotSpot overlayXY) {
        this.overlayXY = overlayXY;
    }

    /**
     * Gets the screen xy.
     *
     * @return the screen xy
     */
    public HotSpot getScreenXY() {
        return screenXY;
    }

    /**
     * Sets the screen xy.
     *
     * @param screenXY the new screen xy
     */
    public void setScreenXY(HotSpot screenXY) {
        this.screenXY = screenXY;
    }

    /**
     * Gets the rotation xy.
     *
     * @return the rotation xy
     */
    public HotSpot getRotationXY() {
        return rotationXY;
    }

    /**
     * Sets the rotation xy.
     *
     * @param rotationXY the new rotation xy
     */
    public void setRotationXY(HotSpot rotationXY) {
        this.rotationXY = rotationXY;
    }

    /**
     * Gets the size.
     *
     * @return the size
     */
    public HotSpot getSize() {
        return size;
    }

    /**
     * Sets the size.
     *
     * @param size the new size
     */
    public void setSize(HotSpot size) {
        this.size = size;
    }

    /**
     * Gets the rotation.
     *
     * @return the rotation
     */
    public Float getRotation() {
        return rotation;
    }

    /**
     * Sets the rotation.
     *
     * @param rotation the new rotation
     */
    public void setRotation(Float rotation) {
        this.rotation = rotation;
    }
}
