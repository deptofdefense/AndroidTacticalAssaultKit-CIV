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
 * contains a mapping from a sourceHref to a targetHref
 */
public class Alias {
    
    /** The target href. */
    @Element(required=false)
    private String targetHref;

    /** The source href. */
    @Element(required=false)
    private String sourceHref;

    /**
     * Gets the target href.
     *
     * @return the target href
     */
    public String getTargetHref() {
        return targetHref;
    }

    /**
     * Sets the target href.
     *
     * @param targetHref the new target href
     */
    public void setTargetHref(String targetHref) {
        this.targetHref = targetHref;
    }

    /**
     * Gets the source href.
     *
     * @return the source href
     */
    public String getSourceHref() {
        return sourceHref;
    }

    /**
     * Sets the source href.
     *
     * @param sourceHref the new source href
     */
    public void setSourceHref(String sourceHref) {
        this.sourceHref = sourceHref;
    }
}
