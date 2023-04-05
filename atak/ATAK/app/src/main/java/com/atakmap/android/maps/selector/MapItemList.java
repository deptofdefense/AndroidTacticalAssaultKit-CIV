
package com.atakmap.android.maps.selector;

import android.content.Context;
import android.widget.Toast;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.overlay.MapOverlay;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

/**
 * A displayable list of map items for selection and filtering purposes
 *
 * Basic usage is to call {@link #setItems(Collection)} with a non-empty list
 * and then {@link #show()} to display the list in a drop-down.
 */
public final class MapItemList {

    private static final String TAG = "MapItemList";

    private final MapView _mapView;
    private final Context _context;
    private final Context _plugin;

    /**
     * List of map items displayed
     */
    final List<MapItem> items = new ArrayList<>();

    /**
     * Title displayed at the top of the list
     */
    @Nullable
    String title;

    /**
     * Text displayed in the confirm button
     */
    @Nullable
    String buttonText;

    /**
     * Callback for when the user selects one or more items
     */
    @Nullable
    SelectCallback onSelect;

    /**
     * Callback for when the user cancels out of the list
     */
    @Nullable
    OnCancel onCancel;

    /**
     * Create a new map item list
     * @param mapView Map view instance
     * @param plugin Plugin context used for resource lookups
     */
    public MapItemList(@NonNull MapView mapView, @NonNull Context plugin) {
        _mapView = mapView;
        _context = mapView.getContext();
        _plugin = plugin;
    }

    /**
     * Create a new map item list
     * @param mapView Map view instance
     */
    public MapItemList(@NonNull MapView mapView) {
        this(mapView, mapView.getContext());
    }

    /**
     * Set the map items displayed in this list
     * @param items List of items to display
     * @return List instance
     */
    public MapItemList setItems(@NonNull Collection<? extends MapItem> items) {
        this.items.clear();
        this.items.addAll(items);
        return this;
    }

    /**
     * Set callback invoked when an item has been selected
     * This will automatically configure the list for single-select mode
     * @param onSelect Callback
     * @return List instance
     */
    public MapItemList setOnItemSelected(@NonNull OnItemSelected onSelect) {
        this.onSelect = onSelect;
        return this;
    }

    /**
     * Set callback invoked when a selection of multiple items has been confirmed
     * This will automatically configure the list for multi-select mode
     * @param onSelect Callback
     * @return List instance
     */
    public MapItemList setOnItemsSelected(@NonNull OnItemsSelected onSelect) {
        this.onSelect = onSelect;
        return this;
    }

    /**
     * Set callback invoked when a user cancels out of the list
     * @param onCancel Callback
     * @return List instance
     */
    public MapItemList setOnCancel(@NonNull OnCancel onCancel) {
        this.onCancel = onCancel;
        return this;
    }

    /**
     * Set the title to display at the top of the list
     * @param title Title
     * @return List instance
     */
    public MapItemList setTitle(@NonNull String title) {
        this.title = title;
        return this;
    }

    /**
     * Set the title to display at the top of the list
     * @param titleId String resource ID (plugin context)
     * @return List instance
     */
    public MapItemList setTitle(@StringRes int titleId) {
        return setTitle(_plugin.getString(titleId));
    }

    /**
     * Set the text displayed in the confirm button in the top-right
     * @param text Button text
     * @return List instance
     */
    public MapItemList setButtonText(@NonNull String text) {
        this.buttonText = text;
        return this;
    }

    /**
     * Set the text displayed in the confirm button in the top-right
     * @param textId String resource ID (plugin context)
     * @return List instance
     */
    public MapItemList setButtonText(@StringRes int textId) {
        return setButtonText(_plugin.getString(textId));
    }

    /**
     * Show this map item list in a drop-down
     * @return True if list successfully shown
     */
    public boolean show() {

        // List cannot be empty
        if (this.items.isEmpty()) {
            showFailToast();
            Log.e(TAG, "Failed to show list - List is empty");
            return false;
        }

        // Callback must be set
        if (this.onSelect == null) {
            showFailToast();
            Log.e(TAG, "Failed to show list - Callback must be set");
            return false;
        }

        // Find the overlay used to display the list
        MapOverlay mo = _mapView.getMapOverlayManager().getOverlay(
                MapItemListOverlay.ID);
        if (!(mo instanceof MapItemListOverlay)) {
            showFailToast();
            Log.e(TAG, "Failed to show list - map overlay \""
                    + MapItemListOverlay.ID + "\" not found!");
            return false;
        }

        MapItemListOverlay overlay = (MapItemListOverlay) mo;

        // Send this list to the overlay
        overlay.show(this);
        return true;
    }

    /**
     * Show a toast message indicating the list could not be displayed
     */
    private void showFailToast() {
        Toast.makeText(_context, R.string.failed_to_show_map_item_list,
                Toast.LENGTH_LONG).show();
    }

    /**
     * Generic callback interface used by {@link OnItemSelected}
     * and {@link OnItemsSelected}
     */
    interface SelectCallback {
    }

    /**
     * Callback for when a single map item can be selected
     */
    public interface OnItemSelected extends SelectCallback {

        /**
         * A map item has been selected
         * @param item Map item
         * @return True to confirm this selection
         */
        boolean onItemSelected(MapItem item);
    }

    /**
     * Callback for when multiple map items can be selected
     */
    public interface OnItemsSelected extends SelectCallback {

        /**
         * One or more map items have been selected
         * @param items Map items
         * @return True to confirm this selection
         */
        boolean onItemsSelected(List<MapItem> items);
    }

    /**
     * Callback for when cancel has been selected
     */
    public interface OnCancel {

        /**
         * The selection has been cancelled
         */
        void onCancel();
    }
}
