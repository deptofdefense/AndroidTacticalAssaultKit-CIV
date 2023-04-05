
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
    private String direction;
    private final long created;
    private final String type;
    private final int bitpos;
    private boolean active;
    private final String description;

    public ServerGroup(String name, String direction, long created,
            String type, int bitpos, boolean active, String description) {
        this.name = name;
        this.direction = direction;
        this.created = created;
        this.type = type;
        this.bitpos = bitpos;
        this.active = active;
        this.description = description;
    }

    public ServerGroup(ServerGroup serverGroup) {
        this(serverGroup.name, serverGroup.direction, serverGroup.created,
                serverGroup.type,
                serverGroup.bitpos, serverGroup.active,
                serverGroup.description);
    }

    public String getName() {
        return name;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
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

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getDescription() {
        return description;
    }

    public boolean isValid() {
        return !FileSystemUtils.isEmpty(name)
                && !FileSystemUtils.isEmpty(direction)
                && !FileSystemUtils.isEmpty(type)
                && created >= 0
                && bitpos >= 0;
    }

    public JSONObject toJSON() {
        if (!isValid())
            return null;
        JSONObject o = new JSONObject();
        try {
            o.put("name", this.name);
            o.put("direction", this.direction);
            o.put("created", this.created);
            o.put("type", this.type);
            o.put("bitpos", this.bitpos);
            o.put("active", this.active);
        } catch (Exception e) {
            Log.e(TAG, "Failed to serialize", e);
            return null;
        }
        return o;
    }

    public static JSONArray toResultJSON(List<ServerGroup> serverGroups) {
        JSONArray arr = new JSONArray();
        for (ServerGroup serverGroup : serverGroups) {
            JSONObject o = serverGroup.toJSON();
            if (o == null)
                continue;
            arr.put(o);
        }
        return arr;
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

        boolean active = true;
        try {
            active = obj.getBoolean("active");
        } catch (JSONException e) {
            Log.v(TAG, "JSONException parsing active!", e);
        }

        int bitpos = -1;
        try {
            bitpos = obj.getInt("bitpos");
        } catch (JSONException e) {
            Log.v(TAG, "JSONException parsing bitpos!", e);
        }

        String description = null;
        try {
            description = obj.getString("description");
        } catch (JSONException e) {
            Log.v(TAG, "JSONException parsing description!", e);
        }

        return new ServerGroup(
                obj.getString("name"),
                obj.getString("direction"),
                createdTime,
                obj.getString("type"),
                bitpos,
                active,
                description);
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
