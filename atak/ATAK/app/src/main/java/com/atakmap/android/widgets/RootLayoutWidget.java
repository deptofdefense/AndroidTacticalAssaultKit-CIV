
package com.atakmap.android.widgets;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.graphics.RectF;
import android.preference.PreferenceManager;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.Window;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.tools.ActionBarReceiver;
import com.atakmap.app.ATAKActivity;
import com.atakmap.app.R;
import com.atakmap.map.AtakMapView;
import com.atakmap.math.MathUtils;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class RootLayoutWidget extends LayoutWidget implements
        AtakMapView.OnMapViewResizedListener, View.OnTouchListener,
        MapWidget2.OnWidgetSizeChangedListener,
        SharedPreferences.OnSharedPreferenceChangeListener,
        View.OnLayoutChangeListener {

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

    private final MapView _mapView;
    private final SharedPreferences _prefs;
    private MapWidget _pressedWidget;
    private Timer widTimer;
    private WidTimerTask widTask;

    // Padding for curved screens
    private float _curvePadding = 0f;
    private final RectF _usableArea = new RectF();
    private View _content;
    private final View _ddHandleLS;
    private final View _ddHandlePT;

    private final LinearLayoutWidget[] _layouts = new LinearLayoutWidget[BOTTOM_EDGE
            + 1];

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
        try {
            _content = ((Activity) _mapView.getContext()).getWindow()
                    .findViewById(Window.ID_ANDROID_CONTENT);
        } catch (Exception ignore) {
        }
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
        if (_ddHandleLS != null)
            _ddHandleLS.addOnLayoutChangeListener(this);
        if (_ddHandlePT != null)
            _ddHandlePT.addOnLayoutChangeListener(this);
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

    @Override
    public void onMapViewResized(AtakMapView view) {
        setSize(view.getWidth(), view.getHeight());
    }

    @Override
    protected void onSizeChanged() {
        super.onSizeChanged();
        for (LinearLayoutWidget l : _layouts) {
            if (l != null)
                onWidgetSizeChanged(l);
        }
    }

    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom,
            int oldLeft, int oldTop, int oldRight, int oldBottom) {
        // Drop down handles changed visibility or size
        onSizeChanged();
    }

    @Override
    public void onWidgetSizeChanged(MapWidget2 widget) {
        if (_content != null)
            _usableArea.set(_curvePadding, 0f,
                    _content.getWidth() - _curvePadding,
                    _content.getHeight() - _curvePadding);

        LinearLayoutWidget w = (LinearLayoutWidget) widget;
        float[] wMargin = w.getMargins();
        float[] wSize = w.getSize(true, false);

        float left = _padding[LEFT] + wMargin[LEFT];
        float top = _padding[TOP] + wMargin[TOP] + _mapView
                .getActionBarHeight();
        float right = _width - _padding[RIGHT] - wMargin[RIGHT] - wSize[0];
        float bottom = _height - _padding[BOTTOM] - wMargin[BOTTOM] - wSize[1];

        float[] tlSize = getFullSize(_layouts[TOP_LEFT]);
        float[] trSize = getFullSize(_layouts[TOP_RIGHT]);
        float[] blSize = getFullSize(_layouts[BOTTOM_LEFT]);
        float[] brSize = getFullSize(_layouts[BOTTOM_RIGHT]);
        float[] bSize = getFullSize(_layouts[BOTTOM_EDGE]);

        // Offset by bottom edge
        if (w != _layouts[BOTTOM_EDGE])
            bottom -= bSize[1];

        // Offset by right-side drop-down in landscape mode
        if (w == _layouts[RIGHT_EDGE] && _ddHandleLS != null
                && _ddHandleLS.getVisibility() == View.VISIBLE)
            right -= _ddHandleLS.getWidth() / 2f;

        // Offset by bottom drop-down in portrait mode
        if (_ddHandlePT != null && _ddHandlePT.getVisibility() == View.VISIBLE)
            bottom -= _ddHandlePT.getHeight() / 2f;

        // Align layouts
        if (w == _layouts[TOP_LEFT]) {
            w.setPoint(left, top);
            onWidgetSizeChanged(_layouts[TOP_EDGE]);
            onWidgetSizeChanged(_layouts[LEFT_EDGE]);
        } else if (w == _layouts[TOP_RIGHT]) {
            // Need to add padding to account for  top-right by toolbar handle
            // and floating action bar
            // TODO: Convert handle and floating toolbar to widgets?
            List<Rect> bounds = ActionBarReceiver.getInstance()
                    .getTopAlignedBounds();
            float topPadding = 0;
            float leftPadding = 0;
            for (Rect r : bounds) {
                if (r.width() >= _width)
                    continue;
                topPadding += r.height();
                leftPadding = Math.max(leftPadding, r.width() - w.getWidth());
            }
            w.setPoint(right, top);
            w.setPadding(leftPadding, topPadding, 0, 0);
            onWidgetSizeChanged(_layouts[TOP_EDGE]);
            onWidgetSizeChanged(_layouts[RIGHT_EDGE]);
        } else if (w == _layouts[BOTTOM_LEFT]) {
            // Adjust padding based on screen curve (if any)
            float pad = MathUtils.clamp(Math.min(
                    _usableArea.left - left,
                    (bottom + wSize[1]) - _usableArea.bottom),
                    0, _curvePadding);

            if (w.setPadding(pad, 0f, 0f, pad))
                return;

            w.setPoint(left, bottom);
            onWidgetSizeChanged(_layouts[LEFT_EDGE]);
        } else if (w == _layouts[BOTTOM_RIGHT]) {
            // Adjust padding based on screen curve (if any)
            float pad = MathUtils.clamp(Math.min(
                    (right + wSize[0]) - _usableArea.right,
                    (bottom + wSize[1]) - _usableArea.bottom),
                    0, _curvePadding);

            if (w.setPadding(0f, 0f, pad, pad))
                return;

            w.setPoint(right, bottom);
            onWidgetSizeChanged(_layouts[RIGHT_EDGE]);
        }

        // Top edge - fill area between top-left and top-right layouts
        else if (w == _layouts[TOP_EDGE]) {
            left += tlSize[0];
            right += wSize[0] - trSize[0];
            w.setPoint(left, top);
            w.setLayoutParams((int) (right - left),
                    LinearLayoutWidget.WRAP_CONTENT);
        }

        // Left edge - fill area between top-left and bottom-left layouts
        else if (w == _layouts[LEFT_EDGE]) {
            top += tlSize[1];
            bottom += wSize[1] - blSize[1];
            w.setPoint(left, top);
            w.setLayoutParams(LinearLayoutWidget.WRAP_CONTENT,
                    (int) (bottom - top));
        }

        // Right edge - fill area between top-right and bottom-right layouts
        else if (w == _layouts[RIGHT_EDGE]) {
            top += trSize[1];
            bottom += wSize[1] - brSize[1];
            w.setPoint(right, top);
            w.setLayoutParams(LinearLayoutWidget.WRAP_CONTENT,
                    (int) (bottom - top));
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
            bPad = Math.max(lPad, rPad);

            if (w.setPadding(lPad, 0f, rPad, bPad))
                return;

            w.setLayoutParams(LinearLayoutWidget.MATCH_PARENT,
                    LinearLayoutWidget.WRAP_CONTENT);
            w.setPoint(left, bottom);
            onWidgetSizeChanged(_layouts[BOTTOM_LEFT]);
            onWidgetSizeChanged(_layouts[BOTTOM_RIGHT]);
        }
    }

    private float[] getFullSize(MapWidget2 w) {
        float[] p = w.getPadding();
        float[] m = w.getMargins();
        return new float[] {
                w.getWidth() + p[LEFT] + p[RIGHT] + m[LEFT] + m[RIGHT],
                w.getHeight() + p[TOP] + p[BOTTOM] + m[TOP] + m[BOTTOM]
        };
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
    public boolean onTouch(View v, MotionEvent event) {
        if (v instanceof MapView && ((MapView) v).getMapTouchController()
                .isLongPressDragging())
            // Prevent widgets from interfering with the long-press drag event
            return false;
        MapWidget hit = seekHit(event, event.getX(), event.getY());
        if (hit == null && _pressedWidget != null
                && event.getAction() == MotionEvent.ACTION_MOVE) {
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
                case MotionEvent.ACTION_DOWN:
                    if (_pressedWidget != hit) {
                        hit.onPress(event);
                        _pressedWidget = hit;

                        //start long press countdown 1 sec
                        widTimer = new Timer("GLWidgetsMapComponent");
                        widTimer.schedule(widTask = new WidTimerTask(),
                                ViewConfiguration.getLongPressTimeout());
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (_pressedWidget == hit) {
                        hit.onMove(event);
                    } else {
                        hit.onPress(event);
                        _pressedWidget = hit;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
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
}
