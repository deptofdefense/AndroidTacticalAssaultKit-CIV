
package com.atakmap.android.tools.menu;

import android.content.Context;
import android.content.res.Resources.NotFoundException;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.TypedValue;

import com.atak.plugins.impl.PluginMapComponent;
import com.atakmap.android.tools.AtakLayerDrawableUtil;
import com.atakmap.app.system.FlavorProvider;
import com.atakmap.app.system.SystemComponentLoader;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 
 */
public class ActionMenuData implements Parcelable {

    private static final String TAG = "ActionMenuData";
    public static final String PLACEHOLDER_TITLE = "PLACEHOLDER";

    public enum PreferredMenu {
        actionBar,
        overflow,
        hidden
    }

    @Attribute
    private String ref;

    /**
     * See ActionMenuData.PreferredMenu
     */
    @Attribute
    private String preferredMenu;

    PersistedActionMenu pam;

    /*
     * Transient runtime/selected state
     */
    private boolean bSelected = false;
    private boolean bEnabled = false;

    private static final Map<String, Drawable> cachedIconMap = new HashMap<>();

    public ActionMenuData() {
        pam = new PersistedActionMenu();
    }

    /**
     * Experimental hook to allow for AirOverlays to poison the cache.
     * When the cache is poisoned, the action bar needs to be refreshed
     *
     * @param s is the string that relates to the icon style to be poisoned.
     * @param d the drawable to use.
     */
    public static void addIconCache(final String s, final Drawable d) {
        cachedIconMap.put(s, d);
    }

    /**
     * Experimental hook to allow for AirOverlays remove the poison the cache.
     * When the cache is no longer poisoned, the action bar needs to be refreshed
     *
     * @param s is the string that relates to the icon style to be unpoisoned.
     */
    public static void removeIconCache(final String s) {
        cachedIconMap.remove(s);
    }

    /**
     * An action menu data item that corresponds with an action or set of actions when the entry
     * is clicked or long pressed.
     *
     * @param ref              the reference for this specific menu item.   This should be unique and not mutable.
     * @param title            the title of the menu item
     * @param iconPath         the path for the icon, can be a string path or in the case of being called
     *                         from a plugin, the string resulting from the call to @see
     *                         PluginMapComponent.addPluginIcon(toolDescriptor) and the addPluginIcon uses
     *                         the toolDescriptor class getIcon() to return a drawable.   This must also
     *                         implement the short name and description.
     * @param enabledIconPath  the icon when enabled, can be null.
     * @param selectedIconPath the icon when selected, can be null.
     * @param preferredMenu    the preferred menu - should be "overflow".
     * @param hideable         is the icon hidable by the user
     * @param clickData        the click data during a click or longpress
     * @param bSelected        selected by default
     * @param bEnabled         enabled by default
     * @param baseline         must be false.
     */
    public ActionMenuData(String ref, String title, String iconPath,
            String enabledIconPath, String selectedIconPath,
            String preferredMenu, boolean hideable,
            List<ActionClickData> clickData,
            boolean bSelected, boolean bEnabled, boolean baseline) {
        pam = new PersistedActionMenu();

        this.ref = ref;
        this.preferredMenu = preferredMenu;

        pam.title = title;
        pam.iconPath = iconPath;
        pam.enabledIconPath = enabledIconPath;
        pam.selectedIconPath = selectedIconPath;
        pam.hideable = hideable;
        pam.actionClickData = clickData;
        pam.baseline = baseline;

        this.bSelected = bSelected;
        this.bEnabled = bEnabled;
    }

    public ActionMenuData(ActionMenuData copy) {
        if (copy != null && copy.isValid()) {
            pam = new PersistedActionMenu();
            ref = copy.ref;
            preferredMenu = copy.preferredMenu;

            pam.title = copy.pam.title;
            pam.iconPath = copy.pam.iconPath;
            pam.enabledIconPath = copy.pam.enabledIconPath;
            pam.selectedIconPath = copy.pam.selectedIconPath;
            pam.hideable = copy.pam.hideable;
            pam.actionClickData = copy.getActionClickData();
            pam.baseline = copy.pam.baseline;
        } else {
            Log.w(TAG,
                    "Invalid copy: "
                            + (copy == null ? "null" : copy.toString()));
        }
    }

    /**
     * Create a placeholder with a unique broadcast so it .equals() fails,
     * and each is unique...
     * sort of a hack, should probably use a UUID
     *
     * @return
     */
    public static ActionMenuData createPlaceholder() {
        return createPlaceholder(UUID.randomUUID().toString());
    }

