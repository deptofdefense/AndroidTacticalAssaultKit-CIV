
package com.atakmap.android.user.icon;

import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.drawable.Drawable;

import com.atakmap.android.maps.MapItem;

import com.atakmap.android.bpha.BPHARectangleCreator;
import com.atakmap.android.bpha.BattlePositionHoldingArea;
import com.atakmap.android.bpha.BattlePositionLayoutHandler;
import com.atakmap.android.bpha.BattlePositionLayoutHandler.BattlePositionSelectorEventHandler;
import com.atakmap.android.cot.detail.SensorDetailHandler;
import com.atakmap.android.drawing.mapItems.DrawingRectangle;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.toolbar.Tool;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.user.CustomNamingView;
import com.atakmap.android.user.EnterLocationTool;
import com.atakmap.android.user.PlacePointTool;
import com.atakmap.android.user.icon.IconPallet.CreatePointException;
import com.atakmap.app.R;
import com.atakmap.app.system.FlavorProvider;
import com.atakmap.app.system.SystemComponentLoader;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.util.HashMap;
import java.util.Map;

import gov.tak.api.annotation.ModifierApi;

/**
 * 
 * 
 */
public class MissionSpecificPalletFragment extends Fragment implements
        BattlePositionSelectorEventHandler {

    private static final String TAG = "MissionSpecificPalletFragment";

    private final Map<Integer, SubType> _pSubTypeMap = new HashMap<>();
    LinearLayout habpButtonLayout;

    private View selectedButton = null;
    private CustomNamingView _customNamingView;

    private ViewGroup subView;
    private ViewGroup subViewAlt;

    private String selectedType = "";

    /**
     * This interface describes a capability that can be installed as part of slots 1,2,3,4 of the
     * Mission package framgment.   
     */
    public interface Slot {
        Drawable getIcon();

        String getLabel();

        String getType();

        void afterAction(Marker m);
    }

    @ModifierApi(since = "4.6", target = "4.9", modifiers = {
            "private"
    })
    protected final static Map<Integer, Slot> _slotMap = new HashMap<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    private static View v;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        v = inflater.inflate(R.layout.enter_location_mission_specific,
                container,
                false);

        _customNamingView = new CustomNamingView(
                CustomNamingView.DEFAULT);
        LinearLayout customHolder = v
                .findViewById(R.id.customHolder);
        customHolder.addView(_customNamingView.getMainView());

        //set up the buttons for the different types
        LinearLayout cpButtonLayout = v
                .findViewById(R.id.contact_point);
        ImageView cpImage = cpButtonLayout
                .findViewById(R.id.enter_location_group_childImage);
        cpImage.setImageDrawable(getResources().getDrawable(
                R.drawable.pointtype_contact_default));
        TextView cpText = cpButtonLayout
                .findViewById(R.id.enter_location_group_childLabel);
        cpText.setText(R.string.contactPoint);

        LinearLayout ipButtonLayout = v
                .findViewById(R.id.initial_point);
        ImageView ipImage = ipButtonLayout
                .findViewById(R.id.enter_location_group_childImage);
        ipImage.setImageDrawable(getResources().getDrawable(
                R.drawable.pointtype_initial_default));
        TextView ipText = ipButtonLayout
                .findViewById(R.id.enter_location_group_childLabel);
        ipText.setText(R.string.initialPoint);

        habpButtonLayout = v.findViewById(R.id.habp_point);
        ImageView habpImage = habpButtonLayout
                .findViewById(R.id.enter_location_group_childImage);
        habpImage.setImageDrawable(getResources().getDrawable(
                R.drawable.pointtype_habp_default));
        TextView habpText = habpButtonLayout
                .findViewById(R.id.enter_location_group_childLabel);
        habpText.setText(R.string.bp_ha);

        LinearLayout waypointButtonLayout = v
                .findViewById(R.id.waypoint_point);
        ImageView waypointImage = waypointButtonLayout
                .findViewById(R.id.enter_location_group_childImage);
        waypointImage.setImageDrawable(getResources().getDrawable(
                R.drawable.pointtype_waypoint_default));
        TextView waypointText = waypointButtonLayout
                .findViewById(R.id.enter_location_group_childLabel);
        waypointText.setText(R.string.waypt);

        LinearLayout sensorButtonLayout = v
                .findViewById(R.id.sensor_point);
        ImageView sensorImage = sensorButtonLayout
                .findViewById(R.id.enter_location_group_childImage);
        sensorImage.setImageDrawable(getResources().getDrawable(
                R.drawable.pointtype_sensor_default));
        TextView sensorText = sensorButtonLayout
                .findViewById(R.id.enter_location_group_childLabel);
        sensorText.setText(R.string.sensor);

        LinearLayout opButtonLayout = v
                .findViewById(R.id.op_point);
        ImageView opImage = opButtonLayout
                .findViewById(R.id.enter_location_group_childImage);
        opImage.setImageDrawable(getResources().getDrawable(
                R.drawable.ic_menu_binos));
        TextView opText = opButtonLayout
                .findViewById(R.id.enter_location_group_childLabel);
        opText.setText(R.string.op);

        ipButtonLayout.setOnClickListener(buttonClickListener);
        cpButtonLayout.setOnClickListener(buttonClickListener);
        habpButtonLayout.setOnClickListener(buttonClickListener);
        waypointButtonLayout.setOnClickListener(buttonClickListener);
        sensorButtonLayout.setOnClickListener(buttonClickListener);
        opButtonLayout.setOnClickListener(buttonClickListener);

        subView = v.findViewById(R.id.subtypeView);
        subViewAlt = v.findViewById(R.id.subtypeViewAlt);

        new BattlePositionLayoutHandler(inflater, subView,
                this);

        // check to see what type is being built - capabilities wise
        FlavorProvider fp = SystemComponentLoader.getFlavorProvider();
        if (fp == null || !fp.hasMilCapabilities()) {
            ipButtonLayout.setVisibility(View.GONE);
            cpButtonLayout.setVisibility(View.GONE);
            habpButtonLayout.setVisibility(View.GONE);
        }

        initSlot(R.id.slot1);
        initSlot(R.id.slot2);
        initSlot(R.id.slot3);
        initSlot(R.id.slot4);

        init();

        return v;
    }

    /**
     * Allow for other utilities to register up to 4 additional point dropper
     * capabilities within the mission tab.
     * @param slotOffset the current slot to register a pallet icon [0..3]
     * @param slot the image to place in the slot
     */
    synchronized static public void registerSlot(int slotOffset, Slot slot) {
        if (slotOffset < 0 || slotOffset > 3)
            return;
        int res;
        if (slotOffset == 0)
            res = R.id.slot1;
        else if (slotOffset == 1)
            res = R.id.slot2;
        else if (slotOffset == 2)
            res = R.id.slot3;
        else
            res = R.id.slot4;

        _slotMap.put(res, slot);

        if (v != null) {
            LinearLayout slotButtonLayout = v
                    .findViewById(res);
            slotButtonLayout.setVisibility(View.VISIBLE);
        }

    }

    synchronized private void initSlot(int res) {
        Slot slot = _slotMap.get(res);
        if (slot == null || v == null)
            return;

        LinearLayout slotButtonLayout = v
                .findViewById(res);
        ImageView slotImage = slotButtonLayout
                .findViewById(R.id.enter_location_group_childImage);
        slotImage.setImageDrawable(slot.getIcon());
        TextView slotText = slotButtonLayout
                .findViewById(R.id.enter_location_group_childLabel);
        slotText.setText(slot.getLabel());
        slotButtonLayout.setOnClickListener(buttonClickListener);
        slotButtonLayout.setVisibility(View.VISIBLE);
        _pSubTypeMap.put(res, new SubType(slot.getType()));
    }

    /**
     * Allows for a dynamically registered capability within the pallet to be unregistered.
     * @param slotOffset the current slot to register a pallet icon [0..3]
     */
    synchronized public void unregisterSlot(int slotOffset) {
        if (slotOffset < 0 || slotOffset > 3)
            return;
        int res;
        if (slotOffset == 0)
            res = R.id.slot1;
        else if (slotOffset == 1)
            res = R.id.slot2;
        else if (slotOffset == 2)
            res = R.id.slot3;
        else
            res = R.id.slot4;

        if (v != null) {
            LinearLayout slotButtonLayout = v
                    .findViewById(res);
            slotButtonLayout.setVisibility(View.GONE);
        }
    }

    /**
     * Allows for a dynamically registered capability within the pallet to be unregistered.
     * @param slot the previously registered slot
     */
    synchronized static public void unregisterSlot(Slot slot) {
        if (slot == null)
            return;
        // look up the corresponding resource
        Integer res = null;
        for (Map.Entry<Integer, Slot> entry : _slotMap.entrySet()) {
            if (entry.getValue() == slot) {
                res = entry.getKey();
                break;
            }
        }
        if (res == null)
            return; // no resource for slot
        if (v != null) {
            LinearLayout slotButtonLayout = v
                    .findViewById(res);
            slotButtonLayout.setVisibility(View.GONE);
        }
    }

    private void init() {
        _pSubTypeMap.put(R.id.waypoint_point, new SubType("b-m-p-w-GOTO"));
        _pSubTypeMap.put(R.id.habp_point, new SubType("BPHA"));
        _pSubTypeMap.put(R.id.initial_point, new SubType("b-m-p-c-ip"));
        _pSubTypeMap.put(R.id.contact_point, new SubType("b-m-p-c-cp"));
        _pSubTypeMap.put(R.id.sensor_point, new SubType("b-m-p-s-p-loc"));
        _pSubTypeMap.put(R.id.op_point, new SubType("b-m-p-s-p-op"));
    }

    static private class SubType {

        SubType(String cotType) {
            this.cotType = cotType;
        }

        public final String cotType;
    }

    private final OnClickListener buttonClickListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            if (selectedButton != null) {
                boolean currentButton = selectedButton == v;
                deselectCurrentButton(currentButton);
                if (currentButton)
                    return;
            }

            setSelected(v);

        }
    };

    private void deselectCurrentButton(boolean endTool) {
        if (selectedButton == null)
            return;

        selectedButton.setSelected(false);
        showHABP(false);
        selectedButton = null;

        if (endTool) {
            Intent myIntent = new Intent();
            myIntent.setAction(ToolManagerBroadcastReceiver.END_TOOL);
            myIntent.putExtra("tool", EnterLocationTool.TOOL_NAME);
            AtakBroadcast.getInstance().sendBroadcast(myIntent);
        }
    }

    private void showHABP(boolean show) {
        if (show) {
            subViewAlt.setVisibility(View.GONE);
            subView.setVisibility(View.VISIBLE);
        } else {
            subViewAlt.setVisibility(View.VISIBLE);
            subView.setVisibility(View.GONE);
        }
    }

    private void setSelected(View v) {
        selectedButton = v;
        selectedButton.setSelected(true);

        int checkedPosition = -1;

        SubType sub = _pSubTypeMap.get(v.getId());
        if (sub != null)
            selectedType = sub.cotType;

        final int id = v.getId();
        if (id == R.id.contact_point) {
            showHABP(false);
            checkedPosition = 0;
        } else if (id == R.id.initial_point) {
            showHABP(false);
            checkedPosition = 1;
        } else if (id == R.id.habp_point) {
            showHABP(true);
            checkedPosition = 2;
        } else if (id == R.id.waypoint_point) {
            showHABP(false);
            checkedPosition = 3;
        } else if (id == R.id.sensor_point) {
            showHABP(false);
            checkedPosition = 4;
        } else if (id == R.id.op_point) {
            showHABP(false);
            checkedPosition = 5;
        } else if (id == R.id.slot1) {
            showHABP(false);
            checkedPosition = 6;
        } else if (id == R.id.slot2) {
            showHABP(false);
            checkedPosition = 7;
        } else if (id == R.id.slot3) {
            showHABP(false);
            checkedPosition = 8;
        } else if (id == R.id.slot4) {
            showHABP(false);
            checkedPosition = 9;
        } else {
            showHABP(false);
        }

        Tool tool = ToolManagerBroadcastReceiver.getInstance()
                .getActiveTool();
        if (tool != null
                && EnterLocationTool.TOOL_NAME.equals(tool.getIdentifier())) {
            //Log.d(TAG, "Skipping BEGIN_TOOL intent");
            return;
        }

        Intent myIntent = new Intent();
        myIntent.setAction(ToolManagerBroadcastReceiver.BEGIN_TOOL);
        myIntent.putExtra("tool", EnterLocationTool.TOOL_NAME);
        myIntent.putExtra("current_type", selectedType);
        myIntent.putExtra("checked_position", checkedPosition);
        AtakBroadcast.getInstance().sendBroadcast(myIntent);
    }

    @Override
    public void onPause() {
        clearSelection(true);
        super.onPause();
    }

    public void clearSelection(boolean bPauseListener) {
        deselectCurrentButton(true);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    public MapItem getPointPlacedIntent(GeoPointMetaData point,
            final String uid)
            throws CreatePointException {
        if (selectedButton == null || selectedType.equals(""))
            throw new CreatePointException(
                    "Select an entry type before entering a location.");

        //check for special points
        if (selectedType.equals("b-m-p-s-p-loc")) {
            PlacePointTool.MarkerCreator mc = new PlacePointTool.MarkerCreator(
                    point)
                            .setUid(uid)
                            .setType(selectedType);
            if (!_customNamingView.genCallsign().equals("")) {
                mc = mc.setCallsign(_customNamingView.genCallsign());
                _customNamingView.incrementStartIndex();
            }
            Marker m = mc.placePoint();
            m.setMetaBoolean(SensorDetailHandler.SENSOR_FOV, true);
            m.setMetaBoolean(SensorDetailHandler.HIDE_FOV, true);
            m.persist(MapView.getMapView().getMapEventDispatcher(), null,
                    this.getClass());

            //clear the selection so that the user can place the sensor FOV endpoint
            deselectCurrentButton(false);
            SensorDetailHandler.selectFOVEndPoint(m, true, false);
            return m;
        } else if (selectedType.equals("BPHA")) {
            DrawingRectangle r;
            if (!_customNamingView.genCallsign().equals("")) {
                r = BPHARectangleCreator.drawRectangle(point, _BPHA,
                        MapView.getMapView(), _customNamingView.genCallsign());
                _customNamingView.incrementStartIndex();
            } else {
                r = BPHARectangleCreator.drawRectangle(point, _BPHA,
                        MapView.getMapView(), "");
            }
            if (r == null) {
                throw new CreatePointException("unable to place rectangle");
            }
            return r;
        }
        //Log.d(TAG, "getPointPlacedIntent: " + _curColor + ", " + uid);

        PlacePointTool.MarkerCreator mc = new PlacePointTool.MarkerCreator(
                point)
                        .setUid(uid)
                        .setType(selectedType)
                        .showCotDetails(false);

        if (!_customNamingView.genCallsign().equals("")) {
            mc = mc.setCallsign(_customNamingView.genCallsign());
            _customNamingView.incrementStartIndex();
        }
        Marker retval = mc.placePoint();

        Slot slot = _slotMap.get(selectedButton.getId());
        if (slot != null) {
            slot.afterAction(retval);
        }
        return retval;

    }

    @Override
    public void onGridSelected(BattlePositionHoldingArea bpha) {
        _BPHA = bpha;
    }

    private BattlePositionHoldingArea _BPHA = new BattlePositionHoldingArea(2,
            2);
}
