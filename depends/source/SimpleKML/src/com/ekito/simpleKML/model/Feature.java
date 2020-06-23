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
import java.util.ArrayList;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.ElementListUnion;
import org.simpleframework.xml.ElementUnion;
import org.simpleframework.xml.Namespace;

import com.ekito.simpleKML.model.atom.Link;


/**
 * This is an abstract element and cannot be used directly in a KML file. The following diagram shows how some of a Feature's elements appear in Google Earth.
 */
public abstract class Feature extends Object {

    /** The name. */
    @Element(required=false)
    private String name;

    /** The visibility. */
    @Element(required=false)
    private String visibility;     // boolean 0/1 false/true

    /** The open. */
    @Element(required=false)
    private String open;           // boolean 0/1 false/true

    /** The author. */
    @Element(required=false)
    @Namespace(prefix="atom")
    private String author;

    /** The author link. */
    @Element(name="link", required=false)
    @Namespace(prefix="atom")
    private Link authorLink;

    /** The address. */
    @Element(required=false)
    private String address;

    /** The address details. */
    @Element(name="AddressDetails", required=false)
    @Namespace(prefix="xal")
    private String addressDetails;

    /** The phone number. */
    @Element(required=false)
    private String phoneNumber;

    /** The snippet. */
    @Element(name="Snippet", required=false)
    private String snippet;

    /** The description. */
    @Element(required=false)
    private String description;

    /** The abstract view. */
    @ElementUnion({
        @Element(name="Camera", type=Camera.class, required=false),
        @Element(name="LookAt", type=LookAt.class, required=false)
    })
    private AbstractView abstractView;
    
    /** The time primitive. */
    @ElementUnion({
        @Element(name="TimeSpan", type=TimeSpan.class, required=false),
        @Element(name="TimeStamp", type=TimeStamp.class, required=false)
    })
    private TimePrimitive timePrimitive;

    /** The style url. */
    @Element(required=false)
    private String styleUrl;

    /** The style selector. */
    @ElementListUnion({
        @ElementList(entry="Style", inline=true, type=Style.class, required=false),
        @ElementList(entry="StyleMap", inline=true, type=StyleMap.class, required=false)
    })
    private List<StyleSelector> styleSelector;

    /** The region. */
    @Element(name="Region", required=false)
    private Region region;

    // TODO get specs for Metadata
    
    /** The extended data. */
    @ElementList(name="ExtendedData", required=false, inline=true)
    private List<ExtendedData> extendedData;

    /**
     * Gets the name.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name.
     *
     * @param name the new name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the visibility.
     *
     * @return the visibility
     */
    public Boolean getVisibility() {
        if (visibility != null) 
            return BooleanUtil.valueOf(visibility);
        else 
            return Boolean.FALSE;
    }

    /**
     * Sets the visibility.
     *
     * @param visibility the new visibility
     */
    public void setVisibility(Boolean visibility) {
        if (visibility != null) 
             this.visibility = visibility.toString();
        else 
             this.visibility = null;
    }

    /**
     * Sets the visibility.
     *
     * @param visibility the new visibility
     */
    public void setVisibility(Integer visibility) {
        if (visibility != null)
             this.visibility = Boolean.toString(visibility == 1);
        else 
             this.visibility = null;
    }


    /**
     * Gets the open.
     *
     * @return the open
     */
    public Boolean getOpen() {
        if (open != null)
            return BooleanUtil.valueOf(open);
        return 
            false;
    }

    /**
     * Sets the open.
     *
     * @param open the new open
     */
    public void setOpen(Boolean open) {
        if (open != null) 
             this.open = open.toString();
        else 
             this.open = null;
    }

    /**
     * Sets the open.
     *
     * @param open the new open
     */
    public void setOpen(Integer open) {
        if (open != null) 
            this.open = Boolean.toString(open == 1);
        else 
            this.open = null;
    }


    /**
     * Gets the author.
     *
     * @return the author
     */
    public String getAuthor() {
        return author;
    }

    /**
     * Sets the author.
     *
     * @param author the new author
     */
    public void setAuthor(String author) {
        this.author = author;
    }

    /**
     * Gets the author link.
     *
     * @return the author link
     */
    public Link getAuthorLink() {
        return authorLink;
    }

    /**
     * Sets the author link.
     *
     * @param authorLink the new author link
     */
    public void setAuthorLink(Link authorLink) {
        this.authorLink = authorLink;
    }

    /**
     * Gets the address.
     *
     * @return the address
     */
    public String getAddress() {
        return address;
    }

    /**
     * Sets the address.
     *
     * @param address the new address
     */
    public void setAddress(String address) {
        this.address = address;
    }

    /**
     * Gets the address details.
     *
     * @return the address details
     */
    public String getAddressDetails() {
        return addressDetails;
    }

    /**
     * Sets the address details.
     *
     * @param addressDetails the new address details
     */
    public void setAddressDetails(String addressDetails) {
        this.addressDetails = addressDetails;
    }

    /**
     * Gets the phone number.
     *
     * @return the phone number
     */
    public String getPhoneNumber() {
        return phoneNumber;
    }

    /**
     * Sets the phone number.
     *
     * @param phoneNumber the new phone number
     */
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    /**
     * Gets the snippet.
     *
     * @return the snippet
     */
    public String getSnippet() {
        return snippet;
    }

    /**
     * Sets the snippet.
     *
     * @param snippet the new snippet
     */
    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }

    /**
     * Gets the description.
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the description.
     *
     * @param description the new description
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Gets the abstract view.
     *
     * @return the abstract view
     */
    public AbstractView getAbstractView() {
        return abstractView;
    }

    /**
     * Sets the abstract view.
     *
     * @param abstractView the new abstract view
     */
    public void setAbstractView(AbstractView abstractView) {
        this.abstractView = abstractView;
    }

    /**
     * Gets the time primitive.
     *
     * @return the time primitive
     */
    public TimePrimitive getTimePrimitive() {
        return timePrimitive;
    }

    /**
     * Sets the time primitive.
     *
     * @param timePrimitive the new time primitive
     */
    public void setTimePrimitive(TimePrimitive timePrimitive) {
        this.timePrimitive = timePrimitive;
    }

    /**
     * Gets the style url.
     *
     * @return the style url
     */
    public String getStyleUrl() {
        return styleUrl;
    }

    /**
     * Sets the style url.
     *
     * @param styleUrl the new style url
     */
    public void setStyleUrl(String styleUrl) {
        this.styleUrl = styleUrl;
    }

    /**
     * Gets the style selector.
     *
     * @return the style selector
     */
    public List<StyleSelector> getStyleSelector() {
        return styleSelector;
    }

    /**
     * Sets the style selector.
     *
     * @param styleSelector the new style selector
     */
    public void setStyleSelector(List<StyleSelector> styleSelector) {
        this.styleSelector = styleSelector;
    }

    /**
     * Gets the region.
     *
     * @return the region
     */
    public Region getRegion() {
        return region;
    }

    /**
     * Sets the region.
     *
     * @param region the new region
     */
    public void setRegion(Region region) {
        this.region = region;
    }

    /**
     * Gets the extended data.
     *
     * @return the extended data
     */
    public ExtendedData getExtendedData() {
        if (extendedData == null || extendedData.size() == 0)
           return null;
        return extendedData.get(0);
    }

    /**
     * Sets the extended data.
     *
     * @param extendedData the new extended data
     */
    public void setExtendedData(ExtendedData extendedData) {
        this.extendedData = new ArrayList<ExtendedData>();
        this.extendedData.add(extendedData);
    }
}
