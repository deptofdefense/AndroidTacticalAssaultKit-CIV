
package com.atakmap.opengl;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.opengl.GLUtils;

public class GLTextureAtlas {

    private final static Comparator<Rect> HORIZONTAL_FREE_COMPARATOR = new Comparator<Rect>() {
        @Override
        public int compare(Rect r1, Rect r2) {
            int retval = r1.area - r2.area;
            if (retval != 0)
                return retval;
            retval = r1.height - r2.height;
            if (retval != 0)
                return retval;
            return (r1.instance - r2.instance);
        }
    };

    private final static Comparator<Rect> VERTICAL_FREE_COMPARATOR = new Comparator<Rect>() {
        @Override
        public int compare(Rect r1, Rect r2) {
            int retval = r1.area - r2.area;
            if (retval != 0)
                return retval;
            retval = r1.width - r2.width;
            if (retval != 0)
                return retval;
            return (r1.instance - r2.instance);
        }
    };

    private Map<String, Long> uriToKey;
    private final int texSize;
    private int freeIndex;
    private int currentTexId;

    private Map<Long, Rect> keyToIconRect;
    private final boolean splitFreeHorizontal;
    private SortedSet<Rect> freeList;

    private final boolean fixedIconSize;
    private final int iconSize;

    private static final String TAG = "GLTextureAtlas";

    public GLTextureAtlas(int texSize) {
        this(texSize, false);
    }

    public GLTextureAtlas(int texSize, boolean splitHorizontal) {
        this(texSize, 0, splitHorizontal);
    }

    public GLTextureAtlas(int texSize, int iconSize) {
        this(texSize, iconSize, false);
    }

    private GLTextureAtlas(int texSize, int iconSize, boolean splitHorizontal) {
        this.iconSize = iconSize;
        this.texSize = 1 << (int) Math.ceil(Math.log(texSize) / Math.log(2));

        this.fixedIconSize = (iconSize > 0);

        this.uriToKey = new HashMap<String, Long>();
        this.freeIndex = 0;
        this.currentTexId = 0;

        this.splitFreeHorizontal = splitHorizontal;

        Comparator<Rect> comp;
        if (!this.fixedIconSize) {
            this.keyToIconRect = new HashMap<Long, Rect>();

            if (this.splitFreeHorizontal)
                comp = HORIZONTAL_FREE_COMPARATOR;
            else
                comp = VERTICAL_FREE_COMPARATOR;
        } else {
            this.keyToIconRect = null;
            comp = HORIZONTAL_FREE_COMPARATOR;
        }
        this.freeList = new TreeSet<Rect>(comp);
    }

    /**
     * Releases all of the textures associated with this atlas.
     */
    public void release() {
        Set<Integer> texIds = new HashSet<Integer>();
        Iterator<Long> iter = this.uriToKey.values().iterator();
        while (iter.hasNext())
            texIds.add(Integer.valueOf(this.getTexId(iter.next().longValue())));

        this.uriToKey.clear();
        if(this.keyToIconRect != null)
            this.keyToIconRect.clear();

        int[] deleteTexIds = new int[texIds.size()];
        int idx = 0;
        for (Integer texId : texIds)
            deleteTexIds[idx++] = texId.intValue();
        GLES20FixedPipeline.glDeleteTextures(idx, deleteTexIds, 0);
        
        this.freeIndex = 0;
        this.currentTexId = 0;
        this.freeList.clear();
    }

    /**
     * Releases the specified atlas texture.
     * 
     * @param textureId The texture ID
     */
    public void releaseTexture(int textureId) {
        Iterator<Long> iter = this.uriToKey.values().iterator();
        long key;
        while (iter.hasNext()) {
            key = iter.next().longValue();
            if (this.getTexId(key) == textureId) {
                iter.remove();
                this.keyToIconRect.remove(Long.valueOf(key));
            }
        }

        if (this.currentTexId == textureId) {
            this.currentTexId = 0;
            if(this.freeList != null)
                this.freeList.clear();
        }

        int[] textures = new int[] {
                textureId
        };
        GLES20FixedPipeline.glDeleteTextures(1, textures, 0);
    }

    /**
     * Returns the atlas key associated with the specified URI. If the atlas does not contain the
     * image associated with the specified URI, <code>0L</code> is returned.
     * 
     * @param uri The URI
     * @return The key associated with the specified URI, or <code>0L</code> if the atlas does not
     *         contain an entry for the URI.
     */
    public long getTextureKey(String uri) {
        final Long retval = this.uriToKey.get(uri);
        if (retval == null)
            return 0L;
        return retval.longValue();
    }

