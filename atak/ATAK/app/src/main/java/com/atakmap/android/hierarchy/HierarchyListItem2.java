
package com.atakmap.android.hierarchy;

import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;

import com.atakmap.annotations.DeprecatedApi;

import java.util.List;

/**
 * Hierarchical list model inspired by javax.swing.tree.TreeNode.
 * Now with a lot more control over display and layout
 * 
 * 
 */
public interface HierarchyListItem2 extends HierarchyListItem {

    /**
     * @deprecated Use {@link #refresh(HierarchyListFilter)}
     * @param sort
     * @return
     */
    @Override
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = false)
    Sort refresh(Sort sort);

    /**
     * Refreshes this node's descendants on the specified filter and sort.
     * If the specified sort cannot be supported, the current sort remains unchanged.
     * 
     * @param filter The new sort
     * @return The filter that is actually being used.
     */
    HierarchyListFilter refresh(HierarchyListFilter filter);

    /**
     * Unregister any listeners,
     */
    void dispose();

    /**
     * Count towards parent children if this item's children is empty
     * @return True to hide, false to always show
     */
    boolean hideIfEmpty();

    /**
     * Does this list support multi-selection of its items?
     * @return True if multi-select is allowed within this list
     */
    boolean isMultiSelectSupported();

    /**
     * Get the item description (shown in gray under the title)
     * @return Description or null to use default
     */
    String getDescription();

    /**
     * Get the icon drawable
     * @return Icon drawable or null to use icon URI instead
     */
    Drawable getIconDrawable();

    /**
     * Return all applicable sort modes for this list
     * @return List of sort modes or null if cannot be sorted
     */
    List<Sort> getSorts();

    /**
     * Get settings screen association key (see DropDownReceiver)
     * @return Association key
     */
    String getAssociationKey();

    /**
     * Get a custom view for the entire OM drop-down
     * This is smoother than switching between drop-downs using intents
     * @return Inflated custom view
     */
    View getCustomLayout();

    /**
     * Get the custom header view for this list
     * @return Inflated header view
     */
    View getHeaderView();

    /**
     * Get the custom footer view for this list
     * @return Inflated footer view
     */
    View getFooterView();

    /**
     * Used when this item is being displayed within a list
     * For overriding the entire list view, see {@link #getCustomLayout}
     * @param convertView The existing list item view. Only inflate a new layout
     *                    if the id/context do not match your expected config.
     * @param parent The list view parent - Use this as "root" when inflating
     * @return List item view
     */
    View getListItemView(View convertView, ViewGroup parent);

    /**
     * Same as {@link #getExtraView()} except the existing view is provided
     * This is to allow more efficient view cycling when scrolling through OM
     * @param convertView The existing extra view. Only inflate a new layout
     *                    if the id/context do not match your expected config.
     * @param parent The extra view parent - Use this as "root" when inflating
     * @return Extra view
     */
    View getExtraView(View convertView, ViewGroup parent);

    /**
     * Get the preferred drop-down size for displaying this list
     * Width is used in landscape mode, height is used in portrait mode
     * @return [width, height] ratios (1 being full screen)
     *         [-1, -1] to use default size
     */
    double[] getDropDownSize();

}
