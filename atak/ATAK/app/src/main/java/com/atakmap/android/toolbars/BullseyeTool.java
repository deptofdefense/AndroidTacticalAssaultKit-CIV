
package com.atakmap.android.toolbars;

import java.util.List;
import java.util.UUID;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PointF;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import com.atakmap.android.drawing.mapItems.DrawingCircle;
import com.atakmap.android.gui.CoordDialogView;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapItem.OnGroupChangedListener;
import com.atakmap.android.maps.MapItem.OnVisibleChangedListener;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.Marker.AugmentedKeyholeInfo;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.PointMapItem.OnPointChangedListener;
import com.atakmap.android.toolbar.ButtonTool;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.toolbar.widgets.TextContainer;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.widgets.AngleOverlayShape;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.Angle;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.NorthReference;
import com.atakmap.map.CameraController;
import com.atakmap.map.elevation.ElevationManager;
import com.atakmap.spatial.file.export.KMZFolder;
import com.ekito.simpleKML.model.Folder;

/**
 * TODO: Clean up bullseyes the same way we did with drawing circles
 * Why isn't there just a "Bullseye" or "BullseyeShape" class?
 * Why all the static listeners and methods?
 */
public class BullseyeTool extends ButtonTool implements
        MapEventDispatcher.MapEventDispatchListener {

    private static final String TAG = "BullseyeTool";
    public static final String TOOL_IDENTIFIER = "com.atakmap.android.toolbars.BullseyeTool";
    public static final String BULLSEYE_COT_TYPE = "u-r-b-bullseye";
    public static final int MAX_RADIUS = 1500000;

    protected final Context _context;
    protected GeoPointMetaData centerLoc = null;
    protected GeoPointMetaData edgeLoc = null;
    protected Marker centerMarker = null;
    protected Marker edgeMarker = null;

    BullseyeTool(MapView mapView, ImageButton button) {
        super(mapView, button, TOOL_IDENTIFIER);
        _context = mapView.getContext();
        ToolManagerBroadcastReceiver.getInstance().registerTool(
                TOOL_IDENTIFIER, this);
    }

    @Override
    public boolean onToolBegin(Bundle extras) {
        centerLoc = null;
        edgeLoc = null;
        centerMarker = null;
        edgeMarker = null;
        _mapView.getMapEventDispatcher().pushListeners();
        _mapView.getMapEventDispatcher().clearListeners(MapEvent.MAP_CLICK);
        _mapView.getMapEventDispatcher()
                .clearListeners(MapEvent.MAP_LONG_PRESS);
        _mapView.getMapEventDispatcher().clearListeners(MapEvent.ITEM_CLICK);
        _mapView.getMapEventDispatcher().clearListeners(
                MapEvent.ITEM_LONG_PRESS);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.MAP_CLICK, this);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.MAP_LONG_PRESS, this);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_CLICK, this);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_LONG_PRESS, this);
        _mapView.getMapTouchController().setToolActive(true);

        prompt(R.string.rb_bullseye);

        return true;
    }

    @Override
    public void onMapEvent(MapEvent event) {

        if (event.getType().equals(MapEvent.MAP_CLICK)
                || event.getType().equals(MapEvent.ITEM_CLICK)) {
            PointF pt = event.getPointF();

            if (centerLoc == null) {
                centerLoc = _mapView.inverseWithElevation(pt.x, pt.y);
                if (event.getType().equals(MapEvent.ITEM_CLICK)) {
                    if (event.getItem() != null
                            && event.getItem() instanceof Marker) {
                        centerMarker = (Marker) event.getItem();
                        centerLoc = centerMarker.getGeoPointMetaData();
                    }
                }

                _mapView.getMapEventDispatcher().removeMapEventListener(
                        MapEvent.MAP_LONG_PRESS, this);

                TextContainer.getInstance().closePrompt();
                prompt(R.string.rb_bullseye_edge);
            } else {
                //check max length
                GeoPointMetaData edgePt = _mapView.inverseWithElevation(pt.x,
                        pt.y);
                if (event.getType().equals(MapEvent.ITEM_CLICK)) {
                    if (event.getItem() != null
                            && event.getItem() instanceof Marker) {
                        edgeMarker = (Marker) event.getItem();
                        edgePt = edgeMarker.getGeoPointMetaData();
                    }
                }
                if (edgePt.get().distanceTo(
                        centerLoc.get()) > MAX_RADIUS) {
                    Toast.makeText(_context, R.string.bullseye_radius_large,
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                edgeLoc = edgePt;

                if (centerMarker != null && edgeMarker != null) {
                    makeRabWidget(centerMarker, edgeMarker);
                }

                finishTool(centerLoc, centerLoc.get().distanceTo(edgeLoc.get()),
                        null);
            }
        } else if (event.getType().equals(MapEvent.MAP_LONG_PRESS)) {
            GeoPointMetaData point = _mapView.inverseWithElevation(
                    event.getPointF().x, event.getPointF().y);
            displayCoordinateDialog(point);
        }
    }

    private void makeRabWidget(PointMapItem pt1, PointMapItem pt2) {
        if (pt1 != null && pt2 != null) {
            String rabUUID = UUID.randomUUID().toString();
            RangeAndBearingMapItem rab = RangeAndBearingMapItem
                    .createOrUpdateRABLine(rabUUID, pt1, pt2);
            if (rab != null) {
                RangeAndBearingMapComponent.getGroup().addItem(rab);
                rab.persist(_mapView.getMapEventDispatcher(), null,
                        this.getClass());
            }
        }
    }

    private void finishTool(GeoPointMetaData center, double radius,
            String coordFmt) {
        Marker centerMarker = new Marker(center, UUID.randomUUID()
                .toString());
        centerMarker.setType(BULLSEYE_COT_TYPE);
        centerMarker.setMetaBoolean("archive", true);
        centerMarker.setMetaString("how", "h-g-i-g-o");
        centerMarker.setMetaBoolean("ignoreOffscreen", true);
        centerMarker.setMovable(true);
        if (coordFmt != null) {
            centerMarker.setMetaString("coordFormat", coordFmt);
        }
        centerMarker.setMetaString("entry", "user");
        _mapView.getRootGroup().findMapGroup("Range & Bearing")
                .addItem(centerMarker);

        TextContainer.getInstance().closePrompt();
        createBullseye(centerMarker, radius);

        Intent i = new Intent(
                BullseyeDropDownReceiver.DROPDOWN_TOOL_IDENTIFIER);
        i.putExtra("edit", true);
        i.putExtra("marker_uid", centerMarker.getUID());
        AtakBroadcast.getInstance().sendBroadcast(i);

        requestEndTool();
    }

    public void saveBullseyeMarker(Marker bullseyeMarker) {
        bullseyeMarker.persist(_mapView.getMapEventDispatcher(),
                null, this.getClass());
    }

    static AngleOverlayShape createBullseye(Marker centerMarker) {
        return createBullseye(centerMarker, 100);
    }

    static AngleOverlayShape createBullseye(Marker centerMarker,
            double radiusInMeters) {
        MapGroup rabGroup = RangeAndBearingMapComponent.getGroup();
        MapView mv = MapView.getMapView();
        if (rabGroup == null || mv == null || centerMarker == null)
            return null;

        //check that marker doesnt already have a bullseye associated
        if (centerMarker.hasMetaValue("bullseyeUID")) {
            MapItem mi = MapGroup.deepFindItemWithMetaString(
                    MapView._mapView.getRootGroup(), "uid",
                    centerMarker.getMetaString("bullseyeUID", ""));
            if (mi instanceof AngleOverlayShape)
                return (AngleOverlayShape) mi;
            else
                return null;
        }

        int numBullseye = getNumBullseye();
        String title = "Bullseye " + numBullseye;
        String bullseyeUID = UUID.randomUUID().toString();
        centerMarker.setMetaString("bullseyeUID", bullseyeUID);

        if (centerMarker.getType().contentEquals(BULLSEYE_COT_TYPE)) {
            centerMarker.setTitle(title);
        } else {
            centerMarker.setMetaBoolean("bullseyeOverlay", true);
        }
        centerMarker.setAugmentedKeyholeInfo(aki);
        AngleOverlayShape aos = new AngleOverlayShape(bullseyeUID);
        aos.setCenter(centerMarker.getGeoPointMetaData());
        aos.setRadius(radiusInMeters, Span.METER);

        aos.setSimpleSpokeView(true);
        aos.setVisible(centerMarker.getVisible());
        aos.setProjectionProportion(true);
        aos.setTitle(title);
        aos.setMetaString("iconUri", ATAKUtilities.getResourceUri(
                R.drawable.bullseye));

        Context context = mv.getContext();
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(context);
        NorthReference ref = NorthReference.findFromValue(Integer.parseInt(
                prefs.getString("rab_north_ref_pref",
                        String.valueOf(NorthReference.MAGNETIC.getValue()))));
        if (ref == NorthReference.TRUE) {
            aos.setTrueAzimuth();
        } else if (ref == NorthReference.MAGNETIC) {
            aos.setMagneticAzimuth();
        } else {
            aos.setGridAzimuth();
        }

        Angle unit = Angle.findFromValue(
                Integer.parseInt(prefs.getString("rab_brg_units_pref",
                        String.valueOf(0))));
        if (unit == Angle.MIL) {
            aos.setBearingUnits(false);
        } else {
            aos.setBearingUnits(true);
        }

        aos.setMetaBoolean("addToObjList", false);
        aos.setCenterMarker(centerMarker);

        centerMarker.addOnPointChangedListener(centerMoveListener);
        centerMarker.addOnGroupChangedListener(centerGroupChangeListener);
        centerMarker.addOnVisibleChangedListener(centerVisibilityListener);

        if (centerMarker.getType().equals(BULLSEYE_COT_TYPE))
            rabGroup.addItem(aos);
        else
            mv.getRootGroup().addItem(aos);

        centerMarker.persist(mv.getMapEventDispatcher(), null,
                centerMarker.getClass());

        return aos;
    }

    /**
     * Create a Bullseye overlay from the CotDetail saved to the center marker
     *
     * @param centerMarker - the center marker for the Bullseye
     * @param detail - the Detail that contains all the parameters of the Bullseye
     * @return - the resulting {@link AngleOverlayShape}
     */
    static AngleOverlayShape createOrUpdateBullseye(Marker centerMarker,
            CotDetail detail) {
        MapGroup rabGroup = RangeAndBearingMapComponent.getGroup();
        MapView mv = MapView.getMapView();
        if (rabGroup == null || mv == null || centerMarker == null)
            return null;
        boolean existingAOS = false;
        String bullseyeUID = detail.getAttribute("bullseyeUID");

        //check for existing Overlay
        AngleOverlayShape aos;
        MapItem mi = MapGroup.deepFindItemWithMetaString(
                MapView._mapView.getRootGroup(), "uid",
                bullseyeUID);
        if (mi instanceof AngleOverlayShape) {
            aos = (AngleOverlayShape) mi;
            existingAOS = true;
        } else {
            aos = new AngleOverlayShape(bullseyeUID);
            aos.setMetaBoolean("addToObjList", false);
        }

        String title = detail.getAttribute("title");
        String edgeToCenter = detail.getAttribute("edgeToCenter");
        String distanceString = detail.getAttribute("distance");
        String distUnitString = detail.getAttribute("distanceUnits");
        String mils = detail.getAttribute("mils");
        boolean hasRangeRings = Boolean.parseBoolean(detail
                .getAttribute("hasRangeRings"));
        String ringDistString = detail.getAttribute("ringDist");
        String ringDistUnitString = detail.getAttribute("ringDistUnits");
        String ringNumString = detail.getAttribute("ringNum");
        NorthReference bearingRef = NorthReference.MAGNETIC;
        if (detail.getAttribute("bearingRef") != null
                &&
                NorthReference
                        .findFromAbbrev(
                                detail.getAttribute("bearingRef")) != null)
            bearingRef = NorthReference.findFromAbbrev(detail
                    .getAttribute("bearingRef"));

        double radiusInMeters = 0.0;
        double ringDist = 0.0;
        int ringNum = 1;
        try {
            radiusInMeters = Double.parseDouble(distanceString);
            ringDist = Double.parseDouble(ringDistString);
            ringNum = Integer.parseInt(ringNumString);
        } catch (Exception ignore) {
        }

        Span distUnits = Span.findFromAbbrev(distUnitString);
        if (distUnits == null)
            distUnits = Span.METER;

        aos.setCenter(centerMarker.getGeoPointMetaData());
        aos.setRadius(SpanUtilities.convert(radiusInMeters, Span.METER,
                distUnits), distUnits);

        aos.setSimpleSpokeView(true);
        aos.setVisible(centerMarker.getVisible());
        aos.setProjectionProportion(true);
        aos.setBearingUnits(!Boolean.parseBoolean(mils));
        if (bearingRef == NorthReference.TRUE)
            aos.setTrueAzimuth();
        else if (bearingRef == NorthReference.MAGNETIC)
            aos.setMagneticAzimuth();
        else
            aos.setGridAzimuth();
        aos.setEdgeToCenterDirection(Boolean.parseBoolean(edgeToCenter));
        aos.setTitle(title);
        aos.setMetaString("iconUri", ATAKUtilities.getResourceUri(
                R.drawable.bullseye));

        //determine if range rings are being edited or added to a new AOS
        if (existingAOS) {
            RangeCircle rabCircle = null;
            MapItem rings = rabGroup.deepFindUID(
                    aos.getMetaString("rangeRingUID", ""));
            if (rings instanceof RangeCircle)
                rabCircle = (RangeCircle) rings;

            if (hasRangeRings) {
                if (rabCircle != null) {
                    //edit the current range rings
                } else {
                    //wrap range rings
                    rabCircle = new RangeCircle(mv);
                }
                rabCircle.setCenterMarker(centerMarker);
                rabCircle.setRadius(ringDist);
                rabCircle.setNumRings(ringNum);

                rabCircle = editRaBcircle(aos, rabCircle,
                        centerMarker.getVisible() && centerMarker
                                .getMetaBoolean("rangeRingVisible", false));
                rabGroup.addItem(rabCircle);

            } else if (rabCircle != null) {
                //clear out old range rings
                rabGroup.removeItem(rabCircle);
            }

        } else {
            if (hasRangeRings) {
                RangeCircle rabCircle = new RangeCircle(mv);
                rabCircle.setCenterMarker(centerMarker);
                rabCircle.setRadius(ringDist);
                rabCircle.setNumRings(ringNum);
                rabCircle.setRadius(ringDist);
                rabCircle = editRaBcircle(aos, rabCircle,
                        centerMarker.getVisible() && centerMarker
                                .getMetaBoolean("rangeRingVisible", false));
                rabGroup.addItem(rabCircle);
            }
        }

        aos.setCenterMarker(centerMarker);

        centerMarker.addOnPointChangedListener(centerMoveListener);
        centerMarker.addOnGroupChangedListener(centerGroupChangeListener);
        centerMarker.addOnVisibleChangedListener(centerVisibilityListener);
        centerMarker.setAugmentedKeyholeInfo(aki);

        if (centerMarker.getType().equals(BULLSEYE_COT_TYPE))
            rabGroup.addItem(aos);
        else
            mv.getRootGroup().addItem(aos);

        return aos;
    }

    private static RangeCircle editRaBcircle(AngleOverlayShape aos,
            RangeCircle rabCircle, boolean visible) {
        MapView mv = MapView.getMapView();
        if (aos.isShowingEdgeToCenter())
            rabCircle.setColor(Color.RED); //RED
        else
            rabCircle.setColor(Color.GREEN); //GREEN
        rabCircle.setStrokeWeight(BullseyeDropDownReceiver.STROKE_WEIGHT);
        rabCircle.setStyle(BullseyeDropDownReceiver.STYLE);
        rabCircle.setClickable(false);
        rabCircle.setMetaBoolean("editable", false);
        rabCircle.setMetaBoolean("addToObjList", false);
        if (mv != null)
            rabCircle.refresh(mv.getMapEventDispatcher(), null,
                    rabCircle.getClass());

        rabCircle.setVisible(visible);
        aos.setMetaString("rangeRingUID", rabCircle.getUID());
        return rabCircle;
    }

    /**
     * Show the dialog to manually enter the center coordinate
     *
     * @param centerPoint - the point to populate the views with
     */
    public void displayCoordinateDialog(GeoPointMetaData centerPoint) {
        final Pair<AlertDialog.Builder, CoordDialogView> builderView = setupCoordDialog();
        if (builderView == null)
            return;
        AlertDialog.Builder builder = builderView.first;
        builder.setPositiveButton(_context.getString(R.string.ok),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {

                        GeoPointMetaData dialogGP = builderView.second
                                .getPoint();
                        if (dialogGP == null || !dialogGP.get().isValid())
                            return;

                        finishTool(dialogGP, 100, builderView.second
                                .getCoordFormat().getDisplayName());
                    }
                });
        builderView.second.setParameters(centerPoint, _mapView
                .getPoint(),
                CoordinateFormat.MGRS);

        builder.show();
    }

    /**
     * Show the dialog to manually enter the center coordinate
     *
     * @param aosCenterMarker - the point to populate the views with
     */
    public static void displayCoordinateDialog(final Marker aosCenterMarker) {
        final MapView mv = MapView.getMapView();
        if (mv == null)
            return;

        final Pair<AlertDialog.Builder, CoordDialogView> builderView = setupCoordDialog();
        if (builderView == null)
            return;

        AlertDialog.Builder builder = builderView.first;
        builder.setPositiveButton(
                mv.getResources().getString(R.string.ok),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        GeoPointMetaData gp = builderView.second.getPoint();
                        if (gp == null || !gp.get().isValid())
                            return;

                        // always look up the elevation since the point has been moved
                        // ATAK-7066 If Bullseye center location is changed in Details, elevation is not updated

                        gp = ElevationManager.getElevationMetadata(gp.get());

                        aosCenterMarker.setMetaString("coordFormat",
                                builderView.second.getCoordFormat()
                                        .getDisplayName());
                        Intent showDetails = new Intent();
                        showDetails.setAction(
                                "com.atakmap.android.maps.SHOW_DETAILS");
                        showDetails.putExtra("uid", aosCenterMarker.getUID());
                        AtakBroadcast.getInstance().sendBroadcast(showDetails);
                        aosCenterMarker.setPoint(gp);
                        CameraController.Programmatic.panTo(
                                mv.getRenderer3(), gp.get(), true);
                    }
                });
        builderView.second.setParameters(aosCenterMarker.getGeoPointMetaData(),
                mv.getPoint(), CoordinateFormat.MGRS);

        builder.show();
    }

    private static Pair<AlertDialog.Builder, CoordDialogView> setupCoordDialog() {
        MapView mv = MapView.getMapView();
        if (mv == null)
            return null;

        AlertDialog.Builder builder = new AlertDialog.Builder(mv.getContext());
        LayoutInflater inflater = LayoutInflater.from(mv.getContext());

        final CoordDialogView coordView = (CoordDialogView) inflater.inflate(
                R.layout.draper_coord_dialog, mv, false);
        coordView.findViewById(R.id.coordDialogElevationView).setVisibility(
                View.GONE);
        builder.setTitle(mv.getResources().getString(R.string.rb_coord_title))
                .setView(coordView)
                .setNegativeButton(R.string.cancel, null);
        return new Pair<>(builder, coordView);
    }

    private static int getNumBullseye() {
        MapGroup rabGroup = RangeAndBearingMapComponent.getGroup();
        if (rabGroup == null)
            return 1;

        List<MapItem> list = rabGroup.deepFindItems("bullseye", "true");
        if (list == null || list.isEmpty())
            return 1;
        else
            return list.size() + 1;
    }

    @Override
    public void onToolEnd() {
        _mapView.getMapTouchController().setToolActive(false);
        _mapView.getMapEventDispatcher().clearListeners();
        _mapView.getMapEventDispatcher().popListeners();
        TextContainer.getInstance().closePrompt();
    }

    private static final OnPointChangedListener centerMoveListener = new OnPointChangedListener() {
        @Override
        public void onPointChanged(PointMapItem item) {
            //move the angle overlay
            MapItem mi = MapGroup.deepFindItemWithMetaString(
                    MapView._mapView.getRootGroup(), "uid",
                    item.getMetaString("bullseyeUID", ""));
            if (mi instanceof AngleOverlayShape) {
                AngleOverlayShape compassDial = (AngleOverlayShape) mi;
                compassDial.setCenter(item.getGeoPointMetaData());
            }
        }
    };

    private static final OnVisibleChangedListener centerVisibilityListener = new OnVisibleChangedListener() {
        @Override
        public void onVisibleChanged(MapItem item) {
            MapGroup rabGroup = RangeAndBearingMapComponent.getGroup();
            if (rabGroup == null)
                return;
            //move the angle overlay
            MapItem mi = MapGroup.deepFindItemWithMetaString(
                    MapView._mapView.getRootGroup(), "uid",
                    item.getMetaString("bullseyeUID", ""));
            if (!(mi instanceof AngleOverlayShape))
                return;
            AngleOverlayShape aos = (AngleOverlayShape) mi;
            aos.setVisible(item.getVisible());
            mi = rabGroup.deepFindUID(aos.getMetaString(
                    "rangeRingUID", ""));
            if (mi instanceof RangeCircle) {
                mi.setVisible(item.getVisible() && item.getMetaBoolean(
                        "rangeRingVisible", false));
            }
        }
    };
    private static final OnGroupChangedListener centerGroupChangeListener = new OnGroupChangedListener() {
        @Override
        public void onItemAdded(MapItem item, MapGroup group) {

        }

        @Override
        public void onItemRemoved(MapItem item, MapGroup group) {
            if (item.hasMetaValue("bullseyeUID")) {
                removeOverlay((Marker) item, false);
            }
        }
    };

    /**
     * Remove all the listeners for the Compass overlay on the map item
     *
     * @param centerItem - the item to remove the listeners from
     */
    private static void removeListeners(MapItem centerItem) {
        centerItem.removeOnGroupChangedListener(centerGroupChangeListener);
        ((Marker) centerItem)
                .removeOnPointChangedListener(centerMoveListener);
        centerItem
                .removeOnVisibleChangedListener(centerVisibilityListener);
    }

    public static void removeOverlay(Marker centerMarker,
            boolean removeCenter) {
        removeListeners(centerMarker);

        MapItem mi = MapGroup.deepFindItemWithMetaString(
                MapView._mapView.getRootGroup(), "uid",
                centerMarker.getMetaString("bullseyeUID", ""));

        MapGroup rabGroup = RangeAndBearingMapComponent.getGroup();
        MapItem rings = null;
        if (rabGroup != null && mi != null)
            rings = rabGroup.deepFindUID(mi.getMetaString("rangeRingUID", ""));

        //remove rings, if it had range rings
        if (rings instanceof DrawingCircle)
            rings.removeFromGroup();

        if (mi instanceof AngleOverlayShape)
            mi.removeFromGroup();

        if (centerMarker.getType().contentEquals(BULLSEYE_COT_TYPE)) {
            if (removeCenter)
                centerMarker.removeFromGroup();
        } else {
            centerMarker.removeMetaData("bullseyeUID");
            centerMarker.removeMetaData("bullseyeOverlay");
        }
    }

    synchronized protected void prompt(int stringId) {
        TextContainer.getInstance().displayPrompt(_context
                .getString(stringId));
    }

    private static AngleOverlayShape getBullseyeShape(Marker m) {
        MapItem mi = MapView.getMapView()
                .getMapItem(m.getMetaString("bullseyeUID", null));
        return mi instanceof AngleOverlayShape ? (AngleOverlayShape) mi : null;
    }

    /**
     * Used to attach bullseye shape to a marker when exporting to KML/KMZ
     */
    private static final AugmentedKeyholeInfo aki = new AugmentedKeyholeInfo() {
        @Override
        public Folder toKml(Folder folder, Marker m) {
            AngleOverlayShape bullseye = getBullseyeShape(m);
            if (bullseye == null)
                return folder;

            try {
                Folder bFolder = (Folder) bullseye.toObjectOf(Folder.class,
                        null);
                if (bFolder != null) {
                    folder.getFeatureList().addAll(bFolder.getFeatureList());
                    folder.getStyleSelector()
                            .addAll(bFolder.getStyleSelector());
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to export bullseye to KML", e);
            }

            return folder;
        }

        @Override
        public KMZFolder toKmz(KMZFolder folder, Marker m) {
            AngleOverlayShape bullseye = getBullseyeShape(m);
            if (bullseye == null)
                return folder;

            try {
                KMZFolder bFolder = (KMZFolder) bullseye
                        .toObjectOf(KMZFolder.class, null);
                if (bFolder != null)
                    folder.getFeatureList().add(bFolder);
            } catch (Exception e) {
                Log.e(TAG, "Failed to export bullseye to KMZ", e);
            }

            return folder;
        }
    };
}
