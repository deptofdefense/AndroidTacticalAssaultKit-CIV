
package com.atakmap.android.drawing;

import android.content.Context;
import android.content.Intent;

import com.atakmap.android.cot.detail.CotDetailHandler;
import com.atakmap.android.cot.detail.CotDetailManager;
import com.atakmap.android.cot.importer.CotImporterManager;
import com.atakmap.android.cot.importer.MapItemImporter;
import com.atakmap.android.cot.importer.MarkerImporter;
import com.atakmap.android.drawing.details.ShapeMsdDetailHandler;
import com.atakmap.android.drawing.details.TacticalOverlayDetailHandler;
import com.atakmap.android.cotdetails.extras.ExtraDetailsManager;
import com.atakmap.android.drawing.importer.DrawingCircleImporter;
import com.atakmap.android.drawing.importer.DrawingShapeImporter;
import com.atakmap.android.drawing.importer.MultiPolylineImporter;
import com.atakmap.android.drawing.importer.RectangleImporter;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import android.os.Handler;

import com.atakmap.android.drawing.mapItems.DrawingRectangle;
import com.atakmap.android.drawing.mapItems.DrawingShape;
import com.atakmap.android.drawing.mapItems.GenericPoint;
import com.atakmap.android.editableShapes.EditablePolylineReceiver;
import com.atakmap.android.importexport.ImporterManager;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.DefaultMapGroup;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.overlay.DefaultMapGroupOverlay;
import com.atakmap.android.widgets.AbstractWidgetMapComponent;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides the toolbar for drawing on the map.  This includes circles,
 * rectangles, lines, and polylines (john madden style).
 */
public class DrawingToolsMapComponent extends AbstractWidgetMapComponent {

    private DrawingToolsMapReceiver _drawingMapReceiver;
    private static MapGroup drawingGroup;
    private static final Object lock = new Object();

    private final List<MapItemImporter> _importers = new ArrayList<>();
    private final List<CotDetailHandler> _detailHandlers = new ArrayList<>();

    @SuppressWarnings("unused")
    private DrawingToolsToolbar _drawingToolbarBroadcastReceiver;

    private ExtraDetailsManager _detailsMgr;

    @Override
    protected void onCreateWidgets(Context context, Intent intent,
            MapView view) {
        try {
            synchronized (lock) {
                drawingGroup = view.getRootGroup().findMapGroup(
                        "Drawing Objects");
                if (drawingGroup == null) {
                    drawingGroup = new DefaultMapGroup("Drawing Objects");
                    drawingGroup.setMetaBoolean("ignoreOffscreen", true);
                    drawingGroup.setMetaBoolean("permaGroup", true);
                    String iconUri = "android.resource://"
                            + view.getContext().getPackageName()
                            + "/" + R.drawable.ic_menu_drawing;
                    view.getMapOverlayManager()
                            .addShapesOverlay(
                                    new DefaultMapGroupOverlay(view,
                                            drawingGroup,
                                            iconUri));
                }
            }

            _drawingMapReceiver = new DrawingToolsMapReceiver(view,
                    drawingGroup, context);
            DocumentedIntentFilter f = new DocumentedIntentFilter();
            f.addAction(DrawingToolsMapReceiver.DELETE_ACTION,
                    "Intent to delete a shape, requires a UID string extra to be passed in. ");
            f.addAction(DrawingToolsMapReceiver.EDIT_ACTION);
            f.addAction(DrawingToolsMapReceiver.DETAILS_ACTION,
                    "Intent to open the details page for a shpe, requires a UID string extra to be passed in.");
            f.addAction(DrawingToolsMapReceiver.ZOOM_ACTION,
                    "Intent to zoom to a shape, requires a UID string extra to be passed in.");
            f.addAction(DrawingToolsMapReceiver.LABEL_ACTION,
                    "Intent to toggle labels on a shape, requires a UID string extra to be passed in.");
            f.addAction(DrawingToolsMapReceiver.IMPORT_KML_ACTION,
                    "Intent to launch importing of data from a KML file, requires a file path string extra to be passed in.");
            f.addAction(DrawingToolsMapReceiver.MSD_ACTION,
                    "Intent to set or remove a minimum safe distance boundary around a shape.");

            f.addCategory("com.atakmap.android.maps.INTEGRATION");
            f.addAction("com.atakmap.android.maps.TOOLSELECTOR_READY",
                    "Intent to notify receivers that the tool selector is ready to launch tools.");

            AtakBroadcast.getInstance().registerReceiver(_drawingMapReceiver,
                    f);

            // Drawing circles
            _importers.add(new DrawingCircleImporter(view, drawingGroup));

            // Drawing ellipse as a stepchild of circles
            _importers.add(
                    new DrawingCircleImporter(view, drawingGroup, "u-d-c-e"));

            // Rectangles
            _importers.add(new RectangleImporter(view, drawingGroup));

            // Freeform shapes
            _importers.add(new DrawingShapeImporter(view, drawingGroup));

            // Multi-polylines
            _importers.add(new MultiPolylineImporter(view, drawingGroup));

            // Generic points (legacy)
            _importers.add(new MarkerImporter(view, drawingGroup,
                    "u-d-p", false));

            _detailHandlers.add(new TacticalOverlayDetailHandler());
            _detailHandlers.add(new ShapeMsdDetailHandler(view));

            for (CotDetailHandler handler : _detailHandlers)
                CotDetailManager.getInstance().registerHandler(handler);

            for (MapItemImporter importer : _importers)
                CotImporterManager.getInstance().registerImporter(importer);

            final DrawingToolsKMLImporter importer = new DrawingToolsKMLImporter(
                    view, drawingGroup,
                    new Handler());

            importer.registerImportFactory(
                    new DrawingRectangle.KmlDrawingRectangleImportFactory());
            importer.registerImportFactory(
                    new GenericPoint.KmlGenericPointImportFactory(
                            view));
            importer.registerImportFactory(
                    new DrawingShape.KmlDrawingShapeImportFactory(view));

            //MarshalManager.registerMarshal(new DrawingCotEventMarshal());
            ImporterManager.registerImporter(importer);

            // Initialize association set common code
            // Contains EditablePolylineMoveTool to move a closed AssocationSet with the area marker
            EditablePolylineReceiver.init(view, context);

            _drawingToolbarBroadcastReceiver = new DrawingToolsToolbar(view,
                    drawingGroup, context,
                    _drawingMapReceiver);

            _detailsMgr = new ExtraDetailsManager();

        } catch (Exception ex) {
            Log.e(TAG, "error: ", ex);
        }
    }

    @Override
    protected void onDestroyWidgets(Context context, MapView view) {
        for (MapItemImporter importer : _importers)
            CotImporterManager.getInstance().unregisterImporter(importer);
        for (CotDetailHandler handler : _detailHandlers)
            CotDetailManager.getInstance().unregisterHandler(handler);
        if (_drawingMapReceiver != null)
            AtakBroadcast.getInstance().unregisterReceiver(_drawingMapReceiver);
        _drawingMapReceiver = null;
        if (_drawingToolbarBroadcastReceiver != null) {
            AtakBroadcast.getInstance().unregisterReceiver(
                    _drawingToolbarBroadcastReceiver);
            _drawingToolbarBroadcastReceiver.dispose();
        }
        _drawingToolbarBroadcastReceiver = null;
    }

    public static MapGroup getGroup() {
        return drawingGroup;
    }

}
