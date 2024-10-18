
package com.atakmap.android.maps;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;

import com.atakmap.android.routes.Route;
import com.atakmap.android.routes.RouteMapReceiver;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.Angle;
import com.atakmap.coremap.conversions.AngleUtilities;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.NorthReference;

import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class Doghouse extends Polyline implements
        SharedPreferences.OnSharedPreferenceChangeListener {

    public interface DoghouseChangeListener {
        void onDoghouseChanged(Doghouse doghouse);

        void onDoghouseRemoved(Doghouse doghouse);
    }

    public enum DoghouseLocation {
        OUTSIDE_OF_TURN(0),
        RIGHT_OF_ROUTE(1),
        LEFT_OF_ROUTE(2);

        private final int _constant;

        DoghouseLocation(int constant) {
            _constant = constant;
        }

        public int getConstant() {
            return _constant;
        }

        @NonNull
        public static DoghouseLocation fromConstant(int c) {
            for (DoghouseLocation loc : values()) {
                if (loc._constant == c) {
                    return loc;
                }
            }

            return OUTSIDE_OF_TURN;
        }
    }

    public enum DoghouseFields {

        EMPTY("Empty"),
        BLANK("Blank"),
        TURNPOINT_ID("Turnpoint ID"),
        DTD_ID("DTD ID"),
        NEXT_CHECKPOINT("Next Checkpoint"),
        BEARING_TO_NEXT("Bearing to Next"),
        DISTANCE_TO_NEXT("Distance to Next"),
        ETE_NEXT("Time"),
        CUMULATIVE_TIME("Cumulative Time");

        private final String _repr;

        DoghouseFields(String repr) {
            _repr = repr;
        }

        @NonNull
        public static DoghouseFields fromString(@NonNull String str) {
            for (DoghouseFields value : values()) {
                if (value._repr.equals(str)) {
                    return value;
                }
            }

            return EMPTY;
        }

        @Override
        public String toString() {
            return _repr;
        }
    }

    @Override
    public void setVisible(boolean visible) {
        //if the current "new" state of the map item is now visible then we must
        //check to associated route visibility
        if (visible) {
            //make sure we have the routeUid set inside metadata
            if (getMetaString("routeUID", null) != null) {
                MapItem item = RouteMapReceiver.getInstance().getRouteGroup()
                        .findItem("uid", getMetaString("routeUID", ""));

                //make sure its a route and if the route is not visible then dont call super() for updating the map item
                //in this case the doghouse map items visibility to show, if the route is visible then we just call the super()
                //which updates the map item to true/false
                if (item instanceof Route && !item.getVisible()) {
                    return;
                }
            } else {
                Log.w("Doghouse",
                        "Warning Doghouse is missing RouteUID metadata this shouldnt happen");
            }
        }
        super.setVisible(visible);
    }

    public static final int MAX_FIELDS = 10;
    public static final int MAX_DENSITY = 200;
    public static final String META_ROUTE_UID = "routeUID";
    public static final String META_ROUTE_LEG = "routeLeg";

    public static final String EXTRA_UID = "doghouse_uid";
    private static final String BLANK = "--";
    private static final int DEFAULT_TRANSLATION = 100;

    private int _turnpointId;
    private double _distanceToNext;
    private double _bearingToNext;
    private GeoPoint _nose;
    private final DoghouseFields[] _data;
    private final SharedPreferences _prefs;
    private int _size;
    private GeoPointMetaData _source;
    private GeoPointMetaData _target;
    private final int _strokeWidth;
    private int _strokeColor;
    private final float[] _shadeColor;
    private final float[] _textColor;
    private DoghouseLocation _relativeLocation;
    private int _distanceFromLeg;
    private double _noseShift;
    private final float _maxScale;
    private final int _sizeSegment;
    private NorthReference _northReference;
    private final boolean _isTablet;

    private final ConcurrentLinkedQueue<DoghouseChangeListener> _listeners;

    Doghouse(int turnpointId,
            GeoPointMetaData source,
            GeoPointMetaData target) {
        super(MapItem.createSerialId(),
                new DefaultMetaDataHolder(),
                UUID.randomUUID().toString());
        setType("Doghouse");
        setTitle("Doghouse");
        setMetaBoolean("addToObjList", false);

        _isTablet = MapView
                .getMapView()
                .getContext()
                .getResources()
                .getBoolean(R.bool.isTablet);

        _prefs = PreferenceManager
                .getDefaultSharedPreferences(MapView.getMapView().getContext());

        _northReference = getNorthReferencePreference();
        // initialize the metadata holder to have null for all types
        for (DoghouseFields key : DoghouseFields.values()) {
            setMetaString(key.toString(), BLANK);
        }

        _source = source;
        _target = target;
        _turnpointId = turnpointId;
        _bearingToNext = source.get().bearingTo(target.get());
        _distanceToNext = source.get().distanceTo(target.get());

        // update values in metadata that we can calculate here
        updateTurnpointId();
        setBearingToNext();
        setDistanceToNext();

        // set all rows to what is indicated in preferences, EMPTY by default
        _data = new DoghouseFields[MAX_FIELDS];
        for (int i = 0; i < MAX_FIELDS; i++) {
            String prefKey = String.format(Locale.US, "dh_data_row_%d", i);
            String value = _prefs.getString(prefKey,
                    DoghouseFields.EMPTY._repr);
            DoghouseFields field = DoghouseFields.fromString(value);
            _data[i] = field;
            if (field != DoghouseFields.EMPTY) {
                _size++;
            }
        }

        _noseShift = _prefs.getFloat(DoghouseReceiver.PERCENT_ALONG_LEG, 0.5f);
        _nose = computeNosePosition(_noseShift);

        _strokeColor = _prefs.getInt(DoghouseReceiver.STROKE_COLOR,
                Color.BLACK);

        int color = _prefs.getInt(DoghouseReceiver.SHADE_COLOR, 0xBFFFFFFF);
        _shadeColor = new float[] {
                Color.alpha(color) / 255f,
                Color.red(color) / 255f,
                Color.green(color) / 255f,
                Color.blue(color) / 255f
        };

        color = _prefs.getInt(DoghouseReceiver.TEXT_COLOR, Color.RED);
        _textColor = new float[] {
                Color.alpha(color) / 255f,
                Color.red(color) / 255f,
                Color.green(color) / 255f,
                Color.blue(color) / 255f
        };

        int constant = _prefs.getInt(
                DoghouseReceiver.RELATIVE_LOCATION,
                DoghouseLocation.OUTSIDE_OF_TURN.getConstant());
        _relativeLocation = DoghouseLocation.fromConstant(constant);

        _distanceFromLeg = _prefs.getInt(DoghouseReceiver.DISTANCE_FROM_LEG,
                100);

        _strokeWidth = _prefs.getInt(DoghouseReceiver.STROKE_WIDTH, 2);

        _sizeSegment = _prefs.getInt(DoghouseReceiver.SIZE_SEGMENT, 120);

        _maxScale = _prefs.getFloat(DoghouseReceiver.MAX_SCALE_VISIBLE, 200f);

        _prefs.registerOnSharedPreferenceChangeListener(this);
        _listeners = new ConcurrentLinkedQueue<>();
        setMetaString("menu", "menus/doghouse_menu.xml");
        setClickable(false);
    }

    private NorthReference getNorthReferencePreference() {
        String northPref = _prefs.getString(
                DoghouseReceiver.NORTH_REFERENCE_KEY,
                String.valueOf(NorthReference.MAGNETIC.getValue()));
        switch (northPref) {
            case "0":
                return NorthReference.TRUE;
            case "1":
                return NorthReference.MAGNETIC;
            default:
                return NorthReference.GRID;
        }
    }

    /**
     * updates the nose of the doghouse on the route segment changes to the route
     * should recalculate this position
     */
    public void updateNose() {
        _noseShift = _prefs.getFloat(DoghouseReceiver.PERCENT_ALONG_LEG, 0.5f);
        _nose = computeNosePosition(_noseShift);
    }

    public String getData(int index) {
        if (index < 0 || index >= MAX_FIELDS) {
            String msg = "Cannot access data at position " + index
                    + " with size " + MAX_FIELDS;
            throw new IllegalArgumentException(msg);
        }
        DoghouseFields key = _data[index];
        String value = getMetaString(key.toString(), null);
        if (key != DoghouseFields.EMPTY && value == null) {
            return BLANK;
        }
        return value;
    }

    boolean addRow(DoghouseFields key) {
        if (_size == MAX_FIELDS) {
            return false;
        }

        _data[_size++] = key;
        fireOnDoghouseChanged();
        return true;
    }

    void clearRow(int index) {
        if (index >= 0 && index < _size) {
            _data[index] = DoghouseFields.BLANK;
            fireOnDoghouseChanged();
        }
    }

    void put(Doghouse.DoghouseFields key, Object value) {
        if (key != null) {
            if (value == null) {
                value = BLANK;
            }
            setMetaString(key.toString(), value.toString());
            fireOnDoghouseChanged();
        }
    }

    void removeRow(int index) {
        if (index >= 0 && index < _size) {
            if (_size - 1 - index >= 0)
                System.arraycopy(_data, index + 1, _data, index,
                        _size - 1 - index);
            _data[--_size] = DoghouseFields.EMPTY;
            fireOnDoghouseChanged();
        }
    }

    void updateRow(int index, DoghouseFields key) {
        if (index >= 0 && index < _size) {
            if (key == DoghouseFields.EMPTY) {
                clearRow(index);
            } else {
                DoghouseFields old = _data[index];
                _data[index] = key;
                if (old == DoghouseFields.EMPTY) {
                    _size++;
                }
                fireOnDoghouseChanged();
            }
        }
    }

    void setTurnpointId(int id) {
        if (_turnpointId != id) {
            _turnpointId = id;
            updateTurnpointId();
            fireOnDoghouseChanged();
        }
    }

    void setSource(@NonNull GeoPointMetaData source) {
        _source = source;
        setBearingToNext();
        setDistanceToNext();
        fireOnDoghouseChanged();
    }

    void setTarget(@NonNull GeoPointMetaData target) {
        _target = target;
        setBearingToNext();
        setDistanceToNext();
        fireOnDoghouseChanged();
    }

    void resetDefaults() {
        Arrays.fill(_data, DoghouseFields.EMPTY);
        _size = 0;
        _data[_size++] = DoghouseFields.TURNPOINT_ID;
        _data[_size++] = DoghouseFields.BEARING_TO_NEXT;
        _data[_size++] = DoghouseFields.DISTANCE_TO_NEXT;
        fireOnDoghouseChanged();
    }

    public int size() {
        return _size;
    }

    public GeoPoint getNose() {
        return _nose;
    }

    public GeoPoint getSource() {
        return _source.get();
    }

    public GeoPoint getTarget() {
        return _target.get();
    }

    public int getStrokeWidth() {
        return _strokeWidth;
    }

    public int getStrokeColor() {
        return _strokeColor;
    }

    public float[] getShadeColor() {
        return _shadeColor;
    }

    public float[] getTextColor() {
        return _textColor;
    }

    public double getBearing() {
        return _bearingToNext;
    }

    public DoghouseLocation getRelativeLocation() {
        return _relativeLocation;
    }

    void setRelativeLocation(DoghouseLocation loc) {
        _relativeLocation = loc;
        fireOnDoghouseChanged();
    }

    public float getSizeSegment() {
        return (float) _sizeSegment;
    }

    public float getMaxVisibleScale() {
        return _maxScale;
    }

    public int getFontOffset() {
        return _isTablet ? 14 : 0;
    }

    public int getTotalTranslation() {
        return DEFAULT_TRANSLATION + _distanceFromLeg;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {

        if (key == null)
            return;
        switch (key) {
            case DoghouseReceiver.STROKE_COLOR:
                _strokeColor = prefs.getInt(key, _strokeColor);
                break;
            case DoghouseReceiver.SHADE_COLOR:
                int color = prefs.getInt(key, -1);
                _shadeColor[0] = Color.alpha(color);
                _shadeColor[1] = Color.red(color);
                _shadeColor[2] = Color.green(color);
                _shadeColor[3] = Color.blue(color);
                break;
            case DoghouseReceiver.TEXT_COLOR:
                color = prefs.getInt(key, -1);
                _textColor[0] = Color.alpha(color);
                _textColor[1] = Color.red(color);
                _textColor[2] = Color.green(color);
                _textColor[3] = Color.blue(color);
                break;
            case DoghouseReceiver.PERCENT_ALONG_LEG:
                _noseShift = prefs.getFloat(key, 0.5F);
                _nose = computeNosePosition(_noseShift);
                break;
            case DoghouseReceiver.DISTANCE_FROM_LEG:
                _distanceFromLeg = prefs.getInt(key, _distanceFromLeg);
                break;
            case DoghouseReceiver.NORTH_REFERENCE_KEY:
                _northReference = getNorthReferencePreference();
                setBearingToNext();
                break;
            default:
                // swallow anything else
                return;
        }
        fireOnDoghouseChanged();
    }

    public void registerDoghouseChangeListener(
            DoghouseChangeListener listener) {
        if (!_listeners.contains(listener)) {
            _listeners.add(listener);
        }
    }

    public void unregisterDoghouseChangeListener(
            DoghouseChangeListener listener) {
        _listeners.remove(listener);
    }

    public void destroy() {
        _prefs.unregisterOnSharedPreferenceChangeListener(this);
        fireOnDoghouseRemoved();
    }

    private void fireOnDoghouseChanged() {
        for (DoghouseChangeListener listener : _listeners) {
            listener.onDoghouseChanged(this);
        }
    }

    private void fireOnDoghouseRemoved() {
        for (DoghouseChangeListener listener : _listeners) {
            listener.onDoghouseRemoved(this);
        }
    }

    private GeoPoint computeNosePosition(double shift) {
        return GeoCalculations.pointAtDistance(_source.get(), _bearingToNext,
                _distanceToNext * shift);
    }

    private void updateTurnpointId() {
        setMetaString(
                DoghouseFields.TURNPOINT_ID.toString(),
                Integer.toString(_turnpointId));
    }

    /**
     * Calculates the bearing from 1 route point to the next and sets it on the doghouse, displaying
     * it using the user's current north reference preference.
     */
    private void setBearingToNext() {
        _bearingToNext = _source.get().bearingTo(_target.get());
        double bearing;
        if (_northReference == NorthReference.MAGNETIC) {
            bearing = ATAKUtilities.convertFromTrueToMagnetic(_source.get(),
                    _bearingToNext);
        } else if (_northReference == NorthReference.TRUE) {
            bearing = _bearingToNext;
        } else {
            double gridConvergence = ATAKUtilities.computeGridConvergence(
                    _source.get(),
                    _target.get());
            bearing = AngleUtilities.wrapDeg(_bearingToNext - gridConvergence);
        }
        String bearingRepr = formatBearingString(bearing);
        setMetaString(
                DoghouseFields.BEARING_TO_NEXT.toString(), bearingRepr);

    }

    private void setDistanceToNext() {
        _distanceToNext = _source.get().distanceTo(_target.get());
        String distanceRepr = formatDistanceString(_distanceToNext);
        setMetaString(DoghouseFields.DISTANCE_TO_NEXT.toString(), distanceRepr);
    }

    private String formatBearingString(double bearing) {
        String type;
        int decimalPlaces = 0;
        switch (_northReference) {
            case MAGNETIC:
                type = "M";
                break;
            case TRUE:
                type = "T";
                break;
            default:
                type = "G";
                decimalPlaces = 3;
        }
        return AngleUtilities.format(bearing, Angle.DEGREE, decimalPlaces)
                + type;
    }

    private String formatDistanceString(double distance) {
        return SpanUtilities.formatType(
                Span.NM, // convert to these units
                distance, // raw value
                Span.METER, // the units of the raw value
                1 // decimal points in format
        );
    }
}
