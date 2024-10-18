
package com.atakmap.android.grg;

import com.atakmap.android.importexport.ExportFilters;
import com.atakmap.android.importexport.Exportable;
import com.atakmap.android.importexport.FormatNotSupportedException;
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

    ImageOverlay(DatasetDescriptor layerInfo, final String uid,
            boolean bEnableTouch) {
        super(uid);
        this.layerInfo = layerInfo;

        this.setZOrder(3d + this.layerInfo.getMaxResolution(null));
        this.setClickable(bEnableTouch);
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

    /**
     * Enable or disable map touch for a image overlay
     * @param b true to enable or false to disable
     */
    public void enableMapTouch(boolean b) {
        setClickable(b);
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
    public boolean isSupported(Class<?> target) {
        return MissionPackageExportWrapper.class.equals(target);
    }

    @Override
    public Object toObjectOf(Class<?> target, ExportFilters filters)
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
