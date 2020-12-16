
package com.atakmap.android.bloodhound.link;

import android.graphics.Color;

import com.atakmap.android.bloodhound.BloodHoundPreferences;
import com.atakmap.android.bloodhound.SimpleSpeedBearingComputer;
import com.atakmap.android.bloodhound.ui.BloodHoundHUD;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.routes.RouteNavigator;
import com.atakmap.android.toolbars.RangeAndBearingMapItem;
import com.atakmap.coremap.conversions.Angle;

/**
 * Represents a link between 2 markers using a R&B line
 * This class is used specifically for the management of bloodhound lines
 * created with the radial menu option on a R&B line, not the link
 * managed by the BloodHound tool itself.
 */
public class BloodHoundLink implements PointMapItem.OnPointChangedListener,
        MapItem.OnGroupChangedListener {

    private final MapView _mapView;
    private final BloodHoundLinkManager _manager;
    private final RangeAndBearingMapItem _line;
    private final PointMapItem _p1, _p2;
    private final double _oldZOrder;
    private final SimpleSpeedBearingComputer _calc;
    private final BloodHoundPreferences _prefs;
    private boolean _disposed;

    public BloodHoundLink(MapView mapView, BloodHoundLinkManager manager,
            RangeAndBearingMapItem line) {
        _mapView = mapView;
        _manager = manager;
        _prefs = new BloodHoundPreferences(mapView);
        _line = line;
        _oldZOrder = line.getZOrder();
        _line.setMetaBoolean("override_label", true);
        _line.setMetaBoolean("hounding", true);
        _p1 = _line.getPoint1Item();
        _p2 = _line.getPoint2Item();
        _calc = new SimpleSpeedBearingComputer(30);
        if (_p1 != null) {
            _p1.addOnPointChangedListener(this);
            _p1.addOnGroupChangedListener(this);
            _calc.add(_p1.getPoint());
        }
        if (_p2 != null) {
            _p2.addOnPointChangedListener(this);
            _p2.addOnGroupChangedListener(this);
        }
        update();
    }

    public void dispose() {
        if (!_disposed) {
            _line.removeMetaData("override_label");
            _line.removeMetaData("hounding");
            _line.setText(_line.getMetaString("label", ""));
            _line.setTextColor(Color.WHITE);
            _line.setZOrder(_oldZOrder);
            if (_p1 != null) {
                _p1.removeOnPointChangedListener(this);
                _p1.removeOnGroupChangedListener(this);
            }
            if (_p2 != null) {
                _p2.removeOnPointChangedListener(this);
                _p2.removeOnGroupChangedListener(this);
            }
        }
        _disposed = true;
    }

    /**
     * Get the R&B line this link is monitoring
     * @return R&B line
     */
    public RangeAndBearingMapItem getLine() {
        return _line;
    }

    /**
     * Re-create the link using the new points on the R&B line
     * Note: This will dispose the current link instance
     */
    public void recreate() {
        dispose();
        _manager.removeLink(_line.getUID());
        _manager.addLink(_line);
    }

    public void update() {
        if (_disposed || _p1 == null || _p2 == null)
            return;

        // Points changed
        if (_p1 != _line.getPoint1Item() || _p2 != _line.getPoint2Item()) {
            recreate();
            return;
        }

        final double range = _p1.getPoint().distanceTo(_p2.getPoint());
        double bearing = _p1.getPoint().bearingTo(_p2.getPoint());

        // Determine relative bearing
        double relativeBearing = Double.NaN;
        if (_p1 instanceof Marker)
            relativeBearing = ((Marker) _p1).getTrackHeading();

        if (Double.isNaN(relativeBearing))
            relativeBearing = _calc.getBearing();

        if (!Double.isNaN(relativeBearing))
            relativeBearing = (bearing - relativeBearing) % 360;

        // Determine ETA
        double eta = Double.NaN;
        try {
            double avgSpeed = _p1.getMetaDouble("avgSpeed30", Double.NaN);
            if (Double.isNaN(avgSpeed) && _p1 instanceof Marker)
                avgSpeed = ((Marker) _p1).getTrackSpeed();
            if (Double.isNaN(avgSpeed))
                avgSpeed = _calc.getAverageSpeed();
            if (!Double.isNaN(avgSpeed) && Double.compare(avgSpeed, 0.0) != 0)
                eta = range / avgSpeed;
        } catch (Exception ignore) {
        }

        StringBuilder sb = new StringBuilder(_line.getMetaString("label", ""));
        sb.append("\n");

        if (!Double.isNaN(relativeBearing)) {
            int rb = (int) Math.round(relativeBearing);
            if (rb > 180)
                rb = -(360 - rb);
            sb.append(rb).append(Angle.DEGREE_SYMBOL).append("R   ");
        }

        sb.append("ETA: ");
        if (!Double.isNaN(eta)) {
            _p1.setMetaDouble("bloodhoundEta", eta);
            sb.append(BloodHoundHUD.formatTime(eta));
        } else
            sb.append("---");

        _line.setText(sb.toString());

        // Colors
        boolean flash = _prefs.get("rab_bloodhound_flash_colors", true)
                && eta <= _prefs.getFlashETA();
        int color, flColor = _prefs.getFlashColor();
        if (Double.isNaN(eta) || eta > _prefs.getOuterETA())
            color = flash && _manager.getOuterTick() == 0
                    ? flColor
                    : _prefs.getOuterColor();
        else if (eta > _prefs.getInnerETA())
            color = flash && _manager.getMiddleTick() == 0
                    ? flColor
                    : _prefs.getMiddleColor();
        else
            color = flash && _manager.getInnerTick() == 0
                    ? flColor
                    : _prefs.getInnerColor();

        _line.setTextColor(color);
        _line.setZOrder(-1000d);
    }

    @Override
    public void onPointChanged(PointMapItem item) {
        if (_p1 == null || _p2 == null)
            return;

        // Check if we should jump to the next waypoint in a route
        PointMapItem next = RouteNavigator.getNextWaypoint(_p2,
                _p1.getPoint().distanceTo(_p2.getPoint()), _mapView,
                _prefs.getSharedPrefs());
        if (next != null) {
            _line.setPoint2(next);
            recreate();
            return;
        }

        if (item == _p1 && Double.isNaN(item.getMetaDouble("avgSpeed30",
                Double.NaN)))
            _calc.add(item.getPoint());
        update();
    }

    @Override
    public void onItemAdded(MapItem item, MapGroup group) {
    }

    @Override
    public void onItemRemoved(MapItem item, MapGroup group) {
        dispose();
    }
}
