
package com.atakmap.android.video;

import android.graphics.Color;

import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.atakmap.android.icons.IconsMapAdapter;
import com.atakmap.android.maps.Association;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.Polyline;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.conversion.EGM96;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.CameraController;

import java.util.UUID;

/**
 * Responsible for controlling the map items on the screen based on a video with the coresponding
 * metadata.
 */
class VideoMapItemController {

    public static final String TAG = "VideoMapItemController";

    private Marker sensorMarker = null;
    private Marker frameMarker = null;
    private Association assoc = null;
    private Polyline fourCornersPolygon = null;
    private final GeoPointMetaData[] corners = new GeoPointMetaData[] {
            new GeoPointMetaData(), new GeoPointMetaData(),
            new GeoPointMetaData(), new GeoPointMetaData(),
            new GeoPointMetaData()
    };

    private boolean showSpiMarker = true;
    private boolean showSensorMarker = true;

    private final IconsMapAdapter iconAdapter;
    private final MapView mapView;

    VideoMapItemController(final MapView mapView) {
        this.mapView = mapView;
        iconAdapter = new IconsMapAdapter(mapView.getContext());
    }

    public void update(final VideoMetadata vmd) {
        updateSensorPos(vmd);
        updateFramePos(vmd);

    }

    /**
     * Allow for the ability to hide the sensor marker.   This overcomes issues with latency
     * where the spi is being transmitted via cursor on target.
     */
    public void showSensorMarker(final boolean showing) {
        showSensorMarker = showing;
    }

    public void showSpiMarker(final boolean showing) {
        showSpiMarker = showing;
    }

    /**
     * Updates the Sensor Position on the Map 
     */
    private void updateSensorPos(final VideoMetadata vmd) {
        // should we show klv and has the mapview been registered?
        if (mapView != null) {

            final ConnectionEntry ce = vmd.connectionEntry;
            if (ce == null) {
                if (sensorMarker != null)
                    sensorMarker.setVisible(false);
                return;
            }

            GeoPointMetaData gp = GeoPointMetaData.wrap(
                    new GeoPoint(vmd.sensorLatitude,
                            vmd.sensorLongitude,
                            EGM96.getHAE(vmd.sensorLatitude,
                                    vmd.sensorLongitude, vmd.sensorAltitude)),
                    GeoPointMetaData.GPS, GeoPointMetaData.GPS);

            if (sensorMarker == null && showSensorMarker) {
                sensorMarker = new Marker(gp, UUID.randomUUID().toString());

                setupMarker(sensorMarker, "a-f-A",
                        ce.getAlias());
                mapView.getRootGroup().addItem(sensorMarker);

                if (frameMarker != null) {
                    createAssociation(sensorMarker, frameMarker);
                }
            }

            if (sensorMarker != null && vmd.platformTail != null
                    && !sensorMarker.getTitle().startsWith(vmd.platformTail)) {
                setTitle(sensorMarker, vmd.platformTail);
            }

            if (sensorMarker != null)
                sensorMarker.setPoint(gp);
        }
    }

    public void zoomTo(MapView mapView, VideoMetadata vmd) {
        if (frameMarker != null && sensorMarker != null
                && frameMarker.getVisible()) {
            ATAKUtilities
                    .scaleToFit(mapView, frameMarker, sensorMarker);
        } else {

            if (!Double.isNaN(vmd.frameLatitude)
                    && !Double.isNaN(vmd.frameLongitude)) {
                CameraController.Programmatic.panTo(
                        mapView.getRenderer3(),
                        new GeoPoint(vmd.frameLatitude, vmd.frameLongitude),
                        true);
            }
        }
    }