    /**
     * Create a placeholder with a specific associated id.  This is used to link a placeholder
     * with a "missing" plugin action, so that dynamically added actions can be replaced in the
     * same spot after a restart.
     *
     * @param id Id of the associated action
     * @return
     */
    public static ActionMenuData createPlaceholder(String id) {
        ArrayList<ActionClickData> placeholderActionClick = new ArrayList<>();
        String uid = PLACEHOLDER_TITLE + "-" + id;
        placeholderActionClick.add(new ActionClickData(new ActionBroadcastData(
                uid, null),
                ActionClickData.CLICK));
        return new ActionMenuData(uid,
                PLACEHOLDER_TITLE, "ic_menu_actionbar_placeholder", null, null,
                PreferredMenu.actionBar.toString(), false,
                placeholderActionClick, false, false, true);
    }

    String getRef() {
        return ref;
    }

    public boolean isSelected() {
        return bSelected;
    }

    public void setSelected(boolean selected) {
        bSelected = selected;
    }

    public boolean isEnabled() {
        return bEnabled;
    }

    public void setEnabled(boolean enabled) {
        bEnabled = enabled;
    }

    public String getTitle() {
        if (pam.title == null)
            return "";

        if (pam.baseline) {
            try {
                Class<?> c = com.atakmap.app.R.string.class;
                Field f = null;
                FlavorProvider fp = SystemComponentLoader.getFlavorProvider();
                if (fp != null && fp.hasMilCapabilities()) {
                    try {
                        f = c.getField("mil_" + pam.title);
                    } catch (Exception ignored) {
                    }
                }

                if (f == null)
                    f = c.getField(pam.title);

                int i = f.getInt(null);
                Context ctx = com.atakmap.android.maps.MapView.getMapView()
                        .getContext();
                if (ctx != null)
                    return ctx.getString(i);
            } catch (Exception e) {
                //                Log.e(TAG, "error, could not find id: " + pam.title, e);
                Log.e(TAG, "error, could not find id: " + pam.title);
            }
        }
        return pam.title;
    }

    /**
     * Return icon path based on selection state
     *
     * @return the stringified icon path
     */
    public String getIcon() {
        String path = pam.iconPath;

        if (bSelected && bEnabled
                && !FileSystemUtils.isEmpty(pam.enabledIconPath)) {
            path = pam.enabledIconPath;
        } else if (bSelected
                && !FileSystemUtils.isEmpty(pam.selectedIconPath)) {
            path = pam.selectedIconPath;
        }
        return path;
    }

