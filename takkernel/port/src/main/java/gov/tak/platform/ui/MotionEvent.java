package gov.tak.platform.ui;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * User input event that covers touch and other pointer devices. This implementation is loosely based on Android's
 * MotionEvent.
 */
public final class MotionEvent {
    /**
     * Button press occurred
     */
    public static final int ACTION_BUTTON_PRESS = 11;

    /**
     * Button release occurred
     */
    public static final int ACTION_BUTTON_RELEASE = 12;

    /**
     * The gesture has been aborted.
     */
    public static final int ACTION_CANCEL = 3;

    /**
     * A press has started.
     */
    public static final int ACTION_DOWN = 0;

    /**
     * The pointer is not down but has entered the boundaries of a view.
     */
    public static final int ACTION_HOVER_ENTER = 9;

    /**
     * The pointer is not down but has exited the boundaries of a view.
     */
    public static final int ACTION_HOVER_EXIT = 10;

    /**
     * A change in position when the pointer is not down.
     */
    public static final int ACTION_HOVER_MOVE = 7;

    /**
     * Masks for the bits in action that are the action value itself (ACTION_* value)
     */
    public static final int ACTION_MASK = 0xff;

    /**
     * A change in position during a press.
     */
    public static final int ACTION_MOVE = 2;

    /**
     * Action occurred outside the motion bounds
     */
    public static final int ACTION_OUTSIDE = 4;

    /**
     * Mask off pointer index
     */
    public static final int ACTION_POINTER_INDEX_MASK = 0xff00;

    /**
     * Shift right to pointer index
     */
    public static final int ACTION_POINTER_INDEX_SHIFT = 8;

    /**
     * Non-primary pointer down
     */
    public static final int ACTION_POINTER_DOWN = 5;

    /**
     * Non-primary pointer up
     */
    public static final int ACTION_POINTER_UP = 6;

    /**
     * The action describes a change in horizontal and/or vertical scroll positions (AXIS_VSCROLL and AXIS_HSCROLL)
     */
    public static final int ACTION_SCROLL = 8;

    /**
     * A press has finished.
     */
    public static final int ACTION_UP = 1;

    public static final int AXIS_BRAKE = 23;
    public static final int AXIS_DISTANCE = 24;
    public static final int AXIS_GAS = 22;
    public static final int AXIS_GENERIC_1 = 32;
    public static final int AXIS_GENERIC_10 = 41;
    public static final int AXIS_GENERIC_11 = 42;
    public static final int AXIS_GENERIC_12 = 43;
    public static final int AXIS_GENERIC_13 = 44;
    public static final int AXIS_GENERIC_14 = 45;
    public static final int AXIS_GENERIC_15 = 46;
    public static final int AXIS_GENERIC_16 = 47;
    public static final int AXIS_GENERIC_2 = 33;
    public static final int AXIS_GENERIC_3 = 34;
    public static final int AXIS_GENERIC_4 = 35;
    public static final int AXIS_GENERIC_5 = 36;
    public static final int AXIS_GENERIC_6 = 37;
    public static final int AXIS_GENERIC_7 = 38;
    public static final int AXIS_GENERIC_8 = 39;
    public static final int AXIS_GENERIC_9 = 40;
    public static final int AXIS_HAT_X = 15;
    public static final int AXIS_HAT_Y = 16;
    public static final int AXIS_HSCROLL = 10;
    public static final int AXIS_LTRIGGER = 17;
    public static final int AXIS_ORIENTATION = 8;
    public static final int AXIS_PRESSURE = 2;
    public static final int AXIS_RELATIVE_X = 27;
    public static final int AXIS_RELATIVE_Y = 28;
    public static final int AXIS_RTRIGGER = 18;
    public static final int AXIS_RUDDER = 20;
    public static final int AXIS_RX = 12;
    public static final int AXIS_RY = 13;
    public static final int AXIS_RZ = 14;
    public static final int AXIS_SCROLL = 26;
    public static final int AXIS_SIZE = 3;
    public static final int AXIS_THROTTLE = 19;
    public static final int AXIS_TILT = 25;
    public static final int AXIS_TOOL_MAJOR = 6;
    public static final int AXIS_TOOL_MINOR = 7;
    public static final int AXIS_TOUCH_MAJOR = 4;
    public static final int AXIS_TOUCH_MINOR = 5;
    public static final int AXIS_VSCROLL = 9;
    public static final int AXIS_WHEEL = 21;
    public static final int AXIS_X = 0;
    public static final int AXIS_Y = 1;
    public static final int AXIS_Z = 11;

