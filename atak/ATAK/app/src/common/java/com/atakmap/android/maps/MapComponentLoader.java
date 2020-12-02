
package com.atakmap.android.maps;

import com.atak.plugins.impl.PluginMapComponent;
import com.atakmap.android.bloodhound.BloodHoundMapComponent;
import com.atakmap.android.brightness.BrightnessComponent;
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
import com.atakmap.android.mapcompass.CompassArrowMapComponent;
import com.atakmap.android.maps.graphics.widgets.GLWidgetsMapComponent;
import com.atakmap.android.maps.tilesets.TilesetMapComponent;
import com.atakmap.android.maps.visibility.VisibilityMapComponent;
import com.atakmap.android.medline.MedicalLineMapComponent;
import com.atakmap.android.menu.MenuMapComponent;
import com.atakmap.android.metricreport.MetricReportMapComponent;
import com.atakmap.android.missionpackage.MissionPackageMapComponent;
import com.atakmap.android.model.ModelMapComponent;
import com.atakmap.android.munitions.DangerCloseMapComponent;
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
import com.atakmap.comms.CommsMapComponent;
import com.atakmap.spatial.wkt.WktMapComponent;

/**
 * For loading the entire list of core map components
 */
public class MapComponentLoader {
    
    public static void loadGLWidgets(MapActivity activity) {
        activity.registerMapComponent(new GLWidgetsMapComponent());
    }

    public static void loadMapComponents(MapActivity activity) {
        // location needs to be first
        activity.registerMapComponent(new LocationMapComponent());

        // common communications next
        activity.registerMapComponent(new CommsMapComponent());

        // JSON preference file serialization and reading
        JSONPreferenceControl.getInstance().initDefaults(activity.getMapView());

        activity.registerMapComponent(new VisibilityMapComponent());
        activity.registerMapComponent(new BrightnessComponent());
        activity.registerMapComponent(new DataMgmtMapComponent());
        activity.registerMapComponent(new ImportExportMapComponent());
        // UserMapComponent & CotMapComponent create MapGroups used by other components
        activity.registerMapComponent(new UserMapComponent());
        activity.registerMapComponent(new CotMapComponent());
        activity.registerMapComponent(new MenuMapComponent());
        activity.registerMapComponent(new ElevationMapComponent());
        activity.registerMapComponent(new TargetBubbleMapComponent());
        activity.registerMapComponent(new DangerCloseMapComponent());
        activity.registerMapComponent(new WarningMapComponent());

        FlavorComponentLoader.loadFires(activity);

        activity.registerMapComponent(new VideoMapComponent());
        // ChatManagerMapComponent after CotMapComponent
        activity.registerMapComponent(new ChatManagerMapComponent());
        activity.registerMapComponent(new ToolbarMapComponent());
        activity.registerMapComponent(new IconsMapComponent());
        activity.registerMapComponent(new ImageMapComponent());
        activity.registerMapComponent(new CoTInfoMapComponent());
        activity.registerMapComponent(new HostileManagerMapComponent());
        activity.registerMapComponent(new OffScreenIndicatorsMapComponent());

        // Layer SPI / API components

        activity.registerMapComponent(new TilesetMapComponent());
        activity.registerMapComponent(new NativeRenderingMapComponent());

        // The LayersMapComponent should be created after all layer SPI components
        // as it will kick off a layer scan.

        activity.registerMapComponent(new LayersMapComponent());

        activity.registerMapComponent(new LRFMapComponent());
        activity.registerMapComponent(new FiresMapComponent());
        activity.registerMapComponent(new CoordOverlayMapComponent());
        activity.registerMapComponent(new WarningComponent());
        activity.registerMapComponent(new GridLinesMapComponent());
        activity.registerMapComponent(new JumpBridgeMapComponent());
        activity.registerMapComponent(new ElevationOverlaysMapComponent());
        activity.registerMapComponent(new DropDownManagerMapComponent());
        activity.registerMapComponent(new PairingLineMapComponent());
        activity.registerMapComponent(new RouteMapComponent());
        activity.registerMapComponent(new CompassRingMapComponent());
        activity.registerMapComponent(new TrackHistoryComponent());
        activity.registerMapComponent(new WktMapComponent());
        activity.registerMapComponent(new ViewshedMapComponent());
        activity.registerMapComponent(new HierarchyMapComponent());
        activity.registerMapComponent(new RangeAndBearingMapComponent());
        activity.registerMapComponent(new BloodHoundMapComponent());
        activity.registerMapComponent(new MedicalLineMapComponent());
        activity.registerMapComponent(new DrawingToolsMapComponent());
        activity.registerMapComponent(new CompassArrowMapComponent());
        activity.registerMapComponent(new RadioMapComponent());
        activity.registerMapComponent(new MissionPackageMapComponent());
        activity.registerMapComponent(new QuickPicMapComponent());
        activity.registerMapComponent(new MapCoreIntentsComponent());
        activity.registerMapComponent(new ApkUpdateComponent());
        activity.registerMapComponent(new GeoFenceComponent());
        activity.registerMapComponent(new EmergencyAlertComponent());
        activity.registerMapComponent(new EmergencyLifecycleListener());
        activity.registerMapComponent(new WFSMapComponent());
        activity.registerMapComponent(new MultiplePairingLineMapComponent());

        FlavorComponentLoader.loadSlant(activity);

        // Night Vision Component must be placed after Location
        activity.registerMapComponent(new NightVisionMapWidgetComponent());
        activity.registerMapComponent(new ResectionMapComponent());
        activity.registerMapComponent(new MetricReportMapComponent());

        // Fire up the state saver last for the internal components.

        activity.registerMapComponent(new StateSaver());

        activity.registerMapComponent(new GeopackageMapComponent());
        activity.registerMapComponent(new ModelMapComponent());
        activity.registerMapComponent(new RubberSheetMapComponent());
        activity.registerMapComponent(new HashtagMapComponent());

        // Vehicle shapes and overhead markers
        activity.registerMapComponent(new VehicleMapComponent());

        // Load up all of the external components, when this is complete it will trigger the
        // state saver to unroll all of the map components.

        activity.registerMapComponent(new PluginMapComponent());
    }
}
