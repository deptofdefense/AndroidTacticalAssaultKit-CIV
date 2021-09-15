
package com.atakmap.android.gridlines.graphics;

import android.graphics.Color;
import android.util.Pair;

import com.atakmap.android.gridlines.GridLinesOverlay;
import com.atakmap.coremap.concurrent.NamedThreadFactory;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.MapRenderer3;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.control.SurfaceRendererControl;
import com.atakmap.map.layer.opengl.GLAbstractLayer2;
import com.atakmap.map.layer.opengl.GLLayer2;
import com.atakmap.map.layer.opengl.GLLayerSpi2;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.util.Visitor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class GLGridLinesOverlay extends GLAbstractLayer2 implements
        GridLinesOverlay.OnGridLinesColorChangedListener,
        GridLinesOverlay.OnGridLinesTypeChangedListener {

    private GLZonesOverlay _zoneLines;
    private GLLatLngZoneOverlay _latlngLines;
    private String _type;
    private float _red, _green, _blue;
    private final GridLinesOverlay subject;
    private Executor _genGridTileExecutor;
    private Executor _segmentLabelsExecutor;

    public final static GLLayerSpi2 SPI2 = new GLLayerSpi2() {
        @Override
        public int getPriority() {
            return 1;
        }

        /** called when registering a Layer with the mapengine
         * @param arg the layer that was added to the MapEngine
         * @return the GlLayer class that matches the layer registered
         */
        @Override
        public GLLayer2 create(Pair<MapRenderer, Layer> arg) {
            final MapRenderer surface = arg.first;
            final Layer layer = arg.second;
            if (layer instanceof GridLinesOverlay)
                return new GLGridLinesOverlay(surface,
                        (GridLinesOverlay) layer);
            return null;
        }
    };

    private GLGridLinesOverlay(MapRenderer surface, GridLinesOverlay subject) {
        super(surface, subject,
                GLMapView.RENDER_PASS_SURFACE | GLMapView.RENDER_PASS_SPRITES);

        this.subject = subject;

        final int color = this.subject.getColor();

        _red = Color.red(color) / 255f;
        _green = Color.green(color) / 255f;
        _blue = Color.blue(color) / 255f;
        _type = this.subject.getType();
    }

    @Override
    public void onGridLinesColorChanged(GridLinesOverlay layer) {
        final int color = this.subject.getColor();

        final float r = Color.red(color) / 255f;
        final float g = Color.green(color) / 255f;
        final float b = Color.blue(color) / 255f;
        this.renderContext.queueEvent(new Runnable() {
            @Override
            public void run() {
                _red = r;
                _green = g;
                _blue = b;
                if (_zoneLines != null) {
                    _zoneLines.setColor(_red, _green, _blue);
                }
                if (_latlngLines != null) {
                    _latlngLines.setColor(_red, _green, _blue);
                }
            }
        });
        final SurfaceRendererControl[] ctrl = new SurfaceRendererControl[1];
        if (renderContext instanceof MapRenderer3)
            ctrl[0] = ((MapRenderer3) renderContext)
                    .getControl(SurfaceRendererControl.class);
        else
            renderContext.visitControl(null,
                    new Visitor<SurfaceRendererControl>() {
                        @Override
                        public void visit(SurfaceRendererControl object) {
                            ctrl[0] = object;
                        }
                    }, SurfaceRendererControl.class);
        if (ctrl[0] != null)
            ctrl[0].markDirty();
    }

    @Override
    protected void init() {
        super.init();

        _genGridTileExecutor = Executors.newFixedThreadPool(1,
                new NamedThreadFactory("GLGridLinesOverlay-tile"));
        _segmentLabelsExecutor = Executors.newFixedThreadPool(1,
                new NamedThreadFactory("GLGridLinesOverlay"));
    }

    @Override
    protected void drawImpl(GLMapView view, int renderPass) {
        if ((renderPass & getRenderPass()) == 0)
            return;

        if (_zoneLines == null) {
            _zoneLines = new GLZonesOverlay();
            _zoneLines._genGridTileExecutor = _genGridTileExecutor;
            _zoneLines._segmentLabelsExecutor = _segmentLabelsExecutor;
            _zoneLines.setColor(_red, _green, _blue);
            _zoneLines.setType(_type);
        }

        if (_latlngLines == null) {
            _latlngLines = new GLLatLngZoneOverlay();
            _latlngLines.setColor(_red, _green, _blue);
            _latlngLines.setType(_type);
        }

        GLES20FixedPipeline.glPushMatrix();

        String MGRS_ZONE_TYPE = "MGRS";
        if (_type.equals(MGRS_ZONE_TYPE)) {
            _zoneLines.draw(view, renderPass);
        } else {
            _latlngLines.draw(view, renderPass);
        }

        GLES20FixedPipeline.glPopMatrix();

    }

    @Override
    public void release() {
        super.release();

        if (_zoneLines != null)
            _zoneLines.release();
        _zoneLines = null;
        if (_latlngLines != null) {
            _latlngLines.release();
            _latlngLines = null;
        }
    }

    @Override
    public int getRenderPass() {
        return GLMapView.RENDER_PASS_SURFACE | GLMapView.RENDER_PASS_SPRITES;
    }

    @Override
    public void onGridLinesTypeChanged(final GridLinesOverlay layer) {
        _type = layer.getType();
        this.renderContext.queueEvent(new Runnable() {
            @Override
            public void run() {
                if (_zoneLines != null) {
                    _zoneLines.setType(layer.getType());
                }
                if (_latlngLines != null) {
                    _latlngLines.setType(layer.getType());
                }
            }
        });
        final SurfaceRendererControl[] ctrl = new SurfaceRendererControl[1];
        if (renderContext instanceof MapRenderer3)
            ctrl[0] = ((MapRenderer3) renderContext)
                    .getControl(SurfaceRendererControl.class);
        else
            renderContext.visitControl(null,
                    new Visitor<SurfaceRendererControl>() {
                        @Override
                        public void visit(SurfaceRendererControl object) {
                            ctrl[0] = object;
                        }
                    }, SurfaceRendererControl.class);
        if (ctrl[0] != null)
            ctrl[0].markDirty();
    }

    @Override
    public void start() {
        super.start();

        subject.addGridLinesColorChangedListener(this);
        subject.addGridLinesTypeChangedListener(this);
    }

    @Override
    public void stop() {
        super.stop();

        subject.removeGridLinesColorChangedListener(this);
        subject.removeGridLinesTypeChangedListener(this);
    }
}