    /**
     * Mouse back button
     */
    public static final int BUTTON_BACK = 8;

    /**
     * Mouse forward button
     */
    public static final int BUTTON_FORWARD = 16;

    /**
     * Left mouse button
     */
    public static final int BUTTON_PRIMARY = 1;

    /**
     * Right mouse button
     */
    public static final int BUTTON_SECONDARY = 2;

    /**
     * Primary stylus button
     */
    public static final int BUTTON_STYLUS_PRIMARY = 32;

    /**
     * Secondary stylus button
     */
    public static final int BUTTON_STYLUS_SECONDARY = 64;

    /**
     * Middle mouse button
     */
    public static final int BUTTON_TERTIARY = 4;

    public static final int CLASSIFICATION_AMBIGUOUS_GESTURE = 1;
    public static final int CLASSIFICATION_DEEP_PRESS = 2;
    public static final int CLASSIFICATION_NONE = 0;

    public static final int EDGE_BOTTOM = 2;
    public static final int EDGE_LEFT = 4;
    public static final int EDGE_RIGHT = 8;
    public static final int EDGE_TOP = 1;

    public static final int FLAG_WINDOW_IS_OBSCURED = 1;
    public static final int FLAG_WINDOW_IS_PARTIALLY_OBSCURED = 2;

    public static final int INVALID_POINTER_ID = -1;

    public static final int TOOL_TYPE_ERASER = 4;
    public static final int TOOL_TYPE_FINGER = 1;
    public static final int TOOL_TYPE_MOUSE = 3;
    public static final int TOOL_TYPE_STYLUS = 2;
    public static final int TOOL_TYPE_UNKNOWN = 0;

    //
    // recycle system
    //
    private static final int MAX_RECYCLE = 10;
    private static final Object recycleLock = new Object();
    private static int recycleCount;
    private static MotionEvent recycleList;
    private static final int MIN_POINTER_ARRAY_SIZE = 5;
    private static final int MAX_POINTER_RECYCLE = MAX_RECYCLE * MIN_POINTER_ARRAY_SIZE;
    private static PointerNode pointerRecycleList;
    private static int pointerRecycleCount;
    private MotionEvent nextRecycled;
    private boolean recycled;

    //
    // overhead values
    //
    private long downTime;
    private long eventTime;
    private int deviceId;
    private int source;
    private int action;
    private int metaState;
    private int buttonState;
    private float xPrecision;
    private float yPrecision;
    private int edgeFlags;
    private int flags;

    //
    // pointers
    //
    private static class PointerNode {
        PointerNode nextRecycled;
        PointerCoords pointerCoords;
        PointerProperties properties;

        PointerNode() {
            pointerCoords = new PointerCoords();
            properties = new PointerProperties();
        }

        public void copyFrom(PointerCoords coords, PointerProperties properties) {
            pointerCoords.copyFrom(coords);
            properties.copyFrom(properties);
        }

        public void clear() {
            pointerCoords.clear();
            properties.clear();
        }
    }

    private ArrayList<PointerNode> pointers;

    private MotionEvent() { }

    private void initOverheadValues(long downTime, long eventTime, int action,
                                    int metaState, int buttonState, float xPrecision, float yPrecision,
                                    int deviceId, int edgeFlags, int source, int flags) {
        this.downTime = downTime;
        this.eventTime = eventTime;
        this.action = action;
        this.metaState = metaState;
        this.buttonState = buttonState;
        this.xPrecision = xPrecision;
        this.yPrecision = yPrecision;
        this.deviceId = deviceId;
        this.edgeFlags = edgeFlags;
        this.source = source;
        this.flags = flags;
    }

