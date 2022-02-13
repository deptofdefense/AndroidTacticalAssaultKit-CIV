
package com.atakmap.android.maps.graphics.widgets;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.opengl.GLUtils;
import android.util.Pair;

import com.atakmap.android.maps.graphics.GLTriangle;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.android.widgets.DrawableWidget;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.opengl.GLES20FixedPipeline;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import androidx.annotation.NonNull;
import gov.tak.api.annotation.DeprecatedApi;

/**
 * OpenGL rendering for drawable widget
 * @deprecated use {@link gov.tak.platform.widgets.opengl.GLDrawableWidget}
 */
@Deprecated
@DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
public class GLDrawableWidget extends GLWidget2 implements
        DrawableWidget.OnChangedListener, Drawable.Callback {

    public final static GLWidgetSpi SPI = new GLWidgetSpi() {
        @Override
        public int getPriority() {
            return 2;
        }

        @Override
        public GLWidget create(Pair<MapWidget, GLMapView> arg) {
            final MapWidget subject = arg.first;
            final GLMapView orthoView = arg.second;
            if (subject instanceof DrawableWidget) {
                DrawableWidget drw = (DrawableWidget) subject;
                GLDrawableWidget glMarkerWidget = new GLDrawableWidget(
                        drw, orthoView);
                glMarkerWidget.startObserving(drw);
                return glMarkerWidget;
            } else {
                return null;
            }
        }
    };

    private static final float[] TEXTURE_COORDINATES = new float[] {
            0.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f,
            1.0f, 0.0f
    };

    private static final Buffer TEXCOORD_BUFFER = ByteBuffer
            .allocateDirect(TEXTURE_COORDINATES.length * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
            .put(TEXTURE_COORDINATES).rewind();

    private final DrawableWidget _subject;

    private Drawable _drawable;
    private ColorFilter _colorFilter;
    protected boolean _needsRedraw;
    private Bitmap _bitmap;
    private Canvas _canvas;
    private int[] _textureID;
    private GLTriangle.Fan _quad;

    public GLDrawableWidget(DrawableWidget drw, GLMapView ortho) {
        super(drw, ortho);
        _subject = drw;
        updateDrawable();
    }

    @Override
    public void startObserving(MapWidget subject) {
        super.startObserving(subject);
        _subject.addChangeListener(this);
    }

    @Override
    public void stopObserving(MapWidget subject) {
        super.stopObserving(subject);
        _subject.removeChangeListener(this);
    }

    @Override
    public void onDrawableChanged(DrawableWidget widget) {
        updateDrawable();
    }

    @Override
    public void drawWidgetContent() {
        // Ensure the bitmap size matches the widget size
        checkBitmapSize();

        // Draw the drawable to the scratch bitmap
        if (_needsRedraw) {
            _canvas.drawColor(0, PorterDuff.Mode.CLEAR);
            if (_drawable != null) {
                _drawable.setColorFilter(_colorFilter);
                _drawable.setBounds(0, 0, (int) _width, (int) _height);
                _drawable.draw(_canvas);
            }

            // Need to regenerate the texture every time the bitmap changes
            generateTexture();
        }
        _needsRedraw = false;

        GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);
        GLES20FixedPipeline.glBlendFunc(GLES20FixedPipeline.GL_SRC_ALPHA,
                GLES20FixedPipeline.GL_ONE_MINUS_SRC_ALPHA);

        GLES20FixedPipeline.glEnableClientState(
                GLES20FixedPipeline.GL_TEXTURE_COORD_ARRAY);
        GLES20FixedPipeline.glTexCoordPointer(2, GLES20FixedPipeline.GL_FLOAT,
                0, TEXCOORD_BUFFER);
        GLES20FixedPipeline.glBindTexture(GLES20FixedPipeline.GL_TEXTURE_2D,
                _textureID[0]);

        GLES20FixedPipeline.glPushMatrix();

        // TODO: Remove the need for this unintuitive translate call
        GLES20FixedPipeline.glTranslatef(0, -_pHeight, 0);

        GLES20FixedPipeline.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        _quad.draw();

        GLES20FixedPipeline.glPopMatrix();

        GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_BLEND);
        GLES20FixedPipeline.glBindTexture(GLES20FixedPipeline.GL_TEXTURE_2D, 0);
        GLES20FixedPipeline.glDisableClientState(
                GLES20FixedPipeline.GL_TEXTURE_COORD_ARRAY);
    }

    @Override
    public void releaseWidget() {
        if (_bitmap != null) {
            _bitmap.recycle();
            _bitmap = null;
        }
        if (_textureID != null) {
            GLES20FixedPipeline.glDeleteTextures(1, _textureID, 0);
            _textureID = null;
        }
        if (_quad != null) {
            _quad.release();
            _quad = null;
        }
    }

    protected void updateDrawable() {
        final Drawable dr = _subject.getDrawable();
        final ColorFilter cf = _subject.getColorFilter();
        orthoView.queueEvent(new Runnable() {
            @Override
            public void run() {
                _drawable = dr;
                if (_drawable != null)
                    _drawable.setCallback(GLDrawableWidget.this);
                _colorFilter = cf;
                _needsRedraw = true;
            }
        });
    }

    private void checkBitmapSize() {
        int bWidth = Math.max(1, (int) _width);
        int bHeight = Math.max(1, (int) _height);
        if (_bitmap != null && _bitmap.getWidth() == bWidth
                && _bitmap.getHeight() == bHeight)
            return;

        if (_bitmap != null)
            _bitmap.recycle();

        // Generate bitmap/canvas
        _bitmap = Bitmap.createBitmap(bWidth, bHeight, Bitmap.Config.ARGB_8888);
        _canvas = new Canvas(_bitmap);
        _canvas.scale(1, -1);
        _canvas.translate(0, -bHeight);

        if (_quad != null)
            _quad.release();
        _quad = new GLTriangle.Fan(2, 4);
        _quad.setX(0, 0f);
        _quad.setY(0, 0f);
        _quad.setX(1, 0f);
        _quad.setY(1, _height);
        _quad.setX(2, _width);
        _quad.setY(2, _height);
        _quad.setX(3, _width);
        _quad.setY(3, 0f);

        _needsRedraw = true;
    }

    private void generateTexture() {
        // Generate texture buffer
        if (_textureID != null)
            GLES20FixedPipeline.glDeleteTextures(1, _textureID, 0);

        _textureID = new int[1];
        GLES20FixedPipeline.glGenTextures(1, _textureID, 0);
        GLES20FixedPipeline.glBindTexture(GLES20FixedPipeline.GL_TEXTURE_2D,
                _textureID[0]);
        GLES20FixedPipeline.glTexParameterf(GLES20FixedPipeline.GL_TEXTURE_2D,
                GLES20FixedPipeline.GL_TEXTURE_MAG_FILTER,
                GLES20FixedPipeline.GL_LINEAR);
        GLES20FixedPipeline.glTexParameterf(GLES20FixedPipeline.GL_TEXTURE_2D,
                GLES20FixedPipeline.GL_TEXTURE_MIN_FILTER,
                GLES20FixedPipeline.GL_NEAREST);
        GLES20FixedPipeline.glTexParameterf(GLES20FixedPipeline.GL_TEXTURE_2D,
                GLES20FixedPipeline.GL_TEXTURE_WRAP_S,
                GLES20FixedPipeline.GL_CLAMP_TO_EDGE);
        GLES20FixedPipeline.glTexParameterf(GLES20FixedPipeline.GL_TEXTURE_2D,
                GLES20FixedPipeline.GL_TEXTURE_WRAP_T,
                GLES20FixedPipeline.GL_CLAMP_TO_EDGE);

        GLUtils.texImage2D(GLES20FixedPipeline.GL_TEXTURE_2D, 0, _bitmap, 0);

        GLES20FixedPipeline.glBindTexture(GLES20FixedPipeline.GL_TEXTURE_2D, 0);
    }

    @Override
    public void invalidateDrawable(@NonNull Drawable who) {
        orthoView.queueEvent(new Runnable() {
            @Override
            public void run() {
                _needsRedraw = true;
            }
        });
    }

    @Override
    public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what,
            long when) {
    }

    @Override
    public void unscheduleDrawable(@NonNull Drawable who,
            @NonNull Runnable what) {
    }
}
