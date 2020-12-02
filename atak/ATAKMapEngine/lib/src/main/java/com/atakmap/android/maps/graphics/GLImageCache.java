
package com.atakmap.android.maps.graphics;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import com.atakmap.coremap.log.Log;

import com.atakmap.android.maps.MapDataRef;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLTexture;
import com.atakmap.opengl.GLTextureAtlas;

/**
 * A managed cache of Bitmap to GLImage mappings.
 * 
 * 
 */
public class GLImageCache {

    private final static Matrix FLIP_MATRIX = new Matrix();
    static {
        FLIP_MATRIX.reset();
        FLIP_MATRIX.setScale(1f, -1f);
    }

    public GLImageCache(GLBitmapLoader bitmapLoader, int atlasTextureSize) {
        _bitmapLoader = bitmapLoader;
        _atlas = new GLTextureAtlas(atlasTextureSize);
    }

    public int getAtlasTextureSize() {
        return _atlas.getTextureSize();
    }

    public void dispose() {
        Collection<Entry> entries;
        entries = _atlasItems.values();
        for (Entry e : entries) {
            if (e._pending != null)
                e._pending.cancel(false);
        }
        entries = _textureItems.values();
        for (Entry e : entries) {
            if (e._pending != null)
                e._pending.cancel(false);
        }
        entries = _prefetchCache;
        for (Entry e : entries) {
            if (e._pending != null)
                e._pending.cancel(false);
        }

        _atlasItems.clear();
        _textureItems.clear();
        _prefetchCache.clear();

        _bitmapLoader = null;
        _atlas.release();
    }

    public void release() {
        Collection<Entry> entries;
        entries = _atlasItems.values();
        for (Entry e : entries) {
            if (e._pending != null)
                e._pending.cancel(false);
        }

        entries = _textureItems.values();
        int[] texIds = new int[entries.size()];
        int numTexIds = 0;
        for (Entry e : entries) {
            if (e._pending != null)
                e._pending.cancel(false);
            if(e._textureId != 0)
                texIds[numTexIds++] = e._textureId;
        }
        if(numTexIds > 0)
            GLES20FixedPipeline.glDeleteTextures(numTexIds, texIds, 0);

        entries = _prefetchCache;
        for (Entry e : entries) {
            if (e._pending != null)
                e._pending.cancel(false);
        }

        _atlasItems.clear();
        _textureItems.clear();
        _prefetchCache.clear();

        _atlas.release();
    }
    
    /**
     * <P>
     * All methods should only be invoked on the GL context thread unless specified otherwise.
     * 
     * @author Developer
     */
    public abstract class Entry {
        protected Entry(String uri, Bitmap.Config config) {
            _uri = uri;
            _config = config;
        }

        /**
         * Returns the original width of the image, as decoded.
         * 
         * @return The width of the image
         */
        public final int getImageWidth() {
            if (_textureId == 0 && !_invalid)
                _update(false);
            return _width;
        }

        /**
         * Returns the original height of the image, as decoded.
         * 
         * @return The width of the image
         */
        public final int getImageHeight() {
            if (_textureId == 0 && !_invalid)
                _update(false);
            return _height;
        }

        /**
         * Returns the x offset of the image in the texture.
         * 
         * @return The x-offset of the image in the texture
         */
        public final int getImageTextureX() {
            if (_textureId == 0 && !_invalid)
                _update(false);
            return _imageTexX;
        }

        /**
         * Returns the y offset of the image in the texture.
         * 
         * @return The y-offset of the image in the texture
         */
        public final int getImageTextureY() {
            if (_textureId == 0 && !_invalid)
                _update(false);
            return _imageTexY;
        }

        /**
         * Returns the width of the image in the texture.
         * 
         * @return The width of the image in the texture
         */
        public final int getImageTextureWidth() {
            if (_textureId == 0 && !_invalid)
                _update(false);
            return _imageTexW;
        }

        /**
         * Returns the height of the image in the texture.
         * 
         * @return The height of the image in the texture
         */
        public final int getImageTextureHeight() {
            if (_textureId == 0 && !_invalid)
                _update(false);
            return _imageTexH;
        }

        public final int getTextureId() {
            return this.getTextureId(true);
        }

        protected final int getTextureId(boolean resolve) {
            if (_textureId == 0 && !_invalid)
                _update(resolve);
            return _textureId;
        }

