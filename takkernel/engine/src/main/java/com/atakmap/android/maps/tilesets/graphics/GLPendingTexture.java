
package com.atakmap.android.maps.tilesets.graphics;

import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import android.graphics.Bitmap;
import android.graphics.Canvas;

import com.atakmap.coremap.log.Log;

import com.atakmap.opengl.GLTexture;

public class GLPendingTexture {


    public static final String TAG = "GLPendingTexture";

    public final static int STATE_PENDING = 0;
    public final static int STATE_RESOLVED = 1;
    public final static int STATE_UNRESOLVED = 3;
    public final static int STATE_CANCELED = 4;

    /**
     * @param futureBitmap
     * @param texture      The texture to be filled. May be replaced when the bitmap has been
     *                     asynchronously loaded if the bitmap's configuration does not match the texture's
     *                     type and format.
     */
    public GLPendingTexture(FutureTask<Bitmap> futureBitmap, GLTexture texture) {
        this(futureBitmap, texture, false);
    }

    public GLPendingTexture(FutureTask<Bitmap> futureBitmap, GLTexture texture, boolean npot) {
        _futureBitmap = futureBitmap;
        _texture = texture;
        _state = STATE_PENDING;
        _nonPowerOf2 = npot;
    }

    public int getState() {
        return _state;
    }

    public boolean isPending() {
        _update(false);
        return _state == STATE_PENDING;
    }

    public boolean isResolved() {
        _update(false);
        return _state == STATE_RESOLVED;
    }

    public boolean isUnresolved() {
        _update(false);
        return _state == STATE_UNRESOLVED;
    }

    public boolean isCanceled() {
        return _state == STATE_CANCELED;
    }

    public void cancel() {
        if (_state == STATE_PENDING) {
            if (_futureBitmap != null) {
                if(_futureBitmap.isDone()) {
                    try {
                        final Bitmap b = _futureBitmap.get();
                        if(b != null)
                            b.recycle();
                    } catch(Throwable ignored) {}
                }
                _futureBitmap.cancel(true);
                _futureBitmap = null;
            }
            _state = STATE_CANCELED;
        }
    }

    public GLTexture getTexture() {
        _update(true);
        return _texture;
    }

    public int getWidth() {
        return _width;
    }

    public int getHeight() {
        return _height;
    }

    private void _update(boolean wait) {
        if (_futureBitmap != null) {
            if (wait || _futureBitmap.isDone()) {

                Bitmap b = null;

                try {
                    b = _futureBitmap.get();
                    if (b != null) {
                        // if the config is null we'll make a copy of the bitmap
                        // with a known config as it is not guaranteed that
                        // GLUtils can get a type/internal format for the bitmap
                        if (b.getConfig() == null) {
                            Bitmap scratch = Bitmap.createBitmap(b.getWidth(), b.getHeight(),
                                    b.hasAlpha() ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565);
                            Canvas canvas = new Canvas(scratch);
                            canvas.drawBitmap(b, 0, 0, null);
                            b.recycle();
                            b = scratch;
                        }
                        
                        final Bitmap.Config config = b.getConfig();
                        // create a new texture if there was no seed texture or
                        // if its format/type is not appropriate for the bitmap
                        if(_texture == null || 
                           GLTexture.getInternalFormat(config) != _texture.getFormat() ||
                           GLTexture.getType(config) != _texture.getType()) {

                            _texture = new GLTexture(b.getWidth(),
                                                     b.getHeight(),
                                                     config,
                                                     _nonPowerOf2);
                        }
                        _texture.load(b);
                        _width = b.getWidth();
                        _height = b.getHeight();

                        _state = STATE_RESOLVED;
                    } else if(_state != STATE_CANCELED){
                        _state = STATE_UNRESOLVED;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "error: ", e);
                    _state = STATE_UNRESOLVED;
                } finally {
                    if (b != null)
                        b.recycle();
                }
                _futureBitmap = null;
            }
        }
    }

    private Future<Bitmap> _futureBitmap;
    private GLTexture _texture;
    private int _state;
    private boolean _nonPowerOf2;
    private int _width;
    private int _height;
}