    /**
     * Create an event with multiple pointers
     *
     * @param downTime time (in milliseconds) when the user originally pressed down to start
     * a stream of position events.
     * @param eventTime The the time (in milliseconds) when this specific event was generated.
     * @param action the action ACTION_ value
     * @param pointerCount number of pointers
     * @param pointerProperties pointer property values
     * @param pointerCoords pointer coordinate values
     * @param metaState
     * @param buttonState
     * @param xPrecision
     * @param yPrecision
     * @param deviceId
     * @param edgeFlags
     * @param source
     * @param flags
     *
     * @return the newly created event
     */
    public static MotionEvent obtain(long downTime, long eventTime, int action, int pointerCount,
                                                           PointerProperties[] pointerProperties, PointerCoords[] pointerCoords,
                                                           int metaState, int buttonState, float xPrecision, float yPrecision,
                                                           int deviceId, int edgeFlags, int source, int flags) {

        MotionEvent event = obtain(pointerCount);
        event.initOverheadValues(downTime, eventTime, action, metaState, buttonState, xPrecision, yPrecision,
                deviceId, edgeFlags, source, flags);

        // let the bounds exception of null exception happen if arguments are wrong
        for (int i = 0; i < pointerCount; ++i) {
            event.pointers.get(i).copyFrom(pointerCoords[i], pointerProperties[i]);
        }

        return event;
    }

    /**
     * Create a single pointer event
     *
     * @param downTime time (in milliseconds) when the user originally pressed down to start
     * a stream of position events.
     * @param eventTime The the time (in milliseconds) when this specific event was generated.
     * @param action the action ACTION_ value
     * @param x
     * @param y
     * @param pressure
     * @param size
     * @param metaState
     * @param xPrecision
     * @param yPrecision
     * @param deviceId
     * @param edgeFlags
     *
     * @return the newly created event
     */
    public static MotionEvent obtain(long downTime, long eventTime, int action, float x, float y,
                                                           float pressure, float size, int metaState, float xPrecision,
                                                           float yPrecision, int deviceId, int edgeFlags) {

        MotionEvent event = obtain(1);
        event.initOverheadValues(downTime, eventTime, action, metaState, 0, xPrecision, yPrecision,
                deviceId, edgeFlags, 0, 0);

        PointerCoords pointerCoords = event.pointers.get(0).pointerCoords;
        pointerCoords.x = x;
        pointerCoords.y = y;
        pointerCoords.pressure = pressure;
        pointerCoords.size = size;

        return event;
    }

    /**
     * Create a single pointer event
     *
     * @param downTime the down time
     * @param eventTime the event time
     * @param action the action ACTION_ value
     * @param x x position
     * @param y y position
     * @param metaState
     *
     * @return the newly created event
     */
    public static MotionEvent obtain(long downTime, long eventTime, int action, float x, float y, int metaState) {
        return obtain(downTime, eventTime, action, x, y,
                1.0f, 1.0f, metaState, 1.0f, 1.0f, 0, 0);
    }

    /**
     * Create an event that is a copy of another event
     *
     * @param other the even to copy
     *
     * @return the newly created event
     */
    public static MotionEvent obtain(MotionEvent other) {
        MotionEvent event = obtain(other.getPointerCount());
        event.initOverheadValues(other.downTime, other.eventTime, other.action, other.metaState, other.buttonState,
                other.xPrecision, other.yPrecision, other.deviceId, other.edgeFlags, other.source, other.flags);

        for (int i = 0; i < other.getPointerCount(); ++i) {
            event.pointers.get(i).copyFrom(other.pointers.get(i).pointerCoords,
                    other.pointers.get(i).properties);
        }

        return event;
    }

