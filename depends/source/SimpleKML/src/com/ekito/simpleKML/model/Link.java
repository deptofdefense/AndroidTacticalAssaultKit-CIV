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
 * {@link Link} specifies the location of any of the following:
 * <ul>
 * <li>KML files fetched by network links</li>
 * <li>Image files used in any Overlay (the {@link Icon} element specifies the image in an {@link Overlay}; {@link Icon} has the same fields as {@link Link})</li>
 * <li>Model files used in the {@link Model} element</li>
 * </ul>
 * The file is conditionally loaded and refreshed, depending on the refresh parameters supplied here. Two different sets of refresh parameters can be specified: one set is based on time (refreshMode and refreshInterval) and one is based on the current "camera" view (viewRefreshMode and viewRefreshTime). In addition, Link specifies whether to scale the bounding box parameters that are sent to the server (viewBoundScale and provides a set of optional viewing parameters that can be sent to the server (viewFormat) as well as a set of optional parameters containing version and language information.
 * 
 * When a file is fetched, the URL that is sent to the server is composed of three pieces of information:
 * <ul>
 * <li>the href (Hypertext Reference) that specifies the file to load.</li>
 * <li>an arbitrary format string that is created from (a) parameters that you specify in the viewFormat element or (b) bounding box parameters (this is the default and is used if no viewFormat element is included in the file).</li>
 * <li>a second format string that is specified in the httpQuery element.</li>
 * </ul>
 * If the file specified in href is a local file, the viewFormat and httpQuery elements are not used.
 * 
 * The {@link Link} element replaces the Url element of {@link NetworkLink} contained in earlier KML releases and adds functionality for the {@link Region} element (introduced in KML 2.1). In Google Earth releases 3.0 and earlier, the {@link Link} element is ignored.
 */
public class Link extends Object {

    /** The href. */
    @Element(required=false)
    private String href;

    /** The refresh mode. */
    @Element(required=false)
    private String refreshMode;

    /** The refresh interval. */
    @Element(required=false)
    private Float refreshInterval;

    /** The view refresh mode. */
    @Element(required=false)
    private String viewRefreshMode;

    /** The view refresh time. */
    @Element(required=false)
    private Float viewRefreshTime;

    /** The view bound scale. */
    @Element(required=false)
    private Float viewBoundScale;

    /** The view format. */
    @Element(required=false)
    private String viewFormat;

    /** The http query. */
    @Element(required=false)
    private String httpQuery;

    /**
     * Gets the href.
     *
     * @return the href
     */
    public String getHref() {
        return href;
    }

    /**
     * Sets the href.
     *
     * @param href the new href
     */
    public void setHref(String href) {
        this.href = href;
    }

    /**
     * Gets the refresh mode.
     *
     * @return the refresh mode
     */
    public String getRefreshMode() {
        return refreshMode;
    }

    /**
     * Sets the refresh mode.
     *
     * @param refreshMode the new refresh mode
     */
    public void setRefreshMode(String refreshMode) {
        this.refreshMode = refreshMode;
    }

    /**
     * Gets the refresh interval.
     *
     * @return the refresh interval
     */
    public Float getRefreshInterval() {
        return refreshInterval;
    }

    /**
     * Sets the refresh interval.
     *
     * @param refreshInterval the new refresh interval
     */
    public void setRefreshInterval(Float refreshInterval) {
        this.refreshInterval = refreshInterval;
    }

    /**
     * Gets the view refresh mode.
     *
     * @return the view refresh mode
     */
    public String getViewRefreshMode() {
        return viewRefreshMode;
    }

    /**
     * Sets the view refresh mode.
     *
     * @param viewRefreshMode the new view refresh mode
     */
    public void setViewRefreshMode(String viewRefreshMode) {
        this.viewRefreshMode = viewRefreshMode;
    }

    /**
     * Gets the view refresh time.
     *
     * @return the view refresh time
     */
    public Float getViewRefreshTime() {
        return viewRefreshTime;
    }

    /**
     * Sets the view refresh time.
     *
     * @param viewRefreshTime the new view refresh time
     */
    public void setViewRefreshTime(Float viewRefreshTime) {
        this.viewRefreshTime = viewRefreshTime;
    }

    /**
     * Gets the view bound scale.
     *
     * @return the view bound scale
     */
    public Float getViewBoundScale() {
        return viewBoundScale;
    }

    /**
     * Sets the view bound scale.
     *
     * @param viewBoundScale the new view bound scale
     */
    public void setViewBoundScale(Float viewBoundScale) {
        this.viewBoundScale = viewBoundScale;
    }

    /**
     * Gets the view format.
     *
     * @return the view format
     */
    public String getViewFormat() {
        return viewFormat;
    }

    /**
     * Sets the view format.
     *
     * @param viewFormat the new view format
     */
    public void setViewFormat(String viewFormat) {
        this.viewFormat = viewFormat;
    }

    /**
     * Gets the http query.
     *
     * @return the http query
     */
    public String getHttpQuery() {
        return httpQuery;
    }

    /**
     * Sets the http query.
     *
     * @param httpQuery the new http query
     */
    public void setHttpQuery(String httpQuery) {
        this.httpQuery = httpQuery;
    }

}
