
package com.atakmap.android.hierarchy;

import android.view.View;

import com.atakmap.android.hierarchy.action.Action;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Hierarchial list model inspired by javax.swing.tree.TreeNode.
 * 
 * 
 */
public interface HierarchyListItem {

    /**
     * Returns the unique ID of the list item
     *
     * @return the unique ID of the list item
     */
    String getUID();

    /**
     * Returns the title to be displayed for the item.
     * 
     * @return The title to be displayed for the item.
     */
    String getTitle();

    /**
     * Returns the preferred index in the list for the item to be displayed at.
     * 
     * @return The preferred list index for display or <code>-1</code> to indicate no preference
     *         (FIFO).
     */
    int getPreferredListIndex();

    /**
     * Returns the number of children that this node has.
     * 
     * @return The number of children that the node has.
     */
    int getChildCount();

    /**
     * Returns the total number of descendant nodes that this node has.
     * 
     * @return The total number of descendant nodes that this node has.
     */
    int getDescendantCount();

    /**
     * Returns the child node at the specified index. Behavior is undefined if the index is invalid.
     * 
     * @param index The index
     * @return The child node at the specified index.
     */
    HierarchyListItem getChildAt(int index);

    /**
     * Check if list item can contain children 
     * 
     * @return
     */
    boolean isChildSupported();

    /**
     * Returns the URI for the icon for the node.
     * 
     * @return The URI for the icon for the node, <code>null</code> if the node
     * has no icon, or "gone" to remove the icon space entirely.
     */
    String getIconUri();

    /**
     * Returns the color to be applied to the icon.
     * 
     * @return The color to be applied to the icon, return <code>0xFFFFFFFF</code> (<code>-1</code>)
     *         to use the original color.
     */
    int getIconColor();

    /**
     * Sets the specified local data on the object. Local data is user specific data that the
     * external object displaying the list can use for its own purposes (e.g. associate some runtime
     * computed property with a node).
     * 
     * @param s The key for the data
     * @param o The value for the data
     * @return The previously assigned value for the specified key.
     */
    Object setLocalData(String s, Object o);

    /**
     * Returns the local data associated with the node for the specified key. Local data is user
     * specific data that the external object displaying the list can use for its own purposes (e.g.
     * associate some runtime computed property with a node).
     * 
     * @param s The key for the local data
     * @return The local data associated with the specified key
     */
    Object getLocalData(String s);

    /**
     * Returns the local data associated with the node for the specified key. Local data is user
     * specific data that the external object displaying the list can use for its own purposes (e.g.
     * associate some runtime computed property with a node).
     * 
     * @param s The key for the local data
     * @param clazz The return type for the data
     * @return The local data associated with the specified key, cast to an object of template class
     *         <code>T</code>.
     */
    <T> T getLocalData(String s, Class<T> clazz);

    /**
     * Returns an instance of the specified action that can be used to interact with the node's
     * underlying content.
     * 
     * @param clazz The action class
     * @return An object that can effect the specified action or <code>null</code> if the specified
     *         action is not supported for the node.
     */
    <T extends Action> T getAction(Class<T> clazz);

    /**
     * Returns the user object associated with the node. This is the underlying object that the node
     * represents; the actual returned value is implementation dependent.
     * 
     * @return The user object associated with the node. May be <code>null</code>.
     */
    Object getUserObject();

    /**
     * Returns the extra view, if any, associated with the node.  The extra view occupies the space
     * to the right of the line item and can be defined as the developer sees fit.  Clicks should
     * be passed through so you can include simple stuff like text, or more interactive things like
     * buttons or check boxes.  Should occupy space nicely with range and bearing values if any are
     * present.  If no extra view is associated with the node, the space will be empty.
     *
     * @return The extra view associated with this node, or <code>null</code> if there is no view.
     */
    View getExtraView();

    /**
     * Refreshes this node's descendants on the specified sort. If the specified sort cannot be
     * supported, the current sort remains unchanged.
     * 
     * @param sortHint The new sort
     * @return The sort that is actually being used.
     */
    Sort refresh(Sort sortHint);

    /**************************************************************************/

    abstract class Sort {
        public String getTitle() {
            return null;
        }

        public String getIconUri() {
            return null;
        }
    }

    final class SortAlphabet extends Sort {

        @Override
        public String getTitle() {
            return "Alphabetical";
        }

        @Override
        public String getIconUri() {
            return ATAKUtilities.getResourceUri(R.drawable.alpha_sort);
        }
    }

    final class SortAlphabetDesc extends Sort {

        @Override
        public String getTitle() {
            return "Descending";
        }

        @Override
        public String getIconUri() {
            return ATAKUtilities.getResourceUri(R.drawable.alpha_sort_desc);
        }
    }

    final class SortDistanceFrom extends Sort {
        public final GeoPoint location;

        public SortDistanceFrom(GeoPoint location) {
            this.location = location;
        }

        @Override
        public String getTitle() {
            return "Distance";
        }

        @Override
        public String getIconUri() {
            return ATAKUtilities.getResourceUri(R.drawable.prox_sort);
        }
    }

    class ComparatorSort extends Sort {
        private final Comparator<HierarchyListItem> comp;
        private final String name, iconUri;

        public ComparatorSort(Comparator<HierarchyListItem> comp,
                String name, String iconUri) {
            this.comp = comp;
            this.name = name;
            this.iconUri = iconUri;
        }

        public ComparatorSort(Comparator<HierarchyListItem> comp,
                String name, int iconResId) {
            this(comp, name, ATAKUtilities.getResourceUri(iconResId));
        }

        public ComparatorSort(Comparator<HierarchyListItem> comp) {
            this(comp, null, null);
        }

        public void sort(List<HierarchyListItem> items) {
            if (this.comp != null)
                Collections.sort(items, this.comp);
        }

        public Comparator<HierarchyListItem> getComparator() {
            return this.comp;
        }

        @Override
        public String getTitle() {
            return this.name;
        }

        @Override
        public String getIconUri() {
            return this.iconUri;
        }
    }
}
