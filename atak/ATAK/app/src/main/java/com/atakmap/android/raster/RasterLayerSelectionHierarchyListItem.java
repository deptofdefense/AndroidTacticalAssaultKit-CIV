
package com.atakmap.android.raster;

import java.util.HashSet;
import java.util.Set;

import android.view.View;

import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.action.Action;
import com.atakmap.android.hierarchy.action.Visibility;
import com.atakmap.map.layer.raster.RasterLayer2;

public class RasterLayerSelectionHierarchyListItem implements
        HierarchyListItem, Visibility {

    protected final RasterLayer2 subject;
    protected final String uid;
    protected final String selection;
    protected final Set<Class<? extends Action>> supportedActions;

    public RasterLayerSelectionHierarchyListItem(RasterLayer2 subject,
            String subjectUid, String selection) {
        this.subject = subject;
        this.uid = subjectUid + "." + selection;
        this.selection = selection;
        this.supportedActions = new HashSet<>();
        this.supportedActions.add(Visibility.class);
    }

    @Override
    public String getUID() {
        return this.uid;
    }

    @Override
    public String getTitle() {
        return this.selection;
    }

    @Override
    public int getPreferredListIndex() {
        return -1;
    }

    @Override
    public int getChildCount() {
        return 0;
    }

    @Override
    public int getDescendantCount() {
        return 0;
    }

    @Override
    public HierarchyListItem getChildAt(int index) {
        return null;
    }

    @Override
    public boolean isChildSupported() {
        return false;
    }

    @Override
    public String getIconUri() {
        return null;
    }

    @Override
    public int getIconColor() {
        return -1;
    }

    @Override
    public Object setLocalData(String s, Object o) {
        return null;
    }

    @Override
    public Object getLocalData(String s) {
        return null;
    }

    @Override
    public <T> T getLocalData(String s, Class<T> clazz) {
        return null;
    }

    @Override
    public <T extends Action> T getAction(Class<T> clazz) {
        if (this.supportedActions.contains(clazz))
            return clazz.cast(this);
        else
            return null;
    }

    @Override
    public Object getUserObject() {
        return null;
    }

    @Override
    public View getExtraView() {
        return null;
    }

    @Override
    public Sort refresh(Sort sortHint) {
        return null;
    }

    /**************************************************************************/
    // Visibility

    @Override
    public boolean setVisible(boolean visible) {
        // toggle visibility of the selection
        this.subject.setVisible(this.selection, visible);
        return true;
    }

    @Override
    public boolean isVisible() {
        return this.subject.isVisible(this.selection);
    }

}
