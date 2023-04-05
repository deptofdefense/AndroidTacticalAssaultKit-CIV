
package com.atakmap.android.update;

import android.content.Context;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.util.zip.IoUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Represent a repository of available apps and plugins
 *
 *
 */
public class ProductRepository {

    private static final String TAG = "ProductRepository";

    /**
     * A file path for product.inf cache of the repo
     */
    private final String _localIndexCache;

    private final String _repoType;

    private List<ProductInformation> _products;

    public ProductRepository(String indexCacheFile, String repoType,
            List<ProductInformation> products) {
        _localIndexCache = indexCacheFile;
        _repoType = repoType;
        _products = products;
    }

    public boolean isValid() {
        if (FileSystemUtils.isEmpty(_localIndexCache))
            return false;

        if (!FileSystemUtils.isEmpty(_products)) {
            for (ProductInformation product : _products) {
                if (!product.isValid()) {
                    Log.w(TAG, "Invalid product");
                    return false;
                }
            }
        }

        return true;
    }

    public boolean isRepoType(String repoType) {
        return FileSystemUtils.isEquals(_repoType, repoType);
    }

    public String getRepoType() {
        return _repoType;
    }

    public String getIndexCacheFilePath() {
        return _localIndexCache;
    }

    public boolean hasProducts() {
        return !FileSystemUtils.isEmpty(_products);
    }

    /**
     * Get all products
     * @return
     */
    public List<ProductInformation> getProducts() {
        List<ProductInformation> ret = new ArrayList<>();
        if (!FileSystemUtils.isEmpty(_products))
            ret.addAll(_products);
        return ret;
    }

    public ProductInformation getProduct(String pkg) {
        if (FileSystemUtils.isEmpty(_products)
                || FileSystemUtils.isEmpty(pkg)) {
            return null;
        }

        for (ProductInformation p : _products) {
            if (FileSystemUtils.isEquals(p.getPackageName(), pkg)) {
                return p;
            }
        }

        return null;
    }

    public boolean hasProduct(String pkg) {
        return getProduct(pkg) != null;
    }

    public void setProducts(List<ProductInformation> products) {
        this._products = products;
    }

    /**
     * Fills the provided map with new products provided by this repository that have not 
     * in the provided map.
     *
     * @param map a map of products keyed by the package name.   This will be filled with
     * unique products offered by this product repository.  No existing products will be 
     * overwritten.
     */
    final void getUniqueProducts(Map<String, ProductInformation> map) {
        for (ProductInformation p : _products) {
            if (map.containsKey(p.getPackageName())) {
                Log.d(TAG, "ignoring: " + p);
            } else {
                map.put(p.getPackageName(), p);
            }
        }

        Log.d(TAG, this + ", getUniqueProducts size: " + map.size());
    }

    /**
     * Get all plugin products
     * @return
     */
    public List<ProductInformation> getPlugins() {
        List<ProductInformation> ret = new ArrayList<>();
        if (!FileSystemUtils.isEmpty(_products)) {
            for (ProductInformation p : _products) {
                if (p.isPlugin()) {
                    ret.add(p);
                }
            }
        }
        return ret;
    }

    public int getSize() {
        if (!hasProducts())
            return 0;
        return _products.size();
    }

    /**
     * Get all products which are installed but not current
     * @return
     */
    public List<ProductInformation> getStale(Context context) {
        List<ProductInformation> ret = new ArrayList<>();
        if (!FileSystemUtils.isEmpty(_products)) {
            for (ProductInformation product : _products) {
                if (AppMgmtUtils.isInstalled(context,
                        product.getPackageName())) {
                    final int installedVersion = AppMgmtUtils
                            .getAppVersionCode(context,
                                    product.getPackageName());
                    if (installedVersion < product.getRevision()) {
                        Log.d(TAG,
                                "Product has been updated: "
                                        + product);
                        ret.add(product);
                    }
                }
            }
        }
        return ret;
    }

