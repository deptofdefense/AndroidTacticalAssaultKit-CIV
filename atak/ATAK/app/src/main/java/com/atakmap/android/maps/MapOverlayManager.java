
package com.atakmap.android.maps;

import android.content.Intent;
import android.view.View;
import android.widget.BaseAdapter;

import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.action.Action;
import com.atakmap.android.hierarchy.action.Visibility;
import com.atakmap.android.hierarchy.items.AbstractHierarchyListItem;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.overlay.MapOverlay;
import com.atakmap.android.overlay.MapOverlayParent;
import com.atakmap.android.overlay.MapOverlayRenderer;
import com.atakmap.android.overlay.Overlay;
import com.atakmap.android.overlay.OverlayManager;
import com.atakmap.android.overlay.OverlayManager.OnServiceListener;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MapOverlayManager {

    private static final String TAG = "MapOverlayManager";

    private final MapView mapView;
    private final Map<String, MapOverlay> overlays;

    private final Legacy legacy;

    public MapOverlayManager(MapView mapView) {
        this.mapView = mapView;

        this.overlays = new HashMap<>();

        this.legacy = new Legacy();

        Intent sharedOverlayServiceIntent = new Intent();
        sharedOverlayServiceIntent
                .setAction("com.atakmap.android.overlay.SHARED");
        if (!OverlayManager.aquireService(mapView.getContext(),
                sharedOverlayServiceIntent,
                this.legacy))
            OverlayManager.aquireService(mapView.getContext(), null,
                    this.legacy);
    }

    public synchronized Collection<MapOverlay> getOverlays() {
        return Collections.unmodifiableCollection(this.overlays.values());
    }

    public synchronized MapOverlay getOverlay(String id) {
        return this.overlays.get(id);
    }

    public synchronized boolean addOtherOverlay(MapOverlay overlay) {
        String iconUri = "android.resource://"
                + mapView.getContext().getPackageName()
                + "/" + R.drawable.ic_overlay_offscrind;

        return addOverlay(
                MapOverlayParent.getOrAddParent(this.mapView, "otheroverlays",
                        "Other Overlays",
                        iconUri, 99, false),
                overlay);
    }

    public synchronized boolean addShapesOverlay(MapOverlay overlay) {
        String iconUri = "android.resource://"
                + mapView.getContext().getPackageName()
                + "/" + R.drawable.shape;

        return addOverlay(
                MapOverlayParent.getOrAddParent(this.mapView, "shapesoverlays",
                        "Shapes",
                        iconUri, 7, false),
                overlay);
    }

    public synchronized boolean addFilesOverlay(MapOverlay overlay) {
        String iconUri = "android.resource://"
                + mapView.getContext().getPackageName()
                + "/" + R.drawable.ic_menu_overlays;

        return addOverlay(
                MapOverlayParent.getOrAddParent(this.mapView, "fileoverlays",
                        "File Overlays",
                        iconUri, 9, false),
                overlay);
    }

    public synchronized boolean removeFilesOverlay(MapOverlay overlay) {
        return removeOverlay(
                MapOverlayParent.getParent(this.mapView, "fileoverlays"),
                overlay);
    }

    public synchronized boolean addAlertsOverlay(MapOverlay overlay) {
        return addOverlay(
                MapOverlayParent.getOrAddParent(this.mapView, "alertoverlays",
                        this.mapView.getContext()
                                .getString(R.string.alerts),
                        "asset://icons/emergency.png", 1, false),
                overlay);
    }

    public synchronized boolean addMarkersOverlay(MapOverlay overlay) {
        return addOverlay(
                MapOverlayParent.getOrAddParent(mapView, "markerroot",
                        "Markers",
                        "asset://icons/affiliations.png", 2, false),
                overlay);
    }

    public synchronized boolean addOverlay(MapOverlayParent parent,
            MapOverlay overlay) {
        if (parent == null || overlay == null) {
            Log.w(TAG, "Cannot add child overlay");
            return false;
        }

        parent.add(overlay);

        if (overlay.getRootGroup() != null)
            this.mapView.getRootGroup()
                    .addGroup(overlay.getRootGroup(),
                            overlay.getQueryFunction());

        updateHierarchy("addOverlay", overlay);

        return true;
    }

    public synchronized boolean removeOverlay(MapOverlayParent parent,
            MapOverlay overlay) {
        if (parent == null || overlay == null) {
            Log.w(TAG, "Cannot remove child overlay");
            return false;
        }

        parent.remove(overlay);

        if (overlay.getRootGroup() != null)
            this.mapView.getRootGroup()
                    .removeGroup(overlay.getRootGroup());

        updateHierarchy("removeOverlay", overlay);

        return true;
    }

    public synchronized boolean addOverlay(MapOverlay overlay) {
        if (this.overlays.containsKey(overlay.getIdentifier()))
            return false;
        this.overlays.put(overlay.getIdentifier(), overlay);

        if (overlay.getRootGroup() != null)
            this.mapView.getRootGroup()
                    .addGroup(overlay.getRootGroup(),
                            overlay.getQueryFunction());

        updateHierarchy("addOverlay", overlay);

        return true;
    }

    public synchronized void removeOverlay(String id) {
        MapOverlay toRemove = this.overlays.get(id);
        if (toRemove == null) {
            for (MapOverlay overlay : this.overlays.values()) {
                if (overlay instanceof MapOverlayParent) {
                    toRemove = ((MapOverlayParent) overlay).remove(id);
                    if (toRemove != null) {
                        break;
                    }
                }
            }
        }
        if (toRemove != null)
            this.removeOverlayNoSync(toRemove);
    }

    public synchronized void removeOverlay(MapOverlay overlay) {
        this.removeOverlayNoSync(overlay);
    }

    private void removeOverlayNoSync(MapOverlay overlay) {
        if (overlay.getRootGroup() != null)
            this.mapView.getRootGroup().removeGroup(overlay.getRootGroup());

        this.overlays.remove(overlay.getIdentifier());
        updateHierarchy("removeOverlay", overlay);
    }

    public void installOverlayRenderer(MapOverlayRenderer renderer) {
        installOverlayRenderer(this.mapView, renderer);
    }

    public void uninstallOverlayRenderer(MapOverlayRenderer renderer) {
        uninstallOverlayRenderer(this.mapView, renderer);
    }

    public void dispose() {
        if (this.legacy.impl != null) {
            this.legacy.impl.releaseService();
            this.legacy.impl = null;
        }
    }

    private void updateHierarchy(String action,
            MapOverlay overlay) {
        AtakBroadcast.getInstance().sendBroadcast(
                new Intent("com.atakmap.android.maps.UPDATE_HIERARCHY")
                        .putExtra(action, overlay.getIdentifier()));
    }

    /**************************************************************************/

    public static void installOverlayRenderer(MapView mapView,
            MapOverlayRenderer renderer) {
        switch (renderer.stackOp) {
            case PUSH_AND_RELEASE:
            case PUSH:
                mapView.pushStack(renderer.stack);
                break;
            default:
                break;
        }

        mapView.addLayer(renderer.stack, renderer.preferredOrder, renderer);
    }

    public static void uninstallOverlayRenderer(MapView mapView,
            MapOverlayRenderer renderer) {
        switch (renderer.stackOp) {
            case ADD:
                mapView.removeLayer(renderer.stack, renderer);
                break;
            case PUSH_AND_RELEASE:
            case PUSH:
                mapView.popStack(renderer.stack);
                break;
            default:
                throw new IllegalStateException();
        }
    }

    /**************************************************************************/

    private class Legacy implements OnServiceListener,
            OverlayManager.OnCacheListener {
        private OverlayManager impl;

        @Override
        public void onOverlayCached(OverlayManager manager, Overlay overlay) {
            MapOverlayManager.this.addOtherOverlay(new LegacyOverlay(overlay));
        }

        @Override
        public void onOverlayUncached(OverlayManager manager, Overlay overlay) {
            MapOverlayManager.this.removeOverlay(overlay.getOverlayId());
        }

        @Override
        public void onOverlayManagerBind(OverlayManager manager) {
            this.impl = manager;

            Overlay[] overlays = this.impl.getOverlays();
            for (Overlay overlay : overlays)
                MapOverlayManager.this
                        .addOverlay(new LegacyOverlay(overlay));

            this.impl.addOnCacheListener(this);
        }

        @Override
        public void onOverlayManagerUnbind(OverlayManager manager) {
            this.impl.removeOnCacheListener(this);

        }
    }

    private static class LegacyOverlay extends AbstractHierarchyListItem
            implements Visibility,
            MapOverlay {
        private final Overlay impl;

        public LegacyOverlay(Overlay impl) {
            this.impl = impl;
        }

        @Override
        public String getIdentifier() {
            return this.impl.getOverlayId();
        }

        @Override
        public String getName() {
            return this.impl.getFriendlyName();
        }

        @Override
        public MapGroup getRootGroup() {
            return null;
        }

        @Override
        public DeepMapItemQuery getQueryFunction() {
            return null;
        }

        @Override
        public HierarchyListItem getListModel(BaseAdapter adapter,
                long actions,
                HierarchyListItem.Sort sort) {
            return this;
        }

        /**********************************************************************/
        // Visibility

        @Override
        public boolean isVisible() {
            return this.impl.getVisible();
        }

        @Override
        public boolean setVisible(boolean visible) {
            this.impl.setVisible(visible);
            return true;
        }

        /**********************************************************************/
        // Hierarchy List Item

        @Override
        public String getTitle() {
            return this.impl.getFriendlyName();
        }

        @Override
        public int getChildCount() {
            return 0;
        }

        @Override
        public int getDescendantCount() {
            return 0;
        }

        @Override
        public HierarchyListItem getChildAt(int index) {
            return null;
        }

        @Override
        public boolean isChildSupported() {
            return false;
        }

        @Override
        public String getIconUri() {
            return this.impl.getIconUri();
        }

        @Override
        public <T extends Action> T getAction(Class<T> clazz) {
            if (clazz.equals(Visibility.class))
                return clazz.cast(this);
            else
                return null;
        }

        @Override
        public Object getUserObject() {
            return this;
        }

        @Override
        public View getExtraView() {
            return null;
        }

        @Override
        public Sort refresh(Sort sort) {
            return sort;
        }
    }
}
