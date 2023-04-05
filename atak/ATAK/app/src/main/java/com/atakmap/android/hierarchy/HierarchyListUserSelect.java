
package com.atakmap.android.hierarchy;

import android.content.Context;
import android.graphics.drawable.Drawable;

import com.atakmap.android.features.FeatureSetHierarchyListItem;
import com.atakmap.android.importexport.ExportFilter;
import com.atakmap.android.importexport.ExportFilters;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.overlay.MapOverlay;
import com.atakmap.android.toolbar.IToolbarExtension;
import com.atakmap.android.user.FilterMapOverlay;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Interface to add functionality to the Hierarchy List Tool e.g. delete all
 * items selected by user. 
 * 
 * Note, to be instantiated via Reflection by the <code>
 * HierarchyListReceiver</code>, implementations must implement a constructor
 * with signature: ctor(Context, String)
 * 
 * 
 */
public abstract class HierarchyListUserSelect extends HierarchyListFilter {

    private static final String TAG = "HierarchyListUserSelect";

    public static final Comparator<HierarchyListUserSelect> COMP_TITLE = new Comparator<HierarchyListUserSelect>() {
        @Override
        public int compare(HierarchyListUserSelect o1,
                HierarchyListUserSelect o2) {
            String t1 = o1 != null ? o1.getTitle() : null;
            String t2 = o2 != null ? o2.getTitle() : null;
            if (t1 != null && t2 == null)
                return -1;
            else if (t2 != null && t1 == null)
                return 1;
            return t1 == null ? 0 : t1.compareTo(t2);
        }
    };

    /**
     * Tag e.g. to associate user selected items with some other content
     */
    protected String _tag;
    protected final long actions;
    protected boolean _multiSelect = true;

    /**
     * List of specific Map Items to display, others will be filtered out by base impl
     */
    protected List<String> mapItemUIDs;

    /**
     * Default filter keys off of this.mapItemsUIDs
     */
    private ExportFilters _filter;

    public HierarchyListUserSelect(String tag, long actions) {
        super(new HierarchyListItem.SortAlphabet());
        _tag = tag;
        this.actions = actions;
    }

    public String getTag() {
        return _tag;
    }

    public void setTag(String tag) {
        this._tag = tag;
    }

    /**
     * Returns the action(s) that may be performed by the selection handler. The value returned by
     * this method will be used when building the hierarchy lists via
     * {@link com.atakmap.android.overlay.MapOverlay#getListModel}.
     * 
     * @return A bitwise-OR of one of more of the <code>ACTION_xxx</code> flags specified in
     *         {@link com.atakmap.android.hierarchy.action.Actions}.
     */
    public long getActions() {
        return this.actions;
    }

    /**
     * ALWAYS_VISIBLE Button always visible VISIBLE_WHEN_SELECTED Button visible when at least one
     * item is selected
     * 
     * 
     */
    public enum ButtonMode {
        ALWAYS_VISIBLE,
        VISIBLE_WHEN_SELECTED
    }

    /**
     * Provide title for HierarchyListReceiver Drop Down
     * 
     * @return Overlay Manager title
     */
    public abstract String getTitle();

    /**
     * Provide text for button once item(s) are selected
     * 
     * @return Button text
     */
    public abstract String getButtonText();

    /**
     * Get Button Mode for the user select handler
     * 
     * @return {@link ButtonMode#ALWAYS_VISIBLE} or {@link ButtonMode#VISIBLE_WHEN_SELECTED}
     */
    public abstract ButtonMode getButtonMode();

    /**
     * An item has been selected
     *
     * @param om Overlay manager
     * @param item Selected item
     * @return True if handled, false to continue
     */
    public boolean onItemSelected(HierarchyListAdapter om,
            HierarchyListItem item) {
        return false;
    }

    /**
     * An item has been deselected
     *
     * @param om Overlay manager
     * @param item Selected item
     * @return True if handled, false to continue
     */
    public boolean onItemDeselected(HierarchyListAdapter om,
            HierarchyListItem item) {
        return false;
    }

    /**
     * Process the user selections. Invoke when button is pressed by the user
     *
     * @param context Application context
     * @param selected Set of selected list items
     * @return True if handled
     */
    public abstract boolean processUserSelections(Context context,
            Set<HierarchyListItem> selected);

    /**
     * The user has cancelled multi-select
     *
     * @param context Application context
     */
    public void cancel(Context context) {
        // Default behavior is to do nothing
    }

