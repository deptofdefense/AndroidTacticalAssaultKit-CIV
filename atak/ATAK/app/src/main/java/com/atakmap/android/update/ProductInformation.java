
package com.atakmap.android.update;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;

import com.atak.plugins.impl.AtakPluginRegistry;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.graphics.GLCapture;
import com.atakmap.android.util.ATAKConstants;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.filesystem.HashingUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Represent an app or plugin app
 *
 *
 */
public class ProductInformation {

    private static final String TAG = "ProductInformation";

    private static final long UNKNOWN_FILESIZE = -1;
    public static final String FILE_PREFIX = "file:";

    /**
     * Reference to repo containing this product
     */
    private ProductRepository parent;

    public enum Platform {
        Android,
        Windows,
        iOS
    }

    public enum ProductType {
        app,
        plugin,
        systemplugin;

        /**
         * Helper method to obtain a more specialized version of the plugin type.
         * @param pkgName the package name to be used
         * @param type the type that is currently known about the plugin
         * @return a more specialized type if the plugin is known as a system plugin.
         */
        public static ProductType getSpecificPluginType(final String pkgName,
                final ProductType type) {
            if (type == ProductType.plugin &&
                    (pkgName.startsWith("com.atakmap.app.flavor.")
                            || pkgName
                                    .startsWith("com.atakmap.app.encryption.")))
                return ProductType.systemplugin;
            return type;
        }
    }

    protected final Platform platform;
    protected final ProductType productType;
    protected final String packageName;
    protected final String simpleName;

    /**
     * Repo available version
     */
    protected final String version;

    /**
     * Repo available revision
     */
    protected final int revision;

    /**
     * Absolute path (URL for remote repo, or file path for local repo
     */
    protected String appUri;

    /**
     * Absolute path (file path for both remote repo and local repo)
     */
    protected String iconUri;

    protected final String description;
    protected final String hash;
    protected final int osRequirement;
    protected long fileSize;

    /**
     * e.g. plugin-api
     */
    protected final String takRequirement;

    /**
     * Currently installed version, or -1
     */
    protected int installedVersion;

    private static final String DELIMITER = ",";
    protected final static Pattern DELIMITER_PATTERN;

    static {
        DELIMITER_PATTERN = Pattern.compile(DELIMITER);
    }

    public ProductInformation(ProductRepository repo,
            Platform platform, ProductType productType, String packageName,
            String simpleName, String version, int revision,
            String appUri, String iconUri, String description,
            String hash, int osRequirement, String takRequirement,
            int installedVersion) {
        this.parent = repo;
        this.platform = platform;
        this.productType = ProductType.getSpecificPluginType(packageName,
                productType);
        this.packageName = packageName;
        this.simpleName = simpleName;
        this.version = version;
        this.revision = revision;
        this.appUri = appUri;
        this.iconUri = iconUri;
        this.description = description;
        this.hash = hash;
        this.osRequirement = osRequirement;
        this.takRequirement = takRequirement;
        this.installedVersion = installedVersion;
        fileSize = UNKNOWN_FILESIZE;
    }

    public ProductInformation(ProductRepository repo, String pkgName,
            String simpleName, int revision, String url, int installedVersion) {
        this.parent = repo;
        this.platform = Platform.Android;
        this.productType = ProductType.getSpecificPluginType(pkgName,
                ProductType.plugin);
        this.packageName = pkgName;
        this.simpleName = simpleName;
        this.version = null;
        this.revision = revision;
        this.appUri = url;
        this.iconUri = null;
        this.description = null;
        this.hash = null;
        this.osRequirement = 1;
        this.takRequirement = null;
        this.installedVersion = installedVersion;
        fileSize = UNKNOWN_FILESIZE;
    }

