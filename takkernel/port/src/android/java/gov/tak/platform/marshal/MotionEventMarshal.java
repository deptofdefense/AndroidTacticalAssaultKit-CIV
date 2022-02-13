package gov.tak.platform.marshal;

import gov.tak.api.marshal.IMarshal;
import gov.tak.platform.ui.KeyEvent;
import gov.tak.platform.ui.MotionEvent;

/**
 * Marshals {@link android.view.MotionEvent} to {@link gov.tak.platform.ui.MotionEvent}
 */
final class MotionEventMarshal {
    final static int[] PLATFORM_METASTATE_MASKS = new int[]
    {
            android.view.KeyEvent.META_ALT_LEFT_ON,
            android.view.KeyEvent.META_ALT_ON,
            android.view.KeyEvent.META_ALT_RIGHT_ON,
            android.view.KeyEvent.META_CAPS_LOCK_ON,
            android.view.KeyEvent.META_CTRL_LEFT_ON,
            android.view.KeyEvent.META_CTRL_ON,
            android.view.KeyEvent.META_CTRL_RIGHT_ON,
            android.view.KeyEvent.META_FUNCTION_ON,
            android.view.KeyEvent.META_META_LEFT_ON,
            android.view.KeyEvent.META_META_ON,
            android.view.KeyEvent.META_META_RIGHT_ON,
            android.view.KeyEvent.META_NUM_LOCK_ON,
            android.view.KeyEvent.META_SCROLL_LOCK_ON,
            android.view.KeyEvent.META_SHIFT_LEFT_ON,
            android.view.KeyEvent.META_SHIFT_ON,
            android.view.KeyEvent.META_SHIFT_RIGHT_ON,
            android.view.KeyEvent.META_SYM_ON,
    };
    final static int[] PORTABLE_METASTATE_MASKS = new int[]
    {
            KeyEvent.META_ALT_LEFT_ON,
            KeyEvent.META_ALT_ON,
            KeyEvent.META_ALT_RIGHT_ON,
            KeyEvent.META_CAPS_LOCK_ON,
            KeyEvent.META_CTRL_LEFT_ON,
            KeyEvent.META_CTRL_ON,
            KeyEvent.META_CTRL_RIGHT_ON,
            KeyEvent.META_FUNCTION_ON,
            KeyEvent.META_META_LEFT_ON,
            KeyEvent.META_META_ON,
            KeyEvent.META_META_RIGHT_ON,
            KeyEvent.META_NUM_LOCK_ON,
            KeyEvent.META_SCROLL_LOCK_ON,
            KeyEvent.META_SHIFT_LEFT_ON,
            KeyEvent.META_SHIFT_ON,
            KeyEvent.META_SHIFT_RIGHT_ON,
            KeyEvent.META_SYM_ON,
    };

    final static int[] PLATFORM_BUTTONSTATE_MASKS = new int[]
    {
            android.view.MotionEvent.BUTTON_FORWARD,
            android.view.MotionEvent.BUTTON_BACK,
            android.view.MotionEvent.BUTTON_PRIMARY,
            android.view.MotionEvent.BUTTON_SECONDARY,
            android.view.MotionEvent.BUTTON_STYLUS_PRIMARY,
            android.view.MotionEvent.BUTTON_STYLUS_SECONDARY,
            android.view.MotionEvent.BUTTON_TERTIARY,
    };
    final static int[] PORTABLE_BUTTONSTATE_MASKS = new int[]
    {
            MotionEvent.BUTTON_FORWARD,
            MotionEvent.BUTTON_BACK,
            MotionEvent.BUTTON_PRIMARY,
            MotionEvent.BUTTON_SECONDARY,
            MotionEvent.BUTTON_STYLUS_PRIMARY,
            MotionEvent.BUTTON_STYLUS_SECONDARY,
            MotionEvent.BUTTON_TERTIARY,
    };

    final static int[] PLATFORM_EDGESTATE_FLAG_MASK = new int[]
    {
        android.view.MotionEvent.EDGE_BOTTOM,
        android.view.MotionEvent.EDGE_RIGHT,
        android.view.MotionEvent.EDGE_TOP,
        android.view.MotionEvent.EDGE_LEFT,
    };
    final static int[] PORTABLE_EDGESTATE_FLAG_MASK = new int[]
    {
            MotionEvent.EDGE_BOTTOM,
            MotionEvent.EDGE_RIGHT,
            MotionEvent.EDGE_TOP,
            MotionEvent.EDGE_LEFT,
    };

