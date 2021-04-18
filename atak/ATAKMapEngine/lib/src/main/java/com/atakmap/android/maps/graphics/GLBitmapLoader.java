
package com.atakmap.android.maps.graphics;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.EnumMap;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadFactory;

import com.atakmap.util.zip.IoUtils;
import com.atakmap.util.zip.ZipEntry;
import com.atakmap.util.zip.ZipFile;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.DatabaseInformation;
import com.atakmap.coremap.io.IOProvider;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.database.CursorIface;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.QueryIface;
import com.atakmap.database.StatementIface;
import com.atakmap.map.RenderContext;
import com.atakmap.net.AsynchronousInetAddressResolver;
import com.atakmap.util.ReferenceCount;
import com.atakmap.map.opengl.GLMapSurface;

import android.util.Base64;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import com.atakmap.coremap.log.Log;

/**
 *  Due to an issue with Android Zip on ICS and potentially other platforms
 *         ZipFile does not like archives with more than 32K entries.
 *         http://code.google.com/p/android/issues/detail?id=23207
 *         http://code.google.com/p/android/issues/detail?id=32209 fine. Investigated usage of
 *         Apache Compress 1.5, and although it was easy to integrate (10x slower), and modify to
 *         specify a hashsize large enough to make sense for that many files, it was still 4x slower
 *         than the Android internal.
 */
public class GLBitmapLoader {

        public enum QueueType { ICON("Icon"),
                                  LOCAL("Local"), 
                                  LOCAL_COMPRESSED("Local Compressed"), 
                                  REMOTE("Remote"), 
                                  GENERIC("Generic"); 
        private String name;

        QueueType(final String name) {
            this.name = name;
        }
        public String toString() {
            return name;
        }

   }

    private static class LoaderSpec implements Comparable<LoaderSpec> {
        private static int instances = 0;

        public final LoaderSpi loader;
        public final QueueType queue;
        private final int insert;

        public LoaderSpec(LoaderSpi loader, QueueType queue) {
            this.loader = loader;
            this.queue = queue;

            synchronized (LoaderSpec.class) {
                this.insert = instances++;
            }
        }

        @Override
        public int compareTo(LoaderSpec other) {
            int retval = other.loader.getPriority() - this.loader.getPriority();
            if (retval == 0) {
                retval = System.identityHashCode(this.loader) - System.identityHashCode(other.loader);
                if ((retval == 0) && (this.loader != other.loader))
                    retval = this.insert - other.insert;
            }
            return retval;
        }

       @Override
       public boolean equals(Object o) {
           if (this == o) return true;
           if (o == null || getClass() != o.getClass()) return false;

           LoaderSpec that = (LoaderSpec) o;

           if (insert != that.insert) return false;
           if (!Objects.equals(loader, that.loader)) return false;
           return queue == that.queue;
       }

       @Override
       public int hashCode() {
           int result = loader != null ? loader.hashCode() : 0;
           result = 31 * result + (queue != null ? queue.hashCode() : 0);
           result = 31 * result + insert;
           return result;
       }
    }

