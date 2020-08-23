
package com.atakmap.android.importfiles.sort;

import android.content.Context;
import android.util.Pair;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.update.ApkUpdateComponent;
import com.atakmap.android.update.AppMgmtUtils;
import com.atakmap.android.update.FileSystemProductProvider;
import com.atakmap.android.update.ProductProviderManager;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.util.Set;

/**
 * Sorts INFZ Product Repo caches, and initiates a repo sync
 * 
 * 
 */
public class ImportINFZSort extends ImportInternalSDResolver {

    private static final String TAG = "ImportINFZSort";

    private final static String INFMATCH = ",";

    public ImportINFZSort(Context context, boolean validateExt) {
        super(".infz",
                FileSystemProductProvider.LOCAL_REPO_PATH,
                validateExt, false,
                context.getString(R.string.app_mgmt_product_repo),
                context.getDrawable(R.drawable.ic_menu_plugins));
    }

    @Override
    public boolean match(File file) {
        if (!super.match(file))
            return false;

        // it is a .infz, now lets see if it has a product.inf
        boolean bMatch = isRepoCache(file);
        Log.d(TAG, "Repo index " + (bMatch ? "found" : "not found"));
        return bMatch;
    }

    @Override
    public File getDestinationPath(File file) {
        File dest = super.getDestinationPath(file);
        if (dest == null)
            return null;

        //FileSystemProductProvider expects "product.infz" for filename, but we can match on *.infz
        return new File(dest.getParentFile(),
                AppMgmtUtils.REPOZ_INDEX_FILENAME);
    }

    @Override
    protected void onFileSorted(File src, File dst, Set<SortFlags> flags) {
        super.onFileSorted(src, dst, flags);
        Log.d(TAG, "Sorted, now initiating repo sync");

        //remove old INF, and we'll extract new oen from the INFZ
        File customInf = FileSystemUtils
                .getItem(FileSystemProductProvider.LOCAL_REPO_INDEX);
        if (FileSystemUtils.isFile(customInf)) {
            FileSystemUtils.delete(customInf);
        }

        //kick off a repo sync
        final ApkUpdateComponent updateComponent = ApkUpdateComponent
                .getInstance();
        final ProductProviderManager providerManager = updateComponent
                .getProviderManager();
        if (providerManager != null) {
            MapView.getMapView().post(new Runnable() {
                @Override
                public void run() {
                    providerManager.sync(false, false);
                }
            });
        }
    }

    @Override
    public Pair<String, String> getContentMIME() {
        return new Pair<>("Product Repo Cache", "application/zip");
    }

    /**
     * Check if the specified zip has a supported product.inf
     *
     * @param zip
     * @return
     */
    private static boolean isRepoCache(File zip) {
        String index = FileSystemUtils.GetZipFileString(zip,
                AppMgmtUtils.REPO_INDEX_FILENAME);
        if (!FileSystemUtils.isEmpty(index) && index.contains(INFMATCH)) {
            return true;
        }

        // zip has no index
        return false;
    }
}
