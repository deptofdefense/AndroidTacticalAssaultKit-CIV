
package com.atakmap.android.maps;

import com.atak.plugins.impl.PluginMapComponent;
import com.atakmap.android.bloodhound.BloodHoundMapComponent;
import com.atakmap.android.brightness.BrightnessComponent;
import com.atakmap.android.channels.ChannelsMapComponent;
import com.atakmap.android.chat.ChatManagerMapComponent;
import com.atakmap.android.compassring.CompassRingMapComponent;
import com.atakmap.android.coordoverlay.CoordOverlayMapComponent;
import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.cotdetails.CoTInfoMapComponent;
import com.atakmap.android.data.DataMgmtMapComponent;
import com.atakmap.android.drawing.DrawingToolsMapComponent;
import com.atakmap.android.dropdown.DropDownManagerMapComponent;
import com.atakmap.android.elev.ElevationMapComponent;
import com.atakmap.android.elev.ElevationOverlaysMapComponent;
import com.atakmap.android.emergency.EmergencyAlertComponent;
import com.atakmap.android.emergency.tool.EmergencyLifecycleListener;
import com.atakmap.android.fires.FiresMapComponent;
import com.atakmap.android.fires.HostileManagerMapComponent;
import com.atakmap.android.firstperson.FirstPersonMapComponent;
import com.atakmap.android.gdal.NativeRenderingMapComponent;
import com.atakmap.android.geofence.component.GeoFenceComponent;
import com.atakmap.android.gpkg.GeopackageMapComponent;
import com.atakmap.android.gridlines.GridLinesMapComponent;
import com.atakmap.android.hashtags.HashtagMapComponent;
import com.atakmap.android.hierarchy.HierarchyMapComponent;
import com.atakmap.android.icons.IconsMapComponent;
import com.atakmap.android.image.ImageMapComponent;
import com.atakmap.android.image.quickpic.QuickPicMapComponent;
import com.atakmap.android.importexport.ImportExportMapComponent;
import com.atakmap.android.jumpbridge.JumpBridgeMapComponent;
import com.atakmap.android.layers.LayersMapComponent;
import com.atakmap.android.location.LocationMapComponent;
import com.atakmap.android.lrf.LRFMapComponent;
import com.atakmap.android.maps.graphics.widgets.GLWidgetsMapComponent;
import com.atakmap.android.maps.selector.MapItemListComponent;
import com.atakmap.android.maps.tilesets.TilesetMapComponent;
import com.atakmap.android.maps.visibility.VisibilityMapComponent;
import com.atakmap.android.medline.MedicalLineMapComponent;
import com.atakmap.android.menu.MenuMapComponent;
import com.atakmap.android.metricreport.MetricReportMapComponent;
import com.atakmap.android.missionpackage.MissionPackageMapComponent;
import com.atakmap.android.model.ModelMapComponent;
import com.atakmap.android.munitions.DangerCloseMapComponent;
import com.atakmap.android.navigation.views.loadout.LoadoutListMapComponent;
import com.atakmap.android.navigation.views.loadout.LoadoutToolsMapComponent;
import com.atakmap.android.navigation.widgets.FreeLookMapComponent;
import com.atakmap.android.navigation.widgets.NavWidgetsMapComponent;
import com.atakmap.android.nightvision.NightVisionMapWidgetComponent;
import com.atakmap.android.offscreenindicators.OffScreenIndicatorsMapComponent;
import com.atakmap.android.pairingline.PairingLineMapComponent;
import com.atakmap.android.radiolibrary.RadioMapComponent;
import com.atakmap.android.resection.ResectionMapComponent;
import com.atakmap.android.routes.RouteMapComponent;
import com.atakmap.android.rubbersheet.RubberSheetMapComponent;
import com.atakmap.android.statesaver.StateSaver;
import com.atakmap.android.targetbubble.TargetBubbleMapComponent;
import com.atakmap.android.toolbar.ToolbarMapComponent;
import com.atakmap.android.toolbars.RangeAndBearingMapComponent;
import com.atakmap.android.track.TrackHistoryComponent;
import com.atakmap.android.update.ApkUpdateComponent;
import com.atakmap.android.user.UserMapComponent;
import com.atakmap.android.vehicle.VehicleMapComponent;
import com.atakmap.android.video.VideoMapComponent;
import com.atakmap.android.viewshed.ViewshedMapComponent;
import com.atakmap.android.warning.WarningComponent;
import com.atakmap.android.warning.WarningMapComponent;
import com.atakmap.android.wfs.WFSMapComponent;
import com.atakmap.app.preferences.json.JSONPreferenceControl;
import com.atakmap.app.system.AbstractSystemComponent;
import com.atakmap.app.system.MapComponentProvider;
import com.atakmap.app.system.SystemComponentLoader;
import com.atakmap.comms.CommsMapComponent;
import com.atakmap.coremap.log.Log;
import com.atakmap.spatial.wkt.WktMapComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * For loading the entire list of core map components
 */
