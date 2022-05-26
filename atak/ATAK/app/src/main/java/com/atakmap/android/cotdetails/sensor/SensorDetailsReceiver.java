
package com.atakmap.android.cotdetails.sensor;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;

import com.atakmap.android.cot.detail.SensorDetailHandler;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.gui.ColorPalette;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.SensorFOV;
import com.atakmap.android.widgets.SeekBarControl;
import com.atakmap.android.widgets.SeekBarControlCompat;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;

/**
 * Receiver for showing sensor details and handling actions such as toggle FOV
 */
public class SensorDetailsReceiver extends DropDownReceiver implements
        MapEventDispatcher.MapEventDispatchListener {

    public static final String TOGGLE_FOV = "com.atakmap.android.cotdetails.sensor.TOGGLE_FOV";
    public static final String FOV_COLOR = "com.atakmap.android.cotdetails.sensor.FOV_COLOR";
    public static final String FOV_DIRECTION = "com.atakmap.android.cotdetails.sensor.FOV_DIRECTION";
    public static final String FOV_SIZE = "com.atakmap.android.cotdetails.sensor.FOV_SIZE";
    public static final String SHOW_DETAILS = "com.atakmap.android.cotdetails.SENSORDETAILS";

    private final MapView _mapView;
    private final Context _context;

    private MapItem _activeSensor;

    public SensorDetailsReceiver(MapView mapView) {
        super(mapView);
        _mapView = mapView;
        _context = mapView.getContext();

        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.MAP_CLICK, this);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_REMOVED, this);
    }

    public DocumentedIntentFilter getIntentFilter() {
        DocumentedIntentFilter f = new DocumentedIntentFilter();
        f.addAction(TOGGLE_FOV, "Toggle the FOV cone on a sensor marker");
        f.addAction(FOV_COLOR, "Set the color of the FOV cone on a sensor");
        f.addAction(FOV_DIRECTION,
                "Set the direction of the FOV cone on a sensor");
        f.addAction(FOV_SIZE, "Set the size of the FOV cone on a sensor");
        f.addAction(SHOW_DETAILS, "Show the details for a sensor marker");
        return f;
    }

    @Override
    protected void disposeImpl() {
        _mapView.getMapEventDispatcher().removeMapEventListener(
                MapEvent.MAP_CLICK, this);
        _mapView.getMapEventDispatcher().removeMapEventListener(
                MapEvent.ITEM_REMOVED, this);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null)
            return;

        String uid = intent.getStringExtra("uid");
        if (FileSystemUtils.isEmpty(uid)) {
            uid = intent.getStringExtra("targetUID");
            if (FileSystemUtils.isEmpty(uid))
                return;
        }

        final MapItem mi = _mapView.getMapItem(uid);
        if (mi == null)
            return;

        MapItem fovMI = _mapView
                .getMapItem(uid + SensorDetailHandler.UID_POSTFIX);
        final SensorFOV fov = fovMI instanceof SensorFOV
                ? (SensorFOV) fovMI
                : null;

        switch (action) {
            // Toggle FOV cone visibility
            case TOGGLE_FOV: {
                boolean hide = intent.getBooleanExtra("hide",
                        !mi.hasMetaValue(SensorDetailHandler.HIDE_FOV));
                toggleFOV(mi, fov, !hide);
                mi.persist(_mapView.getMapEventDispatcher(), null, getClass());
                break;
            }

            // Change FOV cone color
            case FOV_COLOR: {
                promptFOVColor(mi, fov);
                break;
            }

            // Set FOV direction
            case FOV_DIRECTION: {
                if (mi instanceof Marker) {
                    toggleFOV(mi, fov, true);
                    SensorDetailHandler.selectFOVEndPoint((Marker) mi,
                            false, true);
                }
                break;
            }

            // Set FOV size
            case FOV_SIZE: {
                if (fov == null)
                    return;
                if (_activeSensor != null) {
                    if (_activeSensor == mi)
                        return;
                    else
                        SeekBarControl.dismiss();
                }
                toggleFOV(mi, fov, true);
                SeekBarControlCompat.show(new SeekBarControl.Subject() {
                    @Override
                    public int getValue() {
                        return (int) ((fov.getFOV() / 360f) * 100);
                    }

                    @Override
                    public void setValue(int value) {
                        int deg = (int) ((value / 100f) * 360);
                        fov.setMetrics(fov.getAzimuth(), deg, fov.getExtent(),
                                fov.isShowLabels(), fov.getRangeLines());
                        mi.setMetaInteger(SensorDetailHandler.FOV_ATTRIBUTE,
                                deg);
                    }

                    @Override
                    public void onControlDismissed() {
                        if (mi.hasMetaValue("archive"))
                            mi.persist(_mapView.getMapEventDispatcher(),
                                    null, SensorDetailsReceiver.class);
                        _activeSensor = null;
                    }
                }, 5000L);
                _activeSensor = mi;
                break;
            }

            // Show sensor marker details
            case SHOW_DETAILS: {
                if (!isClosed())
                    closeDropDown();
                LayoutInflater inflater = LayoutInflater.from(_context);
                SensorDetailsView sdv = (SensorDetailsView) inflater.inflate(
                        R.layout.sensor_details_view, _mapView, false);
                sdv.setSensorMarker(uid);
                setSelected(mi, "asset:/icons/outline.png");
                showDropDown(sdv, THREE_EIGHTHS_WIDTH, FULL_HEIGHT, FULL_WIDTH,
                        HALF_HEIGHT, false);
                break;
            }
        }
    }

    @Override
    public void onMapEvent(MapEvent event) {
        String type = event.getType();
        if (_activeSensor != null && (type.equals(MapEvent.MAP_CLICK)
                || event.getItem() == _activeSensor))
            SeekBarControl.dismiss();
    }

    private void toggleFOV(MapItem mi, SensorFOV fov, boolean show) {
        mi.toggleMetaData(SensorDetailHandler.HIDE_FOV, !show);
        if (fov != null)
            fov.setVisible(show);

        if (!show) {
            mi.toggleMetaData(SensorDetailHandler.DISPLAY_LABELS, false);
            if (fov != null)
                fov.setMetrics(fov.getAzimuth(), fov.getFOV(),
                        fov.getExtent(), false, fov.getRangeLines());
        }
    }

    private void promptFOVColor(final MapItem mi, final SensorFOV fov) {
        float red = (float) mi.getMetaDouble(SensorDetailHandler.FOV_RED, 1.0);
        float green = (float) mi.getMetaDouble(SensorDetailHandler.FOV_GREEN,
                1.0);
        float blue = (float) mi.getMetaDouble(SensorDetailHandler.FOV_BLUE,
                1.0);
        float alpha = (float) mi.getMetaDouble(SensorDetailHandler.FOV_ALPHA,
                0.3);
        int strokeColor = mi.getMetaInteger(SensorDetailHandler.STROKE_COLOR,
                Color.LTGRAY);

        int color;
        if (alpha > 0) {
            // Copied out of Color.argb(floats) which was added in API 26
            color = (255 << 24) |
                    ((int) (red * 255.0f + 0.5f) << 16) |
                    ((int) (green * 255.0f + 0.5f) << 8) |
                    (int) (blue * 255.0f + 0.5f);
        } else
            color = strokeColor;

        AlertDialog.Builder b = new AlertDialog.Builder(_context)
                .setTitle(R.string.point_dropper_text21);
        ColorPalette palette = new ColorPalette(_context, color);
        b.setView(palette);
        final AlertDialog d = b.create();
        ColorPalette.OnColorSelectedListener l = new ColorPalette.OnColorSelectedListener() {
            @Override
            public void onColorSelected(int color, String label) {
                d.dismiss();
                float red = Color.red(color) / 255f;
                float green = Color.green(color) / 255f;
                float blue = Color.blue(color) / 255f;
                if (fov != null) {
                    fov.setColor(red, green, blue);
                    fov.setStrokeColor(color);
                }
                mi.setMetaDouble(SensorDetailHandler.FOV_RED, red);
                mi.setMetaDouble(SensorDetailHandler.FOV_GREEN, green);
                mi.setMetaDouble(SensorDetailHandler.FOV_BLUE, blue);
                mi.setMetaInteger(SensorDetailHandler.STROKE_COLOR, color);
                mi.persist(_mapView.getMapEventDispatcher(), null,
                        SensorDetailsReceiver.class);
            }
        };
        palette.setOnColorSelectedListener(l);
        d.show();
    }
}
