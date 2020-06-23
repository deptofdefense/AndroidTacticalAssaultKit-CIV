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
 * The {@link PhotoOverlay} element allows you to geographically locate a photograph on the Earth and to specify viewing parameters for this PhotoOverlay. The PhotoOverlay can be a simple 2D rectangle, a partial or full cylinder, or a sphere (for spherical panoramas). The overlay is placed at the specified location and oriented toward the viewpoint.
 * 
 * Because {@link PhotoOverlay} is derived from {@link Feature}, it can contain one of the two elements derived from {@link AbstractView} either {@link Camera} or {@link LookAt}. The {@link Camera} (or {@link LookAt}) specifies a viewpoint and a viewing direction (also referred to as a view vector). The PhotoOverlay is positioned in relation to the viewpoint. Specifically, the plane of a 2D rectangular image is orthogonal (at right angles to) the view vector. The normal of this plane�that is, its front, which is the part with the photo�is oriented toward the viewpoint.
 * 
 * The URL for the PhotoOverlay image is specified in the {@link Icon} tag, which is inherited from {@link Overlay}. The {@link Icon} tag must contain an href element that specifies the image file to use for the PhotoOverlay. In the case of a very large image, the href is a special URL that indexes into a pyramid of images of varying resolutions (see {@link ImagePyramid}).
 */
public class PhotoOverlay extends Overlay {

    /** The rotation. */
    @Element(required=false)
    private Float rotation;

    /** The view volume. */
    @Element(name="ViewVolume",required=false)
    private ViewVolume viewVolume;

    /** The image pyramid. */
    @Element(name="ImagePyramid",required=false)
    private ImagePyramid imagePyramid;

    /** The point. */
    @Element(name="Point",required=false)
    private Point point;

    /** The shape. */
    @Element(required=false)
    private String shape;

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

    /**
     * Gets the view volume.
     *
     * @return the view volume
     */
    public ViewVolume getViewVolume() {
        return viewVolume;
    }

    /**
     * Sets the view volume.
     *
     * @param viewVolume the new view volume
     */
    public void setViewVolume(ViewVolume viewVolume) {
        this.viewVolume = viewVolume;
    }

    /**
     * Gets the image pyramid.
     *
     * @return the image pyramid
     */
    public ImagePyramid getImagePyramid() {
        return imagePyramid;
    }

    /**
     * Sets the image pyramid.
     *
     * @param imagePyramid the new image pyramid
     */
    public void setImagePyramid(ImagePyramid imagePyramid) {
        this.imagePyramid = imagePyramid;
    }

    /**
     * Gets the point.
     *
     * @return the point
     */
    public Point getPoint() {
        return point;
    }

    /**
     * Sets the point.
     *
     * @param point the new point
     */
    public void setPoint(Point point) {
        this.point = point;
    }

    /**
     * Gets the shape.
     *
     * @return the shape
     */
    public String getShape() {
        return shape;
    }

    /**
     * Sets the shape.
     *
     * @param shape the new shape
     */
    public void setShape(String shape) {
        this.shape = shape;
    }
}
