
package com.atakmap.android.widgets;

import android.content.SharedPreferences;
import android.graphics.RectF;
import android.preference.PreferenceManager;
import android.view.MotionEvent;

import java.util.concurrent.ConcurrentLinkedQueue;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.PointMapItem.OnPointChangedListener;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.maps.graphics.widgets.GLWidgetsMapComponent;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;

import java.util.UUID;

/**
 * A MapWidget class that will display an arrow representing the Final Attack Heading towards a
 * targeted IP and with an offset from some designator point. The widget will also display a cone
 * that represents the offset width from the heading on each side. The final attack heading text
 * value will be displayed above the heading arrow, as well as the text heading values of the cone's
 * sides.<br>
 * <br>
 * 
 * 
 */
public class FahArrowWidget extends ShapeWidget implements
        SharedPreferences.OnSharedPreferenceChangeListener {

    public final static String TAG = "FahArrowWidget";
    private final ConcurrentLinkedQueue<OnFahAngleChangedListener> _onFahAngleChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnFahWidthChangedListener> _onFahWidthChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnFahLegChangedListener> _onFahLegChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnTouchableChangedListener> _onTouchableChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnTargetPointChangedListener> _onTargetPointChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnDesignatorPointChangedListener> _onDesignatorPointChanged = new ConcurrentLinkedQueue<>();
    private double _fahAngle;
    private double _fahOffset;
    private double _fahWidth;
    private boolean _touchable = false;
    private PointMapItem _target;
    private PointMapItem _designator;
    private GeoPoint _lastDesignatorLoc;
    private boolean _fakeDesignator;
    private final RectF _hitBox = new RectF(-64f, -64f, 64f, 64f);
    private final MapView _mapView;
    private boolean reverse = true;

    private double distance;

    private static final int MAX_DISTANCE = 1841 * 100;
    private final static int DEFAULT_DISTANCE = 5;

    /**
     * A listener for the FahArrowWidget changing the angle of the final attack heading.
     */
    public interface OnFahAngleChangedListener {
        void onFahAngleChanged(FahArrowWidget arrow);
    }

    /**
     * A listener for the FahArrowWidget changing it's touchable state.
     */
    public interface OnTouchableChangedListener {
        void onTouchableChanged(FahArrowWidget arrow);
    }

    /**
     * A listener for the FahArrowWidget changing the targeted location.
     */
    public interface OnTargetPointChangedListener {
        void onTargetChanged(FahArrowWidget arrow);
    }

    /**
     * A listener for the FahArrowWidget changing the designator's point.
     */
    public interface OnDesignatorPointChangedListener {
        void onDesignatorChanged(FahArrowWidget arrow);
    }

    /**
     * A listener for the FahArrowWidget changing the width angle of the cone.
     */
    public interface OnFahWidthChangedListener {
        void onFahWidthChanged(FahArrowWidget arrow);
    }

    public interface OnFahLegChangedListener {
        void onFahLegChanged(FahArrowWidget arrow);
    }

    public void addOnTargetPointChangedListener(
            OnTargetPointChangedListener l) {
        _onTargetPointChanged.add(l);
    }

    public void addOnFahAngleChangedListener(OnFahAngleChangedListener l) {
        _onFahAngleChanged.add(l);
    }

    public void addOnFahWidthChangedListener(OnFahWidthChangedListener l) {
        _onFahWidthChanged.add(l);
    }

    public void addOnFahLegChangedListener(OnFahLegChangedListener l) {
        _onFahLegChanged.add(l);
    }

    public void addOnTouchableChangedListener(OnTouchableChangedListener l) {
        _onTouchableChanged.add(l);
    }

    public void addOnDesignatorPointChangedListener(
            OnDesignatorPointChangedListener l) {
        _onDesignatorPointChanged.add(l);
    }

    public void removeOnTargetPointChangedListener(
            OnTargetPointChangedListener l) {
        _onTargetPointChanged.remove(l);
    }

    public void removeOnFahAngleChangedListener(OnFahAngleChangedListener l) {
        _onFahAngleChanged.remove(l);
    }

    public void removeOnFahWidthChangedListener(OnFahWidthChangedListener l) {
        _onFahWidthChanged.remove(l);
    }

    public void removeOnFahLegChangedListener(OnFahLegChangedListener l) {
        _onFahLegChanged.remove(l);
    }

    public void removeOnTouchableChangedListener(OnTouchableChangedListener l) {
        _onTouchableChanged.remove(l);
    }

    public void removeOnDesignatorPointChangedListener(
            OnDesignatorPointChangedListener l) {
        _onDesignatorPointChanged.remove(l);
    }

    FahArrowWidget(MapView mapView) {
        _mapView = mapView;
        setZOrder(0d);
        SharedPreferences _preferences = PreferenceManager
                .getDefaultSharedPreferences(_mapView
                        .getContext());
        _preferences.registerOnSharedPreferenceChangeListener(this);
        distance = getInteger(_preferences,
                "fahDistance", DEFAULT_DISTANCE) * 1852d;

    }

    /**
     * Only used with the red X fah.
     */
    public void enableStandaloneManipulation() {
        addOnPressListener(_arrowListener);
        addOnUnpressListener(_arrowListener);
        addOnMoveListener(_arrowListener);
    }

    /**
     * Sets the target item for this widget to follow.
     * 
     * @param target The PointMapItem that represents the target.
     */
    public void setTargetItem(PointMapItem target) {
        if (_target != null) {
            _target.removeOnPointChangedListener(_targetListener);
            _mapView.getMapEventDispatcher().removeMapItemEventListener(
                    _target, _removeListener);
        }
        _target = target;

        _target.addOnPointChangedListener(_targetListener);
        _mapView.getMapEventDispatcher().addMapItemEventListener(_target,
                _removeListener);

        if (_designator == null || _fakeDesignator) {
            // slightly south of the target
            _lastDesignatorLoc = new GeoPoint(
                    _target.getPoint().getLatitude() - .01d,
                    _target.getPoint().getLongitude());
        }
        this.onTargetPointChanged();

    }

    /**
     * Sets the designator item for this widget to follow and adjust the heading off of this
     * location and the target location.
     * 
     * @param designator The PointMapItem that represents the designator.
     */
    public void setDesignatorItem(PointMapItem designator) {
        if (_designator != null) {
            _designator.removeOnPointChangedListener(_designatorListener);
            _mapView.getMapEventDispatcher().removeMapItemEventListener(
                    _designator, _removeListener);
        }
        _designator = designator;
        if (_designator == null) {
            _fakeDesignator = true;
        } else {
            _fakeDesignator = false;
        }

        if (_designator != null) {
            _lastDesignatorLoc = _designator.getPoint();
            _designator.addOnPointChangedListener(_designatorListener);
            _mapView.getMapEventDispatcher().addMapItemEventListener(
                    _designator, _removeListener);
            this.onDesignatorPointChanged();
        }
    }

    /**
     * Removes the listeners from the target.
     */
    public void dispose() {
        // Remove from parent first to stop rendering.
        if (getParent() != null)
            getParent().removeWidget(this);
        // Then remove listener to the target point
        if (_target != null) {
            _target.removeOnPointChangedListener(_targetListener);
            _mapView.getMapEventDispatcher().removeMapItemEventListener(
                    _target, _removeListener);
            _target = null;
        }
        if (_designator != null) {
            _designator.removeOnPointChangedListener(_designatorListener);
            _mapView.getMapEventDispatcher().removeMapItemEventListener(
                    _designator,
                    _removeListener);
            _designator = null;
        }
    }

    /**
     * Heading is set for the HaloWidget as Magnetic North.
     */
    public void setFahOffset(final double offset) {
        if (_fahOffset != offset) {
            _fahOffset = offset;
            _updateAngle();
        }
    }

    public double getFahOffset() {
        return _fahOffset;
    }

    public void setFahWidth(final double fahWidth) {
        if (_fahWidth != fahWidth) {
            _fahWidth = fahWidth;
            this.onFahWidthChanged();
        }
    }

    public double getTrueOffset() {
        if (_lastDesignatorLoc == null || _target == null) {
            return 0;
        }
        // Get the distance and azimuth to from the designator to the target
        int bearing = (int) Math.round(GeoCalculations
                .bearingTo(_lastDesignatorLoc, _target.getPoint()));
        double diff = ATAKUtilities.convertFromTrueToMagnetic(
                _target.getPoint(), bearing)
                - _fahAngle;
        if (diff < 0) {
            diff = 360 + diff;
        }
        diff = diff % 360;
        if (diff == 0 || diff == 1) {
            diff = 360;
        }
        return Math.round(diff);
    }

    /**
     * Completely rebuild the FAH.
     */
    public void rebuild() {
        this.onTargetPointChanged();
    }

    /**
     * Toggles the ability to draw the reverse of the FAH.
     * on by default.
     */
    public void setDrawReverse(final boolean reverse) {
        this.reverse = reverse;
        this.onTargetPointChanged();
    }

    public void setFahValues(final double offset, final double width) {

        if (_lastDesignatorLoc == null || _target == null) {
            return;
        }

        int bearing = (int) Math
                .round(GeoCalculations.bearingTo(_lastDesignatorLoc,
                        _target.getPoint()));

        // Now update the angle based on the azimuth and offset
        //Round the fahAngle to nearest 5 like everything else
        _fahAngle = (int) Math.round((bearing + _fahOffset) / 5f) * 5;
        _fahWidth = width;

        //Log.d(TAG, "offset: " + offset + " width: " + width + " computed FAH angle: " + _fahAngle);

        this.onFahLegChanged();

    }

    public void setTouchable(boolean touchable) {
        _touchable = touchable;
        this.onTouchableChanged();
    }

    @Override
    public boolean testHit(float x, float y) {
        return _hitBox.contains(x, y);
    }

    @Override
    public MapWidget seekHit(float x, float y) {
        if (_touchable && this.testHit(x, y))
            return this;
        else
            return null;
    }

    public double getFahAngle() {
        return _fahAngle;
    }

    public double getFahWidth() {
        return _fahWidth;
    }

    public boolean getTouchable() {
        return _touchable;
    }

    public GeoPoint getTargetPoint() {
        if (_target != null)
            return _target.getPoint();
        else
            return null;
    }

    public double getAppropriateDistance() {

        if (distance == 0) {
            // line 3 is stored in NM
            distance = _target.getMetaDouble("nineline_line_3", -1.0);
            distance = SpanUtilities.convert(distance, Span.NAUTICALMILE,
                    Span.METER);

            if (!(distance > 0) && (_designator != null)) {
                // overhead or 0, use the distance to the target
                distance = GeoCalculations.distanceTo(
                        _designator.getPoint(),
                        _target.getPoint());
            }

            distance = Math.abs(distance);

            if (distance > MAX_DISTANCE)
                distance = MAX_DISTANCE;

        }
        return distance;

    }

    public boolean drawReverse() {
        return reverse;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key) {

        if (key == null)
            return;

        if (key.equals("fahDistance")) {
            distance = getInteger(sharedPreferences, key, DEFAULT_DISTANCE)
                    * 1852d;
            rebuild();
        }
    }

    public GeoPoint getDesignatorPoint() {
        if (_designator != null)
            return _designator.getPoint();
        else
            return null;
    }

    private void onFahAngleChanged() {
        for (OnFahAngleChangedListener l : _onFahAngleChanged) {
            l.onFahAngleChanged(this);
        }
    }

    private void onFahWidthChanged() {
        for (OnFahWidthChangedListener l : _onFahWidthChanged) {
            l.onFahWidthChanged(this);
        }
    }

    private void onFahLegChanged() {
        for (OnFahLegChangedListener l : _onFahLegChanged) {
            l.onFahLegChanged(this);
        }
    }

    private void onTouchableChanged() {
        for (OnTouchableChangedListener l : _onTouchableChanged) {
            l.onTouchableChanged(this);
        }
    }

    private void onTargetPointChanged() {
        _updateAngle();
        for (OnTargetPointChangedListener l : _onTargetPointChanged) {
            l.onTargetChanged(this);
        }
    }

    private void onDesignatorPointChanged() {
        _updateAngle();
        for (OnDesignatorPointChangedListener l : _onDesignatorPointChanged) {
            l.onDesignatorChanged(this);
        }
    }

    private void _updateAngle() {
        if (_lastDesignatorLoc == null || _target == null) {
            return;
        }

        if (_fakeDesignator) {
            // track the target point in the south
            _lastDesignatorLoc = new GeoPoint(
                    _target.getPoint().getLatitude() - .01d,
                    _target.getPoint().getLongitude());
        }

        // Get the distance and azimuth to from the designator to the target
        // use TRUE for the angle computation.
        int bearing = (int) Math.round(ATAKUtilities.convertFromTrueToMagnetic(
                _target.getPoint(),
                GeoCalculations.bearingTo(_lastDesignatorLoc,
                        _target.getPoint())));

        // Now update the angle based on the azimuth and offset
        _fahAngle = (int) Math.round((bearing + _fahOffset) / 5f) * 5;

        // Call the update to the GL class
        this.onFahAngleChanged();
    }

    private final OnPointChangedListener _targetListener = new OnPointChangedListener() {
        @Override
        public void onPointChanged(PointMapItem item) {
            // It's fine if the target point changes often, since it would have to be moved by a
            // user
            onTargetPointChanged();
        }
    };

    private final OnPointChangedListener _designatorListener = new OnPointChangedListener() {
        @Override
        public void onPointChanged(PointMapItem item) {
            // Since the designator can be the user, we should prevent the FAH Widget from moving
            // a lot when the user's GPS jitters

            if (_lastDesignatorLoc == null
                    ||
                    Math.abs(_lastDesignatorLoc
                            .distanceTo(item.getPoint())) > 10) {

                _lastDesignatorLoc = item.getPoint();
                onDesignatorPointChanged();
            }
        }
    };

    private final MapEventDispatcher.OnMapEventListener _removeListener = new MapEventDispatcher.OnMapEventListener() {
        @Override
        public void onMapItemMapEvent(MapItem item, MapEvent event) {
            if (_target == item
                    && event.getType().equals(MapEvent.ITEM_REMOVED)) {
                setVisible(false);
            }
        }
    };

    private int getInteger(SharedPreferences _preferences,
            String key, int defaultVal) {
        try {
            return Integer.parseInt(_preferences
                    .getString(key, "" + defaultVal));
        } catch (Exception e) {
            return defaultVal;
        }
    }

    private final MapWidgetTouchListener _arrowListener = new MapWidgetTouchListener();

    // ******************************** Listeners ************************************//
    private class MapWidgetTouchListener implements MapWidget.OnMoveListener,
            MapWidget.OnPressListener, MapWidget.OnUnpressListener {

        boolean isMovingFah = false;
        float lastMoveX = Float.NaN;
        float lastMoveY = Float.NaN;

        private void _updateFahAngle(float x, float y) {

            if (_target == null)
                return;

            GeoPoint fahPt = _mapView
                    .inverse(x, y, MapView.InverseMode.RayCast).get();
            double bear = GeoCalculations.bearingTo(fahPt,
                    _target.getPoint());
            if (bear < 0)
                bear = bear + 360;
            double orig = GeoCalculations.bearingTo(
                    _lastDesignatorLoc,
                    _target.getPoint());

            if (orig < bear)
                orig += 360;

            double offset = orig - bear;

            int offsetInt = (int) Math.round(offset);
            offsetInt = (offsetInt / 5) * 5;

            if (offsetInt <= 180)
                offsetInt = -offsetInt;
            else if (offsetInt > 540)
                offsetInt = 720 - offsetInt;
            else
                offsetInt = 360 - offsetInt;

            setFahOffset(offsetInt);
        }

        @Override
        public void onMapWidgetUnpress(MapWidget widget, MotionEvent event) {
            if (isMovingFah) {
                _updateFahAngle(event.getX(), event.getY());
                isMovingFah = false;
            }

            // wait for the unpress to completely occur, then pop the listeners
            Thread t = new Thread() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(100);
                    } catch (Exception ignored) {
                    }
                    _mapView.getMapEventDispatcher().popListeners();
                }
            };
            t.start();
        }

        @Override
        public void onMapWidgetPress(MapWidget widget, MotionEvent event) {
            lastMoveX = event.getX();
            lastMoveY = event.getY();
            isMovingFah = true;

            // do not pass through any map clicks to any other listeners during the moving
            _mapView.getMapEventDispatcher().pushListeners();

            _mapView.getMapEventDispatcher().clearListeners(
                    MapEvent.MAP_CLICK);

            _mapView.getMapEventDispatcher().clearListeners(
                    MapEvent.ITEM_CLICK);
        }

        @Override
        public boolean onMapWidgetMove(MapWidget widget, MotionEvent event) {
            if (isMovingFah) {
                float x = event.getX();
                float y = event.getY();
                float d = 0f;

                if (!Float.isNaN(lastMoveX) && !Float.isNaN(lastMoveY)) {
                    // Don't update on every move event, too much computation
                    d = (float) Math.sqrt((lastMoveX - x) * (lastMoveX - x)
                            + (lastMoveY - y)
                                    * (lastMoveY - y));
                } else {
                    lastMoveX = x;
                    lastMoveY = y;
                }

                if (d > 25) {
                    lastMoveX = x;
                    lastMoveY = y;
                    _updateFahAngle(x, y);
                }
                return true;
            }
            return false;
        }
    }

    public static class Item extends Shape {

        private final MapView mapView;
        private final FahArrowWidget arrow;
        private final GLWidgetsMapComponent.WidgetTouchHandler touchListener;

        public Item(MapView view) {
            super(UUID.randomUUID().toString());

            this.mapView = view;
            this.arrow = new FahArrowWidget(view);
            this.setZOrder(Double.NEGATIVE_INFINITY);
            LayoutWidget w = new LayoutWidget();
            w.addWidget(this.arrow);
            this.touchListener = new GLWidgetsMapComponent.WidgetTouchHandler(
                    view, w);
            setClickable(true);
        }

        @Override
        public void setClickable(boolean t) {

            // potentially ben previously added by addition into a group
            mapView.removeOnTouchListener(this.touchListener);

            if (t)
                mapView.addOnTouchListenerAt(1, this.touchListener);
            else
                mapView.removeOnTouchListener(this.touchListener);

            arrow.setTouchable(t);
        }

        public FahArrowWidget getFAH() {
            return this.arrow;
        }

        @Override
        public GeoPoint[] getPoints() {
            return new GeoPoint[0];
        }

        @Override
        public GeoPointMetaData[] getMetaDataPoints() {
            return new GeoPointMetaData[0];
        }

        @Override
        public GeoBounds getBounds(MutableGeoBounds bounds) {
            if (bounds == null)
                bounds = new MutableGeoBounds(-90, -180, 90, 180);
            else
                bounds.set(-90, -180, 90, 180);
            return bounds;
        }

        @Override
        protected void onGroupChanged(boolean added, MapGroup mapGroup) {
            super.onGroupChanged(added, mapGroup);

            // make sure that the touch listener does not get added too many 
            // times.
            mapView.removeOnTouchListener(this.touchListener);

            if (added) {
                // only add the touch listener if it is touchable
                if (arrow.getTouchable()) {
                    mapView.addOnTouchListenerAt(1, this.touchListener);
                }
            } else
                mapView.removeOnTouchListener(this.touchListener);
        }
    }
}
