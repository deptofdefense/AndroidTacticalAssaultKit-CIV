
package com.atakmap.spatial.kml;

import org.simpleframework.xml.stream.CamelCaseStyle;

/**
 * A style to ensure that serialized standalone KML Geometry objects have proper casing. When
 * serializing an entire KML, it would include "<Point>..." for example. But when serializing just
 * the point and its children to insert into spatialite, it comes out as "<point>..." which is
 * incorrect. Workaround for simple-kml is to style here, which is much faster than using Regex on
 * long strings
 * 
 * 
 */
public class KMLGeometryCaseStyle extends CamelCaseStyle {

    public static final String TAG = "KMLGeometryCaseStyle";

    private static final String[] MATCHES = {
            "point", "lineString", "linearRing", "polygon", "multiGeometry"
    };

    public KMLGeometryCaseStyle() {
        super(true, false);
    }

    @Override
    public String getAttribute(String name) {
        // NO-OP
        return name;
    }

    @Override
    public String getElement(String name) {
        for (String match : MATCHES) {
            if (match.equalsIgnoreCase(name)) {
                // Log.d(TAG, "Found getElement match: " + name);
                return super.getElement(name);
            }
        }

        // NO-OP
        return name;
    }

    @Override
    public void setElement(String name, String value) {
        for (String match : MATCHES) {
            if (match.equalsIgnoreCase(name)) {
                // Log.d(TAG, "Found setElement match: " + name + ", " + value);
                super.setElement(name, value);
                break;
            }
        }

        // NO-OP
    }

    @Override
    public void setAttribute(String name, String value) {
        // NO-OP
    }
}
