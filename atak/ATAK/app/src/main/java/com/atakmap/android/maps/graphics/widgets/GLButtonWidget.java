
package com.atakmap.android.maps.graphics.widgets;

import android.graphics.Color;
import android.util.Pair;

import com.atakmap.android.maps.graphics.GLImage;
import com.atakmap.android.maps.graphics.GLTriangle;
import com.atakmap.android.widgets.ButtonWidget;
import com.atakmap.android.widgets.ButtonWidget.OnSizeChangedListener;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLText;

import gov.tak.api.annotation.DeprecatedApi;

/** @deprecated use {@link gov.tak.platform.widgets.opengl.GLButtonWidget} */
@Deprecated
@DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
public class GLButtonWidget extends GLAbstractButtonWidget implements
        OnSizeChangedListener {

    public final static GLWidgetSpi SPI = new GLWidgetSpi() {
        @Override
        public int getPriority() {
            // ButtonWidget : AbstractButtonWidget : MapWidget
            return 2;
        }

        @Override
        public GLWidget create(Pair<MapWidget, GLMapView> arg) {
            final MapWidget subject = arg.first;
            final GLMapView orthoView = arg.second;
            if (subject instanceof ButtonWidget) {
                ButtonWidget buttonWidget = (ButtonWidget) subject;
                GLAbstractButtonWidget glButtonWidget = new GLButtonWidget(
                        buttonWidget, orthoView);
                glButtonWidget.startObserving(buttonWidget);
                return glButtonWidget;
            } else {
                return null;
            }
        }
    };

    public GLButtonWidget(ButtonWidget subject, GLMapView orthoView) {
        super(subject, orthoView);
        _width = subject.getButtonWidth();
        _height = subject.getButtonHeight();
        _bgDirty = true;
    }

    @Override
    public void onButtonSizeChanged(ButtonWidget button) {
        final float width = button.getButtonWidth();
        final float height = button.getButtonHeight();
        getSurface().queueEvent(new Runnable() {
            @Override
            public void run() {
                _width = width;
                _height = height;
                _bgDirty = true;
            }
        });
    }

    @Override
    public void drawButtonBackground(int bgColor) {
        if (bgColor != 0) {

            if (_bgDirty) {
                _bg = _buildRect(_width, _height, _bg);
                _bgDirty = false;
            }

            GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);
            float r = Color.red(bgColor) / 255f;
            float g = Color.green(bgColor) / 255f;
            float b = Color.blue(bgColor) / 255f;
            float a = Color.alpha(bgColor) / 255f;
            GLES20FixedPipeline.glColor4f(r, g, b, a);
            _bg.draw();
            GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_BLEND);
        }
    }

    @Override
    public void drawButtonText(GLText _glText, String _textValue) {
        GLES20FixedPipeline.glPushMatrix();
        float texOffx = (_width - _glText.getStringWidth(_textValue)) / 2f;
        float texOffy = (_height - _glText.getStringHeight()) / 2f;
        GLES20FixedPipeline.glTranslatef(texOffx, texOffy, 0f);
        _glText.draw(_textValue, 1.0f, 1.0f, 1.0f, 1.0f);
        GLES20FixedPipeline.glPopMatrix();
    }

    @Override
    public void drawButtonIcon(int iconColor, GLImage image) {
        image.draw(
                Color.red(iconColor) / 255f,
                Color.green(iconColor) / 255f,
                Color.blue(iconColor) / 255f,
                Color.alpha(iconColor) / 255f);
    }

    private GLTriangle.Strip _buildRect(float width, float height,
            GLTriangle.Strip b) {
        if (b == null) {
            b = new GLTriangle.Strip(2, 4);
        }

        b.setX(0, 0f);
        b.setY(0, 0f);

        b.setX(1, 0f);
        b.setY(1, height);

        b.setX(2, width);
        b.setY(2, 0f);

        b.setX(3, width);
        b.setY(3, height);

        return b;
    }

    private GLTriangle.Strip _bg;
    private float _width;
    private float _height;
    private boolean _bgDirty;
}
