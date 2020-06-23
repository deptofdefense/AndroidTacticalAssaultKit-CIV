
package com.atakmap.android.maps.graphics.widgets;

import android.graphics.RectF;

import com.atakmap.android.widgets.MapWidget;
import com.atakmap.map.opengl.GLMapSurface;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.opengl.GLES20FixedPipeline;

public abstract class GLWidget implements
        MapWidget.OnWidgetPointChangedListener,
        MapWidget.OnVisibleChangedListener {

    public GLWidget(MapWidget subject, GLMapView orthoView) {
        this.subject = subject;
        x = subject.getPointX();
        y = subject.getPointY();
        this.orthoView = orthoView;
    }

    protected GLMapSurface getSurface() {
        return orthoView.getSurface();
    }

    public void startObserving(MapWidget subject) {
        subject.addOnWidgetPointChangedListener(this);
        subject.addOnVisibleChangedListener(this);
    }

    public void stopObserving(MapWidget subject) {
        subject.removeOnWidgetPointChangedListener(this);
        subject.removeOnVisibleChangedListener(this);
    }

    @Override
    public void onWidgetPointChanged(MapWidget widget) {
        final float xpos = widget.getPointX();
        final float ypos = widget.getPointY();
        getSurface().queueEvent(new Runnable() {
            @Override
            public void run() {
                x = xpos;
                y = ypos;
            }
        });
    }

    @Override
    public void onVisibleChanged(MapWidget widget) {
        getSurface().requestRender();
    }

    public void drawWidget() {
        if (subject != null && subject.isVisible()) {
            GLES20FixedPipeline.glPushMatrix();
            GLES20FixedPipeline.glTranslatef(x, -y, 0f);
            drawWidgetContent();
            GLES20FixedPipeline.glPopMatrix();
        }
    }

    /**
     * Retrieve the bounding RectF of the current state of the Map. This accounts for the
     * OrthoMapView's focus, so DropDowns will be accounted for.
     *
     * @return The bounding RectF
     */
    protected RectF getWidgetViewF() {
        // Could be in half or third display of dropdown, so use the offset;
        float right = this.orthoView.focusx * 2;
        // Could be in portrait mode as well, so change the bottom accordingly
        float top = this.orthoView.focusy * 2;
        return new RectF(0f, top, right, 0);
    }

    /**
     * Renders the widget content.
     * 
     * <P><B>Must be invoked on GL render thread!</B>
     */
    public abstract void drawWidgetContent();

    /**
     * Releases any resources allocated as a result of
     * {@link #drawWidgetContent()}.
     * 
     * <P><B>Must be invoked on GL render thread!</B>
     */
    public abstract void releaseWidget();

    protected final MapWidget subject;
    float x, y;
    protected GLMapView orthoView;
}
