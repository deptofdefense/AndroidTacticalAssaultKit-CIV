
package com.atakmap.android.maps.visibility;

import java.util.ArrayList;
import java.util.List;

/**
 * Track visibility conditions for contents within ATAK
 */
public class VisibilityManager {

    private static final VisibilityManager _instance = new VisibilityManager();

    public static VisibilityManager getInstance() {
        return _instance;
    }

    private final List<VisibilityCondition> _conditions = new ArrayList<>();
    private final List<VisibilityListener> _listeners = new ArrayList<>();

    /**
     * Add a new visibility condition
     * @param cond Visibility condition
     */
    public void addCondition(VisibilityCondition cond) {
        synchronized (_conditions) {
            _conditions.add(cond);
        }
        refreshConditions();
    }

    /**
     * Remove visibility condition
     * @param cond Visibility condition
     */
    public void removeCondition(VisibilityCondition cond) {
        synchronized (_conditions) {
            _conditions.remove(cond);
        }
        refreshConditions();
    }

    /**
     * Get list of visibility conditions
     * @return List of visibility conditions
     */
    public List<VisibilityCondition> getConditions() {
        synchronized (_conditions) {
            return new ArrayList<>(_conditions);
        }
    }

    /**
     * Add listener for when visibility conditions are changed
     * @param l Listener
     */
    public void addListener(VisibilityListener l) {
        synchronized (_listeners) {
            _listeners.add(l);
        }
        l.onVisibilityConditions(getConditions());
    }

    /**
     * Remove visibility condition listener
     * @param l Listener
     */
    public void removeListener(VisibilityListener l) {
        synchronized (_listeners) {
            _listeners.remove(l);
        }
    }

    /**
     * Get list of visibility listeners
     * @return List of visibility listeners
     */
    private List<VisibilityListener> getListeners() {
        synchronized (_listeners) {
            return new ArrayList<>(_listeners);
        }
    }

    /**
     * Refresh visibility conditions by notifying listeners
     */
    public void refreshConditions() {
        List<VisibilityCondition> conditions = getConditions();
        for (VisibilityListener l : getListeners())
            l.onVisibilityConditions(conditions);
    }
}
