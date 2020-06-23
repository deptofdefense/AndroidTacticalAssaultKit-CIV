
package com.atakmap.android.gridlines.graphics;

import android.graphics.Color;
import android.util.Pair;

import com.atakmap.android.gridlines.GridLinesOverlay;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.opengl.GLLayer;
import com.atakmap.map.layer.opengl.GLLayer2;
import com.atakmap.map.layer.opengl.GLLayerFactory;
import com.atakmap.map.layer.opengl.GLLayerSpi2;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.opengl.GLES20FixedPipeline;

public class GLGridLinesOverlay implements GLLayer,
        GridLinesOverlay.OnGridLinesColorChangedListener,
        GridLinesOverlay.OnGridLinesTypeChangedListener {

    private GLZonesOverlay _zoneLines;
    private GLLatLngZoneOverlay _latlngLines;
    private String _type;
    private float _red, _green, _blue;
    private final GridLinesOverlay subject;
    private final MapRenderer renderContext;

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
                return GLLayerFactory.adapt(new GLGridLinesOverlay(surface,
                        (GridLinesOverlay) layer));
            return null;
        }
    };

    private GLGridLinesOverlay(MapRenderer surface, GridLinesOverlay subject) {
        this.renderContext = surface;
        this.subject = subject;
        subject.addGridLinesColorChangedListener(this);
        subject.addGridLinesTypeChangedListener(this);

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
    }

    @Override
    public Layer getSubject() {
        return this.subject;
    }

    @Override
    public void draw(GLMapView _orthoMap) {
        if (_zoneLines == null) {
            _zoneLines = new GLZonesOverlay();
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
            _zoneLines.draw(_orthoMap);
        } else {
            _latlngLines.draw(_orthoMap);
        }

        GLES20FixedPipeline.glPopMatrix();
    }

    @Override
    public void release() {
        if (_zoneLines != null)
            _zoneLines.release();
        _zoneLines = null;
        if (_latlngLines != null) {
            _latlngLines.release();
            _latlngLines = null;
        }
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
    }
}
