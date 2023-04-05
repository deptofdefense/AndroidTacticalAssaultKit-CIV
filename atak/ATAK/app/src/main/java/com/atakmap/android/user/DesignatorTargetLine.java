
package com.atakmap.android.user;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.preference.PreferenceManager;

import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.maps.Wedge;
import com.atakmap.android.toolbars.RangeAndBearingMapItem;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.Angle;
import com.atakmap.coremap.conversions.AngleUtilities;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.NorthReference;
import com.atakmap.android.toolbars.DynamicRangeAndBearingEndpoint;
import com.atakmap.android.ipc.AtakBroadcast;
import android.content.Intent;

import java.util.UUID;

public class DesignatorTargetLine implements
        PointMapItem.OnPointChangedListener,
        SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String TAG = "DesignatorTargetLine";

    private final String _rabUUID = UUID.randomUUID().toString();

    private final DynamicRangeAndBearingEndpoint drabe;

    private final Wedge[] wedges = new Wedge[] {
            new Wedge(UUID.randomUUID().toString()),
            new Wedge(UUID.randomUUID().toString()),
            new Wedge(UUID.randomUUID().toString()),
            new Wedge(UUID.randomUUID().toString()),
            new Wedge(UUID.randomUUID().toString()),
            new Wedge(UUID.randomUUID().toString())
    };

    private final MapGroup _mapGroup;
    private final MapView _mapView;
    private final SharedPreferences _preferences;
    private static boolean _showDegrees = true;

    private PointMapItem target;
    private PointMapItem designator;

    boolean targetLine = false;
    boolean safetyZone = false;

    // Clear the current point
    private GeoPoint prevTargetPt = null;
    private GeoPoint prevDesignatorPt = null;

    private final static int transparency = 50;
    private final static int DEFAULT_DISTANCE = 5;

    private double distance;

    public DesignatorTargetLine(MapView mapView, MapGroup mapGroup) {
        _mapGroup = mapGroup;
        _mapView = mapView;
        _preferences = PreferenceManager.getDefaultSharedPreferences(_mapView
                .getContext());
        _preferences.registerOnSharedPreferenceChangeListener(this);
        _showDegrees = _preferences.getBoolean("laserBasketDegrees", true);

        distance = getInteger(_preferences,
                "laserBasketDistance", DEFAULT_DISTANCE) * 1852d;
        drabe = new DynamicRangeAndBearingEndpoint(_mapView,
                GeoPointMetaData.wrap(GeoPoint.ZERO_POINT),
                _rabUUID + "pt1");
        drabe.setPostDragAction(new Runnable() {
            @Override
            public void run() {
                PointMapItem pmi = target;
                if (pmi != null) {
                    Intent localDetails = new Intent();
                    localDetails.setAction(
                            "com.atakmap.android.action.SHOW_POINT_DETAILS");
                    localDetails.putExtra("uid", pmi.getUID());
                    AtakBroadcast.getInstance().sendBroadcast(localDetails);

                }
            }
        });
    }

    private int getInteger(SharedPreferences _preferences,
            String key, int defaultVal) {
        try {
            return Integer.parseInt(_preferences
                    .getString(key, "" + defaultVal));
        } catch (Exception e) {
            return defaultVal;
        }
    }

    @Override
    public void onPointChanged(final PointMapItem item) {
        if (item != target && item != designator) {
            // should only be receiving updates for the currently active friendly
            item.removeOnPointChangedListener(this);
        }

        if (prevDesignatorPt == null || prevTargetPt == null ||
                Math.abs(prevTargetPt.distanceTo(target.getPoint())) > 0.1 ||
                Math.abs(prevDesignatorPt.distanceTo(
                        designator.getPoint())) > 0.1) {

            draw();
            // Update the current points
            prevDesignatorPt = designator.getPoint();
            prevTargetPt = target.getPoint();
        }

    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key) {

        if (key == null)
            return;

        if (key.equals("laserBasketDegrees")) {
            _showDegrees = sharedPreferences.getBoolean(key, true);
            draw();
        } else if (key.equals("laserBasketDistance")) {
            distance = getInteger(sharedPreferences, key, DEFAULT_DISTANCE)
                    * 1852d;
            draw();
        }
    }

    /**
     * Creates a single wedge that will be composed into a larger safety zone.
     */
    private static Wedge make(Wedge w, final GeoPoint target,
            final GeoPoint source,
            final double multiplier, final double offsetAngle,
            final double angle, final int alphaChannelTransparency,
            final int red, final int green, final int blue,
            final String leftLabel,
            final String rightLabel,
            final double zorder) {

        Wedge ret = w
                .setDistanceMultiplier(multiplier)
                .setOffsetAngle(offsetAngle)
                .setSourcePoint(target)
                .setEndPoint(source);
        if (_showDegrees) {
            ret.setAngle(angle, leftLabel, "", rightLabel);
        } else {
            ret.setAngle(angle);
        }
        ret.setStyle(ret.getStyle() | Shape.STYLE_FILLED_MASK);
        ret.setFillColor(
                Color.argb(alphaChannelTransparency, red, green, blue));
        ret.setStrokeWeight(1d);
        ret.setZOrder(zorder); // Draw it far below everything else (especially
                               // the R+B line)
        return ret;
    }

    synchronized public void set(final PointMapItem t) {
        if (t == null)
            return;
        GeoPoint tPoint = t.getPoint();

        GeoPoint dPoint = GeoCalculations.pointAtDistance(tPoint, -180,
                distance);
        drabe.setPoint(dPoint);
        _mapGroup.addItem(drabe);
        set(t, drabe);
    }

    synchronized public void set(final PointMapItem t,
            final PointMapItem d) {

        //If we get passed in null return
        if (t == null || d == null)
            return;
        //If this is the first time and we don't have a stored designator or target
        if ((designator == null) || (target == null)) {
            target = t;
            designator = d;
            target.addOnPointChangedListener(this);
            designator.addOnPointChangedListener(this);
            //Else see if either of the points change, only redraw if they did
        } else if (!d.getUID().equals(designator.getUID())
                || !t.getUID().equals(target.getUID())) {
            reset();
            target = t;
            designator = d;
            target.addOnPointChangedListener(this);
            designator.addOnPointChangedListener(this);
        }
    }

    synchronized public void reset() {
        if (target != null) {
            target.removeOnPointChangedListener(this);
            target = null;
        }
        if (designator != null) {
            designator.removeOnPointChangedListener(this);
            designator = null;
        }
        clearSafetyZone();
        clearTargetLine();
        safetyZone = false;
        targetLine = false;

        prevDesignatorPt = null;
        prevTargetPt = null;
        prevDPoint = null;
    }

    public void showSafetyZone(final boolean b) {
        //Only go through changing the visibility if the visibility actually changed.
        if (safetyZone != b) {
            safetyZone = b;
            if (!b) {
                clearSafetyZone();
                prevBearing = Integer.MIN_VALUE;
            } else {
                drawSafetyZone();
            }
        }
    }

    public void showTargetLine(final boolean b) {
        if (targetLine != b) {
            targetLine = b;

            MapItem rb = _mapGroup.deepFindItem("uid", _rabUUID);
            if (rb != null) {
                rb.setVisible(b);
                drabe.setVisible(b);
            } else {
                if (b)
                    drawTargetLine();
            }
        }
    }

    public void showAll(boolean b) {
        showSafetyZone(b);
        showTargetLine(b);
    }

    private String format(final double mag) {
        return AngleUtilities.format(mag) + "M";
    }

    private int prevBearing = Integer.MIN_VALUE;
    private GeoPoint prevDPoint = null;

    private void drawSafetyZone() {
        if (target == null || designator == null || _mapGroup == null)
            return;

        final GeoPoint tPoint = target.getPoint();

        // compute the angle to the designator
        double rawBearing = GeoCalculations.bearingTo(designator.getPoint(),
                tPoint);

        // use the bearing of the given designator
        int bearing = (int) Math.round(ATAKUtilities
                .convertFromTrueToMagnetic(designator.getPoint(), rawBearing));

        // compute the point at the given distance
        final GeoPoint dPoint;
        if (distance > 0) {
            dPoint = GeoCalculations.pointAtDistance(tPoint, rawBearing - 180,
                    distance);
        } else {
            dPoint = designator.getPoint();
        }

        if (prevBearing == bearing && prevDPoint != null
                && prevDPoint.distanceTo(dPoint) < 0.1)
            return;

        prevBearing = bearing;
        prevDPoint = dPoint;

        // First, remove any existing wedges...

        // Now, add new ones...

        // populate in reverse so that we get proper z-ordering for the leg labels.
        wedges[0] = make(wedges[0], tPoint, dPoint, 1.0, 0.0, 10.0,
                transparency, 255,
                0, 0, "", format(bearing - 10), 100d);

        if (wedges[0].getGroup() == null)
            _mapGroup.addItem(wedges[0]);

        wedges[1] = make(wedges[1], tPoint, dPoint, 1.0, 350.0, 10.0,
                transparency,
                255, 0, 0, format(bearing + 10), "", 100d);
        if (wedges[1].getGroup() == null)
            _mapGroup.addItem(wedges[1]);

        wedges[2] = make(wedges[2], tPoint, dPoint, 1.0, 10.0, 35.0,
                transparency, 0,
                255, 0, "", format(bearing - 45), 101d);
        if (wedges[2].getGroup() == null)
            _mapGroup.addItem(wedges[2]);

        wedges[3] = make(wedges[3], tPoint, dPoint, 1.0, 315.0, 35.0,
                transparency, 0,
                255, 0, format(bearing + 45), "", 101d);
        if (wedges[3].getGroup() == null)
            _mapGroup.addItem(wedges[3]);

        wedges[4] = make(wedges[4], tPoint, dPoint, 1.0, 45.0, 15.0,
                transparency, 0,
                0, 255, "", format(bearing - 60), 102d);
        if (wedges[4].getGroup() == null)
            _mapGroup.addItem(wedges[4]);

        wedges[5] = make(wedges[5], tPoint, dPoint, 1.0, 300.0, 15.0,
                transparency, 0,
                0, 255, format(bearing + 60), "", 102d);
        if (wedges[5].getGroup() == null)
            _mapGroup.addItem(wedges[5]);
    }

    private void drawTargetLine() {
        if (target == null || designator == null || _mapGroup == null)
            return;

        if (RangeAndBearingMapItem.getRABLine(_rabUUID) == null) {
            designator.setMetaString("rabUUID", _rabUUID);
            target.setMetaString("rabUUID", _rabUUID);
            RangeAndBearingMapItem rb = RangeAndBearingMapItem
                    .createOrUpdateRABLine(_rabUUID, designator, target,
                            false);
            rb.setTitle(_mapView.getContext().getString(
                    R.string.designator_target_line));
            rb.setType("rb");
            rb.setMetaBoolean("removable", false);
            rb.setBearingUnits(Angle.DEGREE);
            rb.setNorthReference(NorthReference.MAGNETIC);
            rb.setMetaBoolean("disable_polar", true);
            rb.allowSlantRangeToggle(false);
            _mapGroup.addItem(rb);
        }
    }

    private void draw() {

        if (target == null || designator == null || _mapGroup == null)
            return;

        //Should only be drawing if safetyZone = true
        if (safetyZone) {
            drawSafetyZone();
        }

        if (targetLine)
            drawTargetLine();
    }

    public void clearSafetyZone() {
        if (_mapGroup != null && wedges[0] != null) {
            for (Wedge wedge : wedges) {
                _mapGroup.removeItem(wedge);
            }
        }
    }

    public void clearTargetLine() {
        RangeAndBearingMapItem rb = RangeAndBearingMapItem.getRABLine(_rabUUID);
        if (rb != null) {
            rb.removeFromGroup();
            rb.dispose();
        }
        if (drabe != null)
            drabe.removeFromGroup();
    }

    //Called when the dropdown is closed, clears visibility of everything.
    public void end() {
        clearSafetyZone();
        clearTargetLine();
        safetyZone = false;
        targetLine = false;

        prevDesignatorPt = null;
        prevTargetPt = null;
        prevBearing = Integer.MIN_VALUE;
    }
}
