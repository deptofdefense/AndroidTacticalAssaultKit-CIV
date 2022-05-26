
package com.atakmap.android.drawing.mapItems;

import android.graphics.Color;

import com.atakmap.android.importexport.KmlMapItemImportFactory;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.coremap.log.Log;

import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.spatial.kml.KMLUtil;
import com.ekito.simpleKML.model.Placemark;
import com.ekito.simpleKML.model.Point;
import com.ekito.simpleKML.model.Style;

public class GenericPoint {

    private GenericPoint() {
    }

    /**
     * Create a generic point u-d-p
     * @param title the name of the generic point
     * @param uid the uid to use when creating the generic point
     * @param point the latitude, longitude, and altitude of the generic point
     * @return the marker constructed not added to a map group.
     */
    static Marker createGenericPoint(final String title,
            final String uid, final GeoPointMetaData point) {
        Marker m = new Marker(point, uid);
        m.setTitle(title);
        m.setMetaString("callsign", title);
        m.setMetaString("entry", "user");
        m.setType("u-d-p");
        m.setMovable(true);
        m.setMetaInteger("color", Color.WHITE);
        m.setMetaString("how", "h-g-i-g-o");
        return m;

    }

    public static class KmlGenericPointImportFactory extends
            KmlMapItemImportFactory {

        private static final String TAG = "GenericPointImportFactory";

        private final MapView mapView;

        public KmlGenericPointImportFactory(MapView mapView) {
            this.mapView = mapView;
        }

        @Override
        public MapItem instanceFromKml(Placemark placemark, MapGroup mapGroup) {

            Point point = KMLUtil.getFirstGeometry(placemark, Point.class);
            if (point == null) {
                Log.e(TAG, "Placemark does not have a Point");
                return null;
            }

            String uid = point.getId();
            if (point.getCoordinates() == null) {
                Log.e(TAG, "Placemark does not have a Point");
                return null;
            }

            GeoPointMetaData pp = KMLUtil.convertPoint(point.getCoordinates());

            String title = placemark.getName();

            int color = -1;
            Style style = KMLUtil.getFirstStyle(placemark, Style.class);
            if (style != null && style.getIconStyle() != null)
                color = KMLUtil.parseKMLColor(style.getIconStyle().getColor());

            Marker gp = createGenericPoint(title, uid, pp);
            gp.setMetaInteger("color", color);
            gp.refresh(this.mapView.getMapEventDispatcher(), null,
                    this.getClass());

            return gp;
        }

        @Override
        public String getFactoryName() {
            return FACTORY_NAME;
        }

        static final String FACTORY_NAME = "u-d-p";
    }

}
