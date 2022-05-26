package com.atakmap.map.layer.feature.datastore.caching;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.HashSet;
import java.util.Set;

import com.atakmap.lang.Unsafe;
import com.atakmap.map.layer.feature.Adapters;
import com.atakmap.map.layer.feature.AttributeSet;
import com.atakmap.map.layer.feature.DataStoreException;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.FeatureCursor;
import com.atakmap.map.layer.feature.FeatureDataStore2;
import com.atakmap.map.layer.feature.FeatureDefinition2;
import com.atakmap.map.layer.feature.FeatureSet;
import com.atakmap.map.layer.feature.FeatureSetCursor;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.FeatureDataStore2.FeatureSetQueryParameters;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.GeometryFactory;
import com.atakmap.map.layer.feature.ogr.style.FeatureStyleParser;
import com.atakmap.nio.Buffers;

/**
 * <PRE>
 * Header
 *   timestamp [8 bytes]
 *   number of features [2 bytes]
 *   reserved [2 bytes]
 *   level [4 bytes]
 *   index [4 bytes]
 *   terminal [1 byte]
 *   reserved [3 byte]
 *   feature records index offset [8 bytes]
 *   feature records table offset [8 bytes]
 *   feature set index offset [8 bytes]
 *   feature set records table offset [8 bytes]   
 *   spatial index offset [8 bytes]
 *   
 * Feature Record Index
 *   feature index record 1...n
 *     
 * Feature Index Record
 *   FID [8 bytes]
 *   file offset [8 bytes]
 *   
 * Feature Records Table
 *   record 1....n
 *   
 * Feature Records Table Record
 *   FID [8 bytes]
 *   version [8 bytes]
 *   timestamp [8 bytes]
 *   name UTF-8 [varying]
 *   Geometry WKB [varying]
 *   Style OGR [varying]
 *   Attributes [varying]
 *   
 * Feature Set Index Record
 *   FSID [8 bytes]
 *   file offset [8 bytes]
 *   
 * Feature Set Records Table
 *   feature set record 1....n
 *   
 * Feature Set Records Table Record
 *   FSID [8 bytes]
 *   version [8 bytes]
 *   provider UTF-8 [varying]
 *   type UTF-8 [varying]
 *   name UTF-8 [varying]
 *   minimum resolution [8 bytes]
 *   maximum resolution [8 bytes]
 *   
 * Spatial Index TBD
 * </PRE>
 * 
 * @author Developer
 *
 */
class CacheFileV1 implements CacheFile.Format {
    public final static CacheFile.Format INSTANCE = new CacheFileV1();
    
    static class Context {
        FileChannel channel;
        ByteOrder endian;
        long headerOffset;
        CacheFile.Metadata metadata;
        long recordsIndexOffset;
        long recordsTableOffset;
        long spatialIndexOffset;
        long featureSetIndexOffset;
        long featureSetTableOffset;
        ByteBuffer buf;
        
        int reads;
        int seeks;
        int writes;
    }
    
    protected CacheFileV1() {}
    

    @Override
    public Object openFormatContext(FileChannel channel, ByteOrder endian) throws IOException {
        Context retval = new Context();
        retval.channel = channel;
        retval.endian = endian;
        retval.headerOffset = channel.position();
        retval.metadata = null;
        retval.recordsIndexOffset = 0xFFFFFFFFFFFFFFFFL;
        retval.recordsTableOffset = 0xFFFFFFFFFFFFFFFFL;
        retval.spatialIndexOffset = 0xFFFFFFFFFFFFFFFFL;
        retval.featureSetIndexOffset = 0xFFFFFFFFFFFFFFFFL;
        retval.featureSetTableOffset = 0xFFFFFFFFFFFFFFFFL;
        retval.buf = null;

        return retval;
    }

    @Override
    public void closeFormatContext(Object context) {
        // no-op
    }