    public static ProductInformation create(final ProductRepository repo,
            final Context context, final String paramString) {
        if (FileSystemUtils.isEmpty(paramString)) {
            Log.w(TAG, "Invalid entry");
            return null;
        }

        String[] arrayOfString = DELIMITER_PATTERN.split(paramString, -1);
        if (arrayOfString.length == 4) {
            try {
                String pkgName = trim(arrayOfString[0]);
                String simpleName = trim(arrayOfString[1]);
                int revision = parseInt(trim(arrayOfString[2]), 1);
                String url = trim(arrayOfString[3]);

                //check OS for installed version
                int installedVersion = AppMgmtUtils.APP_NOT_INSTALLED;
                if (AppMgmtUtils.isInstalled(context, pkgName)) {
                    installedVersion = AppMgmtUtils.getAppVersionCode(context,
                            pkgName);
                }

                ProductInformation pi = new ProductInformation(repo,
                        pkgName, simpleName, revision, url, installedVersion);
                if (pi != null && pi.isValid())
                    return pi;
            } catch (Exception localException) {
                Log.w(TAG, "Failed to 4 parse: " + paramString);
                return null;
            }
        } else if (arrayOfString.length >= 12 && arrayOfString.length < 20) {
            //allow for future additional columns to be ignored, but provide a sanity check

            try {
                Platform p = Platform.valueOf(trim(arrayOfString[0]));
                if (p != Platform.Android)
                    return null;

                ProductType pt = ProductType.valueOf(trim(arrayOfString[1]));
                int revision = parseInt(trim(arrayOfString[5]), 1);
                int osReq = parseInt(trim(arrayOfString[10]), 21);

                String pkgName = trim(arrayOfString[2]);

                //check OS for installed version
                int installedVersion = AppMgmtUtils.APP_NOT_INSTALLED;
                if (AppMgmtUtils.isInstalled(context, pkgName)) {
                    installedVersion = AppMgmtUtils.getAppVersionCode(context,
                            pkgName);
                }

                String takRequirement = trim(arrayOfString[11]);
                if (pt == ProductType.app) {
                    // no requirement for os on external apps
                    takRequirement = ATAKConstants.getPluginApi(false);
                }

                ProductInformation pi = new ProductInformation(repo,
                        p, pt, pkgName,
                        trim(arrayOfString[3]), trim(arrayOfString[4]),
                        revision,
                        trim(arrayOfString[6]), trim(arrayOfString[7]),
                        trim(arrayOfString[8]),
                        trim(arrayOfString[9]), osReq, takRequirement,
                        installedVersion);

                if (arrayOfString.length > 12) {
                    long size = Long.parseLong(trim(arrayOfString[12]));
                    pi.setFileSize(size);
                    Log.d(TAG, "Processed product size: " + size);
                }

                if (arrayOfString.length > 13) {
                    Log.d(TAG, "Ignoring some unsupported columns: "
                            + paramString);
                }

                if (pi.isValid())
                    return pi;
            } catch (Exception localException) {
                Log.w(TAG, "Failed to 12/13 parse: " + paramString,
                        localException);
                return null;
            }
        }

        Log.w(TAG, "Invalid column length: " + arrayOfString.length + ", "
                + paramString);
        return null;
    }

