/*
 * TileCacheData.java
 *
 * Created on June 7, 2013, 9:53 AM
 */

package com.atakmap.map.layer.raster.tilereader;

/**
 * @author Developer
 */
public abstract class TileCacheData {

    protected TileCacheData() {
    }

    public abstract int getWidth();

    public abstract int getHeight();

    public abstract byte[] getPixelData();

    public abstract int getPixelDataOffset();

    public abstract int getPixelDataLength();

    /**************************************************************************/

    public static interface Compositor {
        public void composite(TileCacheData dst, TileCacheData src, int dstX,
                int dstY, int dstW, int dstH);
    }

    public static interface Allocator {
        public TileCacheData allocate(int width, int height);

        public void deallocate(TileCacheData data);
    }

    public static interface Serializer {
        public TileCacheData deserialize(byte[] blob, int tileWidth,
                int tileHeight);

        public byte[] serialize(TileCacheData part);
    }
}
