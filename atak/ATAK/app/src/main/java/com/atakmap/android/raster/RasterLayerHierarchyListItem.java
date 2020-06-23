
package com.atakmap.android.raster;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import android.view.View;

import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.action.Action;
import com.atakmap.android.hierarchy.action.Visibility;
import com.atakmap.map.layer.raster.RasterLayer2;

public class RasterLayerHierarchyListItem implements HierarchyListItem,
        Visibility {

    private final static WeakHashMap<RasterLayer2, String> layerUids = new WeakHashMap<>();

    protected final RasterLayer2 subject;
    protected final String iconUri;
    protected final Set<Class<? extends Action>> supportedActions;
    protected final String uid;

    public RasterLayerHierarchyListItem(RasterLayer2 subject) {
        this(subject, null);
    }

    public RasterLayerHierarchyListItem(RasterLayer2 subject, String iconUri) {
        this.subject = subject;
        this.iconUri = iconUri;

        this.supportedActions = new HashSet<>();
        this.supportedActions.add(Visibility.class);

        synchronized (layerUids) {
            String scratch = layerUids.get(this.subject);
            if (scratch == null)
                layerUids.put(this.subject, scratch = UUID.randomUUID()
                        .toString());
            this.uid = scratch;
        }
    }

    @Override
    public String getUID() {
        return this.uid;
    }

    @Override
    public String getTitle() {
        return this.subject.getName();
    }

    @Override
    public int getPreferredListIndex() {
        return -1;
    }

    @Override
    public int getChildCount() {
        return this.subject.getSelectionOptions().size();
    }

    @Override
    public int getDescendantCount() {
        return this.getChildCount();
    }

    @Override
    public HierarchyListItem getChildAt(int index) {
        // XXX - there is currently no callback mechanism for RasterLayer to
        //       report when the selections have changed

        List<String> opts = new ArrayList<>(
                this.subject.getSelectionOptions());
        if (index < 0 || index >= opts.size())
            return null;

        Collections.sort(opts);

        return new RasterLayerSelectionHierarchyListItem(this.subject,
                this.uid, opts.get(index));
    }

    @Override
    public boolean isChildSupported() {
        return true;
    }

    @Override
    public String getIconUri() {
        return this.iconUri;
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
        return this.subject;
    }

    @Override
    public View getExtraView() {
        return null;
    }

    @Override
    public Sort refresh(Sort sortHint) {
        // always sorts alphabetic
        return null;
    }

    /**************************************************************************/
    // Visibility 

    @Override
    public boolean setVisible(boolean visible) {
        // toggle the visibility of the layer's selections
        final Collection<String> opts = this.subject.getSelectionOptions();
        for (String opt : opts)
            this.subject.setVisible(opt, visible);
        return true;
    }

    @Override
    public boolean isVisible() {
        final Collection<String> opts = this.subject.getSelectionOptions();
        boolean retval = false;
        for (String opt : opts) {
            retval |= this.subject.isVisible(opt);
        }

        return retval;
    }

}
