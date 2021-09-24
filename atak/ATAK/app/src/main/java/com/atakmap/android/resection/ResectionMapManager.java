
package com.atakmap.android.resection;

import android.content.Context;
import android.graphics.Color;
import android.widget.BaseAdapter;

import com.atakmap.android.maps.Association;
import com.atakmap.android.maps.DefaultMapGroup;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.overlay.DefaultMapGroupOverlay;
import com.atakmap.android.user.PlacePointTool;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.maps.assets.Icon;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;

import gov.nist.math.jama.Matrix;

public class ResectionMapManager implements MapGroup.OnItemListChangedListener,
        PointMapItem.OnPointChangedListener, MapItem.OnMetadataChangedListener {

    private static final String LANDMARK_TYPE = "b-m-p-s-p-sv-r";
    public static final String LANDMARK_PREFIX = "LM";
    private static final Comparator<Integer> INT_COMP = new Comparator<Integer>() {
        @Override
        public int compare(Integer lhs, Integer rhs) {
            return Integer.compare(lhs, rhs);
        }
    };

    private final MapView _mapView;
    private final Context _context;
    private final MapGroup _mapGroup;

    private BaseAdapter _adapter;
    private boolean _bulkOperation;

    public ResectionMapManager(MapView mapView) {
        _mapView = mapView;
        _context = mapView.getContext();
        MapGroup group = _mapView.getRootGroup().deepFindMapGroup("Resection");
        if (group == null) {
            group = new DefaultMapGroup("Resection");
            group.setMetaString("overlay", "resection");
            group.setMetaBoolean("permaGroup", true);
            group.setMetaInteger("groupOverlayOrder", 5);
            group.setMetaString("iconUri", ATAKUtilities.getResourceUri(
                    R.drawable.ic_resection_compass));
            _mapView.getMapOverlayManager().addMarkersOverlay(
                    new DefaultMapGroupOverlay(_mapView, group));
        }
        _mapGroup = group;
        _mapGroup.addOnItemListChangedListener(this);
        _bulkOperation = true;
        for (MapItem item : _mapGroup.getItems())
            onItemAdded(item, _mapGroup);
        _bulkOperation = false;
    }

    public void setAdapter(BaseAdapter adapter) {
        _adapter = adapter;
    }

    public void dispose() {
        _mapGroup.removeOnItemListChangedListener(this);
        _bulkOperation = true;
        for (MapItem m : _mapGroup.getItems())
            onItemRemoved(m, _mapGroup);
        _bulkOperation = false;
    }

    public Marker addLandmark(GeoPoint point) {
        Marker m = new Marker(point, UUID.randomUUID().toString());
        m.setTitle(getNewCallsign());
        m.setType(LANDMARK_TYPE);
        m.setMetaBoolean("nevercot", true);
        m.setMetaString("entry", "user");
        m.setMovable(true);
        m.setMetaBoolean("removable", true);
        m.setMetaBoolean("editable", true);
        m.setMetaString("menu", "menus/resection_landmark.xml");
        _mapGroup.addItem(m);
        m.setIcon(new Icon.Builder()
                .setImageUri(0, ATAKUtilities.getResourceUri(
                        _context, R.drawable.ic_landmark))
                .setSize(32, 32).setAnchor(16, 32)
                .setColor(0, Color.WHITE)
                .build());
        double bearing = _mapView.getMapData().getDouble("deviceAzimuth", 0.0);
        Marker self = _mapView.getSelfMarker();
        if (self != null && self.getGroup() != null)
            bearing = self.getPoint().bearingTo(point);
        m.setMetaDouble("landmarkBearing", bearing);
        return m;
    }

    private boolean isLandmark(MapItem mi) {
        return mi instanceof Marker && mi.getType().equals(LANDMARK_TYPE);
    }

    public List<Marker> getLandmarks() {
        List<Marker> ret = new ArrayList<>();
        for (MapItem mi : _mapGroup.getItems()) {
            if (isLandmark(mi))
                ret.add((Marker) mi);
        }
        return ret;
    }

    private List<IntersectionLine> getLines() {
        List<IntersectionLine> ret = new ArrayList<>();
        for (MapItem mi : _mapGroup.getItems()) {
            if (mi instanceof IntersectionLine)
                ret.add((IntersectionLine) mi);
        }
        return ret;
    }

    private String getNewCallsign() {
        Collection<MapItem> items = _mapGroup.getItems();
        TreeSet<Integer> taken = new TreeSet<>(INT_COMP);
        for (MapItem mi : items) {
            if (!isLandmark(mi))
                continue;
            String title = mi.getTitle();
            if (!title.startsWith(LANDMARK_PREFIX))
                continue;
            try {
                int num = Integer.parseInt(title.substring(
                        LANDMARK_PREFIX.length()));
                if (num >= 0)
                    taken.add(num);
            } catch (Exception ignore) {
            }
        }
        int id = 0;
        for (Integer i : taken) {
            if (i != id)
                break;
            id++;
        }
        return LANDMARK_PREFIX + id;
    }

    @Override
    public void onItemAdded(MapItem item, MapGroup group) {
        if (isLandmark(item)) {
            Marker m = (Marker) item;
            m.addOnPointChangedListener(this);
            m.addOnMetadataChangedListener("landmarkBearing", this);
            if (!_bulkOperation && _adapter != null)
                _adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onItemRemoved(MapItem item, MapGroup group) {
        if (isLandmark(item)) {
            Marker m = (Marker) item;
            m.removeOnPointChangedListener(this);
            m.removeOnMetadataChangedListener("landmarkBearing", this);
            String uid = m.getUID();
            _mapGroup.removeItem(_mapGroup.deepFindUID(uid + ".line"));
            _mapGroup.removeItem(_mapGroup.deepFindUID(uid + ".end"));
            if (!_bulkOperation && _adapter != null)
                _adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onPointChanged(PointMapItem pmi) {
        onMetadataChanged(pmi, "landmarkBearing");
        if (!_bulkOperation && _adapter != null)
            _adapter.notifyDataSetChanged();
    }

    @Override
    public void onMetadataChanged(MapItem item, final String field) {
        if (isLandmark(item) && field.equals("landmarkBearing")) {
            Marker m = (Marker) item;
            double bearing = m.getMetaDouble("landmarkBearing", 0);
            bearing = (bearing + 180d) % 360d;
            GeoPoint endPoint = GeoPoint.createMutable();
            GeoPoint e = GeoCalculations.pointAtDistance(
                    m.getPoint(), bearing, 1000d);
            endPoint.set(e);
            endPoint.set(m.getPoint().getAltitude());

            String endUID = m.getUID() + ".end";
            Marker end = (Marker) _mapGroup.deepFindUID(endUID);
            if (end == null) {
                end = new Marker(endPoint, endUID);
                end.setVisible(false);
                end.setMetaBoolean("addToObjList", false);
                end.setMetaBoolean("nevercot", true);
                _mapGroup.addItem(end);
            } else
                end.setPoint(endPoint);

            String lineUID = m.getUID() + ".line";
            IntersectionLine line = (IntersectionLine) _mapGroup
                    .deepFindUID(lineUID);
            if (line == null) {
                line = new IntersectionLine(m, end, lineUID);
                _mapGroup.addItem(line);
            }
            line.reset();
        }
    }

    /**
     * Method to create an intermarker as the product of resectioning.
     */
    public void createInterMarker() {
        GeoPoint point = getIntersectionPoint();
        if (point == null)
            return;

        Collection<MapItem> items = _mapView.getRootGroup().deepFindItems(
                "resection", "result");
        for (MapItem mi : items) {
            if (mi instanceof Marker && ((Marker) mi).getPoint()
                    .equals(point)) {
                // No need to create another marker in the same spot
                return;
            }
        }

        String callsign = _mapView.getDeviceCallsign();
        Marker marker = new PlacePointTool.MarkerCreator(point)
                .setUid(UUID.randomUUID().toString())
                .setCallsign(callsign)
                .setType("b-m-p-s-m")
                .showCotDetails(false)
                .placePoint();
        marker.setMetaString("resection", "result");
    }

    /**
     * Calculate intersection point between all landmarks
     * @return Intersection point
     */
    public GeoPoint getIntersectionPoint() {
        List<IntersectionLine> lines = getLines();
        if (lines.size() < 2)
            return null;

        Matrix I = Matrix.identity(2, 2);
        Matrix inn, innp;
        Matrix A = new Matrix(2, 2), B = new Matrix(2, 1);
        for (IntersectionLine line : lines) {
            inn = I.minus(line.getNormal().times(line.getNormal().transpose()));
            innp = inn.times(line.getPoint());
            A.plusEquals(inn);
            B.plusEquals(innp);
        }
        Matrix ret = A.solve(B);
        return new GeoPoint(ret.get(0, 0), ret.get(1, 0));
    }

    private static class IntersectionLine extends Association {

        private final Marker _landmark;
        private Matrix _normal;
        private Matrix _point;

        IntersectionLine(Marker landmark, Marker end, String uid) {
            super(landmark, end, uid);
            _landmark = landmark;
            setLink(LINK_LINE);
            setStyle(STYLE_DASHED);
            setStrokeWeight(3);
            setColor(Color.GREEN);
            setClampToGround(true);
            setMetaBoolean("addToObjList", false);
        }

        private Matrix getPoint() {
            if (_point == null) {
                GeoPoint p = _landmark.getPoint();
                _point = new Matrix(new double[] {
                        p.getLatitude(), p.getLongitude()
                }, 2);
            }
            return _point;
        }

        private Matrix getNormal() {
            if (_normal == null) {
                double bearing = _landmark.getMetaDouble("landmarkBearing", 0);
                bearing = (bearing + 180d) % 360d;
                double aziRad = Math.toRadians(bearing);
                _normal = new Matrix(new double[] {
                        Math.cos(aziRad),
                        Math.sin(aziRad) / Math.cos(Math.toRadians(
                                _landmark.getPoint().getLatitude()))
                }, 2);
                _normal = _normal.times((double) 1 / _normal.norm2());
            }
            return _normal;
        }

        private void reset() {
            _normal = _point = null;
        }
    }
}
