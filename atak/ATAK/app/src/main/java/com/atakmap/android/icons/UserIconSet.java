
package com.atakmap.android.icons;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.database.CursorIface;

/**
 * Icon set may be loaded from XML and or SQLite
 * 
 * 
 */
@Root(name = "iconset")
public class UserIconSet {

    private static final String TAG = "UserIconSet";

    protected final static String COLUMN_ICONSETS_ID = "id";
    protected final static String COLUMN_ICONSETS_NAME = "name";
    protected final static String COLUMN_ICONSETS_UID = "uid";
    protected final static String COLUMN_ICONSETS_DEFAULT_FRIENDLY = "defaultFriendly";
    protected final static String COLUMN_ICONSETS_DEFAULT_HOSTILE = "defaultHostile";
    protected final static String COLUMN_ICONSETS_DEFAULT_NEUTRAL = "defaultNeutral";
    protected final static String COLUMN_ICONSETS_DEFAULT_UNKNOWN = "defaultUnknown";
    protected final static String COLUMN_ICONSETS_SELECTED_GROUP = "selectedGroup";

    /**
     * DB primary key
     */
    private int id;

    /**
     * XML or DB, textual name
     */
    @Attribute(name = "name", required = true)
    private String name;

    /**
     * XML or DB, ID unique across iconset and iconset versions
     */
    @Attribute(name = "uid", required = true)
    private String uid;

    /**
     * XML or DB, default friendly icon
     */
    @Attribute(name = "defaultFriendly", required = false)
    private String defaultFriendly;
    @Attribute(name = "defaultHostile", required = false)
    private String defaultHostile;
    @Attribute(name = "defaultNeutral", required = false)
    private String defaultNeutral;
    @Attribute(name = "defaultUnknown", required = false)
    private String defaultUnknown;

    /**
     * XML flag to skip auto resizing of icons to 32x32px
     */
    @Attribute(name = "skipResize", required = false)
    private boolean skipResize = false;

    /**
     * XML flag to set initial group selected in UI
     * Stored in DB as the initially selected group
     */
    @Attribute(name = "defaultGroup", required = false)
    private String selectedGroup;

    /**
     * iconset XML schema version
     */
    @Attribute(name = "version", required = true)
    private int VERSION = 1;

    /**
     * XML or DB. For XML bitMap is never loaded. For DB bitMap may be loaded
     */
    @ElementList(entry = "icon", inline = true, required = false)
    private List<UserIcon> icons;

    static final String[][] META_DATA_LABELS = {
            {
                    COLUMN_ICONSETS_ID, "INTEGER PRIMARY KEY ASC"
            },
            {
                    COLUMN_ICONSETS_NAME, "TEXT"
            },
            {
                    COLUMN_ICONSETS_UID, "TEXT"
            },
            {
                    COLUMN_ICONSETS_DEFAULT_FRIENDLY, "TEXT"
            },
            {
                    COLUMN_ICONSETS_DEFAULT_HOSTILE, "TEXT"
            },
            {
                    COLUMN_ICONSETS_DEFAULT_NEUTRAL, "TEXT"
            },
            {
                    COLUMN_ICONSETS_DEFAULT_UNKNOWN, "TEXT"
            },
            {
                    COLUMN_ICONSETS_SELECTED_GROUP, "TEXT"
            }
    };

    static String[] getColumns() {
        String[] columns = new String[UserIconSet.META_DATA_LABELS.length];
        for (int i = 0; i < UserIconSet.META_DATA_LABELS.length; ++i) {
            columns[i] = UserIconSet.META_DATA_LABELS[i][0];
        }
        return columns;
    }