    public static ProductInformation create(final ProductRepository repo,
            final Context context, final File file) {

        Log.d(TAG, "Parsing: " + file.getAbsolutePath());

        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo info = pm.getPackageArchiveInfo(file.getAbsolutePath(),
                    PackageManager.GET_ACTIVITIES
                            | PackageManager.GET_META_DATA);
            if (info == null || info.applicationInfo == null) {
                Log.w(TAG,
                        "Failed to parse APK info: " + file.getAbsolutePath());
                return null;
            }

            //set source for app info
            info.applicationInfo.sourceDir = file.getAbsolutePath();
            info.applicationInfo.publicSourceDir = file.getAbsolutePath();

            String pkgName = info.applicationInfo.packageName;

            //find label, app name
            CharSequence cs = null;
            try {
                cs = pm.getApplicationLabel(info.applicationInfo);
                //Log.d(TAG, "info.getApplicationLabel: " + cs);
            } catch (Resources.NotFoundException nfe) {
                Log.w(TAG, "Failed to parse APK applicationInfo: "
                        + file.getAbsolutePath(), nfe);
            }

            if (cs == null || cs.length() < 1
                    || FileSystemUtils.isEquals(pkgName, cs.toString())) {
                Log.d(TAG, "Failed to parse APK applicationInfo: "
                        + file.getAbsolutePath());
                cs = info.applicationInfo.nonLocalizedLabel;
                //Log.d(TAG, "info.applicationInfo.nonLocalizedLabel: " + info.applicationInfo.nonLocalizedLabel);
            }
            if (cs == null || cs.length() < 1
                    || FileSystemUtils.isEquals(pkgName, cs.toString())) {
                Log.d(TAG, "Failed to parse APK nonLocalizedLabel: "
                        + file.getAbsolutePath());
                try {
                    cs = info.applicationInfo.loadLabel(pm);
                    //Log.d(TAG, "info.loadLabel: " + cs);
                } catch (Resources.NotFoundException nfe) {
                    Log.w(TAG, "Failed to parse APK loadLabel: "
                            + file.getAbsolutePath(), nfe);
                    cs = null;
                }
            }
            if (cs == null || cs.length() < 1
                    || FileSystemUtils.isEquals(pkgName, cs.toString())) {
                Log.w(TAG, "Failed to parse APK loadLabel: "
                        + file.getAbsolutePath());
                //Default to use package name
                cs = pkgName;
            }
            String simpleName = cs.toString();
            Log.d(TAG, "wrap simpleName: " + simpleName);

            //check OS for installed version
            int installedVersion = AppMgmtUtils.APP_NOT_INSTALLED;
            if (AppMgmtUtils.isInstalled(context, pkgName)) {
                installedVersion = AppMgmtUtils.getAppVersionCode(context,
                        pkgName);
            }

            String iconUri = FileSystemUtils
                    .stripExtension(file.getAbsolutePath()) + ".png";
            File f = new File(iconUri);
            if (!FileSystemUtils.canWrite(f)) {
                Log.e(TAG, "cannot write: " + iconUri);
                String name = f.getName();
                File cacheDir = new File(context.getCacheDir(), "apk-icons");
                if (!IOProviderFactory.exists(cacheDir)) {
                    if (!IOProviderFactory.mkdir(cacheDir))
                        Log.e(TAG,
                                "could not make the app-icon cache directory");
                }
                iconUri = cacheDir.getAbsolutePath() + "/" + f.getName();
            }

            if (!FileSystemUtils.isFile(iconUri)) {
                Log.d(TAG, "APK icon does not exist: " + iconUri);

                //attempt to extract it
                Drawable icon = info.applicationInfo.loadIcon(pm);
                if (icon != null) {
                    Bitmap bm = ATAKUtilities.getBitmap(icon);
                    if (bm != null) {
                        //TODO create icon in apks/custom to give better chance of having write perms?
                        try {
                            GLCapture.compress(bm, 100,
                                    Bitmap.CompressFormat.PNG,
                                    new File(iconUri), true);
                        } catch (Exception e) {
                            Log.d(TAG,
                                    "failed to write to to the external file: "
                                            + iconUri);
                        }
                    } else {
                        Log.w(TAG, "Could not load bitmap");
                    }
                } else {
                    Log.w(TAG, "Could not load drawable");
                }
            }

            //add prefix indicating absolute path to icon (not relative to APK file)
            iconUri = FILE_PREFIX + iconUri;

            int osReq = 1;
            String desc = "";
            String takReq = "";

            ProductType productType = ProductType.app;
            if (info != null && info.applicationInfo.metaData != null) {
                Object value = info.applicationInfo.metaData.get("plugin-api");
                if (!AtakPluginRegistry.isAtak(info)
                        && value instanceof String) {
                    takReq = (String) value;
                    Log.d(TAG, "plugin-api: " + takReq);
                    productType = ProductType.getSpecificPluginType(pkgName,
                            ProductType.plugin);
                } else {
                    Log.d(TAG, "Not a plugin: " + info.packageName);
                }

                value = info.applicationInfo.metaData.get("app_desc");
                if (value instanceof String) {
                    desc = (String) value;
                    Log.d(TAG, "app_desc: " + desc);
                }
            }

            String appUri = FILE_PREFIX + file.getAbsolutePath();
            ProductInformation p = new ProductInformation(repo,
                    Platform.Android, productType,
                    pkgName, simpleName, info.versionName, info.versionCode,
                    appUri,
                    iconUri, desc, HashingUtils.sha256sum(file), osReq, takReq,
                    installedVersion);
            p.setFileSize(IOProviderFactory.length(file));
            return p;
        } catch (Exception ex) {
            Log.w(TAG, "Failed to parse APK: " + file.getAbsolutePath(), ex);
        }

