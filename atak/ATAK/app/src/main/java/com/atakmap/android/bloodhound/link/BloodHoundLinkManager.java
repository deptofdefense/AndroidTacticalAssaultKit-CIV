
package com.atakmap.android.bloodhound.link;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.toolbars.RangeAndBearingMapItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages active bloodhound links
 */
public class BloodHoundLinkManager {

    private static BloodHoundLinkManager _instance;

    public static synchronized BloodHoundLinkManager getInstance() {
        return _instance;
    }

    private final MapView _mapView;
    private final Map<String, BloodHoundLink> _links = new HashMap<>();
    private boolean _taskRunning;
    private int _outerTick, _middleTick, _innerTick;

    public BloodHoundLinkManager(MapView mapView) {
        _mapView = mapView;
        _instance = this;
    }

    public void dispose() {
        synchronized (_links) {
            _links.clear();
        }
    }

    /**
     * Check whether this line can be used as a bloodhound link
     * Line must have 2 non-endpoint markers
     * @param item Map item
     * @return True if the line can be used as a link
     */
    public boolean canLink(MapItem item) {
        // Item must be a R&B line
        if (item == null || item.getGroup() == null
                || !(item instanceof RangeAndBearingMapItem))
            return false;

        // Can't be the line used for the single-bloodhound tool
        if (item.getType().equals("rb")
                && item.hasMetaValue("displayBloodhoundEta"))
            return false;

        // Line must have 2 non-default endpoint markers
        RangeAndBearingMapItem line = (RangeAndBearingMapItem) item;
        PointMapItem p1 = line.getPoint1Item();
        PointMapItem p2 = line.getPoint2Item();
        return p1 != null
                && p2 != null && !p1.getType().endsWith("-endpoint");
    }

    /**
     * Check if a certain line has an active link based on its UID
     * @param uid R&B line uid
     * @return True if active
     */
    public boolean hasLink(String uid) {
        synchronized (_links) {
            return _links.containsKey(uid);
        }
    }

    /**
     * Add a link to a R&B line
     * @param line R&B line
     */
    public void addLink(RangeAndBearingMapItem line) {
        if (!canLink(line))
            return;
        synchronized (_links) {
            _links.put(line.getUID(), new BloodHoundLink(_mapView, this, line));
            update(false);
        }

    }

    /**
     * Remove a link for a given line UID
     * @param uid R&B line uid
     */
    public void removeLink(String uid) {

        BloodHoundLink link;
        synchronized (_links) {
            link = _links.remove(uid);
        }
        if (link != null)
            link.dispose();
        update(false);
    }

    /**
     * Remove a link provided a Range and Bearing Map Item
     * @param line the link line to remove
     */
    public void removeLink(RangeAndBearingMapItem line) {
        if (line != null)
            removeLink(line.getUID());
    }

    private final Runnable _updateTask = new Runnable() {
        @Override
        public void run() {
            if (++_outerTick == 9)
                _outerTick = 0;
            if (++_middleTick == 6)
                _middleTick = 0;
            if (++_innerTick == 3)
                _innerTick = 0;
            update(true);
        }
    };

    /**
     * Update labels and states for all linked lines
     * @param inTask True if called from the update task
     */
    private void update(boolean inTask) {

        synchronized (_links) {
            if (_links.isEmpty()) {
                if (inTask)
                    _taskRunning = false;
                return;
            }

            for (BloodHoundLink link : new ArrayList<>(_links.values())) {
                RangeAndBearingMapItem line = link.getLine();
                if (!canLink(line)) {
                    _links.remove(line.getUID());
                    link.dispose();
                } else
                    link.update();
            }
        }
        if (!_taskRunning || inTask) {
            _taskRunning = true;
            _mapView.postDelayed(_updateTask, 300);
        }
    }

    // Flash every 2400ms
    int getOuterTick() {
        return _outerTick;
    }

    // Flash every 1500ms
    int getMiddleTick() {
        return _middleTick;
    }

    // Flash every 600ms
    int getInnerTick() {
        return _innerTick;
    }
}
