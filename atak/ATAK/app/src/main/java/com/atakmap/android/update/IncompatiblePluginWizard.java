
package com.atakmap.android.update;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.atakmap.app.R;
import com.atakmap.app.preferences.ToolsPreferenceFragment;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Wizard for updating incompatible plugins
 * 
 * TODO combine with ProductInformationWizard..? Needs a different dialog layout...
 * 
 *
 */
public class IncompatiblePluginWizard {

    private static final String TAG = "IncompatiblePluginWizard";

    private static class IncompatPluginWrapper {
        String pkg;
        String label;
        Drawable icon;
        boolean bUpdateAvailable;

        @Override
        public String toString() {
            return pkg + ", " + bUpdateAvailable + ", " + label;
        }
    }

    private static class IncompatPluginWrapperComparator implements
            Comparator<IncompatPluginWrapper> {

        @Override
        public int compare(IncompatPluginWrapper lhs,
                IncompatPluginWrapper rhs) {
            if (lhs.bUpdateAvailable && !rhs.bUpdateAvailable)
                return -1;
            else if (!lhs.bUpdateAvailable && rhs.bUpdateAvailable)
                return 1;

            return lhs.label.compareTo(rhs.label);
        }
    }

    private final Context _context;
    private final ProductProviderManager _manager;
    private final boolean _bDisplayManualBtn;

    IncompatiblePluginWizard(Context context,
            ProductProviderManager manager, boolean bDisplayManual) {
        _context = context;
        _manager = manager;
        _bDisplayManualBtn = bDisplayManual;
    }

    public void prompt(final Collection<String> incompatiblePlugins) {
        Log.d(TAG, "prompt: " + incompatiblePlugins.size());

        //cache info for each incompat plugin
        final List<IncompatPluginWrapper> plugins = new ArrayList<>();
        for (String pkg : incompatiblePlugins) {
            IncompatPluginWrapper plugin = new IncompatPluginWrapper();
            plugin.pkg = pkg;
            plugin.label = AppMgmtUtils.getAppNameOrPackage(_context, pkg);
            plugin.icon = AppMgmtUtils.getAppDrawable(_context, pkg);
            plugin.bUpdateAvailable = false;

            for (ProductProviderManager.Provider p : _manager.getProviders()) {
                if (p.get() != null && p.get().isStale(_context, pkg)) {
                    plugin.bUpdateAvailable = true;
                    break;
                }
            }

            plugins.add(plugin);
            Log.d(TAG, "Adding incompat plugin: " + plugin);
        }

        //sort by update availability, and then label alphabetically
        Collections.sort(plugins, new IncompatPluginWrapperComparator());
        promptIncompatiblePlugins(plugins
                .toArray(new IncompatPluginWrapper[0]));
    }

