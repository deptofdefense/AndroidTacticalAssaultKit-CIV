
package com.atakmap.android.cot.detail;

import android.content.Intent;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.SensorFOV;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.tools.SensorFOVTool;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.log.Log;

/**
 * Sensor FOV markers
 */
public class SensorDetailHandler extends CotDetailHandler {

    public static final String TAG = "SensorDetailHandler";

    public static final String RANGE_ATTRIBUTE = "range";
    public static final String AZIMUTH_ATTRIBUTE = "azimuth";
    public static final String FOV_ATTRIBUTE = "fov";
    public static final String MAG_REF_ATTRIBUTE = "displayMagneticReference";
    public static final String HIDE_FOV = "hideFov";

    public static final String FOV_ALPHA = "fovAlpha";
    public static final String FOV_RED = "fovRed";
    public static final String FOV_GREEN = "fovGreen";
    public static final String FOV_BLUE = "fovBlue";

    public static final int MAX_SENSOR_RANGE = 15000;

    // TODO: Why is this here, in a detail handler class?
    private static SensorFOVTool _fovTool = null;

    /**
     * Handles sensor details with the following structure:
     *
     * <sensor azimuth="90" fov="160" range="1000" />
     *
     * ...by drawing a Field of View overlay on the map.
     *
     * The cot standard for the Sensor subschema uses the following:
     * - azimuth is a decimal value [0,360), with 0 being true north
     * - fov is a decimal value [0,360), visible-edge to visible-edge (NOT
     *   center-of-field to edge)
     * - range is a decimal in meters (how far out you want the cone to extend)
     *
     */
    SensorDetailHandler() {
        super("sensor");
    }

    @Override
    public boolean isSupported(MapItem item, CotEvent event, CotDetail detail) {
        return item instanceof Marker;
    }

    @Override
    public boolean toCotDetail(MapItem item, CotEvent event, CotDetail detail) {
        if (!item.getType().equals("b-m-p-s-p-loc"))
            // Only export this detail for sensor markers
            return false;

        CotDetail sensor = new CotDetail("sensor");
        sensor.setAttribute(RANGE_ATTRIBUTE, String.valueOf(
                item.getMetaInteger(RANGE_ATTRIBUTE, 100)));
        sensor.setAttribute(AZIMUTH_ATTRIBUTE, String.valueOf(
                item.getMetaInteger(AZIMUTH_ATTRIBUTE, 270)));
        sensor.setAttribute(FOV_ATTRIBUTE,
                String.valueOf(item.getMetaInteger(FOV_ATTRIBUTE, 45)));
        sensor.setAttribute(MAG_REF_ATTRIBUTE,
                String.valueOf(item.getMetaInteger(MAG_REF_ATTRIBUTE, 0)));
        if (item.hasMetaValue(HIDE_FOV))
            sensor.setAttribute(HIDE_FOV,
                    String.valueOf(item.getMetaBoolean(HIDE_FOV, true)));
        sensor.setAttribute(FOV_ALPHA,
                String.valueOf(item.getMetaDouble(FOV_ALPHA, 0.3d)));
        sensor.setAttribute(FOV_RED,
                String.valueOf(item.getMetaDouble(FOV_RED, 1d)));
        sensor.setAttribute(FOV_GREEN,
                String.valueOf(item.getMetaDouble(FOV_GREEN, 1d)));
        sensor.setAttribute(FOV_BLUE,
                String.valueOf(item.getMetaDouble(FOV_BLUE, 1d)));
        detail.addChild(sensor);
        return true;
    }

    @Override
    public ImportResult toItemMetadata(MapItem item, CotEvent event,
            CotDetail detail) {
        Marker marker = (Marker) item;
        double azimuth = parseDouble(detail.getAttribute(AZIMUTH_ATTRIBUTE),
                Double.NaN);
        double fov = parseDouble(detail.getAttribute(FOV_ATTRIBUTE),
                Double.NaN);
        double range = parseDouble(detail.getAttribute(RANGE_ATTRIBUTE),
                Double.NaN);

        if (Double.isNaN(azimuth) || Double.isNaN(fov) || Double.isNaN(range)) {
            Log.w(TAG,
                    "At least one of the required values was missing to plot a sensor FoV");
            return ImportResult.FAILURE;
        }

        item.setMetaInteger(AZIMUTH_ATTRIBUTE, (int) Math.round(azimuth));
        item.setMetaInteger(FOV_ATTRIBUTE, (int) Math.round(fov));
        item.setMetaInteger(RANGE_ATTRIBUTE, (int) Math.round(range));

        int magRef = parseInt(detail.getAttribute(MAG_REF_ATTRIBUTE), 0);
        double red = parseDouble(detail.getAttribute(FOV_RED), 1);
        double green = parseDouble(detail.getAttribute(FOV_GREEN), 1);
        double blue = parseDouble(detail.getAttribute(FOV_BLUE), 1);
        double alpha = parseDouble(detail.getAttribute(FOV_ALPHA), 0.3);

        item.setMetaInteger(MAG_REF_ATTRIBUTE, magRef);
        item.setMetaDouble(FOV_ALPHA, alpha);
        item.setMetaDouble(FOV_RED, red);
        item.setMetaDouble(FOV_GREEN, green);
        item.setMetaDouble(FOV_BLUE, blue);

        if (detail.getAttribute(HIDE_FOV) != null)
            item.setMetaBoolean(HIDE_FOV, true);
        boolean visible = !marker.hasMetaValue(HIDE_FOV);

        float[] color = new float[] {
                (float) red, (float) green, (float) blue, (float) alpha
        };
        return addFovToMap(marker, azimuth, fov, range, color, visible)
                ? ImportResult.SUCCESS
                : ImportResult.FAILURE;
    }

