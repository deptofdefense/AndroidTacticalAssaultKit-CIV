
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
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.app.R;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.atakmap.map.layer.feature.Feature.AltitudeMode;
import com.atakmap.spatial.file.export.GPXExportWrapper;
import com.atakmap.spatial.file.export.KMZFolder;
import com.atakmap.spatial.file.export.OGRFeatureExportWrapper;
import com.ekito.simpleKML.model.Feature;
import com.ekito.simpleKML.model.Folder;

import org.gdal.ogr.ogr;

import java.util.ArrayList;
import java.util.List;

/**
 * A polyline containing multiple children lines
 */
public class MultiPolyline extends DrawingShape implements Exportable {

    private static final String TAG = "Multi-Polyline";

    private final MapView _mapView;
    private String _shapeMarkerType;
    private final List<DrawingShape> _lines = new ArrayList<>();

    /**
     * Constructor that sets up all the menus, types, and metadata for the object
     * @param mapView Map view instance
     * @param mapGroup The map group this polyline will be added to
     * @param uid Unique identifier
     */
    public MultiPolyline(MapView mapView, MapGroup mapGroup, final String uid) {
        super(mapView, mapGroup, uid);
        _mapView = mapView;
        setType("u-d-f-m");
        setMovable(true);
        setRadialMenu(getShapeMenu());
        setShapeMenu("menus/multipolyline_shape_menu.xml");
        setMetaString("iconUri", ATAKUtilities.getResourceUri(
                R.drawable.multipolyline));
        setMetaBoolean("archive", true);
        toggleMetaData("ignoreRender", true);
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
        setLines(lines);
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

    /**
     * Set the lines used by this multi-polyline
     * @param lines List of lines to set
     */
    public void setLines(List<DrawingShape> lines) {
        List<DrawingShape> removed;
        synchronized (_lines) {
            removed = new ArrayList<>(_lines);
            _lines.clear();
            if (lines != null) {
                _lines.addAll(lines);
                removed.removeAll(lines);
            }
        }
        refresh();
        setPoints();
        for (DrawingShape ds : removed)
            onLineRemoved(ds);
    }

    /**
     * Simple getter to return all of the lines associated with this multi-polyline
     * @return an array list of drawing shapes
     */
    public List<DrawingShape> getLines() {
        synchronized (_lines) {
            return new ArrayList<>(_lines);
        }
    }

    /**
     * Sets the points of the multi-polyline, don't think we really need this but keeping
     * in for now.
     */
    public void setPoints() {
        List<GeoPointMetaData> all = new ArrayList<>();
        for (DrawingShape shape : getLines()) {
            all.addAll(shape._points);
            shape.setHeight(getHeight());
            shape.setHeightStyle(HEIGHT_STYLE_POLYGON
                    | HEIGHT_STYLE_OUTLINE_SIMPLE);
        }
        super.setPoints(all.toArray(new GeoPointMetaData[0]));
    }

    public void move(GeoPointMetaData oldPoint, GeoPointMetaData newPoint) {
        for (DrawingShape ds : getLines())
            ds.moveClosedSet(oldPoint, newPoint);
        //Set the multi-polyline's points
        setPoints();
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
        List<DrawingShape> lines = getLines();
        for (DrawingShape ds : lines) {
            int color = ds.getStrokeColor();
            if (lastColor != null && color != lastColor)
                return Color.WHITE;
            lastColor = color;
        }
        return lastColor == null ? Color.WHITE : lastColor;
    }

    /**
     * Check if this multi-polyline has no sub-lines attached to it
     * @return True if this line is empty
     */
    public boolean isEmpty() {
        synchronized (_lines) {
            return _lines.isEmpty();
        }
    }

    /**
     * A function to remove a drawing shape from the multi-polyline object
     * @param ds - The drawing shape to remove
     */
    public void removeLine(DrawingShape ds) {
        synchronized (_lines) {
            _lines.remove(ds);
        }
        onLineRemoved(ds);
        setPoints();
    }

    /**
     * Function that adds a drawing shape to the list
     * @param ds - Drawing shape to add
     */
    public void addLine(DrawingShape ds) {
        if (ds == null)
            return;
        synchronized (_lines) {
            _lines.add(ds);
        }
        refresh(ds);
        setPoints();
    }

    @Override
    public void setTitle(final String title) {
        final String prevTitle = getTitle();
        super.setTitle(title);

        // only call refresh if the title actually changes
        if (!FileSystemUtils.isEquals(title, prevTitle))
            refresh();
    }

    @Override
    public void toggleMetaData(String key, boolean value) {
        super.toggleMetaData(key, value);
        if (key.equals("labels_on"))
            refresh();
    }

    @Override
    protected void onStrokeStyleChanged() {
        super.onStrokeStyleChanged();
        refresh();
    }

    @Override
    public void setStrokeWeight(double strokeWeight) {
        super.setStrokeWeight(strokeWeight);
        refresh();
    }

    @Override
    public void setMovable(boolean movable) {
        super.setMovable(movable);
        refresh();
    }

    @Override
    public void setAltitudeMode(AltitudeMode altitudeMode) {
        super.setAltitudeMode(altitudeMode);
        refresh();
    }

    @Override
    public void setHeight(double height) {
        super.setHeight(height);
        refresh();
    }

    @Override
    public void onVisibleChanged() {
        super.onVisibleChanged();
        refresh();
    }

    /**
     * Refresh line metadata so it matches the parent line
     * @param line Line to refresh metadata for
     */
    private void refresh(DrawingShape line) {
        if (line.getGroup() != getChildMapGroup())
            getChildMapGroup().addItem(line);
        line.setTitle(getTitle());
        line.setMovable(getMovable());
        line.setVisible(getVisible());
        line.setStrokeWeight(getStrokeWeight());
        line.setAltitudeMode(getAltitudeMode());
        line.setHeight(getHeight());
        line.setClickable(getClickable());
        line.setLineStyle(getLineStyle());
        line.setStrokeStyle(getStrokeStyle());
        line.toggleMetaData("labels_on", hasMetaValue("labels_on"));
        line.setMetaString("shapeUID", getUID());
        line.setMetaBoolean("addToObjList", false);
        line.setShapeMenu(getShapeMenu());
    }

    private void refresh(List<DrawingShape> lines) {
        for (DrawingShape line : lines)
            refresh(line);
    }

    /**
     * Refresh metadata on all lines to match parent
     */
    private void refresh() {
        if (_lines == null)
            return;
        synchronized (_lines) {
            refresh(_lines);
        }
    }

    private void onLineRemoved(DrawingShape ds) {
        if (ds.getGroup() == getChildMapGroup())
            getChildMapGroup().removeItem(ds);
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

        synchronized (_lines) {
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
        synchronized (_lines) {
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

        synchronized (_lines) {
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
        synchronized (_lines) {
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
        synchronized (_lines) {
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
        synchronized (_lines) {
            for (int i = 0; i < data.getInt("lineCount", 0)
                    && i < _lines.size(); i++) {
                Bundle lineData = new Bundle();
                lineData.putSerializable("points",
                        data.getSerializable("line" + i));
                _lines.get(i).drawCanvas(cap, lineData);
            }
        }
    }

    /* Deprecated methods */

    /**
     * @deprecated Use {@link #addLine(DrawingShape)}
     */
    @Deprecated
    @DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
    public void add(DrawingShape ds) {
        addLine(ds);
    }

    /**
     * @deprecated Use {@link #removeLine(DrawingShape)}
     */
    @Deprecated
    @DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
    public void removeItem(DrawingShape ds) {
        removeLine(ds);
    }

    /**
     * @deprecated Use {@link #getUID()}
     */
    @Deprecated
    @DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
    public String UID() {
        return getUID();
    }

    /**
     * @deprecated Use {@link MapView#getMapView()}
     */
    @Deprecated
    @DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
    public MapView getView() {
        return _mapView;
    }

    /**
     * @deprecated Use {@link #getGroup()}
     */
    @Deprecated
    @DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
    public MapGroup getMapGroup() {
        return getGroup();
    }

    /**
     * @deprecated Use {@link #getLines()}
     */
    @Deprecated
    @DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
    public List<DrawingShape> get_lines() {
        return getLines();
    }

    /**
     * @deprecated Call {@link DrawingShape#setColor(int)} on line instead
     */
    @Deprecated
    @DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
    public void setColor(final int color, final Point p) {
    }

    /**
     * @deprecated Call {@link #removeLine(DrawingShape)} on line instead
     */
    @Deprecated
    @DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
    public void removeItem(Point p) {
    }
}
