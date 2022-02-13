
package com.atakmap.android.data;

import com.atakmap.android.hierarchy.filters.FOVFilter;
import com.atakmap.android.maps.visibility.VisibilityCondition;
import com.atakmap.android.maps.visibility.VisibilityListener;
import com.atakmap.android.maps.visibility.VisibilityManager;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import com.atakmap.coremap.locale.LocaleUtil;

/**
 * Content resolver specifically for file URIs
 */
public class FileContentResolver implements URIContentResolver,
        URIQueryInterface, VisibilityListener {

    private static final String TAG = "FileContentResolver";

    // Valid file extensions for this resolver (empty to ignore)
    protected final Set<String> _validExts = new HashSet<>();

    // File path -> handler map for quick lookup
    protected final Map<String, FileContentHandler> _handlers = new HashMap<>();

    public FileContentResolver(Set<String> validExts) {
        if (validExts != null) {
            for (String ext : validExts)
                _validExts.add(ext.toLowerCase(LocaleUtil.getCurrent()));
        }
        VisibilityManager.getInstance().addListener(this);
    }

    public synchronized void dispose() {
        VisibilityManager.getInstance().removeListener(this);
        _handlers.clear();
    }

    @Override
    public FileContentHandler getHandler(String tool, String uri) {
        // Valid file checks
        if (uri == null || !uri.startsWith(URIScheme.FILE))
            return null;
        File f = URIHelper.getFile(uri);
        return getHandler(f);
    }

    /**
     * Get a content handler for a given file
     *
     * @param f File
     * @return File content handler
     */
    public FileContentHandler getHandler(File f) {
        if (f == null)
            return null;

        // Check for valid file extension
        if (!_validExts.isEmpty()) {
            String ext = f.getName();
            ext = ext.substring(ext.lastIndexOf(".") + 1);
            if (!_validExts.contains(ext.toLowerCase(LocaleUtil.getCurrent())))
                return null;
        }

        String path = f.getAbsolutePath();
        synchronized (this) {
            return _handlers.get(path);
        }
    }

    /**
     * Add a new handler to the cache and notify listeners
     *
     * @param handler File-based content handler
     * @param allowUpdate True to update existing handlers, false to ignore add
     * @return True if new handler added
     */
    public boolean addHandler(FileContentHandler handler, boolean allowUpdate) {
        if (handler == null)
            return false;

        // Add or update
        boolean updated;
        String path = handler.getFile().getAbsolutePath();
        synchronized (this) {
            updated = _handlers.containsKey(path);
            if (updated && !allowUpdate)
                return false;
            _handlers.put(path, handler);
        }

        // Notify listeners
        if (updated) {
            Log.d(TAG, handler.getContentType() + ": Updated handler for "
                    + handler.getTitle());
            URIContentManager.getInstance().notifyContentChanged(handler);
        } else {
            Log.d(TAG, handler.getContentType() + ": Added handler for "
                    + handler.getTitle());
            URIContentManager.getInstance().notifyContentImported(handler);
        }

        // Run through visibility conditions
        handler.onVisibilityConditions(VisibilityManager.getInstance()
                .getConditions());

        return true;
    }

    public void addHandler(FileContentHandler handler) {
        addHandler(handler, true);
    }

    /**
     * Remove a file-based content handler
     *
     * @param file File
     */
    public void removeHandler(File file) {
        if (file == null)
            return;
        FileContentHandler handler;
        synchronized (this) {
            handler = _handlers.remove(file.getAbsolutePath());
        }
        if (handler != null) {
            Log.d(TAG, handler.getContentType() + ": Removed handler for "
                    + handler.getTitle());
            URIContentManager.getInstance().notifyContentDeleted(handler);
        }
    }

    protected synchronized List<FileContentHandler> getHandlers() {
        return new ArrayList<>(_handlers.values());
    }

    @Override
    public List<URIContentHandler> query(URIQueryParameters params) {
        List<FileContentHandler> handlers = getHandlers();
        List<URIContentHandler> ret = new ArrayList<>();
        for (FileContentHandler h : handlers) {

            // Visibility filter
            if (params.visibleOnly && !h.isVisible())
                continue;

            // Name/title filter
            if (params.name != null && !h.getTitle().matches(params.name))
                continue;

            // Content type filter
            if (params.contentType != null && !h.getContentType().equals(
                    params.contentType))
                continue;

            // Spatial filter
            if (params.fov != null
                    && (!h.isActionSupported(FOVFilter.Filterable.class)
                            || !((FOVFilter.Filterable) h).accept(
                                    params.fov.getMapState())))
                continue;

            // All filters passed
            ret.add(h);
        }

        return ret;
    }

    @Override
    public void onVisibilityConditions(List<VisibilityCondition> conditions) {
        List<FileContentHandler> handlers = getHandlers();
        for (FileContentHandler h : handlers)
            h.onVisibilityConditions(conditions);
    }
}
