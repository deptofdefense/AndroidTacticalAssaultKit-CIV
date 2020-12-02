
package com.atak.plugins.impl;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import com.atakmap.android.maps.MapComponent;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.metrics.MetricsApi;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

// handy utilities - should likely move the uninstall notification into the update
// utilities in future versions.
import com.atakmap.android.update.AppMgmtUtils;
import com.atakmap.android.update.AppMgmtActivity;

/**
 * Provides the appropriate wrapper for a Lifecycle from the transapp 
 * plugin model.   Each plugin implements one or more of the following
 * lifecycles and tools.   This component is the mediation between the
 * concept of a lifecycle and the TAK system.
 */
class LifecycleMapComponent implements MapComponent {

    public static final String TAG = "LifecycleMapComponent";

    private final transapps.maps.plugin.lifecycle.Lifecycle lifecycle;
    private final String packageName;

    private boolean errorReported = false;

    private boolean created = false;

    private void reportError(final Throwable re, final MapView view) {
        this.reportError(re, view, true);
    }

    private void reportError(final Throwable re, final MapView view,
            final boolean toast) {
        if (lifecycle == null || FileSystemUtils.isEmpty(this.packageName)) {
            Log.e(TAG, "encountered a null lifecycle");
            return;
        }

        //first remove from ATAK
        AtakPluginRegistry.get().unloadPlugin(this.packageName);

        //now prompt user to fully uninstall
        String label = AppMgmtUtils.getAppNameOrPackage(view.getContext(),
                this.packageName);
        if (FileSystemUtils.isEmpty(label)) {
            label = "Plugin";
        }

        final Drawable icon = AppMgmtUtils.getAppDrawable(view.getContext(),
                this.packageName);
        final String fTitle = label;
        view.post(new Runnable() {
            @Override
            public void run() {
                final String s = "error with plugin "
                        + LifecycleMapComponent.this.packageName
                        + " (please uninstall): "
                        + lifecycle.getClass().getName();
                Log.e(TAG, s, re);

                if (MetricsApi.shouldRecordMetric()) {
                    Bundle b = new Bundle();
                    b.putString("package",
                            LifecycleMapComponent.this.packageName);
                    b.putString("loaderror", "true");
                    if (re != null)
                        b.putString("detail", re.getMessage());
                    MetricsApi.record("plugin", b);
                }

                if (toast && !errorReported) {
                    errorReported = true;
                    Context c = AppMgmtActivity.getActivityContext();
                    if (c == null)
                        c = view.getContext();

                    AlertDialog.Builder builder = new AlertDialog.Builder(c)
                            .setTitle(String.format(
                                    c.getString(R.string.load_failure_title),
                                    fTitle))
                            .setMessage(String.format(
                                    c.getString(R.string.load_failure_summary),
                                    fTitle, lifecycle.getClass().getName()))
                            .setPositiveButton(R.string.uninstall,
                                    new DialogInterface.OnClickListener() {

                                        @Override
                                        public void onClick(
                                                DialogInterface dialog,
                                                int which) {
                                            dialog.dismiss();
                                            AppMgmtUtils.uninstall(
                                                    view.getContext(),
                                                    packageName);
                                        }
                                    })
                            .setNegativeButton(R.string.cancel, null);

                    if (icon == null)
                        builder.setIcon(R.drawable.ic_menu_error);
                    else
                        builder.setIcon(AppMgmtUtils.getDialogIcon(
                                view.getContext(), icon));

                    builder.show();
                }
            }
        });
    }

    LifecycleMapComponent(
            final transapps.maps.plugin.lifecycle.Lifecycle lifecycle,
            final String packageName) {
        this.lifecycle = lifecycle;
        this.packageName = packageName;
    }

    @Override
    public void onCreate(final Context context, final Intent intent,
            final MapView view) {
        // all of the other Components have onCreate called on the UI thread.
        view.post(new Runnable() {
            @Override
            public void run() {
                try {
                    lifecycle.onCreate((Activity) context,
                            new AtakMapView(view));

                    // feature requested based on plugin loading and not going through onStart, onResume
                    if (((com.atakmap.app.ATAKActivity) view.getContext())
                            .isActive()) {
                        lifecycle.onStart();
                        lifecycle.onResume();
                    }
                    created = true;
                } catch (Throwable le) {
                    reportError(le, view);
                    onDestroy(context, view);
                }
            }
        });
    }

    @Override
    public void onDestroy(final Context context, final MapView view) {
        try {
            lifecycle.onDestroy();
        } catch (Throwable le) {
            reportError(le, view, false);
        }
    }

    @Override
    public void onStart(final Context context, final MapView view) {
        // ignore any errant calls to onStart before it is created.
        // the creation thread will take care of them.  just return.
        if (!created)
            return;

        try {
            lifecycle.onStart();
        } catch (Throwable le) {
            reportError(le, view);
            onDestroy(context, view);
        }
    }

    @Override
    public void onStop(final Context context, final MapView view) {
        try {
            lifecycle.onStop();
        } catch (Throwable le) {
            reportError(le, view);
            onDestroy(context, view);
        }
    }

    @Override
    public void onPause(final Context context, final MapView view) {
        try {
            lifecycle.onPause();
        } catch (Throwable le) {
            reportError(le, view);
            onDestroy(context, view);
        }
    }

    @Override
    public void onResume(final Context context, final MapView view) {
        if (!created)
            return;

        try {
            lifecycle.onResume();
        } catch (Throwable le) {
            reportError(le, view);
            onDestroy(context, view);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        try {
            lifecycle.onConfigurationChanged(c);
        } catch (Throwable tr) {
            Log.e(lifecycle.getClass().getSimpleName(),
                    "onConfigurationChanged threw exception", tr);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Context context, Menu menu) {
        return false;
    }

    @Override
    public boolean onPrepareOptionsMenu(Context context, Menu menu) {
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(Context context, MenuItem item) {
        return false;
    }

    @Override
    public void onOptionsMenuClosed(Context context, Menu menu) {
    }

}
