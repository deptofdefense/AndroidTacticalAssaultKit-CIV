
package gov.tak.platform.widgets.opengl;

import gov.tak.api.commons.graphics.IIcon;
import gov.tak.api.engine.map.MapRenderer;
import gov.tak.api.widgets.IMapWidget;
import gov.tak.api.widgets.IMarkerIconWidget;
import gov.tak.api.widgets.opengl.IGLWidgetSpi;
import gov.tak.platform.commons.opengl.GLES30;
import gov.tak.platform.commons.opengl.Matrix;
import gov.tak.api.engine.Shader;

import com.atakmap.android.maps.graphics.GLImageCache;
import com.atakmap.android.maps.graphics.GLIcon;
import com.atakmap.map.opengl.GLRenderGlobals;

public class GLMarkerIconWidget extends GLWidget implements
        IMarkerIconWidget.OnMarkerWidgetIconChangedListener,
        IMarkerIconWidget.OnMarkerWidgetIconStateChangedListener,
        IMarkerIconWidget.OnMarkerWidgetIconRotationChangedListener {

    public final static IGLWidgetSpi SPI = new IGLWidgetSpi() {
        @Override
        public int getPriority() {
            // MarkerIconWidget : MapWidget
            return 1;
        }

        @Override
        public GLWidget create(MapRenderer renderContext, IMapWidget subject) {
            if (subject instanceof IMarkerIconWidget) {
                IMarkerIconWidget markerWidget = (IMarkerIconWidget) subject;
                GLMarkerIconWidget glMarkerWidget = new GLMarkerIconWidget(
                        markerWidget, renderContext);
                return glMarkerWidget;
            } else {
                return null;
            }
        }
    };

    public GLMarkerIconWidget(IMarkerIconWidget iconWidget,
            MapRenderer orthoView) {
        super(iconWidget, orthoView);

        final IIcon icon = iconWidget.getWidgetIcon();
        final int state = iconWidget.getState();

        runOrQueueEvent(new Runnable() {
            @Override
            public void run() {
                _updateIcon(icon, state);
            }
        });
    }

    @Override
    public void start() {
        super.start();
        if (subject instanceof IMarkerIconWidget) {
            IMarkerIconWidget miw = (IMarkerIconWidget) subject;
            miw.addOnMarkerWidgetIconChangedListener(this);
            miw.addOnMarkerWidgetIconStateChangedListener(this);
            miw.addOnMarkerWidgetIconRotationChangedListener(this);
        }
    }

    @Override
    public void stop() {
        super.stop();
        if (subject instanceof IMarkerIconWidget) {
            IMarkerIconWidget miw = (IMarkerIconWidget) subject;
            miw.removeOnMarkerWidgetIconChangedListener(this);
            miw.removeOnMarkerWidgetIconStateChangedListener(this);
            miw.removeOnMarkerWidgetIconRotationChangedListener(this);
        }
    }

    @Override
    public void onMarkerWidgetIconChanged(IMarkerIconWidget iconWidget) {
        final IIcon icon = iconWidget.getWidgetIcon();
        final int state = iconWidget.getState();
        runOrQueueEvent(new Runnable() {
            @Override
            public void run() {
                _releaseIcon();
                _updateIcon(icon, state);
            }
        });
    }

    @Override
    public void onMarkerWidgetStateChanged(IMarkerIconWidget iconWidget) {
        final IIcon icon = iconWidget.getWidgetIcon();
        final int state = iconWidget.getState();
        runOrQueueEvent(new Runnable() {
            @Override
            public void run() {
                _updateIcon(icon, state);
            }
        });
    }

    @Override
    public void onMarkerWidgetIconRotationChanged(IMarkerIconWidget iconWidget) {
        _rotation = iconWidget.getRotation();
    }

    @Override
    public void drawWidgetContent(DrawState drawState) {
        if (_icon != null) {

            DrawState localDrawState = drawState.clone();
            Matrix.translateM(localDrawState.modelMatrix, 0, _icon.getWidth() / 2f,
                    -_icon.getHeight() / 2f, 0f);
            Matrix.scaleM(localDrawState.modelMatrix, 0, (float) _icon.getWidth() / _pWidth,
                    (float) _icon.getHeight() / _pHeight, 1);

            Matrix.rotateM(localDrawState.modelMatrix,0,_rotation, 0,0,1f);
            Matrix.translateM(localDrawState.modelMatrix, 0, -_icon.getWidth() / 2f,
                    _icon.getHeight() / 2f, 0f);
            Matrix.translateM(localDrawState.modelMatrix, 0, _padding[LEFT], -_padding[TOP],
                    0f);

            Shader shader = getTextureShader();
            int prevProgram = shader.useProgram(true);
            shader.setModelView(localDrawState.modelMatrix);
            shader.setProjection(localDrawState.projectionMatrix);
            GLES30.glEnable(GLES30.GL_BLEND);
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);
            _icon.drawColor(shader, localDrawState.modelMatrix, _color);
            GLES30.glDisable(GLES30.GL_BLEND);
            GLES30.glUseProgram(prevProgram);

            localDrawState.recycle();
        }
    }

    @Override
    public void releaseWidget() {
        runOrQueueEvent(new Runnable() {
            @Override
            public void run() {
                _releaseIcon();
            }
        });
    }

    private void _releaseIcon() {
        if (_icon != null) {
            _icon.release();
            _icon = null;
        }
    }

    private void _setIcon(IIcon icon) {
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

    private void _updateIcon(IIcon icon, int state) {
        if (_icon == null) {
            _setIcon(icon);
        }
        if (icon != null) {
            _color = icon.getColor(state);
            if (_icon != null) {
                GLImageCache.Entry iconEntry = GLRenderGlobals
                        .get(getRenderContext()).getImageCache()
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
