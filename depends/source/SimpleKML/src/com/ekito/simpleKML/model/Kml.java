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
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Attribute;

/**
 * The root element of a KML file. This element is required. It follows the xml declaration at the beginning of the file. The hint attribute is used as a signal to Google Earth to display the file as celestial data.
 * The {@link Kml} element may also include the namespace for any external XML schemas that are referenced within the file.
 * A basic {@link Kml} element contains 0 or 1 {@link Feature} and 0 or 1 {@link NetworkLinkControl}
 */
@Root(name="kml")
public class Kml {

    @Attribute(name = "xmlns", required = false)
    private String XMLNS = "http://www.opengis.net/kml/2.2";

    @Attribute(name = "xmlns:gx", required = false) 
    private String XMLNS_GX = "http://www.google.com/kml/ext/2.2";
 
    @Attribute(name = "xmlns:kml", required = false) 
    private String XMLNS_KML="http://www.opengis.net/kml/2.2";

    @Attribute(name = "xmlns:atom", required = false) 
    private String XMLNS_ATOM = "http://www.w3.org/2005/Atom";

    @Attribute(name = "xmlns:xsd", required = false)
    private String XMLNS_XSD = "https://www.w3.org/2001/XMLSchema";

    
    /** The network link control. */
    @Element(name="NetworkLinkControl", required=false)
    private NetworkLinkControl networkLinkControl;

    /** The feature. */
    @ElementUnion({
        @Element(name="Document", type=Document.class, required=false),
        @Element(name="Folder", type=Folder.class, required=false),
        @Element(name="NetworkLink", type=NetworkLink.class, required=false),
        @Element(name="Placemark", type=Placemark.class, required=false),
        @Element(name="GroundOverlay", type=GroundOverlay.class, required=false),
        @Element(name="PhotoOverlay", type=PhotoOverlay.class, required=false),
        @Element(name="ScreenOverlay", type=ScreenOverlay.class, required=false)
    })
    private Feature feature;

    /**
     * Instantiates a new kml.
     */
    public Kml() {
        super();
    }

    /**
     * Gets the network link control.
     *
     * @return the network link control
     */
    public NetworkLinkControl getNetworkLinkControl() {
        return networkLinkControl;
    }

    /**
     * Sets the network link control.
     *
     * @param networkLinkControl the new network link control
     */
    public void setNetworkLinkControl(NetworkLinkControl networkLinkControl) {
        this.networkLinkControl = networkLinkControl;
    }

    /**
     * Gets the feature.
     *
     * @return the feature
     */
    public Feature getFeature() {
        return feature;
    }

    /**
     * Sets the feature.
     *
     * @param feature the new feature
     */
    public void setFeature(Feature feature) {
        this.feature = feature;
    }

}