    private static MotionEvent obtain(int pointerCount) {

        MotionEvent result = null;
        ArrayList<PointerNode> pointers = null;

        synchronized (recycleLock) {
            if (recycleCount > 0) {
                result = recycleList;
                recycleList = recycleList.nextRecycled;
                --recycleCount;
                result.recycled = false;
                result.nextRecycled = null;
                pointers = result.pointers;
            }

            if (pointerRecycleCount > 0) {

                // make sure we have pointers array
                if (pointers == null)
                    pointers = new ArrayList<>(MIN_POINTER_ARRAY_SIZE);

                // just to be safe
                pointers.clear();

                for (int count = pointerCount; count > 0 && pointerRecycleCount > 0; --count) {
                    pointers.add(pointerRecycleList);
                    pointerRecycleList = pointerRecycleList.nextRecycled;
                    --pointerRecycleCount;
                }
            }
        }
        if (result == null)
            result = new MotionEvent();

        if (pointers == null)
            pointers = new ArrayList<>(MIN_POINTER_ARRAY_SIZE);

        for (int count = pointerCount - pointers.size(); count > 0; --count) {
            pointers.add(new PointerNode());
        }
        result.pointers = pointers;
        return result;
    }

    /**
     * Recycle this event for future use
     */
    public void recycle() {
        synchronized (recycleLock) {

            if (this.recycled)
                throw new IllegalStateException("Already recycled");

            // recycle/release any pointers
            if (pointers != null) {
                for (int i = 0; i < pointers.size(); ++i) {
                    PointerNode pointer = pointers.get(i);
                    if (pointer != null && pointerRecycleCount < MAX_POINTER_RECYCLE) {
                        pointer.nextRecycled = pointerRecycleList;
                        pointer.clear();
                        pointerRecycleList = pointer;
                        ++pointerRecycleCount;
                    }
                }
                pointers.clear();
            }

            // recycle event
            if (recycleCount < MAX_RECYCLE) {
                recycled = true;
                nextRecycled = recycleList;
                recycleList = this;
                ++recycleCount;
            }
        }
    }

    /**
     * The device id the event is associated with
     *
     * @return
     */
    public int getDeviceId() {
        return deviceId;
    }

    /**
     * Get the source input device (currently always 0, unknown)
     *
     * @return
     */
    public int getSource() {
        return source;
    }

    /**
     * Set the input source
     *
     * @param source
     */
    public void setSource(int source) {
        this.source = source;
    }

    /**
     * Get the full action bitfield
     *
     * @return
     */
    public int getAction() {
        return this.action;
    }

    /**
     * Get the masked ACTION_* value from the action bitfield
     *
     * @return
     */
    public int getActionMasked() {
        return this.action & ACTION_MASK;
    }

    /**
     * Get the pointer index associated with the action
     *
     * @return
     */
    public int getActionIndex() {
        return (this.action & ACTION_POINTER_INDEX_MASK)
                >> ACTION_POINTER_INDEX_SHIFT;
    }

    /**
     * Get flags
     *
     * @return
     */
    public int getFlags() {
        return this.flags;
    }

    /**
     * Get the press gesture down time
     *
     * @return
     */
    public long getDownTime() {
        return downTime;
    }

    /**
     * Get the time of the event
     *
     * @return
     */
    public long getEventTime() {
        return eventTime;
    }

    /**
     * Get the pointer x position
     *
     * @return
     */
    public float getX() {
        return getX(0);
    }

    /**
     * Get the pointer y position
     *
     * @return
     */
    public float getY() {
        return getY(0);
    }

    /**
     * Get the pointer pressure
     *
     * @return
     */
    public float getPressure() {
        return getPressure(0);
    }

    /**
     * Get the pointer size
     *
     * @return
     */
    public float getSize() {
        return getSize(0);
    }

    /**
     *
     * @return
     */
    public float getTouchMajor() {
        return getTouchMajor(0);
    }

    /**
     *
     * @return
     */
    public float getTouchMinor() {
        return getTouchMinor(0);
    }

    /**
     *
     * @return
     */
    public float getToolMajor() {
        return getToolMajor(0);
    }

    /**
     *
     * @return
     */
    public float getToolMinor() {
        return getToolMinor(0);
    }

    /**
     *
     * @return
     */
    public float getOrientation() {
        return getOrientation(0);
    }

