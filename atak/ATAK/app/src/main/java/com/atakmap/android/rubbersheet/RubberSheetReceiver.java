
package com.atakmap.android.rubbersheet;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.widget.Toast;

import com.atakmap.android.hierarchy.HierarchyListReceiver;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import com.atakmap.android.ipc.DocumentedExtra;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.rubbersheet.data.RubberSheetUtils;
import com.atakmap.android.rubbersheet.maps.AbstractSheet;
import com.atakmap.android.rubbersheet.maps.RubberModel;
import com.atakmap.android.rubbersheet.maps.RubberSheetMapGroup;
import com.atakmap.android.rubbersheet.tool.RubberModelEditTool;
import com.atakmap.android.rubbersheet.tool.RubberSheetEditTool;
import com.atakmap.android.rubbersheet.ui.dropdown.AbstractSheetDropDown;
import com.atakmap.android.rubbersheet.ui.dropdown.RubberImageDropDown;
import com.atakmap.android.rubbersheet.ui.dropdown.RubberModelDropDown;
import com.atakmap.android.targetbubble.TargetBubbleReceiver;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.widgets.SeekBarControl;
import com.atakmap.android.widgets.SeekBarControlCompat;
import com.atakmap.app.R;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.util.ArrayList;
import java.util.List;

/**
 * Receiver for various rubber sheet actions
 */