    /**
     * Whether or not this accepts multiple selections
     * @return True to accept multiple selections
     */
    public boolean isMultiSelect() {
        return _multiSelect;
    }

    public void setMultiSelect(boolean multiSelect) {
        _multiSelect = multiSelect;
    }

    /**
     * Toolbar view that is displayed while using this select handler
     * @return Toolbar extension object
     */
    public IToolbarExtension getToolbar() {
        return null;
    }

    /**
     * Whether or not this selector can be used externally by other tools
     * If this selector is only used for a specific workflow then leave this as false
     * Otherwise if you want the selector to show up under Overlay Manager > Multi Select, return true
     *
     * @return True if this selector can be used externally
     */
    public boolean isExternalUsageSupported() {
        return false;
    }

    /**
     * Whether to show this select handler as a multi-select option when the
     * user is on the root list of Overlay Manager. The root list is not
     * processed by the usual filtering methods to preserve legacy behavior,
     * so this method handles the filtering.
     *
     * Note: This is only used when {@link #isExternalUsageSupported()} is
     * set to <code>true</code>.
     *
     * @return True to show this select handler as a multi-select option
     * on the root list
     */
    public boolean acceptRootList() {
        return true;
    }

    /**
     * Get the icon representing this selector - used externally
     * @return Icon drawable
     */
    public Drawable getIcon() {
        return null;
    }

    public void setMapItemUIDs(List<String> mapItemUIDs) {
        this.mapItemUIDs = mapItemUIDs;
    }

    /**
     * Get the filter to be used in getChildCount and getDescendantCount
     * Default impl does not filter
     * @return the export filters being used
     */
    protected ExportFilters getFilter() {
        if (_filter == null) {
            _filter = new ExportFilters();

            if (FileSystemUtils.isEmpty(this.mapItemUIDs)) {
                Log.d(TAG, "Not filtering");
                _filter.add(new ExportFilter() {
                    @Override
                    public boolean filter(MapItem item) {
                        return false;
                    }
                });
            } else {
                //filter to require UID to be in list
                Log.d(TAG, "Filtering based on UID list");
                _filter.add(new ExportFilters.UIDFilter(this.mapItemUIDs));
            }
        }

        return _filter;
    }

    /**
     * Return true if item should be displayed, otherwise item will be hidden
     * 
     * @param item the HierarchyListItem to be considered
     * @return true if it should be displayed
     */
    @Override
    public boolean accept(HierarchyListItem item) {
        //Log.d(TAG, "filterListItem " + item.getClass().getName());
        final Object userObject = item.getUserObject();
        if (userObject instanceof MapItem)
            return !this.filterItem((MapItem) userObject);
        else if (userObject instanceof MapGroup)
            return !this.filterGroup((MapGroup) userObject);
        else if (userObject instanceof MapOverlay)
            return !this.filterOverlay((MapOverlay) userObject);
        else
            return !this.filterListItemImpl(item);
    }

    @Override
    public boolean acceptEntry(HierarchyListItem item) {
        return !(item instanceof FeatureSetHierarchyListItem)
                && (!(item instanceof HierarchyListItem2)
                        || ((HierarchyListItem2) item)
                                .isMultiSelectSupported());
    }

    /*
     * The below filter methods accept items if you return FALSE, not TRUE
     * XXX - Whoever wrote these methods thought that wouldn't be confusing...
     */

    /**
     * Default impl filters out misc list items (not MapItem, MapGroup, MapOverlay)
     *
     * @param item the HierarchyListItem to be considered
     * @return true if the item is to be filtered out
     */
    protected boolean filterListItemImpl(HierarchyListItem item) {
        //Log.d(TAG, "filterListItemImpl " + item.getClass().getName()); 
        return true;
    }

    /**
     * Default impl filters out if item is not in mapItemUIDs
     * If mapItemsUIDs is empty, does not filter
     *
     * @param item the MapItem to be used
     * @return true if the item is to be filtered out
     */
    protected boolean filterItem(MapItem item) {
        if (item == null)
            return true;

        if (FileSystemUtils.isEmpty(mapItemUIDs))
            return false;

        return !mapItemUIDs.contains(item.getUID());
    }

