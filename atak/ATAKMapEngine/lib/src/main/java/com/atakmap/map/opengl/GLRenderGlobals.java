package com.atakmap.map.opengl;

import java.util.IdentityHashMap;
import java.util.Map;

import com.atakmap.R;
import com.atakmap.android.maps.graphics.GLBitmapLoader;
import com.atakmap.android.maps.graphics.GLImageCache;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.MapRenderer;
import com.atakmap.opengl.GLNinePatch;
import com.atakmap.opengl.GLTextureAtlas;
import com.atakmap.opengl.GLTextureCache;
import com.atakmap.util.ConfigOptions;
import com.atakmap.util.ReferenceCount;

public final class GLRenderGlobals {
 
    private final static String TAG = "GLRenderGlobals";

    private final static Map<GLMapSurface, ReferenceCount<GLRenderGlobals>> instances = new IdentityHashMap<GLMapSurface, ReferenceCount<GLRenderGlobals>>();

    private GLMapSurface surface;

    private GLTextureCache textureCache;
    private GLImageCache imageCache;
    private GLTextureAtlas nominalIconAtlas;
    private GLTextureAtlas genericAtlas;
    private GLBitmapLoader bitmapLoader;
    private GLNinePatch smallNinePatch;
    private GLNinePatch mediumNinePatch;
    
    private GLRenderGlobals(GLMapSurface surface) {
        this.surface = surface;

        this.textureCache = null;
        this.imageCache = null;
        this.nominalIconAtlas = null;
        this.genericAtlas = null;
        this.bitmapLoader = null;
    }
    
    public synchronized GLTextureCache getTextureCache() {
        if(this.textureCache == null)
            this.textureCache = new GLTextureCache(ConfigOptions.getOption("texturecache.default-size", 100*1024*1024));
        return this.textureCache;
    }
    
    public synchronized GLImageCache getImageCache() {
        if(this.imageCache == null)
            this.imageCache = new GLImageCache(this.getBitmapLoader(), 512);
        return this.imageCache;
    }
    
    public synchronized GLTextureAtlas getIconTextureAtlas() {
        throw new UnsupportedOperationException();
    }
    
    public synchronized GLTextureAtlas getGenericTextureAtlas() {
        throw new UnsupportedOperationException();
    }
    
    public synchronized GLBitmapLoader getBitmapLoader() {
        if(this.bitmapLoader == null)
            this.bitmapLoader = new GLBitmapLoader(this.surface, 1, Thread.MIN_PRIORITY);
        return this.bitmapLoader;
    }

    synchronized void dispose() {
        if(this.bitmapLoader != null) {
            this.bitmapLoader.shutdown();
            this.bitmapLoader = null;
        }
        if(this.imageCache != null) {
            this.imageCache.dispose();
            this.imageCache = null;
        }
    }
    
    public synchronized GLNinePatch getMediumNinePatch() {
        if (this.mediumNinePatch == null)
            this.mediumNinePatch = new GLNinePatch(32, 32);
        return this.mediumNinePatch;
    }

    public synchronized GLNinePatch getSmallNinePatch() {
        if (this.smallNinePatch == null) {
            GLImageCache.Entry cacheEntry = getImageCache().fetchAndRetain(
                    "resource://" + R.drawable.nine_patch_small, false);
            this.smallNinePatch = new GLNinePatch(cacheEntry,
                    16,
                    16,
                    5, 5,
                    10, 10);
        }
        return this.smallNinePatch;
    }

    /*************************************************************************/

    public static synchronized GLRenderGlobals get(MapRenderer renderer) {
        if(!(renderer instanceof GLMapView)) {
            Log.w(TAG, "Only GLMapView renderers may have associated GLRenderGlobals");
            return null;
        }

        final GLMapSurface surface = ((GLMapView)renderer).getSurface();
        return get(surface);
    }

    public static synchronized GLRenderGlobals get(GLMapSurface surface) {
        ReferenceCount<GLRenderGlobals> ref = instances.get(surface);
        if(ref == null)
            instances.put(surface, ref=new ReferenceCount<GLRenderGlobals>(new GLRenderGlobals(surface)));
        else
            ref.reference();
        return ref.value;
    }
    
    static synchronized GLRenderGlobals peek(GLMapSurface surface) {
        ReferenceCount<GLRenderGlobals> ref = instances.get(surface);
        if(ref == null)
            return null;
        return ref.value;
    }
    
    static void dispose(GLMapSurface surface) {
        instances.remove(surface);
    }
    
}
