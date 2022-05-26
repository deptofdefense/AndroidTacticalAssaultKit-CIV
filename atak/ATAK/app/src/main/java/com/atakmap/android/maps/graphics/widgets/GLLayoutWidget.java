
package com.atakmap.android.maps.graphics.widgets;

import android.graphics.Color;
import android.util.Pair;

import com.atakmap.android.maps.graphics.GLTriangle;
import com.atakmap.android.widgets.LayoutWidget;
import com.atakmap.android.widgets.LayoutWidget.OnBackingColorChangedListener;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLNinePatch;

import gov.tak.api.annotation.DeprecatedApi;

/** @deprecated use {@link gov.tak.platform.widgets.opengl.GLLayoutWidget} */
@Deprecated
@DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
public class GLLayoutWidget extends GLAbstractParentWidget implements
        OnBackingColorChangedListener {

    public final static GLWidgetSpi SPI = new GLWidgetSpi() {
        @Override
        public int getPriority() {
            // ButtonWidget : AbstractParentWidget : MapWidget
            return 2;
        }

        @Override
        public GLWidget create(Pair<MapWidget, GLMapView> arg) {
            final MapWidget subject = arg.first;
            final GLMapView orthoView = arg.second;
            if (subject instanceof LayoutWidget) {
                LayoutWidget compositWidget = (LayoutWidget) subject;
                GLLayoutWidget glCompositWidget = new GLLayoutWidget(
                        compositWidget, orthoView);
                glCompositWidget.startObserving(compositWidget);
                return glCompositWidget;
            } else {
                return null;
            }
        }
    };

    protected final LayoutWidget _subject;

    public GLLayoutWidget(LayoutWidget subject, GLMapView _orthoWorldMap) {
        super(subject, _orthoWorldMap);
        _subject = subject;
        _width = subject.getWidth();
        _height = subject.getHeight();
        _backingColor = subject.getBackingColor();
        _ninePatchBG = subject.getNinePatchBG();
    }

    @Override
    public void startObserving(MapWidget subject) {
        super.startObserving(subject);
        if (subject instanceof LayoutWidget) {
            LayoutWidget lw = (LayoutWidget) subject;
            lw.addOnBackingColorChangedListener(this);
            lw.addOnWidgetSizeChangedListener(this);
        }
    }

    @Override
    public void stopObserving(MapWidget subject) {
        super.stopObserving(subject);
        if (subject instanceof LayoutWidget) {
            LayoutWidget lw = (LayoutWidget) subject;
            lw.removeOnBackingColorChangedListener(this);
            lw.addOnWidgetSizeChangedListener(this);
        }
    }

    @Override
    public void drawWidgetContent() {
        GLES20FixedPipeline.glPushAlphaMod(_subject.getAlpha() / 255f);
        drawBacking();
        super.drawWidgetContent();
        _sizeChanged = false;
        GLES20FixedPipeline.glPopAlphaMod();

        // Fade in progress - request render of next frame
        if (_subject.isFadingAlpha())
            getSurface().requestRender();
    }

    @Override
    public void onBackingColorChanged(LayoutWidget layout) {
        final int backingColor = layout.getBackingColor();
        final boolean ninePatchBG = layout.getNinePatchBG();
        getSurface().queueEvent(new Runnable() {
            @Override
            public void run() {
                _backingColor = backingColor;
                _ninePatchBG = ninePatchBG;
            }
        });
    }

    /**
     * Draw the backing fill for this layout
     */
    protected void drawBacking() {
        if (_backingColor == 0)
            return;

        float r = Color.red(_backingColor) / 255f;
        float g = Color.green(_backingColor) / 255f;
        float b = Color.blue(_backingColor) / 255f;
        float a = Color.alpha(_backingColor) / 255f;

        GLNinePatch ninePatch = GLRenderGlobals.get(getSurface())
                .getMediumNinePatch();
        if (_ninePatchBG && ninePatch != null) {
            GLES20FixedPipeline.glColor4f(r, g, b, a);
            ninePatch.draw(_pWidth, _pHeight);
        } else {
            if (_sizeChanged || _backing == null) {
                _backing = new GLTriangle.Fan(2, 4);
                _backing.setX(0, 0f);
                _backing.setY(0, 0f);
                _backing.setX(1, 0f);
                _backing.setY(1, _pHeight);
                _backing.setX(2, _pWidth);
                _backing.setY(2, _pHeight);
                _backing.setX(3, _pWidth);
                _backing.setY(3, 0f);
            }
            GLES20FixedPipeline.glColor4f(r, g, b, a);
            GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);
            _backing.draw();
            GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_BLEND);
        }
    }

    private GLTriangle.Fan _backing;
    protected int _backingColor;
    protected boolean _ninePatchBG;
}
