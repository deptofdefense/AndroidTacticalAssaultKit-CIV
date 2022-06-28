
package com.atakmap.android.widgets;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.graphics.RectF;
import android.preference.PreferenceManager;
import android.view.Gravity;

import gov.tak.api.widgets.IMapWidget;
import gov.tak.api.widgets.IScaleWidget2;
import gov.tak.platform.marshal.MarshalManager;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.navigation.views.NavView;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.app.ATAKActivity;
import com.atakmap.app.R;
import com.atakmap.map.AtakMapView;
import com.atakmap.math.MathUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;

import gov.tak.api.widgets.IRootLayoutWidget;

@Deprecated
@DeprecatedApi(since = "4.4")
public class RootLayoutWidget extends LayoutWidget implements IRootLayoutWidget,
        AtakMapView.OnMapViewResizedListener, View.OnTouchListener,
        MapWidget2.OnWidgetSizeChangedListener,
        SharedPreferences.OnSharedPreferenceChangeListener,
        ViewGroup.OnHierarchyChangeListener {

    private static final String TAG = "RootLayoutWidget";
    private static final String PREF_CURVED_SCREEN = "atakAdjustCurvedDisplay";

    public static final int TOP_LEFT = 0;
    public static final int TOP_EDGE = 1;
    public static final int TOP_RIGHT = 2;
    public static final int LEFT_EDGE = 3;
    public static final int RIGHT_EDGE = 4;
    public static final int BOTTOM_LEFT = 5;
    public static final int BOTTOM_RIGHT = 6;
    public static final int BOTTOM_EDGE = 7;
    private static final String[] LAYOUT_NAMES = new String[] {
            "Top Left", "Top Edge", "Top Right", "Left Edge", "Right Edge",
            "Bottom Left", "Bottom Right", "Bottom Edge"
    };

    // Background colors used for debugging purposes
    private static final int[] LAYOUT_COLORS = new int[] {
            0x40FF0000, 0x4000FF00, 0x40FFFF00, 0x400000FF, 0x40FF00FF,
            0x4000FFFF, 0x40FFFFFF, 0x4000FF00
    };

    /**
     * Listener for when any root layout widgets have changed position or size
     */
    public interface OnLayoutChangedListener {

        /**
         * Layout has been changed in some way
         */
        void onLayoutChanged();
    }

    private final MapView _mapView;
    private final SharedPreferences _prefs;
    private IMapWidget _pressedWidget;
    private Timer widTimer;
    private WidTimerTask widTask;

    // Padding for curved screens
    private float _curvePadding = 0f;
    private final RectF _usableArea = new RectF();
    private final View _content;
    private final View _parent;
    private final View _ddHandleLS;
    private final View _ddHandlePT;

    // Views that take up space
    private final List<View> _views = new ArrayList<>();
    private final Map<View, ViewListener> _viewListeners = new HashMap<>();

    private final LinearLayoutWidget[] _layouts = new LinearLayoutWidget[BOTTOM_EDGE
            + 1];
    private LinearLayoutWidget _ignore;

    // Listeners
    private final ConcurrentLinkedQueue<OnLayoutChangedListener> _layoutListeners = new ConcurrentLinkedQueue<>();

    private class WidTimerTask extends TimerTask {
        @Override
        public void run() {
            _pressedWidget.onLongPress();
        }
    }

    public RootLayoutWidget(final MapView mapView) {
        _mapView = mapView;
        _mapView.addOnTouchListener(this);
        _mapView.addOnMapViewResizedListener(this);

        _prefs = PreferenceManager.getDefaultSharedPreferences(
                mapView.getContext());
        _prefs.registerOnSharedPreferenceChangeListener(this);

        _parent = ((Activity) _mapView.getContext()).findViewById(
                R.id.map_parent);

        setSize(_mapView.getWidth(), _mapView.getHeight());

        // Initialize corner and side widgets
        for (int i = TOP_LEFT; i <= BOTTOM_EDGE; i++) {
            _layouts[i] = new LinearLayoutWidget();
            _layouts[i].setAlpha(255);
            _layouts[i].setName(LAYOUT_NAMES[i]);
            int gravity = 0;
            gravity |= (i == TOP_RIGHT || i == BOTTOM_RIGHT || i == RIGHT_EDGE)
                    ? Gravity.END
                    : (i == TOP_EDGE ? Gravity.CENTER_HORIZONTAL
                            : Gravity.START);
            gravity |= (i == BOTTOM_LEFT || i == BOTTOM_RIGHT)
                    ? Gravity.BOTTOM
                    : (i == LEFT_EDGE || i == RIGHT_EDGE || i == BOTTOM_EDGE
                            ? Gravity.CENTER_VERTICAL
                            : Gravity.TOP);
            _layouts[i].setGravity(gravity);
            _layouts[i].addOnWidgetSizeChangedListener(this);
            //_layouts[i].setBackingColor(LAYOUT_COLORS[i]);
            addWidget(_layouts[i]);
        }

        // Get the content view so we can properly adjust for curved display
        View content = null;
        try {
            Window window = ((Activity) _mapView.getContext()).getWindow();
            content = window.findViewById(Window.ID_ANDROID_CONTENT);
        } catch (Exception ignore) {
        }
        _content = content;

        boolean defValue = false;
        String mdl = android.os.Build.MODEL;
        if (mdl.contains("SM-G892") || mdl.startsWith("SM-G950") ||
                mdl.startsWith("SM-G955") || mdl.startsWith("SM-N950") ||
                mdl.startsWith("SM-G960") || mdl.startsWith("SM-G965")) {
            // Enable preference by default on Galaxy S8 and S9
            defValue = true;
        }
        _prefs.edit().putBoolean(PREF_CURVED_SCREEN, _prefs.getBoolean(
                PREF_CURVED_SCREEN, defValue)).apply();
        onSharedPreferenceChanged(_prefs, PREF_CURVED_SCREEN);

        // The drop-down handles which overlap edges of the widget's usable area
        ATAKActivity act = (ATAKActivity) _mapView.getContext();
        _ddHandleLS = act.findViewById(R.id.sidepanehandle_background);
        _ddHandlePT = act.findViewById(R.id.sidepanehandle_background_portrait);
        addView(_ddHandleLS);
        addView(_ddHandlePT);

        // The top-right toolbar
        addView(act.findViewById(R.id.toolbar_drawer));

        // Nav button views
        NavView navView = NavView.getInstance();
        for (int i = 0; i < navView.getChildCount(); i++)
            addView(navView.getChildAt(i));

        // Track newly added/removed views
        navView.addOnHierarchyChangedListener(this);
    }

    /**
     * Get a layout given its corner index
     * @param corner Corner index (TOP_LEFT, TOP_RIGHT, etc.)
     * @return Corner layout
     */
    public LinearLayoutWidget getLayout(int corner) {
        return (corner >= TOP_LEFT && corner <= BOTTOM_RIGHT)
                || corner == BOTTOM_EDGE ? _layouts[corner]
                        : null;
    }

    /**
     * Get the occupied boundaries taken up by views (and optionally widgets)
     * in the root layout
     * @param includeWidgets True to include widgets in the result
     * @return Occupied boundary rectangles
     */
    public List<Rect> getOccupiedBounds(boolean includeWidgets) {
        List<Rect> ret = new ArrayList<>(_views.size() + _layouts.length);
        for (View v : _views) {
            if (v.getVisibility() == View.VISIBLE)
                ret.add(LayoutHelper.getBounds(v));
        }
        if (includeWidgets) {
            for (LinearLayoutWidget w : _layouts) {
                if (w.isVisible())
                    ret.add(LayoutHelper.getBounds(w));
            }
        }
        return ret;
    }

    /**
     * Get the occupied boundaries taken up by views and widgets
     * in the root layout
     * @return Occupied boundary rectangles
     */
    public List<Rect> getOccupiedBounds() {
        return getOccupiedBounds(true);
    }

    /**
     * Add listener for when any root widgets are moved or resized
     * @param l Listener
     */
    public void addOnLayoutChangedListener(OnLayoutChangedListener l) {
        _layoutListeners.add(l);
    }

    /**
     * Remove layout changed listener
     * @param l Listener
     */
    public void removeOnLayoutChangedListener(OnLayoutChangedListener l) {
        _layoutListeners.remove(l);
    }

    @Override
    public void onMapViewResized(AtakMapView view) {
        setSize(view.getWidth(), view.getHeight());
    }

    @Override
    public void onSizeChanged() {
        super.onSizeChanged();
        for (LinearLayoutWidget l : _layouts) {
            if (l != null)
                onWidgetSizeChanged(l);
        }
    }

    @Override
    public void onChildViewAdded(View parent, View child) {
        // A view has been added to the nav layout
        addView(child);
        onSizeChanged();
    }

    @Override
    public void onChildViewRemoved(View parent, View child) {
        // A view has been removed from the nav layout
        removeView(child);
        onSizeChanged();
    }

    @Override
    public void onWidgetSizeChanged(MapWidget2 widget) {
        if (widget == _ignore)
            return;

        if (_content != null)
            _usableArea.set(_curvePadding, 0f,
                    _content.getWidth() - _curvePadding,
                    _content.getHeight() - _curvePadding);

        Rect maxBounds = LayoutHelper.getBounds(this);
        List<Rect> bounds = getOccupiedBounds(false);
        LayoutHelper layoutHelper = new LayoutHelper(maxBounds, bounds);

        LinearLayoutWidget w = (LinearLayoutWidget) widget;
        float[] wMargin = w.getMargins();
        float[] wSize = w.getSize(true, false);

        float left = _padding[LEFT] + wMargin[LEFT];
        float top = _padding[TOP] + wMargin[TOP];
        float right = _width - _padding[RIGHT] - wMargin[RIGHT] - wSize[0];
        float bottom = _height - _padding[BOTTOM] - wMargin[BOTTOM] - wSize[1];
        int parentWidth = _parent.getWidth();
        int parentHeight = _parent.getHeight();

        // Offset by right-side drop-down in landscape mode
        if (w == _layouts[RIGHT_EDGE] && _ddHandleLS != null
                && _ddHandleLS.getVisibility() == View.VISIBLE)
            right -= _ddHandleLS.getWidth() / 2f;

        // Offset by bottom drop-down in portrait mode
        if (_ddHandlePT != null && _ddHandlePT.getVisibility() == View.VISIBLE)
            bottom -= _ddHandlePT.getHeight() / 2f;

        // Align layouts while avoiding overlap

        // Top-left corner
        if (w == _layouts[TOP_LEFT]) {

            Rect tlRect = getChildrenBounds(w);
            layoutHelper.add(_layouts[BOTTOM_LEFT]);
            tlRect = layoutHelper.findBestPosition(tlRect, TOP_LEFT);

            w.setPoint(tlRect.left, tlRect.top);
            onWidgetSizeChanged(_layouts[TOP_EDGE]);
            onWidgetSizeChanged(_layouts[LEFT_EDGE]);
        }

        // Top-right corner
        else if (w == _layouts[TOP_RIGHT]) {

            Rect trRect = getChildrenBounds(w);
            layoutHelper.add(_layouts[BOTTOM_RIGHT]);
            trRect = layoutHelper.findBestPosition(trRect, TOP_RIGHT);

            // Update widget position and padding
            w.setPoint(trRect.left, trRect.top);
            onWidgetSizeChanged(_layouts[TOP_EDGE]);
            onWidgetSizeChanged(_layouts[RIGHT_EDGE]);
        }

        // Bottom left corner
        else if (w == _layouts[BOTTOM_LEFT]) {
            Rect blRect = getChildrenBounds(w);
            layoutHelper.add(_layouts[BOTTOM_RIGHT]);
            layoutHelper.add(_layouts[TOP_LEFT]);
            layoutHelper.add(_layouts[BOTTOM_EDGE]);
            blRect = layoutHelper.findBestPosition(blRect, BOTTOM_LEFT);

            w.setPoint(blRect.left, blRect.top);
            onWidgetSizeChanged(_layouts[LEFT_EDGE]);
        }

        // Bottom right corner
        else if (w == _layouts[BOTTOM_RIGHT]) {
            // Adjust padding based on screen curve (if any)
            float pad = MathUtils.clamp(Math.min(
                    (right + wSize[0]) - _usableArea.right,
                    (bottom + wSize[1]) - _usableArea.bottom),
                    0, _curvePadding);

            if (w.setPadding(0f, 0f, pad, pad))
                return;

            w.setPoint(right, bottom);
            onWidgetSizeChanged(_layouts[RIGHT_EDGE]);
            onWidgetSizeChanged(_layouts[BOTTOM_EDGE]);
        }

        // Top edge - fill area between top-left and top-right layouts
        else if (w == _layouts[TOP_EDGE]) {

            // Get the size of the content we need to fit
            float[] childrenSize = getChildrenSize(w);
            int width = (int) childrenSize[0];
            int height = (int) childrenSize[1];

            // If the children are set to MATCH_PARENT or have no defined size
            // then default to a decent percentage of the screen
            int defSize = (int) (Math.min(parentWidth, parentHeight) / 1.75f);
            if (width <= 0 || width >= parentWidth)
                width = defSize;
            if (height <= 0 || height >= parentHeight)
                height = defSize / 2;

            // Get all possible top-aligned boundaries and sort from top-to-bottom
            layoutHelper.add(_layouts[TOP_RIGHT]);
            layoutHelper.add(_layouts[TOP_LEFT]);

            // Find the best placement with bias toward the top-right corner
            Rect wr = new Rect(0, 0, width, height);
            wr = layoutHelper.findBestPosition(wr, TOP_EDGE);

            // Calculate the max available width
            wr = layoutHelper.findMaxWidth(wr);

            // Update position and size while preventing recursion
            _ignore = w;
            w.setPoint(wr.left, wr.top);
            w.setLayoutParams(wr.width(), LayoutParams.WRAP_CONTENT);
            _ignore = null;
        }

        // Left edge - fill area between top-left and bottom-left layouts
        else if (w == _layouts[LEFT_EDGE]) {
            Rect leRect = getChildrenBounds(w);

            layoutHelper.add(_layouts[TOP_LEFT]);
            layoutHelper.add(_layouts[BOTTOM_LEFT]);
            layoutHelper.add(_layouts[BOTTOM_EDGE]);

            leRect = layoutHelper.findBestPosition(leRect, LEFT_EDGE);
            leRect = layoutHelper.findMaxHeight(leRect);

            w.setPoint(leRect.left, leRect.top);
            w.setLayoutParams(LinearLayoutWidget.WRAP_CONTENT, leRect.height());
        }

        // Right edge - fill area between top-right and bottom-right layouts
        else if (w == _layouts[RIGHT_EDGE]) {
            Rect reRect = getChildrenBounds(w);

            layoutHelper.add(_layouts[TOP_RIGHT]);
            layoutHelper.add(_layouts[BOTTOM_RIGHT]);

            reRect = layoutHelper.findBestPosition(reRect, RIGHT_EDGE);
            reRect = layoutHelper.findMaxHeight(reRect);

            w.setPoint(reRect.left, reRect.top);
            w.setLayoutParams(LinearLayoutWidget.WRAP_CONTENT, reRect.height());
        }

        //Bottom edge - fill full bottom area
        else if (w == _layouts[BOTTOM_EDGE]) {
            float bPad = (bottom + wSize[1]) - _usableArea.bottom;
            float lPad = MathUtils.clamp(Math.min(
                    _usableArea.left - left, bPad),
                    0, _curvePadding);
            float rPad = MathUtils.clamp(Math.min(
                    (right + wSize[0]) - _usableArea.right, bPad),
                    0, _curvePadding);

            Rect beRect = getChildrenBounds(w);

            layoutHelper.add(_layouts[BOTTOM_RIGHT]);

            beRect = layoutHelper.findBestPosition(beRect, BOTTOM_EDGE);
            beRect = layoutHelper.findMaxWidth(beRect);

            _ignore = w;
            w.setPadding(lPad, 0f, rPad, 0f);
            w.setPoint(beRect.left, beRect.top);
            w.setLayoutParams(beRect.width(), LinearLayoutWidget.WRAP_CONTENT);
            _ignore = null;
            onWidgetSizeChanged(_layouts[BOTTOM_LEFT]);
        }

        // Fire layout listeners
        _mapView.post(new Runnable() {
            @Override
            public void run() {
                for (OnLayoutChangedListener l : _layoutListeners)
                    l.onLayoutChanged();
            }
        });
    }

    @Override
    public void onActionBarToggled(boolean showing) {
        super.onActionBarToggled(showing);

        // Delay positional updates until views are given a chance to initialize
        _mapView.post(new Runnable() {
            @Override
            public void run() {
                onSizeChanged();
            }
        });
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sp, String key) {

        if (key == null)
            return;

        // Make sure widgets aren't cut off on curved-screen devices (ATAK-8160)
        if (key.equals(PREF_CURVED_SCREEN)) {
            _curvePadding = 0f;
            if (sp.getBoolean(PREF_CURVED_SCREEN, false)) {
                float dp = _mapView.getContext().getResources()
                        .getDisplayMetrics().density;
                _curvePadding = 7f * dp;
            }
            onSizeChanged();
        }
    }

    @Override
    public boolean onTouch(View v, android.view.MotionEvent aEvent) {
        if (v instanceof MapView && ((MapView) v).getMapTouchController()
                .isLongPressDragging())
            // Prevent widgets from interfering with the long-press drag event
            return false;
        final gov.tak.platform.ui.MotionEvent event = MarshalManager.marshal(
                aEvent, android.view.MotionEvent.class,
                gov.tak.platform.ui.MotionEvent.class);
        return handleMotionEvent(event);
    }

    @Override
    public boolean handleMotionEvent(gov.tak.platform.ui.MotionEvent event) {
        IMapWidget hit = seekWidgetHit(event, event.getX(), event.getY());
        if (hit == null && _pressedWidget != null
                && event.getAction() == android.view.MotionEvent.ACTION_MOVE) {
            boolean used = _pressedWidget.onMove(event);
            // If this widget isn't handling move events then get rid of it
            if (!used) {
                _pressedWidget.onUnpress(event);
                _pressedWidget = null;
                return false;
            } else {
                return true;
            }
        }

        if (hit != _pressedWidget) {
            if (_pressedWidget != null) {
                _pressedWidget.onUnpress(event);
                _pressedWidget = null;
            }
        }
        if (hit != null) {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    if (_pressedWidget != hit) {
                        hit.onPress(event);
                        _pressedWidget = hit;

                        //start long press countdown 1 sec
                        widTimer = new Timer("GLWidgetsMapComponent");
                        widTimer.schedule(widTask = new WidTimerTask(),
                                ViewConfiguration.getLongPressTimeout());
                    }
                    break;
                case android.view.MotionEvent.ACTION_MOVE:
                    if (_pressedWidget == hit) {
                        hit.onMove(event);
                    } else {
                        hit.onPress(event);
                        _pressedWidget = hit;
                    }
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    hit.onUnpress(event);
                    if (_pressedWidget == hit) {
                        if (widTask != null)
                            widTask.cancel();
                        if (widTimer != null && widTimer.purge() > 0) {
                            //the long press task was canceled, so onClick
                            hit.onClick(event);
                        } //otherwise, ignore
                    }
                    _pressedWidget = null;
                    break;
            }
        }

        return hit != null;
    }

    private void addView(View v) {
        if (v != null) {
            _views.add(v);
            _viewListeners.put(v, new ViewListener(v));
        }
    }

    private void removeView(View v) {
        if (v != null) {
            _views.remove(v);
            ViewListener l = _viewListeners.remove(v);
            if (l != null)
                l.dispose();
        }
    }

    /* Static helper methods */

    private static float[] getFullSize(MapWidget2 w) {
        float[] p = w.getPadding();
        float[] m = w.getMargins();
        return new float[] {
                w.getWidth() + p[LEFT] + p[RIGHT] + m[LEFT] + m[RIGHT],
                w.getHeight() + p[TOP] + p[BOTTOM] + m[TOP] + m[BOTTOM]
        };
    }

    private static float[] getChildrenSize(LinearLayoutWidget w) {
        float maxW = 0, maxH = 0, totalW = 0, totalH = 0;
        Collection<IMapWidget> children = w.getChildren();
        for (IMapWidget c : children) {
            float[] size = getSize(c, true, true);
            if (c instanceof IScaleWidget2) {
                // Scale bar can dynamically resize based on available width
                // Use the minimum width instead of current width
                IScaleWidget2 sw = (IScaleWidget2) c;
                size[0] = sw.getMinWidth();
            }
            if (c instanceof LinearLayoutWidget) {
                LinearLayoutWidget llw = (LinearLayoutWidget) c;
                if (llw._paramWidth == LayoutParams.MATCH_PARENT)
                    size[0] = 0;
                if (llw._paramHeight == LayoutParams.MATCH_PARENT)
                    size[1] = 0;
            }
            maxW = Math.max(maxW, size[0]);
            maxH = Math.max(maxH, size[1]);
            totalW += size[0];
            totalH += size[1];
        }
        int orientation = w.getOrientation();
        float cWidth = orientation == LinearLayout.HORIZONTAL ? totalW : maxW;
        float cHeight = orientation == LinearLayout.VERTICAL ? totalH : maxH;
        return new float[] {
                cWidth, cHeight
        };
    }

    private static Rect getChildrenBounds(LinearLayoutWidget w) {
        float[] childrenSize = getChildrenSize(w);
        int width = (int) Math.max(1, childrenSize[0]);
        int height = (int) Math.max(1, childrenSize[1]);
        return new Rect(0, 0, width, height);
    }

    private class ViewListener implements View.OnLayoutChangeListener,
            ViewTreeObserver.OnGlobalLayoutListener {

        private final View _view;
        //private final String _name;
        private int _visibility;

        ViewListener(View view) {
            _view = view;
            //_name = _mapView.getResources().getResourceName(view.getId());
            _visibility = view.getVisibility();
            _view.addOnLayoutChangeListener(this);
            _view.getViewTreeObserver().addOnGlobalLayoutListener(this);
        }

        public void dispose() {
            _view.removeOnLayoutChangeListener(this);
            _view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
        }

        @Override
        public void onLayoutChange(View v, int l, int t, int r, int b,
                int ol, int ot, int or, int ob) {
            // Nav views changed position or size
            if (l != ol || t != ot || r != or || b != ob) {
                //Log.d(TAG, _name + ": " + l + ", " + t + ", " + r + ", " + b);
                onSizeChanged();
            }
        }

        @Override
        public void onGlobalLayout() {
            int visibility = _view.getVisibility();
            if (visibility != _visibility) {
                _visibility = visibility;
                //Log.d(TAG, _name + ": visibility changed = " + visibility);
                onSizeChanged();
            }
        }
    }
}
