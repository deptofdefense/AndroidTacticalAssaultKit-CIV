
package com.atakmap.android.gpx;

import org.simpleframework.xml.ElementList;

import java.util.List;

/**
 * GPX trksegType A Track Segment holds a list of Track Points which are logically connected in
 * order. To represent a single GPS track where GPS reception was lost, or the GPS receiver was
 * turned off, start a new Track Segment for each continuous span of track data.
 * 
 * 
 */
public class GpxTrackSegment {

    /**
     * A Track Point holds the coordinates, elevation, timestamp, and metadata for a single point in
     * a track.
     */
    @ElementList(entry = "trkpt", inline = true, required = false)
    private List<GpxWaypoint> trkpt;

    // TODO extensions
    // <xsd:element name="extensions" type="extensionsType" minOccurs="0">
    // You can add extend GPX by adding your own elements from another schema here.

    public List<GpxWaypoint> getWaypoints() {
        return trkpt;
    }

    public void setPoints(List<GpxWaypoint> trkpt) {
        this.trkpt = trkpt;
    }

}