    /**
     * Returns <code>true</code> if all images in the atlas are of a fixed size, <code>false</code>
     * otherwise.
     * 
     * @return <code>true</code> if all images in the atlas are of a fixed size, <code>false</code>
     *         otherwise.
     */
    public boolean isFixedImageSize() {
        return this.fixedIconSize;
    }

    /**
     * Returns a {@link RectF} describing the bounds of the image associated with the specified key.
     * If the atlas does not contain an entry for the specified key, <code>null</code> will be
     * returned.
     * 
     * @param key The key
     * @return A {@link RectF} describing the bounds of the image associated with the specified key.
     */
    public RectF getImageRect(long key) {
        return this.getImageRect(key, false, new RectF());
    }

    /**
     * Returns a {@link RectF} describing the bounds of the image associated with the specified key.
     * If the atlas does not contain an entry for the specified key, <code>null</code> will be
     * returned.
     * 
     * @param key The key
     * @param normalized If <code>true</code>, the returned {@link RectF} will have its members
     *            normalized as texel values per the size of the texture containing the image.
     * @return A {@link RectF} describing the bounds of the image associated with the specified key.
     */
    public RectF getImageRect(long key, boolean normalized) {
        return this.getImageRect(key, normalized, new RectF());
    }

    /**
     * Returns a {@link RectF} describing the bounds of the image associated with the specified key.
     * If the atlas does not contain an entry for the specified key, <code>null</code> will be
     * returned.
     * 
     * @param key The key
     * @param normalized If <code>true</code>, the returned {@link RectF} will have its members
     *            normalized as texel values per the size of the texture containing the image.
     * @param rect An optionally pre-allocated {@link RectF} instance that will have its members set
     *            to the bounds of the image associated with the key. If <code>null</code> is
     *            specified, a new instance will be allocated and returned.
     * @return A {@link RectF} describing the bounds of the image associated with the specified key.
     */
    public RectF getImageRect(long key, boolean normalized, RectF rect) {
        if (rect == null)
            rect = new RectF();

        if (this.fixedIconSize) {
            final int index = this.getIndex(key);
            final int numIconCols = (this.texSize / this.iconSize);
            rect.top = (index / (float)numIconCols) * this.iconSize;
            rect.left = (index % numIconCols) * this.iconSize;
            rect.bottom = rect.top + this.iconSize - 1;
            rect.right = rect.left + this.iconSize - 1;
        } else {
            Rect r = this.keyToIconRect.get(Long.valueOf(key));
            if (r == null)
                return null;
            rect.set(r.x, r.y, r.x + r.width, r.y + r.height);
        }

        if (normalized) {
            rect.left /= (float) this.texSize;
            rect.top /= (float) this.texSize;
            rect.right /= (float) this.texSize;
            rect.bottom /= (float) this.texSize;
        }

        return rect;
    }

    /**
     * Returns the width of the image associated with the specified key.
     * 
     * @param key The key
     * @return The width of the image associated with the specified key or <code>0</code> if the
     *         atlas does not contain an entry for the key.
     */
    public int getImageWidth(long key) {
        if (this.fixedIconSize) {
            return this.iconSize;
        } else {
            Rect r = this.keyToIconRect.get(Long.valueOf(key));
            if (r == null)
                return 0;
            return r.width;
        }
    }

    /**
     * Returns the height of the image associated with the specified key.
     * 
     * @param key The key
     * @return The height of the image associated with the specified key or <code>0</code> if the
     *         atlas does not contain an entry for the key.
     */
    public int getImageHeight(long key) {
        if (this.fixedIconSize) {
            return this.iconSize;
        } else {
            Rect r = this.keyToIconRect.get(Long.valueOf(key));
            if (r == null)
                return 0;
            return r.height;
        }
    }

    /**
     * Returns the size of the textures used by the atlas. The value returned by this method should
     * be used to compute texel positions.
     * 
     * @return The size of the textures used by the atlas.
     */
    public int getTextureSize() {
        return this.texSize;
    }

    /**
     * Returns the texture ID containing the data for the image associated with the specified key.
     * 
     * @param key The key
     * @return The texture ID containing the data for the image associated with the specified key.
     */
    public int getTexId(long key) {
        return (int) ((key >> 32L) & 0xFFFFFFFFL);
    }

    /**
     * Returns the index for the entry containing the data for the image associated with the
     * specified key. The result of this method can be used to compute the bounds of the image for
     * fixed image size atlases.
     * 
     * @param key The key
     * @return The index for the entry containing the data for the image associated with the
     *         specified key.
     */
    public int getIndex(long key) {
        return (int) (key & 0xFFFFFFFFL);
    }

