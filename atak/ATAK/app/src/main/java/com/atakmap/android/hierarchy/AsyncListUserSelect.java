
package com.atakmap.android.hierarchy;

import android.content.Context;

import java.util.Set;

/**
 * Extension of HierarchyListUserSelect which supports async tasks
 */
public abstract class AsyncListUserSelect extends HierarchyListUserSelect {

    public AsyncListUserSelect(String tag, long actions) {
        super(tag, actions);
    }

    @Override
    public boolean processUserSelections(Context context,
            Set<HierarchyListItem> selected) {
        // Not supported
        return false;
    }

    /**
     * Process user selections with a runnable callback
     * @param context Map view context
     * @param selected Selected items
     * @param onFinished Callback that should be run after the async task
     *                   (or method, if cancelled) is finished
     */
    public abstract void processUserSelections(Context context,
            Set<HierarchyListItem> selected, Runnable onFinished);
}
