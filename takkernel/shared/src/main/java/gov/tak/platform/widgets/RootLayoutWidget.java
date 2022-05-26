
package gov.tak.platform.widgets;

import gov.tak.api.widgets.IMapWidget;
import gov.tak.api.widgets.IRootLayoutWidget;
import gov.tak.platform.view.Gravity;
import gov.tak.platform.graphics.Insets;
import gov.tak.platform.ui.MotionEvent;

import java.util.Timer;
import java.util.TimerTask;

public class RootLayoutWidget extends LayoutWidget implements IRootLayoutWidget {

    private static final String TAG = "RootLayoutWidget";

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

    private IMapWidget _pressedWidget;
    private IMapWidget _hoveredWidget;
    private Timer widTimer;
    private WidTimerTask widTask;

    private Insets _insets;

    private final LinearLayoutWidget[] _layouts = new LinearLayoutWidget[BOTTOM_EDGE
            + 1];

    private class WidTimerTask extends TimerTask {
        @Override
        public void run() {
            gov.tak.platform.ui.UIEventQueue.invokeLater(new Runnable() {
                @Override
                public void run() {
                    if (_pressedWidget != null) _pressedWidget.onLongPress();
                }
            });
        }
    }

    public RootLayoutWidget() {
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
            //_layouts[i].setBackingColor(LAYOUT_COLORS[i]);
            addChildWidget(_layouts[i]);
        }
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

    public void setInsets(Insets insets) {
        _insets = insets;
    }

    @Override
    public void onSizeChanged() {
        super.onSizeChanged();
        for (LinearLayoutWidget l : _layouts) {
        }
    }
    
    public float[] getFullSize(IMapWidget w) {
        float[] p = w.getPadding();
        float[] m = w.getMargins();
        return new float[] {
                w.getWidth() + p[LEFT] + p[RIGHT] + m[LEFT] + m[RIGHT],
                w.getHeight() + p[TOP] + p[BOTTOM] + m[TOP] + m[BOTTOM]
        };
    }

    @Override
    public boolean handleMotionEvent(MotionEvent event) {

        IMapWidget hit = seekWidgetHit(event, event.getX(), event.getY());
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

        // check for hover exit
        if (hit != _hoveredWidget) {
            if (_hoveredWidget != null) {
                MotionEvent hoverExit = MotionEvent.obtain(event.getDownTime(),
                        event.getEventTime(),
                        MotionEvent.ACTION_HOVER_EXIT,
                        event.getX(),
                        event.getY(),
                        event.getPressure(),
                        event.getSize(),
                        event.getMetaState(),
                        event.getXPrecision(),
                        event.getYPrecision(),
                        event.getDeviceId(),
                        event.getEdgeFlags());
                _hoveredWidget.onHover(hoverExit);
                _hoveredWidget = null;
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
                                1000);
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

                case MotionEvent.ACTION_HOVER_ENTER:
                case MotionEvent.ACTION_HOVER_MOVE:
                    if (_hoveredWidget == null) {
                        _hoveredWidget = hit;
                        MotionEvent hoverEnter = MotionEvent.obtain(event.getDownTime(),
                                event.getEventTime(),
                                MotionEvent.ACTION_HOVER_ENTER,
                                event.getX(),
                                event.getY(),
                                event.getPressure(),
                                event.getSize(),
                                event.getMetaState(),
                                event.getXPrecision(),
                                event.getYPrecision(),
                                event.getDeviceId(),
                                event.getEdgeFlags());
                        hit.onHover(hoverEnter);
                    } else {
                        // pass along the move event
                        hit.onHover(event);
                    }
                    break;
            }
        }

        return hit != null;
    }
}
