
package com.atakmap.android.devtools;

import android.widget.BaseAdapter;

import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.maps.DeepMapItemQuery;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.overlay.AbstractMapOverlay2;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.control.AtmosphereControl;
import com.atakmap.map.layer.control.SurfaceRendererControl;
import com.atakmap.util.Visitor;

import java.util.Collection;

public class DeveloperTools extends AbstractMapOverlay2 {
    final MapView _mapView;

    public DeveloperTools(MapView view) {
        _mapView = view;
    }

    @Override
    public HierarchyListItem getListModel(BaseAdapter adapter,
            long capabilities, HierarchyListFilter preferredFilter) {
        return new DevTools(_mapView);
    }

    @Override
    public String getIdentifier() {
        return "DeveloperTools";
    }

    @Override
    public String getName() {
        return "Developer Tools";
    }

    @Override
    public MapGroup getRootGroup() {
        return null;
    }

    @Override
    public DeepMapItemQuery getQueryFunction() {
        return null;
    }

    final class DevTools extends DevToolGroup {
        public DevTools(MapView view) {
            super("Developer Tools", "DeveloperTools");

            Collection<Layer> layers = view.getLayers();
            for (Layer l : layers)
                _children.add(new LayerHierarchyListItem(l));

            _children.add(new DisplayModeHierachyListItem(view.getRenderer3()));

            final SurfaceRendererControl surfaceControl = view.getRenderer3()
                    .getControl(SurfaceRendererControl.class);
            if (surfaceControl != null) {
                _children.add(new SurfaceRendererControlHierarchyListItem(
                        surfaceControl));
                _children.add(new CollisionOptions(surfaceControl));
            }
            final AtmosphereControl atmosphereControl = view.getRenderer3()
                    .getControl(AtmosphereControl.class);
            if (atmosphereControl != null)
                _children.add(new AtmosphereToggle(atmosphereControl));

            _children.add(new CameraPanToToggle(view));
            _children.add(new ContinuousRenderToggle(
                    view.getRenderer3().getRenderContext()));
            _children.add(new RenderDiagnosticsToggle(
                    view.getGLSurface().getGLMapView()));
            _children.add(new ForceCloseHierarchyListItem());
        }
    };
}
