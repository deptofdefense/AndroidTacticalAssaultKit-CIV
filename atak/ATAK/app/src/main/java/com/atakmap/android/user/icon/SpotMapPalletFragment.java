
package com.atakmap.android.user.icon;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ToggleButton;

import com.atakmap.android.gui.ColorPicker;
import com.atakmap.android.icons.UserIcon;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.toolbar.Tool;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.user.CustomNamingView;
import com.atakmap.android.user.EnterLocationTool;
import com.atakmap.android.user.PlacePointTool;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.util.LinkedList;

/**
 * 
 * 
 */
public class SpotMapPalletFragment extends Fragment {

    private static final String TAG = "SpotMapPalletFragment";

    public static final String SPOT_MAP_POINT_COT_TYPE = "b-m-p-s-m";

    public static final String LABEL_ONLY_ICONSETPATH = UserIcon
            .GetIconsetPath(
                    SpotMapPallet.COT_MAPPING_SPOTMAP,
                    SpotMapReceiver.SPOT_MAP_POINT_COT_TYPE,
                    SpotMapPallet.COT_MAPPING_SPOTMAP_LABEL);

    private View root;
    private ToggleButton dropWhite;
    private ToggleButton dropYellow;
    private ToggleButton dropOrange;
    private ToggleButton dropBrown;
    private ToggleButton dropRed;
    private ToggleButton dropMagenta;

    private ToggleButton dropBlue;
    private ToggleButton dropCyan;
    private ToggleButton dropGreen;
    private ToggleButton dropGrey;
    private ToggleButton dropBlack;
    private ToggleButton dropLabel;

    private Button customButton;

    private RelativeLayout dropCustomFirstLayout;
    private ToggleButton dropCustomFirst;
    private View dropCustomFirstBackground;
    private RelativeLayout dropCustomCenterLayout;
    private ToggleButton dropCustomCenter;
    private View dropCustomCenterBackground;
    private RelativeLayout dropCustomLastLayout;
    private ToggleButton dropCustomLast;
    private View dropCustomLastBackground;
    private View dropCustomSpacer1;
    private View dropCustomSpacer1Background;
    private View dropCustomSpacer2;
    private View dropCustomSpacer2Background;
    private View dropLabelBackground;
    private String _currColorText = "";
    private CustomNamingView _customNamingView;

    private LinkedList<Integer> recentCustomColors;

