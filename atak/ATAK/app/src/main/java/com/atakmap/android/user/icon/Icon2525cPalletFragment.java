
package com.atakmap.android.user.icon;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import com.atakmap.android.icons.UserIcon;
import com.atakmap.android.icons.UserIconSet;
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
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

public class Icon2525cPalletFragment extends Fragment {

    private static final String TAG = "Icon2525cPalletFragment";

    private ImageButton unknownRb;
    private ImageButton neutralRb;
    private ImageButton hostileRb;
    private ImageButton friendlyRb;

    private static String _currType;
    private ImageButton _typeChecked;
    private Button _subtypeButton;
    private int checkedPosition;
    private CustomNamingView _customNamingView;
    private subtypePair currPair;

    private SharedPreferences _prefs;

    /**
     * Create a new instance of CountingFragment, providing "num"
     * as an argument.
     */
    public static Icon2525cPalletFragment newInstance(UserIconSet iconset) {
        Icon2525cPalletFragment f = new Icon2525cPalletFragment();

        Bundle args = new Bundle();
        args.putInt("id", iconset.getId());
        args.putString("name", iconset.getName());
        args.putString("uid", iconset.getUid());
        f.setArguments(args);
        return f;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.enter_location_2525c, container,
                false);
        final subtypePair[] pairs = new subtypePair[] {
                new subtypePair(getString(R.string.ground), "G"),
                new subtypePair(getString(R.string.aircraft), "A"),
                new subtypePair(getString(R.string.artillery), "G-U-C-F"),
                new subtypePair(getString(R.string.building), "G-I"),
                new subtypePair(getString(R.string.mine), "G-E-X-M"),
                new subtypePair(getString(R.string.ship), "S"),
                new subtypePair(getString(R.string.sniper), "G-U-C-I-d"),
                new subtypePair(getString(R.string.tank), "G-E-V-A-T"),
                new subtypePair(getString(R.string.troops), "G-U-C-I"),
                new subtypePair(getString(R.string.vehicles), "G-E-V")
        };
        _customNamingView = new CustomNamingView(
                CustomNamingView.DEFAULT);
        LinearLayout _mainView = _customNamingView.getMainView();
        LinearLayout holder = v.findViewById(R.id.customHolder);
        holder.addView(_mainView);

        unknownRb = v
                .findViewById(R.id.enterLocationTypeUnknown);
        neutralRb = v
                .findViewById(R.id.enterLocationTypeNeutral);
        hostileRb = v
                .findViewById(R.id.enterLocationTypeHostile);
        friendlyRb = v
                .findViewById(R.id.enterLocationTypeFriendly);

        //Setting listeners to the 4 main buttons on the top
        unknownRb.setOnClickListener(_typeCheckedChangedListener);
        neutralRb.setOnClickListener(_typeCheckedChangedListener);
        hostileRb.setOnClickListener(_typeCheckedChangedListener);
        friendlyRb.setOnClickListener(_typeCheckedChangedListener);

