
package com.atakmap.android.rubbersheet.data.create;

import android.util.Xml;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * Parse doc.kml for metadata
 */
public class KMLParser implements ContentHandler {

    private static final String TAG = "KMLParser";

    private static final String EL_NAME = "name";
    private static final String EL_HREF = "href";
    private static final String EL_COORDINATES = "coordinates";
    private static final String EL_NORTH = "north";
    private static final String EL_SOUTH = "south";
    private static final String EL_EAST = "east";
    private static final String EL_WEST = "west";
    private static final String EL_ROTATION = "rotation";

    private final File _file;
    private String _element;
    private String _name, _iconHref, _coords, _north, _east, _south,
            _west, _rotation;

    public KMLParser(File docKml) {
        _file = docKml;
        try (InputStream is = IOProviderFactory.getInputStream(docKml)) {
            Xml.parse(is, Xml.Encoding.UTF_8, this);
        } catch (IOException e) {
            Log.e(TAG, "Failed to read doc.kml: " + docKml);
        } catch (SAXException e) {
            Log.e(TAG, "Failed to parse doc.kml: " + docKml);
        }
    }

    @Override
    public void setDocumentLocator(Locator locator) {
    }

    @Override
    public void startDocument() {
    }

    @Override
    public void endDocument() {
    }

    @Override
    public void startPrefixMapping(String prefix, String uri) {
    }

    @Override
    public void endPrefixMapping(String prefix) {
    }

    @Override
    public void startElement(String uri, String localName, String qName,
            Attributes atts) {
        _element = localName.toLowerCase(LocaleUtil.getCurrent());
    }

    @Override
    public void endElement(String uri, String localName, String qName) {
        _element = null;
    }

    @Override
    public void characters(char[] ch, int start, int length) {
        if (_element != null) {
            String content = new String(ch, start, length);
            switch (_element) {
                case EL_NAME:
                    _name = content;
                    break;
                case EL_HREF:
                    _iconHref = content;
                    break;
                case EL_COORDINATES:
                    _coords = content;
                    break;
                case EL_NORTH:
                    _north = content;
                    break;
                case EL_SOUTH:
                    _south = content;
                    break;
                case EL_EAST:
                    _east = content;
                    break;
                case EL_WEST:
                    _west = content;
                    break;
                case EL_ROTATION:
                    _rotation = content;
                    break;
            }
        }
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length) {
    }

    @Override
    public void processingInstruction(String target, String data) {
    }

    @Override
    public void skippedEntity(String name) {
    }

    public String getName() {
        return _name;
    }

    public File getImageFile() {
        if (!FileSystemUtils.isEmpty(_iconHref))
            return new File(_file.getParent(), _iconHref);
        return null;
    }

    public GeoPoint[] getCoordinates() {
        GeoPoint nw = null, ne = null, se = null, sw = null;
        if (!FileSystemUtils.isEmpty(_coords)) {
            // Space-delimited coordinate string
            String[] arr = _coords.split(" ");
            int i = -1;
            for (String s : arr) {
                i++;

                // Each coord is lon,lat,alt
                String[] cArr = s.split(",");
                if (cArr.length < 2)
                    continue;
                double lon = toDbl(cArr[0]);
                double lat = toDbl(cArr[1]);
                double alt = GeoPoint.UNKNOWN;
                if (cArr.length > 2)
                    alt = toDbl(cArr[2]);
                GeoPoint gp = new GeoPoint(lat, lon, alt);
                if (gp.isValid()) {
                    // LatLonQuad is in counter-clockwise order starting from
                    // south-west coordinate
                    if (i == 0)
                        sw = gp;
                    else if (i == 1)
                        se = gp;
                    else if (i == 2)
                        ne = gp;
                    else
                        nw = gp;
                }
            }
        } else {
            // Geo bounds + rotation
            double north = toDbl(_north);
            double south = toDbl(_south);
            double east = toDbl(_east);
            double west = toDbl(_west);
            double rot = toDbl(_rotation);
            if (Double.isNaN(north) || Double.isNaN(south)
                    || Double.isNaN(east) || Double.isNaN(west))
                return null;
            nw = new GeoPoint(north, west);
            ne = new GeoPoint(north, east);
            se = new GeoPoint(south, east);
            sw = new GeoPoint(south, west);
        }
        if (nw != null && ne != null && se != null && sw != null)
            return new GeoPoint[] {
                    nw, ne, se, sw
            };
        return null;
    }

    private double toDbl(String s) {
        if (!FileSystemUtils.isEmpty(s)) {
            try {
                return Double.parseDouble(s);
            } catch (Exception ignore) {
            }
        }
        return Double.NaN;
    }
}
