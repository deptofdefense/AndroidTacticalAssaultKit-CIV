
package com.atakmap.android.maps.graphics.widgets;

import android.graphics.RectF;

import com.atakmap.android.widgets.MapWidget;
import com.atakmap.map.MapRenderer2;
import com.atakmap.map.opengl.GLMapSurface;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.opengl.GLES20FixedPipeline;

import gov.tak.api.annotation.DeprecatedApi;
import gov.tak.api.widgets.opengl.IGLWidget;
import gov.tak.api.widgets.IMapWidget;
import gov.tak.platform.marshal.MarshalManager;

/** @deprecated use {@link gov.tak.platform.widgets.opengl.GLWidget} */
@Deprecated
@DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
public abstract class GLWidget implements IGLWidget,
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
        this.subject.addOnWidgetPointChangedListener(this);
        this.subject.addOnVisibleChangedListener(this);
    }

    public void stopObserving(MapWidget subject) {
        this.subject.removeOnWidgetPointChangedListener(this);
        this.subject.removeOnVisibleChangedListener(this);
    }

    @Override
    public final void start() {
        startObserving(subject);
    }

    @Override
    public final void stop() {
        stopObserving(subject);
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

    public static gov.tak.platform.widgets.opengl.GLWidget.DrawState drawStateFromFixedPipeline(
            MapRenderer2 mapSceneRenderer2) {
        gov.tak.platform.widgets.opengl.GLWidget.DrawState drawState = new gov.tak.platform.widgets.opengl.GLWidget.DrawState(
                MarshalManager.marshal(
                        mapSceneRenderer2.getMapSceneModel(true,
                                mapSceneRenderer2.getDisplayOrigin()),
                        com.atakmap.map.MapSceneModel.class,
                        gov.tak.api.engine.map.MapSceneModel.class));
        drawState.projectionMatrix = new float[16];
        drawState.modelMatrix = new float[16];
        GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_PROJECTION,
                drawState.projectionMatrix, 0);
        GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_MODELVIEW,
                drawState.modelMatrix, 0);
        return drawState;
    }

    @Override
    public void onVisibleChanged(MapWidget widget) {
        getSurface().requestRender();
    }

    @Override
    public void drawWidget(
            gov.tak.platform.widgets.opengl.GLWidget.DrawState drawState) {
        GLES20FixedPipeline.glPushMatrix();
        GLES20FixedPipeline.glLoadMatrixf(drawState.modelMatrix, 0);
        drawWidget();
        GLES20FixedPipeline.glPopMatrix();
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

    @Override
    public IMapWidget getSubject() {
        return subject;
    }

    @Override
    public float getX() {
        return x;
    }

    @Override
    public float getY() {
        return y;
    }

    @Override
    public void setX(float x) {
        this.x = x;
    }

    @Override
    public void setY(float y) {
        this.y = y;
    }

}
