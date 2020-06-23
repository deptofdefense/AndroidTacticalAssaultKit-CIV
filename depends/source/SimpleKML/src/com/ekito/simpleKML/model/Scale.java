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
 * Scales a model along the x, y, and z axes in the model's coordinate space.
 */
public class Scale {

    /** The x. */
    @Element
    private Float x;

    /** The y. */
    @Element
    private Float y;

    /** The z. */
    @Element
    private Float z;

    /**
     * Gets the x.
     *
     * @return the x
     */
    public Float getX() {
        return x;
    }

    /**
     * Sets the x.
     *
     * @param x the new x
     */
    public void setX(Float x) {
        this.x = x;
    }

    /**
     * Gets the y.
     *
     * @return the y
     */
    public Float getY() {
        return y;
    }

    /**
     * Sets the y.
     *
     * @param y the new y
     */
    public void setY(Float y) {
        this.y = y;
    }

    /**
     * Gets the z.
     *
     * @return the z
     */
    public Float getZ() {
        return z;
    }

    /**
     * Sets the z.
     *
     * @param z the new z
     */
    public void setZ(Float z) {
        this.z = z;
    }
}
