
package com.atakmap.android.update;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.atak.plugins.impl.AtakPluginRegistry;
import com.atakmap.android.http.rest.DownloadProgressTracker;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Provide plugins that are installed on the local system, and not from another repo impl
 * Repo index stored only in RAM
 *
 */
public class SideloadedPluginProvider extends BaseProductProvider {

    private static final String TAG = "SideloadedPluginProvider";

    public static final String LOCAL_SIDELOADED_REPO_PATH = AppMgmtUtils.APK_DIR
            + File.separatorChar + "sideloaded" + File.separatorChar;
    private static final String LOCAL_SIDELOADED_REPO_INDEX = LOCAL_SIDELOADED_REPO_PATH
            + AppMgmtUtils.REPO_INDEX_FILENAME;

    public SideloadedPluginProvider(Activity context) {
        super(context);
    }

    @Override
    protected ProductRepository load() {
        //Note this index is created during rebuild() below
        File localRepo = FileSystemUtils.getItem(LOCAL_SIDELOADED_REPO_INDEX);
        return parseRepo(_context.getString(R.string.app_mgmt_sideloaded),
                localRepo);
    }

    /**
     * Override to convert to SideloadedPluginInformation instances
     *
     * @param repoType the type of repository
     * @param repoIndex the index file for the repository
     * @return the ProductRepository object.
     */
    @Override
    public ProductRepository parseRepo(final String repoType,
            final File repoIndex) {

        //Uri customRepoUri = Uri.fromFile(repoIndex);
        final ProductRepository repo = ProductRepository.parseRepo(_context,
                repoType,
                repoIndex);
        if (repo != null && repo.isValid()) {
            Log.d(TAG, "Updating sideloaded local repo: " + repo);
            List<ProductInformation> plugins = new ArrayList<>();
            for (ProductInformation plugin : repo.getProducts()) {
                SideloadedPluginInformation slp = new SideloadedPluginInformation(
                        plugin, _context);
                if (slp == null || !slp.isValid()) {
                    Log.w(TAG, "Skipping invalid plugin: " + slp);
                    continue;
                }

                plugins.add(slp);
            }
            repo.setProducts(plugins);
            return repo;

        } else {
            Log.d(TAG, "Clearing local repo: " + repoIndex);
            return null;
        }

    }

    @Override
    public ProductRepository rebuild(
            final ProductProviderManager.ProgressDialogListener l) {
        //Note it is assumed we are running on background thread, so we scan directly
        _cache = scan(_context, l);
        if (_cache != null) {
            //now save index for use until next scan
            _cache.save(FileSystemUtils.getItem(LOCAL_SIDELOADED_REPO_INDEX));
        }

        return _cache;
    }

    private static ProductRepository scan(final Context context,
            final ProductProviderManager.ProgressDialogListener listener) {
        Log.d(TAG, "scanSideloadedApps");

        final List<ProductInformation> products = new ArrayList<>();
        final ProductRepository repo = new ProductRepository(
                context.getString(R.string.app_mgmt_sideloaded),
                context.getString(R.string.app_mgmt_sideloaded), products);

        final AtakPluginRegistry pluginRegistry = AtakPluginRegistry.get();
        // Note we scan here to get total # apps, and scan again below to get updated list of
        // plugins... only need to scan once but its a pretty quick operation
        pluginRegistry.scan();
        int total = pluginRegistry.getNumberOfAppsInstalled();
        if (listener != null) {
            listener.update(new ProductProviderManager.ProgressDialogUpdate(0,
                    "Scanning " + total + " apps"));
        }

        final DownloadProgressTracker progressTracker = new DownloadProgressTracker(
                100);

        //blocking call, uses callbacks for reporting
        pluginRegistry
                .scanAndLoadPlugins(
                        new AtakPluginRegistry.PluginLoadingProgressCallback() {
                            @Override
                            public void onProgressUpdated(int percent) {
                                if (listener != null) {
                                    //Log.d(TAG, "onProgressUpdated: " + percent);

                                    //limit how often we update the notification
                                    long currentTime = System
                                            .currentTimeMillis();
                                    if (progressTracker.contentReceived(percent,
                                            currentTime)) {
                                        listener.update(
                                                new ProductProviderManager.ProgressDialogUpdate(
                                                        percent,
                                                        "Scanning installed plugins "
                                                                + percent
                                                                + "%"));
                                        progressTracker.notified(currentTime);
                                    }
                                }
                            }

                            @Override
                            public void onComplete(
                                    final int numLoaded) {
                                Log.d(TAG,
                                        "Loaded "
                                                + numLoaded
                                                + " plugins");

                                Collection<AtakPluginRegistry.PluginDescriptor> discovered = pluginRegistry
                                        .getPluginDescriptors();
                                if (discovered != null
                                        && discovered.size() > 0) {
                                    for (AtakPluginRegistry.PluginDescriptor plugin : discovered) {
                                        ProductInformation product = new SideloadedPluginInformation(
                                                repo, context, plugin);
                                        if (product != null
                                                && product.isValid()) {
                                            products.add(product);
                                        } else {
                                            Log.w(TAG,
                                                    "Failed to convert plugin: "
                                                            + plugin);
                                        }
                                    }

                                }
                            }
                        });

        //Collections.sort(pluginList, new PluginDescriptorSorter(context));

        if (FileSystemUtils.isEmpty(products)) {
            Log.d(TAG, "No sideloaded plugins found");
            return null;
        }

        repo.setProducts(products);

        if (repo.isValid()) {
            Log.d(TAG, "Updating local side loaded repo: " + repo);
            return repo;
        } else {
            Log.d(TAG, "Clearing local side loaded repo");
            return null;
        }
    }

