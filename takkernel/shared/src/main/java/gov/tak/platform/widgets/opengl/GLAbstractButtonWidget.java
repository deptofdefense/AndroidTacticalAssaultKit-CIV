
package gov.tak.platform.widgets.opengl;

import gov.tak.api.commons.graphics.IIcon;
import gov.tak.api.engine.map.MapRenderer;
import gov.tak.api.widgets.IAbstractButtonWidget;
import gov.tak.api.widgets.IWidgetBackground;
import gov.tak.platform.widgets.AbstractButtonWidget;

import android.graphics.Color;
import com.atakmap.android.maps.graphics.GLImageCache;
import com.atakmap.android.maps.graphics.GLImage;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.opengl.GLText;

public abstract class GLAbstractButtonWidget extends GLWidget implements
        AbstractButtonWidget.OnBackgroundChangedListener,
        AbstractButtonWidget.OnIconChangedListener, AbstractButtonWidget.OnStateChangedListener, AbstractButtonWidget.OnTextChangedListener {

    public GLAbstractButtonWidget(IAbstractButtonWidget subject,
            MapRenderer renderContext) {
        super(subject, renderContext);

        _bgColor = Color.WHITE;
        if (subject.getWidgetBackground() != null) {
            _bgColor = subject.getWidgetBackground().getColor(subject.getState());
        }
        _textValue = GLText.localize(subject.getText());
        _textDirty = true;

        onButtonIconChanged(subject);
        onButtonBackgroundChanged(subject);
        onButtonStateChanged(subject);
    }

    @Override
    public void start() {
        super.start();
        if (subject instanceof IAbstractButtonWidget) {
            IAbstractButtonWidget bw = (IAbstractButtonWidget) subject;
            bw.addOnBackgroundChangedListener(this);
            bw.addOnIconChangedListener(this);
            bw.addOnStateChangedListener(this);
        }
    }

    @Override
    public void stop() {
        super.stop();
        if (subject instanceof IAbstractButtonWidget) {
            IAbstractButtonWidget bw = (IAbstractButtonWidget) subject;
            bw.removeOnBackgroundChangedListener(this);
            bw.removeOnIconChangedListener(this);
            bw.removeOnStateChangedListener(this);
        }
    }

    @Override
    public void onButtonBackgroundChanged(IAbstractButtonWidget button) {
        if (button.getWidgetBackground() != null) {
            final int bgColor = button.getWidgetBackground().getColor(
                    button.getState());
            runOrQueueEvent(new Runnable() {
                @Override
                public void run() {
                    _bgColor = bgColor;
                }
            });
        }
    }

    @Override
    public void onButtonIconChanged(IAbstractButtonWidget button) {
        if (button.getWidgetIcon() != null) {
            final String iconUri = button.getWidgetIcon().getImageUri(
                    button.getState());
            final int anchorx = button.getWidgetIcon().getAnchorX();
            final int anchory = button.getWidgetIcon().getAnchorY();
            final int iconWidth = button.getWidgetIcon().getWidth();
            final int iconHeight = button.getWidgetIcon().getHeight();
            runOrQueueEvent(new Runnable() {
                @Override
                public void run() {
                    _updateIconRef(iconUri);
                    _updateIconMetrics(anchorx, anchory, iconWidth, iconHeight);
                }
            });
        }
    }

    @Override
    public void onButtonStateChanged(IAbstractButtonWidget button) {

        final boolean updateBg = button.getWidgetBackground() != null;
        final int bgColor = _getWidgetBackgroundColor(button.getWidgetBackground(),
                button.getState());
        final String iconRef = _getIconRef(button.getWidgetIcon(),
                button.getState());

        runOrQueueEvent(new Runnable() {
            @Override
            public void run() {
                if (updateBg) {
                    _bgColor = bgColor;
                }
                _updateIconRef(iconRef);
            }
        });
    }

    private static int _getWidgetBackgroundColor(IWidgetBackground bg, int state) {
        int color = 0;
        if (bg != null) {
            color = bg.getColor(state);
        }
        return color;
    }

    private static String _getIconRef(IIcon icon, int state) {
        return (icon != null) ? icon.getImageUri(state) : null;
    }

    @Override
    public void onButtonTextChanged(IAbstractButtonWidget button) {
        final String textValue = button.getText();
        runOrQueueEvent(new Runnable() {
            @Override
            public void run() {
                _textValue = GLText.localize(textValue);
                _textDirty = true;
            }
        });
    }

    abstract void drawButtonBackground(DrawState drawState, int bgColor);

    /**
     *
     * @param glText
     * @param _textValue the <B>localized</B> button text
     */
    abstract void drawButtonText(GLWidget.DrawState drawState, GLText glText, String _textValue);

    abstract void drawButtonIcon(GLWidget.DrawState drawState, int iconColor, GLImage iconImage);

    @Override
    public void drawWidgetContent(DrawState drawState) {

        drawButtonBackground(drawState, _bgColor);
        if (_image == null && _imageEntry != null
                && _imageEntry.getTextureId() != 0) {
            int twidth = _iconWidth;
            int theight = _iconHeight;
            int tx = -_anchorx;
            int ty = _anchory - theight + 1;
            _image = new GLImage(
                    _renderContext,
                    _imageEntry.getTextureId(),
                    _imageEntry.getTextureWidth(),
                    _imageEntry.getTextureHeight(),
                    _imageEntry.getImageTextureX(),
                    _imageEntry.getImageTextureY(),
                    _imageEntry.getImageTextureWidth(),
                    _imageEntry.getImageTextureHeight(),
                    tx * GLRenderGlobals.getRelativeScaling(),
                    ty * GLRenderGlobals.getRelativeScaling(),
                    twidth * GLRenderGlobals.getRelativeScaling(),
                    theight * GLRenderGlobals.getRelativeScaling());
        }

        if (_image != null)
            drawButtonIcon(drawState, Color.WHITE, _image);

        if (_textDirty) {
            if (_glText == null) {
                _glText = GLText.getInstance(_renderContext, GLRenderGlobals.getDefaultTextFormat());
            }
            _textDirty = false;
        }

        if (_glText != null) {
            drawButtonText(drawState, _glText, _textValue);
        }
    }

    @Override
    public void releaseWidget() {
        runOrQueueEvent(new Runnable() {
            @Override
            public void run() {
                if (_imageEntry != null) {
                    _imageEntry.release();
                    _imageEntry = null;
                }
                _image = null;
            }
        });
    }

    private void _updateIconMetrics(int anchorx, int anchory, int iconWidth,
            int iconHeight) {
        if (anchorx != _anchorx || _anchory != anchory ||
                iconWidth != _iconWidth || iconHeight != _iconHeight) {
            _anchorx = anchorx;
            _anchory = anchory;
            _iconWidth = iconWidth;
            _iconHeight = iconHeight;
            _image = null;
        }
    }

    /*
     * private void _updateIconRef(MapDataRef ref) { if (_imageEntry == null ||
     * _imageEntry.getMapDataRef() != ref) { // done with it if (_imageEntry != null) {
     * _imageEntry.release(); _imageEntry = null; } if (ref != null) { // FIXME: PROPER WAY TO
     * PREFETCH surface.getImageCache().prefetch(ref); _imageEntry =
     * surface.getImageCache().fetchAndRetain(ref); } _image = null; } }
     */
    protected void _updateIconRef(String uri) {
        if (_imageEntry == null || !_imageEntry.getUri().equals(uri)) {

            // done with it
            if (_imageEntry != null) {
                _imageEntry.release();
                _imageEntry = null;
            }

            if (uri != null) {
//                // FIXME: PROPER WAY TO PREFETCH
                final GLImageCache imageCache = GLRenderGlobals.get(getRenderContext()).getImageCache();
                imageCache.prefetch(uri, false);
                _imageEntry = imageCache.fetchAndRetain(uri,
                        false);
            }
            _image = null;
        }
    }

    protected int _bgColor;
    GLImageCache.Entry _imageEntry;
    GLImage _image;
    protected int _anchorx, _anchory, _iconWidth, _iconHeight;
    GLText _glText;
    protected String _textValue;
    protected boolean _textDirty;

}