public class RubberSheetReceiver extends BroadcastReceiver implements
        MapEventDispatcher.MapEventDispatchListener,
        PointMapItem.OnPointChangedListener {

    private static final String PACKAGE = "com.atakmap.android.rubbersheet.";
    private static final String SHOW_LIST = PACKAGE + "SHOW_LIST";
    private static final String TRANSPARENCY = PACKAGE + "TRANSPARENCY";
    private static final String ROTATE = PACKAGE + "ROTATE";
    private static final String EDIT = PACKAGE + "EDIT";
    private static final String FINE_ADJUST = PACKAGE + "FINE_ADJUST";
    private static final String SHOW_DETAILS = PACKAGE + "SHOW_DETAILS";

    private final MapView _mapView;
    private final Context _context;
    private final MapGroup _group;
    private final List<AbstractSheetDropDown> _dropDowns = new ArrayList<>();

    // Rubber sheet that is being focused on
    private AbstractSheet _activeSheet;

    // Temporary marker used for fine-adjustments
    private Marker _tempMarker;

    public RubberSheetReceiver(MapView mapView) {
        _mapView = mapView;
        _context = mapView.getContext();
        _group = mapView.getRootGroup();

        DocumentedExtra uidExtra = new DocumentedExtra("uid",
                "UID of the rubber image/model", false, String.class);
        DocumentedExtra[] e = new DocumentedExtra[] {
                uidExtra
        };
        DocumentedIntentFilter f = new DocumentedIntentFilter();
        f.addAction(SHOW_LIST, "Show list of rubber sheets");
        f.addAction(TRANSPARENCY, "Adjust transparency of rubber sheet", e);
        f.addAction(SHOW_DETAILS, "Show details for this rubber sheet", e);
        f.addAction(ROTATE, "Rotate rubber sheet using gesture", e);
        f.addAction(FINE_ADJUST, "Fine-adjust the rubber sheet position", e);
        f.addAction(EDIT, "Edit rubber sheet", e);
        AtakBroadcast.getInstance().registerReceiver(this, f);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.MAP_CLICK, this);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_REMOVED, this);

        _dropDowns.add(new RubberImageDropDown(mapView, _group));
        _dropDowns.add(new RubberModelDropDown(mapView, _group));
    }

    public void dispose() {
        for (AbstractSheetDropDown ddr : _dropDowns)
            ddr.dispose();
        AtakBroadcast.getInstance().unregisterReceiver(this);
        _mapView.getMapEventDispatcher().removeMapEventListener(
                MapEvent.MAP_CLICK, this);
        _mapView.getMapEventDispatcher().removeMapEventListener(
                MapEvent.ITEM_REMOVED, this);
        if (_tempMarker != null)
            _tempMarker.removeFromGroup();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null)
            return;

        // Show the full list of sheets
        if (action.equals(SHOW_LIST)) {
            ArrayList<String> paths = new ArrayList<>();
            paths.add(RubberSheetMapGroup.GROUP_NAME);
            Intent om = new Intent(HierarchyListReceiver.MANAGE_HIERARCHY);
            om.putStringArrayListExtra("list_item_paths", paths);
            om.putExtra("isRootList", true);
            AtakBroadcast.getInstance().sendBroadcast(om);
            return;
        }

        // Find the sheet that was selected
        String uid = intent.getStringExtra("uid");
        MapItem item = _mapView.getRootGroup().deepFindUID(uid);
        MapItem shape = ATAKUtilities.findAssocShape(item);
        if (!(shape instanceof AbstractSheet)) {
            Toast.makeText(_context, R.string.unable_to_find_sheet,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        final AbstractSheet sheet = (AbstractSheet) shape;

        // Adjust rubber sheet transparency
        switch (action) {
            case TRANSPARENCY:
                if (_activeSheet != null) {
                    if (_activeSheet == sheet)
                        return;
                    else
                        SeekBarControl.dismiss();
                }
                SeekBarControlCompat.show(new SeekBarControl.Subject() {
                    @Override
                    public int getValue() {
                        return (int) ((sheet.getAlpha() / 255f) * 100);
                    }

                    @Override
                    public void setValue(int value) {
                        sheet.setAlpha((int) ((value / 100f) * 255));
                    }

                    @Override
                    public void onControlDismissed() {
                        if (sheet.hasMetaValue("archive"))
                            sheet.persist(_mapView.getMapEventDispatcher(),
                                    null, RubberSheetReceiver.class);
                        _activeSheet = null;
                    }
                }, 5000L);
                _activeSheet = sheet;
                break;

            // Rotate rubber sheet using two-finger gesture
            case ROTATE:
                String id = sheet instanceof RubberModel
                        ? RubberModelEditTool.TOOL_NAME
                        : RubberSheetEditTool.TOOL_NAME;
                Bundle extras = new Bundle();
                extras.putString("uid", sheet.getUID());
                extras.putBoolean("rotate", true);
                ToolManagerBroadcastReceiver.getInstance().startTool(id,
                        extras);
                break;

            // Fine-adjust rubber sheet position
            case FINE_ADJUST:
                SeekBarControl.dismiss();
                GeoPoint point;
                if (item instanceof PointMapItem) {
                    point = ((PointMapItem) item).getPoint();
                    sheet.setTouchPoint(point);
                } else
                    point = sheet.getClickPoint();
                if (_tempMarker == null) {
                    _tempMarker = new Marker(point, "FINE_ADJUST");
                    _tempMarker.setMetaBoolean("addToObjList", false);
                    _tempMarker.setMetaBoolean("nevercot", true);
                    _tempMarker.setVisible(false);
                    _group.addItem(_tempMarker);
                } else {
                    _tempMarker.removeOnPointChangedListener(this);
                    _tempMarker.setPoint(point);
                }
                _tempMarker.addOnPointChangedListener(this);
                _activeSheet = sheet;
                Intent tb = new Intent(TargetBubbleReceiver.FINE_ADJUST);
                tb.putExtra("uid", _tempMarker.getUID());
                TargetBubbleReceiver.getInstance().onReceive(context, tb);
                break;

            // Show details drop-down
            case SHOW_DETAILS:
            case EDIT:
                for (AbstractSheetDropDown ddr : _dropDowns) {
                    if (ddr.show(sheet, action.equals(EDIT)))
                        break;
                }
                break;
        }
    }

    @Override
    public void onMapEvent(MapEvent event) {
        String type = event.getType();
        if (_activeSheet != null && (type.equals(MapEvent.MAP_CLICK)
                || event.getItem() == _activeSheet))
            SeekBarControl.dismiss();
    }

    @Override
    public void onPointChanged(PointMapItem pmi) {
        if (pmi == _tempMarker && _activeSheet != null) {
            GeoPoint s = _activeSheet.getClickPoint();
            GeoPoint e = _tempMarker.getPoint();
            GeoPointMetaData c = _activeSheet.getCenter();
            float[] ra = new float[2];
            Location.distanceBetween(s.getLatitude(), s.getLongitude(),
                    e.getLatitude(), e.getLongitude(), ra);
            GeoPointMetaData c2 = GeoPointMetaData.wrap(
                    GeoCalculations.pointAtDistance(c.get(), ra[1], ra[0]));
            RubberSheetUtils.getAltitude(c2);
            _activeSheet.move(c, c2);
            _activeSheet = null;
        }
    }
}
