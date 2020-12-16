
package com.atakmap.android.tools.menu;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;

import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.tools.ActionBarReceiver;
import com.atakmap.android.tools.menu.AtakActionBarMenuData.Orientation;
import com.atakmap.app.system.FlavorProvider;
import com.atakmap.app.system.SystemComponentLoader;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.locale.LocaleUtil;

import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the collection of all saved/configured action bar layouts
 * 
 * 
 */
public class AtakActionBarListData {

    private static final String TAG = "AtakActionBarListData";

    private static final int MAX_MENUS = 12; // 12 for landscape, 12 for portrait
    private static final String ACTION_BAR_DIR = "config" + File.separatorChar
            + "actionbars";
    public static final String CURRENT_ACTION_BAR_LAND_LABEL = "actionBar.Label.Current.Landscape";
    public static final String CURRENT_ACTION_BAR_PORT_LABEL = "actionBar.Label.Current.Portrait";
    public static final String DEFAULT_LABEL = "Default";

    private static final String version = "3.11";
    private final List<AtakActionBarMenuData> actionbars = new ArrayList<>();

    public AtakActionBarListData() {
    }

    public AtakActionBarListData(AtakActionBarListData copy) {
        if (copy != null && copy.isValid()) {
            for (AtakActionBarMenuData actionbar : copy.getActionBars()) {
                actionbars.add(new AtakActionBarMenuData(actionbar));
            }
        }
    }

    public boolean isValid() {

        if (FileSystemUtils.isEmpty(version))
            return false;

        if (FileSystemUtils.isEmpty(actionbars))
            return false;

        for (AtakActionBarMenuData actionBar : actionbars) {
            if (actionBar == null || !actionBar.isValid())
                return false;
        }

        return true;
    }

    @Override
    public boolean equals(final Object o) {
        if (o instanceof AtakActionBarListData) {
            AtakActionBarListData c = (AtakActionBarListData) o;
            return this.equals(c);
        } else {
            return super.equals(o);
        }
    }

    public boolean equals(AtakActionBarListData c) {
        return FileSystemUtils.isEquals(getActionBars(), c.getActionBars());
    }

    @Override
    public int hashCode() {
        return 31
                * ((getActionBars() == null ? 0 : getActionBars().hashCode()));
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("" + getActionBars().size() + ";");
        for (AtakActionBarMenuData actionbar : getActionBars()) {
            sb.append(actionbar.toString()).append("; ");
        }

        return sb.toString();
    }

    public String getVersion() {
        return version;
    }

    public List<AtakActionBarMenuData> getActionBars() {
        return actionbars;
    }

    public List<AtakActionBarMenuData> getActionBars(
            AtakActionBarMenuData.Orientation orientation) {
        List<AtakActionBarMenuData> matching = new ArrayList<>();
        if (!isValid())
            return matching;

        for (AtakActionBarMenuData actionbar : getActionBars()) {
            if (actionbar != null && actionbar.isValid()
                    && actionbar.getOrientation() == orientation)
                matching.add(actionbar);
        }
        return matching;
    }

    public boolean has(String label,
            AtakActionBarMenuData.Orientation orientation) {
        return get(label, orientation) != null;
    }

    public AtakActionBarMenuData get(String label,
            AtakActionBarMenuData.Orientation orientation) {
        if (FileSystemUtils.isEmpty(label))
            return null;

        for (AtakActionBarMenuData cur : getActionBars(orientation)) {
            if (cur != null && cur.isValid()
                    && cur.getLabel().equalsIgnoreCase(label)) {
                return cur;
            }
        }

        return null;
    }

    public boolean isFull(AtakActionBarMenuData.Orientation orientation) {
        return getActionBars(orientation).size() >= MAX_MENUS;
    }

