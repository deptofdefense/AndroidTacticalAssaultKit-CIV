
package com.atakmap.android.maps.graphics.widgets;

import android.util.Pair;

import com.atakmap.android.maps.graphics.GLIcon;
import com.atakmap.android.maps.graphics.GLImageCache;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.android.widgets.MarkerIconWidget;
import com.atakmap.android.widgets.MarkerIconWidget.OnMarkerWidgetIconChangedListener;
import com.atakmap.android.widgets.MarkerIconWidget.OnMarkerWidgetIconRotationChangedListener;
import com.atakmap.android.widgets.MarkerIconWidget.OnMarkerWidgetIconStateChangedListener;
import com.atakmap.coremap.maps.assets.Icon;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.opengl.GLES20FixedPipeline;

public class GLMarkerIconWidget extends GLWidget2 implements
        OnMarkerWidgetIconChangedListener,
        OnMarkerWidgetIconStateChangedListener,
        OnMarkerWidgetIconRotationChangedListener {

    public final static GLWidgetSpi SPI = new GLWidgetSpi() {
        @Override
        public int getPriority() {
            // MarkerIconWidget : MapWidget
            return 1;
        }

        @Override
        public GLWidget create(Pair<MapWidget, GLMapView> arg) {
            final MapWidget subject = arg.first;
            final GLMapView orthoView = arg.second;
            if (subject instanceof MarkerIconWidget) {
                MarkerIconWidget markerWidget = (MarkerIconWidget) subject;
                GLMarkerIconWidget glMarkerWidget = new GLMarkerIconWidget(
                        markerWidget, orthoView);
                glMarkerWidget.startObserving(markerWidget);
                return glMarkerWidget;
            } else {
                return null;
            }
        }
    };

    public GLMarkerIconWidget(MarkerIconWidget iconWidget,
            GLMapView orthoView) {
        super(iconWidget, orthoView);

        final Icon icon = iconWidget.getIcon();
        final int state = iconWidget.getState();

        getSurface().queueEvent(new Runnable() {
            @Override
            public void run() {
                _updateIcon(icon, state);
            }
        });
    }

    @Override
    public void startObserving(MapWidget subject) {
        super.startObserving(subject);
        if (subject instanceof MarkerIconWidget) {
            MarkerIconWidget miw = (MarkerIconWidget) subject;
            miw.addOnMarkerWidgetIconChangedListener(this);
            miw.addOnMarkerWidgetIconStateChangedListener(this);
            miw.addOnMarkerWidgetIconRotationChangedListener(this);
        }
    }

    @Override
    public void stopObserving(MapWidget subject) {
        super.stopObserving(subject);
        if (subject instanceof MarkerIconWidget) {
            MarkerIconWidget miw = (MarkerIconWidget) subject;
            miw.removeOnMarkerWidgetIconChangedListener(this);
            miw.removeOnMarkerWidgetIconStateChangedListener(this);
            miw.removeOnMarkerWidgetIconRotationChangedListener(this);
        }
    }

    @Override
    public void onMarkerWidgetIconChanged(MarkerIconWidget iconWidget) {
        final Icon icon = iconWidget.getIcon();
        final int state = iconWidget.getState();
        getSurface().queueEvent(new Runnable() {
            @Override
            public void run() {
                _releaseIcon();
                _updateIcon(icon, state);
            }
        });
    }

    @Override
    public void onMarkerWidgetStateChanged(MarkerIconWidget iconWidget) {
        final Icon icon = iconWidget.getIcon();
        final int state = iconWidget.getState();
        getSurface().queueEvent(new Runnable() {
            @Override
            public void run() {
                _updateIcon(icon, state);
            }
        });
    }

    @Override
    public void onMarkerWidgetIconRotationChanged(MarkerIconWidget iconWidget) {
        _rotation = iconWidget.getRotation();
    }

    @Override
    public void drawWidgetContent() {
        if (_icon != null) {
            GLES20FixedPipeline.glPushMatrix();
            GLES20FixedPipeline.glTranslatef(_icon.getWidth() / 2f,
                    -_icon.getHeight() / 2f, 0f);
            GLES20FixedPipeline.glScalef((float) _icon.getWidth() / _pWidth,
                    (float) _icon.getHeight() / _pHeight, 1);
            GLES20FixedPipeline.glRotatef(_rotation, 0f, 0f, 1f);
            GLES20FixedPipeline.glTranslatef(-_icon.getWidth() / 2f,
                    _icon.getHeight() / 2f, 0f);

            GLES20FixedPipeline.glTranslatef(_padding[LEFT], -_padding[TOP],
                    0f);

            GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);
            GLES20FixedPipeline.glBlendFunc(GLES20FixedPipeline.GL_SRC_ALPHA,
                    GLES20FixedPipeline.GL_ONE_MINUS_SRC_ALPHA);
            _icon.drawColor(_color);
            GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_BLEND);

            GLES20FixedPipeline.glPopMatrix();
        }
    }

    @Override
    public void releaseWidget() {
        getSurface().queueEvent(new Runnable() {
            @Override
            public void run() {
                _releaseIcon();
            }
        });
        stopObserving(subject);
    }

    private void _releaseIcon() {
        if (_icon != null) {
            _icon.release();
            _icon = null;
        }
    }

    private void _setIcon(Icon icon) {
        _releaseIcon();
        if (icon != null) {
            int anchorx = 0;
            int anchory = 0;
            // if (icon.getAnchor() != null) {
            anchorx = icon.getAnchorX();// .x;
            anchory = icon.getAnchorY();// .y;
            // }
            _icon = new GLIcon(icon.getWidth(), icon.getHeight(), anchorx,
                    anchory);
        }
    }

    private void _updateIcon(Icon icon, int state) {
        if (_icon == null) {
            _setIcon(icon);
        }
        if (icon != null) {
            _color = icon.getColor(state);
            if (_icon != null) {
                // MapDataRef iconRef =
                // MapDataRef.parseUri(icon.getImageUri(state));//icon.getIconRef(state);
                GLImageCache.Entry iconEntry = GLRenderGlobals
                        .get(getSurface()).getImageCache()
                        .fetchAndRetain(
                                icon.getImageUri(state), true);
                _icon.updateCacheEntry(iconEntry);
            }
        }
    }

    private GLIcon _icon;
    private int _color;
    private float _rotation = 0f;
}
