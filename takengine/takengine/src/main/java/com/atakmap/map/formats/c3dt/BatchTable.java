package com.atakmap.map.formats.c3dt;

import com.atakmap.coremap.filesystem.FileSystemUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;

final class BatchTable {
    JSONObject json;
    byte[] binary;

    public int getNumProperties() {
        throw new UnsupportedOperationException();
    }
    public String getPropertyName(int index) {
        throw new UnsupportedOperationException();
    }
    public Object getProperty(int propIndex, int index) {
        throw new UnsupportedOperationException();
    }
    public Class<?> getPropertyType(int index) {
        throw new UnsupportedOperationException();
    }

    public static BatchTable parse(ByteBuffer buffer, int jsonOff, int jsonLength, int binaryOff, int binaryLen) throws JSONException {
        // no usable json
        if(jsonLength < 2)
            return null;
        BatchTable batchTable = new BatchTable();

        byte[] json = new byte[jsonLength];
        Util.get(buffer, jsonOff, json, 0, jsonLength);
        batchTable.json = new JSONObject(new String(json, FileSystemUtils.UTF8_CHARSET));
        if(binaryLen > 0) {
            batchTable.binary = new byte[binaryLen];
            Util.get(buffer, binaryOff, batchTable.binary, 0, binaryLen);
        }
        return batchTable;
    }
}