        public final int getTextureWidth() {
            if (_textureId == 0 && !_invalid)
                _update(true);
            return _textureWidth;
        }

        public final int getTextureHeight() {
            if (_textureId == 0 && !_invalid)
                _update(true);
            return _textureHeight;
        }

        public final boolean isPending() {
            if (_textureId == 0 && !_invalid)
                _update(true);
            return _pending != null;
        }

        public final boolean isInvalid() {
            if (_textureId == 0 && !_invalid)
                _update(true);
            return _invalid;
        }

        public final String getUri() {
            return _uri;
        }

        public abstract Entry retain();

        /**
         * Dereferences the entry only deleting it from the cache if the reference count reaches
         * zero. As a convenience, this method always returns null so dereferencing and setting to
         * null is possible in one line (a = a.release()).
         * 
         * @return always null
         */
        public abstract Entry release();

        public final String toString() {
            final int _refs = this.getReferenceCount();
            return ((_refs > 0) ? "+" : "") + _refs + ": " + _uri;// _dataRef;
        }

        protected abstract int getReferenceCount();

        @Override
        protected void finalize() throws Throwable {
            // In a case where this becomes orphaned and able to be cleaned up, release
            // the texture. May not happen.
            try {
                this.release();
            } finally {
                super.finalize();
            }
        }

        /**
         * @param bitmap the bitmap to cache
         */
        protected abstract void updateImpl(final Bitmap bitmap);

        private void _update(boolean resolve) {
            if (_pending == null && resolve) {
                BitmapFactory.Options opts = null;
                if (_config != null) {
                    opts = new BitmapFactory.Options();
                    opts.inPreferredConfig = _config;
                }
                _pending = _bitmapLoader.loadBitmap(_uri, opts);
                _invalid |= (_pending == null);
            }

            if (_pending != null && _pending.isDone()) {
                Bitmap bitmap = null;
                try {
                    bitmap = _pending.get();
                    if (bitmap != null) {
                        Bitmap flippedBitmap = Bitmap.createBitmap(bitmap,
                                                            0,
                                                            0,
                                                            bitmap.getWidth(),
                                                            bitmap.getHeight(),
                                                            FLIP_MATRIX,
                                                            false);
                        bitmap.recycle();
                        bitmap = flippedBitmap;
                    }
                    if (bitmap != null)
                        this.updateImpl(bitmap);
                    else {
                        _invalid = true;
                    }
                } catch (InterruptedException e) {
                    Log.e(TAG, "error: ", e);
                } catch (ExecutionException e) {
                    _invalid = true;
                } catch (CancellationException e) {
                    // ignore
                } finally {
                    if (bitmap != null)
                        bitmap.recycle();
                }
                _pending = null;
            }
        }

        protected String _uri;
        protected int _textureId;
        protected int _textureWidth;
        protected int _textureHeight;
        protected int _imageTexX;
        protected int _imageTexY;
        protected int _imageTexW;
        protected int _imageTexH;
        protected FutureTask<Bitmap> _pending;
        protected boolean _invalid;
        protected int _width;
        protected int _height;
        protected Bitmap.Config _config;
    }

    private class TextureEntry extends Entry {
        private TextureEntry(String uri, Bitmap.Config config) {
            super(uri, config);
        }

        @Override
        public Entry retain() {
            ++_refs;
            return this;
        }

        /**
         * Dereferences the entry only deleting it from the cache if the reference count reaches
         * zero. As a convenience, this method always returns null so dereferencing and setting to
         * null is possible in one line (a = a.release()).
         * 
         * @return always null
         */
        @Override
        public Entry release() {
            if (--_refs <= 0) {
                if (this.getTextureId(false) != 0)
                    _delete(_textureId, false);
                if (_pending != null) {
                    _pending.cancel(false);
                    _pending = null;
                }
            }
            return null;
        }

        @Override
        protected int getReferenceCount() {
            return _refs;
        }

        @Override
        protected void updateImpl(Bitmap flippedBitmap) {
            _width = flippedBitmap.getWidth();
            _height = flippedBitmap.getHeight();

            GLTexture texture = new GLTexture(flippedBitmap.getWidth(),
                    flippedBitmap.getHeight(), flippedBitmap.getConfig());

            GLDebugProfile.reportActive("texture", texture);

            texture.load(flippedBitmap);

            _textureId = texture.getTexId();
            _textureWidth = texture.getTexWidth();
            _textureHeight = texture.getTexHeight();
            _imageTexX = 0;
            _imageTexY = 0;
            _imageTexW = _width;
            _imageTexH = _height;
        }

