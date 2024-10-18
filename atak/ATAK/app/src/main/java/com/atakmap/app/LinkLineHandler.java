
package com.atakmap.app;

import android.graphics.Color;

import com.atakmap.android.maps.Association;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.coremap.log.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LinkLineHandler
        implements MapEventDispatcher.MapEventDispatchListener {
    private static final String TAG = "LinkLineHandler";
    private final Map<String, Map<String, _Link>> _links = new HashMap<>();
    //Child arrives first.
    private final Map<String, List<MapItem>> deferredChildLinkage = new HashMap<>();
    //Parent arrives first.
    private final Map<MapItem, List<String>> deferredParentLinkage = new HashMap<>();
    private final MapGroup _linkGroup;
    private final MapEventDispatcher mapEventDispatcher;

    /**
     * Generates a Broadcast receiver that generates a link between a first and a second UID.
     * @param mapEventDispatcher the mapView to register / unregister item listeners for cases where there
     *                are deferred links arriving.
     * @param linkGroup the group which contains the associations/links.
     */
    public LinkLineHandler(final MapEventDispatcher mapEventDispatcher,
            final MapGroup linkGroup) {
        _linkGroup = linkGroup;
        this.mapEventDispatcher = mapEventDispatcher;
    }

    /**
     * If child comes in with parent, then the parent Uid is given to add to deferred list.
     * If parent comes in without child, then the child Uid should be added to deferred list.
     * @param parentUid unique identifier for the parent
     * @param parentItem parent MapItem. One parent can have many children.
     * @param childUid unique identifier for the child
     * @param childItem child MapItem. One child can only have one parent.
     * @return true if the link has been processed
     */
    public boolean processLink(String parentUid, MapItem parentItem,
            String childUid, MapItem childItem) {
        if (parentItem == null || childItem == null) {
            boolean successDeferred = beginMonitoringDeferred(parentUid,
                    parentItem, childUid, childItem);
            if (!successDeferred) {
                return false;
            }
        }
        //need to dig further to see if childUid will actually exist.
        else if (parentUid != null && childUid != null) {
            if (!_removeLink(parentUid, childUid)) {
                //If a link has been found for parent, just delete it's link with the parent
                //then add the link with the new parent.
                //If the link has not been found, still add a link with the new parent.
                String currentParentUid = findObjectInMap(childUid);
                if (currentParentUid != null) {
                    _removeLink(currentParentUid, childUid);
                }
                return _addLink(parentItem, childItem);
            }
        } else {
            Log.e(TAG, "---------------------------------------------------");
            Log.e(TAG, "One or both of child and parent UID is null.");
            Log.e(TAG, "---------------------------------------------------");
        }
        return false;
    }

    /*
     * Finds the object in the Map, and removes it from the Map.
     * This method is specific for _links Map.
     */
    private String findObjectInMap(String newChildUid) {
        for (Map.Entry<String, Map<String, _Link>> pair : _links.entrySet()) {
            Map<String, _Link> childMap = pair.getValue();

            for (Map.Entry<String, _Link> childPair : childMap.entrySet()) {
                String childUid = childPair.getKey();
                if (childUid.equals(newChildUid)) {
                    return pair.getKey();
                }
            }
        }
        return null;
    }

    /*
     * Finds the object in the list, and removes it from the list.
     * Returns back the list without the object in it if it was found.
     */
    private Map<Object, List<Object>> findObjectInList(Iterator it,
            Object eventObject) {
        Map<Object, List<Object>> returnList = new HashMap<>();

        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            List<Object> childList = (List<Object>) pair.getValue();
            List<Object> newList = new ArrayList<>();
            Object parentObject = null;
            for (Object childObject : childList) {
                if (childObject != eventObject) {
                    //if the items are equal, do not add this to the new list
                    //as it should be removed.
                    newList.add(childObject);
                } else if (childObject == eventObject) {
                    parentObject = pair.getKey();
                }
            }
            if (parentObject != null) {
                returnList.put(parentObject, newList);
                break;
            }
        }
        return returnList;
    }

    @Override
    public void onMapEvent(MapEvent event) {
        if (event == null) {
            Log.e(TAG, "---------------------------------------------------");
            Log.e(TAG, "OnMapEvent: event is null.");
            Log.e(TAG, "---------------------------------------------------");
            return;
        }
        synchronized (deferredChildLinkage) {
            if (event.getType().equals(MapEvent.ITEM_ADDED)) {
                //find if it is parent or child that was added
                MapItem eventItem = event.getItem();
                String eventUid = eventItem.getUID();

                //check if event is parent
                List<MapItem> deferredChildList = deferredChildLinkage
                        .remove(eventUid);
                if (deferredChildList != null) {
                    for (MapItem childItem : deferredChildList)
                        _addLink(eventItem, childItem);
                } else if (deferredChildList == null) {
                    //check if event is child
                    Iterator it = deferredParentLinkage.entrySet().iterator();
                    Map<Object, List<Object>> object = findObjectInList(it,
                            eventItem.getUID());
                    Iterator itObject = object.entrySet().iterator();
                    if (itObject.hasNext()) {
                        Map.Entry pair = (Map.Entry) itObject.next();
                        MapItem parentItem = (MapItem) pair.getKey();
                        List<String> childrenUidList = (List<String>) pair
                                .getValue();
                        //update deferredParentLinkage list
                        if (parentItem != null) {
                            if (childrenUidList.size() != 0) {
                                deferredParentLinkage.put(parentItem,
                                        childrenUidList);
                            } else {
                                deferredParentLinkage.remove(parentItem);
                            }
                            _addLink(parentItem, eventItem);
                        }

                    }
                }
            } else if (event.getType().equals(MapEvent.ITEM_REMOVED)) {
                MapItem eventItem = event.getItem();
                //check if event is parent
                List<String> deferredChildList = deferredParentLinkage
                        .remove(eventItem);
                if (deferredChildList == null) {
                    //check if event is child
                    Iterator it = deferredChildLinkage.entrySet().iterator();
                    Map<Object, List<Object>> object = findObjectInList(it,
                            eventItem);
                    Iterator itObject = object.entrySet().iterator();
                    if (itObject.hasNext()) {
                        Map.Entry pair = (Map.Entry) itObject.next();
                        String parentUid = (String) pair.getKey();
                        List<MapItem> childrenUidList = (List<MapItem>) pair
                                .getValue();
                        if (parentUid != null) {
                            if (childrenUidList.size() != 0) {
                                deferredChildLinkage.put(parentUid,
                                        childrenUidList);
                            } else {
                                deferredChildLinkage.remove(parentUid);
                            }
                        }
                    }
                }
            }
            int size = deferredChildLinkage.size()
                    + deferredParentLinkage.size();
            if (size == 0) {
                mapEventDispatcher.removeMapEventListener(MapEvent.ITEM_ADDED,
                        this);
                mapEventDispatcher.removeMapEventListener(MapEvent.ITEM_REMOVED,
                        this);
            }
        }
    }

    private boolean beginMonitoringDeferred(String parentUid,
            MapItem parentItem, String childUid, MapItem childItem) {
        synchronized (deferredChildLinkage) {
            MapEventDispatcher med = mapEventDispatcher;
            int size = deferredChildLinkage.size()
                    + deferredParentLinkage.size();

            //child came first
            if (parentItem == null) {
                Log.d(TAG,
                        "---------------------------------------------------");
                Log.d(TAG,
                        "Deferring child item: parentItem is null so the childItem came first.");
                Log.d(TAG,
                        "---------------------------------------------------");

                //first see if the child changed references to the parent.
                Iterator it = deferredChildLinkage.entrySet().iterator();
                Map<Object, List<Object>> object = findObjectInList(it,
                        childItem);
                if (object.size() > 0) {
                    //child changed references so remove the child from the current parent UID
                    Iterator itObject = object.entrySet().iterator();
                    if (itObject.hasNext()) {
                        Map.Entry pair = (Map.Entry) itObject.next();
                        String currentParentUid = (String) pair.getKey();
                        List<MapItem> childrenUidList = (List<MapItem>) pair
                                .getValue();

                        //if the current parent UID and the new parent UID are the same,
                        //then there is no need to update the list.
                        if (currentParentUid != null
                                && !currentParentUid.equals(parentUid)) {
                            if (childrenUidList.size() != 0) {
                                deferredChildLinkage.put(currentParentUid,
                                        childrenUidList);
                            } else {
                                deferredChildLinkage.remove(currentParentUid);
                            }
                        }
                    }
                }

                List<MapItem> list = deferredChildLinkage.get(parentUid);
                if (list == null) {
                    list = new ArrayList<>();
                }
                if (!list.contains(childItem)) {
                    list.add(childItem);
                }
                deferredChildLinkage.put(parentUid, list);
            }
            //parent came first
            else if (childItem == null) {
                Log.d(TAG,
                        "---------------------------------------------------");
                Log.d(TAG,
                        "Deferring parent item: childItem is null so the parentItem came first.");
                Log.d(TAG,
                        "---------------------------------------------------");

                List<String> list = deferredParentLinkage.get(parentItem);
                if (list == null)
                    list = new ArrayList<>();

                list.add(childUid);

                deferredParentLinkage.put(parentItem, list);
            } else {
                Log.e(TAG,
                        "---------------------------------------------------");
                Log.e(TAG,
                        "Both childItem and parentItem were null. Cannot defer either one.");
                Log.e(TAG,
                        "---------------------------------------------------");
                return false;
            }

            if (size == 0) {
                med.addMapEventListener(MapEvent.ITEM_ADDED, this);
                med.addMapEventListener(MapEvent.ITEM_REMOVED, this);
            }
        }
        return true;
    }

    private String getLoggableUid(MapItem mi) {
        if (mi == null)
            return null;
        else
            return mi.getUID();
    }

    private boolean _addLink(final MapItem parentItem,
            final MapItem childItem) {
        boolean addedLink = false;

        if (parentItem == null || childItem == null) {
            Log.d(TAG,
                    "Failed to pair items " + getLoggableUid(parentItem) + " ("
                            + parentItem + ") -> " + getLoggableUid(childItem)
                            + " (" + childItem + ")");

            return false;
        }

        String parentUid = parentItem.getUID();
        String childUid = childItem.getUID();

        // Let the detail handler know the marker was found
        childItem.setMetaBoolean("paired", true);
        Log.d(TAG, "Adding link to " + childUid + " ("
                + childItem + ")");
        if (parentItem instanceof PointMapItem
                && childItem instanceof PointMapItem) {
            final _Link link = new _Link();
            link.assoc = new Association((PointMapItem) parentItem,
                    (PointMapItem) childItem, UUID.randomUUID().toString());

            link.assoc.setColor(Color.WHITE);
            link.assoc.setStrokeWeight(3d);
            _linkGroup.addItem(link.assoc);
            addedLink = true;

            link.mapEventListener = new MapItem.OnGroupChangedListener() {
                @Override
                public void onItemAdded(MapItem item, MapGroup newParent) {
                }

                @Override
                public void onItemRemoved(MapItem item, MapGroup oldParent) {
                    _linkGroup.removeItem(link.assoc);
                    Log.d(TAG, "Removing group changed listener from "
                            + parentItem + " and " + childItem);
                    synchronized (parentItem) {
                        parentItem.removeOnGroupChangedListener(this);
                    }
                    childItem.removeOnGroupChangedListener(this);
                    childItem.setMetaBoolean("paired", false);
                }
            };
            synchronized (parentItem) {
                parentItem.addOnGroupChangedListener(link.mapEventListener);
            }
            childItem.addOnGroupChangedListener(link.mapEventListener);
            Log.d(TAG,
                    "Adding group changed listener to " + parentItem + " and "
                            + childItem);

            link.vizListener = new MapItem.OnVisibleChangedListener() {
                @Override
                public void onVisibleChanged(MapItem item) {
                    link.assoc.setVisible(
                            parentItem.getVisible() && childItem.getVisible());
                }
            };
            link.assoc.setVisible(
                    parentItem.getVisible() && childItem.getVisible());
            synchronized (parentItem) {
                parentItem.addOnVisibleChangedListener(link.vizListener);
            }
            childItem.addOnVisibleChangedListener(link.vizListener);

            synchronized (_links) {
                Map<String, _Link> map = _links.get(parentUid);
                if (map == null) {
                    map = new HashMap<>();
                    _links.put(parentUid, map);
                }
                map.put(childUid, link);
            }
        }
        return addedLink;
    }

    private boolean _removeLink(String parentUid, String childUid) {
        _Link link = null;
        synchronized (_links) {
            Map<String, _Link> map = _links.get(parentUid);
            if (map != null) {
                link = map.remove(childUid);
                if (map.size() == 0) {
                    _links.remove(parentUid);
                }
            }
        }
        if (link != null) {
            MapItem item1 = link.assoc.getFirstItem();
            MapItem item2 = link.assoc.getSecondItem();
            if (item1 != null && item2 != null) {
                item1.removeOnGroupChangedListener(link.mapEventListener);
                item1.removeOnVisibleChangedListener(link.vizListener);
                item2.removeOnGroupChangedListener(link.mapEventListener);
                item2.removeOnVisibleChangedListener(link.vizListener);
            }
            _linkGroup.removeItem(link.assoc);
        }

        return link != null;
    }

    /**
     * returns number of parents from each list
     * @return the number of deferred links
     */
    public int getNumDeferredLinks() {
        return deferredChildLinkage.size() + deferredParentLinkage.size();
    }

    /**
     * returns number of parents that have a link
     * @return the number of links
     */
    public int getNumLinks() {
        return _links.size();
    }

    private static class _Link {
        Association assoc;
        MapItem.OnGroupChangedListener mapEventListener;
        MapItem.OnVisibleChangedListener vizListener;
    }
}