    /**
     * Updates the Frame Center Position on the Map
     * Updates.
     */
    private void updateFramePos(final VideoMetadata vmd) {

        if (vmd.connectionEntry == null) {
            setFrameMarkerVisible(false);
            return;
        }

        // Augment the current visibility test so that if the lat / lon are zero or
        // the latitude is either -90 or 90 (poles).
        if (Double.compare(vmd.frameLatitude, -90d) == 0 ||
                Double.compare(vmd.frameLatitude, 90d) == 0 ||
                (Double.compare(vmd.frameLatitude, 0d) == 0 &&
                        Double.compare(vmd.frameLongitude, 0d) == 0)) {
            setFrameMarkerVisible(false);
            return;
        } else {
            setFrameMarkerVisible(true);
        }

        // should we show klv and has the mapview been registered?
        if (mapView != null) {

            if (frameMarker == null && showSpiMarker) {
                frameMarker = new Marker(vmd.frameDTED,
                        UUID.randomUUID().toString());
                setupMarker(frameMarker, "b-m-p-s-p-i",
                        vmd.connectionEntry.getAlias());

                frameMarker.setMetaLong("est.time",
                        new CoordinatedTime().getMilliseconds());

                if (vmd.platformTail == null)
                    setTitle(frameMarker,
                            vmd.connectionEntry.getAlias() + "-SPI");

                // put this in the SPIs group if possible
                final MapGroup mg = mapView.getRootGroup().findMapGroup("SPIs");
                if (mg != null) {
                    mg.addItem(frameMarker);
                } else {
                    Log.d(TAG, "cannot find the SPIs group");
                    mapView.getRootGroup().addItem(frameMarker);
                }

                if (sensorMarker != null) {
                    createAssociation(sensorMarker, frameMarker);
                }
            }
            if (frameMarker != null) {

                GeoPoint pointBefore = frameMarker.getPoint();

                if (pointBefore != null) {
                    final long lastEstimatedTime = frameMarker
                            .getMetaLong("est.time", -1);
                    if (lastEstimatedTime >= 0) {
                        final GeoPoint gp = vmd.frameDTED.get();
                        final long currTime = new CoordinatedTime()
                                .getMilliseconds();
                        final double d = pointBefore.distanceTo(gp);
                        final double s = d
                                / ((currTime - lastEstimatedTime) / 1000d);
                        final double b = pointBefore.bearingTo(gp);
                        frameMarker.setMetaLong("est.time", currTime);
                        frameMarker.setMetaDouble("est.speed", s);
                        frameMarker.setMetaDouble("est.course", b);
                        frameMarker.setMetaDouble("est.dist", d);
                    }
                }

                frameMarker.setPoint(vmd.frameDTED);

            }

            if (frameMarker != null && vmd.platformTail != null
                    && !frameMarker.getTitle().startsWith(vmd.platformTail)) {
                setTitle(frameMarker, vmd.platformTail + "-SPI");
            }

            if (fourCornersPolygon == null) {
                fourCornersPolygon = new Polyline(UUID.randomUUID().toString());
                fourCornersPolygon.setEditable(false);
                fourCornersPolygon.setMovable(false);
                fourCornersPolygon.setClickable(false);
                fourCornersPolygon.setStyle(
                        fourCornersPolygon.getStyle() |
                                Polyline.STYLE_CLOSED_MASK
                                | Polyline.STYLE_FILLED_MASK);
                fourCornersPolygon.setStrokeWeight(4d);
                fourCornersPolygon.setStrokeColor(Color.WHITE);
                fourCornersPolygon.setFillColor(Color.argb(45, 255, 255, 255));
                mapView.getRootGroup().addItem(fourCornersPolygon);
            }

            if (!(Double.isNaN(vmd.corner1lat) || Double.isNaN(vmd.corner1lon)
                    ||
                    Double.isNaN(vmd.corner2lat) || Double.isNaN(vmd.corner2lon)
                    ||
                    Double.isNaN(vmd.corner3lat) || Double.isNaN(vmd.corner3lon)
                    ||
                    Double.isNaN(vmd.corner4lat)
                    || Double.isNaN(vmd.corner4lon))) {

                // Only draw the polygon if all the corners are available
                corners[0].set(new GeoPoint(vmd.corner1lat, vmd.corner1lon));
                corners[1].set(new GeoPoint(vmd.corner2lat, vmd.corner2lon));
                corners[2].set(new GeoPoint(vmd.corner3lat, vmd.corner3lon));
                corners[3].set(new GeoPoint(vmd.corner4lat, vmd.corner4lon));
                corners[4].set(new GeoPoint(vmd.corner1lat, vmd.corner1lon));
                fourCornersPolygon.setPoints(corners);
            }

        }

    }

    /**
     * Only to be called when the frame marker is 0,0.
     */
    private void setFrameMarkerVisible(final boolean b) {
        if (assoc != null)
            assoc.setVisible(b);

        if (frameMarker != null)
            frameMarker.setVisible(b);

        // disable for now until the code is refined to allow for the user to toggle this in 3 states
        // saved into the preferences (so the choice is remembered) 
        //    preferenceKey should be displayFmvOutline 
        //    preferenceValues should be  noDisplay, displayOutline, <future>displayAsLayer
        // currently I have 4 corner videos where the corners are all jacked up.

        if (fourCornersPolygon != null)
            fourCornersPolygon.setVisible(false);
    }

    synchronized private void createAssociation(final Marker sensor,
            final Marker frame) {
        if (assoc == null) {
            assoc = new Association(UUID.randomUUID().toString());
            assoc.setColor(Color.WHITE);
            assoc.setStyle(Association.STYLE_SOLID);
            assoc.setLink(Association.LINK_LINE);
            assoc.setStrokeWeight(3d);
            assoc.setZOrder(3);
            assoc.setFirstItem(sensor);
            assoc.setSecondItem(frame);
            mapView.getRootGroup().addItem(assoc);
        }
        frame.setMetaString("parent_type", "a-f");
        frame.setMetaString("parent_uid", sensor.getUID());

    }

    /**
     * Keep the title and the callsign equal.
     */
    static private void setTitle(final Marker m, final String name) {
        m.setTitle(name);
        m.setMetaString("callsign", name);
    }

    /**
     * Given a created blankMarker, configure the marker to be of type markerType. with attributes
     * consistent with the other full motion video markers.
     */
    private void setupMarker(Marker blankMarker, String markerType,
            String alias) {
        blankMarker.setClickable(true);

        blankMarker.setMetaString("stayopen", "true");
        setTitle(blankMarker, alias);

        blankMarker.setMetaBoolean("editable", false);
        blankMarker.setMovable(false);
        blankMarker.setMetaBoolean("removable", false);
        blankMarker.setType(markerType);
        blankMarker.setMetaString("how", "m-g");

        iconAdapter.adaptMarkerIcon(blankMarker);
        blankMarker.setVisible(true);

    }

    public synchronized void dispose() {
        // remove marker from map
        if (sensorMarker != null) {
            mapView.getRootGroup().removeItem(sensorMarker);
            sensorMarker.setMetaBoolean("removable", true);
            sensorMarker = null;
        }

        if (frameMarker != null) {
            MapGroup mg = frameMarker.getGroup();
            if (mg != null) {
                Log.d(TAG, "removing the frame marker from the screen");
                mg.removeItem(frameMarker);
            }
            frameMarker.setMetaBoolean("removable", true);
            frameMarker = null;
        }

        if (fourCornersPolygon != null) {
            mapView.getRootGroup().removeItem(fourCornersPolygon);
            fourCornersPolygon.setMetaBoolean("removable", true); // what does this do?
            fourCornersPolygon = null;
        }

        if (assoc != null) {
            mapView.getRootGroup().removeItem(assoc);
            assoc = null;
        }
    }

}
