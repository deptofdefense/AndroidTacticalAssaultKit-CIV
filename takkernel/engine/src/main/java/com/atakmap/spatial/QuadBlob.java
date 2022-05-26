package com.atakmap.spatial;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.math.MathUtils;

/**
 * Convenience class for generating SpatiaLite Geometry blobs for quadrilaterals
 * (geometry type POLYGON).
 * 
 * <P>This class is NOT thread-safe.
 * 
 * @author Developer
 */
public final class QuadBlob {
    private final static int QUAD_BLOB_SIZE = 132;

    private ByteBuffer quad;
    private byte blobEndian;

    public QuadBlob() {
        this(ByteOrder.nativeOrder());
    }

    public QuadBlob(ByteOrder endian) {
        this.quad = null;
        
        this.blobEndian = (endian == ByteOrder.BIG_ENDIAN) ? (byte)0x00 : (byte)0x01;
    }

    /**
     * Returns the SpatiaLite geometry equivalent to the quadrilateral specified
     * by the four points. The returned array is <I>live</I> and must be copied
     * if this method is expected to be called again before use of the data is
     * complete.
     */
    public byte[] getBlob(GeoPoint a, GeoPoint b, GeoPoint c, GeoPoint d) {
        if(this.quad == null) {
            this.quad = ByteBuffer.wrap(new byte[QUAD_BLOB_SIZE]);
            
            this.quad.order(ByteOrder.nativeOrder());
            
            this.quad.put((byte)0x00);
            this.quad.put(blobEndian);
            this.quad.putInt(4326);
            this.quad.position(this.quad.position()+32);
            this.quad.put((byte)0x7C);
            this.quad.putInt(0x03);
            
            this.quad.putInt(1);
            this.quad.putInt(5);
            
            this.quad.put(QUAD_BLOB_SIZE-1, (byte)0xFE);
        }

        final double alat = a.getLatitude();
        final double alng = a.getLongitude();
        final double blat = b.getLatitude();
        final double blng = b.getLongitude();
        final double clat = c.getLatitude();
        final double clng = c.getLongitude();
        final double dlat = d.getLatitude();
        final double dlng = d.getLongitude();
        
        this.quad.putDouble(51, alng);
        this.quad.putDouble(59, alat);
        this.quad.putDouble(67, blng);
        this.quad.putDouble(75, blat);
        this.quad.putDouble(83, clng);
        this.quad.putDouble(91, clat);
        this.quad.putDouble(99, dlng);
        this.quad.putDouble(107, dlat);
        this.quad.putDouble(115, alng);
        this.quad.putDouble(123, alat);

        final double mbrMinX = MathUtils.min(alng, blng, clng, dlng);
        final double mbrMinY = MathUtils.min(alat, blat, clat, dlat);
        final double mbrMaxX = MathUtils.max(alng, blng, clng, dlng);
        final double mbrMaxY = MathUtils.max(alat, blat, clat, dlat);
        
        this.quad.putDouble(6, mbrMinX);
        this.quad.putDouble(14, mbrMinY);
        this.quad.putDouble(22, mbrMaxX);
        this.quad.putDouble(30, mbrMaxY);
        
        return this.quad.array();
    }
}
