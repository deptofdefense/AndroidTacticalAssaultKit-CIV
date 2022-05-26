
package com.atakmap.android.gpx;

import com.atakmap.android.util.ATAKConstants;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Order;

import java.util.List;

/**
 * This is the top level GPX class with SimpleXML annotations to support serialization GPX gpxType
 * GPX is the root element in the XML file. GPX documents contain a metadata header, followed by
 * waypoints, routes, and tracks. You can add your own elements to the extensions section of the GPX
 * document.
 * 
 * 
 */
@Root
@Order(elements = {
        "wpt", "rte", "trk"
})
public class Gpx {

    private static final String TAG = "Gpx";

    /**
     * Note this is required by schema, but not always populated in practice
     */
    @SuppressWarnings("FieldMayBeFinal")
    @Attribute(name = "version", required = false)
    private String VERSION = "1.1";

    /**
     * Note this is required by schema, but not always populated in practice
     */
    @Attribute(name = "creator", required = false)
    private String CREATOR = "TAK";

    @SuppressWarnings("FieldMayBeFinal")
    @Attribute(name = "xmlns", required = false)
    private String XMLNS = "http://www.topografix.com/GPX/1/1/";

    @SuppressWarnings("FieldMayBeFinal")
    @Attribute(name = "xmlns:xsd", required = false)
    private String XMLNS_XSD = "https://www.w3.org/2001/XMLSchema";

    // TODO build out metadataType schema
    /**
     * Metadata about the file.
     */
    // @Element
    // private GpxMetdata metadata;

    /**
     * A list of waypoints.
     */
    @ElementList(entry = "wpt", inline = true, required = false)
    private List<GpxWaypoint> wpt;

    /**
     * A list of routes.
     */
    @ElementList(entry = "rte", inline = true, required = false)
    private List<GpxRoute> rte;

    /**
     * A list of tracks.
     */
    @ElementList(entry = "trk", inline = true, required = false)
    private List<GpxTrack> trk;

    public Gpx() {
        CREATOR = ATAKConstants.getVersionName();
    }

    public List<GpxWaypoint> getWaypoints() {
        return wpt;
    }

    public void setWaypoints(List<GpxWaypoint> waypoints) {
        this.wpt = waypoints;
    }

    public List<GpxRoute> getRoutes() {
        return rte;
    }

    public void setRoutes(List<GpxRoute> routes) {
        this.rte = routes;
    }

    public List<GpxTrack> getTracks() {
        return trk;
    }

    public void setTracks(List<GpxTrack> tracks) {
        this.trk = tracks;
    }

    public boolean isEmpty() {
        return (wpt == null || wpt.size() < 1) &&
                (trk == null || trk.size() < 1) &&
                (rte == null || rte.size() < 1);
    }
}
