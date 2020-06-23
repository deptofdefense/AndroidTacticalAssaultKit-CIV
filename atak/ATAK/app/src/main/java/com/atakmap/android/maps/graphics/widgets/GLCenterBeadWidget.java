
package com.atakmap.android.maps.graphics.widgets;

import android.util.Pair;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.graphics.GLTriangle;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.android.widgets.CenterBeadWidget;
import com.atakmap.map.AtakMapView;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.opengl.GLES20FixedPipeline;

public class GLCenterBeadWidget extends GLShapeWidget {

    GLTriangle.Strip _crossHairLine;

    private static final float LINE_WIDTH = (float) Math
            .ceil(1f * MapView.DENSITY);

    private final CenterBeadWidget sw;
    private final AtakMapView mapView;

    public final static GLWidgetSpi SPI = new GLWidgetSpi() {
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
    };

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
        if (getSurface() != null) {

            if (!sw.isVisible())
                return;

            int sheight = getSurface().getHeight();
            int swidth = getSurface().getWidth();

            float size = 50f * MapView.DENSITY;
            final float _left = swidth / 2f - size;
            final float _right = swidth / 2f + size;
            final float _top = sheight / 2f - size;
            float _bottom = sheight / 2f + size;

            final float width = _right - _left;
            float height = _top - _bottom;

            // shift the bottom and the height by the size of the actionbar
            _bottom = _bottom - (mapView.getDefaultActionBarHeight() / 2f);

            GLES20FixedPipeline.glPushMatrix();

            GLES20FixedPipeline.glColor4f(1f, 0f, 0f, .70f);
            GLES20FixedPipeline.glLoadIdentity();

            GLES20FixedPipeline.glTranslatef(_left + width / 2 + width / 8,
                    _bottom + height / 2 - 1f, 0f);
            GLES20FixedPipeline.glScalef(width / 2, 4f, 1f);
            _crossHairLine.draw();

            GLES20FixedPipeline.glLoadIdentity();
            GLES20FixedPipeline.glTranslatef(_left + width / 2 + width / 32,
                    _bottom + height / 2, 0f);
            GLES20FixedPipeline.glScalef(width / 2, 1, 1f);
            _crossHairLine.draw();

            GLES20FixedPipeline.glLoadIdentity();
            GLES20FixedPipeline.glTranslatef(_left - width / 8,
                    _bottom + height / 2 - 1f, 0f);
            GLES20FixedPipeline.glScalef(width / 2, 4f, 1f);
            _crossHairLine.draw();

            GLES20FixedPipeline.glLoadIdentity();
            GLES20FixedPipeline.glTranslatef(_left - width / 32,
                    _bottom + height / 2, 0f);
            GLES20FixedPipeline.glScalef(width / 2, 1f, 1f);
            _crossHairLine.draw();

            GLES20FixedPipeline.glLoadIdentity();
            GLES20FixedPipeline.glTranslatef(_left + width / 2 - 1f,
                    _bottom + height / 2 + height / 8,
                    0f);
            GLES20FixedPipeline.glScalef(4f, height / 2, 1f);
            _crossHairLine.draw();

            GLES20FixedPipeline.glLoadIdentity();
            GLES20FixedPipeline.glTranslatef(_left + width / 2,
                    _bottom + height / 2 + height / 32,
                    0f);
            GLES20FixedPipeline.glScalef(1f, height / 2, 1f);
            _crossHairLine.draw();

            GLES20FixedPipeline.glLoadIdentity();
            GLES20FixedPipeline.glTranslatef(_left + width / 2 - 1f,
                    _bottom - height / 8, 0f);
            GLES20FixedPipeline.glScalef(4f, height / 2, 1f);
            _crossHairLine.draw();

            GLES20FixedPipeline.glLoadIdentity();
            GLES20FixedPipeline.glTranslatef(_left + width / 2,
                    _bottom - height / 32, 0f);
            GLES20FixedPipeline.glScalef(1f, height / 2, 1f);
            _crossHairLine.draw();

            // center dot (black outline, red center)
            GLES20FixedPipeline.glLoadIdentity();
            GLES20FixedPipeline.glColor4f(0f, 0f, 0f, .70f);
            GLES20FixedPipeline.glTranslatef(_left + width / 2 - 3,
                    _bottom + height / 2 - 3,
                    0f);
            GLES20FixedPipeline.glScalef(7f, 7f, 1f);
            _crossHairLine.draw();

            GLES20FixedPipeline.glLoadIdentity();
            GLES20FixedPipeline.glColor4f(1f, 0f, 0f, .70f);
            GLES20FixedPipeline.glTranslatef(_left + width / 2 - 2,
                    _bottom + height / 2 - 2,
                    0f);
            GLES20FixedPipeline.glScalef(5f, 5f, 1f);
            _crossHairLine.draw();

            GLES20FixedPipeline.glPopMatrix();
        }
    }

    @Override
    public void releaseWidget() {
        stopObserving(subject);
    }
}
