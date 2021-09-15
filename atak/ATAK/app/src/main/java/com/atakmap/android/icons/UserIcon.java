
package com.atakmap.android.icons;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import com.atakmap.android.maps.DefaultMapGroup;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.SqliteMapDataRef;
import com.atakmap.android.user.icon.Icon2525cPallet;
import com.atakmap.android.user.icon.SpotMapPallet;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.database.CursorIface;
import com.atakmap.database.DatabaseIface;

import org.simpleframework.xml.Attribute;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

/**
 * Icon may be loaded from XML and or SQLite
 * 
 * 
 */
public class UserIcon {
    private static final String TAG = "UserIcon";

    /**
     * Iconset path is 3-tuple formatted as:
     * <iconsetpath UID>/<group>/<filename>
     */
    public static final String IconsetPath = "IconsetPath";

    final static String COLUMN_ICONS_ID = "id";
    final static String COLUMN_ICONS_SETUID = "iconset_uid";
    final static String COLUMN_ICONS_FILENAME = "filename";
    final static String COLUMN_ICONS_COTTYPE = "type2525b"; // for legacy databases
    final static String COLUMN_ICONS_GROUP = "groupName";
    final static String COLUMN_ICONS_USECNT = "useCnt";
    final static String COLUMN_ICONS_BITMAP = "bitmap";

    /**
     * DB primary key
     */
    private int id;

    /**
     * DB reference to iconset table
     */
    private String iconsetUid;

    /**
     * DB, icon filename, also derived from zip during import
     */
    @Attribute(name = "name", required = true)
    private String fileName;

    /**
     * XML or DB, optional CoT for compliance with other CoT viewers (e.g. when ATAK
     * sends points out over the network).   The name is for legacy iconsets and should be considered
     * reflective of 2525C iconology.
     */
    @Attribute(name = "type2525b", required = false)
    private String type2525c;

    /**
     * DB, and derived from directory in zip file, not specified in XML
     */
    private String group;

    /**
     * DB, track number of times this icon has been used
     */
    private int useCnt;

    /**
     * DB, this data is required in the DB but may or may not be loaded during
     * DB queries. This information is not available when reading in XML
     */
    private Bitmap bitMap;

    public static final String[][] META_DATA_LABELS = {
            {
                    COLUMN_ICONS_ID, "INTEGER PRIMARY KEY ASC"
            },
            {
                    COLUMN_ICONS_SETUID, "TEXT"
            }, {
                    COLUMN_ICONS_FILENAME, "TEXT"
            },
            {
                    COLUMN_ICONS_GROUP, "TEXT"
            }, {
                    COLUMN_ICONS_COTTYPE, "TEXT"
            },
            {
                    COLUMN_ICONS_USECNT, "INTEGER"
            }, {
                    COLUMN_ICONS_BITMAP, "BLOB"
            }
    };

    static String[] getColumns() {
        String[] columns = new String[UserIcon.META_DATA_LABELS.length];
        for (int i = 0; i < UserIcon.META_DATA_LABELS.length; ++i) {
            columns[i] = UserIcon.META_DATA_LABELS[i][0];
        }
        return columns;
    }

    /**
     * Check if iconset is well-formed and references an icon currently stored
     * on this device. Note returns false for 2525C and Spot Map iconset paths
     * 
     * @param iconsetPath
     * @param requireDatabaseMatch
     * @return
     */
    public static boolean IsValidIconsetPath(String iconsetPath,
            boolean requireDatabaseMatch, Context context) {
        if (FileSystemUtils.isEmpty(iconsetPath))
            return false;

        if (iconsetPath.startsWith(Icon2525cPallet.COT_MAPPING_2525) ||
                iconsetPath.startsWith(SpotMapPallet.COT_MAPPING_SPOTMAP))
            return false;

        String[] tokens = iconsetPath.split("/");
        if (tokens == null || tokens.length < 2) {
            return false;
        }

        if (FileSystemUtils.isEmpty(tokens[0])
                || FileSystemUtils.isEmpty(tokens[1])
                || FileSystemUtils.isEmpty(tokens[tokens.length - 1])) {
            return false;
        }

        if (!requireDatabaseMatch) {
            //well formed iconsetpath, we're OK here
            return true;
        }

        //icon is required to be in database presently
        return GetIconFromIconsetPath(iconsetPath, false, context) != null;
    }

