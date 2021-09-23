package com.atakmap.map.layer.model.opengl;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Pair;

import com.atakmap.android.maps.graphics.GLBitmapLoader;
import com.atakmap.android.maps.tilesets.graphics.GLPendingTexture;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.model.Material;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.opengl.GLTexture;
import com.atakmap.util.Disposable;
import com.atakmap.util.ReferenceCount;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class MaterialManager implements Disposable {

    private final MapRenderer ctx;
    private final Map<String, Pair<GLPendingTexture, Set<GLMaterial>>> textures = new HashMap<>();
    private final TextureLoader loader;

    public MaterialManager(MapRenderer ctx) {
        this(ctx, 0);
    }
    public MaterialManager(MapRenderer ctx, int maxTextureSize) {
        this(ctx, new GLBitmapLoaderTextureLoader(ctx, maxTextureSize));
    }

    public MaterialManager(MapRenderer ctx, TextureLoader loader) {
        this.ctx = ctx;
        this.loader = loader;
    }

    public GLMaterial load(Material m) {
        final String textureUri = m.getTextureUri();
        if(textureUri == null)
            return new GLMaterial(m, (GLTexture)null);
        synchronized(this.textures) {
            Pair<GLPendingTexture, Set<GLMaterial>> tex = this.textures.get(textureUri);
            if(tex == null) {
                tex = Pair.create(new GLPendingTexture(loader.load(textureUri), null), Collections.newSetFromMap(new IdentityHashMap<GLMaterial, Boolean>()));
                this.textures.put(textureUri, tex);
            }
            final GLMaterial retval = new GLMaterial(m, tex.first);
            tex.second.add(retval);
            return retval;
        }
    }

    public void unload(final GLMaterial m) {
        if(!m.isTextured())
            return;

        synchronized(this.textures) {
            final String textureUri = m.getSubject().getTextureUri();
            final Pair<GLPendingTexture, Set<GLMaterial>> tex = this.textures.get(textureUri);
            if(tex == null) {
                m.dispose();
                return;
            }

            // remove the GLMaterial reference
            tex.second.remove(m);
            if(!tex.second.isEmpty())
                return;

            // there are no longer any loaded materials for the texture, dispose of it
            tex.first.cancel();
            this.textures.remove(textureUri);

            if(ctx.isRenderThread()) {
                if(tex.first.isResolved())
                    tex.first.getTexture().release();
            } else {
                ctx.queueEvent(new Runnable() {
                    public void run() {
                        if(tex.first.isResolved())
                            tex.first.getTexture().release();
                    }
                });
            }
        }
    }

    @Override
    public void dispose() {
        synchronized(this.textures) {
            final Collection<Pair<GLPendingTexture, Set<GLMaterial>>> toDispose = this.textures.values();
            this.textures.clear();

            if(this.ctx.isRenderThread())
                disposeImpl(toDispose);
            else
                this.ctx.queueEvent(new Runnable() {
                    public void run() {
                        disposeImpl(toDispose);
                    }
                });
        }
    }

    static void disposeImpl(Collection<Pair<GLPendingTexture, Set<GLMaterial>>> textures) {
        for(Pair<GLPendingTexture, Set<GLMaterial>> tex : textures) {
            tex.first.cancel();
            if(tex.first.isResolved())
                tex.first.getTexture().release();
        }
    }

    public static ReferenceCount<MaterialManager> sharedInstance(MapRenderer ctx) {
        return sharedInstance(new MaterialManager(ctx));
    }
    public static ReferenceCount<MaterialManager> sharedInstance(MapRenderer ctx, TextureLoader loader) {
        return sharedInstance(new MaterialManager(ctx, loader));
    }
    public static ReferenceCount<MaterialManager> sharedInstance(MaterialManager mgr) {
        return new MaterialManager.Shared(mgr);
    }

    public static interface TextureLoader {
        public FutureTask<Bitmap> load(String uri);
    }

    private static class GLBitmapLoaderTextureLoader implements TextureLoader {
        final GLBitmapLoader loader;
        final int maxTextureSize;

        GLBitmapLoaderTextureLoader(MapRenderer r, int maxTextureSize) {
            this.loader = GLRenderGlobals.get(r).getBitmapLoader();
            this.maxTextureSize = maxTextureSize;
        }

        @Override
        public FutureTask<Bitmap> load(String uri) {
            if(!uri.startsWith("zip://") && FileSystemUtils.isZipPath(uri)) {
                StringBuilder sb = new StringBuilder("zip://");
                final int extIdx = uri.toLowerCase(LocaleUtil.getCurrent())
                        .indexOf(".zip");
                if(extIdx < 0)
                    throw new IllegalStateException();
                sb.append(uri.substring(0, extIdx+4));
                sb.append('!');
                sb.append(uri.substring(extIdx+4));
                uri = sb.toString();
            } else if(uri.indexOf(":/") < 0) {
                uri = "file://" + uri;
            }
            final FutureTask<Bitmap> retval = this.loader.loadBitmap(uri, new BitmapFactory.Options());
            if(retval == null || this.maxTextureSize <= 0)
                return retval;
            return new ConstrainedSizeFutureBitmap(retval, maxTextureSize);
        }

        final static class ConstrainedSizeFutureBitmap extends FutureTask<Bitmap> {
            final FutureTask<Bitmap> retval;
            final int maxTextureSize;

            ConstrainedSizeFutureBitmap(FutureTask<Bitmap> fb, int maxTextureSize) {
                super(fb, null);

                this.retval = fb;
                this.maxTextureSize = maxTextureSize;
            }

            @Override
            public boolean isCancelled() {
                return retval.isCancelled();
            }

            @Override
            public boolean isDone() {
                return retval.isDone();
            }

            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                return retval.cancel(mayInterruptIfRunning);
            }

            @Override
            public Bitmap get() throws InterruptedException, ExecutionException {
                return process(retval.get());
            }

            @Override
            public Bitmap get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                return process(retval.get(timeout, unit));
            }

            private Bitmap process(Bitmap result) {
                if(result == null || (result.getWidth() <= maxTextureSize || result.getHeight() <= maxTextureSize))
                    return result;

                final double scaleX = (double)maxTextureSize / (double)result.getWidth();
                final double scaleY = (double)maxTextureSize / (double)result.getHeight();
                final double scale = Math.min(scaleX, scaleY);

                final Bitmap scaled =  Bitmap.createScaledBitmap(
                        result,
                        (int)Math.min(scale*result.getWidth(), maxTextureSize),
                        (int)Math.min(scale*result.getHeight(), maxTextureSize),
                        true);
                if(scaled != result)
                    result.recycle();
                return scaled;
            }
        }
    }

    final static class Shared extends ReferenceCount<MaterialManager> {

        public Shared(MaterialManager value) {
            super(value);
        }

        @Override
        protected void onDereferenced() {
            value.dispose();

            super.onDereferenced();
        }
    }
}
