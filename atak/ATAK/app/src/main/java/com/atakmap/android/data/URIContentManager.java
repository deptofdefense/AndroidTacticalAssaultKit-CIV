
package com.atakmap.android.data;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.math.MathUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manager for defining content actions based on a URI
 * Similar to the Import Manager framework but with remote content support
 */
public class URIContentManager {

    private static final String TAG = "URIContentManager";
    private static URIContentManager instance;

    // Sort objects by URIContentPriority
    private static final Comparator<Object> SORT_PRIORITY = new Comparator<Object>() {
        @Override
        public int compare(Object o1, Object o2) {
            if (o1 instanceof URIContentPriority
                    && o2 instanceof URIContentPriority) {
                int p1 = MathUtils.clamp(
                        ((URIContentPriority) o1).getPriority(),
                        URIContentPriority.LOWEST, URIContentPriority.HIGHEST);
                int p2 = MathUtils.clamp(
                        ((URIContentPriority) o2).getPriority(),
                        URIContentPriority.LOWEST, URIContentPriority.HIGHEST);
                return Integer.compare(p2, p1);
            } else if (o1 instanceof URIContentPriority)
                return -1;
            else if (o2 instanceof URIContentPriority)
                return 1;
            return 0;
        }
    };

    private final List<URIContentResolver> _resolvers = new ArrayList<>();
    private final List<URIContentProvider> _providers = new ArrayList<>();
    private final List<URIContentSender> _senders = new ArrayList<>();
    private final Set<URIContentListener> _listeners = new HashSet<>();
    private final Map<String, URIContentHandler> _handlers = new HashMap<>();

    void dispose() {
        synchronized (_resolvers) {
            _resolvers.clear();
        }
    }

    /**
     * Static accessor method for the URI content manager
     * @return URI content manager instance
     */
    public synchronized static URIContentManager getInstance() {
        if (instance == null)
            instance = new URIContentManager();
        return instance;
    }

    /**
     * Register a content resolver
     * @param res URI content resolver
     */
    public void registerResolver(URIContentResolver res) {
        synchronized (_resolvers) {
            _resolvers.add(res);
            Collections.sort(_resolvers, SORT_PRIORITY);
        }
    }

    /**
     * Unregister a content resolver
     * @param res URI content resolver
     */
    public void unregisterResolver(URIContentResolver res) {
        synchronized (_resolvers) {
            _resolvers.remove(res);
        }
    }

    /**
     * Get all registered content resolvers
     * @return List of content resolvers
     */
    public List<URIContentResolver> getResolvers() {
        synchronized (_resolvers) {
            return new ArrayList<>(_resolvers);
        }
    }

    /**
     * Given a content URI and optional tool name, create or obtain a content handler
     * @param tool Tool name (may be null if N/A)
     * @param uri Content URI
     * @return URI content handler or null if not supported
     */
    public URIContentHandler getHandler(String tool, String uri) {
        for (URIContentResolver res : getResolvers()) {
            URIContentHandler h = res.getHandler(tool, uri);
            if (h != null)
                return h;
        }
        return null;
    }

    /**
     * Get or create a content handler for a given file
     * @param file File
     * @return Content handler or null if none supported
     */
    public URIContentHandler getHandler(File file) {
        return getHandler(file, null);
    }

    /**
     * Get the content handler for a file
     * @param file File
     * @param contentType Content type of the file
     * @return Content handler
     */
    public URIContentHandler getHandler(File file, String contentType) {
        List<URIContentHandler> handlers = getHandlers(file, contentType);
        return !FileSystemUtils.isEmpty(handlers) ? handlers.get(0) : null;
    }

    /**
     * Get all content handlers for a given file
     * @param file File
     * @return List of content handlers
     */
    public List<URIContentHandler> getHandlers(File file) {
        return getHandlers(file, null);
    }

    private List<URIContentHandler> getHandlers(File file, String contentType) {
        List<URIContentHandler> handlers = new ArrayList<>();
        if (file == null)
            return handlers;
        String uri = URIHelper.getURI(file);
        for (URIContentResolver res : getResolvers()) {
            URIContentHandler h = res.getHandler(null, uri);
            if (h != null
                    && (contentType == null || h instanceof FileContentHandler
                            && contentType.equals(
                                    ((FileContentHandler) h).getContentType())))
                handlers.add(h);
        }
        return handlers;
    }

