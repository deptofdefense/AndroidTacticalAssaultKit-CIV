
package com.atakmap.android.update;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.atak.plugins.impl.AtakPluginRegistry;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.util.ATAKConstants;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.app.R;
import com.atakmap.app.preferences.ToolsPreferenceFragment;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

/**
 *
 */
public class ProductProviderManager extends BroadcastReceiver {

    private static final String TAG = "ProductProviderManager";
    public static final String PRODUCT_REPOS_REFRESHED = "com.atakmap.android.update.PRODUCT_REPOS_REFRESHED";
    public static final String VIEW_APP_MGMT = "com.atakmap.android.update.VIEW_APP_MGMT";

    private final Activity _context;
    private final SharedPreferences _prefs;

    /**
     * Ongoing sync task
     */
    private RepoSyncTask _ongoingSyncTask;

    public interface Provider {

        void dispose();

        /**
         * Get cached repo contents
         * @return
         */
        ProductRepository get();

        /**
         * Rebuild cached repo index
         * Currently we only do this on background thread
         *
         * @return
         */
        ProductRepository rebuild(
                final ProductProviderManager.ProgressDialogListener listener);

        boolean contains(String pkg);

        /**
         * Notification that an app has been installed
         * @param pkg
         * @return true if repo state was changed
         */
        boolean installed(final String pkg);

        /**
         * Notification that an app has been uninstalled
         * @param pkg
         * @return true if repo state was changed
         */
        boolean uninstalled(final String pkg);

        /**
         * User has requested to install the product
         *
         * This is done on UI thread by default, so if operation may be long running, implementations
         * should off load to another thread
         *
         * @param product the product information used to determine how to install.
         * @return
         */
        void install(final ProductInformation product);

        /**
         * User has requested to uninstall the product
         *
         * This is done on UI thread by default, so if operation may be long running, implementations
         * should off load to another thread
         *
         * @param product
         * @return
         */
        void uninstall(final ProductInformation product);

        /**
         * Check if the repo is hosted remotely (not on this device)
         * @return
         */
        boolean isRemote();
    }

    private static final List<Provider> _providers = new ArrayList<>();

    public ProductProviderManager(Activity activity) {
        _context = activity;
        _prefs = PreferenceManager.getDefaultSharedPreferences(activity);

        AtakBroadcast.getInstance().registerReceiver(_appMgmtReceiver,
                new DocumentedIntentFilter(VIEW_APP_MGMT));
    }

    public void dispose() {
        AtakBroadcast.getInstance().unregisterReceiver(_appMgmtReceiver);
        synchronized (_providers) {
            for (Provider p : _providers) {
                p.dispose();
            }
            _providers.clear();
        }
    }

    private static void addProvider(Provider p) {
        synchronized (_providers) {
            if (!_providers.contains(p)) {
                Log.d(TAG, "Adding: " + p.getClass().getSimpleName());
                _providers.add(p);
            }
        }
    }

    private synchronized static void removeProvider(Provider p) {
        synchronized (_providers) {
            if (_providers.contains(p)) {
                Log.d(TAG, "Removing: " + p.getClass().getSimpleName());
                _providers.remove(p);
            }
        }
    }

    public List<Provider> getProviders() {
        synchronized (_providers) {
            return Collections.unmodifiableList(_providers);
        }
    }

    /**
     * Get the best provider for the specified product
     * Prefer the "parent" repo
     * Prefer those that have a compatible update available
     * Finally, take first repo that contains this package
     *
     * @param product the product information to turn into a provider
     */
    public Provider getProvider(ProductInformation product) {

        //first see if parent repo is available and compatible
        if (product.getParent() != null) {
            ProductInformation pr = product.getParent().getProduct(
                    product.getPackageName());
            if (pr != null && pr.isCompatible()) {
                synchronized (_providers) {
                    for (Provider p : _providers) {
                        ProductRepository repo = p.get();
                        if (repo != null && repo.equals(pr.getParent())) {
                            Log.d(TAG, "Found parent provider: " + p
                                    + ", with compatible product: "
                                    + pr);
                            return p;
                        }
                    }
                }
            }
        }

        //look for first match with a compatible update available
        synchronized (_providers) {
            for (Provider p : _providers) {
                ProductRepository repo = p.get();
                if (repo == null)
                    continue;

                ProductInformation pr = repo
                        .getProduct(product.getPackageName());
                if (pr != null && pr.isCompatible()) {
                    Log.d(TAG, "Found provider: " + p
                            + ", with compatible product: " + pr);
                    return p;
                }
            }
        }

        //just take first match
        synchronized (_providers) {
            for (Provider p : _providers) {
                if (p.contains(product.getPackageName())) {
                    Log.d(TAG, "Found provider: " + p
                            + ", with product: " + product);
                    return p;
                }
            }
        }

        Log.w(TAG, "No provider found for: " + product);
        return null;
    }

