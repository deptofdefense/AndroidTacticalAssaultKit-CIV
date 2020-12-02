package com.atakmap.map.opengl;

import android.content.Context;
import android.opengl.GLES30;

import android.graphics.Bitmap;

import java.util.IdentityHashMap;
import java.util.Map;

import com.atakmap.android.maps.graphics.GLBitmapLoader;
import com.atakmap.android.maps.graphics.GLImageCache;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.RenderContext;
import com.atakmap.opengl.GLNinePatch;
import com.atakmap.opengl.GLTexture;
import com.atakmap.opengl.GLTextureAtlas;
import com.atakmap.opengl.GLTextureCache;
import com.atakmap.util.ConfigOptions;
import com.atakmap.util.ReferenceCount;

public class GLRenderGlobals {
 
    private final static String TAG = "GLRenderGlobals";

    protected final static Map<RenderContext, ReferenceCount<GLRenderGlobals>> instances = new IdentityHashMap<>();

    public static Context appContext;

    private RenderContext context;

    private GLTextureCache textureCache;
    private GLImageCache imageCache;
    private GLTextureAtlas nominalIconAtlas;
    private GLTextureAtlas genericAtlas;
    private GLBitmapLoader bitmapLoader;
    protected GLNinePatch smallNinePatch;
    protected GLNinePatch mediumNinePatch;
    private GLTexture pixel;

    private static float relativeScaling = 1f;
    private static int maxTextureUnits = 0;
    private static boolean limitTextureUnits = false;

    protected GLRenderGlobals(RenderContext surface) {
        this.context = surface;

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
            this.bitmapLoader = new GLBitmapLoader(this.context, 1, Thread.MIN_PRIORITY);
        return this.bitmapLoader;
    }

    public synchronized GLTexture getWhitePixel() {
        if(this.pixel == null) {
            this.pixel = new GLTexture(1, 1, Bitmap.Config.RGB_565);
            this.pixel.load(Bitmap.createBitmap(new int[] {-1}, 1, 1, Bitmap.Config.RGB_565));
        }
        return this.pixel;
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
        if (this.smallNinePatch == null && appContext != null) {
            int id = appContext.getResources().getIdentifier("nine_patch_small", "drawable", appContext.getPackageName());
            GLImageCache.Entry cacheEntry = getImageCache().fetchAndRetain(
                    "resource://" + id, false);
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
        if(appContext == null)
            appContext = findContext(renderer);
        return get(com.atakmap.map.LegacyAdapters.getRenderContext(renderer));
    }

    public static synchronized GLRenderGlobals get(RenderContext surface) {
        if(appContext == null)
            appContext = findContext(surface);

        ReferenceCount<GLRenderGlobals> ref = instances.get(surface);
        if(ref == null)
            instances.put(surface, ref=new ReferenceCount<GLRenderGlobals>(new GLRenderGlobals(surface)));
        else
            ref.reference();
        return ref.value;
    }
    
    static synchronized GLRenderGlobals peek(RenderContext surface) {
        ReferenceCount<GLRenderGlobals> ref = instances.get(surface);
        if(ref == null)
            return null;
        return ref.value;
    }
    
    static void dispose(RenderContext surface) {
        instances.remove(surface);
    }

    public static void setRelativeScaling(float v) {
        relativeScaling = v;
    }

    public static float getRelativeScaling() {
        return relativeScaling;
    }

    public static int getMaxTextureUnits() {
        if (maxTextureUnits == 0) {
            int[] i = new int[1];
            GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_IMAGE_UNITS, i, 0);
            maxTextureUnits = i[0];
        }
        return maxTextureUnits;
    }

    public static void setMaxTextureUnits(int lim) {
        maxTextureUnits = lim;
    }

    public static void setLimitTextureUnits(boolean l) {
        limitTextureUnits = l;
    }

    public static boolean isLimitTextureUnits() {
        return limitTextureUnits;
    }

    private static Context findContext(Object o) {
        if(o == null)
            return null;
        Context ctx = null;
        do {
            if (o instanceof GLMapSurface) {
                ctx = ((GLMapSurface)o).getContext();
                if(ctx == null)
                    break;
            } else if(o instanceof GLMapView) {
                o = ((GLMapView)o).getSurface();
                if(o != null)
                    continue;
            }
            break;
        } while(true);
        return ctx;
    }
}
