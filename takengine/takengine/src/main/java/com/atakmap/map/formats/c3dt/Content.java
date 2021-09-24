package com.atakmap.map.formats.c3dt;

import org.json.JSONException;
import org.json.JSONObject;

final class Content {
    public String uri;
    public Volume boundingVolume;

    public static Content parse(JSONObject json) throws JSONException {
        if(json == null)
            return null;
        Content content = new Content();
        content.uri = json.optString("uri", json.optString("url", null));
        content.boundingVolume = Volume.parse(json.optJSONObject("boundingVolume"));
        return content;
    }
}