    /**
     * Check if iconset is well-formed and references an icon currently stored
     * on this device. Note returns false for 2525C and Spot Map iconset paths.
     * Also returns false if the icon is not currently in the local database
     * 
     * @param iconsetPath
     * @return
     */
    public static boolean IsValidIconsetPath(String iconsetPath,
            Context context) {
        return IsValidIconsetPath(iconsetPath, true, context);
    }

    public Object getFromMetaDataLabel(String label) {
        if (COLUMN_ICONS_ID.equals(label)) {
            return getId();
        } else if (COLUMN_ICONS_SETUID.equals(label)) {
            return getIconsetUid();
        } else if (COLUMN_ICONS_FILENAME.equals(label)) {
            return getFileName();
        } else if (COLUMN_ICONS_COTTYPE.equals(label)) {
            return get2525cType();
        } else if (COLUMN_ICONS_BITMAP.equals(label)) {
            return getBitMap();
        } else if (COLUMN_ICONS_GROUP.equals(label)) {
            return getGroup();
        } else if (COLUMN_ICONS_USECNT.equals(label)) {
            return getUseCount();
        }
        return null;
    }

    /**
     * Default Constructor
     */
    public UserIcon() {
        this(-1, null, null, null, null, null, 0);
    }

    public UserIcon(int id, String iconsetUid, String group, String fileName,
            String cotType, Bitmap bitmap, int usecnt) {
        setId(id);
        setIconsetUid(iconsetUid);
        setGroup(group);
        setFileName(fileName);
        set2525cType(cotType);
        setBitMap(bitmap);
        setUseCount(usecnt);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getIconsetUid() {
        return iconsetUid;
    }

    public void setIconsetUid(String uid) {
        this.iconsetUid = uid;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String get2525cType() {
        return type2525c;
    }

    public void set2525cType(String t) {
        this.type2525c = t;
    }

    public Bitmap getBitMap() {
        return bitMap;
    }

    public void setBitMap(Bitmap bitMap) {
        this.bitMap = bitMap;
    }

    public int getUseCount() {
        return useCnt;
    }

    public void setUseCount(int useCnt) {
        this.useCnt = useCnt;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public boolean isValid() {
        return !FileSystemUtils.isEmpty(iconsetUid)
                && !FileSystemUtils.isEmpty(group)
                && !FileSystemUtils.isEmpty(fileName);
    }

    public String toString() {
        return iconsetUid + "/" + group + "/" + fileName + "/" + type2525c;
    }

    @Override
    public boolean equals(Object rhs) {
        if (!(rhs instanceof UserIcon))
            return false;

        UserIcon rhsInfo = (UserIcon) rhs;
        if (id != rhsInfo.getId())
            return false;

        if (!FileSystemUtils.isEquals(iconsetUid, rhsInfo.iconsetUid))
            return false;

        if (!FileSystemUtils.isEquals(fileName, rhsInfo.fileName))
            return false;

        if (!FileSystemUtils.isEquals(type2525c, rhsInfo.type2525c))
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        return (fileName + id).hashCode();
    }

    static UserIcon fromCursor(CursorIface cursor, boolean bBitMap) {
        Bitmap bitMap = null;
        if (bBitMap) {
            bitMap = UserIcon.decodeBitMap(cursor.getBlob(cursor
                    .getColumnIndex(COLUMN_ICONS_BITMAP)));
            if (bitMap == null)
                Log.w(TAG, "Failed to load bitmap from cursor");
        }

        return new UserIcon(cursor.getInt(cursor
                .getColumnIndex(UserIcon.COLUMN_ICONS_ID)),
                cursor.getString(cursor
                        .getColumnIndex(UserIcon.COLUMN_ICONS_SETUID)),
                cursor.getString(cursor
                        .getColumnIndex(UserIcon.COLUMN_ICONS_GROUP)),
                cursor.getString(cursor
                        .getColumnIndex(UserIcon.COLUMN_ICONS_FILENAME)),
                cursor.getString(cursor
                        .getColumnIndex(UserIcon.COLUMN_ICONS_COTTYPE)),
                bitMap,
                cursor.getInt(cursor
                        .getColumnIndex(UserIcon.COLUMN_ICONS_USECNT)));
    }

    public String getIconBitmapQuery() {
        if (!isValid()) {
            Log.w(TAG, "Invalid icon");
            return null;
        }

        return GetIconBitmapQuery(getId());
    }

    /**
     * Create an optimized query based on id (indexed primary key), query
     * only returns "bitmap" column
     * 
     * @param id
     * @return
     */
    public static String GetIconBitmapQuery(int id) {
        return "select " + UserIcon.COLUMN_ICONS_BITMAP +
                " from " + UserIconDatabase.TABLE_ICONS +
                " where " + UserIcon.COLUMN_ICONS_ID + "=" + id;
    }

    /**
     * Get iconset path uniquely describing this icon
     * @return the iconset path
     */
    public String getIconsetPath() {
        if (!isValid()) {
            Log.w(TAG, "Invalid icon");
            return null;
        }

        return GetIconsetPath(iconsetUid, group, fileName);
    }

    public static String GetIconsetPath(String iconsetUid, String group,
            String fileName) {
        if (FileSystemUtils.isEmpty(iconsetUid)
                || FileSystemUtils.isEmpty(group)
                || FileSystemUtils.isEmpty(fileName)) {
            Log.w(TAG, "Invalid iconset path inputs");
            return null;
        }

        return iconsetUid + "/" + group + "/" + fileName;
    }

    /**
     * Lookup icon based on iconset
     * 
     * @param iconsetPath the iconset path that describes a iconset icon starting with the uuid for
     *                    the iconset and then the path.
     * @param bBitmap     if the bitmap is required in the return
     * @return the bitmap if the iconset is found, otherwise null.
     */
    public static UserIcon GetIconFromIconsetPath(String iconsetPath,
            boolean bBitmap, Context context) {
        if (FileSystemUtils.isEmpty(iconsetPath)
                || !iconsetPath.contains("/")) {
            Log.w(TAG, "Failed to parse iconsetPath: " + iconsetPath); //, new Exception());
            return null;
        }

        String[] tokens = iconsetPath.split("/");
        if (tokens == null || tokens.length < 2) {
            Log.w(TAG, "Failed to split iconsetPath");
            return null;
        }

        //note group is tokens[1] currently not used for querying icon
        return UserIconDatabase.instance(context).getIcon(tokens[0],
                tokens[tokens.length - 1],
                bBitmap);
    }

    /**
     * Lookup icon and create an optimzed bitmap query
     * 
     * @param iconsetPath the iconset path that describes a iconset icon starting with the uuid for
     *        the iconset and then the path.
     * @param context to use when getting the UserIconDatabase
     * @return the string that represents the query
     */
    public static String GetIconBitmapQueryFromIconsetPath(String iconsetPath,
            Context context) {
        if (FileSystemUtils.isEmpty(iconsetPath)
                || !iconsetPath.contains("/")) {
            Log.w(TAG, "Failed to parse iconsetPath: " + iconsetPath); //, new Exception());
            return null;
        }

        String[] tokens = iconsetPath.split("/");
        if (tokens == null || tokens.length < 2) {
            Log.w(TAG, "Failed to split iconsetPath");
            return null;
        }

        //note group is tokens[1] currently not used for querying icon
        UserIcon icon = UserIconDatabase.instance(context).getIcon(tokens[0],
                tokens[tokens.length - 1], false);
        if (icon == null || !icon.isValid()) {
            Log.w(TAG, "Failed to query from iconsetPath: " + iconsetPath);
            return null;
        }

        return GetIconBitmapQuery(icon.getId());
    }

    /**
     * Get subgroup for the specified iconset path
     * 
     * @param group
     * @param iconsetPath
     * @param context 
     * @return
     */
    public static MapGroup GetOrAddSubGroup(MapGroup group, String iconsetPath,
            Context context) {
        if (group == null || FileSystemUtils.isEmpty(iconsetPath)) {
            Log.w(TAG, "Cannot get invalid child group");
            return group;
        }

        String[] tokens = iconsetPath.split("/");
        if (tokens == null || tokens.length < 2) {
            Log.w(TAG, "Failed to split iconsetPath");
            return group;
        }

        String groupName = tokens[1];
        if (FileSystemUtils.isEmpty(groupName)) {
            Log.w(TAG, "Cannot get child group without group name");
            return group;
        }

        boolean bNewGroup = false;
        MapGroup subGroup = null;
        synchronized (group) {
            subGroup = group.findMapGroup(groupName);
            if (subGroup == null) {
                bNewGroup = true;
                subGroup = new DefaultMapGroup(groupName);
                group.addGroup(subGroup);
            }
        }

        //if this group was added, lets set icon, but do so outside of
        //sync block since we are querying DB
        if (bNewGroup) {
            //attempt to get icon for this map group
            UserIcon.setGroupIcon(subGroup, groupName, context);
        }

        return subGroup;
    }

    public static void setGroupIcon(MapGroup group, String groupFilter,
            Context context) {
        UserIcon icon = UserIconDatabase.instance(context).getMostUsedIcon(
                groupFilter);
        if (icon != null && icon.isValid()) {
            String optimizedQuery = icon.getIconBitmapQuery();
            if (!FileSystemUtils.isEmpty(optimizedQuery)) {
                String iconUri = new SqliteMapDataRef(
                        UserIconDatabase.instance(context).getDatabaseName(),
                        optimizedQuery).toUri();
                group.setMetaString("iconUri", iconUri);
                //Log.d(TAG, "group=" + groupFilter + " iconUri=" + iconUri);
            }
        }
    }

    /**
     * Get Bitmap based on query created via GetIconBitmapQuery
     * 
     * @param queryUri
     * @return
     */
    public static byte[] GetIconBytes(String queryUri, Context context) {
        Uri u = null;
        if (queryUri != null) {
            try {
                u = Uri.parse(queryUri);
            } catch (Throwable ignored) {
            }
        }
        if (u == null) {
            Log.w(TAG, "Failed to parse URI: " + queryUri);
            return null;
        }

        final String scheme = u.getScheme();
        if (scheme == null || !scheme.equals("sqlite")) {
            Log.w(TAG, "Invalid scheme: " + queryUri);
            return null;
        }

        if (FileSystemUtils.isEmpty(u.getPath())
                || !u.getPath().contains("iconsets.sqlite")) {
            Log.w(TAG, "Failed to parse URI: " + queryUri);
            return null;
        }

        String query = null;
        try {
            final String q = u.getQueryParameter("query");
            if (q != null) {
                query = URLDecoder.decode(q,
                        FileSystemUtils.UTF8_CHARSET.name());
            }
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "Failed to parse query: " + queryUri, e);
            return null;
        }

        if (FileSystemUtils.isEmpty(query)) {
            Log.w(TAG, "Failed to parse icon ID from query: " + query);
            return null;
        }

        DatabaseIface database = UserIconDatabase.instance(context)
                .getReadableDatabase();
        if (database == null) {
            Log.w(TAG, "Failed to obtain database" + query);
            return null;
        }

        CursorIface result = null;
        try {
            result = database.query(query, null);
            if (result.moveToNext()) {
                return result.getBlob(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to execute query: " + query, e);
        } finally {
            if (result != null)
                result.close();
        }

        Log.w(TAG, "Failed to find icon for query: " + query);
        return null;
    }

    /**
     * Get Bitmap based on query created via GetIconBitmapQuery
     * 
     * @param queryUri the uri to use for getting the bitmap.
     * @return
     */
    public static Bitmap GetIconBitmap(String queryUri, Context context) {
        byte[] bytes = UserIcon.GetIconBytes(queryUri, context);
        if (FileSystemUtils.isEmpty(bytes)) {
            Log.w(TAG, "Failed to parse bitmap from query URI: " + queryUri);
            return null;
        }

        return UserIcon.decodeBitMap(bytes);
    }

    /**
     * Get icon ID based on query created via GetIconBitmapQuery
     * 
     * @param query
     * @return -1 upon error
     */
    public static int GetIconID(String query) {
        if (FileSystemUtils.isEmpty(query)) {
            Log.w(TAG, "Failed to parse empty query");
            return -1;
        }

        // all we need to get the icon is the icon ID
        String toMatch = UserIcon.COLUMN_ICONS_ID + "=";
        int index = query.indexOf(toMatch);
        if (index < 0) {
            Log.w(TAG, "Failed to parse icon ID from query: " + query);
            return -1;
        }

        int id = -1;
        try {
            id = Integer.parseInt(query.substring(index + toMatch.length()));
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse icon ID from query (2): " + query, e);
            return -1;
        }

        return id;
    }

    /**
     * Get Bitmap based on query created via GetIconBitmapQuery
     * 
     * @param query
     * @param bBitmap 
     * @return
     */
    public static UserIcon GetIcon(String query, boolean bBitmap,
            Context context) {
        int id = GetIconID(query);
        if (id < 0) {
            Log.w(TAG, "Failed to parse icon ID from query (3): " + query);
            return null;
        }

        // extracted icon ID from SQL, now query database
        return UserIconDatabase.instance(context).getIcon(id, bBitmap);
    }

    public static Bitmap decodeBitMap(byte[] blob) {
        if (FileSystemUtils.isEmpty(blob))
            return null;

        return BitmapFactory.decodeByteArray(blob, 0, blob.length);
    }
}
