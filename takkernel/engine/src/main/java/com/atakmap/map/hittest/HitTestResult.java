package com.atakmap.map.hittest;

import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.maps.coords.GeoPoint;

/**
 * Result data for hit-testing
 */
public class HitTestResult {
    
    /**
     * The type of hit on the object
     */
    public enum Type {
        POINT,
        LINE,
        FILL;

        @Override
        public String toString() {
            return super.toString().toLowerCase(LocaleUtil.getCurrent());
        }
    }

    // The subject object that was hit
    public final Object subject;

    // The point on the map the hit was detected
    public GeoPoint point;

    // The type of hit: POINT, LINE, or FILL
    public Type type = Type.POINT;

    // The point or line index hit (-1 for N/A)
    public int index = -1;

    // The number of hits
    public int count = 1;

    public HitTestResult(Object subject) {
        this.subject = subject;
    }

    public HitTestResult(Object subject, GeoPoint point) {
        this(subject);
        this.point = point;
    }

    public HitTestResult(Object subject, HitTestResult other) {
        this(subject);
        this.point = other.point;
        this.type = other.type;
        this.index = other.index;
        this.count = other.count;
    }

    public HitTestResult(HitTestResult other) {
        this(other.subject, other);
    }
}