    /**
     * Notify user that some plugins are incompatible with this version of ATAK
     *
     * @param data the plugin wrapper list
     */
    private void promptIncompatiblePlugins(final IncompatPluginWrapper[] data) {
        LayoutInflater inflater = LayoutInflater.from(_context);
        View incompatView = inflater.inflate(R.layout.app_mgmt_incompat_layout,
                null);

        final ListView pluginListView = incompatView
                .findViewById(R.id.app_mgmt_incompat_listview);
        IncompatiblePluginWizard.IncompatPluginListAdapter pluginListAdapter = new IncompatiblePluginWizard.IncompatPluginListAdapter(
                _context, data);
        View header = inflater.inflate(R.layout.app_mgmt_incompat_header, null);
        pluginListView.addHeaderView(header);
        pluginListView.setAdapter(pluginListAdapter);

        Context c = AppMgmtActivity.getActivityContext();
        if (c == null)
            c = _context;

        final AlertDialog.Builder creator = new AlertDialog.Builder(c)
                .setIcon(com.atakmap.android.util.ATAKConstants.getIconId())
                .setTitle(data.length
                        + " "
                        + _context
                                .getString(
                                        R.string.app_mgmt_incompat_plugins)
                        + (data.length == 1 ? "" : "s"))
                .setView(incompatView)
                .setCancelable(false)
                .setPositiveButton(R.string.resolve,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                dialog.dismiss();
                                Log.d(TAG, "Update incompat plugins");
                                updateIncompat(data, 0);
                            }
                        })
                .setNegativeButton(R.string.ignore, null);
        if (_bDisplayManualBtn) {
            creator.setNeutralButton(R.string.manually,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            Log.d(TAG, "Manually resolve incompat plugins");

                            Intent mgmtPlugins = new Intent(_context,
                                    AppMgmtActivity.class);
                            if (_context instanceof Activity) {
                                ((Activity) _context)
                                        .startActivityForResult(
                                                mgmtPlugins,
                                                ToolsPreferenceFragment.APP_MGMT_REQUEST_CODE);
                            } else {
                                Log.e(TAG, "could not launch the activity"
                                        + mgmtPlugins);
                            }
                        }
                    });
        }

        final AlertDialog dialog = creator.create();

        ImageButton helpButton = incompatView
                .findViewById(R.id.app_mgmt_incompt_moreinfo);
        helpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "More info on incompat plugins");
                dialog.dismiss();

                Context c = AppMgmtActivity.getActivityContext();
                if (c == null)
                    c = _context;

                new AlertDialog.Builder(c)
                        .setIcon(com.atakmap.android.util.ATAKConstants
                                .getIconId())
                        .setTitle(R.string.app_mgmt_incompat_plugins)
                        .setMessage(R.string.app_mgmt_incompat_message)
                        .setCancelable(false)
                        .setPositiveButton(R.string.ok,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog,
                                            int which) {
                                        promptIncompatiblePlugins(data);
                                    }
                                })
                        .show();
            }
        });

        // set dialog dims appropriately based on device size
        WindowManager.LayoutParams screenLP = new WindowManager.LayoutParams();
        Window w = dialog.getWindow();
        if (w != null) {
            WindowManager.LayoutParams currParams = w.getAttributes();
            if (currParams != null) {
                screenLP.copyFrom(currParams);
                screenLP.width = WindowManager.LayoutParams.MATCH_PARENT;
                screenLP.height = WindowManager.LayoutParams.MATCH_PARENT;
                dialog.getWindow().setAttributes(screenLP);
            }
        }

        try {
            dialog.show();
        } catch (Exception e) {
            Log.e(TAG, "dialog can no longer be shown", new Exception());
        }
    }

    /**
     * Dialog based wizard to update/uninstall incompatible plugins
     * @param plugins the list of incompatible plugins
     * @param index the index of the one to update
     */
    private void updateIncompat(final IncompatPluginWrapper[] plugins,
            final int index) {
        Log.d(TAG, "updateIncompat size: " + plugins.length + ", index: "
                + index);
        final IncompatPluginWrapper plugin = plugins[index];

        String message = (plugin.bUpdateAvailable
                ? "Would you like to update or uninstall "
                : "Would you like to uninstall ")
                + plugin.label
                + _context.getString(R.string.question_mark_symbol);
        if (index > 0) {
            message = "Wait for previous step to complete. " + message;
        }

        Context c = AppMgmtActivity.getActivityContext();
        if (c == null)
            c = _context;

        AlertDialog.Builder dialog = new AlertDialog.Builder(c)
                .setTitle(
                        plugin.label + " - " + (index + 1) + " of "
                                + plugins.length)
                .setMessage(message)
                //.setCancelable(false)
                .setPositiveButton(
                        (plugin.bUpdateAvailable ? "Update" : "Uninstall"),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                if (index < plugins.length - 1) {
                                    //show next dialog
                                    dialog.dismiss();
                                    updateIncompat(plugins, index + 1);

                                    //update the plugin
                                    updateIncompat(plugin,
                                            plugin.bUpdateAvailable);
                                } else {
                                    //update the plugin
                                    updateIncompat(plugin,
                                            plugin.bUpdateAvailable);

                                    //check for updates
                                    dialog.dismiss();
                                    _manager.checkForAvailableUpdates();
                                }
                            }
                        })
                .setNegativeButton(R.string.skip, // implemented
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                if (index < plugins.length - 1) {
                                    //show next dialog
                                    dialog.dismiss();
                                    updateIncompat(plugins, index + 1);
                                } else {
                                    //check for updates
                                    dialog.dismiss();
                                    _manager.checkForAvailableUpdates();
                                }
                            }
                        });
        if (plugin.bUpdateAvailable) {
            dialog.setNeutralButton("Uninstall",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (index < plugins.length - 1) {
                                //show next dialog
                                dialog.dismiss();
                                updateIncompat(plugins, index + 1);

                                //uninstall the plugin
                                updateIncompat(plugin, false);
                            } else {
                                //uninstall the plugin
                                updateIncompat(plugin, false);

                                //check for updates
                                dialog.dismiss();
                                _manager.checkForAvailableUpdates();
                            }
                        }
                    });
        }

        if (plugin.icon != null) {
            dialog.setIcon(AppMgmtUtils.getDialogIcon(_context, plugin.icon));
        } else {
            dialog.setIcon(com.atakmap.android.util.ATAKConstants.getIconId());
        }

        dialog.show();
    }

    private void updateIncompat(final IncompatPluginWrapper plugin,
            final boolean bUpdate) {

        //TODO async thread?
        final ProductProviderManager.Provider provider = _manager
                .getProvider(plugin.pkg);
        if (provider == null) {
            Log.w(TAG,
                    "Cannot update incompat without provider: "
                            + plugin);
            //TODO TOAST
            return;
        }

        ProductInformation product = provider.get().getProduct(plugin.pkg);
        if (product == null) {
            Log.w(TAG,
                    "Cannot update incompat without product: "
                            + plugin);
            //TODO TOAST
            return;
        }

        if (bUpdate) {
            //TODO do the next Plugin/extensions get loaded, and updated code in place with this approach?
            //or do we need a restart after a plugin is unloaded and reloaded?

            Log.d(TAG, "Updating incompat: " + product + " via "
                    + provider);
            provider.install(product);
        } else {
            Log.d(TAG, "Uninstalling incompat: " + product + " via "
                    + provider);
            provider.uninstall(product);
        }
    }

    /**
     * Adapter of list of incompatible plugins
     */
    private static class IncompatPluginListAdapter extends
            ArrayAdapter<IncompatPluginWrapper> {

        final public static String TAG = "IncompatPluginListAdapter";

        private final IncompatPluginWrapper[] incompatPlugins;
        private final LayoutInflater _inflater;

        IncompatPluginListAdapter(Context context,
                IncompatPluginWrapper[] incompatPlugins) {
            super(context, R.layout.app_mgmt_incompat_row, incompatPlugins);

            this.incompatPlugins = incompatPlugins;
            this._inflater = LayoutInflater.from(context);

            Log.d(TAG, "created with " + this.incompatPlugins.length
                    + " plugins listed");
        }

        @NonNull
        @Override
        public View getView(int position, View convertView,
                @NonNull ViewGroup parent) {
            //TODO display any more details e.g. version #s, why not compat
            //TODO cache labels/icons for faster loading of views?
            View row = convertView;
            IncompatPluginHolder holder;
            if (row == null) {
                row = _inflater.inflate(R.layout.app_mgmt_incompat_row, parent,
                        false);
                holder = new IncompatPluginHolder();
                holder.updateAvailableIcon = row
                        .findViewById(
                                R.id.app_mgmt_row_incompat_updateAvailable);
                holder.appIcon = row
                        .findViewById(R.id.app_mgmt_row_incompat_icon);
                holder.appLabel = row
                        .findViewById(R.id.app_mgmt_row_incompat_title);
                row.setTag(holder);
            } else {
                holder = (IncompatPluginHolder) row.getTag();
            }

            final IncompatPluginWrapper plugin = incompatPlugins[position];
            if (plugin == null) {
                Log.w(TAG, "IncompatPluginWrapper is empty");
                return _inflater.inflate(R.layout.empty, parent, false);
            }
            if (!FileSystemUtils.isEmpty(plugin.label)) {
                holder.appLabel.setText(plugin.label);
            }

            if (plugin.icon != null) {
                holder.appIcon.setVisibility(View.VISIBLE);
                holder.appIcon.setImageDrawable(plugin.icon);
            } else {
                holder.appIcon.setVisibility(View.INVISIBLE);
            }

            if (plugin.bUpdateAvailable) {
                holder.updateAvailableIcon
                        .setImageResource(R.drawable.importmgr_status_green);
            } else {
                holder.updateAvailableIcon
                        .setImageResource(R.drawable.importmgr_status_red);
            }

            return row;
        }

        static class IncompatPluginHolder {
            ImageView updateAvailableIcon;
            ImageView appIcon;
            TextView appLabel;
        }

    }
}