    private static int _curColor;
    private CompoundButton _typeChecked;
    private boolean _labelSelected;
    private int _selectId = 0;
    private SharedPreferences _prefs;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        _labelSelected = false;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        _prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());

        View v = inflater.inflate(R.layout.enter_location_dotmap, container,
                false);
        root = v;

        LinearLayout customHolder = v
                .findViewById(R.id.customHolder);
        _customNamingView = new CustomNamingView(
                CustomNamingView.DOTMAP);
        customHolder.addView(_customNamingView.getMainView());

        _curColor = Color.WHITE;

        // Views that have to do with CoT or tool types
        dropWhite = v
                .findViewById(R.id.enterLocationTypePointWhite);
        dropYellow = v
                .findViewById(R.id.enterLocationTypePointYellow);
        dropOrange = v
                .findViewById(R.id.enterLocationTypePointOrange);
        dropBrown = v
                .findViewById(R.id.enterLocationTypePointBrown);
        dropRed = v.findViewById(R.id.enterLocationTypePointRed);
        dropMagenta = v
                .findViewById(R.id.enterLocationTypePointMagenta);
        dropBlue = v
                .findViewById(R.id.enterLocationTypePointBlue);
        dropCyan = v
                .findViewById(R.id.enterLocationTypePointCyan);
        dropGreen = v
                .findViewById(R.id.enterLocationTypePointGreen);
        dropGrey = v
                .findViewById(R.id.enterLocationTypePointGrey);
        dropBlack = v
                .findViewById(R.id.enterLocationTypePointBlack);
        dropLabel = v
                .findViewById(R.id.enterLocationTypePointLabel);
        dropLabelBackground = v
                .findViewById(R.id.enterLocationTypePointLabelBackground);

        dropWhite.setOnCheckedChangeListener(_typeCheckedChangedListener);
        dropYellow.setOnCheckedChangeListener(_typeCheckedChangedListener);
        dropOrange.setOnCheckedChangeListener(_typeCheckedChangedListener);
        dropBrown.setOnCheckedChangeListener(_typeCheckedChangedListener);
        dropRed.setOnCheckedChangeListener(_typeCheckedChangedListener);
        dropMagenta.setOnCheckedChangeListener(_typeCheckedChangedListener);
        dropBlue.setOnCheckedChangeListener(_typeCheckedChangedListener);
        dropCyan.setOnCheckedChangeListener(_typeCheckedChangedListener);
        dropGreen.setOnCheckedChangeListener(_typeCheckedChangedListener);
        dropGrey.setOnCheckedChangeListener(_typeCheckedChangedListener);
        dropBlack.setOnCheckedChangeListener(_typeCheckedChangedListener);
        dropLabel.setOnCheckedChangeListener(_typeCheckedChangedListener);

        dropCustomFirstLayout = v
                .findViewById(R.id.customColorButtonFirstBackgroundLayout);
        dropCustomFirst = v
                .findViewById(R.id.customColorButtonFirst);
        dropCustomFirstBackground = v
                .findViewById(R.id.customColorButtonFirstBackground);
        dropCustomCenterLayout = v
                .findViewById(R.id.customColorButtonCenterBackgroundLayout);
        dropCustomCenter = v
                .findViewById(R.id.customColorButtonCenter);
        dropCustomCenterBackground = v
                .findViewById(R.id.customColorButtonCenterBackground);
        dropCustomLastLayout = v
                .findViewById(R.id.customColorButtonLastBackgroundLayout);
        dropCustomLast = v
                .findViewById(R.id.customColorButtonLast);
        dropCustomLastBackground = v
                .findViewById(R.id.customColorButtonLastBackground);
        dropCustomSpacer1 = v.findViewById(R.id.customColorButtonSpacer1);
        dropCustomSpacer1Background = v
                .findViewById(R.id.customColorButtonSpacer1BackgroundLayout);
        dropCustomSpacer2 = v.findViewById(R.id.customColorButtonSpacer2);
        dropCustomSpacer2Background = v
                .findViewById(R.id.customColorButtonSpacer2BackgroundLayout);

        dropCustomFirst.setOnCheckedChangeListener(_typeCheckedChangedListener);
        dropCustomCenter
                .setOnCheckedChangeListener(_typeCheckedChangedListener);
        dropCustomLast.setOnCheckedChangeListener(_typeCheckedChangedListener);

        recentCustomColors = new LinkedList<>();
        loadCustomColors(false);
        customButton = v.findViewById(R.id.customButton);
        customButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {
                Activity ac = getActivity();
                final ColorPicker picker = new ColorPicker(ac, _curColor);
                AlertDialog.Builder b = new AlertDialog.Builder(ac)
                        .setTitle(R.string.point_dropper_text54)
                        .setPositiveButton(R.string.ok,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog,
                                            int which) {
                                        dialog.dismiss();
                                        customColorSelected(picker.getColor());
                                    }
                                })
                        .setNegativeButton(R.string.cancel, null);
                b.setView(picker);
                final AlertDialog alert = b.create();
                alert.show();
            }
        });

        if (_selectId != 0)
            select(_selectId);
        return v;
    }

    @Override
    public void onPause() {
        clearSelection(true);
        _selectId = 0;
        super.onPause();
    }

    public void select(int resId) {
        if (root != null && resId != 0) {
            View match = root.findViewById(resId);
            if (match instanceof ToggleButton) {
                if (_typeChecked != null && _typeChecked != match
                        && _typeChecked.getId() == resId)
                    _typeChecked = null;
                ((ToggleButton) match).setChecked(true);
            }

        }
        _selectId = resId;
    }

    public void clearSelection(boolean bPauseListener) {
        if (bPauseListener) {
            if (dropWhite != null)
                dropWhite.setOnCheckedChangeListener(null);
            if (dropYellow != null)
                dropYellow.setOnCheckedChangeListener(null);
            if (dropOrange != null)
                dropOrange.setOnCheckedChangeListener(null);
            if (dropBrown != null)
                dropBrown.setOnCheckedChangeListener(null);
            if (dropRed != null)
                dropRed.setOnCheckedChangeListener(null);
            if (dropMagenta != null)
                dropMagenta.setOnCheckedChangeListener(null);
            if (dropBlue != null)
                dropBlue.setOnCheckedChangeListener(null);
            if (dropCyan != null)
                dropCyan.setOnCheckedChangeListener(null);
            if (dropGreen != null)
                dropGreen.setOnCheckedChangeListener(null);
            if (dropGrey != null)
                dropGrey.setOnCheckedChangeListener(null);
            if (dropBlack != null)
                dropBlack.setOnCheckedChangeListener(null);
            if (dropLabel != null)
                dropLabel.setOnCheckedChangeListener(null);
            if (dropCustomFirst != null)
                dropCustomFirst.setOnCheckedChangeListener(null);
            if (dropCustomCenter != null)
                dropCustomCenter.setOnCheckedChangeListener(null);
            if (dropCustomLast != null)
                dropCustomLast.setOnCheckedChangeListener(null);
        }

        if (dropWhite != null)
            dropWhite.setChecked(false);
        if (dropYellow != null)
            dropYellow.setChecked(false);
        if (dropOrange != null)
            dropOrange.setChecked(false);
        if (dropBrown != null)
            dropBrown.setChecked(false);
        if (dropRed != null)
            dropRed.setChecked(false);
        if (dropMagenta != null)
            dropMagenta.setChecked(false);
        if (dropBlue != null)
            dropBlue.setChecked(false);
        if (dropCyan != null)
            dropCyan.setChecked(false);
        if (dropGreen != null)
            dropGreen.setChecked(false);
        if (dropGrey != null)
            dropGrey.setChecked(false);
        if (dropBlack != null)
            dropBlack.setChecked(false);
        if (dropLabel != null)
            dropLabel.setChecked(false);
        if (dropCustomFirst != null)
            dropCustomFirst.setChecked(false);
        if (dropCustomCenter != null)
            dropCustomCenter.setChecked(false);
        if (dropCustomLast != null)
            dropCustomLast.setChecked(false);

        if (bPauseListener) {
            if (dropWhite != null)
                dropWhite
                        .setOnCheckedChangeListener(
                                _typeCheckedChangedListener);
            if (dropYellow != null)
                dropYellow
                        .setOnCheckedChangeListener(
                                _typeCheckedChangedListener);
            if (dropOrange != null)
                dropOrange
                        .setOnCheckedChangeListener(
                                _typeCheckedChangedListener);
            if (dropBrown != null)
                dropBrown
                        .setOnCheckedChangeListener(
                                _typeCheckedChangedListener);
            if (dropRed != null)
                dropRed.setOnCheckedChangeListener(_typeCheckedChangedListener);
            if (dropMagenta != null)
                dropMagenta
                        .setOnCheckedChangeListener(
                                _typeCheckedChangedListener);
            if (dropBlue != null)
                dropBlue.setOnCheckedChangeListener(
                        _typeCheckedChangedListener);
            if (dropCyan != null)
                dropCyan.setOnCheckedChangeListener(
                        _typeCheckedChangedListener);
            if (dropGreen != null)
                dropGreen
                        .setOnCheckedChangeListener(
                                _typeCheckedChangedListener);
            if (dropGrey != null)
                dropGrey.setOnCheckedChangeListener(
                        _typeCheckedChangedListener);
            if (dropBlack != null)
                dropBlack
                        .setOnCheckedChangeListener(
                                _typeCheckedChangedListener);
            if (dropLabel != null)
                dropLabel
                        .setOnCheckedChangeListener(
                                _typeCheckedChangedListener);
            if (dropCustomFirst != null)
                dropCustomFirst
                        .setOnCheckedChangeListener(
                                _typeCheckedChangedListener);
            if (dropCustomCenter != null)
                dropCustomCenter
                        .setOnCheckedChangeListener(
                                _typeCheckedChangedListener);
            if (dropCustomLast != null)
                dropCustomLast
                        .setOnCheckedChangeListener(
                                _typeCheckedChangedListener);
        }
    }

    @Override
    public void onViewStateRestored(Bundle saved) {
        super.onViewStateRestored(saved);
        if (root != null) {
            root.post(new Runnable() {
                @Override
                public void run() {
                    select(_selectId);
                }
            });
        }
    }

    private final CompoundButton.OnCheckedChangeListener _typeCheckedChangedListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton cb, boolean isChecked) {
            if (isChecked) {
                if (_typeChecked != null) {
                    CompoundButton oldChecked = _typeChecked;
                    _typeChecked = cb;
                    if (oldChecked != _typeChecked)
                        oldChecked.setChecked(false);
                } else {
                    _typeChecked = cb;
                }
                _onTypeCheckedChanged(cb, true);
            } else {
                if (_typeChecked != null && _typeChecked == cb) {
                    _typeChecked = null;
                    _onTypeCheckedChanged(cb, false);
                } else {
                    // if the toggle was not already checked than this is a change of type and no
                    // need to call
                    // _onTypeCheckedChanged for this toggle, since the views will be updated by the
                    // button that was clicked
                }
            }
        }
    };

    private void _onTypeCheckedChanged(CompoundButton buttonView,
            boolean isChecked) {
        // on tool begin
        int checkedPosition = -1;
        _labelSelected = false;
        if (isChecked) {
            int checkedId = buttonView.getId();
            if (checkedId == R.id.enterLocationTypePointWhite) {
                _curColor = Color.WHITE;
                _currColorText = getString(R.string.w);
                checkedPosition = 0;
            } else if (checkedId == R.id.enterLocationTypePointYellow) {
                _curColor = Color.YELLOW;
                _currColorText = getString(R.string.y);
                checkedPosition = 1;
            } else if (checkedId == R.id.enterLocationTypePointOrange) {
                _curColor = Color.argb(255, 255, 119, 0);
                _currColorText = getString(R.string.o);
                checkedPosition = 2;
            } else if (checkedId == R.id.enterLocationTypePointBrown) {
                _curColor = Color.argb(255, 139, 69, 19);
                _currColorText = getString(R.string.br);
                checkedPosition = 3;
            } else if (checkedId == R.id.enterLocationTypePointRed) {
                _curColor = Color.RED;
                _currColorText = getString(R.string.r);
                checkedPosition = 4;
            } else if (checkedId == R.id.enterLocationTypePointMagenta) {
                _curColor = Color.MAGENTA;
                _currColorText = getString(R.string.p);
                checkedPosition = 5;
            } else if (checkedId == R.id.enterLocationTypePointBlue) {
                _curColor = Color.BLUE;
                _currColorText = getString(R.string.b);
                checkedPosition = 6;
            } else if (checkedId == R.id.enterLocationTypePointCyan) {
                _curColor = Color.CYAN;
                _currColorText = getString(R.string.cy);
                checkedPosition = 7;
            } else if (checkedId == R.id.enterLocationTypePointGreen) {
                _curColor = Color.GREEN;
                _currColorText = getString(R.string.g);
                checkedPosition = 8;
            } else if (checkedId == R.id.enterLocationTypePointGrey) {
                _curColor = Color.argb(255, 119, 119, 119);
                _currColorText = getString(R.string.gry);
                checkedPosition = 9;
            } else if (checkedId == R.id.enterLocationTypePointBlack) {
                _curColor = Color.BLACK;
                _currColorText = getString(R.string.blk);
                checkedPosition = 10;
            } else if (checkedId == R.id.enterLocationTypePointLabel) {
                if (_curColor == Color.BLACK)
                    _curColor = Color.WHITE;
                _currColorText = "";
                checkedPosition = 11;
                _labelSelected = true;
            } else if (checkedId == R.id.customColorButtonFirst) {
                ColorDrawable buttonColor = (ColorDrawable) dropCustomFirstBackground
                        .getBackground();
                _curColor = buttonColor.getColor();
                _currColorText = getString(R.string.cstm);
                checkedPosition = 12;
            } else if (checkedId == R.id.customColorButtonCenter) {
                ColorDrawable buttonColor = (ColorDrawable) dropCustomCenterBackground
                        .getBackground();
                _currColorText = getString(R.string.cstm);
                _curColor = buttonColor.getColor();
                checkedPosition = 13;
            } else if (checkedId == R.id.customColorButtonLast) {
                ColorDrawable buttonColor = (ColorDrawable) dropCustomLastBackground
                        .getBackground();
                _curColor = buttonColor.getColor();
                _currColorText = getString(R.string.cstm);
                checkedPosition = 14;
            }
        }

        try {
            ColorDrawable buttonColor = (ColorDrawable) dropLabelBackground
                    .getBackground().mutate();
            if (_curColor == Color.BLACK)
                buttonColor.setColor(Color.WHITE);
            else
                buttonColor.setColor(_curColor);
        } catch (Exception e) {
            Log.d(TAG, "error: ", e);
        }

        Log.d(TAG, "Checked position is now " + checkedPosition);
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
            myIntent.setAction("com.atakmap.android.maps.toolbar.BEGIN_TOOL");
            myIntent.putExtra("tool", EnterLocationTool.TOOL_NAME);
            myIntent.putExtra("current_type", "point");
            myIntent.putExtra("checked_position", checkedPosition);
            AtakBroadcast.getInstance().sendBroadcast(myIntent);
        }
    }

    private void customColorSelected(int color) {
        recentCustomColors.addFirst(color);
        if (recentCustomColors.size() > 3) {
            recentCustomColors.removeLast();
        }

        //save out user selections
        int numRecentColors = recentCustomColors.size();
        if (numRecentColors == 1) {
            saveCustomColors(recentCustomColors.get(0), null, null);
        } else if (numRecentColors == 2) {
            saveCustomColors(recentCustomColors.get(0),
                    recentCustomColors.get(1), null);
        } else {
            saveCustomColors(recentCustomColors.get(0),
                    recentCustomColors.get(1), recentCustomColors.get(2));
        }

        //now update the UI
        loadCustomColors(true);
    }

    private void saveCustomColors(Integer c1, Integer c2, Integer c3) {
        SharedPreferences.Editor edit = _prefs.edit();
        if (c1 == null) {
            edit.remove("spotmap.customcolor.1");
        } else {
            edit.putInt("spotmap.customcolor.1", c1);
        }

        if (c2 == null) {
            edit.remove("spotmap.customcolor.2");
        } else {
            edit.putInt("spotmap.customcolor.2", c2);
        }

        if (c3 == null) {
            edit.remove("spotmap.customcolor.3");
        } else {
            edit.putInt("spotmap.customcolor.3", c3);
        }

        edit.apply();
    }

    private void loadCustomColors(boolean buttonChecked) {

        recentCustomColors.clear();
        if (_prefs.contains("spotmap.customcolor.1")) {
            recentCustomColors.add(_prefs.getInt("spotmap.customcolor.1",
                    Color.WHITE));
        }
        if (_prefs.contains("spotmap.customcolor.2")) {
            recentCustomColors.addLast(_prefs.getInt("spotmap.customcolor.2",
                    Color.WHITE));
        }
        if (_prefs.contains("spotmap.customcolor.3")) {
            recentCustomColors.addLast(_prefs.getInt("spotmap.customcolor.3",
                    Color.WHITE));
        }

        int numRecentColors = recentCustomColors.size();
        if (numRecentColors == 0) {
            dropCustomFirst.setVisibility(ToggleButton.GONE);
            dropCustomCenter.setVisibility(ToggleButton.GONE);
            dropCustomLast.setVisibility(ToggleButton.GONE);

            dropCustomFirstLayout.setVisibility(LinearLayout.GONE);
            dropCustomCenterLayout.setVisibility(LinearLayout.GONE);
            dropCustomLastLayout.setVisibility(LinearLayout.GONE);

            dropCustomSpacer1.setVisibility(View.VISIBLE);
            dropCustomSpacer1Background.setVisibility(View.VISIBLE);
            dropCustomSpacer2.setVisibility(View.VISIBLE);
            dropCustomSpacer2Background.setVisibility(View.VISIBLE);
            dropCustomCenterBackground.setVisibility(View.VISIBLE);
        } else if (numRecentColors == 1) {
            // Show only the solo button, and then hide everything else
            dropCustomFirst.setVisibility(ToggleButton.GONE);
            dropCustomCenter.setVisibility(ToggleButton.VISIBLE);
            dropCustomLast.setVisibility(ToggleButton.GONE);

            // Now show only the solo background, and hide everything else
            dropCustomFirstLayout.setVisibility(LinearLayout.GONE);
            dropCustomCenterLayout.setVisibility(LinearLayout.VISIBLE);
            dropCustomLastLayout.setVisibility(LinearLayout.GONE);

            dropCustomSpacer1.setVisibility(View.GONE);
            dropCustomSpacer1Background.setVisibility(View.GONE);
            dropCustomSpacer2.setVisibility(View.INVISIBLE);
            dropCustomSpacer2Background.setVisibility(View.INVISIBLE);

            // Now set the color of the visible background
            dropCustomCenterBackground.setBackgroundColor(recentCustomColors
                    .get(0));

            if (buttonChecked)
                dropCustomCenter.setChecked(true);
        } else if (numRecentColors == 2) {
            // Hide the solo button, and show the first and last buttons
            dropCustomFirst.setVisibility(ToggleButton.VISIBLE);
            dropCustomCenter.setVisibility(ToggleButton.GONE);
            dropCustomLast.setVisibility(ToggleButton.VISIBLE);

            // Now hide the solo background, and show the first and last
            dropCustomFirstLayout.setVisibility(LinearLayout.VISIBLE);
            dropCustomCenterLayout.setVisibility(LinearLayout.GONE);
            dropCustomLastLayout.setVisibility(LinearLayout.VISIBLE);

            dropCustomSpacer1.setVisibility(View.INVISIBLE);
            dropCustomSpacer1Background.setVisibility(View.INVISIBLE);
            dropCustomSpacer2.setVisibility(View.GONE);
            dropCustomSpacer2Background.setVisibility(View.GONE);

            dropCustomFirstBackground.setBackgroundColor(recentCustomColors
                    .get(0));
            dropCustomLastBackground.setBackgroundColor(recentCustomColors
                    .get(1));

            if (buttonChecked)
                dropCustomFirst.setChecked(true);
        } else {
            // Show all of the
            dropCustomFirst.setVisibility(ToggleButton.VISIBLE);
            dropCustomCenter.setVisibility(ToggleButton.VISIBLE);
            dropCustomLast.setVisibility(ToggleButton.VISIBLE);

            // Now hide the solo background, and show the first and last
            dropCustomFirstLayout.setVisibility(LinearLayout.VISIBLE);
            dropCustomCenterLayout.setVisibility(LinearLayout.VISIBLE);
            dropCustomLastLayout.setVisibility(LinearLayout.VISIBLE);

            dropCustomSpacer1.setVisibility(View.GONE);
            dropCustomSpacer1Background.setVisibility(View.GONE);
            dropCustomSpacer2.setVisibility(View.GONE);
            dropCustomSpacer2Background.setVisibility(View.GONE);

            dropCustomFirstBackground.setBackgroundColor(recentCustomColors
                    .get(0));
            dropCustomCenterBackground.setBackgroundColor(recentCustomColors
                    .get(1));
            dropCustomLastBackground.setBackgroundColor(recentCustomColors
                    .get(2));

            if (buttonChecked) {
                if (_typeChecked == dropCustomFirst) {
                    _curColor = recentCustomColors.get(0);
                } else {
                    dropCustomFirst.setChecked(true);
                }
            }
        }
    }

    public Marker getPointPlacedIntent(GeoPointMetaData point,
            final String uid) {

        PlacePointTool.MarkerCreator mc = new PlacePointTool.MarkerCreator(
                point)
                        .setUid(uid)
                        .setType(SPOT_MAP_POINT_COT_TYPE)
                        .showCotDetails(false);

        if (_labelSelected) {
            mc.setIconPath(LABEL_ONLY_ICONSETPATH);
            mc.setTextColor(_curColor);
            mc.setColor(_curColor);
        } else {
            mc.setColor(_curColor)
                    .setIconPath(UserIcon.GetIconsetPath(
                            SpotMapPallet.COT_MAPPING_SPOTMAP,
                            SpotMapReceiver.SPOT_MAP_POINT_COT_TYPE,
                            String.valueOf(_curColor)));
        }

        if (!_customNamingView.genCallsign().equals("")) {
            mc.setCallsign(_customNamingView.genCallsign());
            _customNamingView.incrementStartIndex();
        } else {
            int count = PlacePointTool.getCount(_currColorText, MapView
                    .getMapView().getRootGroup()
                    .deepFindItems("type", "b-m-p-s-m"));
            if (_currColorText.isEmpty())
                mc.setCallsign(String.valueOf(count));
            else
                mc.setCallsign(_currColorText + " " + count);
        }
        return mc.placePoint();
    }
}
