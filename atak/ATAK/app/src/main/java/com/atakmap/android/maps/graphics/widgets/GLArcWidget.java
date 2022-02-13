
package com.atakmap.android.maps.graphics.widgets;

import android.graphics.Color;
import android.util.Pair;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.graphics.GLTriangle;
import com.atakmap.android.widgets.ArcWidget;
import com.atakmap.android.widgets.ArcWidget.OnCentralAngleChangedListener;
import com.atakmap.android.widgets.ArcWidget.OnOffsetAngleChangedListener;
import com.atakmap.android.widgets.ArcWidget.OnRadiusChangedListener;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.opengl.GLES20FixedPipeline;

import gov.tak.api.annotation.DeprecatedApi;

/** @deprecated use {@link gov.tak.platform.widgets.opengl.GLArcWidget} */
@Deprecated
@DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
public class GLArcWidget extends GLShapeWidget implements
        OnRadiusChangedListener,
        OnCentralAngleChangedListener,
        OnOffsetAngleChangedListener {

    public final static GLWidgetSpi SPI = new GLWidgetSpi() {
        @Override
        public int getPriority() {
            // ArcWidget : ShapeWidget : MapWidget
            return 2;
        }

        @Override
        public GLWidget create(Pair<MapWidget, GLMapView> arg) {
            final MapWidget subject = arg.first;
            final GLMapView orthoView = arg.second;
            if (subject instanceof ArcWidget) {
                ArcWidget arc = (ArcWidget) subject;
                GLArcWidget glArc = new GLArcWidget(arc, orthoView);
                glArc.startObserving(arc);
                return glArc;
            } else {
                return null;
            }
        }
    };

    public GLArcWidget(ArcWidget arc, GLMapView orthoView) {
        super(arc, orthoView);
        _updateArc(arc);
    }

    @Override
    public void startObserving(MapWidget subject) {
        super.startObserving(subject);
        if (subject instanceof ArcWidget) {
            ArcWidget arc = (ArcWidget) subject;
            arc.addOnCentralAngleChangedListener(this);
            arc.addOnOffsetAngleChangedListener(this);
            arc.addOnRadiusChangedListener(this);
        }
    }

    @Override
    public void stopObserving(MapWidget subject) {
        super.stopObserving(subject);
        if (subject instanceof ArcWidget) {
            ArcWidget arc = (ArcWidget) subject;
            arc.removeOnCentralAngleChangedListener(this);
            arc.removeOnOffsetAngleChangedListener(this);
            arc.removeOnRadiusChangedListener(this);
        }
    }

    @Override
    public void onRadiusChanged(ArcWidget arc) {
        _updateArc(arc);
    }

    @Override
    public void onCentralAngleChanged(ArcWidget arc) {
        _updateArc(arc);
    }

    @Override
    public void onOffsetAngleChanged(ArcWidget arc) {
        _updateArc(arc);
    }

    @Override
    public void drawWidgetContent() {
        if (_verts != null) {
            // _setColor(Color.BLACK);
            GLES20FixedPipeline.glColor4f(0, 0, 0, 1);
            GLES20FixedPipeline.glLineWidth(OUTLINE_WIDTH);
            _verts.draw(GLES20FixedPipeline.GL_LINE_STRIP);

            _setColor(strokeColor);
            GLES20FixedPipeline.glLineWidth(LINE_WIDTH);
            _verts.draw(GLES20FixedPipeline.GL_LINE_STRIP);
        }
    }

    @Override
    public void releaseWidget() {
        stopObserving(subject);
    }

    private void _updateArc(ArcWidget arc) {
        final float centralAngle = arc.getCentralAngle();
        final float offsetAngle = arc.getOffsetAngle();
        final float radius = arc.getRadius();

        getSurface().queueEvent(new Runnable() {
            @Override
            public void run() {
                _buildArc(radius, offsetAngle, centralAngle);
            }
        });
    }

    private void _buildArc(float radius, float offsetAngle,
            float centralAngle) {

        int lineCount = 8;// (int)((centralAngle / 90f) * 12f);
        // if (lineCount<4) lineCount=4; //Always have at least 4 segments
        // if (lineCount>8) lineCount=8;
        int vertCount = lineCount + 1;

        if (_verts == null) {
            _verts = new GLTriangle.Fan(2, vertCount);
        } else {
            _verts.setPointCount(vertCount);
        }

        double angle = offsetAngle * Math.PI / 180d;
        double step = (centralAngle / lineCount) * Math.PI / 180d;

        float[] verts = new float[vertCount * 2];
        for (int i = 0; i < verts.length; i += 2) {
            float px = radius * (float) Math.cos(angle);
            float py = radius * (float) Math.sin(angle);
            verts[i] = px;
            verts[i + 1] = py;
            // _verts.setX(i/2,px);
            // _verts.setY(i/2,py);
            angle += step;
        }
        _verts.setPoints(verts);
    }

    private void _setColor(int color) {
        /*
         * float r = Color.red(color) / 255f; float g = Color.green(color) / 255f; float b =
         * Color.blue(color) / 255f; float a = Color.alpha(color) / 255f;
         */
        GLES20FixedPipeline.glColor4f(Color.red(color) / 255f,
                Color.green(color) / 255f,
                Color.blue(color) / 255f, Color.alpha(color) / 255f);
    }

    private GLTriangle.Fan _verts;

    private static final float LINE_WIDTH = (float) Math
            .ceil(1f * MapView.DENSITY);
    // private static final float OUTLINE_WIDTH = (float) Math.ceil(3f * MapView.DENSITY);
    private static final float OUTLINE_WIDTH = LINE_WIDTH + 2;
}
