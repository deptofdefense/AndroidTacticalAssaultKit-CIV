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

import java.util.List;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;

/**
 * Specifies how a Feature is displayed in the list view. The list view is a hierarchy of containers and children; in Google Earth, this is the Places panel.
 */
public class ListStyle extends ColorStyle {

    /** The list item type. */
    @Element(required=false)
    private String listItemType;

    /** The bg color. */
    @Element(required=false)
    private String bgColor;

    /** The item icon. */
    //BYOUNG https://github.com/Ekito/Simple-KML/issues/9
    //@Element(name="ItemIcon",required=false)
    //private Icon itemIcon;
    /** The inner boundary is list. */
    @ElementList(name="ItemIcon", entry="ItemIcon", inline=true, type=Icon.class, required=false)
    private List<Icon> itemIconList;

    /**
     * Gets the list item type.
     *
     * @return the list item type
     */
    public String getListItemType() {
        return listItemType;
    }

    /**
     * Sets the list item type.
     *
     * @param listItemType the new list item type
     */
    public void setListItemType(String listItemType) {
        this.listItemType = listItemType;
    }

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
     * Gets the item icon.
     *
     * @return the item icon
     */
    public List<Icon> getItemIcon() {
        return itemIconList;
    }

    /**
     * Sets the item icon.
     *
     * @param itemIcon the new item icon
     */
    public void setItemIcon(List<Icon> itemIcon) {
        this.itemIconList = itemIcon;
    }
}