    @Override
    public CacheFile.Metadata readCacheMetadata(Object context) throws IOException {
        Context ctx = (Context)context;
        if(ctx.metadata == null) {
            ensureBuffer(ctx, 8192);
            
            ctx.buf.clear();
            ctx.channel.position(ctx.headerOffset);
            ctx.seeks++;
            ctx.channel.read(ctx.buf);
            ctx.reads++;
            ctx.buf.flip();

            ctx.metadata = new CacheFile.Metadata();
            ctx.metadata.timestamp = ctx.buf.getLong();
            ctx.metadata.numFeatures = ctx.buf.getShort()&0xFFFF;
            ctx.metadata.numFeatureSets = ctx.buf.getShort()&0xFFFF;
            ctx.metadata.level = ctx.buf.getInt();
            ctx.metadata.index = ctx.buf.getInt();
            ctx.metadata.terminal = (ctx.buf.get() != 0x00);
            Buffers.skip(ctx.buf, 3);
            ctx.recordsIndexOffset = ctx.buf.getLong();
            ctx.recordsTableOffset = ctx.buf.getLong();
            ctx.spatialIndexOffset = ctx.buf.getLong();
            ctx.featureSetIndexOffset = ctx.buf.getLong();
            ctx.featureSetTableOffset = ctx.buf.getLong();
        }
        return ctx.metadata;
    }

    

    private static Feature readFeature(Context ctx) throws IOException {
        fillIfNecessary(ctx, 36);
        
        String name = null;
        Geometry geometry = null;
        Style style = null;
        AttributeSet attr = null;

        final long fsid = ctx.buf.getLong();
        final long fid = ctx.buf.getLong();
        final long version = ctx.buf.getLong();
        final long timestamp = ctx.buf.getLong();

        // name
        name = readUTF8String(ctx);
        
        // geometry
        fillIfNecessary(ctx, 4);
        final int geomWkbLen = ctx.buf.getInt();
        if(geomWkbLen > 0) {
            fillIfNecessary(ctx, geomWkbLen);
            
            final int lim = ctx.buf.limit();
            ctx.buf.limit(ctx.buf.position()+geomWkbLen);
            geometry = GeometryFactory.parseWkb(ctx.buf);
            ctx.buf.limit(lim);
        }
        
        // style
        final String ogrStyle = readUTF8String(ctx);
        if(ogrStyle != null) {
            style = FeatureStyleParser.parse2(ogrStyle);
        }
        
        // attributes
        fillIfNecessary(ctx, 4);
        final int numAttributes = ctx.buf.getInt();
        if(numAttributes > 0) {
            attr = new AttributeSet();
            for(int i = 0; i < numAttributes; i++) {
                decodeAttribute(ctx, attr);
            }
        }
        
        return new Feature(fsid, fid, name, geometry, style, attr, timestamp, version);
    }

    @Override
    public Feature getFeature(Object context, int recordIdx) throws IOException {
        final Context ctx = (Context)context;
        if(recordIdx < 0 || recordIdx >= ctx.metadata.numFeatures)
            throw new IllegalArgumentException();
            
        ensureBuffer(ctx, 8192);
        
        ctx.buf.clear();
        ctx.buf.limit(16);
        ctx.channel.position(ctx.recordsIndexOffset + (recordIdx*16));
        ctx.seeks++;
        ctx.channel.read(ctx.buf);
        ctx.reads++;
        ctx.buf.flip();
        
        Buffers.skip(ctx.buf, 8);
        final long offset = ctx.buf.getLong();
        
        ctx.buf.clear();
        ctx.channel.position(offset);
        ctx.seeks++;
        ctx.channel.read(ctx.buf);
        ctx.reads++;
        ctx.buf.flip();
        return readFeature(ctx);
    }

