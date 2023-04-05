
package com.atakmap.android.update;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import com.atakmap.android.http.rest.operation.NetworkOperation;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.update.http.GetRepoIndexOperation;
import com.atakmap.android.update.http.RepoIndexRequest;
import com.atakmap.app.R;
import com.atakmap.comms.http.TakHttpClient;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.DefaultIOProvider;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.atakmap.filesystem.HashingUtils;
import com.foxykeep.datadroid.exception.ConnectionException;
import com.foxykeep.datadroid.exception.DataException;

import org.apache.http.HttpStatus;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * Provide products from a remote repo
 * Repo index stored at: atak/support/apks/remote/product.inf
 *
 *
 */
public class RemoteProductProvider extends BaseProductProvider {

    private static final String TAG = "RemoteProductProvider";

    public static final String REMOTE_REPO_CACHE_PATH = AppMgmtUtils.APK_DIR
            + File.separatorChar + "remote" + File.separatorChar;
    private static final String REMOTE_REPO_INDEX = REMOTE_REPO_CACHE_PATH
            + AppMgmtUtils.REPO_INDEX_FILENAME;
    private static final String REMOTE_REPOZ_INDEX = REMOTE_REPO_CACHE_PATH
            + AppMgmtUtils.REPOZ_INDEX_FILENAME;

    private final SharedPreferences _prefs;

    public RemoteProductProvider(Activity context, SharedPreferences prefs) {
        super(context);
        _prefs = prefs;

        if (!_prefs.contains("atakUpdateServerUrl")) {
            Log.d(TAG,
                    "Setting default remote repo: "
                            + context
                                    .getString(
                                            R.string.atakUpdateServerUrlDefault));
            _prefs.edit()
                    .putString(
                            "atakUpdateServerUrl",
                            context.getString(
                                    R.string.atakUpdateServerUrlDefault))
                    .apply();
        }
    }

    @Override
    protected ProductRepository load() {
        return parseRepo(_context.getString(R.string.app_mgmt_update_server),
                FileSystemUtils.getItem(REMOTE_REPO_INDEX));
    }

