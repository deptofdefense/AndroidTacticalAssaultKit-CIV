
package com.atakmap.android.track;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Polyline;
import com.atakmap.android.routes.elevation.model.UnitConverter;
import com.atakmap.android.track.crumb.CrumbDatabase;
import com.atakmap.android.track.maps.TrackPolyline;
import com.atakmap.android.util.AltitudeUtilities;
import com.atakmap.android.util.SpeedFormatter;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

/**
 * A convenience wrapper around a Polyline
 *
 * 
 */
public class TrackDetails
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "TrackDetails";
    private final MapView _mapView;
    private final Context _context;
    private final SharedPreferences _prefs;

    private TrackPolyline _trackPolyline;

    //TODO cache distance and times?

    //cache for computed values (based on DB crumbs, as polyline does not store this data
    private GeoPointMetaData _minAlt = null;
    private GeoPointMetaData _maxAlt = null;
    private double _maxSlope = 0;
    private double _maxSpeed = 0; //m/s
    private GeoPointMetaData _maxSpeedLocation; //geolocation for the max speed
    private double _avgSpeed = 0;
    private double _gain = 0;
    private double _loss = 0;
    private final UnitConverter.FORMAT _maxSlopeUnit = UnitConverter.FORMAT.GRADE; //DEGREE

    /**
     * Enumeration desired styles, note must match Polyline BASIC_LINE_STYLE_*
     */
    public enum Style {
        Solid(Polyline.BASIC_LINE_STYLE_SOLID),
        Dashed(Polyline.BASIC_LINE_STYLE_DASHED),
        Arrows(TrackPolyline.BASIC_LINE_STYLE_ARROWS);

        final int styleValue;

        Style(int v) {
            styleValue = v;
        }
    }

    public TrackDetails(MapView mapView, TrackPolyline track) {
        _mapView = mapView;
        _context = mapView.getContext();
        setPolyline(track);
        _prefs = PreferenceManager.getDefaultSharedPreferences(_context);

        _prefs.registerOnSharedPreferenceChangeListener(this);
        _trackPolyline.setCrumbSize(Integer.parseInt(_prefs.getString(
                "track_crumb_size", "10")));
    }

    @Override
    public void onSharedPreferenceChanged(
            SharedPreferences sharedPreferences, String key) {

        if (key == null)
            return;

        if (key.equals("track_crumb_size")) {
            _trackPolyline.setCrumbSize(Integer.parseInt(
                    _prefs.getString("track_crumb_size", "10")));
        }
    }

    /**
     * True if this is the most recent track stored locally for the UID that own's this track
     * @return true if the track is the most recent track
     */
    public boolean isCurrentTrack() {
        return _trackPolyline.getMetaBoolean(CrumbDatabase.META_TRACK_CURRENT,
                false);
    }

    /**
     * Cleans up the track.
     */
    public void dispose() {
        _prefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    public String getTitle() {
        return _trackPolyline.getMetaString("title", "Polyline");
    }

    public void setTitle(String title, Context context) {

        // update map item
        _trackPolyline.setTitle(title);

        // update underlying segment data
        int trackId = getTrackDbId();
        if (trackId >= 0) {
            Intent intent = new Intent();
            intent.setAction("com.atakmap.android.bread.UPDATE_TRACK_METADATA");
            intent.putExtra(CrumbDatabase.META_TRACK_DBID, trackId);
            intent.putExtra("name", title);
            AtakBroadcast.getInstance().sendBroadcast(intent);
        }
    }

    public boolean getVisible() {
        return _trackPolyline.getVisible()
                && _trackPolyline.getGroup() != null;
    }

    public void setVisible(boolean visible) {
        _trackPolyline.setVisible(visible);
    }

    public int getColor() {
        return _trackPolyline.getStrokeColor();
    }

    public void setColor(int color, Context context) {
        _trackPolyline.setStrokeColor(color);

        // update underlying data
        int trackId = getTrackDbId();
        if (trackId >= 0) {
            Intent intent = new Intent();
            intent.setAction("com.atakmap.android.bread.UPDATE_TRACK_METADATA");
            intent.putExtra(CrumbDatabase.META_TRACK_DBID, trackId);
            intent.putExtra("color", String.valueOf(color));
            AtakBroadcast.getInstance().sendBroadcast(intent);
        }
    }

    public static void setBasicStyle(TrackPolyline p, String lineStyle) {
        p.setMetaString("linestyle", lineStyle);
        // set basic style based on "linestyle" default to Solid if "linestyle" is not set
        p.setBasicLineStyle(TrackDetails.getBasicStyle(lineStyle));
    }

    /**
     * Get String label from the line's getBasicLineStyle()
     *
     * @param trackPolyline the track polyline
     * @return the style label for a specified TrackPolyline.
     */
    public static String getStyleLabel(TrackPolyline trackPolyline) {

        for (Style s : Style.values()) {
            if (s.styleValue == trackPolyline.getBasicLineStyle())
                return s.toString();
        }

        return Style.Solid.toString();
    }

    /**
     * Get String basic style from the line's "linestyle"
     *
     * @param trackPolyline
     * @return
     */
    public static int getBasicStyle(TrackPolyline trackPolyline) {

        return getBasicStyle(trackPolyline.getMetaString("linestyle",
                Style.Solid.toString()));
    }

    public static int getBasicStyle(String lineStyle) {

        try {
            return Style.valueOf(lineStyle.trim()).styleValue;
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Unable to get invalid style: " + lineStyle, e);
        }

        return Style.Solid.styleValue;
    }

    public int getStyle() {
        return _trackPolyline.getBasicLineStyle();
    }

    public void setStyle(String style, Context context) {
        if (style == null) {
            Log.w(TAG, "Unable to set empty style");
            return;
        }

        try {
            setStyle(Style.valueOf(style.trim()), context);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Unable to set invalid style: " + style, e);
        }
    }

    public void setStyle(TrackDetails.Style style, Context context) {
        _trackPolyline.setBasicLineStyle(style.styleValue);
        _trackPolyline
                .setMetaString("linestyle", getStyleLabel(_trackPolyline));

        // update underlying KML
        int trackId = getTrackDbId();
        if (trackId >= 0) {
            Intent intent = new Intent();
            intent.setAction("com.atakmap.android.bread.UPDATE_TRACK_METADATA");
            intent.putExtra(CrumbDatabase.META_TRACK_DBID, trackId);
            intent.putExtra("linestyle", style.toString());
            AtakBroadcast.getInstance().sendBroadcast(intent);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof TrackDetails) {
            TrackDetails c = (TrackDetails) o;
            return this.equals(c);
        } else {
            return super.equals(o);
        }
    }

    public boolean equals(TrackDetails c) {
        if (!getTitle().equals(c.getTitle()))
            return false;

        if (getTrackDbId() != c.getTrackDbId())
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        return 31 * (getTitle().hashCode() + getTrackDbId());
    }

    public String getSummary() {
        return _trackPolyline.getMetaString("linestyle", "");
    }

    public int getTrackDbId() {
        return _trackPolyline.getMetaInteger(CrumbDatabase.META_TRACK_DBID, -1);
    }

    public long getStartTime() {
        return _trackPolyline.getMetaLong("timestamp", -1);
    }

    public long getTimeElapsedLong() {
        return getTimeElapsedLong(_trackPolyline);
    }

    public static long getTimeElapsedLong(TrackPolyline p) {
        long start = p.getMetaLong("timestamp", -1);
        long end = p.getMetaLong("lastcrumbtime", -1);

        if (start < 0 || end < 0)
            return 0;

        return end - start;
    }

    public GeoPointMetaData getStartPoint() {
        return _trackPolyline.getStartPoint();
    }

    public GeoPointMetaData getEndPoint() {
        return _trackPolyline.getEndPoint();
    }

    public double getDistanceDouble() {
        return _trackPolyline.getTotalDistance();
    }

    public static String getDistanceString(double dist, int units) {
        return SpanUtilities.formatType(units, dist, Span.METER);
    }

    public String getDistanceString(int units) {
        return SpanUtilities.formatType(units, getDistanceDouble(), Span.METER);
    }

    public String getTrackUID() {
        return _trackPolyline.getUID();
    }

    public String getUserUID() {
        return ""
                + _trackPolyline.getMetaString(
                        CrumbDatabase.META_TRACK_NODE_UID, "");
    }

    public String getUserCallsign() {
        return ""
                + _trackPolyline.getMetaString(
                        CrumbDatabase.META_TRACK_NODE_TITLE, "");
    }

    @Override
    public String toString() {
        return getTitle() + ", " + getSummary();
    }

    public TrackPolyline getPolyline() {
        return _trackPolyline;
    }

    public void setPolyline(TrackPolyline polyline) {
        if (_trackPolyline != polyline) {
            if (_trackPolyline != null)
                removePolyline();
            _trackPolyline = polyline;
        }
    }

    /**
     * Show this track on the map including its start and end markers
     * The start/end markers are added in the onItemAdded callback
     * @param group Map group to add the track to
     */
    public void showPolyline(MapGroup group) {
        MapItem existing = group.deepFindUID(_trackPolyline.getUID());
        if (existing != null && existing != _trackPolyline)
            group.removeItem(existing);
        if (_trackPolyline.getGroup() == null)
            group.addItem(_trackPolyline);
        _trackPolyline.setVisible(true);
    }

    /**
     * Remove the track from the map including its start and end markers
     * The start/end markers are removed in the onItemRemoved callback
     */
    public void removePolyline() {
        if (!_trackPolyline.removeFromGroup()) {
            MapItem mi = _mapView.getRootGroup().deepFindUID(
                    _trackPolyline.getUID());
            if (mi instanceof TrackPolyline)
                mi.removeFromGroup();
        }
    }

    public int getCount() {
        if (_trackPolyline == null)
            return 0;

        return _trackPolyline.getNumPoints();
    }

    public boolean hasPoints() {
        if (_trackPolyline == null)
            return false;

        return (_trackPolyline.getNumPoints() > 0);
    }

    public void setMaxSlope(double d) {
        _maxSlope = d;
    }

    public double getMaxSlope() {
        return _maxSlope;
    }

    public String getMaxSlopeString() {
        return UnitConverter.formatToString(_maxSlope,
                UnitConverter.FORMAT.SLOPE,
                _maxSlopeUnit);
    }

    public void setGain(double d) {
        _gain = d;
    }

    /**
     * Returns the gain summation
     * @return Gain summation in feet
     */
    public double getGain() {
        return _gain;
    }

    public String getGainString() {
        final int altFmt = Integer.parseInt(_prefs.getString("alt_unit_pref",
                String.valueOf(Span.ENGLISH)));
        return SpanUtilities.formatType(altFmt, _gain, Span.FOOT);
    }

    public void setLoss(double d) {
        _loss = d;
    }

    /**
     * Returns the positive loss summation
     * @return Loss summation in feet
     */
    public double getLoss() {
        return _loss;
    }

    public String getLossString() {
        final int altFmt = Integer.parseInt(_prefs.getString("alt_unit_pref",
                String.valueOf(Span.ENGLISH)));
        return SpanUtilities.formatType(altFmt, _loss, Span.FOOT);
    }

    public void setMinAlt(GeoPointMetaData d) {
        _minAlt = d;
    }

    public GeoPointMetaData getMinAlt() {
        return _minAlt;
    }

    public String getMinAltString() {
        return AltitudeUtilities.format(_minAlt);
    }

    public void setMaxAlt(GeoPointMetaData d) {
        _maxAlt = d;
    }

    public GeoPointMetaData getMaxAlt() {
        return _maxAlt;
    }

    public String getMaxAltString() {
        return AltitudeUtilities.format(_maxAlt);
    }

    public void setMaxSpeed(double d, GeoPointMetaData point) {
        _maxSpeed = d;
        _maxSpeedLocation = point;
    }

    /**
     * Get the max speed
     * @return Max speed in meters/second
     */
    public double getMaxSpeed() {
        return _maxSpeed;
    }

    public GeoPointMetaData getMaxSpeedLocation() {
        return _maxSpeedLocation;
    }

    public String getMaxSpeedString(int units) {
        if (_maxSpeed < 0)
            return "";

        return getSpeedString(_maxSpeed, units);
    }

    private String getSpeedString(final double speed, final int units) {
        switch (units) {
            case Span.NM:
                return SpeedFormatter.getInstance().getSpeedFormatted(speed,
                        SpeedFormatter.KTS);
            case Span.METRIC:
                return SpeedFormatter.getInstance().getSpeedFormatted(speed,
                        SpeedFormatter.KMPH);
            case Span.ENGLISH:
            default:
                return SpeedFormatter.getInstance().getSpeedFormatted(speed,
                        SpeedFormatter.MPH);
        }

    }

    public void setAvgSpeed(double d) {
        _avgSpeed = d;
    }

    /**
     * Get the average speed
     * @return Average speed in meters/second
     */
    public double getAvgSpeed() {
        return _avgSpeed;
    }

    public String getAvgSpeedString(int units) {
        if (_avgSpeed < 0)
            return "";

        return getSpeedString(_avgSpeed, units);
    }
}