    /**
     * Get a particular pointer axis value
     *
     * @param axis
     * @return
     */
    public float getAxisValue(int axis) {
        return getAxisValue(axis, 0);
    }

    /**
     * Get the number of pointers in the event
     *
     * @return
     */
    public int getPointerCount() {
        return pointers.size();
    }

    /**
     * Get the pointer id at a pointer index
     *
     * @param pointerIndex
     * @return
     */
    public int getPointerId(int pointerIndex) {
        return pointers.get(pointerIndex).properties.id;
    }

    /**
     *
     * @param pointerIndex
     * @return
     */
    public int getToolType(int pointerIndex) {
        return pointers.get(pointerIndex).properties.toolType;
    }

    /**
     *
     * @param pointerId
     * @return
     */
    public int findPointerIndex(int pointerId) {
        for (int i = 0; i < getPointerCount(); ++i)
            if (getPointerId(i) == pointerId)
                return i;
        return -1;
    }

    /**
     *
     * @param pointerIndex
     * @return
     */
    public float getX(int pointerIndex) {
        return pointers.get(pointerIndex).pointerCoords.x;
    }

    /**
     *
     * @param pointerIndex
     * @return
     */
    public float getY(int pointerIndex) {
        return pointers.get(pointerIndex).pointerCoords.y;
    }

    /**
     *
     * @param pointerIndex
     * @return
     */
    public float getPressure(int pointerIndex) {
        return pointers.get(pointerIndex).pointerCoords.pressure;
    }

    /**
     *
     * @param pointerIndex
     * @return
     */
    public float getSize(int pointerIndex) {
        return pointers.get(pointerIndex).pointerCoords.size;
    }

    /**
     *
     * @param pointerIndex
     * @return
     */
    public float getTouchMajor(int pointerIndex) {
        return pointers.get(pointerIndex).pointerCoords.touchMajor;
    }

    /**
     *
     * @param pointerIndex
     * @return
     */
    public float getTouchMinor(int pointerIndex) {
        return pointers.get(pointerIndex).pointerCoords.touchMinor;
    }

    /**
     *
     * @param pointerIndex
     * @return
     */
    public float getToolMajor(int pointerIndex) {
        return pointers.get(pointerIndex).pointerCoords.toolMajor;
    }

    /**
     *
     * @param pointerIndex
     * @return
     */
    public float getToolMinor(int pointerIndex) {
        return pointers.get(pointerIndex).pointerCoords.toolMinor;
    }

    /**
     *
     * @param pointerIndex
     * @return
     */
    public float getOrientation(int pointerIndex) {
        return pointers.get(pointerIndex).pointerCoords.orientation;
    }

    /**
     *
     * @param axis
     * @param pointerIndex
     * @return
     */
    public float getAxisValue(int axis, int pointerIndex) {
        return pointers.get(pointerIndex).pointerCoords.getAxisValue(axis);
    }

    /**
     *
     * @param pointerIndex
     * @param outPointerCoords
     */
    public void getPointerCoords(int pointerIndex, PointerCoords outPointerCoords) {
        outPointerCoords.copyFrom(pointers.get(pointerIndex).pointerCoords);
    }

    /**
     *
     * @param pointerIndex
     * @param outPointerProperties
     */
    public void getPointerProperties(int pointerIndex, PointerProperties outPointerProperties) {
        outPointerProperties.copyFrom(pointers.get(pointerIndex).properties);
    }

    /**
     *
     * @return
     */
    public int getMetaState() {
        return metaState;
    }

    /**
     *
     * @return
     */
    public int getButtonState() {
        return buttonState;
    }

    /**
     *
     * @return
     */
    public float getXPrecision() {
        return xPrecision;
    }

    /**
     *
     * @return
     */
    public float getYPrecision() {
        return yPrecision;
    }

    /**
     *
     * @return
     */
    public int getEdgeFlags() {
        return edgeFlags;
    }

    /**
     *
     * @param flags
     */
    public void setEdgeFlags(int flags) {
        this.edgeFlags = flags;
    }

