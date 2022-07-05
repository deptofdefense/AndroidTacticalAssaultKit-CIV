
package com.atakmap.android.navigation;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

import com.atakmap.android.navigation.models.NavButtonIntentAction;
import com.atakmap.android.navigation.models.NavButtonModel;
import com.atakmap.android.tools.menu.ActionBroadcastData;
import com.atakmap.android.tools.menu.ActionBroadcastExtraStringData;
import com.atakmap.android.tools.menu.ActionClickData;
import com.atakmap.android.tools.menu.ActionMenuData;
import com.atakmap.app.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import transapps.maps.plugin.tool.Tool;

/**
 * Manages the list of available actions (All Tools drop-down)
 */
public class NavButtonManager {

    private static NavButtonManager _instance;

    public static NavButtonManager getInstance() {
        return _instance;
    }

    /**
     * Listener for when the model list has been modified
     */
    public interface OnModelListChangedListener {

        /**
         * Model list has been changed
         */
        void onModelListChanged();
    }

    /**
     * Listener for when a specific button model has been modified
     */
    public interface OnModelChangedListener {

        /**
         * Called when a model has been modified
         */
        void onModelChanged(NavButtonModel model);
    }

    private final Context context;
    private final List<NavButtonModel> modelList = new ArrayList<>();
    private final Map<String, NavButtonModel> modelByRef = new HashMap<>();
    private final ConcurrentLinkedQueue<OnModelListChangedListener> listListeners = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnModelChangedListener> modelListeners = new ConcurrentLinkedQueue<>();

    public NavButtonManager(Context c) {
        context = c;
        loadDefaultActions();

        if (_instance == null)
            _instance = this;
    }

    /**
     * Get a button model given its name
     * @param reference model reference used to uniquely identify the model
     * @return Button model or null if not found
     */
    public NavButtonModel getModelByReference(String reference) {
        synchronized (modelList) {
            NavButtonModel mdl = modelByRef.get(reference);
            if (mdl == null) {
                // Try appending .xml
                if (!reference.endsWith(".xml"))
                    mdl = modelByRef.get(reference + ".xml");
            }
            return mdl;
        }
    }

    /**
     * Get a button model given its index
     * @param index Button model index
     * @return Button model or null if not found
     */
    public NavButtonModel getModelByIndex(int index) {
        synchronized (modelList) {
            return index >= 0 && index < modelList.size()
                    ? modelList.get(index)
                    : null;
        }
    }

    /**
     * Find a button model for a plugin given its {@link Tool} instance
     * @param plugin Plugin tool instance
     * @return Button model or null if not found
     */
    public NavButtonModel getModelByPlugin(Tool plugin) {
        Class<? extends Tool> cl = plugin.getClass();
        Package pkg = cl.getPackage();
        if (pkg == null)
            return null;
        String ref = "plugin://" + pkg.getName() + "/" + cl.getName();
        return getModelByReference(ref);
    }

    /**
     * Get the index of a model in the list
     * @param model Button model
     * @return Index of -1 if not found
     */
    public int indexOfModel(NavButtonModel model) {
        synchronized (modelList) {
            return modelList.indexOf(model);
        }
    }

    /**
     * Retrieve a copy of the model list
     * @return List of button models
     */
    public List<NavButtonModel> getButtonModels() {
        synchronized (modelList) {
            return new ArrayList<>(modelList);
        }
    }

    /**
     * Convert action menu data to a button model
     * @param data Action menu data
     * @return Button model
     */
    public NavButtonModel modelFromActionData(ActionMenuData data) {
        NavButtonModel.Builder b = new NavButtonModel.Builder();
        b.setReference(data.getRef());
        b.setName(data.getTitle(context));

        // Icons - need to toggle selected state on to get the proper selected
        // drawable
        b.setImage(data.getIcon(context));
        data.setSelected(true);
        b.setSelectedImage(data.getIcon(context));
        data.setSelected(false);

        // Parse press/long-press actions
        for (ActionClickData acd : data.getActionClickData())
            parseClickData(acd, b);

        return b.build();
    }

    /**
     * Add a button model to the root list
     * @param model Button model to add
     */
    public void addButtonModel(NavButtonModel model) {
        // Add new toolbar entries before Settings and Quit
        synchronized (modelList) {
            int size = modelList.size();
            modelList.add(size >= 2 ? size - 2 : 0, model);
            modelByRef.put(model.getReference(), model);
            Collections.sort(modelList, DEFAULT_SORT_ORDER);
        }
        onModelListChanged();
    }

    /**
     * Add a button model given action menu data
     * @param data Action menu data
     */
    public void addButtonModel(ActionMenuData data) {
        addButtonModel(modelFromActionData(data));
    }

    /**
     * Remove a button model from the root list
     * @param model Button model
     * @return True if removed
     */
    public boolean removeButtonModel(NavButtonModel model) {
        boolean removed = false;
        synchronized (modelList) {
            modelByRef.remove(model.getReference());
            for (NavButtonModel button : modelList) {
                if (button == model
                        || button.getReference().equals(model.getReference())) {
                    modelList.remove(button);
                    removed = true;
                    break;
                }
            }
        }
        if (removed)
            onModelListChanged();
        return removed;
    }

    /**
     * Remove a button model given action menu data
     * @param data Action menu data
     * @return True if removed
     */
    public boolean removeButtonModel(ActionMenuData data) {
        return removeButtonModel(modelFromActionData(data));
    }