    @Override
    public FeatureCursor getFeatures(Object context) throws IOException {
        final Context ctx = (Context)context;
            
        ensureBuffer(ctx, 8192);
        
        ctx.buf.clear();
        ctx.channel.position(ctx.recordsTableOffset);
        ctx.seeks++;
        ctx.channel.read(ctx.buf);
        ctx.reads++;
        ctx.buf.flip();
        
        return new FeatureCursor() {
            int idx = 0;
            Feature row = null;
            @Override
            public boolean moveToNext() {
                row = null;
                if(idx >= ctx.metadata.numFeatures)
                    return false;
                try {
                    row = readFeature(ctx);
                    idx++;
                    return true;
                } catch(IOException e) {
                    return false;
                }
            }

            @Override
            public void close() {}

            @Override
            public boolean isClosed() {
                return false;
            }

            @Override
            public Object getRawGeometry() {
                return row.getGeometry();
            }

            @Override
            public int getGeomCoding() {
                return GEOM_ATAK_GEOMETRY;
            }

            @Override
            public String getName() {
                return row.getName();
            }

            @Override
            public int getStyleCoding() {
                return STYLE_ATAK_STYLE;
            }

            @Override
            public Object getRawStyle() {
                return row.getStyle();
            }

            @Override
            public AttributeSet getAttributes() {
                return row.getAttributes();
            }

            @Override
            public Feature get() {
                return row;
            }

            @Override
            public long getId() {
                return row.getId();
            }

            @Override
            public long getVersion() {
                return row.getVersion();
            }

            @Override
            public long getFsid() {
                return row.getFeatureSetId();
            }
        };
    }
    
    @Override
    public Feature findFeature(Object context, long fid) throws IOException {
        final Context ctx = (Context)context;

        ensureBuffer(ctx, 8192);
        
        ctx.channel.position(ctx.recordsIndexOffset);
        ctx.seeks++;
        for(int i = 0; i < ctx.metadata.numFeatures; i++) {
            fillIfNecessary(ctx, 16);
            final long recFid = ctx.buf.getLong();
            final long recOff = ctx.buf.getLong();;
            if(recFid == fid) {
                ctx.buf.clear();
                ctx.channel.position(recOff);
                ctx.seeks++;
                ctx.channel.read(ctx.buf);
                ctx.reads++;
                ctx.buf.flip();

                return readFeature(ctx);
            }
        }

        return null;
    }

    @Override
    public void getFIDs(Object context, Set<Long> fids) throws IOException {
        final Context ctx = (Context)context;

        ensureBuffer(ctx, 8192);
        
        ctx.channel.position(ctx.recordsIndexOffset);
        ctx.seeks++;
        for(int i = 0; i < ctx.metadata.numFeatures; i++) {
            fillIfNecessary(ctx, 16);
            final long recFid = ctx.buf.getLong();
            Buffers.skip(ctx.buf, 8);
            fids.add(recFid);
        }
    }

    @Override
    public boolean findIntersectingFeatures(Object context, Set<Long> fids, Geometry filter) {
        // XXX - implement spatial index
        return false;
    }
    
    @Override
    public FeatureSet getFeatureSet(Object context, int index) throws IOException {
        final Context ctx = (Context)context;
        
        ensureBuffer(ctx, 8192);
        
        ctx.channel.position(ctx.featureSetIndexOffset + (index*16));
        ctx.seeks++;
        ctx.buf.clear();
        ctx.buf.limit(16);
        ctx.channel.read(ctx.buf);
        ctx.reads++;
        ctx.buf.flip();
        
        final long idxfsid = ctx.buf.getLong();
        final long fsOffset = ctx.buf.getLong();

        ctx.channel.position(fsOffset);
        ctx.seeks++;
        ctx.buf.clear();
        ctx.channel.read(ctx.buf);
        ctx.reads++;
        ctx.buf.flip();
        
        fillIfNecessary(ctx, 38);
        final long fsid = ctx.buf.getLong();
        final long version = ctx.buf.getLong();
        final String provider = readUTF8String(ctx);
        final String type = readUTF8String(ctx);
        final String name = readUTF8String(ctx);
        fillIfNecessary(ctx, 16);
        final double minRes = ctx.buf.getDouble();
        final double maxRes = ctx.buf.getDouble();
        
        return new FeatureSet(fsid, provider, type, name, minRes, maxRes, version); 
    }

