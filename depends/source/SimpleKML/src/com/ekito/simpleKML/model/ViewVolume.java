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
 * Defines how much of the current scene is visible. Specifying the field of view is analogous to specifying the lens opening in a physical camera. A small field of view, like a telephoto lens, focuses on a small part of the scene. A large field of view, like a wide-angle lens, focuses on a large part of the scene.
 */
public class ViewVolume {

    /** The left fov. */
    @Element(required=false)
    private Double leftFov;

    /** The right fov. */
    @Element(required=false)
    private Double rightFov;

    /** The bottom fov. */
    @Element(required=false)
    private Double bottomFov;

    /** The top fov. */
    @Element(required=false)
    private Double topFov;

    /** The near. */
    @Element(required=false)
    private Double near;

    /**
     * Gets the left fov.
     *
     * @return the left fov
     */
    public Double getLeftFov() {
        return leftFov;
    }

    /**
     * Sets the left fov.
     *
     * @param leftFov the new left fov
     */
    public void setLeftFov(Double leftFov) {
        this.leftFov = leftFov;
    }

    /**
     * Gets the right fov.
     *
     * @return the right fov
     */
    public Double getRightFov() {
        return rightFov;
    }

    /**
     * Sets the right fov.
     *
     * @param rightFov the new right fov
     */
    public void setRightFov(Double rightFov) {
        this.rightFov = rightFov;
    }

    /**
     * Gets the bottom fov.
     *
     * @return the bottom fov
     */
    public Double getBottomFov() {
        return bottomFov;
    }

    /**
     * Sets the bottom fov.
     *
     * @param bottomFov the new bottom fov
     */
    public void setBottomFov(Double bottomFov) {
        this.bottomFov = bottomFov;
    }

    /**
     * Gets the top fov.
     *
     * @return the top fov
     */
    public Double getTopFov() {
        return topFov;
    }

    /**
     * Sets the top fov.
     *
     * @param topFov the new top fov
     */
    public void setTopFov(Double topFov) {
        this.topFov = topFov;
    }

    /**
     * Gets the near.
     *
     * @return the near
     */
    public Double getNear() {
        return near;
    }

    /**
     * Sets the near.
     *
     * @param near the new near
     */
    public void setNear(Double near) {
        this.near = near;
    }
}
