
package com.atakmap.android.drawing.mapItems;

import android.os.Bundle;

import com.atakmap.android.cot.detail.CotDetailManager;
import com.atakmap.coremap.maps.conversion.EGM96;
import com.atakmap.android.editableShapes.Rectangle;
import com.atakmap.android.gpx.GpxTrack;
import com.atakmap.android.gpx.GpxTrackSegment;
import com.atakmap.android.gpx.GpxWaypoint;
import com.atakmap.android.imagecapture.CapturePP;
import com.atakmap.android.importexport.ExportFilters;
import com.atakmap.android.importexport.Exportable;
import com.atakmap.android.importexport.FormatNotSupportedException;
import com.atakmap.android.importexport.KmlMapItemImportFactory;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.missionpackage.export.MissionPackageExportWrapper;
import com.atakmap.android.routes.RouteGpxIO;
import com.atakmap.app.R;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.cot.event.CotPoint;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.atakmap.spatial.file.export.GPXExportWrapper;
import com.atakmap.spatial.file.export.KMZFolder;
import com.atakmap.spatial.file.export.OGRFeatureExportWrapper;
import com.atakmap.spatial.kml.KMLUtil;
import com.ekito.simpleKML.model.Coordinate;
import com.ekito.simpleKML.model.Data;
import com.ekito.simpleKML.model.ExtendedData;
import com.ekito.simpleKML.model.Feature;
import com.ekito.simpleKML.model.Folder;
import com.ekito.simpleKML.model.Geometry;
import com.ekito.simpleKML.model.IconStyle;
import com.ekito.simpleKML.model.LineStyle;
import com.ekito.simpleKML.model.Placemark;
import com.ekito.simpleKML.model.Point;
import com.ekito.simpleKML.model.PolyStyle;
import com.ekito.simpleKML.model.Polygon;
import com.ekito.simpleKML.model.Style;
import com.ekito.simpleKML.model.StyleSelector;