    /**
     *
     * @param action
     */
    public void setAction(int action) {
        this.action = action;
    }

    @Override
    public String toString() {
        return "MotionEvent{" + Integer.toHexString(System.identityHashCode(this))
                + " pointerId=" + getPointerId(0)
                + " action=" + actionToString(getAction())
                + " x=" + getX()
                + " y=" + getY()
                + " pressure=" + getPressure()
                + " size=" + getSize()
                + " touchMajor=" + getTouchMajor()
                + " touchMinor=" + getTouchMinor()
                + " toolMajor=" + getToolMajor()
                + " toolMinor=" + getToolMinor()
                + " orientation=" + getOrientation()
                + " meta=0x" + Integer.toHexString(getMetaState())
                + " pointerCount=" + getPointerCount()
                + " flags=0x" + Integer.toHexString(getFlags())
                + " edgeFlags=0x" + Integer.toHexString(getEdgeFlags())
                + " device=" + getDeviceId()
                + " source=0x" + Integer.toHexString(getSource())
                + "}";
    }

    /**
     *
     * @param action
     * @return
     */
    public static String actionToString(int action) {
        switch (action) {
            case ACTION_DOWN: return "ACTION_DOWN";
            case ACTION_UP: return "ACTION_UP";
            case ACTION_CANCEL: return "ACTION_CANCEL";
            case ACTION_OUTSIDE: return "ACTION_OUTSIDE";
            case ACTION_MOVE: return "ACTION_MOVE";
            case ACTION_HOVER_MOVE: return "ACTION_HOVER_MOVE";
            case ACTION_SCROLL: return "ACTION_SCROLL";
            case ACTION_HOVER_ENTER: return "ACTION_HOVER_ENTER";
            case ACTION_HOVER_EXIT: return "ACTION_HOVER_EXIT";
        }

        int index = (action & ACTION_POINTER_INDEX_MASK) >> ACTION_POINTER_INDEX_SHIFT;
        switch (action & ACTION_MASK) {
            case ACTION_POINTER_DOWN: return "ACTION_POINTER_DOWN(" + index + ")";
            case ACTION_POINTER_UP: return "ACTION_POINTER_UP(" + index + ")";
            default: return Integer.toString(action);
        }
    }

    /**
     *
     * @param axis
     * @return
     */
    public static String axisToString(int axis) {
        switch (axis) {
            case AXIS_X: return "AXIS_X";
            case AXIS_Y: return "AXIS_Y";
            case AXIS_Z: return "AXIS_Z";
            case AXIS_PRESSURE: return "AXIS_PRESSURE";
            case AXIS_SIZE: return "AXIS_SIZE";
            case AXIS_TOUCH_MAJOR: return "AXIS_TOUCH_MAJOR";
            case AXIS_TOUCH_MINOR: return "AXIS_TOUCH_MINOR";
            case AXIS_TOOL_MAJOR: return "AXIS_TOOL_MAJOR";
            case AXIS_TOOL_MINOR: return "AXIS_TOOL_MINOR";
            case AXIS_ORIENTATION: return "AXIS_ORIENTATION";
            case AXIS_BRAKE: return "AXIS_BRAKE";
            case AXIS_DISTANCE: return "AXIS_DISTANCE";
            case AXIS_GAS: return "AXIS_GAS";
            case AXIS_GENERIC_1: return "AXIS_GENERIC_1";
            case AXIS_GENERIC_10: return "AXIS_GENERIC_10";
            case AXIS_GENERIC_11: return "AXIS_GENERIC_11";
            case AXIS_GENERIC_12: return "AXIS_GENERIC_12";
            case AXIS_GENERIC_13: return "AXIS_GENERIC_13";
            case AXIS_GENERIC_14: return "AXIS_GENERIC_14";
            case AXIS_GENERIC_15: return "AXIS_GENERIC_15";
            case AXIS_GENERIC_16: return "AXIS_GENERIC_16";
            case AXIS_GENERIC_2: return "AXIS_GENERIC_2";
            case AXIS_GENERIC_3: return "AXIS_GENERIC_3";
            case AXIS_GENERIC_4: return "AXIS_GENERIC_4";
            case AXIS_GENERIC_5: return "AXIS_GENERIC_5";
            case AXIS_GENERIC_6: return "AXIS_GENERIC_6";
            case AXIS_GENERIC_7: return "AXIS_GENERIC_7";
            case AXIS_GENERIC_8: return "AXIS_GENERIC_8";
            case AXIS_GENERIC_9: return "AXIS_GENERIC_9";
            case AXIS_HAT_X: return "AXIS_HAT_X";
            case AXIS_HAT_Y: return "AXIS_HAT_Y";
            case AXIS_HSCROLL: return "AXIS_HSCROLL";
            case AXIS_LTRIGGER: return "AXIS_LTRIGGER";
            case AXIS_RELATIVE_X: return "AXIS_RELATIVE_X";
            case AXIS_RELATIVE_Y: return "AXIS_RELATIVE_Y";
            case AXIS_RTRIGGER: return "AXIS_RTRIGGER";
            case AXIS_RUDDER: return "AXIS_RUDDER";
            case AXIS_RX: return "AXIS_RX";
            case AXIS_RY: return "AXIS_RY";
            case AXIS_RZ: return "AXIS_RZ";
            case AXIS_SCROLL: return "AXIS_SCROLL";
            case AXIS_THROTTLE: return "AXIS_THROTTLE";
            case AXIS_TILT: return "AXIS_TILT";
            case AXIS_VSCROLL: return "AXIS_VSCROLL";
            case AXIS_WHEEL: return "AXIS_WHEEL";
            default: {
                if (axis < 0 || axis > 63) {
                    throw new IllegalArgumentException("Axis out of range.");
                }
                return Integer.toString(axis);
            }
        }
    }

