package com.atakmap.map.formats.c3dt;

import org.json.JSONException;
import org.json.JSONObject;

final class Asset {
    public String version;
    public String tilesetVersion;

    public static Asset parse(JSONObject json) throws JSONException {
        if(json == null)
            return null;
        Asset retval = new Asset();
        retval.version = json.optString("version", null);
        retval.tilesetVersion = json.optString("tilesetVersion", null);
        return retval;
    }
}