    /**
     * Add listener for events where a button model has been modified
     * @param l Listener
     */
    public void addModelChangedListener(OnModelChangedListener l) {
        modelListeners.add(l);
    }

    /**
     * Remove model changed listener
     * @param l Listener
     */
    public void removeModelChangedListener(OnModelChangedListener l) {
        modelListeners.remove(l);
    }

    /**
     * Add listener for events where the button model list is modified
     * @param l Listener
     */
    public void addModelListChangedListener(OnModelListChangedListener l) {
        listListeners.add(l);
    }

    /**
     * Remove model list changed listener
     * @param l Listener
     */
    public void removeModelListChangedListener(OnModelListChangedListener l) {
        listListeners.remove(l);
    }

    /**
     * Notify listeners that a model has been modified.    Please ensure this is run on the UI
     * thread.
     * @param model Model that was modified
     */
    public void notifyModelChanged(NavButtonModel model) {
        if (model == null)
            return;
        List<OnModelChangedListener> listeners = new ArrayList<>(
                modelListeners);
        for (OnModelChangedListener l : listeners)
            l.onModelChanged(model);
    }

    /**
     * Notify listeners that a model has been modified
     * @param reference Model reference that was modified
     */
    public void notifyModelChanged(String reference) {
        notifyModelChanged(getModelByReference(reference));
    }

    /***
     * Create list of TakButtonModels using the generated ActionMenuData
     */
    private void loadDefaultActions() {

        // Load the actions from the assets directory
        List<ActionMenuData> actions = ActionMenuData.getAllActions(context);

        // Convert to button models
        List<NavButtonModel> models = new ArrayList<>(actions.size());
        for (ActionMenuData data : actions)
            models.add(modelFromActionData(data));

        // Add special zoom button model
        models.add(new NavButtonModel.Builder()
                .setReference("zoom")
                .setImage(context.getDrawable(R.drawable.ic_zoom_in_out))
                .setName(context.getString(R.string.zoom))
                .setSupportedPositions(NavButtonModel.LEFT)
                .build());

        // Register with the manager
        synchronized (modelList) {
            modelList.clear();
            modelByRef.clear();
            for (NavButtonModel mdl : models) {
                modelList.add(mdl);
                modelByRef.put(mdl.getReference(), mdl);
            }
            Collections.sort(modelList, DEFAULT_SORT_ORDER);
        }

        // Notify listeners that the list of available models has been changed
        onModelListChanged();
    }

    /**
     * Called whenever the model list is modified
     */
    private void onModelListChanged() {
        List<OnModelListChangedListener> listeners = new ArrayList<>(
                listListeners);
        for (OnModelListChangedListener l : listeners)
            l.onModelListChanged();
    }

    /**
     * Parse click data from {@link ActionMenuData} into a
     * {@link NavButtonModel.Builder}
     *
     * @param acd Click data
     * @param b Button model builder
     */
    private void parseClickData(ActionClickData acd, NavButtonModel.Builder b) {
        ActionBroadcastData broadcast = acd.getBroadcast();
        Intent intent = new Intent(broadcast.getAction());

        // Parse extras
        if (broadcast.hasExtras()) {
            List<ActionBroadcastExtraStringData> actionExtras = broadcast
                    .getExtras();
            for (ActionBroadcastExtraStringData extraData : actionExtras)
                intent.putExtra(extraData.getKey(), extraData.getValue());
        }

        NavButtonIntentAction action = new NavButtonIntentAction(intent);
        action.setDismissMenu(acd.shouldDismissMenu());

        // Set attributes on the builder
        String actionType = acd.getActionType();
        if (actionType.equals("click"))
            b.setAction(action);
        else if (actionType.equals("longClick"))
            b.setActionLong(action);
    }

    // Keep "Settings" and "Quit" at the bottom by default
    private static final Map<String, Integer> defSortOrder = new HashMap<>();
    static {
        defSortOrder.put("settings.xml", 1);
        defSortOrder.put("quit.xml", 2);
    }

    /**
     * Get the sort order for a given button model
     * @param model Button model
     * @return Sort order
     */
    private static int getSortOrder(NavButtonModel model) {
        String ref = model.getReference();

        // Settings & Quit at the bottom
        Integer order = defSortOrder.get(ref);
        if (order != null)
            return order;

        // Plugins between default tools and Settings/Quit
        if (ref != null && ref.startsWith("plugin://"))
            return 0;

        // Default tools on top
        return -1;
    }

    /**
     * Get the non-null name for a model
     * @param model Button model
     * @return Name or empty string if null
     */
    @NonNull
    private static String getName(NavButtonModel model) {
        String name = model.getName();
        return name != null ? name : "";
    }

    /**
     * The default sort order for tools and plugins:
     * [Default Tools A-Z][Plugins A-Z][Settings][Quit]
     */
    private static final Comparator<NavButtonModel> DEFAULT_SORT_ORDER = new Comparator<NavButtonModel>() {
        @Override
        public int compare(NavButtonModel b1, NavButtonModel b2) {
            int order1 = getSortOrder(b1);
            int order2 = getSortOrder(b2);
            int comp = Integer.compare(order1, order2);
            if (comp == 0)
                comp = getName(b1).compareTo(getName(b2));
            return comp;
        }
    };
}
