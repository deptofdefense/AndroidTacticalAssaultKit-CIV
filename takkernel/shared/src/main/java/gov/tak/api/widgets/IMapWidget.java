
package gov.tak.api.widgets;

import gov.tak.api.binding.IPropertyBindingObject;
import gov.tak.platform.binding.PropertyInfo;
import gov.tak.platform.graphics.PointF;
import gov.tak.platform.ui.MotionEvent;

public interface IMapWidget extends IPropertyBindingObject<IMapWidget> {

    PropertyInfo PROPERTY_POINT = new PropertyInfo("point", PointF.class);

    interface OnWidgetPointChangedListener {
        void onWidgetPointChanged(IMapWidget widget);
    }

    interface OnVisibleChangedListener {
        void onVisibleChanged(IMapWidget widget);
    }

    interface OnPressListener {
        void onMapWidgetPress(IMapWidget widget, MotionEvent event);
    }

    interface OnLongPressListener {
        void onMapWidgetLongPress(IMapWidget widget);
    }

    interface OnUnpressListener {
        void onMapWidgetUnpress(IMapWidget widget, MotionEvent event);
    }

    interface OnMoveListener {
        boolean onMapWidgetMove(IMapWidget widget, MotionEvent event);
    }

    interface OnClickListener{
        void onMapWidgetClick(IMapWidget widget, MotionEvent event);
    }

    interface OnWidgetSizeChangedListener {
        void onWidgetSizeChanged(IMapWidget widget);
    }

    interface OnHoverListener {
        void onMapWidgetHover(IMapWidget widget, MotionEvent event);
    }

    void addOnWidgetPointChangedListener(OnWidgetPointChangedListener l);
    void removeOnWidgetPointChangedListener(OnWidgetPointChangedListener l);
    void addOnVisibleChangedListener(OnVisibleChangedListener l);
    void removeOnVisibleChangedListener(OnVisibleChangedListener l);
    void addOnPressListener(OnPressListener l);
    void removeOnPressListener(OnPressListener l);
    void addOnLongPressListener(OnLongPressListener l);
    void removeOnLongPressListener(OnLongPressListener l);
    void addOnUnpressListener(OnUnpressListener l);
    void removeOnUnpressListener(OnUnpressListener l);
    void addOnMoveListener(OnMoveListener l);
    void removeOnMoveListener(OnMoveListener l);
    void addOnClickListener(OnClickListener l);
    void removeOnClickListener(OnClickListener l);
    void addOnWidgetSizeChangedListener(OnWidgetSizeChangedListener l);
    void removeOnWidgetSizeChangedListener(OnWidgetSizeChangedListener l);
    void addOnHoverListener(OnHoverListener l);
    void removeOnHoverListener(OnHoverListener l);
    void onPress(MotionEvent event);
    void onLongPress();
    void onUnpress(MotionEvent event);
    void onClick(MotionEvent event);
    boolean onMove(MotionEvent event);
    void onHover(MotionEvent event);
    boolean isEnterable();

    void setPoint(float x, float y);

    void setName(String name);
    String getName();

    /**
     * Subclass to allow for a new x,y,height,width to be computed when the orientation
     * has changed.
     */
    void orientationChanged();

    /**
     * Set the ascending order of the MapItem. zOrder affects hit order.
     * Note:  zOrder does not affect rendering order at this time.
     *
     * @param zOrder Z-order value
     */
    void setZOrder(double zOrder);
    double getZOrder();

    /**
     * Set the visibility of this map widget
     * @param visible True to show, false to hide
     * @return True if visibility changed
     */
    boolean setVisible(boolean visible);
    boolean isVisible();
    boolean testHit(float x, float y);
    IMapWidget seekWidgetHit(MotionEvent event, float x, float y);
    float getPointX();
    float getPointY();

    /**
     * Get the absolute position of this widget on the screen
     * @return Widget absolute position
     */
    PointF getAbsoluteWidgetPosition();

    /**
     * Get the absolute path to this widget, starting from the root layout
     * @return Widget absolute path
     */
    String getAbsolutePath();

    void setParent(IParentWidget parent);
    IParentWidget getParent();

    boolean setWidth(float width);
    float getWidth();

    boolean setHeight(float height);
    float getHeight();

    boolean setSize(float width, float height);
    float[] getSize(boolean incPadding, boolean incMargin);

    void setMargins(float left, float top, float right, float bottom);
    float[] getMargins();

    boolean setPadding(float left, float top, float right, float bottom);
    boolean setPadding(float p);
    float[] getPadding();


    /**
     * Set whether this widget can be touched
     * @param touchable True if touchable
     */
    void setTouchable(boolean touchable);

    boolean isTouchable();

}
