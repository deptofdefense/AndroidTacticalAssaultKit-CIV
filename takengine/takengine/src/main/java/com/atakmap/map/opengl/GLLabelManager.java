package com.atakmap.map.opengl;

import com.atakmap.android.maps.MapTextFormat;
import com.atakmap.interop.Interop;
import com.atakmap.map.EngineLibrary;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.Point;
import com.atakmap.math.Rectangle;

public class GLLabelManager {
    private final static int TEXT_ALIGNMENT_LEFT = 0;
    private final static int TEXT_ALIGNMENT_CENTER = 1;
    private final static int TEXT_ALIGNMENT_RIGHT = 2;

    private final static int VERTICAL_ALIGNMENT_TOP = 0;
    private final static int VERTICAL_ALIGNMENT_MIDDLE = 1;
    private final static int VERTICAL_ALIGNMENT_BOTTOM = 2;

    private final static int PRIORITY_HIGH = 0;
    private final static int PRIORITY_STANDARD = 1;
    private final static int PRIORITY_LOW = 2;

    private final static int ALTMODE_CLAMP_TO_GROUND = 0;
    private final static int ALTMODE_RELATIVE = 1;
    private final static int ALTMODE_ABSOLUTE = 2;

    private static final String TAG = GLLabelManager.class.getSimpleName();
    private final static double DEFAULT_MIN_RENDER_SCALE = (1.0d / 100000.0d);
    private final static double ICON_SCALE = 1d;
    public final static int NO_ID = -1;

    public final static int HINT_WEIGHTED_FLOAT = getHintWeightedFloat();
    public final static int HINT_DUPLICATE_ON_SPLIT = getHintDuplicateOnSplit();
    public final static int HINT_AUTO_SURFACE_OFFSET_ADJUST = getHintAutoSurfaceOffsetAdjust();
    public final static int HINT_XRAY = getHintXRay();
    public final static int HINT_SCROLLING_TEXT = getHintScrollingText();

    private long pointer;

    public enum TextAlignment
    {
        Left,
        Center,
        Right
    };

    public enum VerticalAlignment
    {
        Top,
        Middle,
        Bottom
    };

    public enum Priority
    {
        High,
        Standard,
        Low
    }

    static {
        EngineLibrary.initialize();
    }

    final static Interop<Geometry> Geometry_interop = Interop.findInterop(Geometry.class);
    final static Interop<GLMapView> GLMapView_interop = Interop.findInterop(GLMapView.class);

    Object owner;

    GLLabelManager(long ptr, Object ownerRef) {
        pointer = ptr;
        owner = ownerRef;
    }


    /**
     * Sets the alignment for a label.
     * @param id the id of the label.
     * @param alignment the text alignment for the label.
     */
    public void setAlignment(int id, TextAlignment alignment) {
        int align;
        switch(alignment) {
            case Left:
                align = TEXT_ALIGNMENT_LEFT;
                break;
            case Center:
                align = TEXT_ALIGNMENT_CENTER;
                break;
            case Right:
                align = TEXT_ALIGNMENT_RIGHT;
                break;
            default :
                throw new IllegalArgumentException();
        }
        setAlignment(pointer, id, align);
    }

    /**
     * Sets the vertical alignment for a label.
     * @param id the id of the label.
     * @param alignment the text alignment for the label.
     */
    public void setVerticalAlignment(int id, VerticalAlignment alignment) {
        int align;
        switch(alignment) {
            case Top:
                align = VERTICAL_ALIGNMENT_TOP;
                break;
            case Middle:
                align = VERTICAL_ALIGNMENT_MIDDLE;
                break;
            case Bottom:
                align = VERTICAL_ALIGNMENT_BOTTOM;
                break;
            default :
                throw new IllegalArgumentException();
        }
        setVerticalAlignment(pointer, id, align);
    }

    /**
     * Sets the altitude mode for a label.
     * @param id the id of the label.
     * @param altitudeMode the altitude mode
     */
    public void setAltitudeMode(int id, Feature.AltitudeMode altitudeMode) {
        int altmode;
        switch(altitudeMode) {
            case ClampToGround:
                altmode = ALTMODE_CLAMP_TO_GROUND;
                break;
            case Relative:
                altmode = ALTMODE_RELATIVE;
                break;
            case Absolute:
                altmode = ALTMODE_ABSOLUTE;
                break;
            default :
                throw new IllegalArgumentException();
        }
        setAltitudeMode(pointer, id, altmode);
    }