    public boolean add(AtakActionBarMenuData actionbar) {
        if (actionbar == null || !actionbar.isValid()) {
            Log.w(TAG, "Not adding invalid action bar");
            return false;
        }

        // ensure unique name for action bar
        for (AtakActionBarMenuData cur : getActionBars(actionbar
                .getOrientation())) {
            if (cur != null && cur.isValid()
                    && cur.getLabel().equalsIgnoreCase(actionbar.getLabel())) {
                Log.w(TAG,
                        "Action bar already exists with label: "
                                + actionbar.getLabel());
                return false;
            }
        }

        Log.d(TAG, "Adding action bar: " + actionbar.toString());
        return getActionBars().add(actionbar);
    }

    public boolean remove(AtakActionBarMenuData actionbar) {
        if (actionbar == null) {
            Log.w(TAG, "Not removing invalid action bar");
            return false;
        }

        if (getActionBars().contains(actionbar)) {
            Log.d(TAG, "Removing action bar: " + actionbar.toString());
            return getActionBars().remove(actionbar);
        } else {
            Log.w(TAG, "Not removing action bar: " + actionbar.toString());
            return false;
        }
    }

    /**
     * Get the configured action bar based on current ATAK orientation setting and current action
     * bar label (stored in prefs)
     * 
     * @param prefs
     * @return
     */
    public AtakActionBarMenuData getActionBar(SharedPreferences prefs,
            Context context) {
        Orientation orientation = getOrientation(context);
        String orientationPrefName = getOrientationPrefName(orientation);

        return getActionBar(
                prefs.getString(orientationPrefName, DEFAULT_LABEL),
                orientation);
    }

    public static String getOrientationPrefName(Orientation orientation) {
        return (orientation == Orientation.landscape)
                ? CURRENT_ACTION_BAR_LAND_LABEL
                : CURRENT_ACTION_BAR_PORT_LABEL;
    }

    /**
     * Get the specified action bar
     * 
     * @param label
     * @return
     */
    public AtakActionBarMenuData getActionBar(String label,
            AtakActionBarMenuData.Orientation orientation) {
        if (!isValid())
            return null;

        if (FileSystemUtils.isEmpty(label))
            return null;

        for (AtakActionBarMenuData actionBar : getActionBars(orientation)) {
            if (actionBar != null && actionBar.isValid()
                    && actionBar.getLabel().equals(label))
                return actionBar;
        }
        Log.w(TAG, "No action bar found for label: " + label);

        for (AtakActionBarMenuData actionBar : getActionBars(orientation)) {
            if (actionBar != null && actionBar.isValid()
                    && actionBar.getLabel().equals(DEFAULT_LABEL))
                return actionBar;
        }
        Log.w(TAG, "No action bar found for default label: " + DEFAULT_LABEL);

        if (getActionBars().size() > 0) {
            AtakActionBarMenuData actionBar = getActionBars().get(0);
            Log.d(TAG, "Returning first action bar: " + actionBar.getLabel());
            return actionBar;
        }

        Log.w(TAG, "No action bar found at all...");
        return null;
    }

    public static AtakActionBarMenuData.Orientation getOrientation(
            Context context) {
        switch (AtakPreferenceFragment.getOrientation(context)) {
            case ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:
            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT: {
                return AtakActionBarMenuData.Orientation.portrait;
            }
            case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
            case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
            default: {
                return AtakActionBarMenuData.Orientation.landscape;
            }
        }
    }

    public static boolean reset(Context context, boolean full) {
        File actionBarDir = FileSystemUtils.getItem(ACTION_BAR_DIR);
        File[] files = IOProviderFactory.listFiles(actionBarDir);
        if (files != null) {
            for (File f : files) {
                final String fname = f.toString();
                if (fname.endsWith("_portrait.xml")
                        || fname.endsWith("_landscape.xml")) {
                    if (!IOProviderFactory.delete(f))
                        Log.d(TAG, "could not remove: " + fname);
                }
            }
        }
        rollout(context, full);
        return true;
    }