    final static int[] PLATFORM_FLAGS_MASK = new int[]
    {
            android.view.MotionEvent.FLAG_WINDOW_IS_OBSCURED,
            android.view.MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED,
    };
    final static int[] PORTABLE_FLAGS_MASK = new int[]
    {
            MotionEvent.FLAG_WINDOW_IS_OBSCURED,
            MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED,
    };

    /**
     * Marshals from <I>platform</I> {@link android.view.MotionEvent} to <I>portable</I>
     * {@link gov.tak.platform.ui.MotionEvent}
     */
    final static class Portable implements IMarshal {
        @Override
        public <T, V> T marshal(V inOpaque) {
            if (inOpaque == null) return null;
            final android.view.MotionEvent in = (android.view.MotionEvent) inOpaque;
            return (T) MotionEvent.obtain(
                    in.getDownTime(),
                    in.getEventTime(),
                    marshalAction_platform2portable(in.getAction()),
                    in.getPointerCount(),
                    marshalPointerProperties_platform2portable(in),
                    marshalPointerCoords_platform2portable(in),
                    marshalMaskImpl(in.getMetaState(), PLATFORM_METASTATE_MASKS, PORTABLE_METASTATE_MASKS),
                    marshalMaskImpl(in.getButtonState(), PLATFORM_BUTTONSTATE_MASKS, PORTABLE_BUTTONSTATE_MASKS),
                    in.getXPrecision(),
                    in.getYPrecision(),
                    in.getDeviceId(),
                    marshalMaskImpl(in.getEdgeFlags(), PLATFORM_EDGESTATE_FLAG_MASK, PORTABLE_EDGESTATE_FLAG_MASK),
                    marshalSource(in.getSource()),
                    marshalMaskImpl(in.getFlags(), PLATFORM_FLAGS_MASK, PORTABLE_FLAGS_MASK)
            );
        }
    }

    /**
     * Marshals from <I>portable</I> {@link gov.tak.platform.ui.MotionEvent} to <I>platform</I>
     * {@link android.view.MotionEvent} to
     */
    final static class Platform implements IMarshal {
        @Override
        public <T, V> T marshal(V inOpaque) {
            if (inOpaque == null) return null;
            final MotionEvent in = (MotionEvent) inOpaque;
            return (T) android.view.MotionEvent.obtain(
                    in.getDownTime(),
                    in.getEventTime(),
                    marshalAction_portable2platform(in.getAction()),
                    in.getPointerCount(),
                    marshalPointerProperties_portable2platform(in),
                    marshalPointerCoords_portable2platform(in),
                    marshalMaskImpl(in.getMetaState(), PORTABLE_METASTATE_MASKS, PLATFORM_METASTATE_MASKS),
                    marshalMaskImpl(in.getButtonState(), PORTABLE_BUTTONSTATE_MASKS, PLATFORM_BUTTONSTATE_MASKS),
                    in.getXPrecision(),
                    in.getYPrecision(),
                    in.getDeviceId(),
                    marshalMaskImpl(in.getEdgeFlags(), PORTABLE_EDGESTATE_FLAG_MASK, PLATFORM_EDGESTATE_FLAG_MASK),
                    marshalSource(in.getSource()),
                    marshalMaskImpl(in.getFlags(), PORTABLE_FLAGS_MASK, PLATFORM_FLAGS_MASK)
            );
        }
    }

