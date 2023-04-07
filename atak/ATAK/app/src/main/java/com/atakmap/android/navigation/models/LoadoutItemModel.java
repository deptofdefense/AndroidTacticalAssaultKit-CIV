
package com.atakmap.android.navigation.models;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.atakmap.android.math.MathUtils;
import com.atakmap.android.navigation.NavButtonManager;
import com.atakmap.android.navigation.views.loadout.LoadoutManager;
import com.atakmap.android.util.ATAKConstants;
import com.atakmap.coremap.filesystem.FileSystemUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Model used for a button loadout (the tool buttons in the top left)
 */
public class LoadoutItemModel {

    private static final int VERSION = 2;
    private static final String PERSIST_SEPERATOR = "###";

    // The UID for this loadout
    private final String uid;

    // The title of this loadout
    private String title;

    // Buttons mapped by their loadout index
    private final HashMap<String, NavButtonModel> buttons = new HashMap<>();

    // Unique set of buttons used for contains checks
    private final Set<NavButtonModel> buttonSet = new HashSet<>();

    // Set of hidden tools for this loadout mapped by their model reference
    private final Set<String> hiddenTools = new HashSet<>();

    // Tool sort order mapped by reference
    private final Map<String, Integer> sortOrder = new HashMap<>();

    // Whether or not this is a temporary loadout (not persisted)
    // Used during editing
    private boolean temporary;

    /**
     * Generates a navigation bar load out with just a provided name.
     * @param title the provided name
     */
    public LoadoutItemModel(String title) {
        this(UUID.randomUUID().toString(), title, null);
    }

    /**
     * Generates a navigation bar load out with a provided name and loadout
     * @param title the provided name
     * @param copy Loadout copy
     */
    public LoadoutItemModel(String title, LoadoutItemModel copy) {
        this(title);
        buttons.putAll(copy.buttons);
        buttonSet.addAll(copy.buttonSet);
        hiddenTools.addAll(copy.hiddenTools);
        sortOrder.putAll(copy.sortOrder);
    }

    public LoadoutItemModel(LoadoutItemModel copy) {
        this(copy.title, copy);
    }

    /**
     * Generates a navigation bar load out with a reference and a name
     * @param uid the reference should be unique across all navigation bars
     * @param title the title for the load out
     */
    public LoadoutItemModel(String uid, String title) {
        this(uid, title, null);
    }

    /**
     * Generates a navigation bar load out with a reference and a name
     * @param uid the reference should be unique across all navigation bars
     * @param title the title for the load out
     * @param stringSet the set of navigation buttons associated with the navigation bar load out
     *                  (can be null).
     */
    public LoadoutItemModel(String uid, String title,
            List<String> stringSet) {
        this.uid = uid;
        this.title = title;

        if (stringSet != null) {
            // Convert button string set to list
            for (String str : stringSet) {
                String[] buttonValues = str.split(PERSIST_SEPERATOR);
                if (buttonValues.length != 2)
                    continue;

                // Each entry is the position key + the button name
                String buttonKey = buttonValues[0];
                String buttonReference = buttonValues[1];
                setButton(buttonKey, buttonReference);
            }
        }
    }

    /**
     * Get the unique ID for this loadout
     * @return the unique identifier for this loadout
     */
    public final String getUID() {
        return uid;
    }

    /**
     * Get the title of this loadout
     * @return Title
     */
    public String getTitle() {
        return this.title;
    }

    /**
     * Set the displayed title for this loadout
     * @param title Title
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Set the button at the given position key
     * @param key Position key
     * @param btn Button (null to remove)
     */
    public void setButton(String key, NavButtonModel btn) {
        if (btn != null) {
            buttons.put(key, btn);
            buttonSet.clear();
            buttonSet.addAll(buttons.values());
            setToolVisible(btn.getReference(), true);
        } else
            removeButton(key);
    }

    /**
     * Set the given button to a model by its reference
     * If the reference is not found a blank placeholder will be used
     * @param key Button key
     * @param reference Button model reference
     */
    public void setButton(String key, String reference) {
        NavButtonModel mdl = NavButtonManager.getInstance()
                .getModelByReference(reference);
        if (mdl == null)
            mdl = new NavButtonModel(reference, null);
        setButton(key, mdl);
    }

