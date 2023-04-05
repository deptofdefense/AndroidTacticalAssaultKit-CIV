
package com.atakmap.android.update;

import android.app.Activity;

import com.atak.plugins.impl.AtakPluginRegistry;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.annotations.ModifierApi;

import java.io.File;

public abstract class BaseProductProvider implements
        ProductProviderManager.Provider {

    private static final String TAG = "BaseProductProvider";
    protected ProductRepository _cache;

    @ModifierApi(since = "4.2", target = "4.5", modifiers = {
            "private"
    })
    protected Activity _context;

    public BaseProductProvider(Activity context) {
        _context = context;
    }

    @Override
    public void dispose() {
    }

    @Override
    public ProductRepository get() {
        if (_cache == null)
            _cache = load();

        return _cache;
    }

    /**
     * Load cached index
     *
     * @return load the cached indicies.
     */
    protected abstract ProductRepository load();

    /**
     * Runs on UI thread, must be quick (i.e. file already on filesystem). If long running, then
     * impls should override install() and getAPK() to offline to background thread
     *
     * @param product the product information
     * @return the apk associated with the product information
     */
    protected abstract File getAPK(final ProductInformation product);

    @Override
    public boolean contains(String pkg) {
        return !(_cache == null || FileSystemUtils.isEmpty(pkg))
                && _cache.hasProduct(pkg);

    }

    /**
     * Base impl ignores bForce
     *
     * @param product install the product described by the passed in product information
     */
    @Override
    public void install(final ProductInformation product) {
        //add to list of outstanding installs
        ApkUpdateComponent updateComponent = ApkUpdateComponent.getInstance();
        if (updateComponent != null)
            updateComponent.getApkUpdateReceiver().addInstalling(product);

        //get the file
        File apk = getAPK(product);
        //attempt install
        if (!AppMgmtUtils.install(_context, apk)) {
            Log.w(TAG, "installProduct failed: " + product.toString());
        }
    }

    @Override
    public void uninstall(final ProductInformation product) {
        if (product.isPlugin()) {
            AtakPluginRegistry.get().unloadPlugin(product.getPackageName());
        }

        AppMgmtUtils.uninstall(_context, product.getPackageName());
    }

    @Override
    public boolean installed(String pkg) {
        if (contains(pkg)) {
            Log.d(TAG, this + ": installed, " + pkg);
            if (_cache.installed(_context, pkg)) {
                Log.d(TAG, "installProduct Updated: " + pkg);
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean uninstalled(String pkg) {
        if (contains(pkg)) {
            Log.d(TAG, this + ": uninstalled, " + pkg);
            if (_cache.uninstalled(_context, pkg)) {
                Log.d(TAG, "Uninstall Updated: " + pkg);
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    protected ProductRepository parseRepo(String repoType, File repoIndex) {
        ProductRepository repo = ProductRepository.parseRepo(_context,
                repoType, repoIndex);
        if (repo != null && repo.isValid()) {
            Log.d(TAG, "Updating local repo: " + repo);
        } else {
            Log.d(TAG, "Clearing local repo: " + repoIndex);
            repo = null;
        }

        return repo;
    }

    @Override
    public String toString() {
        return this.getClass().getName();
    }
}
