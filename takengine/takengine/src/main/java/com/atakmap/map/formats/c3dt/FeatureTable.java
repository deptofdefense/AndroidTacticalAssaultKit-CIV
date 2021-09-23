package com.atakmap.map.formats.c3dt;

import com.atakmap.coremap.filesystem.FileSystemUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;

final class FeatureTable {
    JSONObject json;
    byte[] binary;

    public boolean hasProperty(String name) {
        return (json != null && json.has(name));
    }
    public double[] getDoubleArray(String name) {
        if(json == null || !json.has(name))
            return null;
        JSONArray jarr = json.optJSONArray(name);
        if(jarr == null)
            return null;
        try {
            double[] retval = new double[jarr.length()];
            for(int i = 0; i < jarr.length(); i++)
                retval[i] = jarr.getDouble(i);
            return retval;
        } catch(JSONException e) {
            return null;
        }
    }
    public Class<?> getPropertyType(int index) {
        throw new UnsupportedOperationException();
    }

    public static FeatureTable parse(ByteBuffer buffer, int jsonOff, int jsonLength, int binaryOff, int binaryLen) throws JSONException {
        // no usable json
        if(jsonLength < 2)
            return null;
        FeatureTable featureTable = new FeatureTable();

        byte[] json = new byte[jsonLength];
        Util.get(buffer, jsonOff, json, 0, jsonLength);
        featureTable.json = new JSONObject(new String(json, FileSystemUtils.UTF8_CHARSET));
        if(binaryLen > 0) {
            featureTable.binary = new byte[binaryLen];
            Util.get(buffer, binaryOff, featureTable.binary, 0, binaryLen);
        }
        return featureTable;
    }
}