    @Override
    public void writeCache(FileChannel channel, ByteOrder endian, int level, int index, long timestamp, FeatureDataStore2 features, FeatureDataStore2.FeatureQueryParameters params) throws IOException, DataStoreException {
        FeatureSetQueryParameters fsParams = new FeatureSetQueryParameters();
        fsParams.ids = new HashSet<Long>();

        final long headerOff = channel.position();

        int numFeatureRecords = 0;
        
        final long recordsIndexOff;
        final long recordsTableOff = headerOff + 64;
        final long spatialIndexOff = 0xFFFFFFFFFFFFFFFFL;
        final long featureSetIndexOff;
        final long featureSetTableOff;
        
        int limit = Integer.MAX_VALUE;
        if(params != null && params.limit > 0 && params.limit < Integer.MAX_VALUE) {
            params = new FeatureDataStore2.FeatureQueryParameters(params);
            params.limit++;
            limit = params.limit;
        }
        
        long recordsIndexPtr = 0L;
        try {
            recordsIndexPtr = Unsafe.allocate(16*0xFFFF);
            
            Context writeCtx = new Context();
            writeCtx.endian = endian;
            writeCtx.buf = ByteBuffer.allocate(10240);
            writeCtx.buf.order(endian);
            writeCtx.channel = channel;
            
            // write the records
            channel.position(recordsTableOff);
            writeCtx.seeks++;
            
            FeatureCursor results = null;
            try {
                results = features.queryFeatures(params);
                final FeatureDefinition2 defn2 = Adapters.adapt(results);
                long recordsIndexWritePtr = recordsIndexPtr;
                while(results.moveToNext()) {
                    if(numFeatureRecords == 0xFFFF)
                        throw new IllegalArgumentException("Only 65535 records supported");
                    
                    // update the index record
                    Unsafe.setLongs(recordsIndexWritePtr, results.getId(), channel.position()+writeCtx.buf.position());
                    recordsIndexWritePtr += 16;
                    numFeatureRecords++;
                    
                    fsParams.ids.add(results.getFsid());
                                        
                    // write the feature to the records table
                    flushIfNecessary(writeCtx, 8);
                    writeCtx.buf.putLong(results.getFsid());
                    flushIfNecessary(writeCtx, 8);
                    writeCtx.buf.putLong(results.getId());
                    flushIfNecessary(writeCtx, 8);
                    writeCtx.buf.putLong(results.getVersion());
                    flushIfNecessary(writeCtx, 8);
                    writeCtx.buf.putLong(defn2.getTimestamp());
        
                    // name
                    writeUTF8String(writeCtx, results.getName());
                    
                    // geometry
                    if(results.getRawGeometry() == null) {
                        flushIfNecessary(writeCtx, 4);
                        writeCtx.buf.putInt(0);                
                    } else if(results.getGeomCoding() == FeatureCursor.GEOM_WKB) {
                        final byte[] wkb = (byte[])results.getRawGeometry();
                        flushIfNecessary(writeCtx, 4 + wkb.length);
                        writeCtx.buf.putInt(wkb.length);
                        writeCtx.buf.put(wkb);
                    } else {
                        Geometry geometry = results.get().getGeometry();
                        final int wkbSize = geometry.computeWkbSize();
                        flushIfNecessary(writeCtx, 4 + wkbSize);
                        writeCtx.buf.putInt(wkbSize);
                        geometry.toWkb(writeCtx.buf);
                    }
                    
                    // style
                    if(results.getRawStyle() == null) {
                        writeUTF8String(writeCtx, null);
                    } else if(results.getStyleCoding() == FeatureCursor.STYLE_OGR) {
                        writeUTF8String(writeCtx, (String)results.getRawStyle());
                    } else {
                        final Style style = results.get().getStyle();
                        writeUTF8String(writeCtx, FeatureStyleParser.pack(style));
                    }
                    
                    // attributes
                    AttributeSet attr = results.getAttributes();
                    if(attr != null) {
                        Set<String> keys = attr.getAttributeNames();
                        flushIfNecessary(writeCtx, 4);
                        writeCtx.buf.putInt(keys.size());
                        for(String key : keys) {
                            encodeAttribute(writeCtx, attr, key);
                        }
                    } else {
                        flushIfNecessary(writeCtx, 4);
                        writeCtx.buf.putInt(0);
                    }
                }
            } finally {
                if(results != null)
                    results.close();
            }
            
            // flush any outstanding records
            if(writeCtx.buf.position() > 0) {
                writeCtx.buf.flip();
                channel.write(writeCtx.buf);
                writeCtx.writes++;
            }
    
            // capture the records index offset
            recordsIndexOff = channel.position();

            // write the features records index
            writeCtx.buf.clear();
            for(int i = 0; i < numFeatureRecords; i++) {
                flushIfNecessary(writeCtx, 16);
                writeCtx.buf.putLong(Unsafe.getLong(recordsIndexPtr + (i*16)));
                writeCtx.buf.putLong(Unsafe.getLong(recordsIndexPtr + (i*16) + 8));
            }
            if(writeCtx.buf.position() > 0) {
                writeCtx.buf.flip();
                channel.write(writeCtx.buf);
                writeCtx.writes++;
            }
            
            // capture the feature set table offset
            featureSetTableOff = channel.position();
            
            FeatureSetCursor fsResult = null;
            try {
                fsResult = features.queryFeatureSets(fsParams);
                long recordsIndexWritePtr = recordsIndexPtr;
                writeCtx.buf.clear();
                while(fsResult.moveToNext()) {
                    FeatureSet fs = fsResult.get();
                    
                    // update the index record
                    Unsafe.setLongs(recordsIndexWritePtr, fs.getId(), channel.position()+writeCtx.buf.position());
                    recordsIndexWritePtr += 16;

                    flushIfNecessary(writeCtx, 38);
                    writeCtx.buf.putLong(fs.getId());
                    writeCtx.buf.putLong(fs.getVersion());
                    writeUTF8String(writeCtx, fs.getProvider());
                    writeUTF8String(writeCtx, fs.getType());
                    writeUTF8String(writeCtx, fs.getName());
                    writeCtx.buf.putDouble(fs.getMinResolution());
                    writeCtx.buf.putDouble(fs.getMaxResolution());
                }
            } finally {
                if(fsResult != null)
                    fsResult.close();
            }
            if(writeCtx.buf.position() > 0) {
                writeCtx.buf.flip();
                channel.write(writeCtx.buf);
                writeCtx.writes++;
            }

            // capture the feature set index offset
            featureSetIndexOff = channel.position();
            
            // write the feature sets records index
            writeCtx.buf.clear();
            for(int i = 0; i < fsParams.ids.size(); i++) {
                flushIfNecessary(writeCtx, 16);
                writeCtx.buf.putLong(Unsafe.getLong(recordsIndexPtr + (i*16)));
                writeCtx.buf.putLong(Unsafe.getLong(recordsIndexPtr + (i*16) + 8));
            }
            if(writeCtx.buf.position() > 0) {
                writeCtx.buf.flip();
                channel.write(writeCtx.buf);
                writeCtx.writes++;
            }
            
            // write the header
            channel.position(headerOff);
            writeCtx.seeks++;
            writeCtx.buf.clear();
            
            writeCtx.buf.putLong(timestamp); // 8
            writeCtx.buf.putShort((short)numFeatureRecords); // 10
            writeCtx.buf.putShort((short)fsParams.ids.size()); // 12
            writeCtx.buf.putInt(level); // 16
            writeCtx.buf.putInt(index); // 20
            writeCtx.buf.put((numFeatureRecords < limit) ?
                                        (byte)0x01 : (byte)0x00); // 21
            Buffers.skip(writeCtx.buf, 3); // 24 
            writeCtx.buf.putLong(recordsIndexOff); // 32
            writeCtx.buf.putLong(recordsTableOff); // 40
            writeCtx.buf.putLong(spatialIndexOff); // 48
            writeCtx.buf.putLong(featureSetIndexOff); // 56
            writeCtx.buf.putLong(featureSetTableOff); // 64
            writeCtx.buf.flip();
            
            channel.write(writeCtx.buf);
            writeCtx.writes++;
        } finally {
            if(recordsIndexPtr != 0L)
                Unsafe.free(recordsIndexPtr);
        }
    }