public class MapComponentLoader {
    private static final String TAG = "MapComponentLoader";

    private static final List<Class> mapComponents = new ArrayList<>();

    static {
        // location needs to be first
        mapComponents.add(LocationMapComponent.class);

        // common communications next
        mapComponents.add(CommsMapComponent.class);

        mapComponents.add(LoadoutToolsMapComponent.class);
        mapComponents.add(NavWidgetsMapComponent.class);
        mapComponents.add(VisibilityMapComponent.class);
        mapComponents.add(BrightnessComponent.class);
        mapComponents.add(DataMgmtMapComponent.class);
        mapComponents.add(ImportExportMapComponent.class);
        // UserMapComponent & CotMapComponent create MapGroups used by other components
        mapComponents.add(UserMapComponent.class);
        mapComponents.add(CotMapComponent.class);
        mapComponents.add(MenuMapComponent.class);
        mapComponents.add(FreeLookMapComponent.class);
        mapComponents.add(ElevationMapComponent.class);
        mapComponents.add(TargetBubbleMapComponent.class);
        mapComponents.add(DangerCloseMapComponent.class);
        mapComponents.add(WarningMapComponent.class);

        mapComponents.add(VideoMapComponent.class);
        // ChatManagerMapComponent after CotMapComponent
        mapComponents.add(ChatManagerMapComponent.class);
        mapComponents.add(ToolbarMapComponent.class);
        mapComponents.add(IconsMapComponent.class);
        mapComponents.add(ImageMapComponent.class);
        mapComponents.add(CoTInfoMapComponent.class);
        mapComponents.add(HostileManagerMapComponent.class);
        mapComponents.add(OffScreenIndicatorsMapComponent.class);

        // Layer SPI / API components

        mapComponents.add(TilesetMapComponent.class);
        mapComponents.add(NativeRenderingMapComponent.class);

        // The LayersMapComponent should be created after all layer SPI components
        // as it will kick off a layer scan.

        mapComponents.add(LayersMapComponent.class);

        mapComponents.add(LRFMapComponent.class);
        mapComponents.add(FiresMapComponent.class);
        mapComponents.add(CoordOverlayMapComponent.class);
        mapComponents.add(WarningComponent.class);
        mapComponents.add(GridLinesMapComponent.class);
        mapComponents.add(JumpBridgeMapComponent.class);
        mapComponents.add(ElevationOverlaysMapComponent.class);
        mapComponents.add(DropDownManagerMapComponent.class);
        mapComponents.add(PairingLineMapComponent.class);
        mapComponents.add(RouteMapComponent.class);
        mapComponents.add(CompassRingMapComponent.class);
        mapComponents.add(TrackHistoryComponent.class);
        mapComponents.add(WktMapComponent.class);
        mapComponents.add(ViewshedMapComponent.class);
        mapComponents.add(HierarchyMapComponent.class);
        mapComponents.add(RangeAndBearingMapComponent.class);
        mapComponents.add(BloodHoundMapComponent.class);
        mapComponents.add(MedicalLineMapComponent.class);
        mapComponents.add(DrawingToolsMapComponent.class);
        mapComponents.add(RadioMapComponent.class);
        mapComponents.add(MissionPackageMapComponent.class);
        mapComponents.add(QuickPicMapComponent.class);
        mapComponents.add(MapCoreIntentsComponent.class);
        mapComponents.add(ApkUpdateComponent.class);
        mapComponents.add(GeoFenceComponent.class);
        mapComponents.add(EmergencyAlertComponent.class);
        mapComponents.add(EmergencyLifecycleListener.class);
        mapComponents.add(WFSMapComponent.class);
        mapComponents.add(MultiplePairingLineMapComponent.class);

        // Night Vision Component must be placed after Location
        mapComponents.add(NightVisionMapWidgetComponent.class);
        mapComponents.add(ResectionMapComponent.class);
        mapComponents.add(MetricReportMapComponent.class);

        // Fire up the state saver last for the internal components.

        mapComponents.add(StateSaver.class);

        mapComponents.add(GeopackageMapComponent.class);
        mapComponents.add(ModelMapComponent.class);
        mapComponents.add(RubberSheetMapComponent.class);
        mapComponents.add(HashtagMapComponent.class);

        // Vehicle models
        mapComponents.add(VehicleMapComponent.class);

        mapComponents.add(LoadoutListMapComponent.class);
        mapComponents.add(FirstPersonMapComponent.class);
        mapComponents.add(ChannelsMapComponent.class);
        mapComponents.add(MapItemListComponent.class);

        // Load up all of the external components, when this is complete it will trigger the
        // state saver to unroll all of the map components.    This should be done last.
        mapComponents.add(PluginMapComponent.class);

    }

