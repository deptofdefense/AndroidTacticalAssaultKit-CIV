
package com.atakmap.android.action;

import com.atakmap.android.config.ConfigEnvironment;
import com.atakmap.android.config.ConfigFactory;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;

import org.w3c.dom.Node;

import java.util.LinkedList;
import java.util.List;

/**
 * Linear set of multiple MapActions
 */
class SetMapAction implements MapAction {

    private final List<MapAction> _set = new LinkedList<>();

    public static class Factory implements ConfigFactory<MapAction> {
        @Override
        public MapAction createFromElem(ConfigEnvironment config,
                Node defNode) {
            SetMapAction setAction = new SetMapAction();
            Node childNode = defNode.getFirstChild();
            while (childNode != null) {
                if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                    MapAction action = MapActionFactory.createFromElem(
                            childNode, config);
                    if (action != null) {
                        setAction._set.add(action);
                    }
                }
                childNode = childNode.getNextSibling();
            }

            return setAction;
        }
    }

    @Override
    public void performAction(final MapView mapView, final MapItem mapItem) {
        for (MapAction a : _set) {
            a.performAction(mapView, mapItem);
        }
    }

}