        // check to see what type is being built - capabilities wise
        FlavorProvider fp = SystemComponentLoader.getFlavorProvider();
        if (fp == null || !fp.hasMilCapabilities()) {
            v.findViewById(R.id.enterLocationSubtypeLayout)
                    .setVisibility(View.GONE);
        }
        _subtypeButton = v
                .findViewById(R.id.enterLocationSubtypeButton);
        final String[] items = new String[10];
        for (int i = 0; i < items.length; i++) {
            items[i] = pairs[i].readableString;
        }
        final AlertDialog dialog = new AlertDialog.Builder(MapView.getMapView()
                .getContext())
                        .setItems(items, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface,
                                    int i) {
                                currPair = pairs[i];
                                _subtypeButton.setText(currPair.readableString);
                            }
                        })
                        .setTitle(R.string.point_dropper_text55)
                        .setNegativeButton(R.string.close, null).create();

        _subtypeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.show();
            }
        });

        _prefs = PreferenceManager
                .getDefaultSharedPreferences(getActivity());

        if (currPair == null) {
            currPair = new subtypePair(getString(R.string.ground), "G");
        }
        _subtypeButton.setText(currPair.readableString);

        return v;
    }

    private static class subtypePair {
        String readableString;
        String cotString;

        subtypePair(String rs, String cot) {
            readableString = rs;
            cotString = cot;
        }
    }

    private final Button.OnClickListener _typeCheckedChangedListener = new Button.OnClickListener() {
        @Override
        public void onClick(View v) {
            //Clear the edit text focus whenever we switch subtypes
            _customNamingView.clearEditTextFocus();
            if (_typeChecked == v) {
                _typeChecked.setSelected(false);
                _onTypeCheckedChanged(_typeChecked, false);
                _typeChecked = null;
            } else {
                if (_typeChecked != null) {
                    _typeChecked.setSelected(false);
                }
                if (v instanceof ImageButton) {
                    _typeChecked = (ImageButton) v;
                    _typeChecked.setSelected(true);
                    _onTypeCheckedChanged(_typeChecked, true);
                }
            }
        }
    };

    private void _onTypeCheckedChanged(ImageButton buttonView,
            boolean isChecked) {
        // on tool begin
        //Clear the edit text focus whenever we switch main types
        _customNamingView.clearEditTextFocus();
        checkedPosition = -1;
        //One of the main buttons is checked
        if (isChecked) {
            int checkedId = buttonView.getId();

            //These all function the same,
            //setType based on what is checked
            //set the text based off of that type
            //Get a finalText by combining type and sub_type
            //Set that text to the screen
            //Set a checked position
            if (checkedId == R.id.enterLocationTypeUnknown) {
                _currType = "a-u";
                checkedPosition = 0;
            } else if (checkedId == R.id.enterLocationTypeNeutral) {
                _currType = "a-n";
                checkedPosition = 1;
            } else if (checkedId == R.id.enterLocationTypeHostile) {
                _currType = "a-h";
                checkedPosition = 2;
            } else if (checkedId == R.id.enterLocationTypeFriendly) {
                _currType = "a-f";
                checkedPosition = 3;
            }
        }
        Log.d(TAG, "Checked position is now " + checkedPosition);
        if (checkedPosition == -1) {
            _currType = null;
        } else {
            _customNamingView.clearEditTextFocus();
        }

        if (checkedPosition == -1) {
            Intent myIntent = new Intent();
            myIntent.setAction(ToolManagerBroadcastReceiver.END_TOOL);
            myIntent.putExtra("tool", EnterLocationTool.TOOL_NAME);
            AtakBroadcast.getInstance().sendBroadcast(myIntent);
        } else {
            //if point select tool is already active, do not relaunch b/c it is 
            //"ended" by Tool Mgr in the process
            Tool tool = ToolManagerBroadcastReceiver.getInstance()
                    .getActiveTool();
            if (tool != null
                    && EnterLocationTool.TOOL_NAME
                            .equals(tool.getIdentifier())) {
                //Log.d(TAG, "Skipping BEGIN_TOOL intent");
                return;
            }

            Intent myIntent = new Intent();
            myIntent.setAction(ToolManagerBroadcastReceiver.BEGIN_TOOL);
            myIntent.putExtra("tool", EnterLocationTool.TOOL_NAME);
            myIntent.putExtra("current_type", _currType);
            myIntent.putExtra("checked_position", checkedPosition);
            AtakBroadcast.getInstance().sendBroadcast(myIntent);
        }
    }

    /**
     * Main function that is responsible for actually placing the new point on the map
     * @param p
     * @param uid - uid of us
     * @return a newly created marker
     * @throws CreatePointException thrown if nothing is selected and the user tries to
     * create a point
     */
    Marker getPointPlacedIntent(final GeoPointMetaData p, final String uid)
            throws CreatePointException {

        GeoPointMetaData point = p;

        //Set the UID as that will be the same for all points
        PlacePointTool.MarkerCreator mc = new PlacePointTool.MarkerCreator(
                point)
                        .setUid(uid);

        //Make sure a type is selected
        if (_currType == null) {
            throw new CreatePointException(
                    "Select an entry type before entering a location.");
            //If we are of type point
        } else {

            String type = _currType;
            //If we have a sub-type set it
            if (currPair != null)
                type += "-" + currPair.cotString;
            //Get the iconPath
            String iconsetPath = UserIcon.GetIconsetPath(
                    Icon2525cPallet.COT_MAPPING_2525C, _currType, type);
            _prefs.edit().putString("lastCoTTypeSet", type)
                    .putString("lastIconsetPath", iconsetPath).apply();
            //Set the type, icon path
            mc = mc
                    .setType(type)
                    .setIconPath(iconsetPath)
                    .showCotDetails(false);

            if (!_customNamingView.genCallsign().equals("")) {
                mc = mc.setCallsign(_customNamingView.genCallsign());
                _customNamingView.incrementStartIndex();
            }
        }
        return mc.placePoint();
    }

    //Function that clears a selection when the user selects an already selected type
    public void clearSelection(boolean bPauseListener) {
        _currType = null;
        _typeChecked = null;

        //TODO hack to avoid _typeCheckedChangedListener sending the END_TOOL intent
        if (bPauseListener) {
            if (unknownRb != null)
                unknownRb.setOnClickListener(null);
            if (neutralRb != null)
                neutralRb.setOnClickListener(null);
            if (hostileRb != null)
                hostileRb.setOnClickListener(null);
            if (friendlyRb != null)
                friendlyRb.setOnClickListener(null);
        }

        if (unknownRb != null)
            unknownRb.setSelected(false);
        if (neutralRb != null)
            neutralRb.setSelected(false);
        if (hostileRb != null)
            hostileRb.setSelected(false);
        if (friendlyRb != null)
            friendlyRb.setSelected(false);

        if (bPauseListener) {
            if (unknownRb != null)
                unknownRb
                        .setOnClickListener(_typeCheckedChangedListener);
            if (neutralRb != null)
                neutralRb
                        .setOnClickListener(_typeCheckedChangedListener);
            if (hostileRb != null)
                hostileRb
                        .setOnClickListener(_typeCheckedChangedListener);
            if (friendlyRb != null)
                friendlyRb
                        .setOnClickListener(_typeCheckedChangedListener);
        }
    }

}
