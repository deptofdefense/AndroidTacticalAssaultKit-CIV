
package com.atakmap.android.devtools;

final class ForceCloseHierarchyListItem extends DevToolToggle {
    public ForceCloseHierarchyListItem() {
        super("Force Close", "ForceClose");
    }

    @Override
    protected void setEnabled(boolean visible) {
        if (visible)
            System.exit(1);
    }

    @Override
    protected boolean isEnabled() {
        return false;
    }
}
