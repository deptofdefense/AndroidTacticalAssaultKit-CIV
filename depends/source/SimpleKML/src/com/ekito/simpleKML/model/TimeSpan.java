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
 * Represents an extent in time bounded by begin and end dateTimes.
 * 
 * If begin or end is missing, then that end of the period is unbounded.
 * 
 * The dateTime is defined according to XML Schema time. The value can be expressed as yyyy-mm-ddThh:mm:sszzzzzz, where T is the separator between the date and the time, and the time zone is either Z (for UTC) or zzzzzz, which represents hh:mm in relation to UTC. Additionally, the value can be expressed as a date only.
 */
public class TimeSpan extends TimePrimitive {

    /** The begin. */
    @Element(required=false)
    private String begin;
    
    /** The end. */
    @Element(required=false)
    private String end;

    /**
     * Gets the begin.
     *
     * @return the begin
     */
    public String getBegin() {
        return begin;
    }

    /**
     * Sets the begin.
     *
     * @param begin the new begin
     */
    public void setBegin(String begin) {
        this.begin = begin;
    }

    /**
     * Gets the end.
     *
     * @return the end
     */
    public String getEnd() {
        return end;
    }

    /**
     * Sets the end.
     *
     * @param end the new end
     */
    public void setEnd(String end) {
        this.end = end;
    }
}