import org.gdal.ogr.ogr;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DrawingRectangle extends Rectangle implements Exportable {
    private static final String TAG = "DrawingRectangle";
    public static final String KEY_BPHA = "BPHA";

    protected DrawingRectangle(MapGroup mapGroup, String uid) {
        super(mapGroup, uid);
        setMetaBoolean("archive", true);
    }

    public DrawingRectangle(MapGroup mapGroup,
            GeoPointMetaData p0,
            GeoPointMetaData p1,
            GeoPointMetaData p2,
            GeoPointMetaData p3,
            String uid) {
        super(mapGroup, p0, p1, p2, p3, uid);
        setMetaBoolean("archive", true);
    }

    @Override
    protected String getUIDKey() {
        return "shapeUID";
    }

    @Override
    protected String getAssocType() {
        return "rectangle_line";
    }

    @Override
    protected String getCenterMarkerType() {
        return "center_u-d-r";
    }

    @Override
    protected String getMenuPath() {
        return "menus/drawing_rectangle_geofence_menu.xml";
    }

    @Override
    protected String getCotType() {
        return "u-d-r";
    }

    @Override
    public String getCornerPointType() {
        return "corner_u-d-r";
    }

    @Override
    protected String getSideMarkerType() {
        return "side_u-d-r";
    }

    public static class Builder extends Rectangle.Builder {
        public Builder(MapGroup group, Mode mode) {
            super(new DrawingRectangle(group, UUID.randomUUID().toString()),
                    mode);
        }

        @Override
        public DrawingRectangle build() {
            return (DrawingRectangle) super.build();
        }
    }

    public static class KmlDrawingRectangleImportFactory extends
            KmlMapItemImportFactory {
        private static final String TAG = "DrawingRectangleImportFactory";

        @Override
        public MapItem instanceFromKml(Placemark placemark, MapGroup mapGroup)
                throws FormatNotSupportedException {

            Polygon polygon = KMLUtil
                    .getFirstGeometry(placemark, Polygon.class);
            if (polygon == null) {
                Log.e(TAG, "Placemark does not have a Polygon");
                return null;
            }

            String uid = polygon.getId();
            if (polygon.getOuterBoundaryIs() == null
                    || polygon.getOuterBoundaryIs().getLinearRing() == null
                    ||
                    polygon.getOuterBoundaryIs().getLinearRing()
                            .getCoordinates() == null
                    ||
                    polygon.getOuterBoundaryIs().getLinearRing()
                            .getCoordinates().getList() == null) {
                Log.e(TAG, "Placemark does not have a Polygon OuterBoundaryIs");
                return null;
            }

            GeoPointMetaData[] points = KMLUtil.convertCoordinates(polygon
                    .getOuterBoundaryIs()
                    .getLinearRing().getCoordinates());
            if (points == null || points.length < 1) {
                Log.e(TAG,
                        "Placemark does not have a Polygon OuterBoundaryIs points");
                return null;
            }

            String title = placemark.getName();
            int stroke = -1;
            int fill = -1;
            Style style = KMLUtil.getFirstStyle(placemark, Style.class);
            if (style != null && style.getLineStyle() != null)
                stroke = KMLUtil.parseKMLColor(style.getLineStyle().getColor());
            if (style != null && style.getPolyStyle() != null)
                fill = KMLUtil.parseKMLColor(style.getPolyStyle().getColor());

            DrawingRectangle rect = new DrawingRectangle(
                    mapGroup.addGroup(title),
                    points[0], points[1], points[2], points[3], uid);
            rect.setStrokeColor(stroke);
            rect.setFillColor(fill);

            return rect;
        }

        @Override
        public String getFactoryName() {
            return FACTORY_NAME;
        }

        static final String FACTORY_NAME = "u-d-r";
    }

    protected CotEvent toCot() {

        CotEvent cotEvent = new CotEvent();

        CoordinatedTime time = new CoordinatedTime();
        cotEvent.setTime(time);
        cotEvent.setStart(time);
        cotEvent.setStale(time.addDays(1));

        cotEvent.setUID(getUID());
        cotEvent.setVersion("2.0");
        cotEvent.setHow("h-e");

        cotEvent.setPoint(new CotPoint(getCenter().get()));
        cotEvent.setType(getCotType());

        CotDetail detail = new CotDetail("detail");
        cotEvent.setDetail(detail);

        for (int i = 0; i < 4; i++) {
            CotDetail link = new CotDetail("link");
            link.setAttribute("point", getPointAt(i).getPoint().toString());
            detail.addChild(link);
        }

        if (hasMetaValue(KEY_BPHA)) {
            CotDetail bpha = new CotDetail(KEY_BPHA);
            bpha.setAttribute("value", getMetaString(KEY_BPHA, ""));
            detail.addChild(bpha);
        }

        CotDetailManager.getInstance().addDetails(this, cotEvent);

        return cotEvent;
    }

    protected Folder toKml() {
        try {
            // style element
            Style style = new Style();
            IconStyle istyle = new IconStyle();
            istyle.setColor(KMLUtil.convertKmlColor(getColor()));

            //set white pushpin and Google Earth will tint based on color above
            com.ekito.simpleKML.model.Icon icon = new com.ekito.simpleKML.model.Icon();

            String whtpushpin = com.atakmap.android.maps.MapView.getMapView()
                    .getContext()
                    .getString(R.string.whtpushpin);
            icon.setHref(whtpushpin);

            istyle.setIcon(icon);
            style.setIconStyle(istyle);

            LineStyle lstyle = new LineStyle();
            lstyle.setColor(KMLUtil.convertKmlColor(getStrokeColor()));
            lstyle.setWidth(2F);
            style.setLineStyle(lstyle);

            PolyStyle pstyle = new PolyStyle();
            pstyle.setColor(KMLUtil.convertKmlColor(getFillColor()));
            // if fully transparent, then no fill, otherwise check fill mask
            //Note rectangle currently does not have STYLE_FILLED_MASK set by default
            int a = (getFillColor() >> 24) & 0xFF;
            if (a == 0)
                pstyle.setFill(0);
            else
                pstyle.setFill(1);
            pstyle.setOutline(1);
            style.setPolyStyle(pstyle);

            String styleId = KMLUtil.hash(style);
            style.setId(styleId);

            // Folder element containing styles, shape and label
            Folder folder = new Folder();
            if (getGroup() != null
                    && !FileSystemUtils.isEmpty(getGroup().getFriendlyName()))
                folder.setName(getGroup().getFriendlyName());
            else
                folder.setName(getTitle());

            List<StyleSelector> styles = new ArrayList<>();
            styles.add(style);
            folder.setStyleSelector(styles);
            List<Feature> folderFeatures = new ArrayList<>();
            folder.setFeatureList(folderFeatures);

            // Placemark element
            Placemark outerPlacemark = new Placemark();
            outerPlacemark.setId(getUID() + getTitle() + " outer");
            outerPlacemark.setName(getTitle());
            outerPlacemark.setStyleUrl("#" + styleId);
            outerPlacemark.setVisibility(getVisible() ? 1 : 0);

            //First 4 points are the corners
            GeoPointMetaData[] pa = this.getGeoPoints();
            if (pa == null || pa.length < 4) {
                throw new FormatNotSupportedException(
                        "Rectangle missing points");
            }

            GeoPointMetaData[] points = new GeoPointMetaData[] {
                    pa[0], pa[1], pa[2], pa[3]
            };
            MapView mv = MapView.getMapView();
            boolean idlWrap180 = mv != null && mv.isContinuousScrollEnabled()
                    && GeoCalculations.crossesIDL(points, 0, points.length);

            boolean clampToGroundKMLElevation = Double.isNaN(getHeight())
                    || Double.compare(getHeight(), 0.0) == 0;

            // if getHeight is not known, then ignore the altitude otherwise pass in
            // false so that the point is created retative to ground with the appropriate height
            // passed in to the linear ring method.
            Polygon polygon = KMLUtil.createPolygonWithLinearRing(points,
                    getUID(), clampToGroundKMLElevation, idlWrap180,
                    getHeight());
            if (polygon == null) {
                Log.w(TAG, "Unable to create KML Polygon");
                return null;
            }

            List<Geometry> outerGeomtries = new ArrayList<>();
            outerPlacemark.setGeometryList(outerGeomtries);
            outerGeomtries.add(polygon);

            List<Data> dataList = new ArrayList<>();
            Data data = new Data();
            data.setName("factory");
            data.setValue("u-d-r");
            dataList.add(data);
            ExtendedData edata = new ExtendedData();
            edata.setDataList(dataList);
            outerPlacemark.setExtendedData(edata);
            folderFeatures.add(outerPlacemark);

            Coordinate coord = KMLUtil.convertKmlCoord(this.getCenter(), true);
            if (coord == null) {
                Log.w(TAG, "No center marker location set");
            } else {
                Point centerPoint = new Point();
                centerPoint.setAltitudeMode("clampToGround");
                centerPoint.setCoordinates(coord);

                // icon for the middle of this drawing rectangle
                Placemark centerPlacemark = new Placemark();
                centerPlacemark.setId(getUID() + getTitle() + " center");
                centerPlacemark.setName(getTitle());
                centerPlacemark.setVisibility(getVisible() ? 1 : 0);
                centerPlacemark.setStyleUrl("#" + styleId);

                List<Geometry> centerGeomtries = new ArrayList<>();
                centerGeomtries.add(centerPoint);
                centerPlacemark.setGeometryList(centerGeomtries);
                folderFeatures.add(centerPlacemark);
            }

            return folder;
        } catch (Exception e) {
            Log.e(TAG,
                    "Export of DrawingRectangle to KML failed with Exception",
                    e);
        }

        return null;
    }

    protected KMZFolder toKmz() {
        Folder f = toKml();
        if (f == null)
            return null;
        return new KMZFolder(f);
    }

    protected OGRFeatureExportWrapper toOgrGeometry()
            throws FormatNotSupportedException {
        org.gdal.ogr.Geometry geometry = new org.gdal.ogr.Geometry(
                org.gdal.ogr.ogrConstants.wkbLineString);
        GeoPointMetaData[] pa = this.getGeoPoints();
        if (pa == null || pa.length < 4) {
            throw new FormatNotSupportedException("Rectangle missing points");
        }

        GeoPointMetaData[] points = new GeoPointMetaData[] {
                pa[0], pa[1], pa[2], pa[3]
        };

        double unwrap = 0;
        MapView mv = MapView.getMapView();
        if (mv != null && mv.isContinuousScrollEnabled()
                && GeoCalculations.crossesIDL(points, 0, points.length))
            unwrap = 360;

        for (GeoPointMetaData point : points)
            OGRFeatureExportWrapper.addPoint(geometry, point.get(), unwrap);

        //loop back to the first point
        OGRFeatureExportWrapper.addPoint(geometry, points[0].get(), unwrap);

        String name = getTitle();
        String groupName = name;
        if (getGroup() != null) {
            groupName = getGroup().getFriendlyName();
        }
        return new OGRFeatureExportWrapper(groupName, ogr.wkbLineString,
                new OGRFeatureExportWrapper.NamedGeometry(geometry, name));
    }

    protected GPXExportWrapper toGpx() throws FormatNotSupportedException {
        //add center point
        GeoPointMetaData point = getCenter();
        GpxWaypoint wp = null;
        if (point != null) {
            wp = new GpxWaypoint();
            wp.setLat(point.get().getLatitude());
            wp.setLon(point.get().getLongitude());

            if (point.get().isAltitudeValid()) {
                // This seems like it should be MSL.   Not documented in the spec
                // https://productforums.google.com/forum/#!topic/maps/ThUvVBoHAvk
                final double alt = EGM96.getMSL(point.get());
                wp.setEle(alt);
            }

            wp.setName(getTitle() + " Center");
            wp.setDesc(getUID() + " " + getMetaString("remarks", null));
        }

        //now add rectangle
        GpxTrack t = new GpxTrack();
        t.setName(getTitle());
        t.setDesc(getUID());

        List<GpxTrackSegment> trkseg = new ArrayList<>();
        t.setSegments(trkseg);
        GpxTrackSegment seg = new GpxTrackSegment();
        trkseg.add(seg);
        List<GpxWaypoint> trkpt = new ArrayList<>();
        seg.setPoints(trkpt);

        GeoPointMetaData[] pa = this.getGeoPoints();
        if (pa == null || pa.length < 4) {
            throw new FormatNotSupportedException("Rectangle missing points");
        }

        GeoPointMetaData[] points = new GeoPointMetaData[] {
                pa[0], pa[1], pa[2], pa[3]
        };

        double unwrap = 0;
        MapView mv = MapView.getMapView();
        if (mv != null && mv.isContinuousScrollEnabled()
                && GeoCalculations.crossesIDL(points, 0, points.length))
            unwrap = 360;

        for (GeoPointMetaData point1 : points)
            trkpt.add(RouteGpxIO.convertPoint(point1.get(), unwrap));

        //loop back to the first point        
        trkpt.add(RouteGpxIO.convertPoint(points[0].get(), unwrap));

        GPXExportWrapper folder = new GPXExportWrapper();
        if (wp != null)
            folder.getExports().add(wp);
        folder.getExports().add(t);
        return folder;
    }

    @Override
    public boolean isSupported(Class<?> target) {
        return CotEvent.class.equals(target) ||
                Folder.class.equals(target) ||
                KMZFolder.class.equals(target) ||
                MissionPackageExportWrapper.class.equals(target) ||
                GPXExportWrapper.class.equals(target) ||
                OGRFeatureExportWrapper.class.equals(target);
    }

    @Override
    public Object toObjectOf(Class<?> target, ExportFilters filters)
            throws FormatNotSupportedException {
        if (filters != null && filters.filter(this))
            return null;

        if (CotEvent.class.equals(target)) {
            return toCot();
        } else if (Folder.class.equals(target)) {
            return toKml();
        } else if (KMZFolder.class.equals(target)) {
            return toKmz();
        } else if (MissionPackageExportWrapper.class.equals(target)) {
            return Marker.toMissionPackage(this);
        } else if (GPXExportWrapper.class.equals(target)) {
            return toGpx();
        } else if (OGRFeatureExportWrapper.class.equals(target)) {
            return toOgrGeometry();
        }

        return null;
    }

    @Override
    public Bundle preDrawCanvas(CapturePP capture) {
        // Let the lines draw themselves
        return null;
    }

    @Override
    public void drawCanvas(CapturePP capture, Bundle data) {
        // Let the lines draw themselves
    }

}