    /**
     * Default impl filters out if group has no items in mapItemUIDs
     * If mapItemsUIDs is empty, does not filter
     *
     * @param group
     * @return
     */
    protected boolean filterGroup(MapGroup group) {
        if (group == null)
            return true;

        if (FileSystemUtils.isEmpty(mapItemUIDs))
            return false;

        boolean atLeastOne = group
                .deepForEachItem(new MapGroup.MapItemsCallback() {
                    @Override
                    public boolean onItemFunction(MapItem item) {
                        if (item == null)
                            return false;

                        return mapItemUIDs.contains(item.getUID());
                    }
                });

        //also require at least one unfiltered descendent map item
        //bFilter &= (getDescendantCount(group, false) < 1);

        Log.d(TAG, "Group: "
                + group.getFriendlyName()
                + (atLeastOne ? " contains at least one specified UID"
                        : " contains none of the specified UIDs"));
        return !atLeastOne;
    }

    /**
     * Default impl filters out if it is a FilterMapOverlay, and it has no unfiltered children
     *
     * @param overlay
     * @return
     */
    protected boolean filterOverlay(MapOverlay overlay) {
        if (!(overlay instanceof FilterMapOverlay))
            return false;

        int filteredCount = ((FilterMapOverlay) overlay).getDescendantCount(
                getFilter(), false);
        //Log.d(TAG, "overlay " + overlay.getName() + " has filtered count: " + filteredCount);
        return filteredCount < 1;
    }

    /**
     * Default impl applies filters to MapGroup and FilterMapOverlay
     * @param item
     * @return
     */
    public int getChildCount(HierarchyListItem item) {
        //Log.d(TAG, "getChildCount: " + item.getClass().getName());

        final Object userObject = item.getUserObject();
        if (userObject == null) {
            //Log.w(TAG, "getChildCount userObject null: " + item.getChildCount());
            return item.getChildCount();
        }

        int childCount = 0;
        if (userObject instanceof MapItem) {
            childCount = item.getChildCount();
        } else if (userObject instanceof MapGroup) {
            MapGroup mg = (MapGroup) userObject;
            childCount = getChildCount(mg);
        } else if (userObject instanceof FilterMapOverlay) {
            childCount = ((FilterMapOverlay) userObject)
                    .getChildCount(getFilter());
        } else {
            //Log.w(TAG, "Unsupported user object: " + userObject.getClass().getName());
            childCount = item.getChildCount();
        }

        //Log.d(TAG, "getChildCount userObject " + userObject.getClass().getName() + ": "
        //        + item.getTitle() + " child: " + childCount);
        return childCount;
    }

    /**
     * Default impl applies filters to MapGroup and FilterMapOverlay
     *
     * @param item
     * @return
     */
    public int getDescendantCount(HierarchyListItem item) {
        //Log.d(TAG, "getDescendantCount: " + item.getClass().getName());

        final Object userObject = item.getUserObject();
        if (userObject instanceof MapItem)
            return item.getDescendantCount();
        else if (userObject instanceof MapGroup) {
            MapGroup mg = (MapGroup) userObject;
            return getDescendantCount(mg, false);
        } else if (userObject instanceof FilterMapOverlay)
            return ((FilterMapOverlay) userObject).getDescendantCount(
                    getFilter(), false);
        else
            return item.getDescendantCount();
    }

    /**
     * Get count of children which at least one unfiltered descendant
     *
     * @param group
     * @return
     */
    private int getChildCount(MapGroup group) {
        int count = 0;
        for (MapItem item : group.getItems()) {
            if (item != null) {
                if (!getFilter().filter(item))
                    count++;
            }
        }

        for (MapGroup child : group.getChildGroups()) {
            if (getDescendantCount(child, false) > 0)
                count++;
        }

        return count;
    }

    /**
     * Get descendant count. Optionally include child FilterMapOverlays in the count
     *
     * @param group
     * @param bCountAllChildOverlays true to count child overlays and children
     *      false to count only child overlays which have descendant map items
     * @return
     */
    private int getDescendantCount(MapGroup group,
            boolean bCountAllChildOverlays) {
        if (group == null)
            return 0;

        int count = 0;
        for (MapItem item : group.getItems()) {
            if (item != null) {
                if (!getFilter().filter(item))
                    count++;
            }
        }

        int childrenWithLeaf = 0, tempCount = 0;
        Collection<MapGroup> childGroups = group.getChildGroups();
        for (MapGroup childGroup : childGroups) {
            tempCount = getDescendantCount(childGroup, bCountAllChildOverlays);
            if (tempCount > 0)
                childrenWithLeaf++;
            count += tempCount;
        }

        if (bCountAllChildOverlays)
            count += childGroups.size();
        else
            count += childrenWithLeaf;

        return count;
    }
}
