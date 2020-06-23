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
import org.simpleframework.xml.ElementUnion;

/**
 * Controls the behavior of files fetched by a {@link NetworkLink}.
 */
public class NetworkLinkControl {

    /** The min refresh period. */
    @Element(required=false)
    private Float minRefreshPeriod;

    /** The max session length. */
    @Element(required=false)
    private Float maxSessionLength;

    /** The cookie. */
    @Element(required=false)
    private String cookie;

    /** The message. */
    @Element(required=false)
    private String message;

    /** The link name. */
    @Element(required=false)
    private String linkName;

    /** The link description. */
    @Element(required=false)
    private String linkDescription;

    /** The link snippet. */
    @Element(required=false)
    private String linkSnippet;

    /** The expires. */
    @Element(required=false)
    private String expires;

    /** The update. */
    @Element(name="Update", required=false)
    private Update update;
    
    /** The abstract view. */
    @ElementUnion({
        @Element(name="Camera", type=Camera.class, required=false),
        @Element(name="LookAt", type=LookAt.class, required=false)
    })
    private AbstractView abstractView;

    /**
     * Gets the min refresh period.
     *
     * @return the min refresh period
     */
    public Float getMinRefreshPeriod() {
        return minRefreshPeriod;
    }

    /**
     * Sets the min refresh period.
     *
     * @param minRefreshPeriod the new min refresh period
     */
    public void setMinRefreshPeriod(Float minRefreshPeriod) {
        this.minRefreshPeriod = minRefreshPeriod;
    }

    /**
     * Gets the max session length.
     *
     * @return the max session length
     */
    public Float getMaxSessionLength() {
        return maxSessionLength;
    }

    /**
     * Sets the max session length.
     *
     * @param maxSessionLength the new max session length
     */
    public void setMaxSessionLength(Float maxSessionLength) {
        this.maxSessionLength = maxSessionLength;
    }

    /**
     * Gets the cookie.
     *
     * @return the cookie
     */
    public String getCookie() {
        return cookie;
    }

    /**
     * Sets the cookie.
     *
     * @param cookie the new cookie
     */
    public void setCookie(String cookie) {
        this.cookie = cookie;
    }

    /**
     * Gets the message.
     *
     * @return the message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Sets the message.
     *
     * @param message the new message
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * Gets the link name.
     *
     * @return the link name
     */
    public String getLinkName() {
        return linkName;
    }

    /**
     * Sets the link name.
     *
     * @param linkName the new link name
     */
    public void setLinkName(String linkName) {
        this.linkName = linkName;
    }

    /**
     * Gets the link description.
     *
     * @return the link description
     */
    public String getLinkDescription() {
        return linkDescription;
    }

    /**
     * Sets the link description.
     *
     * @param linkDescription the new link description
     */
    public void setLinkDescription(String linkDescription) {
        this.linkDescription = linkDescription;
    }

    /**
     * Gets the link snippet.
     *
     * @return the link snippet
     */
    public String getLinkSnippet() {
        return linkSnippet;
    }

    /**
     * Sets the link snippet.
     *
     * @param linkSnippet the new link snippet
     */
    public void setLinkSnippet(String linkSnippet) {
        this.linkSnippet = linkSnippet;
    }

    /**
     * Gets the expires.
     *
     * @return the expires
     */
    public String getExpires() {
        return expires;
    }

    /**
     * Sets the expires.
     *
     * @param expires the new expires
     */
    public void setExpires(String expires) {
        this.expires = expires;
    }

    /**
     * Gets the update.
     *
     * @return the update
     */
    public Update getUpdate() {
        return update;
    }

    /**
     * Sets the update.
     *
     * @param update the new update
     */
    public void setUpdate(Update update) {
        this.update = update;
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
}
