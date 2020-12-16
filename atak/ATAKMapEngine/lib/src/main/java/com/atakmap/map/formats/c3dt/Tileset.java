package com.atakmap.map.formats.c3dt;

import org.json.JSONException;
import org.json.JSONObject;

final class Tileset {
    public Asset asset;
    public JSONObject properties;
    public double geometricError;
    public Tile root;
    public double maxScreenSpaceError;

    public static Tileset parse(JSONObject json) throws JSONException {
        return parse(json, null);
    }

    /**
     *
     * @param json
     * @param parent    If parsing an external tileset, parent tile
     * @return
     * @throws JSONException
     */
    public static Tileset parse(JSONObject json, Tile parent) throws JSONException {
        if(json == null)
            return null;
        Tileset tileset = new Tileset();
        tileset.asset = Asset.parse(json.optJSONObject("asset"));
        tileset.geometricError = json.optDouble("geometricError", 0d);
        tileset.root = Tile.parse(parent, json.optJSONObject("root"));
        tileset.properties = json.optJSONObject("properties");
        // XXX - max screen space error
        tileset.maxScreenSpaceError = 32d;
        return tileset;
    }
}