        return null;
    }

    private static String trim(String s) {
        if (FileSystemUtils.isEmpty(s))
            return s;

        return s.trim();
    }

    public boolean isSignatureValid() {
        Context c = MapView.getMapView().getContext();

        if (isInstalled() && isPlugin()) {
            return AtakPluginRegistry.verifySignature(c, packageName);
        } else {
            return true;
        }
    }

    public boolean isValid() {
        return (platform != null
                && productType != null
                && !FileSystemUtils.isEmpty(packageName)
                && !FileSystemUtils.isEmpty(simpleName)
                && !FileSystemUtils.isEmpty(version)
                && revision >= 0
                && !FileSystemUtils.isEmpty(appUri)
                && !FileSystemUtils.isEmpty(iconUri)
                && !FileSystemUtils.isEmpty(description)
                && !FileSystemUtils.isEmpty(hash)
                && isSignatureValid()
                && osRequirement >= 0
                && !FileSystemUtils.isEmpty(takRequirement))
                || (platform != null
                        && productType != null
                        && !FileSystemUtils.isEmpty(packageName)
                        && !FileSystemUtils.isEmpty(simpleName)
                        && revision >= 0);
    }

    public boolean search(String searchTerms) {
        if (FileSystemUtils.isEmpty(searchTerms) || !isValid())
            return false;

        return search(packageName, searchTerms)
                || search(simpleName, searchTerms)
                || search(appUri, searchTerms)
                || search(String.valueOf(version), searchTerms)
                || search(description, searchTerms)
                || search(platform.toString(), searchTerms)
                || search(productType.toString(), searchTerms);

    }

    public static boolean search(String value, String searchTerms) {
        return (!FileSystemUtils.isEmpty(value) && value.contains(searchTerms));
    }

    public ProductRepository getParent() {
        return parent;
    }

    public void setParent(ProductRepository parent) {
        this.parent = parent;
    }

    public Platform getPlatform() {
        return platform;
    }

    public ProductType getProductType() {
        return productType;
    }

    public boolean isPlugin() {
        return productType != null &&
                (productType == ProductType.plugin);
    }

    public String getPackageName() {
        return packageName;
    }

    public String getSimpleName() {
        return simpleName;
    }

    public String getVersion() {
        return version;
    }

    public int getRevision() {
        return revision;
    }

    public String getAppUri() {
        return appUri;
    }

    public String getIconUri() {
        return iconUri;
    }

    public Drawable getIcon() {
        Bitmap bm = null;
        String iconPath = getIconPath(this);
        if (FileSystemUtils.isFile(iconPath)) {
            try (FileInputStream stream = IOProviderFactory
                    .getInputStream(new File(iconPath))) {
                bm = BitmapFactory.decodeStream(stream);
            } catch (IOException ignored) {
            }
        }

        if (bm != null) {
            Log.d(TAG, "getIcon: " + iconPath);
            return new BitmapDrawable(MapView.getMapView().getContext()
                    .getResources(), bm);
        }

        Log.d(TAG, "Failed to get icon: " + iconPath);
        return null;
    }

    private static String getIconPath(final ProductInformation product) {
        if (!FileSystemUtils.isEmpty(product.getIconUri())) {
            if (product.getIconUri().startsWith(FILE_PREFIX)) {
                Log.d(TAG,
                        "Already absolute icon file link: "
                                + product.getIconUri());
                //strip out the "file:" prefix
                return product.getIconUri().substring(FILE_PREFIX.length());
            } else {
                Log.d(TAG,
                        "Processing relative icon link: "
                                + product.getIconUri()
                                + ", with base URI: "
                                + product.getParent().getIndexCacheFilePath());

                //relative URL, strip off the /product.inf and build out path to APK
                String baseUrl = product.getParent().getIndexCacheFilePath();
                int index = baseUrl.lastIndexOf("/");
                if (index < 0) {
                    Log.d(TAG, "Unable to parse base URL: " + baseUrl);
                    return null;
                }

                baseUrl = baseUrl.substring(0, index + 1);
                String relPath = product.getIconUri();
                if (relPath.startsWith("/"))
                    relPath = relPath.substring(1);
                return baseUrl + relPath;
            }
        }

        Log.d(TAG, "Product icon URI not set");
        return null;
    }

    public String getDescription() {
        return description;
    }

    public String getHash() {
        return hash;
    }

    public int getOsRequirement() {
        return osRequirement;
    }

    public String getTakRequirement() {
        return takRequirement;
    }

    public long getFileSize() {
        return fileSize;
    }

    public boolean hasFileSize() {
        return fileSize > 0;
    }

    public void setFileSize(long size) {
        this.fileSize = size;
    }

    public int getInstalledVersion() {
        return installedVersion;
    }

    public void setInstalledVersion(int installedVersion) {
        Log.d(TAG, "setInstalledVersion: " + installedVersion);
        this.installedVersion = installedVersion;
    }

    public boolean isInstalled() {
        //Log.d(TAG, "isInstalled: " + (installedVersion != AppMgmtUtils.APP_NOT_INSTALLED) + ", " + installedVersion);
        return installedVersion != AppMgmtUtils.APP_NOT_INSTALLED;
    }

    public String toString() {
        return this.packageName + "," + this.simpleName + ","
                + this.version + "," + this.appUri;
    }

    public String toFullString() {
        return this.platform.toString() + "," + this.productType.toString()
                + ","
                + getString(this.packageName) + ","
                + getString(this.simpleName) + ","
                + getString(this.version) + "," + this.revision + ","
                + getString(this.appUri) + "," + getString(this.iconUri) + ","
                + getString(this.description) + "," + getString(this.hash) + ","
                + this.osRequirement + "," + getString(this.takRequirement)
                + ","
                + this.fileSize;
    }

    protected static String getString(String s) {
        if (s == null)
            return "";
        return s.replaceAll(",", " ");
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ProductInformation) {
            ProductInformation c = (ProductInformation) o;
            return this.equals(c);
        } else {
            return super.equals(o);
        }
    }

    /**
     * Note this only checks package name, not that the other details match
     * @param c the produce to check to make sure it is equal.
     * @return true if the package name is equals
     */
    public boolean equals(ProductInformation c) {
        return FileSystemUtils.isEquals(this.packageName, c.packageName);
    }

    @Override
    public int hashCode() {
        return packageName.hashCode();
    }

    /**
     * Check is OS API level is met, and ATAK Plugin API level is met
     * @return true of the OS level is met.
     */
    public boolean isCompatible() {
        return isTakCompatible();
    }

    /**
     * Get string version of why the product is incompatible, or empty string if its compatible
     * @return
     */
    public String getInCompatibilityReason() {
        String reason = getTakInCompatibilityReason();
        if (!FileSystemUtils.isEmpty(reason)) {
            return reason;
        }

        return getOsInCompatibilityReason();
    }

    private String getOsInCompatibilityReason() {
        if (Build.VERSION.SDK_INT < osRequirement) {
            Log.d(TAG, packageName + " !compatible os req: "
                    + Build.VERSION.SDK_INT + " < " + osRequirement);
            return "suggested Android build: " + osRequirement;
        }

        return "";
    }

    public boolean isOsCompatible() {
        return FileSystemUtils.isEmpty(getOsInCompatibilityReason());
    }

    private String getTakInCompatibilityReason() {
        //special case for ATAK
        if (FileSystemUtils.isEquals(packageName,
                ATAKConstants.getPackageName())) {
            Log.d(TAG, "ATAK is self compatible, available for upgrade");
            return "";
        }

        if (!isSignatureValid()) {
            return "The plugin is not signed correctly and will not be loaded.";
        }

        //if requirement not specified, then allow it for now
        if (FileSystemUtils.isEmpty(takRequirement)) {
            Log.d(TAG, packageName + " tak req not specified: "
                    + this.packageName);
            return "";
        }

        if (AtakPluginRegistry.isTakCompatible(this.packageName,
                this.takRequirement)) {
            return "";
        }

        return "requires TAK v"
                + AtakPluginRegistry.stripPluginApiVersion(this.takRequirement);
    }

    /**
     * Specifically check to see if the API is considered compatible.
     * @return true if it is, otherwise false.
     */
    public boolean isApiCompatible() {
        if (FileSystemUtils.isEquals(packageName,
                ATAKConstants.getPackageName()))
            return true;

        if (FileSystemUtils.isEmpty(takRequirement))
            return true;

        return AtakPluginRegistry.isTakCompatible(this.packageName,
                this.takRequirement);
    }

    /**
     * Returns true if the Product is considered TAK compatible.
     * @return true if the product is compatible or false if there is a reason why it is not.
     */
    public boolean isTakCompatible() {
        return FileSystemUtils.isEmpty(getTakInCompatibilityReason());
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (Exception ignored) {
            return def;
        }
    }
}