    /**
     * Get the provider for the specified product
     *
     * @param pkg
     */
    public Provider getProvider(String pkg) {
        synchronized (_providers) {
            for (Provider p : _providers) {
                ProductRepository repo = p.get();
                if (repo != null && repo.hasProduct(pkg)) {
                    return p;
                }
            }
        }
        Log.w(TAG, "No provider found for: " + pkg);
        return null;
    }

    /**
     * Get all products from all repos
     * Note a given app (package name) is only included once, from the first repo it is found in
     *
     * @return
     */
    public List<ProductInformation> getAllProducts() {
        List<ProductRepository> repos = new ArrayList<>();

        synchronized (_providers) {

            for (Provider p : _providers) {
                ProductRepository repo = p.get();
                if (repo != null && repo.isValid()) {
                    repos.add(repo);
                }
            }
        }
        return getAllProducts(repos);
    }

    /**
     * Get all products from all repos
     * Note a given app (package name) is only included once, from the first repo it is found in
     *
     * @return
     */
    public static List<ProductInformation> getAllProducts(
            List<ProductRepository> repos) {

        Map<String, ProductInformation> map = new HashMap<>();

        for (ProductRepository repo : repos) {
            if (repo != null && repo.isValid()) {
                //get all products which we dont already have in the list
                //e.g. sideloaded is the last provider, so only take those apps if not in another repo
                repo.getUniqueProducts(map);
            } else {
                Log.w(TAG, "Skipping invalid repo");
            }
        }
        return new ArrayList<>(map.values());
    }

    /**
     * Always check providers for updates if the ATAK version code has changed
     * Also check during startup based on user pref
     * @return
     */
    public void init() {
        Log.d(TAG, "init");

        //first build out providers
        addProvider(new BundledProductProvider(_context));
        addProvider(new FileSystemProductProvider(_context, _prefs));
        addProvider(new RemoteProductProvider(_context, _prefs));
        //add sideloaded plugins last so we can see if plugins is covered by other providers
        addProvider(new SideloadedPluginProvider(_context));

        //now init each provider
        synchronized (_providers) {

            for (Provider p : _providers) {
                ProductRepository repo = p.get();
                if (repo == null || !repo.isValid()) {
                    Log.w(TAG, "init: " + p.getClass().getName()
                            + " no repo available");
                } else {
                    Log.d(TAG,
                            "init: " + p.getClass().getName() + ": "
                                    + repo);
                }
            }
        }
        // since if we never have, or have not since ATAK was updated, or if user wants to
        // sync during each startup
        int previousSync = _prefs.getInt("repoSyncedVersion", -1);
        boolean bStartupSync = _prefs.getBoolean("repoStartupSync", false);
        if (previousSync == -1
                || previousSync != ATAKConstants.getVersionCode()
                || bStartupSync) {
            Log.d(TAG, "Startup syncing repos");
            sync(true, true);
        } else {
            Log.d(TAG,
                    "Skipping startup sync, just checking for incompatible plugins");
            Set<String> incompatiblePlugins = AtakPluginRegistry.get()
                    .getIncompatiblePlugins();
            if (incompatiblePlugins != null && incompatiblePlugins.size() > 0) {
                new IncompatiblePluginWizard(_context, this, true)
                        .prompt(incompatiblePlugins);
            } else {
                Log.d(TAG, "No incompatible plugins found");
            }
        }
    }

