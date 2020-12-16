package com.atakmap.map.layer.feature.datastore.caching;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.map.layer.feature.AttributeSet;
import com.atakmap.map.layer.feature.DataStoreException;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.FeatureCursor;
import com.atakmap.map.layer.feature.FeatureDataStore2;
import com.atakmap.map.layer.feature.FeatureDataStore2.FeatureQueryParameters;
import com.atakmap.map.layer.feature.FeatureSet;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.nio.Buffers;
import com.atakmap.util.Disposable;

/**
 * magic number [0x54414B464541545552454341434845]
 * reserved [1 byte]
 * byte order [1 byte]
 * reserved [1 byte]
 * version [2 bytes]
 * client version [4 bytes]
 * cache data [var]
 */
public final class CacheFile implements Disposable {

    final static byte[] MAGIC_NUMBER = new byte[]
            {
                (byte)0x54,
                (byte)0x41,
                (byte)0x4B,
                (byte)0x46,
                (byte)0x45,
                (byte)0x41,
                (byte)0x54,
                (byte)0x55,
                (byte)0x52,
                (byte)0x45,
                (byte)0x43,
                (byte)0x41,
                (byte)0x43,
                (byte)0x48,
                (byte)0x45,
            };

    static class Metadata {
        int numFeatures;
        int numFeatureSets;
        long timestamp;
        int level;
        int index;
        boolean terminal;
    }

    static interface Format {
        public Object openFormatContext(FileChannel channel, ByteOrder endian) throws IOException;
        public void closeFormatContext(Object context);
        
        public Metadata readCacheMetadata(Object context) throws IOException;
        public Feature getFeature(Object context, int recordIdx) throws IOException;
        public FeatureCursor getFeatures(Object context) throws IOException;
        public Feature findFeature(Object context, long fid) throws IOException;
        public void getFIDs(Object context, Set<Long> fids) throws IOException;
        public boolean findIntersectingFeatures(Object context, Set<Long> fids, Geometry filter);
        public FeatureSet getFeatureSet(Object context, int index) throws IOException;

        public void writeCache(FileChannel channel, ByteOrder endian, int level, int index, long timestamp, FeatureDataStore2 features, FeatureQueryParameters params) throws IOException, DataStoreException;
    }

    private final static int CURRENT_VERSION = 2;
    private final static Map<Integer, Format> FORMATS = new HashMap<Integer, Format>();
    static {
        FORMATS.put(1, CacheFileV1.INSTANCE);
        FORMATS.put(2, CacheFileV1.INSTANCE);
    }

    private int clientVersion;
    private Metadata metadata;
    private FileChannel channel;
    private Format format;
    private Object context;

    private CacheFile(int clientVersion, Metadata metadata, Format format, Object context, FileChannel channel) {
        this.clientVersion = clientVersion;
        this.metadata = metadata;
        this.format = format;
        this.context = context;
        this.channel = channel;
    }

    public int getClientVersion() {
        return this.clientVersion;
    }
    /**
     * Returns a flag indicating whether the contents of the data store, as
     * filtered during cache file creation, were limited or represent the full
     * data holdings that satisfy the filter, ignoring any limit.
     *  
     * @return  <code>true</code> if the cache contents reflect a limited view
     *          of the content in the data store per the creation filter or
     *          <code>false</code> if the cached data represents all content
     *          that satisfied the filter.
     */
    public boolean contentExceedsLimit() {
        return !this.metadata.terminal;
    }

    public long getTimestamp() {
        return this.metadata.timestamp;
    }
    
    public int getLevel() {
        return this.metadata.level;
    }
    
    public int getIndex() {
        return this.metadata.index;
    }
    
    public int getNumFeatures() {
        return this.metadata.numFeatures;
    }

    public Feature getFeature(int recordIndex) throws IOException {
        return this.format.getFeature(this.context, recordIndex);
    }
    