    /**
     * Only execute from non-UI thread
     *
     * @param l the listener for the rebuild action
     * @return the ProductRepository as a result of calling rebuild
     */
    @Override
    public ProductRepository rebuild(
            final ProductProviderManager.ProgressDialogListener l) {

        if (!_prefs.getBoolean("appMgmtEnableUpdateServer", false)) {
            clearLocalCache(null);
            return null;
        }

        final String updateUrl = getRemoteRepoUrl();
        if (FileSystemUtils.isEmpty(updateUrl)) {
            clearLocalCache("Update Server URL not set");
            return null;
        }

        Log.d(TAG, "rebuild: Remote repo URL: " + updateUrl);

        File repoDir = FileSystemUtils.getItem(REMOTE_REPO_CACHE_PATH);
        if (!repoDir.exists()) {
            if (!repoDir.mkdirs()) {
                Log.e(TAG, "failed to wrap" + repoDir);
            }
        }

        //send request w/out credentials initially
        final RepoIndexRequest request = new RepoIndexRequest(updateUrl,
                repoDir.getAbsolutePath(), ApkDownloader.notificationId, false,
                ApkDownloader.getBasicUserCredentials(updateUrl));

        if (l != null) {
            l.update(new ProductProviderManager.ProgressDialogUpdate(1,
                    "Downloading remote repo index"));
        }

        // set to false in order to perform testing against insecure http
        if (!updateUrl.toLowerCase(LocaleUtil.getCurrent())
                .startsWith("https")) {
            clearLocalCache("Update Server must be HTTPs");
            return null;
        }

        //Note it is assumed we are running on background thread, so we execute network operation
        //directly, rather than using HTTPRequestService
        try {
            Bundle b = GetRepoIndexOperation.GetFile(_context,
                    TakHttpClient.GetHttpClient(updateUrl),
                    request, 0, 1, false, l);

            if (b != null
                    && b.containsKey(GetRepoIndexOperation.PARAM_GETCREDENTIALS)
                    &&
                    b.getBoolean(GetRepoIndexOperation.PARAM_GETCREDENTIALS)) {
                _context.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ApkDownloader.promptForCredentials(_context, request,
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        AtakBroadcast
                                                .getInstance()
                                                .sendBroadcast(
                                                        new Intent(
                                                                AppMgmtActivity.SYNC));
                                    }
                                });
                    }
                });
                return null;
            }
            if (b != null)
                return processBundle(b, l);
        } catch (DataException | ConnectionException e) {
            Log.w(TAG, "Failed to get repo index: " + updateUrl, e);
            clearLocalCache(e.getMessage());
        }
        return _cache;

    }

    private ProductRepository processBundle(Bundle b,
            final ProductProviderManager.ProgressDialogListener l)
            throws DataException, ConnectionException {
        if (b == null
                || b.getInt(
                        NetworkOperation.PARAM_STATUSCODE) != HttpStatus.SC_OK) {
            throw new ConnectionException("Failed to get repo index");
        }

        final File index = FileSystemUtils.getItem(REMOTE_REPO_INDEX);
        if (!index.exists()) {
            throw new DataException("Failed to extract file: "
                    + index.getAbsolutePath());
        }

        if (l != null) {
            l.update(new ProductProviderManager.ProgressDialogUpdate(99,
                    "Parsing repo cache index"));
        }

        _cache = parseRepo(
                _context.getString(R.string.app_mgmt_update_server),
                index);

        _prefs.edit().putBoolean("repoRemoteServerLastStatus", true)
                .putLong("repoRemoteServerLastTimeMillis",
                        new CoordinatedTime().getMilliseconds())
                .apply();
        return _cache;

    }

    private void clearLocalCache(final String reason) {
        Log.d(TAG, reason);

        if (reason != null && reason.length() > 0) {
            _context.runOnUiThread(new Runnable() {
                public void run() {
                    Toast.makeText(_context, reason, Toast.LENGTH_SHORT).show();
                }
            });
        }

        //TODO also delete all cached icons and APKs?
        _prefs.edit().putString("repoRemoteServerLastReason", reason)
                .putBoolean("repoRemoteServerLastStatus", false)
                .putLong("repoRemoteServerLastTimeMillis",
                        new CoordinatedTime().getMilliseconds())
                .apply();

        //if we failed to sync with the update server, lets delete the local index/cache
        File toDelete = FileSystemUtils.getItem(REMOTE_REPO_INDEX);
        if (toDelete.exists()) {
            toDelete.delete();
        }
        toDelete = FileSystemUtils.getItem(REMOTE_REPOZ_INDEX);
        if (toDelete.exists()) {
            toDelete.delete();
        }

        _cache = null;
    }

    private String getRemoteRepoUrl() {
        String updateUrl = _prefs.getString("atakUpdateServerUrl",
                _context.getString(R.string.atakUpdateServerUrlDefault));
        if (FileSystemUtils.isEmpty(updateUrl)) {
            //TODO toast on all these errors
            Log.d(TAG, "rebuild: Remote repo URL not set");
            return null;
        }

        //see if user just typed IP address
        if (!updateUrl.startsWith("http")) {
            updateUrl = "http://" + updateUrl;
        }

        return updateUrl;
    }

    /**
     * Extract the repo cache into apk/support/remote
     *
     * @param infz the infz file to extract
     * @return true if the file extracted correctly
     */
    public static boolean extract(File infz) {
        return extract(infz, FileSystemUtils.getItem(REMOTE_REPO_CACHE_PATH));
    }

    /**
     * Dumb cache clear for now
     */
    static public void cleanup(final File infz, final File dest) {
        final File oldInf = new File(dest, "product.inf");
        final File newInf = new File(dest, "product.orig");

        if (!FileSystemUtils.renameTo(oldInf, newInf,
                new DefaultIOProvider())) {
            Log.w(TAG, "Cannot rename file: " + oldInf);
            return;
        }
        try {
            FileSystemUtils.unzip(infz, dest, true, new DefaultIOProvider());
        } catch (IOException e) {
            Log.w(TAG, "Cannot extract file: " + infz.getAbsolutePath(), e);
            return;
        }
        String oldHash = HashingUtils.md5sum(oldInf);
        String newHash = HashingUtils.md5sum(newInf);
        if (oldHash != null && newHash != null) {
            if (!oldHash.equals(newHash)) {
                // in order for the infz file not to get clobbered

                Log.d(TAG,
                        "repository information has changed, hashcodes do not match");
                final File tmp = FileSystemUtils.moveToTemp(MapView
                        .getMapView().getContext(), infz, true,
                        new DefaultIOProvider());

                if (!dest.delete()) {
                    Log.w(TAG, "could not delete the current destination folder"
                            + dest);
                }
                if (!dest.mkdirs()) {
                    Log.w(TAG,
                            "Cannot create repo index "
                                    + dest.getAbsolutePath());
                }

                if (!FileSystemUtils.renameTo(tmp, infz,
                        new DefaultIOProvider())) {
                    Log.w(TAG, "Cannot rename file: " + oldInf);
                }
            } else {
                Log.d(TAG,
                        "repository information has not changed, hashcodes match");
            }
        } else {
            Log.d(TAG,
                    "error computing has codes, failing out");
        }

    }

    /**
     * Extract the repo cache into the specified destination directory
     * @param infz the infz file describing the metadata for all apk offerings
     * @param dest the destination to extract the infz file to
     * @return true if the extraction is successful
     */
    public static boolean extract(File infz, File dest) {
        if (infz == null || !infz.exists()) {
            Log.w(TAG,
                    "Cannot extract invalid file: " + ((infz == null) ? "null"
                            : infz.getAbsolutePath()));
            return false;
        }

        if (!dest.exists()
                && !dest.mkdirs()) {
            Log.w(TAG, "Cannot create repo index " + dest.getAbsolutePath());
        }

        cleanup(infz, dest);

        try {
            FileSystemUtils.unzip(infz, dest, true, new DefaultIOProvider());
        } catch (IOException e) {
            Log.w(TAG, "Cannot extract file: " + infz.getAbsolutePath(), e);
            return false;
        }

        File index = new File(dest, AppMgmtUtils.REPO_INDEX_FILENAME);
        if (!index.exists()) {
            Log.w(TAG, "Failed to extract file: " + infz.getAbsolutePath());
            return false;
        }

        return true;
    }

    @Override
    public File getAPK(final ProductInformation product) {
        //See APKDownloader
        Log.w(TAG, "getAPK not supported: " + product.toString());
        return null;
    }

    /**
     * Leverage HTTPRequestService to download APK
     * @param product the product to install.
     */
    @Override
    public void install(final ProductInformation product) {
        Log.d(TAG, "install: " + product.toString());

        //TODO does BundledProductProvider need to be async? due to extracting APK from APK, to file system

        //get absolute HTTP link to repo index
        String repoApkUrl = getRemoteRepoUrl();
        if (FileSystemUtils.isEmpty(repoApkUrl)) {
            //TODO toast?
            Log.w(TAG,
                    "Cannot install without remote repo URL: "
                            + product);
            return;
        }

        if (!repoApkUrl.endsWith(AppMgmtUtils.REPO_INDEX_FILENAME)
                && !repoApkUrl.endsWith(AppMgmtUtils.REPOZ_INDEX_FILENAME)) {
            if (!repoApkUrl.endsWith("/")) {
                repoApkUrl += "/";
            }
            repoApkUrl += AppMgmtUtils.REPOZ_INDEX_FILENAME;
        }

        //now get absolute link to APK
        if (product.getAppUri().startsWith("http://")
                || product.getAppUri().startsWith("https://")) {
            Log.d(TAG, "Already absolute APK link: " + product.getAppUri());
        } else {
            Log.d(TAG, "Processing relative APK link: " + product.getAppUri()
                    + ", with base URI: " + repoApkUrl);

            //relative URL, strip off the /product.inf and build out path to APK
            String baseUrl = repoApkUrl;
            int index = baseUrl.lastIndexOf("/");
            if (index < 0) {
                Log.d(TAG, "Unable to parse base URL: " + baseUrl);
                return;
            }

            baseUrl = baseUrl.substring(0, index + 1);
            String relPath = product.getAppUri();
            if (relPath.startsWith("/"))
                relPath = relPath.substring(1);
            repoApkUrl = baseUrl + relPath;
        }

        //now get filename
        String filename = product.getAppUri();
        int index = filename.lastIndexOf("/");
        if (index > 0) {
            filename = filename.substring(index + 1);
        }

        download(product, repoApkUrl, filename);
    }

    @Override
    public boolean isRemote() {
        return true;
    }

    /**
     * Override to convert to use java.io.FileReader outside of IO Abstraction
     *
     * @param repoType The type of repo; such as remote in this case
     * @param repoIndex The file for the repo index
     * @return The product repository
     */
    @Override
    public ProductRepository parseRepo(String repoType, File repoIndex) {
        ProductRepository repo = null;
        try {
            repo = ProductRepository.parseRepo(_context,
                    repoIndex.getAbsolutePath(),
                    repoType, new BufferedReader(new FileReader(repoIndex)));
        } catch (IOException e) {
            Log.w(TAG, "Failed parse: " + repoIndex.getAbsolutePath(), e);
        }

        if (repo != null && repo.isValid()) {
            Log.d(TAG, "Updating local repo: " + repo);
        } else {
            Log.d(TAG, "Clearing local repo: " + repoIndex);
            repo = null;
        }

        return repo;
    }

    private void download(ProductInformation product, String repoApkUrl,
            String filename) {
        //do not prompt, just proceed to download and install
        Toast.makeText(_context, "Installing " + product.getSimpleName(),
                Toast.LENGTH_SHORT).show();
        Log.d(TAG, "download and install: " + product + " from: "
                + repoApkUrl);

        //add to list of outstanding installs
        ApkUpdateComponent updateComponent = ApkUpdateComponent.getInstance();
        if (updateComponent != null)
            updateComponent.getApkUpdateReceiver().addInstalling(product);
        //TODO remove from this list if download fails

        AtakBroadcast.getInstance().sendBroadcast(
                new Intent(ApkUpdateReceiver.DOWNLOAD_APK)
                        .putExtra("url", repoApkUrl)
                        .putExtra("package", product.getPackageName())
                        .putExtra("hash", product.getHash())
                        .putExtra("filename", filename)
                        .putExtra("install", true));
    }
}