    private boolean isAvailableUpdates() {
        Log.d(TAG, "isAvailableUpdates");

        //now check if any apps are out of date (e.g. an update available in a repo)
        synchronized (_providers) {
            for (Provider p : _providers) {
                ProductRepository repo = p.get();
                if (repo != null && repo.isValid()) {
                    List<ProductInformation> staleApps = repo
                            .getStale(_context);
                    if (!FileSystemUtils.isEmpty(staleApps)) {
                        Log.d(TAG, p.getClass().getName() + ": has stale apps: "
                                + staleApps.size());
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * See if any installed apps are out of date (have an update available)
     */
    public void checkForAvailableUpdates() {
        Log.d(TAG, "checkForAvailableUpdates");

        //now check if any apps are out of date (e.g. an update availble in a repo)
        boolean bPromptUserUpdate = false;
        synchronized (_providers) {
            for (Provider p : _providers) {
                ProductRepository repo = p.get();
                if (repo != null && repo.isValid()) {
                    List<ProductInformation> staleApps = repo
                            .getStale(_context);
                    if (!FileSystemUtils.isEmpty(staleApps)) {
                        Log.d(TAG, p.getClass().getName() + ": has stale apps: "
                                + staleApps.size());
                        bPromptUserUpdate = true;
                    }
                }
            }
        }
        if (bPromptUserUpdate) {
            Log.d(TAG, "prompting user to update stale apps");

            //TODO need some type of "ignore" option still
            //TODO which activity...?

            // if the AppMgmtActivity is showing use the activity context, 
            // otherwise use the other context
            Context c = AppMgmtActivity.getActivityContext();
            if (c == null)
                c = _context;

            AlertDialog.Builder b = new AlertDialog.Builder(c)
                    .setIcon(com.atakmap.android.util.ATAKConstants.getIconId())
                    .setTitle(R.string.app_mgmt_text3)
                    .setMessage(R.string.app_mgmt_text4)
                    .setPositiveButton(R.string.ok,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog,
                                        int which) {
                                    Intent mgmtPlugins = new Intent(_context,
                                            AppMgmtActivity.class);
                                    _context.startActivityForResult(
                                            mgmtPlugins,
                                            ToolsPreferenceFragment.APP_MGMT_REQUEST_CODE);
                                }
                            })
                    .setNegativeButton(R.string.cancel, null);
            try {
                b.show();
            } catch (Exception e) {
                Log.e(TAG, "dialog can no longer be shown", new Exception());
            }

        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ApkUpdateReceiver.APP_ADDED.equals(intent.getAction())) {
            String pkg = intent.getStringExtra("package");
            synchronized (_providers) {
                Log.d(TAG, "app added: " + pkg);
                for (Provider p : _providers) {
                    p.installed(pkg);
                }
            }
        } else if (ApkUpdateReceiver.APP_REMOVED.equals(intent.getAction())) {
            String pkg = intent.getStringExtra("package");
            Log.d(TAG, "app removed: " + pkg);
            synchronized (_providers) {
                for (Provider p : _providers) {
                    p.uninstalled(pkg);
                }
            }
        }
    }

    public void sync(final boolean bSilent, final boolean bCheckForIncompat) {
        sync(_context, bSilent, new RepoSyncListener() {
            @Override
            public void complete() {
                Log.d(TAG, "Sync complete, checking for updates...");
                _ongoingSyncTask = null;

                //first check if any previously loaded plugins are now incompatible
                Set<String> incompatiblePlugins = AtakPluginRegistry.get()
                        .getIncompatiblePlugins();
                if (bCheckForIncompat && incompatiblePlugins != null
                        && incompatiblePlugins.size() > 0) {
                    new IncompatiblePluginWizard(_context,
                            ProductProviderManager.this, true)
                                    .prompt(incompatiblePlugins);
                } else {
                    checkForAvailableUpdates();
                }
            }
        });
    }

    public void sync(final Context uiContext, final boolean bSilent,
            RepoSyncListener l) {
        //TODO sychronized?
        if (_ongoingSyncTask == null
                || _ongoingSyncTask.getStatus() == AsyncTask.Status.FINISHED) {
            if (l == null) {
                //if no listener is provided, we just send out an intent
                l = new RepoSyncListener() {
                    @Override
                    public void complete() {
                        AtakBroadcast
                                .getInstance()
                                .sendBroadcast(
                                        new Intent(
                                                ProductProviderManager.PRODUCT_REPOS_REFRESHED));
                    }
                };
            }

            Log.d(TAG, "Initiating sync task");
            _ongoingSyncTask = new RepoSyncTask(uiContext, this, bSilent, l);
            _ongoingSyncTask.execute();
        } else {
            //TODO toast? to wait or try again in a while...?
            Log.w(TAG, "sync task already running");
        }
    }

    private final BroadcastReceiver _appMgmtReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (VIEW_APP_MGMT.equals(intent.getAction())) {
                Log.d(TAG, "VIEW_APP_MGMT");
                Intent mgmtPlugins = new Intent(_context,
                        AppMgmtActivity.class);
                _context.startActivityForResult(mgmtPlugins,
                        ToolsPreferenceFragment.APP_MGMT_REQUEST_CODE);
            }
        }
    };

    /**
     * Listener for repo sync
     */
    public interface RepoSyncListener {
        void complete();
    }

    /**
     * Container with progress update
     */
    public static class ProgressDialogUpdate {
        final String message;
        int progress;

        public ProgressDialogUpdate(int p, String m) {
            progress = p;
            message = m;
        }
    }

    public interface ProgressDialogListener {
        void update(ProgressDialogUpdate progress);
    }

    /**
     * Simple background task to query list of conferences available on XMPP server
     * Display waiting dialog in the meantime
     *
     * 
     */
    private static class RepoSyncTask extends
            AsyncTask<Void, ProgressDialogUpdate, Void> {

        private static final String TAG = "RepoSyncTask";
        private final ProductProviderManager _manager;
        private final static int NOTIF_ID = 37865;
        private boolean _bAvailableUpdates;

        private class BoundedProgressListener
                implements ProgressDialogListener {

            private final int count;
            private final int total;

            public BoundedProgressListener(int count, int total) {
                this.count = count;
                this.total = total;
            }

            @Override
            public void update(ProgressDialogUpdate progress) {
                if (progress == null)
                    return;

                //adjust progress based on bounding total
                //progress.progress = count + progress.progress / total;
                progress.progress = count * 100 / total + progress.progress
                        / total;

                publishProgress(progress);
            }
        }

        /**
         * True to skip Android notificiation
         */
        private final boolean _bSilent;

        /**
         * Optional progress dialog
         */
        private ProgressDialog _progressDialog;

        /**
         * Callback listener to invoke when sync task is complete
         */
        private final RepoSyncListener _listener;

        /**
         * Context for the dialog
         */
        private final Context _uiContext;

        private NotificationManager _notifyManager;
        private Notification.Builder _builder;

        RepoSyncTask(Context uiContext, ProductProviderManager manager,
                boolean bSilent, RepoSyncListener l) {
            this._uiContext = uiContext;
            this._manager = manager;
            this._bSilent = bSilent;
            this._listener = l;
            this._bAvailableUpdates = false;
        }

        @Override
        protected void onPreExecute() {
            // Before running code in background/worker thread
            if (!_bSilent) {
                _progressDialog = new ProgressDialog(_uiContext);
                _progressDialog.setTitle("Syncing with Product Repositories");
                _progressDialog.setIcon(
                        com.atakmap.android.util.ATAKConstants.getIconId());
                _progressDialog.setMessage("Beginning sync...");
                _progressDialog.setCancelable(true);
                _progressDialog.setIndeterminate(false);
                _progressDialog
                        .setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                _progressDialog.show();
            }

            _notifyManager = (NotificationManager) _manager._context
                    .getSystemService(Context.NOTIFICATION_SERVICE);

            if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                _builder = new Notification.Builder(_manager._context);
            } else {
                _builder = new Notification.Builder(_manager._context,
                        "com.atakmap.app.def");
            }

            _builder.setContentTitle(
                    _uiContext.getString(R.string.scanning_apps))
                    .setContentText(_uiContext.getString(R.string.scanning))
                    .setSmallIcon(
                            com.atakmap.android.util.ATAKConstants.getIconId());

            //TODO maybe use notification as well so we can see scan progress even if dialog is not displayed?
        }

        @Override
        protected void onProgressUpdate(ProgressDialogUpdate... progress) {
            // set the current progress of the progress dialog UI
            if (_progressDialog != null && progress != null) {
                _progressDialog.setProgress(progress[0].progress);
                if (!FileSystemUtils.isEmpty(progress[0].message))
                    _progressDialog.setMessage(progress[0].message);
            }

            if (_notifyManager != null && _builder != null
                    && progress != null) {
                _builder.setProgress(100, progress[0].progress, false);
                _builder.setContentText(progress[0].message);

                _notifyManager.notify(NOTIF_ID,
                        _builder.build());
            }
        }

        @Override
        protected Void doInBackground(Void... params) {
            Thread.currentThread().setName(TAG);

            //TODO also update a notification if !bSilent

            String status = "Scanning system for existing plugins...";
            Log.d(TAG, "Progress: " + status);
            publishProgress(new ProgressDialogUpdate(1, status));

            //rebuild index for all repo providers
            List<ProductRepository> repos = new ArrayList<>();
            int index = 0;

            synchronized (_providers) {
                for (Provider p : _providers) {
                    ProductRepository repo = p
                            .rebuild(new BoundedProgressListener(
                                    index++, _providers.size()));
                    if (repo != null && repo.isValid()) {
                        Log.d(TAG,
                                "Rebuilt cache for: " + p.getClass().getName()
                                        + ", cnt=" + repo.getSize());
                        repos.add(repo);
                    } else {
                        Log.d(TAG, "No rebuilt cache available for: "
                                + p.getClass().getName());
                    }

                    //TODO anything to do with repos list?
                    int provider_size = _providers.size();
                    status = "Scanned " + index + " of " + provider_size
                            + " repos";

                    Log.d(TAG, "Progress: " + status);

                    if (provider_size > 0)
                        publishProgress(new ProgressDialogUpdate(index * 100
                                / provider_size, status));
                }
            }

            _bAvailableUpdates = _manager.isAvailableUpdates();

            status = "Finishing sync...";
            Log.d(TAG, "Progress: " + status);
            publishProgress(new ProgressDialogUpdate(99, status));

            return null;
        }

        @Override
        protected void onCancelled(Void aVoid) {
            super.onCancelled(aVoid);
            Log.d(TAG, "Cancelled");

            if (_progressDialog != null) {
                _progressDialog.dismiss();
                _progressDialog = null;
            }

            Toast.makeText(_manager._context, R.string.background_scanning,
                    Toast.LENGTH_SHORT).show();
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            Log.d(TAG, "onPostExecute: " + _bAvailableUpdates);
            //TODO error checking

            if (_progressDialog != null) {
                try {
                    _progressDialog.dismiss();
                } catch (IllegalArgumentException ae) {
                    Log.d(TAG, "dialog already dismissed");
                }
                _progressDialog = null;
            }

            _manager._prefs
                    .edit()
                    .putLong("repoLastSyncTime", System.currentTimeMillis())
                    .putInt("repoSyncedVersion", ATAKConstants.getVersionCode())
                    .apply();

            if (_bAvailableUpdates) {
                Intent notificationIntent = new Intent();
                notificationIntent.setAction(VIEW_APP_MGMT);

                NotificationUtil.getInstance().postNotification(NOTIF_ID,
                        com.atakmap.android.util.ATAKConstants.getIconId(),
                        NotificationUtil.WHITE,
                        _manager._context.getString(R.string.app_mgmt_text5),
                        _manager._context.getString(R.string.app_mgmt_text4),
                        notificationIntent, true);
            } else {
                NotificationUtil.getInstance().clearNotification(NOTIF_ID);
            }

            if (_listener != null) {
                //TODO pass through errors? e.g. failed to update from remote repo
                _listener.complete();
            }
        }
    }
}
