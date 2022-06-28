
package com.atakmap.android.update;

import android.content.Context;
import android.graphics.drawable.Drawable;

import com.atak.plugins.impl.AtakPluginRegistry;
import com.atakmap.coremap.filesystem.FileSystemUtils;

/**
 * Represents a side loaded plugin on the local device
 *
 *
 */
public class SideloadedPluginInformation extends ProductInformation {

    private final Context _context;

    public SideloadedPluginInformation(ProductRepository parent,
            Context context,
            AtakPluginRegistry.PluginDescriptor plugin) {
        //Note we use targetSDK for OS requirement, rather than min SDK, buts its all thats available
        super(parent, Platform.Android, ProductType.plugin, plugin
                .getPackageName(),
                AppMgmtUtils.getAppNameOrPackage(context,
                        plugin.getPackageName()),
                AppMgmtUtils
                        .getAppVersionName(context, plugin.getPackageName()),
                AppMgmtUtils
                        .getAppVersionCode(context, plugin.getPackageName()),
                null, null,
                getString(AppMgmtUtils
                        .getAppDescription(context, plugin.getPackageName())),
                null, AppMgmtUtils.getTargetSdkVersion(
                        context, plugin.getPackageName()),
                plugin.getPluginApi(), AppMgmtUtils.getAppVersionCode(context,
                        plugin.getPackageName()));
        _context = context;
    }

    public SideloadedPluginInformation(ProductInformation plugin,
            Context context) {
        super(plugin.getParent(), Platform.Android, ProductType.plugin, plugin
                .getPackageName(),
                AppMgmtUtils.getAppNameOrPackage(context,
                        plugin.getPackageName()),
                AppMgmtUtils
                        .getAppVersionName(context, plugin.getPackageName()),
                AppMgmtUtils
                        .getAppVersionCode(context, plugin.getPackageName()),
                plugin.getAppUri(), plugin.getIconUri(),
                plugin.getDescription(),
                plugin.getHash(), plugin.getOsRequirement(), plugin
                        .getTakRequirement(),
                AppMgmtUtils
                        .getAppVersionCode(context, plugin.getPackageName()));
        _context = context;
    }

    @Override
    public boolean isValid() {
        return platform != null
                && productType != null
                && !FileSystemUtils.isEmpty(packageName)
                && !FileSystemUtils.isEmpty(simpleName)
                && revision >= 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;

        SideloadedPluginInformation that = (SideloadedPluginInformation) o;

        return _context != null ? _context.equals(that._context)
                : that._context == null;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (_context != null ? _context.hashCode() : 0);
        return result;
    }

    @Override
    public Drawable getIcon() {
        return AppMgmtUtils.getAppDrawable(_context, packageName);
    }
}
