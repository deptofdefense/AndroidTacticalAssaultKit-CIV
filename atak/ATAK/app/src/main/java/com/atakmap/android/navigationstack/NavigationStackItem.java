
package com.atakmap.android.navigationstack;

import java.util.ArrayList;
import java.util.List;

import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.maps.MapView;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.ImageButton;

/**
 * Container for an view within a {@link DropDownNavigationStack}
 */
public class NavigationStackItem extends BroadcastReceiver
        implements NavigationStackView, NavigationStackManager {

    protected final MapView _mapView;
    protected final Context _context;
    protected View _itemView;
    protected DropDownNavigationStack _navigationStack;

    public NavigationStackItem(MapView mapView) {
        _mapView = mapView;
        _context = mapView.getContext();
    }

    final public MapView getMapView() {
        return _mapView;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
    }

    @Override
    public String getTitle() {
        return "";
    }

    @Override
    public List<ImageButton> getButtons() {
        return new ArrayList<>();
    }

    @Override
    public View getView() {
        return _itemView;
    }

    @Override
    public boolean onBackButton() {
        return false;
    }

    @Override
    public boolean onCloseButton() {
        return false;
    }

    @Override
    public void onClose() {
    }

    final public void dispose() {
        disposeImpl();
        _itemView = null;
        _navigationStack = null;
    }

    protected void disposeImpl() {
    }

    @Override
    public DropDownNavigationStack getNavigationStack() {
        return _navigationStack;
    }

    @Override
    public void setNavigationStack(DropDownNavigationStack navigationStack) {
        this._navigationStack = navigationStack;
    }

    /**
     * Helper method for creating a button to use with {@link #getButtons()}
     * @param context Button context
     * @param drawableId Icon drawable ID
     * @param onClick On-click callback
     * @return New button
     */
    protected ImageButton createButton(Context context, int drawableId,
            View.OnClickListener onClick) {
        ImageButton btn = new ImageButton(context);
        btn.setImageResource(drawableId);
        btn.setOnClickListener(onClick);
        return btn;
    }

    /**
     * Helper method for creating a button to use with {@link #getButtons()}
     * The context used is the application context provided by {@link MapView}
     * @param drawableId Icon drawable ID
     * @param onClick On-click callback
     * @return New button
     */
    protected ImageButton createButton(int drawableId,
            View.OnClickListener onClick) {
        return createButton(getMapView().getContext(), drawableId, onClick);
    }

    /* Navigation stack method redirects */

    /**
     * Push a view onto the navigation stack
     * @param view Navigation stack view
     */
    protected void pushView(NavigationStackView view) {
        if (_navigationStack != null)
            _navigationStack.pushView(view);
    }

    /**
     * Pop a view off the navigation stack
     */
    protected void popView() {
        if (_navigationStack != null)
            _navigationStack.popView();
    }

    /**
     * Hide the current view
     */
    protected void hideView() {
        if (_navigationStack != null)
            _navigationStack.hideView();
    }

    /**
     * Close the entire navigation stack
     */
    protected void closeNavigationStack() {
        if (_navigationStack != null)
            _navigationStack.closeNavigationStack();
    }

    /**
     * Un-hide the current view
     */
    protected void unhideView() {
        if (_navigationStack != null)
            _navigationStack.unhideView();
    }

    /**
     * Update the buttons in the header of the drop-down
     */
    protected void updateButtons() {
        if (_navigationStack != null)
            _navigationStack.updateButtons();
    }

    /**
     * Redirect for {@link DropDownReceiver#setRetain(boolean)}
     * @param retain True to retain
     */
    public void setRetain(boolean retain) {
        if (_navigationStack != null)
            _navigationStack.setRetain(retain);
    }

    /**
     * Redirect for {@link DropDownReceiver#setTransient(boolean)}
     * @param isTransient True if transient
     */
    public void setTransient(boolean isTransient) {
        if (_navigationStack != null)
            _navigationStack.setTransient(isTransient);
    }

    /**
     * Redirect for {@link DropDownReceiver#isVisible()}
     * @return True if visible
     */
    public boolean isVisible() {
        if (_navigationStack != null)
            return _navigationStack.isVisible();

        return false;
    }

    /**
     * Redirect for {@link DropDownReceiver#setAssociationKey(String)}
     * @param associationKey Associated preference key
     */
    public void setAssociationKey(String associationKey) {
        if (_navigationStack != null)
            _navigationStack.setAssociationKey(associationKey);
    }
}
