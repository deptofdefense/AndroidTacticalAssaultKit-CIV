
package com.atakmap.android.features;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;

import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.HierarchyListReceiver;
import com.atakmap.android.hierarchy.HierarchyListUserSelect;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.overlay.MapOverlayParent;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.map.layer.feature.FeatureDataStore2;
import com.atakmap.map.layer.feature.FeatureDataStore2.FeatureQueryParameters;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import androidx.annotation.NonNull;

/**
 * Overlay Manager select interface for bulk editing of features
 */
public class FeatureEditListUserSelect extends HierarchyListUserSelect {

    private final MapView _mapView;
    private final Context _context;

    public FeatureEditListUserSelect(@NonNull MapView mapView) {
        super("", 0L);
        _mapView = mapView;
        _context = mapView.getContext();
    }

    @Override
    public String getTitle() {
        return _context.getString(R.string.edit_features);
    }

    @Override
    public Drawable getIcon() {
        return _context.getDrawable(R.drawable.ic_edit_features);
    }

    @Override
    public String getButtonText() {
        return _context.getString(R.string.edit);
    }

    @Override
    public ButtonMode getButtonMode() {
        return ButtonMode.VISIBLE_WHEN_SELECTED;
    }

    @Override
    public boolean accept(HierarchyListItem item) {

        // Accept File Overlays
        final Object o = item.getUserObject();
        if (o instanceof MapOverlayParent) {
            MapOverlayParent parent = (MapOverlayParent) o;
            if (FileSystemUtils.isEquals(parent.getName(), "File Overlays"))
                return true;
        }

        // Accept any item that supports feature editing
        return item.getAction(FeatureEdit.class) != null;
    }

    @Override
    public boolean acceptEntry(HierarchyListItem item) {
        return accept(item);
    }

    @Override
    public boolean isExternalUsageSupported() {
        // Show this as a multi-select option when we're in File Overlays
        return true;
    }

    @Override
    public boolean acceptRootList() {
        // Do not show this option when we're on the root list
        return false;
    }

    @Override
    public boolean processUserSelections(Context context,
            Set<HierarchyListItem> items) {

        // Cached requests for merge checking
        final Map<String, List<FeatureEditRequest>> map = new HashMap<>();

        // Build requests from feature items
        List<FeatureEditRequest> requests = new ArrayList<>(items.size());
        for (HierarchyListItem item : items) {

            // Make sure feature editing is supported
            FeatureEdit action = item.getAction(FeatureEdit.class);
            if (action == null)
                continue;

            // Pull database and associated query parameters from item
            FeatureDataStore2 db = action.getFeatureDatabase();
            FeatureQueryParameters params = action.getFeatureQueryParams();
            FeatureEditRequest req = new FeatureEditRequest(db, params);

            // Check if we can merge this request with the existing requests
            // so we can avoid unnecessary extra database queries
            String uri = db.getUri();
            List<FeatureEditRequest> cached = map.get(uri);
            if (cached == null)
                map.put(uri, cached = new ArrayList<>());
            if (!merge(cached, req)) {
                // Request wasn't merged - add it as a new request
                cached.add(req);
                requests.add(req);
            }
        }

        // Turn off select handler
        AtakBroadcast.getInstance().sendBroadcast(new Intent(
                HierarchyListReceiver.CLEAR_HIERARCHY));

        // Nothing to process
        if (requests.isEmpty())
            return false;

        // Open drop-down editor
        new FeatureEditDropdownReceiver(_mapView).show(getTitle(), requests);
        return true;
    }

    /**
     * See if we can merge two sets of edit requests together in order to
     * save some database performance
     * @param existing Existing requests
     * @param other Other query parameters that are copied from
     * @return True if the other request was merged into an existing request
     */
    private boolean merge(List<FeatureEditRequest> existing,
            FeatureEditRequest other) {
        for (FeatureEditRequest req : existing) {

            // If two request params are the same besides their set of feature
            // IDs then merge them together
            if (req.params.ids != null && other.params.ids != null
                    && req.equals(other, true, false)) {
                req.params.ids = mergeSet(req.params.ids, other.params.ids);
                return true;
            }

            // If two request params are the same besides their set of feature
            // set of IDs then merge
            if (req.getFeatureSetIds() != null && req.getFeatureSetIds() != null
                    && req.equals(other, false, true)) {
                req.params.featureSetFilter.ids = mergeSet(
                        req.params.featureSetFilter.ids,
                        other.params.featureSetFilter.ids);
                return true;
            }
        }
        return false;
    }

    /**
     * Merge two sets of longs while addressing unmodifiable sets
     * @param set1 Set to add to
     * @param set2 Set to copy from
     * @return Set that was added to (new instance if input is unmodifiable)
     */
    private Set<Long> mergeSet(Set<Long> set1, Set<Long> set2) {
        try {
            set1.addAll(set2);
        } catch (Exception ignore) {
            set1 = new HashSet<>(set1);
            set1.addAll(set2);
        }
        return set1;
    }
}
