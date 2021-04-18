package com.atakmap.map.formats.c3dt;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.io.UriFactory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class B3DM {
    private final static int MAGIC_GLTF_LE = 0x46546C67;
    private final static int MAGIC_B3DM_LE = 0x6D643362;

    int magic;
    int version;
    int byteLength;
    int batchLength;
    int batchTableByteLength;
    int batchTableJsonByteLength;
    int batchTableBinaryByteLength;
    int featureTableJsonByteLength;
    int featureTableBinaryByteLength;

    public BatchTable batchTable;
    public FeatureTable featureTable;

    public GLTF gltf;

    private int headerVersion;

    // 20 byte header
    // https://github.com/AnalyticalGraphicsInc/3d-tiles/blob/856ec21d823be74383141a79dac4a28145fcf2c0/TileFormats/Batched3DModel/README.md
    // magic int8[4]
    // version int32
    // byteLength int32
    // batchLength int32
    // batchTableByteLength int32

    // 24 byte header
    // https://github.com/AnalyticalGraphicsInc/3d-tiles/blob/a45866de985f5b37aa98fc75340878d7a78e4056/TileFormats/Batched3DModel/README.md
    // magic int8[4]
    // version int32
    // byteLength int32
    // batchTableJsonByteLength int32
    // batchTableBinaryByteLength int32
    // batchLength int32

    // 28 byte header
    // magic int8[4]
    // version int32
    // byteLength int32
    // featureTableJsonByteLength int32
    // featureTableBinaryByteLength int32
    // batchTableJsonByteLength int32
    // batchTableBinaryByteLength int32

    public static B3DM parse(String uri, ContentSource handler) throws JSONException, IOException {
        try(UriFactory.OpenResult openResult = UriFactory.open(uri)) {
            if (openResult == null || openResult.inputStream == null)
                return null;
            byte[] bytes = openResult.contentLength > 0 ?
                    FileSystemUtils.read(openResult.inputStream, (int)openResult.contentLength, true) :
                    FileSystemUtils.read(openResult.inputStream);

            return parse(ByteBuffer.wrap(bytes), Util.resolve(uri), handler);
        }
    }
    public static B3DM parse(File file) throws JSONException, IOException {
        if(!IOProviderFactory.exists(file))
            return null;
        return parse(ByteBuffer.wrap(FileSystemUtils.read(file)), file.getParentFile().toString(), null);
    }
    public static B3DM parse(ByteBuffer buffer) throws JSONException {
        return parse(buffer, null, null);
    }
    public static B3DM parse(ByteBuffer buffer, String baseUri, ContentSource handler) throws JSONException {
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        B3DM b3dm = new B3DM();
        b3dm.magic = buffer.getInt();
        if(b3dm.magic != MAGIC_B3DM_LE)
            return null;

        b3dm.version = buffer.getInt();
        b3dm.byteLength = buffer.getInt();

        if(isHeader20(buffer)) { // 20 byte
            b3dm.headerVersion = 20;
            b3dm.batchLength = buffer.getInt();
            b3dm.batchTableByteLength = buffer.getInt();
            b3dm.batchTable = BatchTable.parse(buffer, buffer.position(), b3dm.batchTableByteLength, buffer.position()+b3dm.batchTableByteLength, 0);
            Util.skip(buffer, b3dm.batchTableByteLength);

            // for 20-byte header, look for CESIUM_RTC extension in glTF
            ByteBuffer view = buffer.duplicate();
            view.order(ByteOrder.LITTLE_ENDIAN);
            final int jsonLen = view.getInt(view.position()+12);
            if(jsonLen > 0) {
                try {
                    JSONObject gltf = new JSONObject(new String(view.array(), view.position()+20, jsonLen, FileSystemUtils.UTF8_CHARSET));
                    do {
                        JSONObject o = gltf;

                        o = o.optJSONObject("extensions");
                        if(o == null)
                            break;
                        o = o.getJSONObject("CESIUM_RTC");
                        if(o == null)
                            break;
                        JSONArray cesium_rtc = o.optJSONArray("center");
                        if(cesium_rtc == null)
                            break;
                        // create a feature table and inject CESIUM_RTC as RTC_CENTER
                        b3dm.featureTable = new FeatureTable();
                        b3dm.featureTable.json = new JSONObject();
                        b3dm.featureTable.json.put("RTC_CENTER", cesium_rtc);
                    } while(false);
                } catch(Throwable ignored) {

                }
            }
        } else if(isHeader24(buffer)) {
            b3dm.headerVersion = 24;
            b3dm.batchTableJsonByteLength = buffer.getInt();
            b3dm.batchTableBinaryByteLength = buffer.getInt();
            b3dm.batchLength = buffer.getInt();
            b3dm.batchTable = BatchTable.parse(buffer, buffer.position(), b3dm.batchTableJsonByteLength, buffer.position()+b3dm.batchTableJsonByteLength, b3dm.batchTableBinaryByteLength);
            Util.skip(buffer, b3dm.batchTableByteLength);
        } else {
            b3dm.headerVersion = 28;
            b3dm.featureTableJsonByteLength = buffer.getInt();
            b3dm.featureTableBinaryByteLength = buffer.getInt();
            b3dm.batchTableJsonByteLength = buffer.getInt();
            b3dm.batchTableBinaryByteLength = buffer.getInt();
            b3dm.featureTable = FeatureTable.parse(buffer, buffer.position(), b3dm.featureTableJsonByteLength, buffer.position()+b3dm.featureTableJsonByteLength, b3dm.featureTableBinaryByteLength);
            Util.skip(buffer, b3dm.featureTableJsonByteLength+b3dm.featureTableBinaryByteLength);
            b3dm.batchTable = BatchTable.parse(buffer, buffer.position(), b3dm.batchTableJsonByteLength, buffer.position()+b3dm.batchTableJsonByteLength, b3dm.batchTableBinaryByteLength);
            Util.skip(buffer, b3dm.batchTableJsonByteLength+b3dm.batchTableBinaryByteLength);
        }

        b3dm.gltf = GLTF.parse(buffer, baseUri, handler);

        return b3dm;
    }

    private static boolean isHeader20(ByteBuffer buffer) {
        final int batchTableByteLength = buffer.getInt(buffer.position() + 4);
        if (batchTableByteLength > 0) {
            // indicates batch table content, check for JSON immediately following
            return buffer.get(buffer.position() + 8) == '{';
        } else if(batchTableByteLength == 0) {
            // no batch table content, look for "gltf" as magic number
            final int magic = buffer.getInt(buffer.position() + 8);
            return magic == 0x46546C67;
        } else {
            return false;
        }
    }

    private static boolean isHeader24(ByteBuffer buffer) {
        // XXX -
        return false;
    }
}