    static int marshalAction_platform2portable(int inAction) {
        switch(inAction&android.view.MotionEvent.ACTION_MASK) {
            case android.view.MotionEvent.ACTION_DOWN:
                return MotionEvent.ACTION_DOWN;
            case android.view.MotionEvent.ACTION_BUTTON_PRESS:
                return MotionEvent.ACTION_BUTTON_PRESS;
            case android.view.MotionEvent.ACTION_BUTTON_RELEASE:
                return MotionEvent.ACTION_BUTTON_RELEASE;
            case android.view.MotionEvent.ACTION_CANCEL:
                return MotionEvent.ACTION_CANCEL;
            case android.view.MotionEvent.ACTION_HOVER_ENTER:
                return MotionEvent.ACTION_HOVER_ENTER;
            case android.view.MotionEvent.ACTION_HOVER_EXIT:
                return MotionEvent.ACTION_HOVER_EXIT;
            case android.view.MotionEvent.ACTION_HOVER_MOVE:
                return MotionEvent.ACTION_HOVER_MOVE;
            case android.view.MotionEvent.ACTION_MOVE:
                return MotionEvent.ACTION_MOVE;
            case android.view.MotionEvent.ACTION_OUTSIDE:
                return MotionEvent.ACTION_OUTSIDE;
            case android.view.MotionEvent.ACTION_POINTER_DOWN:
                return MotionEvent.ACTION_POINTER_DOWN;
            case android.view.MotionEvent.ACTION_POINTER_UP:
                return MotionEvent.ACTION_POINTER_UP;
            case android.view.MotionEvent.ACTION_SCROLL:
                return MotionEvent.ACTION_SCROLL;
            case android.view.MotionEvent.ACTION_UP:
                return MotionEvent.ACTION_UP;
        }
        return MotionEvent.ACTION_CANCEL;
    }

    static int marshalAction_portable2platform(int inAction) {
        switch(inAction&MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                return android.view.MotionEvent.ACTION_DOWN;
            case MotionEvent.ACTION_BUTTON_PRESS:
                return android.view.MotionEvent.ACTION_BUTTON_PRESS;
            case MotionEvent.ACTION_BUTTON_RELEASE:
                return android.view.MotionEvent.ACTION_BUTTON_RELEASE;
            case MotionEvent.ACTION_CANCEL:
                return android.view.MotionEvent.ACTION_CANCEL;
            case MotionEvent.ACTION_HOVER_ENTER:
                return android.view.MotionEvent.ACTION_HOVER_ENTER;
            case MotionEvent.ACTION_HOVER_EXIT:
                return android.view.MotionEvent.ACTION_HOVER_EXIT;
            case MotionEvent.ACTION_HOVER_MOVE:
                return android.view.MotionEvent.ACTION_HOVER_MOVE;
            case MotionEvent.ACTION_MOVE:
                return android.view.MotionEvent.ACTION_MOVE;
            case MotionEvent.ACTION_OUTSIDE:
                return android.view.MotionEvent.ACTION_OUTSIDE;
            case MotionEvent.ACTION_POINTER_DOWN:
                return android.view.MotionEvent.ACTION_POINTER_DOWN;
            case MotionEvent.ACTION_POINTER_UP:
                return android.view.MotionEvent.ACTION_POINTER_UP;
            case MotionEvent.ACTION_SCROLL:
                return android.view.MotionEvent.ACTION_SCROLL;
            case MotionEvent.ACTION_UP:
                return android.view.MotionEvent.ACTION_UP;
        }
        return android.view.MotionEvent.ACTION_CANCEL;
    }

    static int marshalSource(int inSource) {
        // XXX - sources not yet defined in Portability API
        return 0;
    }

    static int marshalMaskImpl(int value, int[] inMasks, int[] outMasks) {
        int result = 0;
        for(int i = 0; i < inMasks.length; i++) {
            if((value&inMasks[i]) == inMasks[i]) result |= outMasks[i];
        }
        return result;
    }

    static int marshalToolType_platform2portable(int inType) {
        switch(inType) {
            case android.view.MotionEvent.TOOL_TYPE_ERASER:
                return MotionEvent.TOOL_TYPE_ERASER;
            case android.view.MotionEvent.TOOL_TYPE_FINGER:
                return MotionEvent.TOOL_TYPE_FINGER;
            case android.view.MotionEvent.TOOL_TYPE_MOUSE:
                return MotionEvent.TOOL_TYPE_MOUSE;
            case android.view.MotionEvent.TOOL_TYPE_STYLUS:
                return MotionEvent.TOOL_TYPE_STYLUS;
            case android.view.MotionEvent.TOOL_TYPE_UNKNOWN:
            default :
                return MotionEvent.TOOL_TYPE_UNKNOWN;
        }
    }

