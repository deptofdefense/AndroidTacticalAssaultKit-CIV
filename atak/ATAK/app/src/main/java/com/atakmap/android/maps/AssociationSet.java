
package com.atakmap.android.maps;

import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AssociationSet extends MetaShape {

    private final List<Association> _assocs = new ArrayList<>();
    private final List<GeoPoint> _points = new ArrayList<>();
    private final List<GeoPointMetaData> _metaPoints = new ArrayList<>();
    private final MutableGeoBounds _bounds = new MutableGeoBounds(0, 0, 0, 0);

    public AssociationSet(String uid) {
        super(uid);
    }

    public void setAssociations(List<Association> assocs) {
        List<Association> oldLinks;
        synchronized (this) {
            oldLinks = new ArrayList<>(_assocs);
            oldLinks.removeAll(assocs);
            _assocs.clear();
            _assocs.addAll(assocs);
            for (Association assoc : assocs) {
                GeoPoint[] points = assoc.getPoints();
                GeoPointMetaData[] gpms = assoc.getMetaDataPoints();
                _points.addAll(Arrays.asList(points));
                _metaPoints.addAll(Arrays.asList(gpms));
            }
            _bounds.set(getPoints());
        }

        //onPointsChanged();

        for (Association assoc : oldLinks) {
            if (assoc.getParent() == this)
                assoc.setParent(null);
        }
        for (Association assoc : assocs)
            assoc.setParent(this);
    }

    public synchronized Association[] getAssociations() {
        return _assocs.toArray(new Association[0]);
    }

    public synchronized Association getAssociationAt(int i) {
        return i >= 0 && i < _assocs.size() ? _assocs.get(i) : null;
    }

    @Override
    public synchronized GeoPoint[] getPoints() {
        return _points.toArray(new GeoPoint[0]);
    }

    @Override
    public GeoPointMetaData[] getMetaDataPoints() {
        return _metaPoints.toArray(new GeoPointMetaData[0]);
    }

    @Override
    public GeoBounds getBounds(MutableGeoBounds bounds) {
        return _bounds;
    }

    @Override
    public void setHeight(double height) {
        super.setHeight(height);
        for (Association a : getAssociations())
            a.setHeight(height);
    }
}