        private int _refs;
    }

    private class AtlasEntry extends Entry {
        private AtlasEntry(String uri, Bitmap.Config config) {
            super(uri, config);

            final long key = _atlas.getTextureKey(uri);
            if (key != 0L) {
                _textureId = _atlas.getTexId(key);
                _textureWidth = _atlas.getTextureSize();
                _textureHeight = _atlas.getTextureSize();
                _imageTexX = _atlas.getImageTextureOffsetX(key);
                _imageTexY = _atlas.getImageTextureOffsetY(key);
                _imageTexW = _atlas.getImageWidth(key);
                _imageTexH = _atlas.getImageHeight(key);
                _width = _atlas.getImageWidth(key);
                _height = _atlas.getImageHeight(key);
            }
        }

        @Override
        public Entry retain() {
            return this;
        }

        @Override
        public Entry release() {
            return null;
        }

        @Override
        protected int getReferenceCount() {
            return 1;
        }

        @Override
        protected void updateImpl(Bitmap bitmap) {
            final long key = _atlas.addImage(_uri, bitmap);
            _textureId = _atlas.getTexId(key);
            _textureWidth = _atlas.getTextureSize();
            _textureHeight = _atlas.getTextureSize();
            _imageTexX = _atlas.getImageTextureOffsetX(key);
            _imageTexY = _atlas.getImageTextureOffsetY(key);
            _imageTexW = _atlas.getImageWidth(key);
            _imageTexH = _atlas.getImageHeight(key);
            _width = _atlas.getImageWidth(key);
            _height = _atlas.getImageHeight(key);
        }
    }

    public Entry fetchAndRetain(String uri, boolean atlas) {
        return this.fetchAndRetain(uri, null, atlas);
    }

    public Entry fetchAndRetain(String uri, Bitmap.Config config, boolean atlas) {
        // atlas = false;
        return _retain(_fetchOrAdd(uri, config, atlas));
    }

    public Entry tryFetchAndRetain(MapDataRef locator, boolean atlas) {
        return this.tryFetchAndRetain(locator.toUri(), atlas);
    }

    public Entry tryFetchAndRetain(String uri, boolean atlas) {
        return _retain(tryFetch(uri, atlas));
    }

    public Entry tryFetch(String uri, boolean atlas) {
        final Map<String, Entry> items;
        if (atlas)
            items = _atlasItems;
        else
            items = _textureItems;
        return items.get(uri);
    }

    public Entry prefetch(String uri, boolean atlas) {
        Entry e = fetchAndRetain(uri, atlas);
        if (e != null && !_prefetchCache.contains(e)) {
            _prefetchCache.add(e);
        }
        return e;
    }

    private Entry _fetchOrAdd(String uri, Bitmap.Config config, boolean atlas) {
        final Map<String, Entry> items;
        if (atlas)
            items = _atlasItems;
        else
            items = _textureItems;
        Entry entry = items.get(uri);
        if (entry == null) {
            if (atlas) {
                entry = new AtlasEntry(uri, config);
            } else {
                entry = new TextureEntry(uri, config);
            }
            items.put(uri, entry);
        }
        return entry;
    }

    public void logContents(int logPriority) {
        Collection<Entry> entries;
        entries = _atlasItems.values();
        for (Entry e : entries)
            Log.println(logPriority, TAG, "[ATLAS] " + e.toString());
        entries = _textureItems.values();
        for (Entry e : entries)
            Log.println(logPriority, TAG, "[TEXTURE] " + e.toString());
    }

    private static Entry _retain(Entry entry) {
        if (entry != null) {
            entry.retain();
        }
        return entry;
    }

    private void _delete(int textureId, boolean atlas) {
        if (atlas) {
            _atlas.releaseTexture(textureId);
        } else {
            int[] textures = new int[] {
                    textureId
            };
            GLES20FixedPipeline.glDeleteTextures(1, textures, 0);
        }
    }

    public static final String TAG = "GLImageCache";
    private HashMap<String, Entry> _atlasItems = new HashMap<>();
    private HashMap<String, Entry> _textureItems = new HashMap<>();
    private List<Entry> _prefetchCache = new LinkedList<>();

    private GLBitmapLoader _bitmapLoader;
    private GLTextureAtlas _atlas;
}