    /**
     *
     * @param button
     * @return
     */
    public boolean isButtonPressed(int button) {
        return ((buttonState >> button) & 1) != 0;
    }

    /**
     *
     */
    public static final class PointerProperties {
        /**
         * The pointer id
         */
        public int id;

        /**
         * The pointer tool-type
         */
        public int toolType;

        /**
         *
         */
        public PointerProperties() { }

        /**
         *
         * @param other
         */
        public PointerProperties(PointerProperties other) {
            id = other.id;
            toolType = other.toolType;
        }

        /**
         * Clear the pointer properties
         */
        public void clear() {
            id = 0;
            toolType = 0;
        }

        /**
         * Copy properties from other properties
         *
         * @param other
         */
        public void copyFrom(PointerProperties other) {
            id = other.id;
            toolType = other.toolType;
        }

        /**
         * Test equality
         *
         * @param other
         * @return
         */
        public boolean equals(Object other) {
            if (!(other instanceof PointerProperties))
                return false;
            return id == ((PointerProperties)other).id &&
                    toolType == ((PointerProperties)other).toolType;
        }

        /**
         * Get the hash code of the properties
         *
         * @return
         */
        public int hashCode() {
            return Integer.hashCode(id);
        }
    }

    /**
     * Pointer coordinate axis information
     */
    public static final class PointerCoords {
        //
        // next 3 lines pulled from Android impl
        //
        private static final int INITIAL_PACKED_AXIS_VALUES = 8;
        private long mPackedAxisBits;
        private float[] mPackedAxisValues;

        /**
         *
         */
        public float orientation;

        /**
         *
         */
        public float pressure;

        /**
         *
         */
        public float size;

        /**
         *
         */
        public float toolMajor;

        /**
         *
         */
        public float toolMinor;

        /**
         *
         */
        public float touchMajor;

        /**
         *
         */
        public float touchMinor;

        /**
         *
         */
        public float x;

        /**
         *
         */
        public float y;

        /**
         *
         */
        public PointerCoords() { }

        /**
         *
         * @param other
         */
        public PointerCoords(PointerCoords other) {
            orientation = other.orientation;
            pressure = other.pressure;
            size = other.size;
            toolMajor = other.toolMajor;
            toolMinor = other.toolMinor;
            touchMajor = other.touchMajor;
            touchMinor = other.touchMinor;
            x = other.x;
            y = other.y;
            if (other.mPackedAxisValues != null)
                mPackedAxisValues = Arrays.copyOf(other.mPackedAxisValues, other.mPackedAxisValues.length);
            mPackedAxisBits = other.mPackedAxisBits;
        }

