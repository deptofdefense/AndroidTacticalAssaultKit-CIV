
package com.atakmap.android.gpx;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;

import java.util.List;

/**
 * GPX trkType trk represents a track - an ordered list of points describing a path.
 * 
 * 
 */
public class GpxTrack extends GpxBase {

    // TODO xsd:nonNegativeInteger, restrictions on other fields too
    /**
     * GPS track number.
     */
    @Element(required = false)
    private Integer number;

    // TODO extensions
    // <xsd:element name="extensions" type="extensionsType" minOccurs="0">
    // You can add extend GPX by adding your own elements from another schema here.

    /**
     * A Track Segment holds a list of Track Points which are logically connected in order. To
     * represent a single GPS track where GPS reception was lost, or the GPS receiver was turned
     * off, start a new Track Segment for each continuous span of track data.
     */
    @ElementList(entry = "trkseg", inline = true, required = false)
    private List<GpxTrackSegment> trkseg;

    public Integer getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    public List<GpxTrackSegment> getSegments() {
        return trkseg;
    }

    public void setSegments(List<GpxTrackSegment> trkseg) {
        this.trkseg = trkseg;
    }
}
