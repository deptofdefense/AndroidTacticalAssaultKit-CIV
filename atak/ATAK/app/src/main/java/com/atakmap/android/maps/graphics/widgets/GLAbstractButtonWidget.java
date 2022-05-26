
package com.atakmap.android.maps.graphics.widgets;

import android.graphics.Color;

import com.atakmap.android.maps.MapDataRef;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.graphics.GLImage;
import com.atakmap.android.maps.graphics.GLImageCache;
import com.atakmap.android.widgets.AbstractButtonWidget;
import com.atakmap.android.widgets.AbstractButtonWidget.OnBackgroundChangedListener;
import com.atakmap.android.widgets.AbstractButtonWidget.OnIconChangedListener;
import com.atakmap.android.widgets.AbstractButtonWidget.OnStateChangedListener;
import com.atakmap.android.widgets.AbstractButtonWidget.OnTextChangedListener;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.android.widgets.WidgetBackground;
import com.atakmap.android.widgets.WidgetIcon;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.opengl.GLText;

import gov.tak.api.annotation.DeprecatedApi;

/** @deprecated use {@link gov.tak.platform.widgets.opengl.GLAbstractButtonWidget} */
@Deprecated
@DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
public abstract class GLAbstractButtonWidget extends GLWidget implements
        OnBackgroundChangedListener,
        OnIconChangedListener, OnStateChangedListener, OnTextChangedListener {

    public GLAbstractButtonWidget(AbstractButtonWidget subject,
            GLMapView orthoView) {
        super(subject, orthoView);
        _bgColor = _iconColor = Color.WHITE;
        if (subject.getBackground() != null) {
            _bgColor = subject.getBackground().getColor(subject.getState());
        }
        _textValue = GLText.localize(subject.getText());
        _textDirty = true;

        onButtonIconChanged(subject);
        onButtonBackgroundChanged(subject);
        onButtonStateChanged(subject);
    }

    @Override
    public void startObserving(MapWidget subject) {
        super.startObserving(subject);
        if (subject instanceof AbstractButtonWidget) {
            AbstractButtonWidget bw = (AbstractButtonWidget) subject;
            bw.addOnBackgroundChangedListener(this);
            bw.addOnIconChangedListener(this);
            bw.addOnStateChangedListener(this);
        }
    }

    @Override
    public void stopObserving(MapWidget subject) {
        super.stopObserving(subject);
        if (subject instanceof AbstractButtonWidget) {
            AbstractButtonWidget bw = (AbstractButtonWidget) subject;
            bw.removeOnBackgroundChangedListener(this);
            bw.removeOnIconChangedListener(this);
            bw.removeOnStateChangedListener(this);
        }
    }

    @Override
    public void onButtonBackgroundChanged(AbstractButtonWidget button) {
        if (button.getBackground() != null) {
            final int bgColor = button.getBackground().getColor(
                    button.getState());
            getSurface().queueEvent(new Runnable() {
                @Override
                public void run() {
                    _bgColor = bgColor;
                }
            });
        }
    }

    @Override
    public void onButtonIconChanged(AbstractButtonWidget button) {
        if (button.getIcon() != null) {
            final MapDataRef iconRef = button.getIcon().getIconRef(
                    button.getState());
            final int anchorx = button.getIcon().getAnchorX();
            final int anchory = button.getIcon().getAnchorY();
            final int iconWidth = button.getIcon().getIconWidth();
            final int iconHeight = button.getIcon().getIconHeight();
            getSurface().queueEvent(new Runnable() {
                @Override
                public void run() {
                    _updateIconRef(iconRef != null ? iconRef.toUri() : null);
                    _updateIconMetrics(anchorx, anchory, iconWidth, iconHeight);
                }
            });
        }
    }

    @Override
    public void onButtonStateChanged(AbstractButtonWidget button) {

        final boolean updateBg = button.getBackground() != null;
        final int bgColor = _getBackgroundColor(button.getBackground(),
                button.getState());
        final MapDataRef iconRef = _getIconRef(button.getIcon(),
                button.getState());

        getSurface().queueEvent(new Runnable() {
            @Override
            public void run() {
                if (updateBg) {
                    _bgColor = bgColor;
                }
                _updateIconRef(iconRef != null ? iconRef.toUri() : null);
            }
        });
    }

    private static int _getBackgroundColor(WidgetBackground bg, int state) {
        int color = 0;
        if (bg != null) {
            color = bg.getColor(state);
        }
        return color;
    }

    private static MapDataRef _getIconRef(WidgetIcon icon, int state) {
        MapDataRef ref = null;
        if (icon != null) {
            ref = icon.getIconRef(state);
        }
        return ref;
    }

    @Override
    public void onButtonTextChanged(AbstractButtonWidget button) {
        final String textValue = button.getText();
        getSurface().queueEvent(new Runnable() {
            @Override
            public void run() {
                _textValue = GLText.localize(textValue);
                _textDirty = true;
            }
        });
    }

    public abstract void drawButtonBackground(int bgColor);

    /**
     * 
     * @param glText
     * @param _textValue the <B>localized</B> button text
     */
    public abstract void drawButtonText(GLText glText, String _textValue);

    public abstract void drawButtonIcon(int iconColor, GLImage iconImage);

    @Override
    public void drawWidgetContent() {

        drawButtonBackground(_bgColor);

        if (_image == null && _imageEntry != null
                && _imageEntry.getTextureId() != 0) {
            int twidth = _iconWidth;
            int theight = _iconHeight;
            int tx = -_anchorx;
            int ty = _anchory - theight + 1;
            _image = new GLImage(_imageEntry.getTextureId(),
                    _imageEntry.getTextureWidth(),
                    _imageEntry.getTextureHeight(),
                    _imageEntry.getImageTextureX(),
                    _imageEntry.getImageTextureY(),
                    _imageEntry.getImageTextureWidth(),
                    _imageEntry.getImageTextureHeight(),
                    tx * MapView.DENSITY,
                    ty * MapView.DENSITY,
                    twidth * MapView.DENSITY,
                    theight * MapView.DENSITY);
        }

        if (_image != null)
            drawButtonIcon(_iconColor, _image);

        if (_textDirty) {
            if (_glText == null) {
                _glText = GLText.getInstance(MapView.getDefaultTextFormat());
            }
            _textDirty = false;
        }

        if (_glText != null) {
            drawButtonText(_glText, _textValue);
        }
    }

    @Override
    public void releaseWidget() {
        getSurface().queueEvent(new Runnable() {
            @Override
            public void run() {
                if (_imageEntry != null) {
                    _imageEntry.release();
                    _imageEntry = null;
                }
                _image = null;
            }
        });
        stopObserving(subject);
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
                // FIXME: PROPER WAY TO PREFETCH
                final GLImageCache imageCache = GLRenderGlobals.get(
                        getSurface()).getImageCache();
                imageCache.prefetch(uri, false);
                _imageEntry = imageCache.fetchAndRetain(uri,
                        false);
            }
            _image = null;
        }
    }

    protected int _bgColor, _iconColor;
    protected GLImageCache.Entry _imageEntry;
    protected GLImage _image;
    protected int _anchorx, _anchory, _iconWidth, _iconHeight;
    protected GLText _glText;
    protected String _textValue;
    protected boolean _textDirty;

}
