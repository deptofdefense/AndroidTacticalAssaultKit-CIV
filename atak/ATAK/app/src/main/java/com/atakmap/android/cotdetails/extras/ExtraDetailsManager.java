
package com.atakmap.android.cotdetails.extras;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages extra detail views within each details drop-down
 */
public class ExtraDetailsManager {

    private static ExtraDetailsManager _instance;

    public static ExtraDetailsManager getInstance() {
        return _instance != null ? _instance
                : (_instance = new ExtraDetailsManager());
    }

    private final Set<ExtraDetailsProvider> _providers = new HashSet<>();
    private final Set<ExtraDetailsListener> _listeners = new HashSet<>();

    /**
     * Register an extra details provider
     * @param provider Provider
     */
    public void addProvider(ExtraDetailsProvider provider) {
        synchronized (_providers) {
            if (!_providers.add(provider))
                return;
        }
        for (ExtraDetailsListener l : getListeners())
            l.onProviderAdded(provider);
    }

    /**
     * Notify listeners that this provider has been updated
     * @param provider Provider
     */
    public void updateProvider(ExtraDetailsProvider provider) {
        for (ExtraDetailsListener l : getListeners())
            l.onProviderChanged(provider);
    }

    /**
     * Unregister an extra details provider
     * @param provider Provider
     */
    public void removeProvider(ExtraDetailsProvider provider) {
        synchronized (_providers) {
            if (!_providers.remove(provider))
                return;
        }
        for (ExtraDetailsListener l : getListeners())
            l.onProviderRemoved(provider);
    }

    /**
     * Get all registered details provider
     * @return List of providers
     */
    public List<ExtraDetailsProvider> getProviders() {
        synchronized (_providers) {
            return new ArrayList<>(_providers);
        }
    }

    /**
     * Add provider listener
     * @param listener Provider listener
     */
    public void addListener(ExtraDetailsListener listener) {
        synchronized (_listeners) {
            _listeners.add(listener);
        }
    }

    /**
     * Remove provider listener
     * @param listener Provider listener
     */
    public void removeListener(ExtraDetailsListener listener) {
        synchronized (_listeners) {
            _listeners.remove(listener);
        }
    }

    /**
     * Get provider listeners
     * @return Listeners
     */
    private List<ExtraDetailsListener> getListeners() {
        synchronized (_listeners) {
            return new ArrayList<>(_listeners);
        }
    }
}
