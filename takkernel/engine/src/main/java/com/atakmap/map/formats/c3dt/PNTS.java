package com.atakmap.map.formats.c3dt;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class PNTS {
    private final static int MAGIC_PNTS_LE = 0x73746E70;

    // header
    int magic;
    int version;
    int byteLength;
    int featureTableJsonByteLength;
    int featureTableBinaryByteLength;
    int batchTableJsonByteLength;
    int batchTableBinaryByteLength;

    FeatureTable featureTable;
    BatchTable batchTable;

    public static PNTS parse(String uri, ContentSource handler) throws JSONException, IOException, URISyntaxException {
        final byte[] bytes = handler.getData(uri, null);
        if (bytes == null)
            return null;
        return parse(ByteBuffer.wrap(bytes));
    }
    public static PNTS parse(File file) throws JSONException, IOException {
        if(!IOProviderFactory.exists(file))
            return null;
        return parse(ByteBuffer.wrap(FileSystemUtils.read(file)));
    }

    public static PNTS parse(ByteBuffer buffer) throws JSONException {
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        PNTS pnts = new PNTS();
        pnts.magic = buffer.getInt();
        if(pnts.magic != MAGIC_PNTS_LE)
            return null;

        pnts.version = buffer.getInt();
        pnts.byteLength = buffer.getInt();


        pnts.featureTableJsonByteLength = buffer.getInt();
        pnts.featureTableBinaryByteLength = buffer.getInt();
        pnts.batchTableJsonByteLength = buffer.getInt();
        pnts.batchTableBinaryByteLength = buffer.getInt();
        pnts.featureTable = FeatureTable.parse(buffer, buffer.position(), pnts.featureTableJsonByteLength, buffer.position()+pnts.featureTableJsonByteLength, pnts.featureTableBinaryByteLength);
        Util.skip(buffer, pnts.featureTableJsonByteLength+pnts.featureTableBinaryByteLength);
        pnts.batchTable = BatchTable.parse(buffer, buffer.position(), pnts.batchTableJsonByteLength, buffer.position()+pnts.batchTableJsonByteLength, pnts.batchTableBinaryByteLength);
        Util.skip(buffer, pnts.batchTableJsonByteLength+pnts.batchTableBinaryByteLength);

        return pnts;
    }
}
