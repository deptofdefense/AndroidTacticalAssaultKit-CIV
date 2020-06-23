
package com.atakmap.android.update.sorters;

import android.content.Context;

import com.atak.plugins.impl.AtakPluginRegistry;
import com.atakmap.android.update.AppMgmtUtils;

import java.util.Comparator;

/**
 * Comparator to support sorting based on appName
 */
class PluginDescriptorSorter implements
        Comparator<AtakPluginRegistry.PluginDescriptor> {

    private final Context _context;

    public PluginDescriptorSorter(Context context) {
        _context = context;
    }

    @Override
    public int compare(AtakPluginRegistry.PluginDescriptor lhs,
            AtakPluginRegistry.PluginDescriptor rhs) {
        final String lname = AppMgmtUtils.getAppNameOrPackage(_context,
                lhs.getPackageName());
        final String rname = AppMgmtUtils.getAppNameOrPackage(_context,
                rhs.getPackageName());
        return lname.compareToIgnoreCase(rname);
    }
}