    /**
     * Returns the x-coordinate of the origin of the image in the texture.
     * 
     * @param key The key
     * @return The x-coordinate of the origin of the image in the texture or <code>0</code> if the
     *         atlas does not contain an entry for the specified key.
     */
    public int getImageTextureOffsetX(long key) {
        if (this.fixedIconSize) {
            final int index = this.getIndex(key);
            final int numIconCols = (this.texSize / this.iconSize);
            return (index % numIconCols) * this.iconSize;
        } else {
            Rect r = this.keyToIconRect.get(key);
            if (r == null)
                return 0;
            return r.x;
        }
    }

    /**
     * Returns the y-coordinate of the origin of the image in the texture.
     * 
     * @param key The key
     * @return The y-coordinate of the origin of the image in the texture or <code>0</code> if the
     *         atlas does not contain an entry for the specified key.
     */
    public int getImageTextureOffsetY(long key) {
        if (this.fixedIconSize) {
            final int index = this.getIndex(key);
            final int numIconCols = (this.texSize / this.iconSize);
            return (index / numIconCols) * this.iconSize;
        } else {
            Rect r = this.keyToIconRect.get(Long.valueOf(key));
            if (r == null)
                return 0;
            return r.y;
        }
    }

    /**
     * Adds the specified image to the atlas. The image will be resized if the atlas uses fixed size
     * images and the dimensions of the bitmap are not equal to the fixed size specified during
     * instantiation.
     * 
     * @param uri The URI associated with the image
     * @param bitmap The image data
     * @return The atlas key for the entry that was created.
     */
    public long addImage(String uri, Bitmap bitmap) {
        Bitmap icon = bitmap;
        try {
            final int iconWidth = this.fixedIconSize ? this.iconSize : bitmap.getWidth();
            final int iconHeight = this.fixedIconSize ? this.iconSize : bitmap.getHeight();
            final Bitmap.Config config = bitmap.getConfig();

            // make the icon compatible if it needs to be resized or if the
            // source config is not ARGB8888
            if(iconWidth != bitmap.getWidth() ||
               iconHeight != bitmap.getHeight() ||
               config != Bitmap.Config.ARGB_8888) {

                // XXX - don't use Bitmap.createScaledBitmap; see bug 2045
                Bitmap compatible = Bitmap.createBitmap(iconWidth, iconHeight,
                        Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(compatible);
                canvas.drawBitmap(icon,
                        new android.graphics.Rect(0, 0, bitmap.getWidth(), bitmap.getHeight()),
                        new android.graphics.Rect(0, 0, iconWidth, iconHeight),
                        null);
                icon = compatible;
            } else if (bitmap.getWidth() > this.texSize || bitmap.getHeight() > this.texSize) {
                throw new IllegalArgumentException("bitmap width(" + bitmap.getWidth() +") or height(" + bitmap.getHeight() + ") exeeds the texSize(" + this.texSize + ")");
            }

            // allocate a new texture if the current is filled
            if (this.fixedIconSize) {
                final int numIcons = (this.texSize / this.iconSize);
                if (this.freeIndex == (numIcons * numIcons))
                    this.currentTexId = 0;
            } else {
                SortedSet<Rect> tail = this.freeList.tailSet(new Rect(0, 0, icon.getWidth(), icon
                        .getHeight()));
                boolean haveFree = false;
                for (Rect r : tail) {
                    if (r.width >= icon.getWidth() && r.height >= icon.getHeight()) {
                        haveFree = true;
                        break;
                    }
                }
                if (!haveFree)
                    this.currentTexId = 0;
            }

            final int[] boundTexId = new int[1];
            GLES20FixedPipeline.glGetIntegerv(GLES20FixedPipeline.GL_TEXTURE_BINDING_2D, boundTexId, 0);

            if (this.currentTexId == 0) {
                int[] id = new int[1];
                GLES20FixedPipeline.glGenTextures(1, id, 0);
                if (id[0] == 0)
                    throw new RuntimeException("Failed to generate new texture id");
                this.currentTexId = id[0];

                GLES20FixedPipeline.glBindTexture(GLES20FixedPipeline.GL_TEXTURE_2D,
                        this.currentTexId);
                GLES20FixedPipeline.glTexParameterf(GLES20FixedPipeline.GL_TEXTURE_2D,
                        GLES20FixedPipeline.GL_TEXTURE_MAG_FILTER, GLES20FixedPipeline.GL_LINEAR);
                GLES20FixedPipeline.glTexParameterf(GLES20FixedPipeline.GL_TEXTURE_2D,
                        GLES20FixedPipeline.GL_TEXTURE_MIN_FILTER, GLES20FixedPipeline.GL_NEAREST);
                GLES20FixedPipeline
                        .glTexParameterf(GLES20FixedPipeline.GL_TEXTURE_2D,
                                GLES20FixedPipeline.GL_TEXTURE_WRAP_S,
                                GLES20FixedPipeline.GL_CLAMP_TO_EDGE);
                GLES20FixedPipeline
                        .glTexParameterf(GLES20FixedPipeline.GL_TEXTURE_2D,
                                GLES20FixedPipeline.GL_TEXTURE_WRAP_T,
                                GLES20FixedPipeline.GL_CLAMP_TO_EDGE);
                GLES20FixedPipeline.glTexImage2D(GLES20FixedPipeline.GL_TEXTURE_2D, 0,
                        GLES20FixedPipeline.GL_RGBA,
                        this.texSize, this.texSize, 0,
                        GLES20FixedPipeline.GL_RGBA, GLES20FixedPipeline.GL_UNSIGNED_BYTE, null);

                this.freeIndex = 0;
                if (!this.fixedIconSize) {
                    this.freeList.clear();
                    this.freeList.add(new Rect(0, 0, this.texSize, this.texSize));
                }
            } else {
                GLES20FixedPipeline.glBindTexture(GLES20FixedPipeline.GL_TEXTURE_2D,
                        this.currentTexId);
            }

            final long retval = ((long) this.currentTexId << 32L)
                    | ((long) this.freeIndex & 0xFFFFFFFFL);
            if (this.fixedIconSize) {
                final int numIconCols = (this.texSize / this.iconSize);
                final int x = (this.freeIndex % numIconCols) * this.iconSize;
                final int y = (this.freeIndex / numIconCols) * this.iconSize;

                GLUtils.texSubImage2D(GLES20FixedPipeline.GL_TEXTURE_2D, 0, x, y, icon);
            } else {
                Rect iconR = new Rect(0, 0, icon.getWidth(), icon.getHeight());
                SortedSet<Rect> tail = this.freeList.tailSet(iconR);
                Iterator<Rect> iter = tail.iterator();
                Rect free = null;
                while (iter.hasNext()) {
                    free = iter.next();
                    if (free.width >= icon.getWidth() && free.height >= icon.getHeight()) {
                        iter.remove();
                        break;
                    }
                    free = null;
                }
                // the icon is smaller than the texture and we have already made
                // sure that there is enough room to accommodate it
                if (free == null) {
                    throw new IllegalStateException("free is null");
                }
                
                // update to reflect the position of the free region
                iconR.x = free.x;
                iconR.y = free.y;
                // subdivide the free region, favoring a vertical or horizontal
                // split
                if (this.splitFreeHorizontal) {
                    if (free.width > icon.getWidth())
                        this.freeList.add(new Rect(free.x + icon.getWidth(), free.y, free.width
                                - icon.getWidth(), free.height));
                    if (free.height > icon.getHeight())
                        this.freeList.add(new Rect(free.x, free.y + icon.getHeight(), icon
                                .getWidth(), free.height - icon.getHeight()));
                } else {
                    if (free.height > icon.getHeight())
                        this.freeList.add(new Rect(free.x, free.y + icon.getHeight(), free.width,
                                free.height - icon.getHeight()));
                    if (free.width > icon.getWidth())
                        this.freeList.add(new Rect(free.x + icon.getWidth(), free.y, free.width
                                - icon.getWidth(), icon.getHeight()));
                }

                GLUtils.texSubImage2D(GLES20FixedPipeline.GL_TEXTURE_2D, 0, iconR.x, iconR.y, icon);

                this.keyToIconRect.put(Long.valueOf(retval), iconR);
            }

            this.uriToKey.put(uri, Long.valueOf(retval));

            this.freeIndex++;

            GLES20FixedPipeline.glBindTexture(GLES20FixedPipeline.GL_TEXTURE_2D, boundTexId[0]);

            return retval;
        } finally {
            if (icon != bitmap)
                icon.recycle();
        }

    }

    /**************************************************************************/

    private static class Rect {
        private static int nextInstance = 0;

        private int instance;
        public int x;
        public int y;
        public int width;
        public int height;
        public int area;

        public Rect(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            
            this.area = this.width*this.height;

            this.instance = nextInstance++;
        }
    }
}