    /**
     * Set the button at the given position
     * @param index Position index
     * @param btn Button (null to remove)
     */
    public void setButton(int index, NavButtonModel btn) {
        setButton(ATAKConstants.getPackageName() + ":id/tak_nav_button_"
                + index, btn);
    }

    /**
     * Set the button at the given position
     * @param index Position index
     * @param reference Button reference
     */
    public void setButton(int index, String reference) {
        NavButtonModel btn = NavButtonManager.getInstance()
                .getModelByReference(reference);
        setButton(index, btn);
    }

    /**
     * Set whether to show the zoom button in the loadout
     * @param showZoom True to show the zoom button
     */
    public void showZoomButton(boolean showZoom) {
        String key = ATAKConstants.getPackageName() + ":id/tak_nav_zoom";
        if (showZoom)
            setButton(key, "zoom");
        else
            removeButton(key);
    }

    /**
     * Remove button at the given position key
     * @param key Position key
     */
    public void removeButton(String key) {
        NavButtonModel btn = buttons.remove(key);
        if (btn != null)
            buttonSet.remove(btn);
    }

    /**
     * Remove button with the given button model
     * @param btn Button model
     */
    public void removeButton(NavButtonModel btn) {
        if (btn == null)
            return;
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, NavButtonModel> e : buttons.entrySet()) {
            NavButtonModel cached = e.getValue();
            if (cached != null && cached.getReference().equals(
                    btn.getReference()))
                toRemove.add(e.getKey());
        }
        for (String key : toRemove)
            removeButton(key);
    }

    /**
     * Get the current number of buttons
     * @return Number of buttons
     */
    public int getNumButtons() {
        return buttons.size();
    }

    /**
     * Get a button given its position key
     * @param key Position key
     * @return Button or null if not found
     */
    public NavButtonModel getButton(String key) {
        return buttons.get(key);
    }

    /**
     * Get the key for a given button model
     * @param mdl Button model
     * @return Button key or null if the loadout does not contain this button
     */
    public String getButtonKey(NavButtonModel mdl) {
        if (containsButton(mdl)) {
            for (Map.Entry<String, NavButtonModel> e : buttons.entrySet()) {
                if (e.getValue() == mdl)
                    return e.getKey();
            }
        }
        return null;
    }

    /**
     * Check if this loadout contains the given button model
     * @param btn Button to check
     * @return True if the button is contained in this loadout
     */
    public boolean containsButton(NavButtonModel btn) {
        return buttonSet.contains(btn);
    }

    /**
     * Check if this loadout contains the given button model
     * @param reference Button model reference
     * @return True if the button model exists and is contained in this loadout
     */
    public boolean containsButton(String reference) {
        NavButtonModel mdl = NavButtonManager.getInstance()
                .getModelByReference(reference);
        return containsButton(mdl);
    }

    /**
     * Check if this loadout is the default loadout
     * @return True if default
     */
    public boolean isDefault() {
        return LoadoutManager.DEFAULT_LOADOUT_UID.equals(getUID());
    }

    /**
     * Set whether a tool in this loadout is visible or not
     * This determines whether the tool shows in the "overflow" menu
     * list of available tools
     * @param modelRef {@link NavButtonModel#getReference()}
     * @param visible True if visible, false if hidden
     */
    public void setToolVisible(String modelRef, boolean visible) {
        if (visible)
            hiddenTools.remove(modelRef);
        else
            hiddenTools.add(modelRef);
    }

    /**
     * Set whether a tool in this loadout is visible or not
     * This determines whether the tool shows in the "overflow" menu
     * list of available tools
     * @param modelRefs List of {@link NavButtonModel#getReference()}
     * @param visible True if visible, false if hidden
     */
    public void setToolsVisible(Collection<String> modelRefs, boolean visible) {
        if (visible)
            hiddenTools.removeAll(modelRefs);
        else
            hiddenTools.addAll(modelRefs);
    }

    /**
     * Check whether a tool is visible
     * @param modelRef {@link NavButtonModel#getReference()}
     * @return True if visible
     */
    public boolean isToolVisible(String modelRef) {
        return !hiddenTools.contains(modelRef);
    }

    /**
     * Set the sort order for this loadout using a list of sorted button models
     * @param models Button models to use as the order reference
     * This can be a list of {@link NavButtonModel} or {@link String}
     */
    public void setSortOrder(@Nullable List<?> models) {
        sortOrder.clear();
        if (models != null) {
            for (int i = 0; i < models.size(); i++) {
                Object mdl = models.get(i);
                String ref = mdl instanceof NavButtonModel
                        ? ((NavButtonModel) mdl).getReference()
                        : String.valueOf(mdl);
                sortOrder.put(ref, i);
            }
        }
    }

    /**
     * Get all button models sorted by this loadout's sort order
     * @return List of button models
     */
    public List<NavButtonModel> getSortedModels() {
        List<NavButtonModel> models = NavButtonManager.getInstance()
                .getButtonModels();
        sortModels(models);
        return models;
    }

    /**
     * Sort button models by this loadout's sort order
     * @param models Tool button models to sort
     */
    private void sortModels(List<NavButtonModel> models) {
        // No sort order defined
        if (sortOrder.isEmpty())
            return;

        // Track tools that don't have a defined sort order
        final List<NavButtonModel> noOrder = new ArrayList<>();
        for (int i = 0; i < models.size(); i++) {
            if (!sortOrder.containsKey(models.get(i).getReference()))
                noOrder.add(models.remove(i--));
        }

        // Sort tools
        Collections.sort(models, new Comparator<NavButtonModel>() {
            @Override
            public int compare(NavButtonModel o1, NavButtonModel o2) {
                Integer s1 = sortOrder.get(o1.getReference());
                Integer s2 = sortOrder.get(o2.getReference());
                if (s1 != null && s2 != null)
                    return s1.compareTo(s2);
                else if (s1 != null)
                    return -1;
                else
                    return 1;
            }
        });

        // Define sort order for newly added tools/plugins
        if (!noOrder.isEmpty()) {

            // Keep new tools before Settings and Quit
            Integer s = sortOrder.get("settings.xml");
            if (s == null)
                s = models.size();
            Integer q = sortOrder.get("quit.xml");
            if (q == null)
                q = models.size();
            int min = Math.min(s, q);

            try {
                models.addAll(min, noOrder);
            } catch (Exception e) {
                // Playstore Crash: NullPointerException during menu sorting
                // Experienced on a Nokia 3.1 A Android 9 (SDK 28) - unable
                // to find steps for reproduction.   Just add to the end of the
                // models.
                models.addAll(noOrder);
            }
            // Update sort order with new tools included
            setSortOrder(models);
        }
    }

    /**
     * Persist this loadout to shared preferences
     */
    public void persist() {
        LoadoutManager.getInstance().persistLoadout(this, true);
    }

    /**
     * Refresh loadout button models
     * Useful in cases where a plugin has been loaded or unloaded
     */
    public void refreshButtons() {
        for (Map.Entry<String, NavButtonModel> e : buttons.entrySet()) {
            String key = e.getKey();
            NavButtonModel mdl = e.getValue();
            setButton(key, mdl.getReference());
        }
    }

    /**
     * Copy data from another loadout
     * @param other Other loadout
     */
    public void copy(LoadoutItemModel other) {
        this.title = other.title;
        buttons.clear();
        buttons.putAll(other.buttons);
        buttonSet.clear();
        buttonSet.addAll(other.buttonSet);
        hiddenTools.clear();
        hiddenTools.addAll(other.hiddenTools);
        sortOrder.clear();
        sortOrder.putAll(other.sortOrder);
    }

    /**
     * Check whether or not this loadout is temporary (non-persistable)
     * @return True if temporary
     */
    public boolean isTemporary() {
        return this.temporary;
    }

    /**
     * Set whether this loadout is temporary
     * @param temp True if temporary
     */
    public void setTemporary(boolean temp) {
        this.temporary = temp;
    }

    /**
     * Serialize a loadout to the preferences string set format
     * @return Loadout data string set
     */
    public Set<String> toStringSet() {
        Set<String> set = new HashSet<>();
        set.add("version=" + VERSION);
        set.add("uid=" + getUID());
        set.add("title=" + getTitle());
        addStringList(set, "buttons", getButtonStringSet());
        addStringList(set, "hidden", getHiddenStringSet());
        addStringList(set, "sortOrder", getSortedStringSet());
        return set;
    }

    /**
     * Parse a loadout from the preference string set
     * @param set String set containing loadout data
     * @return Loadout or null if failed
     */
    public static LoadoutItemModel fromStringSet(Set<String> set) {
        if (set == null || set.size() < 3)
            return null;

        // Parse the key values in the set
        Map<String, String> map = new HashMap<>();
        for (String kv : set) {
            int eqIdx = kv.indexOf('=');
            if (eqIdx == -1)
                continue;
            String key = kv.substring(0, eqIdx);
            String value = kv.substring(eqIdx + 1);
            map.put(key, value);
        }

        int version = MathUtils.parseInt(map.get("version"), 0);
        String uid, title, buttons;
        String hidden = null;
        String sortOrder = null;

        if (version > 0) {
            // New format stores each attribute in key=value format
            uid = map.get("uid");
            title = map.get("title");
            buttons = map.get("buttons");
            hidden = map.get("hidden");
            sortOrder = map.get("sortOrder");
        } else {
            // Legacy string set parsing
            List<String> values = new ArrayList<>(set);
            Collections.sort(values);
            uid = values.get(0).substring(1);
            title = values.get(1).substring(1);
            buttons = values.get(2).substring(1);
            if (values.size() > 3)
                hidden = values.get(3).substring(1);
        }

        // UID and title are required
        if (FileSystemUtils.isEmpty(uid) || FileSystemUtils.isEmpty(title))
            return null;

        List<String> buttonList = getStringList(buttons);
        List<String> hiddenList = getStringList(hidden);
        List<String> sortList = getStringList(sortOrder);

        LoadoutItemModel loadout = new LoadoutItemModel(uid, title,
                buttonList);
        loadout.setToolsVisible(hiddenList, false);
        loadout.setSortOrder(sortList);

        // Make sure the zoom button is visible by default if version < 1
        if (version < 1)
            loadout.showZoomButton(true);

        return loadout;
    }

    /**
     * Add a keyed string list to a string set
     * @param set String set
     * @param key Key for the string set
     * @param strings List of strings (if empty nothing is added)
     */
    private static void addStringList(Set<String> set, String key,
            @Nullable List<String> strings) {
        if (!FileSystemUtils.isEmpty(strings))
            set.add(key + "=" + TextUtils.join(",", strings));
    }

    /**
     * Parse list given a comma-separated list of strings
     * @param strings Comma-separated string list
     * @return List of strings
     */
    private static List<String> getStringList(@Nullable String strings) {
        return !FileSystemUtils.isEmpty(strings)
                ? Arrays.asList(strings.split(","))
                : new ArrayList<>();
    }

    /**
     * Get a set of strings representing each button key and a button reference
     * @return Button string set
     */
    private List<String> getButtonStringSet() {
        List<String> buttonStrings = new ArrayList<>();
        for (Map.Entry<String, NavButtonModel> entry : buttons.entrySet()) {
            buttonStrings.add(entry.getKey() + PERSIST_SEPERATOR
                    + entry.getValue().getReference());
        }
        return buttonStrings;
    }

    /**
     * Get a set of strings representing each hidden tool
     * @return Hidden tools string set
     */
    private List<String> getHiddenStringSet() {
        return new ArrayList<>(hiddenTools);
    }

    /**
     * Get a set of strings for the sorted tool order
     * @return Sort order string set
     */
    @Nullable
    private List<String> getSortedStringSet() {
        if (sortOrder.isEmpty())
            return null;
        List<NavButtonModel> models = getSortedModels();
        List<String> ret = new ArrayList<>(models.size());
        for (NavButtonModel mdl : models)
            ret.add(mdl.getReference());
        return ret;
    }
}
