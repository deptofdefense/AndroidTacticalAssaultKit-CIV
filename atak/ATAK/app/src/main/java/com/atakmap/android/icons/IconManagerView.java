
package com.atakmap.android.icons;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import com.atakmap.android.gui.ImportFileBrowserDialog;
import com.atakmap.android.importfiles.sort.ImportUserIconSetSort;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.user.icon.Icon2525cPallet;
import com.atakmap.android.user.icon.SpotMapPallet;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.app.system.ResourceUtil;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * LinearLayout view group for Icon Manager
 * 
 * 
 */
public class IconManagerView extends LinearLayout {

    private static final String TAG = "IconManagerView";
    public static final String DEFAULT_MAPPING_CHANGED = "com.atakmap.android.icons.DEFAULT_MAPPING_CHANGED";

    private MapView _mapView;
    private Context _context;

    private IconsetAdapter _iconsetAdapter;
    private ListView _iconsetList;

    private SharedPreferences _prefs;

    public IconManagerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        _prefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public void dispose() {
        _mapView = null;
        _iconsetList = null;
        _prefs = null;

        if (_iconsetAdapter != null) {
            _iconsetAdapter.dispose();
            _iconsetAdapter = null;
        }
    }

    void refresh(MapView mapView) {
        _mapView = mapView;
        _context = mapView.getContext();

        if (_iconsetAdapter == null) {
            Button btnAddIconset = findViewById(
                    R.id.iconmgr_iconset_addBtn);
            btnAddIconset.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(View v) {
                    importIconSet();
                }
            });

