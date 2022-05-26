
package com.atakmap.android.maps.graphics.widgets;

import android.util.Pair;

import com.atakmap.android.maps.graphics.GLTriangle;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.android.widgets.CenterBeadWidget;
import com.atakmap.map.AtakMapView;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.opengl.GLES20FixedPipeline;

import gov.tak.api.annotation.DeprecatedApi;
import gov.tak.api.engine.map.MapRenderer;
import gov.tak.api.widgets.IMapWidget;
import gov.tak.api.widgets.opengl.IGLWidget;
import gov.tak.api.widgets.opengl.IGLWidgetSpi;
import gov.tak.platform.marshal.MarshalManager;

/** @deprecated use {@link gov.tak.platform.widgets.opengl.GLCenterBeadWidget} */
@Deprecated
@DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
public class GLCenterBeadWidget extends GLShapeWidget {

    GLTriangle.Strip _crossHairLine;

    private final CenterBeadWidget sw;
    private final AtakMapView mapView;

    public final static GLWidgetSpi SPI = new SpiImpl();

    public GLCenterBeadWidget(CenterBeadWidget subject, GLMapView orthoView) {
        super(subject, orthoView);
        this.sw = subject;
        this.mapView = orthoView.getSurface().getMapView();
        _crossHairLine = new GLTriangle.Strip(2, 4);
        _crossHairLine.setX(0, 0f);
        _crossHairLine.setY(0, 0f);

        _crossHairLine.setX(1, 0f);
        _crossHairLine.setY(1, 1f);

        _crossHairLine.setX(2, 1f);
        _crossHairLine.setY(2, 0f);

        _crossHairLine.setX(3, 1f);
        _crossHairLine.setY(3, 1f);
    }

    @Override
    public void drawWidgetContent() {
        if (!sw.isVisible())
            return;

        float density = (float) (orthoView.getSurface().getMapView()
                .getDisplayDpi() / 240d);
        float radius = 50f * density;
        float diameter = radius * 2;
        float d8 = diameter / 8, d32 = diameter / 32;
        float left = orthoView.focusx - radius;
        float bottom = (orthoView.getTop() - orthoView.focusy) - radius;

        GLES20FixedPipeline.glPushMatrix();

        GLES20FixedPipeline.glColor4f(1f, 0f, 0f, .70f);
        GLES20FixedPipeline.glLoadIdentity();

        GLES20FixedPipeline.glTranslatef(left + radius + d8,
                bottom + radius - 1f, 0f);
        GLES20FixedPipeline.glScalef(radius, 4f, 1f);
        _crossHairLine.draw();

        GLES20FixedPipeline.glLoadIdentity();
        GLES20FixedPipeline.glTranslatef(left + radius + d32,
                bottom + radius, 0f);
        GLES20FixedPipeline.glScalef(radius, 1, 1f);
        _crossHairLine.draw();

        GLES20FixedPipeline.glLoadIdentity();
        GLES20FixedPipeline.glTranslatef(left - d8,
                bottom + radius - 1f, 0f);
        GLES20FixedPipeline.glScalef(radius, 4f, 1f);
        _crossHairLine.draw();

        GLES20FixedPipeline.glLoadIdentity();
        GLES20FixedPipeline.glTranslatef(left - d32,
                bottom + radius, 0f);
        GLES20FixedPipeline.glScalef(radius, 1f, 1f);
        _crossHairLine.draw();

        GLES20FixedPipeline.glLoadIdentity();
        GLES20FixedPipeline.glTranslatef(left + radius - 1f,
                bottom + radius + d8, 0f);
        GLES20FixedPipeline.glScalef(4f, radius, 1f);
        _crossHairLine.draw();

        GLES20FixedPipeline.glLoadIdentity();
        GLES20FixedPipeline.glTranslatef(left + radius,
                bottom + radius + d32, 0f);
        GLES20FixedPipeline.glScalef(1f, radius, 1f);
        _crossHairLine.draw();

        GLES20FixedPipeline.glLoadIdentity();
        GLES20FixedPipeline.glTranslatef(left + radius - 1f,
                bottom - d8, 0f);
        GLES20FixedPipeline.glScalef(4f, radius, 1f);
        _crossHairLine.draw();

        GLES20FixedPipeline.glLoadIdentity();
        GLES20FixedPipeline.glTranslatef(left + radius,
                bottom - d32, 0f);
        GLES20FixedPipeline.glScalef(1f, radius, 1f);
        _crossHairLine.draw();

        // center dot (black outline, red center)
        GLES20FixedPipeline.glLoadIdentity();
        GLES20FixedPipeline.glColor4f(0f, 0f, 0f, .70f);
        GLES20FixedPipeline.glTranslatef(left + radius - 3,
                bottom + radius - 3, 0f);
        GLES20FixedPipeline.glScalef(7f, 7f, 1f);
        _crossHairLine.draw();

        GLES20FixedPipeline.glLoadIdentity();
        GLES20FixedPipeline.glColor4f(1f, 0f, 0f, .70f);
        GLES20FixedPipeline.glTranslatef(left + radius - 2,
                bottom + radius - 2, 0f);
        GLES20FixedPipeline.glScalef(5f, 5f, 1f);
        _crossHairLine.draw();

        GLES20FixedPipeline.glPopMatrix();
    }

    @Override
    public void releaseWidget() {
        stopObserving(subject);
    }

    private final static class SpiImpl implements GLWidgetSpi, IGLWidgetSpi {

        @Override
        public IGLWidget create(MapRenderer renderer, IMapWidget widget) {
            if (!(widget instanceof CenterBeadWidget))
                return null;
            final gov.tak.platform.widgets.CenterBeadWidget impl = MarshalManager
                    .marshal(
                            (CenterBeadWidget) widget,
                            CenterBeadWidget.class,
                            gov.tak.platform.widgets.CenterBeadWidget.class);
            if (impl == null)
                return null;
            return gov.tak.platform.widgets.opengl.GLCenterBeadWidget.SPI
                    .create(renderer, impl);
        }

        @Override
        public int getPriority() {
            // CenterBeadWidget : MapWidget
            return 1;
        }

        @Override
        public GLWidget create(Pair<MapWidget, GLMapView> arg) {
            final MapWidget subject = arg.first;
            final GLMapView orthoView = arg.second;
            if (subject instanceof CenterBeadWidget) {
                CenterBeadWidget cbWidget = (CenterBeadWidget) subject;
                GLCenterBeadWidget glcbWidget = new GLCenterBeadWidget(
                        cbWidget,
                        orthoView);
                glcbWidget.startObserving(cbWidget);
                return glcbWidget;
            } else {
                return null;
            }
        }
    }
}