    /**
     * Check if product is installed but not current
     * @return
     */
    public boolean isStale(Context context, String pkg) {
        if (FileSystemUtils.isEmpty(_products)) {
            return false;
        }

        for (ProductInformation product : _products) {
            if (FileSystemUtils.isEquals(pkg, product.getPackageName())
                    && AppMgmtUtils.isInstalled(context,
                            product.getPackageName())) {
                final int installedVersion = AppMgmtUtils.getAppVersionCode(
                        context, product.getPackageName());
                if (installedVersion < product.getRevision()) {
                    Log.d(TAG,
                            "Product has been updated: " + product);
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public String toString() {
        return _localIndexCache
                + (hasProducts() ? (", size = " + _products.size()) : "");
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ProductRepository) {
            ProductRepository c = (ProductRepository) o;
            return this.equals(c);
        } else {
            return super.equals(o);
        }
    }

    /**
     * Note this only checks package name, not that the othr details match
     * @param c
     * @return
     */
    public boolean equals(ProductRepository c) {
        return FileSystemUtils.isEquals(this._localIndexCache,
                c._localIndexCache);
    }

    /**
     * See if this repo has the product, and update internal state
     *
     * @param context
     * @param pkg
     * @return true if repo state was updated
     */
    public boolean installed(Context context, String pkg) {
        ProductInformation product = getProduct(pkg);
        if (product == null)
            return false;

        int installedVersion = AppMgmtUtils.getAppVersionCode(context, pkg);
        if (installedVersion != AppMgmtUtils.APP_NOT_INSTALLED
                && installedVersion != product.installedVersion) {
            product.setInstalledVersion(installedVersion);
            return true;
        }

        return false;
    }

    /**
     * See if this repo has the product, and update internal state
     *
     * @param context
     * @param pkg
     * @return true if repo state was updated
     */
    public boolean uninstalled(Context context, String pkg) {
        ProductInformation product = getProduct(pkg);
        if (product == null)
            return true;

        int installedVersion = AppMgmtUtils.getAppVersionCode(context, pkg);
        if (installedVersion == AppMgmtUtils.APP_NOT_INSTALLED
                && installedVersion != product.installedVersion) {
            product.setInstalledVersion(installedVersion);
            return true;
        }

        return false;
    }

    /**
     * Remove the product
     * @param pkg
     * @return true if repo state was updated
     */
    public boolean removeProduct(String pkg) {
        if (FileSystemUtils.isEmpty(pkg) || !hasProducts())
            return false;

        //TODO synchronized?
        ProductInformation toRemove = null;
        for (ProductInformation product : _products) {
            if (FileSystemUtils.isEquals(pkg, product.getPackageName())) {
                toRemove = product;
                break;
            }
        }

        if (toRemove != null) {
            Log.d(TAG, "Removing: " + pkg + ", " + this);
            _products.remove(toRemove);
            return true;
        }

        return false;
    }

    /**
     * Add the product
     * @param product
     * @return true if repo state was updated
     */
    public boolean addProduct(ProductInformation product) {
        if (product == null || !product.isValid()
                || hasProduct(product.getPackageName()))
            return false;

        Log.d(TAG, "Adding: " + product + ", " + this);
        _products.add(product);
        return true;
    }

    /**
     * Parse the repo index from input stream
     *
     * @param repoIndexUrl
     * @param in
     * @return
     */
    public static ProductRepository parseRepo(final Context context,
            final String repoIndexUrl, final String repoType,
            final BufferedReader in) {
        List<ProductInformation> products = new ArrayList<>();
        ProductRepository repo = new ProductRepository(repoIndexUrl, repoType,
                products);

        try {
            String line;
            while ((line = in.readLine()) != null) {
                if (!line.startsWith("#")) {
                    ProductInformation pi = ProductInformation.create(repo,
                            context, line);
                    if (pi != null && pi.isValid()) {
                        Log.d(TAG, "Parsed: " + pi.toFullString());
                        products.add(pi);
                    } else {
                        Log.w(TAG, "Unable to parse: " + line);
                    }
                }
            }
        } catch (Exception ioe) {
            Log.d(TAG, "error occurred handling updates", ioe);
        } finally {
            IoUtils.close(in, TAG, "error closing the input stream");
        }

        if (FileSystemUtils.isEmpty(products)) {
            //repo may be empty e.g. atak/support/apks/custom not used by default
            Log.i(TAG, "Repo is empty: " + repoIndexUrl);
        }

        repo.setProducts(products);
        return repo;
    }

    /**
     * Parse the repo index from file
     *
     * @param context
     * @param in
     * @return
     */
    public static ProductRepository parseRepo(final Context context,
            final String repoType, final File in) {
        if (!FileSystemUtils.isFile(in)) {
            Log.w(TAG, "File does not exist: " + in.getAbsolutePath());
            return null;
        }

        try {
            // Closed in passed-to method
            BufferedReader reader = new BufferedReader(
                    IOProviderFactory.getFileReader(in));
            return parseRepo(context, in.getAbsolutePath(), repoType, reader);
        } catch (IOException ex) {
            Log.w(TAG, "Failed parse: " + in.getAbsolutePath(), ex);
        }

        return null;
    }

    /**
     * Save the repo to the specified file location
     * @param index
     * @return
     */
    public boolean save(File index) {
        if (!isValid()) {
            Log.w(TAG,
                    "Cannot save invalid repo to index: "
                            + index.getAbsolutePath());
            return false;
        }

        return save(index, _products);
    }

    public static boolean save(File index, List<ProductInformation> products) {
        Log.d(TAG, "Saving index cache: " + index.getAbsolutePath());

        StringBuilder sb = new StringBuilder();
        for (ProductInformation product : products) {
            sb.append(product.toFullString());
            sb.append("\n");
        }

        try {
            final File parent = index.getParentFile();
            if (!IOProviderFactory.exists(parent)
                    && !IOProviderFactory.mkdirs(parent)) {
                Log.w(TAG, "unable to create directory: " + parent);
            }
            FileSystemUtils.write(IOProviderFactory.getOutputStream(index),
                    sb.toString());
            return true;
        } catch (IOException e) {
            Log.w(TAG, "Failed to save to: " + index.getAbsolutePath(), e);
        }

        return false;
    }

    @Override
    public int hashCode() {
        int retval = (_localIndexCache == null) ? 0
                : _localIndexCache
                        .hashCode();
        retval = 31 * retval + ((_repoType == null) ? 0 : _repoType.hashCode());
        retval = 31 * retval + ((_products == null) ? 0 : _products.hashCode());
        return retval;
    }
}
