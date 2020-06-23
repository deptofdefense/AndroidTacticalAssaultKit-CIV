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
 * For very large images, you'll need to construct an image pyramid, which is a hierarchical set of images, each of which is an increasingly lower resolution version of the original image. Each image in the pyramid is subdivided into tiles, so that only the portions in view need to be loaded. Google Earth calculates the current viewpoint and loads the tiles that are appropriate to the user's distance from the image. As the viewpoint moves closer to the PhotoOverlay, Google Earth loads higher resolution tiles. Since all the pixels in the original image can't be viewed on the screen at once, this preprocessing allows Google Earth to achieve maximum performance because it loads only the portions of the image that are in view, and only the pixel details that can be discerned by the user at the current viewpoint.
 * When you specify an image pyramid, you also modify the href in the {@link Icon} element to include specifications for which tiles to load.
 */
public class ImagePyramid {

    /** The tile size. */
    @Element(required=false)
    private Integer tileSize;

    /** The max width. */
    @Element(required=false)
    private Integer maxWidth;

    /** The max height. */
    @Element(required=false)
    private Integer maxHeight;

    /** The grid origin. */
    @Element(required=false)
    private String gridOrigin;

    /**
     * Gets the tile size.
     *
     * @return the tile size
     */
    public Integer getTileSize() {
        return tileSize;
    }

    /**
     * Sets the tile size.
     *
     * @param tileSize the new tile size
     */
    public void setTileSize(Integer tileSize) {
        this.tileSize = tileSize;
    }

    /**
     * Gets the max width.
     *
     * @return the max width
     */
    public Integer getMaxWidth() {
        return maxWidth;
    }

    /**
     * Sets the max width.
     *
     * @param maxWidth the new max width
     */
    public void setMaxWidth(Integer maxWidth) {
        this.maxWidth = maxWidth;
    }

    /**
     * Gets the max height.
     *
     * @return the max height
     */
    public Integer getMaxHeight() {
        return maxHeight;
    }

    /**
     * Sets the max height.
     *
     * @param maxHeight the new max height
     */
    public void setMaxHeight(Integer maxHeight) {
        this.maxHeight = maxHeight;
    }

    /**
     * Gets the grid origin.
     *
     * @return the grid origin
     */
    public String getGridOrigin() {
        return gridOrigin;
    }

    /**
     * Sets the grid origin.
     *
     * @param gridOrigin the new grid origin
     */
    public void setGridOrigin(String gridOrigin) {
        this.gridOrigin = gridOrigin;
    }
}
