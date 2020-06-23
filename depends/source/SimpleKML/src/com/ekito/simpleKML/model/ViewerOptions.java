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

import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Namespace;

/**
 * This element enables special viewing modes in Google Earth 6.0 and later. It has one or more gx:option child elements. The gx:option element has a name attribute and an enabled attribute. The name specifies one of the following: Street View imagery ("streetview"), historical imagery ("historicalimagery"), and sunlight effects for a given time of day ("sunlight"). The enabled attribute is used to turn a given viewing mode on or off.
 */
@Namespace(prefix="gx")
public class ViewerOptions {

    /** The option. */
    @ElementList(entry="option", inline=true)
    private List<Option> option;

    /**
     * Gets the option.
     *
     * @return the option
     */
    public List<Option> getOption() {
        return option;
    }

    /**
     * Sets the option.
     *
     * @param option the new option
     */
    public void setOption(List<Option> option) {
        this.option = option;
    }
}
