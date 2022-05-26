package com.atakmap.map.layer.feature.gpkg;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.GeometryFactory;

/**
 * http://www.geopackage.org/spec110/#gpb_format
 * 
 * @author Developer
 */
public final class GeopackageBlobParser {
    public static Geometry parse(byte[] blob) {
        return parse(ByteBuffer.wrap(blob));
    }
    
    public static Geometry parse(ByteBuffer blob) {
        // GeoPackageBinaryHeader
        final byte magic0 = blob.get();
        final byte magic1 = blob.get();
        if(magic0 != (byte)'G' || magic1 != 'P')
            throw new IllegalArgumentException("geometry does not start with GP - instead starts with: " +
                    (char)magic0 + (char)magic1);
        final byte version = blob.get();
        if(version != (byte)0x00)
            throw new UnsupportedOperationException("unsupported version");
        final byte flags = blob.get();
        final int reserved = ((flags&0xC0)>>6);
        final int geopackageBinaryType = ((flags&0x20)>>5);
        final int emptyGeometryFlag = ((flags&0x10)>>4);
        final int envelopeContentsIndicator = ((flags&0x0E)>>1);
        final int byteOrder = (flags&0x01);

        // empty geometry
        if(emptyGeometryFlag != 0)
            return null;

        if(byteOrder == 0x00)
            blob.order(ByteOrder.BIG_ENDIAN);
        else if(byteOrder == 0x01)
            blob.order(ByteOrder.LITTLE_ENDIAN);
        else
            throw new IllegalStateException();

        final int srs_id = blob.getInt();
        
        // envelope
        switch(envelopeContentsIndicator) {
            case 0 :
                // no envelope
                break;
            case 1 :
                // x, y
                blob.position(blob.position()+(4*8));
                break;
            case 2 :
                // x, y, z
                blob.position(blob.position()+(6*8));
                break;
            case 3 :
                // x, y, m
                blob.position(blob.position()+(6*8));
                break;
            case 4 :
                // x, y, z, m
                blob.position(blob.position()+(8*8));
                break;
            case 5 :
            case 6 :
            case 7 :
                throw new IllegalArgumentException("Invalid envelope contents indicator code");
            default :
                throw new IllegalStateException();
        }
        
        // WKB geometry
        return GeometryFactory.parseWkb(blob);
    }    
}