    static void loadGLWidgets(MapActivity activity) {
        activity.registerMapComponent(new GLWidgetsMapComponent());
    }

    /**
     * Allows for a system flavor to register a MapComponent that should be instantiated after an
     * existing component.  If component specified to be loaded after does not exist, the supplied
     * map component will be loaded at the end.
     * @param mapComponent The MapComponent to load
     * @param c the class to load this immediately after.  If c is null, then the mapComponent is
     *          loaded at the very end.
     */
    public static void registerComponentAfter(final Class mapComponent,
            final Class c) {

        SystemComponentLoader.securityCheck();
        if (c == null) {
            mapComponents.add(mapComponent);
            return;
        }

        for (int i = 0; i < mapComponents.size(); ++i) {
            if (c == mapComponents.get(i)) {
                if (i + 1 < mapComponents.size()) {
                    mapComponents.add(i + 1, mapComponent);
                } else {
                    mapComponents.add(c);
                }
                return;

            }
        }
    }

    static void loadMapComponents(MapActivity activity) {

        // JSON preference file serialization and reading
        JSONPreferenceControl.getInstance().initDefaults(activity.getMapView());

        for (Class component : mapComponents) {
            try {
                activity.registerMapComponent(
                        (MapComponent) component.newInstance());
            } catch (Throwable t) {
                Log.e(TAG, "error loading: " + component, t);
            }
        }

        final AbstractSystemComponent[] systemComponents = SystemComponentLoader
                .getComponents();
        for (AbstractSystemComponent systemComponent : systemComponents) {
            if (systemComponent instanceof MapComponentProvider) {
                for (MapComponent mc : ((MapComponentProvider) systemComponent)
                        .getMapComponents()) {
                    try {
                        activity.registerMapComponent(mc);
                    } catch (Throwable t) {
                        Log.e(TAG, "error loading: " + mc, t);
                    }
                }
            }
        }
    }
}