        /**
         *
         */
        public void clear() {
            orientation = 0;
            pressure = 0;
            size = 0;
            toolMajor = 0;
            toolMinor = 0;
            touchMajor = 0;
            touchMinor = 0;
            x = 0;
            y = 0;
            mPackedAxisBits = 0;
            mPackedAxisValues = null;
        }

        /**
         *
         * @param other
         */
        public void copyFrom(PointerCoords other) {
            orientation = other.orientation;
            pressure = other.pressure;
            size = other.size;
            toolMajor = other.toolMajor;
            toolMinor = other.toolMinor;
            touchMajor = other.touchMajor;
            touchMinor = other.touchMinor;
            x = other.x;
            y = other.y;
            if (other.mPackedAxisValues != null)
                mPackedAxisValues = Arrays.copyOf(other.mPackedAxisValues, other.mPackedAxisValues.length);
            mPackedAxisBits = other.mPackedAxisBits;
        }

        public float getAxisValue(int axis) {
            switch (axis) {
                case AXIS_X: return x;
                case AXIS_Y: return y;
                case AXIS_PRESSURE: return pressure;
                case AXIS_SIZE: return size;
                case AXIS_TOUCH_MAJOR: return touchMajor;
                case AXIS_TOUCH_MINOR: return touchMinor;
                case AXIS_TOOL_MAJOR: return toolMajor;
                case AXIS_TOOL_MINOR: return toolMinor;
                case AXIS_ORIENTATION: return orientation;
                default: {
                    //
                    // this scope pulled from Android impl
                    //
                    if (axis < 0 || axis > 63) {
                        throw new IllegalArgumentException("Axis out of range.");
                    }
                    final long bits = mPackedAxisBits;
                    final long axisBit = 1L << axis;
                    if ((bits & axisBit) == 0) {
                        return 0;
                    }
                    final int index = Long.bitCount(bits & (axisBit - 1L));
                    return mPackedAxisValues[index];
                }
            }
        }

        public void setAxisValue(int axis, float value) {
            switch (axis) {
                case AXIS_X: x = value; break;
                case AXIS_Y: y = value; break;
                case AXIS_PRESSURE: pressure = value; break;
                case AXIS_SIZE: size = value; break;
                case AXIS_TOUCH_MAJOR: touchMajor = value; break;
                case AXIS_TOUCH_MINOR: touchMinor = value; break;
                case AXIS_TOOL_MAJOR: toolMajor = value; break;
                case AXIS_TOOL_MINOR: toolMinor = value; break;
                case AXIS_ORIENTATION: orientation = value; break;
                default: {
                    //
                    // this scope pulled from Android impl
                    //
                    if (axis < 0 || axis > 63) {
                        throw new IllegalArgumentException("Axis out of range.");
                    }
                    final long bits = mPackedAxisBits;
                    final long axisBit = 1L << axis;
                    final int index = Long.bitCount(bits & (axisBit - 1L));
                    float[] values = mPackedAxisValues;
                    if ((bits & axisBit) == 0) {
                        if (values == null) {
                            values = new float[INITIAL_PACKED_AXIS_VALUES];
                            mPackedAxisValues = values;
                        } else {
                            final int count = Long.bitCount(bits);
                            if (count < values.length) {
                                if (index != count) {
                                    System.arraycopy(values, index, values, index + 1,
                                            count - index);
                                }
                            } else {
                                float[] newValues = new float[count * 2];
                                System.arraycopy(values, 0, newValues, 0, index);
                                System.arraycopy(values, index, newValues, index + 1,
                                        count - index);
                                values = newValues;
                                mPackedAxisValues = values;
                            }
                        }
                        mPackedAxisBits = bits | axisBit;
                    }
                    values[index] = value;
                }
            }
        }
    }
}