    /**************************************************************************/
    
    private static void ensureBuffer(Context ctx, int length) {
        if(ctx.buf == null || ctx.buf.capacity() < length) {
            ctx.buf = ByteBuffer.allocate(length);
            ctx.buf.order(ctx.endian);
        }
    }
    
    private static void fillIfNecessary(Context ctx, int required) throws IOException {
        if(required > ctx.buf.capacity()) {
            // allocate a new buffer that can accommodate the required size and
            // copy the current contents to its head
            ByteBuffer swap = ByteBuffer.allocate(required);
            swap.order(ctx.endian);
            swap.put(ctx.buf);
            // swap the buffer pointers
            ctx.buf = swap;
            
            // fill the buffer with more data
            ctx.buf.limit(ctx.buf.capacity());
            ctx.channel.read(ctx.buf);
            ctx.reads++;
            ctx.buf.flip();
        } else if(required > ctx.buf.remaining()) {
            // shift existing content to the head of the buffer
            ctx.buf.compact();

            // fill the buffer with more data
            ctx.channel.read(ctx.buf);
            ctx.reads++;
            ctx.buf.flip();
        }
        
        if(ctx.buf.remaining() < required)
            throw new EOFException();
    }
    
    private static void flushIfNecessary(Context ctx, int required) throws IOException {
        if(required > ctx.buf.capacity()) {
            // flush the buffer contents to the channel
            ctx.buf.flip();
            ctx.channel.write(ctx.buf);
            ctx.writes++;
            // allocate a new buffer that can accommodate the required size and
            // copy the current contents to its head
            ByteBuffer swap = ByteBuffer.allocate(required);
            swap.order(ctx.endian);
            swap.put(ctx.buf);

            // swap the buffer pointers
            ctx.buf = swap;
        } else if(required > ctx.buf.remaining()) {
            // flush the buffer contents to the channel
            ctx.buf.flip();
            ctx.channel.write(ctx.buf);
            ctx.writes++;

            // clear the buffer to accept more data
            ctx.buf.clear();
        }
        
        if(ctx.buf.remaining() < required)
            throw new IllegalStateException();
    }
    