    @Override
    public File getAPK(final ProductInformation product) {
        Log.w(TAG, "getAPK unsupported: " + product.toString());
        return null;
    }

    @Override
    public void install(ProductInformation product) {
        installProduct(product);
    }

    static boolean installProduct(ProductInformation plugin) {
        Log.d(TAG, "install: " + plugin.toString());

        //already installed/sideloaded, just load plugin into ATAK
        if (!AtakPluginRegistry.get().loadPlugin(plugin.getPackageName())) {
            Log.w(TAG, "Failed to load plugin: " + plugin);
            return false;
        }

        //notify listeners
        AtakBroadcast.getInstance().sendBroadcast(
                new Intent(ApkUpdateReceiver.APP_ADDED)
                        .putExtra("package", plugin.getPackageName()));
        return true;
    }

    /**
     * unloadProduct the plugin from ATAK, but do no uninstall from OS
     * @param plugin the application to unloadProduct based on product information
     */
    static void unloadProduct(ProductInformation plugin) {
        Log.d(TAG, "unloadProduct: " + plugin.toString());
        AtakPluginRegistry.get().unloadPlugin(plugin.getPackageName());
        AtakBroadcast.getInstance().sendBroadcast(
                new Intent(ProductProviderManager.PRODUCT_REPOS_REFRESHED));
    }

    @Override
    public boolean installed(String pkg) {
        //this repo only supports plugins
        if (!AtakPluginRegistry.get().isPlugin(pkg))
            return false;

        //if we have it already, just update internal state, no need to save out as "installed version"
        //is not persisted in the repo index
        if (super.installed(pkg))
            return true;

        //if we dont have it, and no other repos do, then add and save out
        ProductProviderManager mgr = ApkUpdateComponent.getInstance()
                .getProviderManager();
        ProductProviderManager.Provider provider = mgr.getProvider(pkg);
        if (provider == null) {
            AtakPluginRegistry.PluginDescriptor plugin = AtakPluginRegistry
                    .get().getPlugin(pkg);
            if (plugin == null) {
                Log.w(TAG, "Failed to find installed plugin: " + pkg);
                return false;
            }

            Log.d(TAG, this + ": installed added, " + pkg);
            final SideloadedPluginInformation product = new SideloadedPluginInformation(
                    this._cache, _context, plugin);
            if (!product.isValid()) {
                Log.w(TAG, "Failed to find installed plugin: " + pkg);
                return false;
            }

            // if the _cache has not been generated and a product was installed
            if (_cache != null && _cache.addProduct(product)) {
                Log.d(TAG, "install Updated: " + pkg);
                return _cache.save(FileSystemUtils
                        .getItem(LOCAL_SIDELOADED_REPO_INDEX));
            }
        }

        return false;
    }

    @Override
    public boolean uninstalled(String pkg) {
        //if we have it, remove from internal state, and save state out
        if (contains(pkg)) {
            Log.d(TAG, this + ": uninstalled, " + pkg);
            if (_cache.removeProduct(pkg)) {
                Log.d(TAG, "Uninstall Updated: " + pkg);
                return _cache.save(FileSystemUtils
                        .getItem(LOCAL_SIDELOADED_REPO_INDEX));
            }
        }

        return false;
    }
}