    public Object getFromMetaDataLabel(String label) {
        if (COLUMN_ICONSETS_ID.equals(label)) {
            return getId();
        } else if (COLUMN_ICONSETS_NAME.equals(label)) {
            return getName();
        } else if (COLUMN_ICONSETS_UID.equals(label)) {
            return getUid();
        } else if (COLUMN_ICONSETS_DEFAULT_FRIENDLY.equals(label)) {
            return getDefaultFriendly();
        } else if (COLUMN_ICONSETS_DEFAULT_HOSTILE.equals(label)) {
            return getDefaultHostile();
        } else if (COLUMN_ICONSETS_DEFAULT_NEUTRAL.equals(label)) {
            return getDefaultNeutral();
        } else if (COLUMN_ICONSETS_DEFAULT_UNKNOWN.equals(label)) {
            return getDefaultUnknown();
        } else if (COLUMN_ICONSETS_SELECTED_GROUP.equals(label)) {
            return getSelectedGroup();
        }
        return null;
    }

    /**
     * Default Constructor
     */
    public UserIconSet() {
    }

    public UserIconSet(String name, String uid) {
        setName(name);
        setUid(uid);
    }

    public UserIconSet(int id, String name, String uid, String defaultFriendly,
            String defaultHostile, String defaultNeutral,
            String defaultUnknown,
            String selectedGroup) {
        setId(id);
        setName(name);
        setUid(uid);
        this.defaultFriendly = defaultFriendly;
        this.defaultHostile = defaultHostile;
        this.defaultNeutral = defaultNeutral;
        this.defaultUnknown = defaultUnknown;
        this.selectedGroup = selectedGroup;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public int getVersion() {
        return VERSION;
    }

    public void setVersion(int version) {
        VERSION = version;
    }

    public List<UserIcon> getIcons() {
        return icons;
    }

    public void setIcons(List<UserIcon> icons) {
        this.icons = icons;
    }

    public boolean hasIcons() {
        return icons != null && icons.size() > 0;
    }

    public String getDefaultFriendly() {
        return defaultFriendly;
    }

    public String getDefaultHostile() {
        return defaultHostile;
    }

    public String getDefaultNeutral() {
        return defaultNeutral;
    }

    public String getDefaultUnknown() {
        return defaultUnknown;
    }

    public boolean isSkipResize() {
        return skipResize;
    }

    public boolean hasSelectedGroup() {
        return !FileSystemUtils.isEmpty(selectedGroup);
    }

    public String getSelectedGroup() {
        return selectedGroup;
    }

    public void setSelectedGroup(String selectedGroup) {
        this.selectedGroup = selectedGroup;
    }

    /**
     * Get icon with the specified filename
     * @param filename
     * @return
     */
    public UserIcon getIcon(String filename) {
        if (!hasIcons() || FileSystemUtils.isEmpty(filename))
            return null;

        for (UserIcon icon : icons) {
            if (icon != null && filename.equalsIgnoreCase(icon.getFileName()))
                return icon;
        }

        return null;
    }

    public boolean hasIcon(String filename) {
        return getIcon(filename) != null;
    }

    /**
     * Get icon with best match for CoT/2525C type
     * @param type
     * @return
     */
    public UserIcon getIconBestMatch(String type) {
        if (FileSystemUtils.isEmpty(type) || !hasIcons())
            return null;

        //first look for exact 2525C match
        for (UserIcon icon : icons) {
            if (icon != null && type.equals(icon.get2525cType())) {
                return icon;
            }
        }

        //look for partial 2525C match
        UserIcon icon = _getIconBestMatch(type);
        if (icon != null)
            return icon;

        //fallback on default for top level affiliations
        String t = type.toLowerCase(LocaleUtil.getCurrent());
        if (t.startsWith("a-f")) { // Friendly
            icon = getIcon(getDefaultFriendly());
            if (icon != null)
                return icon;
        } else if (t.startsWith("a-h")) { // Hostile
            icon = getIcon(getDefaultHostile());
            if (icon != null)
                return icon;
        } else if (t.startsWith("a-n")) { // Neutral
            icon = getIcon(getDefaultNeutral());
            if (icon != null)
                return icon;
        } else if (t.startsWith("a-u")) { // Unknown
            icon = getIcon(getDefaultUnknown());
            if (icon != null)
                return icon;
        }

        //no icon found, return first one in list
        return icons.get(0);
    }

    private UserIcon _getIconForType(String type) {
        for (UserIcon icon : icons) {
            if (type.equalsIgnoreCase(icon.get2525cType()))
                return icon;
        }

        return null;
    }

    private UserIcon _getIconBestMatch(String type) {
        //Log.d(TAG, "_getIconBestMatch " + type);
        UserIcon icon = _getIconForType(type);
        if (icon == null) {
            int lastDashIdx = type.lastIndexOf('-');
            if (lastDashIdx != -1) {
                icon = _getIconBestMatch(type.substring(0, lastDashIdx));
            }
        }
        return icon;
    }

    public List<String> getGroups() {
        List<String> groups = new ArrayList<>();

        if (!hasIcons()) {
            return groups;
        }

        for (UserIcon icon : icons) {
            if (icon != null && icon.isValid()
                    && !groups.contains(icon.getGroup())) {
                groups.add(icon.getGroup());
            }
        }

        return groups;
    }

    public List<UserIcon> getIcons(String group) {
        List<UserIcon> groupIcons = new ArrayList<>();

        if (FileSystemUtils.isEmpty(group) || !hasIcons()) {
            return groupIcons;
        }

        for (UserIcon icon : icons) {
            if (icon != null && icon.isValid()
                    && group.equals(icon.getGroup())) {
                groupIcons.add(icon);
            }
        }

        return groupIcons;
    }

    public boolean isValid() {
        return !FileSystemUtils.isEmpty(name) &&
                !FileSystemUtils.isEmpty(uid);
    }

    public String toString() {
        return name + "/" + uid + (icons == null ? "" : "/" + icons.size());
    }

    public boolean equals(Object rhs) {
        if (!(rhs instanceof UserIconSet))
            return false;

        UserIconSet rhsInfo = (UserIconSet) rhs;
        if (VERSION != rhsInfo.getVersion())
            return false;

        if (id != rhsInfo.getId())
            return false;

        if (!FileSystemUtils.isEquals(name, rhsInfo.name))
            return false;

        if (!FileSystemUtils.isEquals(uid, rhsInfo.uid))
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        return (name + id).hashCode();
    }

    static UserIconSet fromCursor(CursorIface cursor) {
        return new UserIconSet(
                cursor.getInt(cursor
                        .getColumnIndex(UserIconSet.COLUMN_ICONSETS_ID)),
                cursor.getString(cursor
                        .getColumnIndex(UserIconSet.COLUMN_ICONSETS_NAME)),
                cursor.getString(cursor
                        .getColumnIndex(UserIconSet.COLUMN_ICONSETS_UID)),
                cursor.getString(cursor
                        .getColumnIndex(
                                UserIconSet.COLUMN_ICONSETS_DEFAULT_FRIENDLY)),
                cursor.getString(cursor
                        .getColumnIndex(
                                UserIconSet.COLUMN_ICONSETS_DEFAULT_HOSTILE)),
                cursor.getString(cursor
                        .getColumnIndex(
                                UserIconSet.COLUMN_ICONSETS_DEFAULT_NEUTRAL)),
                cursor.getString(cursor
                        .getColumnIndex(
                                UserIconSet.COLUMN_ICONSETS_DEFAULT_UNKNOWN)),
                cursor.getString(cursor
                        .getColumnIndex(
                                UserIconSet.COLUMN_ICONSETS_SELECTED_GROUP)));
    }

    /**
     * Get the list of configured devices from file
     * 
     * @return
     */
    public static UserIconSet loadUserIconSet(String s) {
        Log.d(TAG, "Loading User Icon Set from string");

        Serializer serializer = new Persister();
        try {
            return serializer.read(UserIconSet.class, s);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load User Icon Set from: " + s, e);
            return null;
        }
    }

    public String save() {
        Log.d(TAG, "Saving iconset to string");
        Serializer serializer = new Persister();

        try {
            StringWriter sw = new StringWriter();
            serializer.write(this, sw);
            return sw.toString();
        } catch (Exception e) {
            Log.e(TAG, "Failed to save iconset: " + this, e);
            return "";
        }
    }
}
