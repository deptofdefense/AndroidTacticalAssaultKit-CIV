package com.atakmap.io;

import android.net.Uri;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.DatabaseInformation;
import com.atakmap.coremap.io.IOProvider;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.atakmap.database.CursorIface;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.QueryIface;
import com.atakmap.database.StatementIface;
import com.atakmap.math.MathUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class CachingProtocolHandler implements ProtocolHandler {
    private final static String TAG = "CachingProtocolHandler";

    private final ProtocolHandler source;
    private final File cacheDir;
    private final DatabaseIface index;
    private final Set<String> pendingRequests = new HashSet<>();
    private long maxCache;
    private long cacheSize;

    public CachingProtocolHandler(ProtocolHandler source, File cacheDir, long maxCache) {
        if(source == null)
            throw new IllegalArgumentException();
        if(cacheDir == null)
            throw new IllegalArgumentException();

        this.source = source;
        this.cacheDir = cacheDir;
        if(!IOProviderFactory.exists(this.cacheDir))
            IOProviderFactory.mkdirs(this.cacheDir);
        this.maxCache = maxCache;

        final File dbfile = new File(cacheDir.getAbsolutePath(), "index.sqlite");
        if(!IOProviderFactory.exists(dbfile)) {
            this.index = IOProviderFactory.createDatabase(
                    new DatabaseInformation(Uri.fromFile(dbfile)));
            this.index.execute("CREATE TABLE IF NOT EXISTS " +
                            "cacheindex (uri TEXT, path TEXT, length INTEGER, cache_datetime INTEGER)",
                null);
            this.index.setVersion(1);
        } else {
            this.index = IOProviderFactory.createDatabase(
                    new DatabaseInformation(Uri.fromFile(dbfile)));
        }

        CursorIface result = null;
        try {
            result = this.index.query("SELECT SUM(length) FROM cacheindex", null);
            if(result.moveToNext())
                this.cacheSize = result.getLong(0);
            else
                this.cacheSize = 0;
        } finally {
            if(result != null)
                result.close();
        }
    }

    @Override
    public UriFactory.OpenResult handleURI(String uri) {
        synchronized(this) {
            final boolean waiting = pendingRequests.contains(uri);
            if(waiting)
                try {
                    this.wait();
                } catch(InterruptedException ignored) {}

            //  if present in cache, return value else return value from source
            QueryIface query = null;
            try {
                query = this.index.compileQuery("SELECT path, length FROM cacheindex WHERE uri = ? LIMIT 1");
                query.bind(1, uri);
                if (query.moveToNext()) {
                    try {
                        UriFactory.OpenResult result = new UriFactory.OpenResult();
                        result.inputStream = IOProviderFactory.getInputStream(new File(query.getString(0)));
                        result.contentLength = query.getLong(1);
                        return result;
                    } catch(IOException e) {
                        Log.w(TAG, "Failed to reconstruct content from cache file");
                    }
                } else if(waiting) {
                    // we were waiting, but there was no cache update
                    return null;
                }
            } finally {
                if (query != null)
                    query.close();
            }
            this.pendingRequests.add(uri);
        }
        // if not present, slurp into cache
        UriFactory.OpenResult result = this.source.handleURI(uri);
        File cached = null;
        if(result != null) {
            try {
                byte[] transfer = new byte[MathUtils.clamp((int)result.contentLength, 64*1024, 1024*1024)];
                cached = IOProviderFactory.createTempFile("cache", "", this.cacheDir);
                try(FileOutputStream fos = IOProviderFactory.getOutputStream(cached)) {
                    FileSystemUtils.copyStream(result.inputStream, true, fos, false, transfer);
                }
            } catch(IOException e) {
                Log.w(TAG, "Failed to create cache file");
            }
        }
        synchronized(this) {
            if(cached != null) {
                StatementIface stmt = null;
                try {
                    stmt = this.index.compileStatement("INSERT INTO cacheindex (uri, path, length, cache_datetime) VALUES(?, ?, ?, ?)");
                    stmt.bind(1, uri);
                    stmt.bind(2, cached.getAbsolutePath());
                    stmt.bind(3, IOProviderFactory.length(cached));
                    stmt.bind(4, System.currentTimeMillis());

                    stmt.execute();
                } finally {
                    if(stmt != null)
                        stmt.close();
                }

                try {
                    result.inputStream = IOProviderFactory.getInputStream(cached);
                    result.contentLength = IOProviderFactory.length(cached);
                } catch(IOException ignored) {}

                this.cacheSize += IOProviderFactory.length(cached);
                if(this.maxCache > 0L && this.cacheSize > this.maxCache+(this.maxCache/20)) {
                    long deleteToRowID = 0;
                    // evict to 10% under limit
                    final long target = this.maxCache-(this.maxCache/10);

                    CursorIface cursor = null;
                    try {
                        cursor = this.index.query("SELECT ROWID, path, length FROM cacheindex ORDER BY ROWID ASC", null);
                        while(cursor.moveToNext()) {
                            // delete the file
                            IOProviderFactory.delete(new File(cursor.getString(1)), IOProvider.SECURE_DELETE);
                            // update the cache size
                            this.cacheSize -= cursor.getLong(2);
                            // mark the row for delete
                            deleteToRowID = cursor.getLong(0);
                            // stop when shrunk
                            if(this.cacheSize <= target)
                                break;
                        }
                    } finally {
                        if(cursor != null)
                            cursor.close();
                    }

                    stmt = null;
                    try {
                        stmt = this.index.compileStatement("DELETE FROM cacheindex WHERE ROWID <= ?");
                        stmt.bind(1, deleteToRowID);
                        stmt.execute();
                    } finally {
                        if(stmt != null)
                            stmt.close();
                    }
                }
            }
            this.pendingRequests.remove(uri);
            this.notifyAll();
        }

        return result;
    }

    @Override
    public synchronized long getContentLength(String uri) {
        //  if present in cache, return value else return value from source
        QueryIface query = null;
        try {
            query = this.index.compileQuery("SELECT length FROM cacheindex WHERE uri = ? LIMIT 1");
            query.bind(1, uri);
            if(query.moveToNext())
                return query.getLong(0);
        } finally {
            if(query != null)
                query.close();
        }

        return this.source.getContentLength(uri);
    }

    @Override
    public Collection<String> getSupportedSchemes() {
        return this.source.getSupportedSchemes();
    }
}
