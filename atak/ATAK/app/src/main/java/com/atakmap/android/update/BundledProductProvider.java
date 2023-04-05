
package com.atakmap.android.update;

import android.app.Activity;
import android.content.res.AssetManager;

import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Provides products that are bundled within ATAK APK
 * Repo index stored at:  atak/support/apks/bundled/product.inf
 *
 *
 */
public class BundledProductProvider extends BaseProductProvider {

    private static final String TAG = "BundledProductProvider";

    public static final String LOCAL_BUNDLED_REPO_PATH = AppMgmtUtils.APK_DIR
            + File.separatorChar + "bundled" + File.separatorChar;
    private static final String LOCAL_BUNDLED_REPO_INDEX = LOCAL_BUNDLED_REPO_PATH
            + AppMgmtUtils.REPO_INDEX_FILENAME;

    public BundledProductProvider(Activity context) {
        super(context);
    }

    @Override
    protected ProductRepository load() {
        return load(false);
    }

    @Override
    public ProductRepository rebuild(
            ProductProviderManager.ProgressDialogListener l) {
        if (l != null) {
            l.update(new ProductProviderManager.ProgressDialogUpdate(1,
                    "Parsing repo cache index"));
        }

        ProductRepository repo = load(true);
        return check(repo, l);
    }

    /**
     * Checks a repo to see if the icons are properly extracted.
     */
    private ProductRepository check(ProductRepository repo,
            ProductProviderManager.ProgressDialogListener l) {

        try {
            if (repo == null || !repo.isValid() || !repo.hasProducts()) {
                Log.d(TAG, "No local bundled repo products available");
            } else {
                if (l != null) {
                    l.update(new ProductProviderManager.ProgressDialogUpdate(
                            50, "Extracting repo cache icons"));
                }

                Log.d(TAG, "Processing bundled repo: " + repo);
                for (ProductInformation product : repo.getProducts()) {
                    boolean bUpdateAvailable = false;
                    if (AppMgmtUtils.isInstalled(_context,
                            product.getPackageName())) {
                        final int installedVersion = AppMgmtUtils
                                .getAppVersionCode(_context,
                                        product.getPackageName());
                        if (installedVersion < product.getRevision()) {
                            Log.d(TAG,
                                    "Product has been updated: "
                                            + product);
                            bUpdateAvailable = true;
                        }
                    }

                    //if icon does not exist, or app has been updated, then extract icon
                    //assume icon uri is relative for the local repo
                    File productIcon = FileSystemUtils
                            .getItem(LOCAL_BUNDLED_REPO_PATH
                                    + product.getIconUri());
                    if (bUpdateAvailable
                            || !FileSystemUtils.isFile(productIcon)) {
                        Log.d(TAG,
                                "Extracting product icon: "
                                        + product.getIconUri());
                        if (!FileSystemUtils.copyFromAssetsToStorageFile(
                                _context, "apks/" + product.getIconUri(),
                                LOCAL_BUNDLED_REPO_PATH + product.getIconUri(),
                                true)) {
                            Log.e(TAG, "could not extract product icon for:"
                                    + product);
                        }
                    } else {
                        Log.d(TAG,
                                "Product icon current: "
                                        + productIcon.getAbsolutePath());
                    }
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "error occurred handling updates", e);
        }

        if (l != null) {
            l.update(new ProductProviderManager.ProgressDialogUpdate(99,
                    "Repo icons extracted"));
        }

        _cache = repo;
        return _cache;
    }

    protected ProductRepository load(boolean bReload) {
        File dir = FileSystemUtils.getItem("support/apks");
        if (!IOProviderFactory.exists(dir)) {
            if (!IOProviderFactory.mkdirs(dir)) {
                Log.d(TAG, "unable to create support directory: " + dir);
            }
        }

        final File localRepo = FileSystemUtils
                .getItem(LOCAL_BUNDLED_REPO_INDEX);

        if (FileSystemUtils.copyFromAssetsToStorageFile(
                _context, "apks/" + AppMgmtUtils.REPO_INDEX_FILENAME,
                LOCAL_BUNDLED_REPO_INDEX, bReload)) {

            // since the product.inf needed to be extracted, we will
            // need to extract the rest of the icons
            return check(
                    parseRepo(_context.getString(R.string.app_mgmt_bundled),
                            localRepo),
                    null);
        } else {

            return parseRepo(_context.getString(R.string.app_mgmt_bundled),
                    localRepo);
        }
    }

    /**
     * Pull specified APK from ATAK APK resources, and place on filesystem outside of the IO abstraction
     * @param product the product information entry that describes a plugin or tool bundled with
     *                the software.
     * @return the file for the APK
     */
    @Override
    public File getAPK(final ProductInformation product) {
        AssetManager assetManager = _context.getAssets();

        File apkroot = FileSystemUtils
                .getItem(BundledProductProvider.LOCAL_BUNDLED_REPO_PATH);
        if (!apkroot.exists()) {
            if (!apkroot.mkdirs()) {
                Log.d(TAG,
                        " Failed to make dir at " + apkroot.getAbsolutePath());
            }
        }

        File apkfile = new File(apkroot, product.getAppUri());
        Log.d(TAG, "Extracting app to: " + apkfile.getAbsolutePath());
        try {

            // the resulting copyStream call properly closes the FileOutputStream() created 
            FileSystemUtils.copyStream(
                    assetManager.open("apks/" + product.getAppUri()),
                    new FileOutputStream(apkfile));
        } catch (Exception e) {
            Log.e(TAG, "failed to install: " + product, e);
            return null;
        }

        return apkfile;
    }
}