    public GLBitmapLoader(RenderContext surface, int threadCount,
                          final int threadPriority) {
        _renderContext = surface;

        for (QueueType type : QueueType.values()) { 
           final String name = "GLBitmapLoader-"+type;
           _executor.put(type, Executors.newFixedThreadPool(threadCount,
                new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, name);
                        t.setPriority(threadPriority);
                        return t;
                    }
                }));
         }

        // XXX -
        Context ctx = null;
        if(_renderContext instanceof GLMapSurface)
            ctx = ((GLMapSurface) _renderContext).getContext();
        else
            Log.w(TAG, "Failed to obtain Context; asset and resource decoding not enabled");

        this.loaders = new HashMap<String, LoaderSpec>();
        this.registerLoader("file", FileBitmapLoaderSpi.INSTANCE, QueueType.LOCAL);
        this.registerLoader("sqlite", SqliteBitmapLoaderSpi.INSTANCE, QueueType.LOCAL);
        if(ctx != null)
            this.registerLoader("asset", AssetBitmapLoaderSpi.getInstance(ctx), QueueType.ICON);
        if(ctx != null)
            this.registerLoader("root", AssetBitmapLoaderSpi.getInstance(ctx), QueueType.ICON);
        this.registerLoader("base64", Base64BitmapLoaderSpi.INSTANCE, QueueType.ICON);
        if(ctx != null)
            this.registerLoader("resource", ResourceBitmapLoaderSpi.getInstance(ctx), QueueType.ICON);
        if(ctx != null)
            this.registerLoader("android.resource", ResourceBitmapLoaderSpi.getInstance(ctx), QueueType.ICON);
        this.registerLoader("arc", ZipBitmapLoaderSpi.INSTANCE, QueueType.LOCAL_COMPRESSED);
        this.registerLoader("zip", ZipBitmapLoaderSpi.INSTANCE, QueueType.LOCAL_COMPRESSED);
        this.registerLoader("http", new UrlBitmapLoaderSpi(), QueueType.REMOTE);
        this.registerLoader("https", new UrlBitmapLoaderSpi(), QueueType.REMOTE);
    }

    /**
     * Register a loader for a specific scheme and what type of queue they will be put in during
     * execution.
     * @param scheme  the uri scheme
     * @param loader the loader to call
     * @param queue the queue to execute the loader on.  @see QueueType.
     */
    public void registerLoader(String scheme, LoaderSpi loader, QueueType queue) {
        synchronized(this.loaders) {
            this.loaders.put(scheme, new LoaderSpec(loader, queue));
        }
    }

    /**
     * Unregister a loader for all schemes.
     * @param loader the loader to call
     */
    public void unregisterLoader(LoaderSpi loader) {
        synchronized(this.loaders) {
            Iterator<Map.Entry<String, LoaderSpec>> iter = this.loaders.entrySet().iterator();
            Map.Entry<String, LoaderSpec> entry;
            while(iter.hasNext()) {
                entry = iter.next();
                if(entry.getValue().loader == loader)
                    iter.remove();
            }
        }
    }

    /**
     * Queue up the loading of data for constructing a Bitmap.
     * @param type is the queue type to be used for loading the bitmap.   No programatic
     * restrictions are placed on the queue type that is chosen, however poor selections will
     * potentially result in poor performance.   Loading all of the queues with remotely loaded
     * objects for example could cause a backlog due to network I/O.
     */
    public void loadBitmap(final FutureTask<Bitmap> r, final QueueType type) {
        loadAsync(r, type);
    }

    public void loadAsync(final FutureTask<?> r, final QueueType type) {
        Runnable event = r;
        if(!_renderContext.isContinuousRenderEnabled())
            event = new Runnable() {
                @Override
                public void run() {
                    try {
                        r.run();
                    } finally {
                        _renderContext.requestRefresh();
                    }
                }
            };
        _executor.get(type).execute(event);
    }

    public void shutdown() {
        if (_executor != null) {
            for (QueueType t : QueueType.values()) {
                _executor.get(t).shutdown();
                if (!_executor.get(t).isTerminated())
                    Log.e(TAG, "failed to shutdown future tasks for the bitmap loader: " + t);
            }
        }

        for (ReferenceCount<ZipFile> value : _zipCache.values()) {
            try {
                value.value.close();
            } catch (Exception ignored) {
            }
        }
        _zipCache.clear();

        for (ReferenceCount<DatabaseIface> value : _dbCache.values()) {
            try {
                value.value.close();
            } catch (Exception ignored) {
            }
        }
        _dbCache.clear();

        resetUrlIconCacheDatabase();
    }

    /**
     * If the `urlIconCacheDatabase` is initialized, close and set to `null`.
     * This will force re-initialization on the next attempt to read from the
     * cache.
     */
    private synchronized void resetUrlIconCacheDatabase() {
        if (this.urlIconCacheDatabase != null) {
            if (this.insertUrlIconStatement != null) {
                this.insertUrlIconStatement.close();
                this.insertUrlIconStatement = null;
            }
            if (this.queryUrlIconBitmapStatement != null) {
                this.queryUrlIconBitmapStatement.close();
                this.queryUrlIconBitmapStatement = null;
            }
            this.urlIconCacheDatabase.close();
            this.urlIconCacheDatabase = null;
        }
    }

    public FutureTask<Bitmap> loadBitmap(final String uri,
                                         final BitmapFactory.Options opts) {

        FutureTask<Bitmap> r = null;
        Uri u = Uri.parse(uri);
        // Check if the scheme is null. This can happen if the uri is relative
        // only.
        if (u == null)
            return null;

        final String scheme = u.getScheme();
        final LoaderSpec s = this.loaders.get(scheme);
        if(s != null) {
            r = new FutureTask<>(new Callable<Bitmap>() {
                @Override
                public Bitmap call() {
                    try {
                        return s.loader.loadBitmap(uri, opts);
                    } catch (Exception e) {
                        Log.e(TAG, "error: " + e, e);
                        return null;
                    }
                }
            });
            this.loadBitmap(r, s.queue);
        }

        if (r == null) {
            Log.d(TAG, "Failed to select loader for: " + uri);
        }

        return r;
    }

    /**
     * A valid ZipFile has a tileset.xml file and is non-null. XXX: This is likely where we might
     * have to trigger a removal of the references to this zip file. Because the entry exists, it
     * probably was valid at one point.
     */
    static private boolean validZip(ZipFile zf) {
        if (zf != null) {
            ZipEntry zEntry = zf.getEntry("tileset.xml");
            if (zEntry == null) {
                try {
                    zf.close();
                } catch (Exception ignored) {
                }
                return false;
            } else {
                return true;
            }

        } else
            return false;
    }

    /**
     * Mount a zip archive for use, it is unmounted. If it is already mounted, the mount reference
     * count will be increased by one. The mounted zip file is made available by a call to
     * getMountedArchive and guaranteed to remain open until the reference count goes to zero.
     *
     * @param arcPath the archive to mount in zip format.
     */
    static public void mountArchive(final String arcPath) {
        boolean addRefresher = false;
        synchronized (_zipCache) {
            ReferenceCount<ZipFile> zip = _zipCache.get(arcPath);
            if (zip == null || !validZip(zip.value)) {
                try {
                    zip = new ReferenceCount<>(new ZipFile(arcPath));
                    Log.d(TAG, "archive: " + arcPath + " add to cache.");
                    _zipCache.put(arcPath, zip);
                    addRefresher = (_zipCache.size() == 1);
                } catch (IOException e) {
                    Log.e(TAG, "error: ", e);
                }
            } else {
                zip.reference();
            }
        }
    }

    /**
     * Unmount a zip archive. If the reference is zero, the zip archive is closed and no longer made
     * available through a call to getMountedArchive.
     *
     * @param arcPath the archive to mount in zip format.
     */
    static public void unmountArchive(final String arcPath) {
        boolean removeRefresher = false;
        synchronized (_zipCache) {
            ReferenceCount<ZipFile> zip = _zipCache.get(arcPath);
            try {
                if (zip != null) {
                    zip.dereference();
                    if (!zip.isReferenced()) {
                        Log.d(TAG, "archive: " + arcPath + " remove from cache.");
                        _zipCache.remove(arcPath);
                        zip.value.close();
                        removeRefresher = _zipCache.isEmpty();
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "error: ", e);
            }
        }

    }

    /**
     * Provides the requested archive if it has been sucessfully mounted otherwise it will return
     * null. The callee should not attempt to close the mounted archive as it will make it unusable
     * by future users. XXX: You have been warned.
     *
     * @param path the archive to mount in zip format.
     *
     * @deprecated Will be made private, without replacement
     */
    @Deprecated
    @DeprecatedApi(since = "4.1.1", forRemoval = true, removeAt = "4.4")
    static public ZipFile getMountedArchive(String path) {
        synchronized (_zipCache) {
            ReferenceCount<ZipFile> zipFile = _zipCache.get(path);
            if (zipFile != null)
                return zipFile.value;
            else
                return null;
        }
    }

    /**
     * Mount a database in readonly mode for use, it is unmounted. If it is already mounted, the
     * mount reference count will be increased by one. The mounted database is made available by a
     * call to getMountedDatabase and guaranteed to remain open until the reference count goes
     * to zero.
     *
     * @param arcPath the archive to mount in zip format.
     */
    static public void mountDatabase(final String arcPath) {
        synchronized (_dbCache) {
            ReferenceCount<DatabaseIface> db = _dbCache.get(arcPath);
            if (db != null) {
                db.reference();
                return;
            }
            final File f = new File(arcPath);
            if (!IOProviderFactory.exists(f)) {
                Log.w(TAG, "SQLite Database " + arcPath + " could not be mounted.");
                return;
            }
            db = new ReferenceCount<>(
                    IOProviderFactory.createDatabase(
                        new DatabaseInformation(
                            Uri.fromFile(f),
                            DatabaseInformation.OPTION_READONLY)));
            Log.d(TAG, "archive: " + arcPath + " add to cache.");
            _dbCache.put(arcPath, db);

            // XXX - need to invoke enableWriteAheadLogging for concurrent
            // access???
        }
    }

    /**
     * Unmount a database. If the reference is zero, the database is closed and no longer made
     * available through a call to getMountedDatabase.
     *
     * @param arcPath the database to mount.
     */
    static public void unmountDatabase(String arcPath) {
        synchronized (_dbCache) {
            ReferenceCount<DatabaseIface> db = _dbCache.get(arcPath);
            if (db == null)
                return;

            db.dereference();
            if (!db.isReferenced()) {
                Log.d(TAG, "archive: " + arcPath + " remove from cache.");
                _dbCache.remove(arcPath);
                db.value.close();
            }
        }
    }


    /**
     * @deprecated  always returns null, will be removed without replacement
     */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
    static public android.database.sqlite.SQLiteDatabase getMountedDatabase(String path) {
        return null;
    }

    /**
     * Provides the requested database if it has been sucessfully mounted otherwise it will return
     * null. The callee should not attempt to close the mounted database as it will make it unusable
     * by future users. XXX: You have been warned.
     *
     * @param path the archive to mount in zip format.
     * @deprecated  always returns null, will be removed without replacement
     */
    @Deprecated
    @DeprecatedApi(since = "4.1.1", forRemoval = true, removeAt = "4.4")
    static public DatabaseIface getMountedDatabase2(String path) {
        synchronized (_dbCache) {
            ReferenceCount<DatabaseIface> db = _dbCache.get(path);
            if (db != null)
                return db.value;
            else
                return null;
        }
    }

    public FutureTask<Bitmap> loadBitmap(String uriStr, Bitmap.Config config) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredConfig = config;

        try {
            return loadBitmap(uriStr, opts);
        } catch (Exception ex) {
            Log.e(TAG, "error: ", ex);
            return null;
        }
    }

    public static interface LoaderSpi {
        public Bitmap loadBitmap(String uri, BitmapFactory.Options opts) throws Exception;
        public int getPriority();
    }

    public static abstract class AbstractInputStreamLoaderSpi implements LoaderSpi {
        private final int priority;

        protected AbstractInputStreamLoaderSpi(int priority) {
            this.priority = priority;
        }

        @Override
        public Bitmap loadBitmap(String uri, BitmapFactory.Options opts) throws Exception{
            InputStream stream = null;
            try {
                stream = this.openStream(uri);
                return this.loadBitmapImpl(uri, stream, opts);
            } finally {
                if(stream != null)
                    stream.close();
            }
        }

        @Override
        public final int getPriority() {
            return this.priority;
        }

        protected abstract InputStream openStream(String uri) throws Exception;

        protected Bitmap loadBitmapImpl(String uri, InputStream stream, BitmapFactory.Options opts) throws Exception {
            return BitmapFactory.decodeStream(stream, null, opts);
        }
    }

    private final static class AssetBitmapLoaderSpi extends AbstractInputStreamLoaderSpi {

        private static Map<Context, LoaderSpi> instances = new IdentityHashMap<Context, LoaderSpi>();

        private final Context context;

        private AssetBitmapLoaderSpi(Context context) {
            super(-1);

            this.context = context;
        }

        @Override
        protected InputStream openStream(String uri) throws Exception {
            if(this.context == null)
                throw new RuntimeException("No Context is associated.");

            final Uri u = Uri.parse(uri);
            final String scheme = u.getScheme();
            String assetPath = uri.substring((scheme != null) ? scheme.length()+2 : 0);
            if (assetPath.startsWith("/")) {
                assetPath = assetPath.substring(1);
            }

            return this.context.getAssets().open(assetPath);
        }

        public synchronized static LoaderSpi getInstance(Context context) {
            LoaderSpi retval = instances.get(context);
            if(retval == null)
                instances.put(context, retval=new AssetBitmapLoaderSpi(context));
            return retval;
        }
    }

    private final static class Base64BitmapLoaderSpi extends AbstractInputStreamLoaderSpi {

        public final static LoaderSpi INSTANCE = new Base64BitmapLoaderSpi();

        private Base64BitmapLoaderSpi() {
            super(-1);
        }

        @Override
        protected InputStream openStream(String uri) throws Exception {
            final Uri u = Uri.parse(uri);
            final String scheme = u.getScheme();
            String image = uri.substring((scheme != null) ? scheme.length()+2 : 0);
            if (image.startsWith("/")) {
                image = image.substring(1);
            }
            byte[] buf = Base64.decode(image.getBytes(FileSystemUtils.UTF8_CHARSET), Base64.URL_SAFE | Base64.NO_WRAP);
            return new ByteArrayInputStream(buf);
        }
    }

    private final static class ResourceBitmapLoaderSpi extends AbstractInputStreamLoaderSpi {

        private static Map<Context, LoaderSpi> instances = new IdentityHashMap<Context, LoaderSpi>();

        private final Context context;

        private ResourceBitmapLoaderSpi(Context context) {
            super(-1);

            this.context = context;
        }

        @Override
        protected InputStream openStream(String uri) throws Exception {
            if(this.context == null)
                throw new RuntimeException("No Context is associated.");

            Uri u = Uri.parse(uri);
            // Check if the scheme is null. This can happen if the uri is relative
            // only.
            if (u == null)
                throw new IllegalArgumentException();

            final String scheme = u.getScheme();

            int resourceId;
            String packageName = null;
            if (scheme.equals("resource")) {
                resourceId = Integer.parseInt(uri.substring(11));
            } else if (scheme.equals("android.resource")) {
                String[] parts = uri.substring(19).split("/");
                packageName = parts[0];
                resourceId = Integer.parseInt(parts[1]);
            } else {
                throw new IllegalArgumentException();
            }

            if (packageName != null) {
                return this.context.getContentResolver()
                        .openInputStream(Uri.parse("android.resource://" + packageName
                                + "/" + resourceId));
            } else {
                return this.context.getResources()
                        .openRawResource(resourceId);
            }
        }

        public synchronized static LoaderSpi getInstance(Context context) {
            LoaderSpi retval = instances.get(context);
            if(retval == null)
                instances.put(context, retval=new ResourceBitmapLoaderSpi(context));
            return retval;
        }
    }

    private static class ZipBitmapLoaderSpi extends AbstractInputStreamLoaderSpi {

        public final static LoaderSpi INSTANCE = new ZipBitmapLoaderSpi();

        private ZipBitmapLoaderSpi() {
            super(-1);
        }

        @Override
        protected Bitmap loadBitmapImpl(String uri, InputStream stream, BitmapFactory.Options opts) throws Exception {
            final Bitmap retval = super.loadBitmapImpl(uri, stream, opts);
            if(retval != null)
                retval.setHasAlpha(false);
            return retval;
        }

        @Override
        protected InputStream openStream(String uriStr) throws Exception {
            Uri uri = Uri.parse(uriStr);
            final String path = uri.getPath();
            int entryPathIdx = path.indexOf('!');

            final String arcPath = path.substring(0, entryPathIdx);
            final String entryPath = path.substring(entryPathIdx + 2);

            ZipFile zip = getMountedArchive(arcPath);
            if (zip == null) {
                mountArchive(arcPath);
                zip = getMountedArchive(arcPath);
                if (zip == null) {
                    Log.w(TAG, "Archive could not be mounted: " + arcPath);
                    return null;
                }
            }

            ZipEntry entry = zip.getEntry(entryPath);
            if (entry == null)
                return null;

            return zip.getInputStream(entry);
        }
    }

    private static class FileBitmapLoaderSpi implements LoaderSpi {

        public final static LoaderSpi INSTANCE = new FileBitmapLoaderSpi();

        private FileBitmapLoaderSpi() {}

        @Override
        public Bitmap loadBitmap(String uri, BitmapFactory.Options opts) throws Exception {
            final Uri u = Uri.parse(uri);
            final String scheme = u.getScheme();
            String filePath = uri.substring((scheme != null) ? scheme.length()+3 : 0);
            try(FileInputStream fis = IOProviderFactory.getInputStream(new File(filePath))) {
                return BitmapFactory.decodeStream(fis, null, opts);
            } catch(IOException e) {
                return null;
            }
        }

        @Override
        public int getPriority() {
            return -1;
        }
    }

    private static class SqliteBitmapLoaderSpi extends AbstractInputStreamLoaderSpi {
        public final static LoaderSpi INSTANCE = new SqliteBitmapLoaderSpi();

        private SqliteBitmapLoaderSpi() {
            super(-1);
        }

        @Override
        protected InputStream openStream(String uri) throws Exception {
            // XXX -
            Uri u = Uri.parse(uri);
            final String query;
            try {
                final String queryString = u.getQueryParameter("query");

                if (queryString == null)
                    throw new IllegalStateException("null query string");

                query = URLDecoder.decode(queryString, FileSystemUtils.UTF8_CHARSET.name());
            } catch (UnsupportedEncodingException e) {
                throw new IllegalStateException(e);
            }
            final String filePath = u.getPath();

            ReferenceCount<DatabaseIface> database;
            synchronized(_dbCache) {
                database = _dbCache.get(filePath);
            }
            if (database == null) {
                Log.w(TAG, "SQLite Database " + filePath + " is not mounted.");
                return null;
            }

            CursorIface result = null;
            try {
                result = database.value.query(query, null);
                if (result.moveToNext())
                    return new ByteArrayInputStream(result.getBlob(0));
                else
                    return null;
            } finally {
                if (result != null)
                    result.close();
            }
        }
    }

    /** @deprecated use {@link #setIconCacheDb(File, IconCacheSeed)} */
    @Deprecated
    @DeprecatedApi(since = "4.1.1", forRemoval = true, removeAt = "4.4")
    public static void setIconCacheDb(File file) {
        setIconCacheDb(file, null);
    }
    public synchronized static void setIconCacheDb(File file, IconCacheSeed seeder) {
        // no-op
        if(file == null && iconCacheFile == null)
            return;
        // reset temp file error flag
        if(file == null)
            iconCacheTempFileError = false;
        iconCacheFile = file;
        iconCacheSeed = seeder;
    }
    public synchronized static File getIconCacheDb() {
        if(iconCacheFile == null && !iconCacheTempFileError) {
            try {
                iconCacheFile = IOProviderFactory.createTempFile("tmp", ".tmp", null);
            } catch(IOException e) {
                Log.w(TAG, "Failed to create Icon Cache", e);
                iconCacheTempFileError = true;
            }
        }
        return iconCacheFile;
    }

    private class UrlBitmapLoaderSpi implements LoaderSpi {

        private void ensureCacheDb() {
            if (GLBitmapLoader.this.urlIconCacheDatabase == null) {
                final File iconCacheDbFile = getIconCacheDb();
                if(iconCacheDbFile != null) {
                    GLBitmapLoader.this.urlIconCacheDatabase = IOProviderFactory.createDatabase(
                            new DatabaseInformation(Uri.fromFile(iconCacheDbFile)));
                    if(GLBitmapLoader.this.urlIconCacheDatabase == null && IOProviderFactory.exists(iconCacheDbFile)) {
                        IOProviderFactory.delete(iconCacheDbFile, IOProvider.SECURE_DELETE);
                        GLBitmapLoader.this.urlIconCacheDatabase = IOProviderFactory.createDatabase(
                            new DatabaseInformation(Uri.fromFile(iconCacheDbFile)));
                    }
                    // rebuild the DB if necessary
                    if (GLBitmapLoader.this.urlIconCacheDatabase.getVersion() != ICON_CACHE_DB_VERSION) {
                        GLBitmapLoader.this.urlIconCacheDatabase
                                .execute("DROP TABLE IF EXISTS cache", null);
                        GLBitmapLoader.this.urlIconCacheDatabase
                                .execute("CREATE TABLE cache (url TEXT, bitmap BLOB)", null);
                        GLBitmapLoader.this.urlIconCacheDatabase.setVersion(ICON_CACHE_DB_VERSION);
                        // seed the DB if the seeder is specified
                        if(iconCacheSeed != null)
                            iconCacheSeed.seed(GLBitmapLoader.this.urlIconCacheDatabase);
                    }
                }
            }
        }

        @Override
        public int getPriority() {
            return -1;
        }

        @Override
        public Bitmap loadBitmap(String url, BitmapFactory.Options opts) throws Exception {
            final boolean cache;
            synchronized (GLBitmapLoader.this) {
                this.ensureCacheDb();

                cache = (GLBitmapLoader.this.urlIconCacheDatabase != null);
                if (GLBitmapLoader.this.urlIconCacheDatabase != null) {
                    if (queryUrlIconBitmapStatement == null)
                        queryUrlIconBitmapStatement = urlIconCacheDatabase
                                .compileQuery("SELECT bitmap FROM cache WHERE url = ?");

                    try {
                        queryUrlIconBitmapStatement.bind(1, url);
                        do {
                            if (!queryUrlIconBitmapStatement.moveToNext())
                                break; // not in cache, proceed to download
                            byte[] fd = queryUrlIconBitmapStatement.getBlob(0);
                            if (fd != null)
                                return BitmapFactory.decodeByteArray(fd, 0, fd.length, opts);
                        } while(false);
                    } finally {
                        queryUrlIconBitmapStatement.clearBindings();
                    }
                }
            }

            URLConnection connection;
            try {
                URL u = new URL(url);
                synchronized (dnsLookup) {
                    final String host = u.getHost();
                    if (unresolvableHosts.contains(host)) {
                        return null;
                    } else if (!resolvedHosts.contains(host)) {
                        AsynchronousInetAddressResolver resolver = dnsLookup.get(host);
                        long lookupTimeout = 1L;
                        if (resolver == null) {
                            resolver = new AsynchronousInetAddressResolver(host);
                            dnsLookup.put(host, resolver);
                            lookupTimeout = 500L;
                        }

                        try {
                            InetAddress addr = resolver.get(lookupTimeout);
                            if (addr != null) {
                                resolvedHosts.add(host);
                                dnsLookup.remove(host);
                            } else {
                                return null;
                            }
                        } catch (IOException e) {
                            Log.e(TAG, "I/O error resolving host " + host, e);
                            unresolvableHosts.add(host);
                            dnsLookup.remove(host);
                            return null;
                        }
                    }
                }

                connection = u.openConnection();
                connection.setRequestProperty("User-Agent", "TAK");

                connection.setConnectTimeout(500);
                connection.setReadTimeout(5000);
                InputStream is = connection.getInputStream();

                File cacheFile = null;
                if (cache) {
                    cacheFile = IOProviderFactory.createTempFile("icon", ".cache", null);

                    try(OutputStream fos = IOProviderFactory.getOutputStream(cacheFile)) {
                        FileSystemUtils.copyStream(is, fos);
                    } finally {
                        // if during construction of the FileOutputStream, there is an
                        // exception thrown, is will not be closed right away.
                        IoUtils.close(is);
                    }
                    is = IOProviderFactory.getInputStream(cacheFile);
                }

                final Bitmap urlBitmap = BitmapFactory.decodeStream(is, null, opts);

                // XXX - restrict bitmap caching to images less than 128x128 to
                // attempt to only cache icons. the loader should take
                // some kind of 'extras' that will indicate whether or not
                // caching is desired
                if (cache && urlBitmap != null && urlBitmap.getWidth() < 128
                        && urlBitmap.getHeight() < 128) {
                    synchronized (GLBitmapLoader.this) {
                        if (GLBitmapLoader.this.urlIconCacheDatabase != null) {
                            if (insertUrlIconStatement == null)
                                insertUrlIconStatement = urlIconCacheDatabase
                                        .compileStatement("INSERT INTO cache (url, bitmap) VALUES (?, ?)");

                            try {
                                insertUrlIconStatement.bind(1, url);
                                insertUrlIconStatement.bind(2, FileSystemUtils.read(cacheFile));

                                insertUrlIconStatement.execute();
                            } finally {
                                insertUrlIconStatement.clearBindings();
                            }
                        }
                    }
                }

                if (cacheFile != null)
                    FileSystemUtils.delete(cacheFile);

                return urlBitmap;
            } catch (Exception e) {
                Log.e(TAG, "Failed to load url: " + url, e);
                // failed to load the bitmap
                return null;
            }
        }
    }

    public interface IconCacheSeed {
        void seed(DatabaseIface db);
    }

    private static final String TAG = "GLBitmapLoader";
    private static final HashMap<String, ReferenceCount<ZipFile>> _zipCache = new HashMap<>();
    private static final HashMap<String, ReferenceCount<DatabaseIface>> _dbCache = new HashMap<>();
    private static final Map<String, AsynchronousInetAddressResolver> dnsLookup = new HashMap<>();
    private static final Set<String> resolvedHosts = new HashSet<>();
    private static final Set<String> unresolvableHosts = new HashSet<>();
    private static File iconCacheFile = null;
    private static IconCacheSeed iconCacheSeed = null;
    private static boolean iconCacheTempFileError = false;

    public final static int ICON_CACHE_DB_VERSION = 2;

    private Map<QueueType, ExecutorService> _executor = new EnumMap<QueueType, ExecutorService>(QueueType.class);
    private final Map<String, LoaderSpec> loaders;

    private RenderContext _renderContext;
    private DatabaseIface urlIconCacheDatabase;

    private StatementIface insertUrlIconStatement;
    private QueryIface queryUrlIconBitmapStatement;
}