    private static boolean rollout(Context context, boolean full) {
        FileSystemUtils.copyFromAssetsToStorageFile(context,
                "actionbar/default_landscape.xml",
                ACTION_BAR_DIR + "/default_landscape.xml", true);
        FileSystemUtils.copyFromAssetsToStorageFile(context,
                "actionbar/default_portrait.xml",
                ACTION_BAR_DIR + "/default_portrait.xml", true);
        if (full) {
            final String[] list;

            list = new String[] {
                    "planning", "minimal"
            };

            for (String s : list) {
                FileSystemUtils.copyFromAssetsToStorageFile(context,
                        "actionbar/" + s + "_landscape.xml",
                        ACTION_BAR_DIR + "/" + s + "_landscape.xml", true);
                FileSystemUtils.copyFromAssetsToStorageFile(context,
                        "actionbar/" + s + "_portrait.xml",
                        ACTION_BAR_DIR + "/" + s + "_portrait.xml", true);
            }
            FlavorProvider fp = SystemComponentLoader.getFlavorProvider();
            if (fp != null)
                fp.rolloutActionBars();

        }

        return true;

    }

    /**
     * Get the list of configured action bar layouts from the default location
     * 
     * @return the AtakActionBarListData object that refers to the action bar items.
     */
    public static AtakActionBarListData loadActionBars(Context context) {

        File actionBarDir = FileSystemUtils.getItem(ACTION_BAR_DIR);
        final File dl = new File(actionBarDir, "default_landscape.xml");
        final File dp = new File(actionBarDir, "default_portrait.xml");
        if (!IOProviderFactory.exists(dl) || !IOProviderFactory.exists(dp)) {
            rollout(context, true);
        } else {
            rollout(context, false);
        }
        return loadActionBars(actionBarDir);
    }

    /** 
     * Gets a list of configured action bar layouts from a custom location.
     */
    public static AtakActionBarListData loadActionBars(File directory) {

        AtakActionBarListData data = new AtakActionBarListData();

        Serializer serializer = new Persister();
        try {
            File[] files = IOProviderFactory.listFiles(directory);
            if (files != null) {
                for (File f : files) {
                    FileInputStream fis = null;
                    try {
                        final String fname = f.toString();
                        if (fname.endsWith("_portrait.xml")
                                || fname.endsWith("_landscape.xml")) {
                            fis = IOProviderFactory.getInputStream(f);
                            AtakActionBarMenuData actionBar = serializer.read(
                                    AtakActionBarMenuData.class, fis);
                            for (ActionMenuData amd : actionBar.getActions()) {
                                amd.deferredLoad();
                            }
                            data.add(actionBar);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "error loading actionbar data: " + f, e);
                    } finally {
                        try {
                            if (fis != null)
                                fis.close();
                        } catch (Exception ignore) {
                        }
                    }
                }
                return data;
            } else {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    public boolean save() {
        File actionBarDir = FileSystemUtils.getItem(ACTION_BAR_DIR);
        //sends intent out to all receivers that need to catch
        // changes in custom action bar setups
        ActionBarReceiver.getInstance().updatePluginActionBars();

        if (!IOProviderFactory.exists(actionBarDir))
            IOProviderFactory.mkdirs(actionBarDir);
        for (AtakActionBarMenuData actionBar : actionbars) {
            File actionBarFile = new File(actionBarDir,
                    FileSystemUtils.sanitizeWithSpacesAndSlashes(
                            actionBar.getLabel() + "_"
                                    + actionBar.getOrientation()
                                    + ".xml".toLowerCase(
                                            LocaleUtil.getCurrent())));
            FileOutputStream fos = null;
            try {
                fos = IOProviderFactory.getOutputStream(actionBarFile);
                Serializer serializer = new Persister();
                serializer.write(actionBar, fos);
            } catch (Exception e) {
                Log.e(TAG, "failed to write Action bar to: " + actionBarFile,
                        e);
            } finally {
                try {
                    if (fos != null)
                        fos.close();
                } catch (Exception ignore) {
                }
            }
        }
        return true;
    }
}
