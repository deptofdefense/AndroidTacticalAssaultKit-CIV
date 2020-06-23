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
import org.simpleframework.xml.Namespace;

/**
 * This is an abstract element and cannot be used directly in a KML file. This element is extended by the {@link Camera} and {@link LookAt} elements.
 */
public abstract class AbstractView extends Object {

    /** The time primitive. */
    @ElementUnion({
        @Element(name="TimeSpan", type=TimeSpan.class, required=false),
        @Element(name="TimeStamp", type=TimeStamp.class, required=false)
    })
    @Namespace(prefix="gx")
    private TimePrimitive timePrimitive;

    /** The viewer options. */
    @Element(name="ViewerOptions", required=false)
    private ViewerOptions viewerOptions;

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
     * Gets the viewer options.
     *
     * @return the viewer options
     */
    public ViewerOptions getViewerOptions() {
        return viewerOptions;
    }

    /**
     * Sets the viewer options.
     *
     * @param viewerOptions the new viewer options
     */
    public void setViewerOptions(ViewerOptions viewerOptions) {
        this.viewerOptions = viewerOptions;
    }
}
