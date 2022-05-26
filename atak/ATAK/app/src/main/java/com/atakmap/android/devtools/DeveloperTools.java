
package com.atakmap.android.devtools;

import android.widget.BaseAdapter;

import com.atakmap.android.hierarchy.HierarchyListFilter;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.items.LayerHierarchyListItem;
import com.atakmap.android.maps.DeepMapItemQuery;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.map.layer.control.ClampToGroundControl;
import com.atakmap.map.layer.control.LollipopControl;
import com.atakmap.android.overlay.AbstractMapOverlay2;
import com.atakmap.map.MapControl;
import com.atakmap.map.MapRenderer3;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.Layer2;
import com.atakmap.map.layer.control.AtmosphereControl;
import com.atakmap.map.layer.control.SurfaceRendererControl;
import com.atakmap.util.Visitor;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

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

    static final class DevTools extends DevToolGroup {
        public DevTools(MapView view) {
            super("Developer Tools", "DeveloperTools");

            Collection<Layer> layers = view.getLayers();
            for (Layer l : layers)
                _children.add(new LayerHierarchyListItem(l));

            _children.add(new DisplayModeHierachyListItem(view.getRenderer3()));

            final MapRenderer3 renderer = view.getRenderer3();
            _children.add(new CameraControls(renderer));
            final SurfaceRendererControl surfaceControl = renderer
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

            _children.add(new DevToolToggle("Lollipops Visible",
                    "Controls.LollipopControl") {
                @Override
                protected boolean isEnabled() {
                    final boolean[] toggled = new boolean[] {
                            false
                    };
                    // XXX - could be implemented to lookup
                    //       `RootMapGroupLayer` exactly on MapView and then
                    //       visit specifically for `LollipopControl`
                    renderer.visitControls(
                            new Visitor<Iterator<Map.Entry<Layer2, Collection<MapControl>>>>() {
                                @Override
                                public void visit(
                                        Iterator<Map.Entry<Layer2, Collection<MapControl>>> object) {
                                    boolean visited = false;
                                    while (object.hasNext()) {
                                        for (MapControl c : object.next()
                                                .getValue()) {
                                            if (c instanceof LollipopControl) {
                                                toggled[0] |= ((LollipopControl) c)
                                                        .getLollipopsVisible();
                                                visited = true;
                                            }
                                        }
                                    }
                                    toggled[0] |= !visited;
                                }
                            });

                    // treat as on if either no controls were visited, or if any visited were visible
                    return toggled[0];
                }

                @Override
                protected void setEnabled(final boolean v) {
                    renderer.visitControls(
                            new Visitor<Iterator<Map.Entry<Layer2, Collection<MapControl>>>>() {
                                @Override
                                public void visit(
                                        Iterator<Map.Entry<Layer2, Collection<MapControl>>> object) {
                                    boolean visited = false;
                                    while (object.hasNext()) {
                                        for (MapControl c : object.next()
                                                .getValue()) {
                                            if (c instanceof LollipopControl) {
                                                ((LollipopControl) c)
                                                        .setLollipopsVisible(v);
                                            }
                                        }
                                    }
                                }
                            });
                }
            });

            _children.add(new DevToolToggle("Legacy Altitude Rendering",
                    "Controls.LegacyAltitudeRendering") {
                @Override
                protected boolean isEnabled() {
                    final boolean[] toggled = new boolean[] {
                            false
                    };
                    // XXX - could be implemented to lookup
                    //       `RootMapGroupLayer` exactly on MapView and then
                    //       visit specifically for `ClampToGroundControl`
                    renderer.visitControls(
                            new Visitor<Iterator<Map.Entry<Layer2, Collection<MapControl>>>>() {
                                @Override
                                public void visit(
                                        Iterator<Map.Entry<Layer2, Collection<MapControl>>> object) {
                                    boolean visited = false;
                                    while (object.hasNext()) {
                                        for (MapControl c : object.next()
                                                .getValue()) {
                                            if (c instanceof ClampToGroundControl) {
                                                toggled[0] |= ((ClampToGroundControl) c)
                                                        .getClampToGroundAtNadir();
                                                visited = true;
                                            }
                                        }
                                    }
                                    toggled[0] |= !visited;
                                }
                            });

                    // treat as on if either no controls were visited, or if any visited were visible
                    return toggled[0];
                }

                @Override
                protected void setEnabled(final boolean v) {
                    renderer.visitControls(
                            new Visitor<Iterator<Map.Entry<Layer2, Collection<MapControl>>>>() {
                                @Override
                                public void visit(
                                        Iterator<Map.Entry<Layer2, Collection<MapControl>>> object) {
                                    boolean visited = false;
                                    while (object.hasNext()) {
                                        for (MapControl c : object.next()
                                                .getValue()) {
                                            if (c instanceof LollipopControl) {
                                                ((ClampToGroundControl) c)
                                                        .setClampToGroundAtNadir(
                                                                v);
                                            }
                                        }
                                    }
                                }
                            });
                }
            });
        }
    }
}