    /**
     * Sets the desired offset for a label.
     * @param id the id of the label.
     * @param desiredOffset the desired offset as described by a vector (x, y, z)
     */
    public void setDesiredOffset(int id, Point desiredOffset) {
        setDesiredOffset(pointer, id, desiredOffset.getX(), desiredOffset.getY(), desiredOffset.getZ());
    }

    /**
     * Sets the geometry for a label.
     * @param id the id of the label.
     * @param geometry the geometry for a specific label
     */
    public void setGeometry(int id, Geometry geometry) {
        setGeometry(pointer, id, Geometry_interop.getPointer(geometry));
    }


    /**
     * Sets the text format for a label.
     * @param id the id of the label.
     * @param format the text format for a specific label
     */
    public void setTextFormat(int id, MapTextFormat format) {
        setTextFormat(id, format.getTypefaceFamilyName(), format.getFontSize(), format.getTypeface().isBold(),
                format.getTypeface().isItalic(), format.isUnderlined(), format.isStrikethrough());
    }

    /**
     * Sets the priority for a label.
     * @param id the id of the label.
     * @param priority the priorty for a specific label
     */
    public void setPriority(int id, Priority priority) {
        int pri;
        switch(priority) {
            case High:
                pri = PRIORITY_HIGH;
                break;
            case Standard:
                pri = PRIORITY_STANDARD;
                break;
            case Low:
                pri = PRIORITY_LOW;
                break;
            default :
                throw new IllegalArgumentException();
        }
        setPriority(pointer, id, pri);
    }

    /**
     * Sets the hints for a label.
     * @param id the id of the label.
     * @param hints the hint mask for a specific label { HINT_* }
     */
    public void setHints(int id, int hints) {
        setHints(pointer, id, hints);
    }

    /**
     * Return the hints for a label
     * @param id the id of the label.
     * @return the currently set hint mask { HINT_* }
     */
    public int getHints(int id) {
        return getHints(pointer, id);
    }

    /**
     * Add an empty label to the label manager
     * @return id the id of the label.
     */
    public int addLabel() {
        return addLabel(null);
    }

    /**
     * Add a label to the label manager with the provide text
     * @param label the text for the label
     * @return id the id of the label.
     */
    public int addLabel(final String label) {
        return addLabel(pointer, label);
    }


    /**
     * Sets the fill state for a label (filled or unfilled).
     * @param id the id of the label.
     * @param fill true if the text will be filled or false if it just an outline
     */
    public void setFill(int id, boolean fill) {
        setFill(pointer, id, fill);
    }

    /**
     * Remove a label from the label manager
     * @param id the id of the label.
     */
    public void removeLabel(int id) {
        removeLabel(pointer, id);
    }

    /**
     * Given an existing label id, set the text
     * @param id the id of the label.
     * @param text the text to replace the existing label with
     */
    public void setText(int id, String text) {
        setText(pointer, id, text);
    }

    /**
     * Sets the desired offset for a label.
     * @param id the id of the label.
     * @param x the desired offset in the x direction
     * @param y the desired offset in the y direction
     * @param z the desired offset in the z direction
     */
    public void setDesiredOffset(int id, double x, double y, double z) {
        setDesiredOffset(pointer, id, x, y, z);
    }

    /**
     * Given an existing label id, set the color of the text
     * @param id the id of the label.
     * @param color the color of the text
     */
    public void setColor(int id, int color) {
        setColor(pointer, id, color);
    }

    /**
     * Given an existing label id, set the background color of the text
     * @param id the id of the label.
     * @param color the background color of the text
     */
    public void setBackgroundColor(int id, int color) {
        setBackColor(pointer, id, color);
    }

    /**
     * Given an existing label id, set the max draw resolution
     * @param id the id of the label.
     * @param maxDrawResolution the max draw resolution
     */
    public void setMaxDrawResolution(int id, double maxDrawResolution)
    {
        setMaxDrawResolution(pointer, id, maxDrawResolution);
    }

