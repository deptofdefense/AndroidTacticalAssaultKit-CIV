
package com.atakmap.android.http.rest;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.spatial.kml.KMLUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * A TAK Server Group
 *
 *
 */
public class ServerGroup {

    private static final String TAG = "ServerGroup";
    public static final String GROUP_LIST_MATCHER = "com.bbn.marti.remote.groups.Group";
    public static final String PATH_ALL_GROUPS = "api/groups/all";

    private final String name;
    private final String direction;
    private final long created;
    private final String type;
    private final int bitpos;

    public ServerGroup(String name, String direction, long created,
            String type, int bitpos) {
        this.name = name;
        this.direction = direction;
        this.created = created;
        this.type = type;
        this.bitpos = bitpos;
    }

    public String getName() {
        return name;
    }

    public String getDirection() {
        return direction;
    }

    public long getCreated() {
        return created;
    }

    public String getType() {
        return type;
    }

    public int getBitpos() {
        return bitpos;
    }

    public boolean isValid() {
        return !FileSystemUtils.isEmpty(name)
                && !FileSystemUtils.isEmpty(direction)
                && !FileSystemUtils.isEmpty(type)
                && created >= 0
                && bitpos >= 0;
    }

    public static ServerGroup fromJSON(JSONObject obj) throws JSONException {

        String createdString = obj.getString("created");
        long createdTime = -1;
        try {
            createdTime = KMLUtil.KMLDateFormatter.get()
                    .parse(createdString).getTime();
        } catch (ParseException e) {
            Log.w(TAG, "Unable to parse created: " + createdString,
                    e);
            throw new JSONException("Unable to parse created time");
        }

        return new ServerGroup(
                obj.getString("name"),
                obj.getString("direction"),
                createdTime,
                obj.getString("type"),
                obj.getInt("bitpos"));
    }

    public static List<ServerGroup> fromResultJSON(JSONObject json)
            throws JSONException {

        if (!json.has("type"))
            throw new JSONException("Missing type");
        String jtype = json.getString("type");
        if (!FileSystemUtils.isEquals(jtype,
                ServerGroup.GROUP_LIST_MATCHER)) {
            throw new JSONException("Invalid type: " + jtype);
        }

        JSONArray array = json.getJSONArray("data");
        ArrayList<ServerGroup> results = new ArrayList<>();
        if (array == null || array.length() < 1) {
            return results;
        }

        for (int i = 0; i < array.length(); i++) {
            ServerGroup r = ServerGroup.fromJSON(array.getJSONObject(i));
            if (r == null || !r.isValid())
                throw new JSONException("Invalid ServerGroup");
            results.add(r);
        }

        return results;
    }
}