    public FeatureCursor getFeatures() throws IOException {
        return new FeatureCursor() {
            int rowIdx = 0;
            Feature row = null;

            @Override
            public boolean moveToNext() {
                this.row = null;
                if(rowIdx >= getNumFeatures())
                    return false;
                try {
                    row = getFeature(rowIdx++);
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
                return this.row.getGeometry();
            }

            @Override
            public int getGeomCoding() {
                return GEOM_ATAK_GEOMETRY;
            }

            @Override
            public String getName() {
                return this.row.getName();
            }

            @Override
            public int getStyleCoding() {
                return STYLE_ATAK_STYLE;
            }

            @Override
            public Object getRawStyle() {
                return this.row.getStyle();
            }

            @Override
            public AttributeSet getAttributes() {
                return this.row.getAttributes();
            }

            @Override
            public Feature get() {
                return this.row;
            }

            @Override
            public long getId() {
                return this.row.getId();
            }

            @Override
            public long getVersion() {
                return this.row.getVersion();
            }

            @Override
            public long getFsid() {
                return this.row.getFeatureSetId();
            }
        };
    }
    
    public Feature findFeature(long fid) throws IOException {
        return this.format.findFeature(this.context, fid);
    }
    
    public void getFIDs(Set<Long> fids) throws IOException {
        this.format.getFIDs(this.context, fids);
    }
    
    public boolean findIntersectingFeatures(Set<Long> fids, Geometry filter) throws IOException {
        return this.format.findIntersectingFeatures(this.context, fids, filter);
    }
    
    public int getNumFeatureSets() {
        return this.metadata.numFeatureSets;
    }

    public FeatureSet getFeatureSet(int index) throws IOException {
        return this.format.getFeatureSet(this.context, index);
    }

    @Override
    public void dispose() {
        if(this.format != null) {
            this.format.closeFormatContext(this.context);
            this.format = null;
            this.context = null;
        }
        if(this.channel != null) {
            try {
                this.channel.close();
            } catch(IOException ignored) {}
            this.channel = null;
        }
    }
    
    /**************************************************************************/
    
    public static void createCacheFile(int clientVersion, int level, int index, long timestamp, FeatureDataStore2 features, FeatureQueryParameters params, String path) throws IOException, DataStoreException {
        FileChannel channel = null;
        try {
            channel = IOProviderFactory.getChannel(new File(path), "rw");
            
            ByteBuffer buf = ByteBuffer.allocate(24);
            buf.put(MAGIC_NUMBER);
            Buffers.skip(buf, 1);
            buf.put((buf.order() == ByteOrder.BIG_ENDIAN) ? (byte)0x01 : (byte)0x00);
            Buffers.skip(buf, 1);
            buf.putShort((short)CURRENT_VERSION);
            buf.putInt(clientVersion);
            buf.flip();
            channel.write(buf);

            Format fmt = FORMATS.get(CURRENT_VERSION);
            if(fmt == null)
                throw new IllegalStateException();
            
            fmt.writeCache(channel, buf.order(), level, index, timestamp, features, params);
        } finally {
            if(channel != null)
                channel.close();
        }
    }
    
    public static CacheFile readCacheFile(String path) throws IOException {
        FileChannel channel = null;
        try {
            channel = IOProviderFactory.getChannel(new File(path), "r");
            
            ByteBuffer buf = ByteBuffer.allocate(24);
            int retval = channel.read(buf);
            if (retval < 20)
                throw new EOFException();
              
            buf.flip();
            if(buf.remaining() < 20)
                throw new EOFException();
            
            for(int i = 0; i < MAGIC_NUMBER.length; i++) {
                if(buf.get() != MAGIC_NUMBER[i])
                    return null;
            }
            
            Buffers.skip(buf, 1);
            
            switch(buf.get()&0xFF) {
                case 0x00 :
                    buf.order(ByteOrder.LITTLE_ENDIAN);
                    break;
                case 0x01 :
                    buf.order(ByteOrder.BIG_ENDIAN);
                    break;
                default :
                    return null;
            }
            Buffers.skip(buf, 1);
            
            final int version = buf.getShort()&0xFF;
            Format fmt = FORMATS.get(version);
            if(fmt == null)
                return null;
            
            int clientVersion = 0;
            if(version > 1) {
                clientVersion = buf.getInt();
            }

            Object ctx = null;
            try {
                ctx = fmt.openFormatContext(channel, buf.order());
                if(ctx == null)
                    return null;
                Metadata metadata = fmt.readCacheMetadata(ctx);
                if(metadata == null)
                    return null;
                final CacheFile retFile = new CacheFile(clientVersion, metadata, fmt, ctx, channel);
                ctx = null;
                channel = null;
                return retFile;
            } finally {
                if(ctx != null)
                    fmt.closeFormatContext(ctx);
            }
        } finally {
            if(channel != null)
                channel.close();
        }
    }
}