            Button btnDefaultCoTMapping = findViewById(
                    R.id.iconmgr_iconset_defaultCoTMapping);
            btnDefaultCoTMapping.setText(
                    ResourceUtil.getResource(R.string.civ_default_cot_mapping,
                            R.string.default_cot_mapping));
            btnDefaultCoTMapping.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(View v) {
                    setDefaultCoTMapping();
                }
            });

            _iconsetList = findViewById(R.id.iconmgr_iconset_list);
            View header = ((Activity) getContext()).getLayoutInflater()
                    .inflate(
                            R.layout.iconset_list_header, null);
            _iconsetList.addHeaderView(header);
            _iconsetAdapter = new IconsetAdapter(_mapView);
            _iconsetList.setAdapter(_iconsetAdapter);
        }

        refreshIconsets();
    }

    /**
     * Display dialog to select 2525C, dotmap, or any iconset
     */
    private void setDefaultCoTMapping() {
        final List<UserIconSet> iconsets = new ArrayList<>();
        List<UserIconSet> temp = UserIconDatabase.instance(_context)
                .getIconSets(false, false);
        if (!FileSystemUtils.isEmpty(temp))
            iconsets.addAll(temp);

        String currentMapping = getDefaultCoTMapping(_prefs);
        boolean forceMapping = getForceCoTMapping(_prefs);

        //first two entries are 2525C and Dot Map, followed by user icon sets
        int count = 2 + iconsets.size();
        final String[] items = new String[count];
        if (!FileSystemUtils.isEmpty(currentMapping)
                && currentMapping.equals(Icon2525cPallet.COT_MAPPING_2525C))
            items[0] = ResourceUtil.getString(_context, R.string.civ_s2525C,
                    R.string.s2525C)
                    + _context.getString(R.string.mapping_selected);
        else
            items[0] = ResourceUtil.getString(_context, R.string.civ_s2525C,
                    R.string.s2525C);

        if (!FileSystemUtils.isEmpty(currentMapping)
                && currentMapping.equals(SpotMapPallet.COT_MAPPING_SPOTMAP))
            items[1] = _context.getString(R.string.spot_map)
                    + _context.getString(R.string.mapping_selected);
        else
            items[1] = _context.getString(R.string.spot_map);

        for (int i = 0; i < iconsets.size(); i++) {
            if (!FileSystemUtils.isEmpty(currentMapping)
                    && currentMapping.equals(iconsets.get(i).getUid()))
                items[2 + i] = "'"
                        + iconsets.get(i).getName()
                        + _context.getString(R.string.mapping_iconset)
                        + _context.getString(R.string.mapping_selected);
            else
                items[2 + i] = "'"
                        + iconsets.get(i).getName()
                        + _context.getString(R.string.mapping_iconset);
        }

        ArrayAdapter<String> iconsetAdapter = new ArrayAdapter<>(
                _context, android.R.layout.simple_list_item_1, items);

        LayoutInflater inflater = LayoutInflater.from(_context);
        LinearLayout layout = (LinearLayout) inflater.inflate(
                R.layout.enter_location_preferred_iconset_list, null);
        ListView listView = layout
                .findViewById(R.id.preferredIconsetList);
        final CheckBox force_iconset = layout
                .findViewById(R.id.preferredIconset_force);
        force_iconset.setChecked(forceMapping);
        listView.setAdapter(iconsetAdapter);

        AlertDialog.Builder b = new AlertDialog.Builder(_context);
        b.setTitle(_context.getString(R.string.mapping_title));
        b.setView(layout);
        b.setNegativeButton(_context.getString(R.string.cancel), null);

        final AlertDialog bd = b.create();
        listView.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int item,
                    long arg3) {
                bd.dismiss();

                switch (item) {
                    case 0: {
                        setDefaultCoTMapping(_prefs,
                                Icon2525cPallet.COT_MAPPING_2525C);
                    }
                        break;
                    case 1: {
                        setDefaultCoTMapping(_prefs,
                                SpotMapPallet.COT_MAPPING_SPOTMAP);
                    }
                        break;
                    default: {
                        UserIconSet iconset = iconsets.get(item - 2);
                        setDefaultCoTMapping(_prefs, iconset.getUid());
                    }
                }

                setForceCoTMapping(_prefs, force_iconset.isChecked());

                //now refresh icons as needed
                AtakBroadcast.getInstance().sendBroadcast(
                        new Intent(DEFAULT_MAPPING_CHANGED));
            }
        });

        bd.show();
    }

    /**
     * Browse for zip, then import, refresh list
     */
    protected void importIconSet() {
        ImportFileBrowserDialog.show(
                getContext().getString(R.string.mapping_dialog),
                ATAKUtilities.getStartDirectory(_mapView.getContext()),
                new String[] {
                        "zip"
                },
                new ImportFileBrowserDialog.DialogDismissed() {
                    @Override
                    public void onFileSelected(final File f) {
                        if (f == null)
                            return;

                        final String path = f.getAbsolutePath();
                        if (!FileSystemUtils.checkExtension(f, "zip")) {
                            Log.w(TAG,
                                    "File Import Browser returned unsupported file: "
                                            + path);
                            return;
                        }

                        //imported via Icon Manager, do not require XML
                        if (!ImportUserIconSetSort.HasIconset(f, false)) {
                            Log.w(TAG, "Selected zip is not a valid iconset: "
                                    + path);
                            Toast.makeText(getContext(),
                                    R.string.mapping_tip,
                                    Toast.LENGTH_LONG).show();
                            return;
                        }

                        //initiate import, callback intent will refresh the list
                        Intent loadIntent = new Intent();
                        loadIntent.setAction(IconsMapAdapter.ADD_ICONSET);
                        loadIntent.putExtra("filepath", path);
                        AtakBroadcast.getInstance().sendBroadcast(loadIntent);
                    }

                    @Override
                    public void onDialogClosed() {
                        //Do nothing
                    }
                }, getContext());

    }

    private void refreshIconsets() {
        _iconsetAdapter.clear();

        List<UserIconSet> iconsets = UserIconDatabase.instance(_context)
                .getIconSets(true, false);
        if (FileSystemUtils.isEmpty(iconsets))
            return;

        for (UserIconSet iconset : iconsets) {
            if (iconset != null && iconset.isValid()) {
                _iconsetAdapter.add(iconset);
            }
        }
    }

    /**
     * Get default CoT Mapping
     * 
     * @param prefs the shared preferencs to use to get the default mapping.
     * @return if not set, user 25252B iconset
     */
    public static String getDefaultCoTMapping(final SharedPreferences prefs) {
        if (prefs == null) {
            Log.w(TAG, "Failed to get Default CoT Mapping");
            return Icon2525cPallet.COT_MAPPING_2525C;
        }

        return prefs.getString("iconset.default.cotMapping",
                Icon2525cPallet.COT_MAPPING_2525C);
    }

    /**
     * Set default CoT Mapping
     * 
     * @param prefs the shared preference to set the default mapping to.
     * @param mapping if empty, remove the pref
     */
    public static void setDefaultCoTMapping(final SharedPreferences prefs,
            final String mapping) {
        if (prefs == null) {
            Log.w(TAG, "Failed to set Default CoT Mapping");
            return;
        }

        if (FileSystemUtils.isEmpty(mapping)) {
            //no mapping specified        
            Log.d(TAG, "removed Default CoT Mapping");
            Editor editor = prefs.edit();
            editor.remove("iconset.default.cotMapping");
            editor.apply();
        }

        //if the copy succeeded, make a note in the preferences
        Log.d(TAG, "updating Default CoT Mapping: " + mapping);
        Editor editor = prefs.edit();
        editor.putString("iconset.default.cotMapping", mapping);
        editor.apply();
    }

    public static void validateDefaultCoTMapping(SharedPreferences prefs,
            String uid) {
        String defaultUUID = getDefaultCoTMapping(prefs);
        if (!FileSystemUtils.isEmpty(defaultUUID) && defaultUUID.equals(uid)) {
            Log.d(TAG, "Refreshing Default CoT Mapping");
            setDefaultCoTMapping(prefs, Icon2525cPallet.COT_MAPPING_2525C);
        }
    }

    public static void setForceCoTMapping(SharedPreferences prefs, boolean b) {
        if (prefs == null) {
            Log.w(TAG, "Failed to set Force CoT Mapping");
            return;
        }

        //if the copy succeeded, make a note in the preferences
        Log.d(TAG, "Updating Force CoT Mapping: " + b);
        Editor editor = prefs.edit();
        editor.putBoolean("iconset.force.cotMapping", b);
        editor.apply();
    }

    /**
     * Check whether user has forced all icons to use the preferred CoT Mapping
     * 
     * @param prefs the shared preference to use
     * @return true or false
     */
    public static boolean getForceCoTMapping(SharedPreferences prefs) {
        if (prefs == null) {
            Log.w(TAG, "Failed to check Force CoT Mapping");
            return false;
        }

        return prefs.getBoolean("iconset.force.cotMapping", false);
    }
}