    static int marshalToolType_portable2platform(int inType) {
        switch(inType) {
            case MotionEvent.TOOL_TYPE_ERASER:
                return android.view.MotionEvent.TOOL_TYPE_ERASER;
            case MotionEvent.TOOL_TYPE_FINGER:
                return android.view.MotionEvent.TOOL_TYPE_FINGER;
            case MotionEvent.TOOL_TYPE_MOUSE:
                return android.view.MotionEvent.TOOL_TYPE_MOUSE;
            case MotionEvent.TOOL_TYPE_STYLUS:
                return android.view.MotionEvent.TOOL_TYPE_STYLUS;
            case MotionEvent.TOOL_TYPE_UNKNOWN:
            default :
                return android.view.MotionEvent.TOOL_TYPE_UNKNOWN;
        }
    }

    static MotionEvent.PointerCoords[] marshalPointerCoords_platform2portable(android.view.MotionEvent in) {
        final int numPointerProperties = in.getPointerCount();
        if(numPointerProperties == 0)   return new MotionEvent.PointerCoords[0];
        final MotionEvent.PointerCoords[] out = new MotionEvent.PointerCoords[numPointerProperties];
        for(int i = 0; i < numPointerProperties; i++) {
            android.view.MotionEvent.PointerCoords inCoords = new android.view.MotionEvent.PointerCoords();
            in.getPointerCoords(i, inCoords);
            out[i] = new MotionEvent.PointerCoords();
            out[i].orientation = inCoords.orientation;
            out[i].pressure = inCoords.pressure;
            out[i].size = inCoords.size;
            out[i].toolMajor = inCoords.toolMajor;
            out[i].toolMinor = inCoords.toolMinor;
            out[i].touchMajor = inCoords.touchMajor;
            out[i].touchMinor = inCoords.touchMinor;
            out[i].x = inCoords.x;
            out[i].y = inCoords.y;
        }
        return out;
    }

    static android.view.MotionEvent.PointerCoords[] marshalPointerCoords_portable2platform(MotionEvent in) {
        final int numPointerProperties = in.getPointerCount();
        if(numPointerProperties == 0)   return new android.view.MotionEvent.PointerCoords[0];
        final android.view.MotionEvent.PointerCoords[] out = new android.view.MotionEvent.PointerCoords[numPointerProperties];
        for(int i = 0; i < numPointerProperties; i++) {
            MotionEvent.PointerCoords inCoords = new MotionEvent.PointerCoords();
            in.getPointerCoords(i, inCoords);
            out[i] = new android.view.MotionEvent.PointerCoords();
            out[i].orientation = inCoords.orientation;
            out[i].pressure = inCoords.pressure;
            out[i].size = inCoords.size;
            out[i].toolMajor = inCoords.toolMajor;
            out[i].toolMinor = inCoords.toolMinor;
            out[i].touchMajor = inCoords.touchMajor;
            out[i].touchMinor = inCoords.touchMinor;
            out[i].x = inCoords.x;
            out[i].y = inCoords.y;
        }
        return out;
    }

    static MotionEvent.PointerProperties[] marshalPointerProperties_platform2portable(android.view.MotionEvent in) {
        final int numPointerProperties = in.getPointerCount();
        if(numPointerProperties == 0)   return new MotionEvent.PointerProperties[0];
        final MotionEvent.PointerProperties[] out = new MotionEvent.PointerProperties[numPointerProperties];
        for(int i = 0; i < numPointerProperties; i++) {
            android.view.MotionEvent.PointerProperties inProps = new android.view.MotionEvent.PointerProperties();
            in.getPointerProperties(i, inProps);
            out[i] = new MotionEvent.PointerProperties();
            out[i].id = inProps.id;
            out[i].toolType = marshalToolType_platform2portable(inProps.toolType);
        }
        return out;
    }

    static android.view.MotionEvent.PointerProperties[] marshalPointerProperties_portable2platform(MotionEvent in) {
        final int numPointerProperties = in.getPointerCount();
        if(numPointerProperties == 0)   return new android.view.MotionEvent.PointerProperties[0];
        final android.view.MotionEvent.PointerProperties[] out = new android.view.MotionEvent.PointerProperties[numPointerProperties];
        for(int i = 0; i < numPointerProperties; i++) {
            MotionEvent.PointerProperties inProps = new MotionEvent.PointerProperties();
            in.getPointerProperties(i, inProps);
            out[i] = new android.view.MotionEvent.PointerProperties();
            out[i].id = inProps.id;
            out[i].toolType = marshalToolType_portable2platform(inProps.toolType);
        }
        return out;
    }
}