    /**
     * Query all content handlers using specific parameters
     * @param params Query parameters
     * @return the list of URI content handles that match the URI query parameters
     */
    public List<URIContentHandler> query(URIQueryParameters params) {
        List<URIContentHandler> handlers = new ArrayList<>();
        for (URIContentResolver res : getResolvers()) {
            if (res instanceof URIQueryInterface) {
                List<URIContentHandler> ret = ((URIQueryInterface) res)
                        .query(params);
                if (ret != null)
                    handlers.addAll(ret);
            }
        }
        return handlers;
    }

    /* LISTENERS */

    /**
     * Register a content listener
     * @param listener URI content listener
     */
    public void registerListener(URIContentListener listener) {
        synchronized (_listeners) {
            _listeners.add(listener);
        }
    }

    /**
     * Unregister a content listener
     * @param listener URI content listener
     */
    public void unregisterListener(URIContentListener listener) {
        synchronized (_listeners) {
            _listeners.remove(listener);
        }
    }

    /**
     * Notify manager and listeners that content has finished being imported
     * @param handler URI content handler
     */
    public void notifyContentImported(URIContentHandler handler) {
        synchronized (_handlers) {
            _handlers.put(handler.getURI(), handler);
        }
        for (URIContentListener l : getListeners())
            l.onContentImported(handler);
    }

    /**
     * Notify manager and listeners that content has finished being deleted
     * @param handler URI content handler
     */
    public void notifyContentDeleted(URIContentHandler handler) {
        synchronized (_handlers) {
            URIContentHandler h = _handlers.get(handler.getURI());
            if (h == handler)
                _handlers.remove(h.getURI());
        }
        for (URIContentListener l : getListeners())
            l.onContentDeleted(handler);
    }

    /**
     * Notify manager and listeners that content has been changed
     * @param handler URI content handler
     */
    public void notifyContentChanged(URIContentHandler handler) {
        synchronized (_handlers) {
            _handlers.put(handler.getURI(), handler);
        }
        for (URIContentListener l : getListeners())
            l.onContentChanged(handler);
    }

    /**
     * Get a list of all registered content handlers
     * @return List of handlers
     */
    public List<URIContentHandler> getRegisteredHandlers() {
        synchronized (_handlers) {
            return new ArrayList<>(_handlers.values());
        }
    }

    private List<URIContentListener> getListeners() {
        synchronized (_listeners) {
            return new ArrayList<>(_listeners);
        }
    }

    /* PROVIDERS */

    /**
     * Register a content provider
     * @param provider URI content provider
     */
    public void registerProvider(URIContentProvider provider) {
        synchronized (_providers) {
            _providers.add(provider);
            Collections.sort(_providers, SORT_PRIORITY);
        }
    }

    /**
     * Unregister a content provider
     * @param provider URI content provider
     */
    public void unregisterProvider(URIContentProvider provider) {
        synchronized (_providers) {
            _providers.remove(provider);
        }
    }

    /**
     * Get all content providers
     * @return List of content providers
     */
    public List<URIContentProvider> getProviders() {
        synchronized (_providers) {
            return new ArrayList<>(_providers);
        }
    }

    /**
     * Get all content providers which support a given tool
     * @param requestTool Request tool string
     * @return List of content providers
     */
    public List<URIContentProvider> getProviders(String requestTool) {
        List<URIContentProvider> providers = getProviders();
        for (int i = 0; i < providers.size(); i++) {
            URIContentProvider p = providers.get(i);
            if (!p.isSupported(requestTool))
                providers.remove(i--);
        }
        return providers;
    }

    /* SEND METHODS */

    /**
     * Register a content send method
     * @param sender URI content sender
     */
    public void registerSender(URIContentSender sender) {
        synchronized (_senders) {
            _senders.add(sender);
            Collections.sort(_senders, SORT_PRIORITY);
        }
    }

    /**
     * Unregister a content send method
     * @param sender URI content sender
     */
    public void unregisterSender(URIContentSender sender) {
        synchronized (_senders) {
            _senders.remove(sender);
        }
    }

    /**
     * Get all content send methods
     * @return List of senders
     */
    public List<URIContentSender> getSenders() {
        synchronized (_senders) {
            return new ArrayList<>(_senders);
        }
    }

    /**
     * Get all content send methods which support a given URI
     * @param contentURI Content URI
     * @return List of supported senders
     */
    public List<URIContentSender> getSenders(String contentURI) {
        List<URIContentSender> supported = new ArrayList<>();
        for (URIContentSender s : getSenders()) {
            if (s.isSupported(contentURI))
                supported.add(s);
        }
        return supported;
    }
}
