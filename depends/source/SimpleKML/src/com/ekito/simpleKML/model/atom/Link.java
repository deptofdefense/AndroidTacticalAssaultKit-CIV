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
package com.ekito.simpleKML.model.atom;

import org.simpleframework.xml.Attribute;

/**
 * Specifies the URL of the website containing this KML or KMZ file. Be sure to include the namespace for this element in any KML file that uses it: xmlns:atom="http://www.w3.org/2005/Atom"
 */
public class Link {
    
    /** The href. */
    @Attribute
    private String href;

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
}
