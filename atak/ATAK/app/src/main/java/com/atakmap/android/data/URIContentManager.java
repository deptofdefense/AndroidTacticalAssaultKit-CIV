
package com.atakmap.android.data;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Manager for defining content actions based on a URI
 * Similar to the Import Manager framework but with remote content support
 */
public class URIContentManager {

    private static final String TAG = "URIContentManager";
    private static URIContentManager instance;

    private final Set<URIContentResolver> _resolvers = new HashSet<>();
    private final Set<URIContentListener> _listeners = new HashSet<>();
    private final List<URIContentProvider> _providers = new ArrayList<>();
    private final List<URIContentSender> _senders = new ArrayList<>();

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

    public URIContentHandler getHandler(File file) {
        if (file == null)
            return null;
        return getHandler(null, URIHelper.getURI(file));
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
        for (URIContentListener l : getListeners())
            l.onContentImported(handler);
    }

    /**
     * Notify manager and listeners that content has finished being deleted
     * @param handler URI content handler
     */
    public void notifyContentDeleted(URIContentHandler handler) {
        for (URIContentListener l : getListeners())
            l.onContentDeleted(handler);
    }

    /**
     * Notify manager and listeners that content has been changed
     * @param handler URI content handler
     */
    public void notifyContentChanged(URIContentHandler handler) {
        for (URIContentListener l : getListeners())
            l.onContentChanged(handler);
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
