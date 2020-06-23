
package com.atakmap.android.grg;

import android.graphics.PointF;

import com.atakmap.android.importexport.ExportFilters;
import com.atakmap.android.importexport.Exportable;
import com.atakmap.android.importexport.FormatNotSupportedException;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.missionpackage.export.MissionPackageExportWrapper;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.feature.geometry.Polygon;
import com.atakmap.map.layer.raster.DatasetDescriptor;

public class ImageOverlay extends Shape implements Exportable {

    private static final String TAG = "ImageOverlay";

    private final DatasetDescriptor layerInfo;
    private final float _hitRadius = 32 * MapView.DENSITY;
    private boolean bEnableMapTouch;

    ImageOverlay(DatasetDescriptor layerInfo, final String uid,
            boolean bEnableTouch) {
        super(uid);
        this.layerInfo = layerInfo;

        this.setZOrder(3d + this.layerInfo.getMaxResolution(null));
        this.bEnableMapTouch = bEnableTouch;
        this.setClickable(true);
        setMetaBoolean("editable", false);
        setMetaString("fileUri", this.layerInfo.getUri());
        setMetaString("menu", "menus/grg_menu.xml");
    }

    public DatasetDescriptor getLayerInfo() {
        return this.layerInfo;
    }

    public String toString() {
        return this.layerInfo.getName();
    }

    @Override
    public GeoPointMetaData getCenter() {
        Envelope mbb = this.layerInfo.getMinimumBoundingBox();
        return GeoPointMetaData
                .wrap(new GeoPoint((mbb.maxY + mbb.minY) / 2.0d,
                        (mbb.maxX + mbb.minX) / 2.0d));
    }

    public void enableMapTouch(boolean b) {
        bEnableMapTouch = b;
    }

    // placed here for 3.2
    // TODO: relocate
    private final MutableGeoBounds scratchBounds = new MutableGeoBounds(0, 0,
            0, 0);
    private final PointF scratch_lr = new PointF();
    private final PointF scratch_ul = new PointF();

    private boolean testOrthoHit(int xpos, int ypos, MapView view,
            GeoBounds _bounds) {

        scratch_ul.x = xpos - _hitRadius;
        scratch_ul.y = ypos - _hitRadius;

        scratch_lr.x = xpos + _hitRadius;
        scratch_lr.y = ypos + _hitRadius;

        final GeoPoint ul = view.inverse(scratch_ul,
                MapView.InverseMode.RayCast).get();
        final GeoPoint lr = view.inverse(scratch_lr,
                MapView.InverseMode.RayCast).get();

        scratchBounds.set(ul, lr);

        return _bounds.intersects(scratchBounds) &&
                (!_bounds.contains(ul) || !_bounds.contains(lr));
    }

    @Override
    public boolean testOrthoHit(final int xpos, final int ypos,
            final GeoPoint point, final MapView view) {
        if (!bEnableMapTouch) {
            return false;
        }
        final GeoBounds bounds = getBounds(null);
        boolean retVal = testOrthoHit(xpos, ypos, view, bounds);
        if (retVal) {
            setMetaString("menu_point", point.toString());
            setTouchPoint(point);
        }
        return retVal;
    }

    @Override
    public GeoPoint[] getPoints() {
        Geometry geom = this.layerInfo.getCoverage(null);
        LineString quad;
        if (geom instanceof Polygon) {
            quad = ((Polygon) geom).getExteriorRing();
        } else if (geom instanceof LineString) {
            quad = (LineString) geom;
        } else {
            Envelope mbb = this.layerInfo.getMinimumBoundingBox();

            quad = new LineString(2);
            quad.addPoint(mbb.minX, mbb.maxY);
            quad.addPoint(mbb.maxX, mbb.maxY);
            quad.addPoint(mbb.maxX, mbb.minY);
            quad.addPoint(mbb.minX, mbb.minY);
        }

        GeoPoint[] retval = new GeoPoint[quad.isClosed()
                ? quad.getNumPoints() - 1
                : quad.getNumPoints()];
        for (int i = 0; i < retval.length; i++) {
            retval[i] = new GeoPoint(quad.getY(i), quad.getX(i));
        }
        return retval;
    }

    @Override
    public GeoPointMetaData[] getMetaDataPoints() {
        return GeoPointMetaData.wrap(getPoints());
    }

    @Override
    public GeoBounds getBounds(MutableGeoBounds bounds) {
        Envelope mbb = this.layerInfo.getMinimumBoundingBox();

        if (bounds != null) {
            bounds.set(mbb.minY, mbb.minX, mbb.maxY, mbb.maxX);
            return bounds;
        } else {
            return new GeoBounds(mbb.minY, mbb.minX, mbb.maxY, mbb.maxX);
        }
    }

    @Override
    public boolean isSupported(Class target) {
        return MissionPackageExportWrapper.class.equals(target);
    }

    @Override
    public Object toObjectOf(Class target, ExportFilters filters)
            throws FormatNotSupportedException {
        if (!isSupported(target)) {
            //nothing to export
            return null;
        }

        if (MissionPackageExportWrapper.class.equals(target)) {
            return toMissionPackage(filters);
        }

        return null;
    }

    private MissionPackageExportWrapper toMissionPackage(
            ExportFilters filters) {
        if (filters != null && filters.filter(this))
            return null;

        String file = getMetaString("file", null);
        if (!FileSystemUtils.isFile(file)) {
            Log.w(TAG, "Cannot export without file");
        }

        MissionPackageExportWrapper export = new MissionPackageExportWrapper();
        export.getFilepaths().add(file);
        return export;
    }
}
