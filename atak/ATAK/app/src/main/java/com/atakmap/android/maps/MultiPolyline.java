
package com.atakmap.android.maps;

import android.graphics.Color;
import android.graphics.Point;
import android.os.Bundle;

import com.atakmap.android.cot.detail.CotDetailManager;
import com.atakmap.android.drawing.mapItems.DrawingShape;
import com.atakmap.android.gpx.GpxTrack;
import com.atakmap.android.gpx.GpxTrackSegment;
import com.atakmap.android.gpx.GpxWaypoint;
import com.atakmap.android.imagecapture.CapturePP;
import com.atakmap.android.importexport.ExportFilters;
import com.atakmap.android.importexport.Exportable;
import com.atakmap.android.missionpackage.export.MissionPackageExportWrapper;
import com.atakmap.android.routes.RouteGpxIO;
import com.atakmap.app.R;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.atakmap.spatial.file.export.GPXExportWrapper;
import com.atakmap.spatial.file.export.KMZFolder;
import com.atakmap.spatial.file.export.OGRFeatureExportWrapper;
import com.ekito.simpleKML.model.Feature;
import com.ekito.simpleKML.model.Folder;

import org.gdal.ogr.ogr;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class MultiPolyline extends DrawingShape implements Exportable {

    private String _shapeMarkerType;
    private static final String TAG = "Multi-Polyline";
    private final MapGroup childItemMapGroup;
    private List<DrawingShape> _lines;
    private final MapView _mapView;
    private final MapGroup mapGroup;
    private final String _uid;
    private final Object lock = new Object();
    private final MutableGeoBounds _scratchBounds = new MutableGeoBounds(0, 0,
            0, 0);

    /**
     * Constructor that sets up all the menus, types, and metadata for the object
     * @param mapView Map view instance
     * @param mapGroup The map group this polyline will be added to
     * @param uid Unique identifier
     */
    public MultiPolyline(MapView mapView, MapGroup mapGroup, final String uid) {
        super(mapView, mapGroup, uid);
        this.childItemMapGroup = mapGroup.addGroup();
        this._mapView = mapView;
        this._uid = uid;
        this.setClickable(true);
        this.setMovable(true);
        this.mapGroup = mapGroup;
        setMetaString("menu", getShapeMenu());
        this.setShapeMenu("menus/multipolyline_shape_menu.xml");
        this.setMetaString("iconUri", "android.resource://"
                + mapView.getContext().getPackageName()
                + "/" + R.drawable.multipolyline);
        this.setMetaBoolean("moveable", true);
        setMetaBoolean("archive", true);
        _lines = new ArrayList<>();
        //this.setMarkerPointType("shape");
        this.setType("u-d-f-m");
    }

    /**
     * Alternate constructor for constructing from COT event when we already have lines
     * @param mapView The view to construct the multi polyline with.
     * @param mapGroup The group to place the polyline in
     * @param lines The lines that make up the polyline
     * @param uid The unique identifier for the new multi polyline
     */
    public MultiPolyline(MapView mapView, MapGroup mapGroup,
            List<DrawingShape> lines,
            final String uid) {
        this(mapView, mapGroup, uid);
        this._lines = lines;
    }

    public String UID() {
        return _uid;
    }

    @Override
    public void setTitle(final String title) {
        super.setTitle(title);
        synchronized (lock) {
            for (DrawingShape ds : _lines)
                ds.setTitle(title);
        }
    }

    @Override
    public void toggleMetaData(String key, boolean value) {
        super.toggleMetaData(key, value);
        if (key.equals("labels_on")) {
            synchronized (lock) {
                for (DrawingShape ds : _lines)
                    ds.toggleMetaData(key, value);
            }
        }
    }

    //Various getters/setters for variables

    @Override
    public MapGroup getChildMapGroup() {
        return this.childItemMapGroup;
    }

    public MapView getView() {
        return this._mapView;
    }

    public MapGroup getMapGroup() {
        return this.mapGroup;
    }

    @Override
    protected String getMarkerPointType() {
        if (_shapeMarkerType == null) {
            _shapeMarkerType = "shape_marker";
        }
        return _shapeMarkerType;
    }

    @Override
    protected String getShapeMenu() {
        return getMetaString("shapeMenu", "menus/multipolyline_shape_menu.xml");
    }

    @Override
    protected String getCotType() {
        return "u-d-f-m";
    }

    public void setLines(List<DrawingShape> lines) {
        synchronized (lock) {
            this._lines = lines;
        }
        setPoints();
        toggleMetaData("labels_on", hasMetaValue("labels_on"));
    }

    /**
     * Sets the points of the multi-polyline, don't think we really need this but keeping
     * in for now.
     */
    public void setPoints() {
        List<GeoPointMetaData> all = new ArrayList<>();
        synchronized (lock) {
            for (DrawingShape shape : _lines) {
                all.addAll(shape._points);
                shape.setHeight(getHeight());
                shape.setHeightStyle(HEIGHT_STYLE_POLYGON
                        | HEIGHT_STYLE_OUTLINE_SIMPLE);
            }
        }
        super.setPoints(all.toArray(new GeoPointMetaData[0]));
    }

    @Override
    public void setHeight(double height) {
        super.setHeight(height);
        synchronized (lock) {
            for (DrawingShape shape : _lines)
                shape.setHeight(height);
        }
    }

    public void move(GeoPointMetaData oldPoint, GeoPointMetaData newPoint) {
        List<DrawingShape> moveRefresh = new ArrayList<>();
        synchronized (lock) {
            for (DrawingShape ds : this._lines) {
                ds.moveClosedSet(oldPoint, newPoint);
                moveRefresh.add(ds);
            }
            //Set the multi-polyline's points and refresh
            setPoints();
        }
        for (DrawingShape ds : moveRefresh)
            ds.refresh(mapView.getMapEventDispatcher(), null, getClass());
        refresh(mapView.getMapEventDispatcher(), null, getClass());
    }

    @Override
    public boolean testOrthoHit(int xpos, int ypos, GeoPoint point,
            MapView view) {
        if (!isTouchable())
            return false;
        synchronized (lock) {
            for (DrawingShape shape : _lines) {
                if (shape.testOrthoHit(xpos, ypos, point, view)) {
                    GeoPoint gp = shape.findTouchPoint();
                    setMetaString("hit_type", shape.getMetaString(
                            "hit_type", null));
                    setMetaInteger("hit_index", shape.getMetaInteger(
                            "hit_index", 0));
                    setMetaString("menu", getShapeMenu());
                    setMetaString("menu_point", gp.toString());
                    setTouchPoint(gp);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public int getStrokeColor() {
        return Color.WHITE;
    }

    @Override
    public int getIconColor() {
        // If the entire multi-polyline is made up of 1 color, use that
        // Otherwise use white
        Integer lastColor = null;
        List<DrawingShape> lines = get_lines();
        for (DrawingShape ds : lines) {
            int color = ds.getStrokeColor();
            if (lastColor != null && color != lastColor)
                return Color.WHITE;
            lastColor = color;
        }
        return lastColor == null ? Color.WHITE : lastColor;
    }

    /**
     * Simple getter to return all of the lines associated with this multi-polyline
     * @return an array list of drawing shapes
     */
    public List<DrawingShape> get_lines() {
        List<DrawingShape> retval;
        synchronized (lock) {
            retval = new ArrayList<>(_lines);
        }
        return retval;
    }

    /**
     * A function to remove a drawing shape from the multi-polyline object
     * @param ds - The drawing shape to remove
     */
    public void removeItem(DrawingShape ds) {
        synchronized (lock) {
            _lines.remove(ds);
        }
        setPoints();
    }

    /**
     * Function that adds a drawing shape to the list
     * @param ds - Drawing shape to add
     */
    public void add(DrawingShape ds) {
        if (ds == null)
            return;
        synchronized (lock) {
            _lines.add(ds);
        }
        setPoints();
        ds.toggleMetaData("labels_on", hasMetaValue("labels_on"));
    }

    /**
     * Function for removing a drawing shape from the multi-polyline object
     * based on a point that was pressed on the screen
     * @param p - The point of the screen that was touched
     */
    public void removeItem(Point p) {
        int _currentX = p.x;
        int _currentY = p.y;
        GeoPoint _geoPoint = _mapView.inverse(_currentX,
                _currentY, MapView.InverseMode.RayCast).get();
        boolean changed = false;
        synchronized (lock) {
            Iterator<DrawingShape> dsIte = this._lines.iterator();
            while (dsIte.hasNext()) {
                DrawingShape ds = dsIte.next();
                //For every individual line see if the user touched it
                if (ds.testOrthoHit(_currentX, _currentY, _geoPoint,
                        _mapView)) {
                    //If they did hit it remove it
                    dsIte.remove();
                    changed = true;
                }
            }
        }
        // Refresh points
        if (changed)
            setPoints();
        //Refresh because that seems like the standard
        this.refresh(this.mapView.getMapEventDispatcher(), null,
                this.getClass());
    }

    @Override
    public void setStrokeWeight(double strokeWeight) {
        super.setStrokeWeight(strokeWeight);
        synchronized (lock) {
            for (DrawingShape ds : this._lines) {
                ds.setStrokeWeight(strokeWeight);
            }
        }
    }

    @Override
    public void setMovable(boolean movable) {
        super.setMovable(movable);

        synchronized (lock) {
            if (this._lines == null)
                return;

            //Loop through the iterator
            for (DrawingShape ds : this._lines) {
                ds.setMovable(movable);
            }
        }
    }

    /**
     * A function that looks through all of the individual lines in this object
     * sees if the user touched it, and if so change that lines color
     * @param color - The color to set a line to
     * @param p - The point on the screen the user touched
     */
    public void setColor(final int color, final Point p) {
        int _currentX = p.x;
        int _currentY = p.y;
        GeoPoint _geoPoint = _mapView.inverse(_currentX,
                _currentY, MapView.InverseMode.RayCast).get();

        List<DrawingShape> updateList = new ArrayList<>();
        synchronized (lock) {
            //Loop through the iterator
            for (DrawingShape ds : this._lines) {
                int _alpha = ds.getFillColor() >>> 24;
                //See if the user hit the line
                if (ds.testOrthoHit(_currentX, _currentY, _geoPoint,
                        _mapView)) {
                    //If they did set the color
                    ds.setStrokeColor(color);
                    ds.setFillColor(Color.argb(_alpha, Color.red(color),
                            Color.green(color), Color.blue(color)));

                    updateList.add(ds);
                }
            }
        }

        // do not fire the refresh in the synchronization block
        for (DrawingShape ds : updateList)
            ds.refresh(this.mapView.getMapEventDispatcher(), null,
                    this.getClass());

        //Refresh because thats what we do
        this.refresh(this.mapView.getMapEventDispatcher(), null,
                this.getClass());
    }

    /**
     * Function to get the bounds of the multi-polyline object. Simply gets
     * the bounds of each individual line and adds them up
     * @param bounds bounds to add
     * @return GeoBounds object representing the bounds of the multi-polyline object
     */
    @Override
    public GeoBounds getBounds(MutableGeoBounds bounds) {
        //Loop through individual lines
        double west = 180, east = -180, north = -90, south = 90;
        synchronized (lock) {
            for (DrawingShape shape : _lines) {
                shape.getBounds(_scratchBounds);
                west = Math.min(west, _scratchBounds.getWest());
                south = Math.min(south, _scratchBounds.getSouth());
                east = Math.max(east, _scratchBounds.getEast());
                north = Math.max(north, _scratchBounds.getNorth());
            }
        }
        this.minimumBoundingBox.set(north, west, south, east);

        if (bounds != null) {
            bounds.set(this.minimumBoundingBox);
            return bounds;
        }
        return new GeoBounds(this.minimumBoundingBox);
    }

    /**
     * Produce a CoT message that represents the MultiPolyline
     */
    @Override
    public CotEvent toCot() {

        CotEvent event = new CotEvent();

        //Set a bunch of details to the COT message
        CoordinatedTime time = new CoordinatedTime();
        event.setTime(time);
        event.setStart(time);
        event.setStale(time.addDays(1));

        event.setUID(getUID());
        event.setVersion("2.0");
        event.setHow("h-e");

        event.setType(getCotType());

        CotDetail detail = new CotDetail("detail");
        event.setDetail(detail);

        // For each of the lines in the multipolyline add there toCot string as a link

        synchronized (lock) {
            for (DrawingShape line : _lines) {
                line.setTitle(getTitle());
                CotDetail link = new CotDetail("link");
                link.setAttribute("line", line.toCot().toString());
                detail.addChild(link);
            }
        }

        CotDetailManager.getInstance().addDetails(this, event);

        //Return the newly created object
        return event;
    }

    /**
     * Function to check if we are able to convert a multipolyline to a certain format
     * @param target the target format
     * @return boolean based on whether we are able to convert a multipolyline to the
     * target format
     */
    @Override
    public boolean isSupported(Class<?> target) {
        return CotEvent.class.equals(target) ||
                Folder.class.equals(target) ||
                KMZFolder.class.equals(target) ||
                MissionPackageExportWrapper.class.equals(target) ||
                GPXExportWrapper.class.equals(target) ||
                OGRFeatureExportWrapper.class.equals(target);
    }

    /**
     * Function that will convert a multipolyline to an instance of a specific format
     * kml,kmz,cot,etc
     * @param target The type to convert the multipolyline to
     * @param filters    Allows <code>ExportMarshal</code> instances to filter e.g. based
     *     on geographic region or other criteria
     * @return the multipolyline in the form of the target
     */
    @Override
    public Object toObjectOf(Class<?> target, ExportFilters filters) {

        if (filters != null && filters.filter(this))
            return null;

        if (CotEvent.class.equals(target))
            return toCot();
        else if (MissionPackageExportWrapper.class.equals(target))
            return Marker.toMissionPackage(this);
        else if (Folder.class.equals(target))
            return toKml();
        else if (KMZFolder.class.equals(target))
            return toKmz();
        else if (GPXExportWrapper.class.equals(target))
            return toGpx();
        else if (OGRFeatureExportWrapper.class.equals(target))
            return toOgrGeometry();

        return null;
    }

    @Override
    protected Folder toKml() {
        synchronized (lock) {
            String title = getTitle();
            if (title == null)
                title = TAG;
            List<Feature> subFolders = new ArrayList<>();
            for (int i = 0; i < _lines.size(); i++) {
                Folder sub = (Folder) _lines.get(i).toObjectOf(Folder.class,
                        null);
                if (sub == null)
                    continue;
                String name = title + " (" + (i + 1) + ")";
                sub.setName(name);
                for (Feature f : sub.getFeatureList())
                    f.setName(name);
                subFolders.add(sub);
            }
            if (subFolders.isEmpty())
                return null;
            Folder folder = new Folder();
            folder.setName(title);
            folder.setFeatureList(subFolders);
            return folder;
        }
    }

    @Override
    protected KMZFolder toKmz() {
        Folder kml = toKml();
        return kml == null ? null : new KMZFolder(kml);
    }

    @Override
    protected GPXExportWrapper toGpx() {
        GpxTrack t = new GpxTrack();
        t.setName(getTitle() + " - ");
        t.setDesc(getUID());

        List<GpxTrackSegment> trkseg = new ArrayList<>();
        t.setSegments(trkseg);

        synchronized (lock) {
            for (DrawingShape line : _lines) {
                GpxTrackSegment seg = new GpxTrackSegment();
                trkseg.add(seg);
                List<GpxWaypoint> trkpt = new ArrayList<>();
                seg.setPoints(trkpt);
                double unwrap = 0;
                if (line.getBounds(null).crossesIDL())
                    unwrap = 360;
                GeoPoint[] points = line.getPoints();
                for (GeoPoint gp : points)
                    trkpt.add(RouteGpxIO.convertPoint(gp, unwrap));
            }
        }

        if (trkseg.isEmpty())
            return null;

        return new GPXExportWrapper(t);
    }

    private OGRFeatureExportWrapper toOgrGeometry() {
        List<OGRFeatureExportWrapper.NamedGeometry> geomList = new ArrayList<>();
        String name = getTitle() + " line ";
        synchronized (lock) {
            for (int i = 0; i < _lines.size(); i++) {
                DrawingShape line = _lines.get(i);
                double unwrap = 0;
                if (line.getBounds(null).crossesIDL())
                    unwrap = 360;
                org.gdal.ogr.Geometry g = new org.gdal.ogr.Geometry(
                        org.gdal.ogr.ogrConstants.wkbLineString);
                GeoPoint[] points = line.getPoints();
                for (GeoPoint gp : points)
                    OGRFeatureExportWrapper.addPoint(g, gp, unwrap);
                geomList.add(new OGRFeatureExportWrapper.NamedGeometry(
                        g, name + (i + 1)));
            }
        }

        if (geomList.isEmpty())
            return null;

        String groupName = getTitle() + " lines";
        if (getGroup() != null)
            groupName = getGroup().getFriendlyName();
        OGRFeatureExportWrapper ret = new OGRFeatureExportWrapper(groupName);
        ret.addGeometries(ogr.wkbLineString, geomList);
        return ret;
    }

    @Override
    public Bundle preDrawCanvas(CapturePP capture) {
        // Store forward returns for each line
        Bundle data = new Bundle();
        int lineNum = 0;
        synchronized (lock) {
            for (DrawingShape line : _lines) {
                Bundle lineData = line.preDrawCanvas(capture);
                data.putSerializable("line" + (lineNum++),
                        lineData.getSerializable("points"));
            }
        }
        data.putInt("lineCount", lineNum);
        return data;
    }

    @Override
    public void drawCanvas(CapturePP cap, Bundle data) {
        // Draw each line
        synchronized (lock) {
            for (int i = 0; i < data.getInt("lineCount", 0)
                    && i < _lines.size(); i++) {
                Bundle lineData = new Bundle();
                lineData.putSerializable("points",
                        data.getSerializable("line" + i));
                _lines.get(i).drawCanvas(cap, lineData);
            }
        }
    }
}