    /**
     * Attempt to create Icon for action
     *
     * @param context
     * @return
     * @throws NotFoundException
     */
    public Drawable getIcon(Context context) throws NotFoundException {

        // TODO this may be quicker approach, but Proguard could cause issues on reflection and
        // optimizations of unreferenced drawables?
        // Class res = R.drawable.class;
        // Field field = res.getField(getIcon());
        // int resID = field.getInt(null);
        // context.getResources().getDrawable(resID);

        final String currIconPath = getIcon();

        final Drawable cachedIcon = cachedIconMap.get(currIconPath);

        if (cachedIcon != null)
            return cachedIcon;

        //for custom action bar items(plugin)
        if (currIconPath.contains("plugin://")) {
            String path = pam.iconPath.replace("plugin://", "");
            Drawable drawRet = Drawable.createFromPath(path);
            cachedIconMap.put(currIconPath, drawRet);
            return drawRet;
        }

        int resID = context.getResources().getIdentifier(currIconPath,
                "drawable",
                context.getPackageName());

        if (resID == 0) // this means that a resource wasn't found with the icon's identifier
        {
            // look it up in the PluginMapComponent
            return PluginMapComponent.getPluginIconWithId(currIconPath);
        }

        // return context.getResources().getDrawable(resID);
        // scale it properly for action bar

        InputStream is = null;
        Drawable drawRet;

        try {
            Log.d(TAG, "caching: " + currIconPath);
            drawRet = Drawable.createFromResourceStream(
                    context.getResources(),
                    new TypedValue(),
                    is = context.getResources().openRawResource(resID), null);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ignored) {
                }
            }
        }

        if (drawRet != null)
            cachedIconMap.put(currIconPath, drawRet);

        if (drawRet == null) {
            //try a LayerDrawable

            LayerDrawable ld = (LayerDrawable) context.getResources()
                    .getDrawable(resID);
            if (ld != null) {
                AtakLayerDrawableUtil inst = AtakLayerDrawableUtil
                        .getInstance(context);
                //will only add it if it doesn't have it already
                inst.addLayerDrawableBroadcastString(currIconPath);
                int count = inst.getBadgeInt(currIconPath);
                if (count > 0) {
                    inst.setBadgeCount(ld, count);
                }
            }

            drawRet = ld;
        }

        return drawRet;
    }

    public PreferredMenu getPreferredMenu() {
        return PreferredMenu.valueOf(preferredMenu);
    }

    public void setPreferredMenu(PreferredMenu preferred) {
        preferredMenu = preferred.toString();
    }

    public boolean isHideable() {
        return pam.hideable;
    }

    public boolean isPlaceholder() {
        return pam.title != null && pam.title.startsWith(PLACEHOLDER_TITLE);
    }

    public boolean isBaseline() {
        return pam.baseline;
    }

    public ActionClickData getActionClickData(String type) {
        for (ActionClickData data : pam.actionClickData) {
            if (data.getActionType().equals(type))
                return data;
        }
        return null;
    }

    public List<ActionClickData> getActionClickData() {
        return pam.actionClickData;
    }

    public boolean hasClickData() {
        if (pam.actionClickData != null && pam.actionClickData.size() > 0) {
            for (ActionClickData data : pam.actionClickData) {
                if (!data.isValid())
                    return false; //Found bad data - puke
            }
            return true; //all data present is valid - Great!
        }
        return false; //no data available - cry
    }

    public boolean isValid() {

        if (FileSystemUtils.isEmpty(pam.title) ||
                FileSystemUtils.isEmpty(pam.iconPath) ||
                FileSystemUtils.isEmpty(preferredMenu) ||
                !hasClickData())
            return false;

        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ActionMenuData) {
            ActionMenuData c = (ActionMenuData) o;
            return this.equals(c);
        } else {
            return super.equals(o);
        }
    }

    public boolean equals(ActionMenuData c) {
        if (!isValid() || !c.isValid()) {
            return false;
        }

        if (!FileSystemUtils.isEquals(getTitle(), c.getTitle())) {
            return false;
        }

        if (!FileSystemUtils.isEquals(pam.iconPath, c.pam.iconPath)) {
            return false;
        }

        if (!FileSystemUtils.isEquals(pam.enabledIconPath,
                c.pam.enabledIconPath)) {
            return false;
        }

        if (!FileSystemUtils.isEquals(pam.selectedIconPath,
                c.pam.selectedIconPath)) {
            return false;
        }

        if (pam.hideable != c.pam.hideable) {
            return false;
        }

        if (pam.baseline != c.pam.baseline) {
            return false;
        }

        if (getPreferredMenu() != c.getPreferredMenu()) {
            return false;
        }

        if (getActionClickData() != null
                && !getActionClickData().equals(c.getActionClickData())) {
            return false;
        }

        if (getActionClickData() != c.getActionClickData()) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return 31 * ((getTitle() == null ? 0 : getTitle().hashCode())
                + (pam.iconPath == null ? 0
                        : pam.iconPath.hashCode()));
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(getTitle())
                .append(" ")
                .append(pam.iconPath)
                .append(" ");
        for (ActionClickData data : pam.actionClickData) {
            builder.append(data.toString()).append(" ");
        }
        builder.append(getPreferredMenu());
        return builder.toString();
    }

    public int getId() {
        if (!isValid()) {
            return 0;
        }

        if (ref != null)// && ref.startsWith("plugin://"))
            return ref.hashCode();

        int hashCode = pam.title.hashCode() + pam.iconPath.hashCode();

        if (hasClickData()) {
            for (ActionClickData data : pam.actionClickData) {
                hashCode += data.getBroadcast().getAction().hashCode();
            }
        }

        return 31 * hashCode;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {

        if (isValid()) {
            parcel.writeString(ref);
            parcel.writeString(preferredMenu);

            parcel.writeString(pam.title);
            parcel.writeString(pam.iconPath);
            parcel.writeString(pam.enabledIconPath);
            parcel.writeString(pam.selectedIconPath);
            parcel.writeByte(pam.hideable ? (byte) 1 : (byte) 0);
            parcel.writeByte(bSelected ? (byte) 1 : (byte) 0);
            parcel.writeByte(bEnabled ? (byte) 1 : (byte) 0);
            parcel.writeByte(pam.baseline ? (byte) 1 : (byte) 0);
            for (ActionClickData data : pam.actionClickData) {
                parcel.writeParcelable(data, flags);
            }
        }
    }

    private ActionMenuData(Parcel in) {
        pam = new PersistedActionMenu();
        readFromParcel(in);
    }

    private void readFromParcel(Parcel in) {
        ref = in.readString();
        preferredMenu = in.readString();

        pam.title = in.readString();
        pam.iconPath = in.readString();
        pam.enabledIconPath = in.readString();
        pam.selectedIconPath = in.readString();
        pam.hideable = in.readByte() != 0;
        bSelected = in.readByte() != 0;
        bEnabled = in.readByte() != 0;
        pam.baseline = in.readByte() != 0;
        pam.actionClickData = in.readArrayList(ActionClickData.class
                .getClassLoader());
    }

    public static final Parcelable.Creator<ActionMenuData> CREATOR = new Parcelable.Creator<ActionMenuData>() {
        @Override
        public ActionMenuData createFromParcel(Parcel in) {
            return new ActionMenuData(in);
        }

        @Override
        public ActionMenuData[] newArray(int size) {
            return new ActionMenuData[size];
        }
    };

    public static class PersistedActionMenu {

        @Attribute
        private String title;

        @Attribute
        private String iconPath;

        @Attribute(required = false)
        private String selectedIconPath;

        @Attribute(required = false)
        private String enabledIconPath;

        /**
         * True for baseline tools, false e.g. for plugins
         */
        @Attribute(required = false)
        private boolean baseline;

        /**
         * Can this be placed in the "Hidden" menu
         */
        @Attribute
        private boolean hideable;

        /**
         * Action to take when the menu is launched
         */

        @ElementList(required = true, inline = true, entry = "ClickData")
        private List<ActionClickData> actionClickData;
    }

    // for the imutable asset:// references
    private final static Map<String, PersistedActionMenu> pamCache = new HashMap<>();

    public void deferredLoad() {
        //Log.d(TAG, "deferred loading of the ActionMenuData");

        if (ref == null)
            return;

        if (ref.startsWith("asset://")) {

            // this is fine because asset:// refs are imutable
            PersistedActionMenu pam = pamCache.get(ref);
            if (pam != null) {
                this.pam = pam;
                return;
            }

            Context ctx = com.atakmap.android.maps.MapView.getMapView()
                    .getContext();

            try {
                String xml = FileSystemUtils.copyStreamToString(
                        FileSystemUtils.getInputStreamFromAsset(ctx,
                                ref.substring("asset://".length())),
                        true,
                        FileSystemUtils.UTF8_CHARSET);

                pam = fromItemXml(xml);
                if (pam != null) {
                    // this is fine because asset:// refs are imutable
                    pamCache.put(ref, pam);
                    this.pam = pam;

                    // success
                    return;
                } else {
                    Log.e(TAG, "unable to load: " + ref);
                }
            } catch (IOException ioe) {
                Log.e(TAG, "unable to load: " + ref, ioe);

            }

        } else if (ref.startsWith("plugin://")) {
            Log.e(TAG, "no need to defer load: " + ref);
            String[] s = ref.substring("plugin://".length())
                    .split("/");
            if (s.length == 2) {
                this.pam.iconPath = "ic_menu_actionbar_placeholder";
                this.pam.title = s[1];
                this.pam.baseline = false;

                List<ActionClickData> placeholderActionClick = new ArrayList<>();
                placeholderActionClick.add(new ActionClickData(
                        new ActionBroadcastData(
                                PLACEHOLDER_TITLE + "-"
                                        + UUID.randomUUID().toString(),
                                null),
                        ActionClickData.CLICK));
                this.pam.actionClickData = placeholderActionClick;
                // success
                return;
            } else {
                Log.e(TAG, "unable to load: " + ref);
            }
        }

        // messy for now, but assume an error condition has occurred or the reference is 
        // a placholder - put in a placeholder

        List<ActionClickData> placeholderActionClick = new ArrayList<>();
        placeholderActionClick.add(new ActionClickData(new ActionBroadcastData(
                PLACEHOLDER_TITLE + "-" + UUID.randomUUID().toString(), null),
                ActionClickData.CLICK));
        this.bSelected = false;
        this.bEnabled = false;
        this.pam.title = PLACEHOLDER_TITLE;
        this.pam.iconPath = "ic_menu_actionbar_placeholder";
        this.pam.selectedIconPath = null;
        this.pam.enabledIconPath = null;
        this.pam.hideable = false;
        this.pam.actionClickData = placeholderActionClick;
        this.pam.baseline = true;

        // a custom added action can have any ref, so it will get a placeholder, but we want it
        // to be treated like a "plugin://".  Note that only the ref and baseline fields matter,
        // the rest is replaced later
        if (!ref.startsWith(PLACEHOLDER_TITLE)) {
            this.pam.baseline = false;
            this.pam.title = ref; // just for easier debugging
        }
    }

    /**
     * Parse the specified action bar string
     *
     * @param xml the xml to turn into a action menu
     * @return the action menu
     */
    public static PersistedActionMenu fromItemXml(String xml) {
        Serializer serializer = new Persister();
        try {
            return serializer.read(PersistedActionMenu.class, xml);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load action bar: " + xml, e);
            return null;
        }
    }

}