    /**
     * Given an existing label id, set the size.   
     * @param id the id of the label.
     * @param sizeRect the preallocated allocated rectangle that is the size of the text fully
     * rendered
     * @return the size of the text
     */
    public Rectangle getSize(int id, Rectangle sizeRect) {
        if (sizeRect == null)
            sizeRect = new Rectangle(0,0,0,0);
        getSize(pointer, id, sizeRect);

        return sizeRect;
    }

    /**
     * Given an existing label id, set the rotation.   
     * @param id the id of the label.
     * @param rotation in degrees
     * @return if the rotation is absolute or relative to the curren rotation of the map
     */
    public void setRotation(int id, float rotation, boolean absolute) {
        setRotation(pointer, id, rotation, absolute);
    }

    /**
     * Given an existing label id, set the full text format.   
     * @param id the id of the label.
     * @param fontName the name of the font
     * @param size the size of the font
     * @param bold the font style will be BOLD if set to true
     * @param italic the font style will be ITALIC if set to true
     * @param underline the font style will be UNDERLINE if set to true
     * @param strikethrough the font style will be STRIKETHROUGH if set to true
     */ 
    public void setTextFormat(int id, String fontName, float size, boolean bold, boolean italic, boolean underline, boolean strikethrough) {
        setTextFormat(pointer, id, fontName, size, bold, italic, underline, strikethrough);
    }

 
    /**
     * Given an existing label id, set the visibilty.   
     * @param id the id of the label.
     * @param visible true if the label is to be visible
     */
    public void setVisible(int id, boolean visible) {
        setVisible(pointer, id, visible);
    }

    public void setVisible(boolean visible)
    {
        setVisible(pointer, visible);
    }

    /*************************************************************************/

    static native void resetFont(long pointer);
    static native void removeLabel(long pointer, int id);
    static native void setVisible(long pointer, int id, boolean visible);
    static native void setAlwaysRender(long pointer, int id, boolean alwaysRender);
    static native void setMaxDrawResolution(long pointer, int id, double maxDrawResolution);
    static native void setColor(long pointer, int id, int color);
    static native void setBackColor(long pointer, int id, int color);
    static native void setFill(long pointer, int id, boolean fill);
    static native void setVisible(long pointer, boolean fill);
    static native void setAlignment(long pointer, int id, int alignment);
    static native void setVerticalAlignment(long pointer, int id, int alignment);
    static native void setText(long pointer, int id, String text);
    static native void setAltitudeMode(long pointer, int id, int altitudeMode);
    static native void setGeometry(long pointer, int id, long geometryPtr);
    static native void setDesiredOffset(long pointer, int id, double x, double y, double z);
    static native void getSize(long pointer, int id, Rectangle sizeRect);
    static native int addLabel(long pointer, String label);
    static native void setTextFormat(long pointer, int id, String fontName, float size, boolean bold, boolean italic, boolean underline, boolean strikethrough);
    static native void setRotation(long pointer, int id, float rotation, boolean absolute);
    static native void setPriority(long pointer, int id, int priority);
    static native void setHints(long pointer, int id, int hints);
    static native int getHints(long pointer, int id);
    static native void release(long pointer);
    static native int getRenderPass(long pointer);
    static native void start(long pointer);
    static native void stop(long pointer);
    static native void draw(long pointer, long viewPtr, int renderPass);


    // Not sure if these are needed but just in case the following accessor functions are provided to access
    // the following public GLLabelManager fields
    native void setLabelRotation(long pointer, float labelRotation);
    native float getLabelRotation(long pointer);
    native void setAbsoluteLabelRotation(long pointer, boolean absoluteLabelRotation);
    native boolean getAbsoluteLabelRotation(long pointer);
    native void setLabelFadeTimer(long pointer, long labelFadeTimer);
    native long getLabelFadeTimer(long pointer);

    // hints
    static native int getHintWeightedFloat();
    static native int getHintDuplicateOnSplit();
    static native int getHintAutoSurfaceOffsetAdjust();
    static native int getHintXRay();
    static native int getHintScrollingText();
}
