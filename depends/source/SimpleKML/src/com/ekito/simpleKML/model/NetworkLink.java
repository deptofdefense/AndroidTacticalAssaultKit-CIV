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
 * References a KML file or KMZ archive on a local or remote network. Use the {@link Link} element to specify the location of the KML file. Within that element, you can define the refresh options for updating the file, based on time and camera change. NetworkLinks can be used in combination with Regions to handle very large datasets efficiently.
 */
public class NetworkLink extends Feature {
    
    /** The refresh visibility. */
    @Element(required=false)
    private String refreshVisibility;     // boolean 0/1 false/true

    /** The fly to view. */
    @Element(required=false)
    private String flyToView;             // boolean 0/1 false/true

    /** The link. */
    @Element(name="Link",required=false)
    private Link link;

    /**
     * Gets the refresh visibility.
     *
     * @return the refresh visibility
     */
    public Boolean getRefreshVisibility() {
        if (refreshVisibility != null) 
            return BooleanUtil.valueOf(refreshVisibility);
        else 
            return Boolean.FALSE;
    }

    /**
     * Sets the refresh visibility.
     *
     * @param refreshVisibility the new refresh visibility
     */
    public void setRefreshVisibility(Boolean refreshVisibility) {
        if (refreshVisibility != null) 
              this.refreshVisibility = refreshVisibility.toString();
        else 
              this.refreshVisibility = null;
    }

    /**
     * Sets the refresh visibility.
     *
     * @param refreshVisibility the new refresh visibility
     */
    public void setRefreshVisibility(Integer refreshVisibility) {
        if (refreshVisibility != null) 
            this.refreshVisibility = Boolean.toString(refreshVisibility == 1);
        else 
              this.refreshVisibility = null;
    }


    /**
     * Gets the fly to view.
     *
     * @return the fly to view
     */
    public Boolean getFlyToView() {
        if (flyToView != null)
            return BooleanUtil.valueOf(flyToView);
        else 
            return Boolean.FALSE;
    }

    /**
     * Sets the fly to view.
     *
     * @param flyToView the new fly to view
     */
    public void setFlyToView(Boolean flyToView) {
        if (flyToView != null) 
             this.flyToView = flyToView.toString();
        else 
             this.flyToView = null;
    }

    /**
     * Sets the fly to view.
     *
     * @param flyToView the new fly to view
     */
    public void setFlyToView(Integer flyToView) {
        if (flyToView != null)
             this.flyToView = Boolean.toString(flyToView == 1);
        else 
             this.flyToView = null;
    }


    /**
     * Gets the link.
     *
     * @return the link
     */
    public Link getLink() {
        return link;
    }

    /**
     * Sets the link.
     *
     * @param link the new link
     */
    public void setLink(Link link) {
        this.link = link;
    }
}