    private static MapGroup _cachedMapGroup = null;

    public static MapGroup getOrAddMapGroup() {
        if (_cachedMapGroup == null) {
            Log.d(TAG, "Creating a new FoV map group");
            _cachedMapGroup = MapView.getMapView().getRootGroup()
                    .addGroup("Field Of View");
        }
        return _cachedMapGroup;
    }

    public static final String UID_POSTFIX = "-fov";

    static SensorFOV getOrAddSensorFov(final MapGroup group,
            final Marker marker) {
        String uidOfSensorFov = marker.getUID() + UID_POSTFIX;

        // Look to see if the item already has a SensorFOV on the map
        MapItem item = group.findItem("uid", uidOfSensorFov);
        if (item != null) {
            //Log.d(TAG, "Returning FoV that already was on the map");
            //TODO: check to make sure that it's actually a SensorFOV
            return (SensorFOV) item;
        }

        Log.d(TAG, "Adding a new FoV to the map");
        final SensorFOV ret = new SensorFOV(uidOfSensorFov);

        // When the original marker gets removed, also remove the SensorFOV
        marker.addOnGroupChangedListener(new MapItem.OnGroupChangedListener() {
            @Override
            public void onItemAdded(MapItem item, MapGroup markerGroup) {
            }

            @Override
            public void onItemRemoved(MapItem item, MapGroup markerGroup) {
                Log.d(TAG, "Item " + item.getUID()
                        + " was removed, removing associated SensorFOV");
                if (item == marker && group != null && ret != null)
                    group.removeItem(ret);
                else
                    Log.w(TAG,
                            "Problem removing SensorFOV associated with item "
                                    + item.getUID());
            }
        });

        //when marker visibility changed, change the SensorFOV visibility
        marker.addOnVisibleChangedListener(
                new MapItem.OnVisibleChangedListener() {
                    @Override
                    public void onVisibleChanged(MapItem item) {
                        if (ret != null) {
                            if (item.getVisible()) {
                                if (!marker.hasMetaValue(
                                        SensorDetailHandler.HIDE_FOV))
                                    ret.setVisible(true);
                            } else {
                                ret.setVisible(false);
                            }
                        }
                    }
                });

        // When the original marker gets moved, also move the SensorFOV
        marker.addOnPointChangedListener(
                new PointMapItem.OnPointChangedListener() {
                    @Override
                    public void onPointChanged(PointMapItem item) {
                        if (ret != null)
                            ret.setPoint(marker.getGeoPointMetaData());
                    }
                });

        group.addItem(ret);
        return ret;
    }

    public static boolean addFovToMap(final Marker marker, double azimuth,
            double fov,
            double range, float[] color, boolean visible) {
        //Log.d(TAG, "Plotting Sensor FoV with: azimuth=" + azimuth + ", fov="
        //        + fov + ", range=" + range + "m");
        MapGroup mapGroup = getOrAddMapGroup();
        if (mapGroup != null) {
            SensorFOV fovItem = getOrAddSensorFov(mapGroup, marker);
            fovItem.setVisible(visible);
            //TODO: register a mapitem listener to update the FoV position if the marker moves
            fovItem.setPoint(marker.getGeoPointMetaData());
            fovItem.setColor(color[0], color[1], color[2]);
            fovItem.setAlpha(color[3]);
            fovItem.setMetrics((float) azimuth, (float) fov, (float) range);
            return true;
        } else {
            Log.w(TAG, "Unexpectedly had a NULL map group for Field of View");
        }
        return false;
    }

    public static void selectFOVEndPoint(final Marker m,
            final boolean showDetails, boolean addBackListener) {
        if (_fovTool == null)
            _fovTool = new SensorFOVTool(MapView.getMapView(), null);
        Intent intent = new Intent();
        intent.setAction(ToolManagerBroadcastReceiver.BEGIN_TOOL);
        intent.putExtra("tool", SensorFOVTool.TOOL_NAME);
        intent.putExtra("uid", m.getUID());
        intent.putExtra("showDetails", showDetails);
        AtakBroadcast.getInstance().sendBroadcast(intent);
    }
}