    private static String readUTF8String(Context ctx) throws IOException {
        fillIfNecessary(ctx, 2);
        final int strLen = ctx.buf.getShort()&0xFFFF;
        if(strLen < 1)
            return null;
        fillIfNecessary(ctx, strLen);
        final int lim = ctx.buf.limit();
        ctx.buf.limit(ctx.buf.position()+strLen);
        StringBuilder retval = new StringBuilder();
        while(ctx.buf.hasRemaining())
            retval.append(ctx.buf.getChar());
        ctx.buf.limit(lim);
        return retval.toString();
    }
    
    private static void writeUTF8String(Context ctx, String str) throws IOException {
        if(str == null) {
            flushIfNecessary(ctx, 2);
            ctx.buf.putShort((short)0);
            return;
        } else {
            final int strlen = str.length();
            flushIfNecessary(ctx, 2 + strlen*2);
            ctx.buf.putShort((short)(strlen*2));
            for(int i = 0; i < strlen; i++)
                ctx.buf.putChar(str.charAt(i));
        }
    }
    
    private static void decodeAttribute(Context ctx, AttributeSet attr) throws IOException {
        final String key = readUTF8String(ctx);
        fillIfNecessary(ctx, 1);
        final int type = ctx.buf.get()&0xFF;
        switch(type) {
            case 0 : // null
                attr.setAttribute(key, (byte[])null);
                break;
            case 1 : // int
                fillIfNecessary(ctx, 4);
                attr.setAttribute(key, ctx.buf.getInt());
                break;
            case 2 : // long
                fillIfNecessary(ctx, 8);
                attr.setAttribute(key, ctx.buf.getLong());
                break;
            case 3 : // double
                fillIfNecessary(ctx, 8);
                attr.setAttribute(key, ctx.buf.getDouble());
                break;
            case 4 : // string
                attr.setAttribute(key, readUTF8String(ctx));
                break;
            case 5 : // byte[]
            {
                fillIfNecessary(ctx, 4);
                byte[] blob = new byte[ctx.buf.getInt()];
                fillIfNecessary(ctx, blob.length);
                ctx.buf.get(blob);
                attr.setAttribute(key, blob);
                break;
            }
            case 11 : // int[]
            {
                fillIfNecessary(ctx, 4);
                int[] arr = new int[ctx.buf.getInt()];
                for(int i = 0; i < arr.length; i++) {
                    fillIfNecessary(ctx, 4);
                    arr[i] = ctx.buf.getInt();
                }
                attr.setAttribute(key, arr);
                break;
            }
            case 12 : // long[]
            {
                fillIfNecessary(ctx, 4);
                long[] arr = new long[ctx.buf.getInt()];
                for(int i = 0; i < arr.length; i++) {
                    fillIfNecessary(ctx, 8);
                    arr[i] = ctx.buf.getLong();
                }
                attr.setAttribute(key, arr);
                break;
            }
            case 13 : // double[]
            {
                fillIfNecessary(ctx, 4);
                double[] arr = new double[ctx.buf.getInt()];
                for(int i = 0; i < arr.length; i++) {
                    fillIfNecessary(ctx, 8);
                    arr[i] = ctx.buf.getDouble();
                }
                attr.setAttribute(key, arr);
                break;
            }
            case 14 : // string[]
            {
                fillIfNecessary(ctx, 4);
                String[] arr = new String[ctx.buf.getInt()];
                for(int i = 0; i < arr.length; i++) {
                    arr[i] = readUTF8String(ctx);
                }
                attr.setAttribute(key, arr);
                break;
            }
            case 15 : // byte[][]
            {
                fillIfNecessary(ctx, 4);
                byte[][] arr = new byte[ctx.buf.getInt()][];
                for(int i = 0; i < arr.length; i++) {
                    fillIfNecessary(ctx, 4);
                    arr[i] = new byte[ctx.buf.getInt()];
                    fillIfNecessary(ctx, arr[i].length);
                    ctx.buf.get(arr[i]);
                }
                attr.setAttribute(key, arr);
                break;
            }
            case 20 : // AttributeSet
            {
                fillIfNecessary(ctx, 4);
                final int numAttributes = ctx.buf.getInt();
                if(numAttributes > 0) {
                    AttributeSet nested = new AttributeSet();
                    for(int i = 0; i < numAttributes; i++) {
                        decodeAttribute(ctx, nested);
                    }
                    attr.setAttribute(key, nested);
                } else {
                    attr.setAttribute(key, (AttributeSet)null);
                }
                break;
            }
            default :
                throw new IllegalArgumentException();
        }
    }
    
