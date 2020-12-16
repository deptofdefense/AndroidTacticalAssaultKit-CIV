
package com.atakmap.android.tools;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import androidx.core.app.NavUtils;

import android.text.InputType;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.Toast;

import com.atakmap.android.metrics.activity.MetricActivity;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.tools.menu.ActionBroadcastExtraStringData;
import com.atakmap.android.tools.menu.ActionClickData;
import com.atakmap.android.tools.menu.ActionMenuData;
import com.atakmap.android.tools.menu.AtakActionBarListData;
import com.atakmap.android.tools.menu.AtakActionBarMenuData;
import com.atakmap.android.tools.menu.AtakActionBarMenuData.Orientation;
import com.atakmap.android.update.AppMgmtActivity;
import com.atakmap.app.R;
import com.atakmap.app.preferences.ToolsPreferenceFragment;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import com.atakmap.coremap.locale.LocaleUtil;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.util.SparseBooleanArray;
import com.atakmap.android.missionpackage.api.MissionPackageApi;
import com.atakmap.android.missionpackage.file.MissionPackageManifest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.io.File;

import com.atakmap.android.maps.MapView;
import android.content.Context;
import android.content.BroadcastReceiver;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;

/**
 * 
 */
public class AllToolsActivity extends MetricActivity implements
        ActionBar.OnNavigationListener {

    protected static final String TAG = "AllToolsActivity";
    public static final int ALLTOOLSCONFIG_REQUEST_CODE = 1557;
    private SharedPreferences _prefs;
    private AtakActionBarListData _actionBars;
    private int _actionBarHeight = 120;

    // Title navigation Spinner data
    private ArrayList<AllToolsNavLayout> _layoutNavs;
    private AllToolsNavLayoutAdapter _layoutNavAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(null);
        AtakPreferenceFragment.setOrientation(this);
        setContentView(R.layout.all_tools);

        Intent i = getIntent();
        if (i != null && i.hasExtra("actionBarHeight"))
            _actionBarHeight = i.getIntExtra("actionBarHeight", 120);

        _prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // look to configured orientation, based on user settings
        AtakPreferenceFragment.setSoftKeyIllumination(this);

        DocumentedIntentFilter filter = new DocumentedIntentFilter();
        filter.addAction(
                "com.atakmap.app.QUITAPP",
                "Intent to start the quiting process, if the boolean extra FORCE_QUIT is set, the application will not prompt the user before quitting");

        if (MapView.getMapView() != null)
            AtakBroadcast.getInstance().registerReceiver(_quitReceiver, filter);

    }

    private final BroadcastReceiver _quitReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            AllToolsActivity.this.finish();
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.all_tools_options, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int id = item.getItemId();
        if (id == android.R.id.home) {
            try {
                NavUtils.navigateUpFromSameTask(this);
            } catch (IllegalArgumentException iae) {
                Log.d(TAG, "error occurred", iae);
                finish();
            }
            return true;
        } else if (id == R.id.allToolsEdit) {
            editLayout();
            return true;
        } else if (id == R.id.allToolsReset) {
            reset();
            return true;
        } else if (id == R.id.allToolsAppMgmt) {
            appMgmt();
            return true;
        } else if (id == R.id.mp_add_actionbar) {
            generateMissionPackage();
            return true;
        }
        return false;
    }

    // step one of generating a mission package
    private void generateMissionPackage() {
        final AtakActionBarListData actionBarListData = getActionBars();
        if (actionBarListData == null)
            return;

        final List<AtakActionBarMenuData> eligible = new ArrayList<>();

        for (AtakActionBarMenuData actionBar : actionBarListData
                .getActionBars()) {
            final String label = actionBar.getLabel();
            if (AtakActionBarListData.DEFAULT_LABEL.equals(label) ||
                    label.equals("JTAC") ||
                    label.equals("Minimal") ||
                    label.equals("Planning")) {
                // skip
            } else {
                eligible.add(actionBar);
            }
        }
        if (eligible.size() == 0) {
            Toast.makeText(AllToolsActivity.this,
                    "no user created toolbars found", Toast.LENGTH_SHORT)
                    .show();
            return;
        }

        final EditText editName = new EditText(this);
        editName.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editName.setFilters(AllToolsConfigMenuActivity.getNameFilters(this));

        AlertDialog.Builder ab = new AlertDialog.Builder(this);
        ab.setTitle(R.string.export_to_missionpackage_name)
                .setView(editName)
                .setPositiveButton(R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int whichButton) {
                                String name = editName.getText().toString();
                                if (FileSystemUtils.isEmpty(name)) {
                                    Toast.makeText(AllToolsActivity.this,
                                            "Invalid Input", Toast.LENGTH_SHORT)
                                            .show();
                                    generateMissionPackage();
                                } else {
                                    generateMissionPackage2(name, eligible);
                                }
                            }
                        })
                .setNegativeButton(R.string.cancel, null);
        ab.show();
    }

    public void generateMissionPackage2(final String name,
            final List<AtakActionBarMenuData> actionBarList) {

        final List<String> visualList = new ArrayList<>();

        for (AtakActionBarMenuData actionBar : actionBarList) {
            visualList.add(actionBar.getLabel() + " ("
                    + actionBar.getOrientation() + ")");
        }

        final String[] visualArray = new String[visualList.size()];
        visualList.toArray(visualArray);
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_multiple_choice,
                visualList);

        final ListView actionbarListView = new ListView(this);
        actionbarListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        actionbarListView.setAdapter(adapter);

        AlertDialog.Builder ab = new AlertDialog.Builder(this);
        ab.setTitle(R.string.export_to_missionpackage)
                .setView(actionbarListView)
                .setPositiveButton(R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int whichButton) {
                                final List<AtakActionBarMenuData> exportedList = new ArrayList<>();

                                SparseBooleanArray sparseBooleanArray = actionbarListView
                                        .getCheckedItemPositions();
                                int count = actionbarListView.getCount();
                                if (sparseBooleanArray != null) {
                                    for (int i = 0; i < count; i++) {
                                        if (sparseBooleanArray.get(i)) {
                                            //Log.d(TAG, actionbarListView.getItemAtPosition(i).toString());
                                            exportedList
                                                    .add(actionBarList.get(i));
                                        }
                                    }
                                }
                                if (exportedList.size() == 0) {
                                    Toast.makeText(AllToolsActivity.this,
                                            "no toolbars selected",
                                            Toast.LENGTH_SHORT).show();
                                    generateMissionPackage2(name,
                                            actionBarList);
                                } else {
                                    MissionPackageManifest mf = MissionPackageApi
                                            .CreateTempManifest(name, true,
                                                    true, null);
                                    for (AtakActionBarMenuData exported : exportedList) {
                                        File f = FileSystemUtils
                                                .getItem(("config/actionbars/"
                                                        + exported.getLabel()
                                                        + "_"
                                                        + exported
                                                                .getOrientation()
                                                        + ".xml").toLowerCase(
                                                                LocaleUtil
                                                                        .getCurrent()));
                                        try {
                                            File dir = FileSystemUtils
                                                    .createTempDir("actionbar",
                                                            "tmp", null);
                                            File nf = new File(dir,
                                                    f.getName());
                                            FileSystemUtils.copyFile(f, nf);
                                            f = nf;
                                        } catch (IOException ignored) {
                                        }

                                        mf.addFile(f, null);
                                        Log.d(TAG, "added: " + f.getName());
                                    }
                                    MissionPackageApi.Save(
                                            AllToolsActivity.this, mf, null);
                                    Toast.makeText(AllToolsActivity.this,
                                            R.string.mission_package_created,
                                            Toast.LENGTH_SHORT).show();
                                }
                            }
                        })
                .setNegativeButton(R.string.cancel, null);
        ab.show();

    }

    private void appMgmt() {
        Intent mgmtPlugins = new Intent(this,
                AppMgmtActivity.class);
        startActivityForResult(mgmtPlugins,
                ToolsPreferenceFragment.APP_MGMT_REQUEST_CODE);
    }

    private void reset() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.tool_text15)
                .setMessage(R.string.tool_text16)
                .setPositiveButton(R.string.reset,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                Log.d(TAG, "Resetting default layouts");
                                AtakActionBarListData.reset(getBaseContext(),
                                        true);

                                //edit pref to default
                                Editor editor = _prefs.edit();
                                editor.putString(
                                        AtakActionBarListData.CURRENT_ACTION_BAR_LAND_LABEL,
                                        AtakActionBarListData.DEFAULT_LABEL)
                                        .putString(
                                                AtakActionBarListData.CURRENT_ACTION_BAR_PORT_LABEL,
                                                AtakActionBarListData.DEFAULT_LABEL);
                                editor.apply();

                                //update custom action bars with a reset to the config/actionbars
                                ActionBarReceiver.getInstance()
                                        .updatePluginActionBars();

                                AtakBroadcast
                                        .getInstance()
                                        .sendBroadcast(
                                                new Intent(
                                                        ActionBarReceiver.RELOAD_ACTION_BAR)
                                                                .putExtra(
                                                                        "label",
                                                                        AtakActionBarListData.DEFAULT_LABEL));

                                //reload grid on this activity
                                refreshGrid(true);

                                dialog.dismiss();
                            }
                        })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void editLayout() {
        final Orientation orientation = AtakActionBarListData
                .getOrientation(AllToolsActivity.this);
        final String orientationPrefName = AtakActionBarListData
                .getOrientationPrefName(orientation);

        String currentLabel = _prefs.getString(orientationPrefName,
                AtakActionBarListData.DEFAULT_LABEL);

        //Default profile is immutable
        if (!AtakActionBarListData.DEFAULT_LABEL.equals(currentLabel)) {
            //not default, go ahead and edit
            Intent allToolsActivity = new Intent(getBaseContext(),
                    AllToolsConfigMenuActivity.class);
            allToolsActivity.putExtra("actionBarHeight", _actionBarHeight);
            startActivityForResult(allToolsActivity,
                    ALLTOOLSCONFIG_REQUEST_CODE);
            return;
        }

        final EditText editName = new EditText(this);
        editName.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editName.setFilters(AllToolsConfigMenuActivity.getNameFilters(this));

        new AlertDialog.Builder(this)
                .setTitle(R.string.tool_text17)
                .setView(editName)
                .setPositiveButton(R.string.save,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int whichButton) {
                                String newLabel = AllToolsConfigMenuActivity
                                        .sanitize(
                                                editName.getText().toString());
                                if (FileSystemUtils.isEmpty(newLabel)) {
                                    Log.w(TAG,
                                            "(Default) Label is required to save Action Bar");
                                    Toast.makeText(AllToolsActivity.this,
                                            R.string.label_required,
                                            Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                if (AtakActionBarListData.DEFAULT_LABEL
                                        .equalsIgnoreCase(newLabel)) {
                                    Log.w(TAG,
                                            "(Default) Label may not be reused");
                                    Toast.makeText(
                                            AllToolsActivity.this,
                                            R.string.label_may_not_be_reused,
                                            Toast.LENGTH_LONG).show();
                                    return;
                                }

                                AtakActionBarListData baselineActionBars = ActionBarReceiver
                                        .loadActionBars(AllToolsActivity.this);
                                if (baselineActionBars == null
                                        || !baselineActionBars.isValid()) {
                                    Log.w(TAG,
                                            "(Default) Failed to load Action Bar");
                                    Toast.makeText(AllToolsActivity.this,
                                            R.string.tool_text1,
                                            Toast.LENGTH_LONG).show();
                                    return;
                                }

                                if (baselineActionBars.has(newLabel,
                                        orientation)) {
                                    Log.w(TAG,
                                            "(Default) Action Bar already exists with label: "
                                                    + newLabel
                                                    + " and orientation: "
                                                    + orientation);
                                    Toast.makeText(
                                            AllToolsActivity.this,
                                            AllToolsActivity.this.getString(
                                                    R.string.tool_text8)
                                                    + newLabel,
                                            Toast.LENGTH_SHORT)
                                            .show();
                                    return;
                                }

                                if (baselineActionBars.isFull(orientation)) {
                                    Log.w(TAG,
                                            "(Default) Action Bar is full, cannot add label: "
                                                    + newLabel
                                                    + " and orientation: "
                                                    + orientation);
                                    Toast.makeText(
                                            AllToolsActivity.this,
                                            R.string.tool_text9,
                                            Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                //get default
                                AtakActionBarMenuData defaultActionBar = baselineActionBars
                                        .getActionBar(
                                                _prefs, AllToolsActivity.this);
                                if (defaultActionBar == null
                                        || !defaultActionBar.isValid()) {
                                    Log.w(TAG,
                                            "(Default) Action Bar not found for orientation: "
                                                    + orientation);
                                    Toast.makeText(
                                            AllToolsActivity.this,
                                            R.string.tool_text18,
                                            Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                // create new action bar (copy of default layout)
                                AtakActionBarMenuData actionbar = new AtakActionBarMenuData(
                                        defaultActionBar);
                                actionbar.setLabel(newLabel);

                                // add new layout, serialize out, then edit it
                                AtakActionBarListData snapshot = new AtakActionBarListData(
                                        baselineActionBars);
                                if (!snapshot.add(actionbar)) {
                                    Log.w(TAG,
                                            "(Default) Failed to add Action Bar with label: "
                                                    + newLabel);
                                    Toast.makeText(
                                            AllToolsActivity.this,
                                            getString(R.string.tool_text19)
                                                    + newLabel,
                                            Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                if (!snapshot.save()) {
                                    Log.w(TAG, "(Default) Failed to add/save: "
                                            + newLabel);
                                    Toast.makeText(AllToolsActivity.this,
                                            "Failed to save: " + newLabel,
                                            Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                Log.d(TAG, "(Default) Saved menu layout: "
                                        + actionbar.toString());

                                //set current label and launch edit activity
                                Editor editor = _prefs.edit();
                                editor.putString(orientationPrefName, newLabel);
                                editor.apply();

                                AtakBroadcast
                                        .getInstance()
                                        .sendBroadcast(
                                                new Intent(
                                                        ActionBarReceiver.RELOAD_ACTION_BAR)
                                                                .putExtra(
                                                                        "label",
                                                                        newLabel));

                                Intent allToolsActivity = new Intent(
                                        getBaseContext(),
                                        AllToolsConfigMenuActivity.class);
                                allToolsActivity.putExtra("actionBarHeight",
                                        _actionBarHeight);
                                startActivityForResult(allToolsActivity,
                                        ALLTOOLSCONFIG_REQUEST_CODE);
                            }
                        })
                .setNegativeButton(R.string.cancel, null).show();
    }

    private synchronized AtakActionBarListData getActionBars() {
        if (_actionBars == null) {
            _actionBars = ActionBarReceiver.loadActionBars(this);
        }

        return _actionBars;
    }

    @Override
    protected void onResume() {
        AtakPreferenceFragment.setOrientation(this);
        refreshGrid(true);

        invalidateOptionsMenu(); // rebuild list of layouts to "Select"
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        _layoutNavs = null;
        _layoutNavAdapter = null;
        _actionBars = null;
        _prefs = null;
        super.onDestroy();
    }

    private void refreshGrid(boolean reloadLayouts) {
        if (reloadLayouts) {
            _actionBars = null; // force reloading of latest action bar layouts
        }

        final AtakActionBarListData actionBars = getActionBars();
        if (actionBars == null || !actionBars.isValid()) {
            Toast.makeText(AllToolsActivity.this, "Failed to load Action Bar",
                    Toast.LENGTH_LONG).show();
            return;
        }

        AtakActionBarMenuData actionBarData = actionBars.getActionBar(_prefs,
                this);
        if (actionBarData == null || !actionBarData.isValid()) {
            Toast.makeText(AllToolsActivity.this,
                    R.string.tool_text20,
                    Toast.LENGTH_LONG).show();
            return;
        }

        final List<ActionMenuData> actions = actionBarData
                .getPlaceholderActions(false);
        GridView gridview = findViewById(R.id.gridAllTools);
        gridview.setAdapter(new ActionMenuAdapter(this, actions,
                null, null, null));

        gridview.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View v,
                    int position, long id) {
                if (position >= actions.size()) {
                    Log.e(TAG, "Failed to launch tool: " + position);
                    Toast.makeText(AllToolsActivity.this,
                            getString(R.string.tool_text21) + position,
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                // finish this Activity
                finish();

                ActionMenuData action = actions.get(position);
                if (action == null
                        || !action.isValid()
                        || !action.getActionClickData(ActionClickData.CLICK)
                                .hasBroadcast()) {
                    Log.e(TAG,
                            "Unable to load Action: "
                                    + (action == null ? ""
                                            : (", " + action
                                                    .toString())));
                    Toast.makeText(
                            AllToolsActivity.this,
                            getString(R.string.tool_text22)
                                    + (action == null ? " tool " + position
                                            : (action.getTitle())),
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                Log.e(TAG, "Processing menu action: " + action.toString());
                final Intent intent = new Intent();
                intent.setAction(action
                        .getActionClickData(ActionClickData.CLICK)
                        .getBroadcast()
                        .getAction());
                if (action.getActionClickData(ActionClickData.CLICK)
                        .getBroadcast().hasExtras()) {
                    for (ActionBroadcastExtraStringData extra : action
                            .getActionClickData(ActionClickData.CLICK)
                            .getBroadcast().getExtras()) {
                        intent.putExtra(extra.getKey(), extra.getValue());
                    }
                }

                new Handler().post(new Runnable() {
                    @Override
                    public void run() {
                        AtakBroadcast.getInstance().sendBroadcast(intent);
                    }
                });
            }
        });

        Orientation orientation = AtakActionBarListData.getOrientation(this);
        final String orientationPrefName = AtakActionBarListData
                .getOrientationPrefName(orientation);
        String label = _prefs.getString(orientationPrefName,
                AtakActionBarListData.DEFAULT_LABEL);

        if (!actionBars.has(label, orientation)) {
            // that label no longer available...
            Log.d(TAG, "Reverting to default label, from: " + label);
            label = AtakActionBarListData.DEFAULT_LABEL;
            Editor editor = _prefs.edit();
            editor.putString(orientationPrefName,
                    AtakActionBarListData.DEFAULT_LABEL);
            editor.apply();
        }

        if (reloadLayouts) {
            // avoid circular/loop of resetting adapter each time user selects
            // a layout (since it resets layout to index 0)
            ActionBar actionBar = getActionBar();
            if (actionBar == null)
                return;

            actionBar.setTitle(getString(R.string.app_name));
            actionBar.setSubtitle(R.string.tool_text23);
            actionBar.setDisplayHomeAsUpEnabled(true);

            // Enabling Spinner dropdown navigation
            int currentPosition = -1, count = 0;
            actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
            _layoutNavs = new ArrayList<>();
            for (AtakActionBarMenuData actionBarMenu : actionBars
                    .getActionBars(orientation)) {
                _layoutNavs.add(new AllToolsNavLayout(actionBarMenu,
                        com.atakmap.android.util.ATAKConstants.getIconId()));
                if (label.equals(actionBarMenu.getLabel()))
                    currentPosition = count;
                count++;
            }

            // title drop down adapter
            _layoutNavAdapter = new AllToolsNavLayoutAdapter(
                    getApplicationContext(), _layoutNavs);

            // assigning the spinner navigation
            actionBar.setListNavigationCallbacks(_layoutNavAdapter, this);

            if (currentPosition >= 0)
                actionBar.setSelectedNavigationItem(currentPosition);
        }
    }

    @Override
    public boolean onNavigationItemSelected(int itemPosition, long itemID) {
        AllToolsNavLayout layout = (AllToolsNavLayout) _layoutNavAdapter
                .getItem(itemPosition);
        if (layout == null) {
            Log.w(TAG, "Unable to find layout: " + itemPosition);
            return false;
        }

        Orientation orientation = AtakActionBarListData.getOrientation(this);
        final String orientationPrefName = AtakActionBarListData
                .getOrientationPrefName(orientation);

        String current = _prefs.getString(orientationPrefName,
                AtakActionBarListData.DEFAULT_LABEL);
        if (current.equals(layout.getMenu().getLabel())) {
            Log.d(TAG, "Already on correct layout");
            return true;
        }

        Log.d(TAG, "onNav: " + itemPosition + ", " + itemID + ", "
                + layout.getMenu().toString());
        // change current ATAK menu...
        _prefs.edit().putString(orientationPrefName, layout.getMenu()
                .getLabel()).apply();

        AtakBroadcast.getInstance().sendBroadcast(new Intent(
                ActionBarReceiver.RELOAD_ACTION_BAR)
                        .putExtra("label", layout.getMenu().getLabel()));
        refreshGrid(false);
        return true;
    }
}