    private static void encodeAttribute(Context ctx, AttributeSet attr, String key) throws IOException {
        writeUTF8String(ctx, key);
       
        final Class<?> attrType = attr.getAttributeType(key);
        flushIfNecessary(ctx, 1);
        if(attrType == Integer.TYPE) {
            ctx.buf.put((byte)1);
            flushIfNecessary(ctx, 4);
            ctx.buf.putInt(attr.getIntAttribute(key));
        } else if(attrType == Long.TYPE) {
            ctx.buf.put((byte)2);
            flushIfNecessary(ctx, 8);
            ctx.buf.putLong(attr.getLongAttribute(key));
        } else if(attrType == Double.TYPE) {
            ctx.buf.put((byte)3);
            flushIfNecessary(ctx, 8);
            ctx.buf.putDouble(attr.getDoubleAttribute(key));
        } else if(attrType == String.class) {
            ctx.buf.put((byte)4);
            writeUTF8String(ctx, attr.getStringAttribute(key));
        } else if(attrType == byte[].class) {
            ctx.buf.put((byte)5);
            final byte[] value = attr.getBinaryAttribute(key);
            if(value != null) {
                flushIfNecessary(ctx, 4+value.length);
                ctx.buf.putInt(value.length);
                ctx.buf.put(value);
            } else {
                flushIfNecessary(ctx, 4);
                ctx.buf.putInt(0);
            }
        } else if(attrType == int[].class) {
            ctx.buf.put((byte)11);
            final int[] value = attr.getIntArrayAttribute(key);
            if(value != null) {
                flushIfNecessary(ctx, 4);
                ctx.buf.putInt(value.length);
                for(int i = 0; i < value.length; i++) {
                    flushIfNecessary(ctx, 4);
                    ctx.buf.putInt(value[i]);
                }
            } else {
                flushIfNecessary(ctx, 4);
                ctx.buf.putInt(0);
            }
        } else if(attrType == long[].class) {
            ctx.buf.put((byte)12);
            final long[] value = attr.getLongArrayAttribute(key);
            if(value != null) {
                flushIfNecessary(ctx, 4);
                ctx.buf.putInt(value.length);
                for(int i = 0; i < value.length; i++) {
                    flushIfNecessary(ctx, 8);
                    ctx.buf.putLong(value[i]);
                }
            } else {
                flushIfNecessary(ctx, 4);
                ctx.buf.putInt(0);
            }
        } else if(attrType == double[].class) {
            ctx.buf.put((byte)13);
            final double[] value = attr.getDoubleArrayAttribute(key);
            if(value != null) {
                flushIfNecessary(ctx, 4);
                ctx.buf.putInt(value.length);
                for(int i = 0; i < value.length; i++) {
                    flushIfNecessary(ctx, 8);
                    ctx.buf.putDouble(value[i]);
                }
            } else {
                flushIfNecessary(ctx, 4);
                ctx.buf.putInt(0);
            }
        } else if(attrType == String[].class) {
            ctx.buf.put((byte)14);
            final String[] value = attr.getStringArrayAttribute(key);
            if(value != null) {
                flushIfNecessary(ctx, 4);
                ctx.buf.putInt(value.length);
                for(int i = 0; i < value.length; i++) {
                    writeUTF8String(ctx, value[i]);
                }
            } else {
                flushIfNecessary(ctx, 4);
                ctx.buf.putInt(0);
            }
        } else if(attrType == byte[][].class) {
            ctx.buf.put((byte)15);
            final byte[][] value = attr.getBinaryArrayAttribute(key);
            if(value != null) {
                flushIfNecessary(ctx, 4);
                ctx.buf.putInt(value.length);
                for(int i = 0; i < value.length; i++) {
                    if(value[i] == null) {
                        flushIfNecessary(ctx, 4);
                        ctx.buf.putInt(0);
                    } else {
                        flushIfNecessary(ctx, 4+value[i].length);
                        ctx.buf.putInt(value[i].length);
                        ctx.buf.put(value[i]);
                    }
                }
            } else {
                flushIfNecessary(ctx, 4);
                ctx.buf.putInt(0);
            }
        } else if(attrType == AttributeSet.class) {
            ctx.buf.put((byte)20);
            
            AttributeSet nested = attr.getAttributeSetAttribute(key);
            flushIfNecessary(ctx, 4);
            if(nested != null) {
                final Set<String> keys = nested.getAttributeNames();
                ctx.buf.putInt(keys.size());
                for(String nestedKey : keys) {
                    encodeAttribute(ctx, nested, nestedKey);
                }
            } else {
                ctx.buf.putInt(0);
            }
        } else {
            // assume null
            ctx.buf.put((byte)0);
        }
    }    
}
