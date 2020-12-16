
package com.atakmap.android.tools;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.core.app.NavUtils;

import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.util.DisplayMetrics;
import android.view.DragEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.DragShadowBuilder;
import android.view.View.OnClickListener;
import android.view.View.OnDragListener;
import android.view.View.OnLongClickListener;
import android.widget.AbsListView;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import com.atakmap.android.maps.MapView;
import android.content.Context;
import android.content.BroadcastReceiver;

import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.metrics.activity.MetricActivity;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.tools.menu.ActionMenuData;
import com.atakmap.android.tools.menu.ActionMenuData.PreferredMenu;
import com.atakmap.android.tools.menu.AtakActionBarListData;
import com.atakmap.android.tools.menu.AtakActionBarMenuData;
import com.atakmap.android.tools.menu.AtakActionBarMenuData.Orientation;
import com.atakmap.android.tools.undo.UndoAction;
import com.atakmap.android.tools.undo.UndoStack;
import com.atakmap.android.tools.undo.UndoStack.UndoActionResult;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 
 */
public class AllToolsConfigMenuActivity extends MetricActivity {

    protected static final String TAG = "AllToolsConfigMenuActivity";

    public static final int MAX_NAME_LENGTH = 10;
    public static final String NAME_REGEX = "[^A-Za-z0-9-_.\u0600-\u06FF]";

    private GridView _gridviewActionBar;
    private GridView _gridviewHidden;
    private ListView _listviewOverflow;

    private TextView _textActionBarEmpty;
    private TextView _textOverflowEmpty;
    private TextView _textHiddenEmpty;

    private ActionBarMenuAdapter _adapterActionBar;
    private ActionMenuAdapter _adapterOverflow;
    private ActionMenuAdapter _adapterHidden;

    private Drawable _dragDropBackground;
    private int _actionBarHeight = 120;
    private int _iconSize = 107;
    private int _maxWidth = 0;
    private Orientation _orientation;

    /**
     * Last few changes/operations
     */
    private UndoStack _undo;

    /**
     * Baseline action bar from file system (not including outstanding user config changes)
     */
    private AtakActionBarListData _baselineActionBars;

    private SharedPreferences _prefs;

    /**
     * Flag indicating changes should be saved out at end of Activity lifecyble
     */
    private boolean bCheckForChanges;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.all_tools_config);
        bCheckForChanges = true;
        _prefs = PreferenceManager.getDefaultSharedPreferences(this);

        Intent i = getIntent();
        if (i != null && i.hasExtra("actionBarHeight"))
            _actionBarHeight = i.getIntExtra("actionBarHeight", 120);

        _iconSize = (int) (_actionBarHeight / 1.6f);
        if (_prefs.getBoolean("largeActionBar", false))
            _iconSize *= ActionBarReceiver.SCALE_FACTOR;
        _iconSize += (ActionBarReceiver.ActionItemPaddingLR * 2) +
                (ActionBarReceiver.nwTimSortFix ? 10 : 0);

        _orientation = AtakActionBarListData.getOrientation(this);
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        _maxWidth = Math.min(metrics.widthPixels, metrics.heightPixels);
        if (_orientation == Orientation.landscape)
            _maxWidth = Math.max(metrics.widthPixels, metrics.heightPixels);

        try {
            // Note, this is not always exactly correct since our ic_action_overflow drawable is
            // typically wider than the icon/view provided by Android for the overflow. But if
            // anything it gives us a little extra buffer, so not a big issue at the moment
            Drawable overflow = getResources().getDrawable(
                    R.drawable.ic_action_overflow);
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(overflow);
            iv.getDrawable()
                    .setColorFilter(new PorterDuffColorFilter(
                            ActionBarReceiver.getUserIconColor(),
                            PorterDuff.Mode.SRC_ATOP));
            iv.measure(ActionBarReceiver.QuerySpec,
                    ActionBarReceiver.QuerySpec);
            _maxWidth -= iv.getMeasuredWidth();
        } catch (Exception e) {
            Log.e(TAG, "Failed to load action overflow icon", e);
            _maxWidth -= 64;
        }

        int maxIcons = (int) Math.floor(_maxWidth / (float) _iconSize);

        // look to configured orientation, based on user settings
        AtakPreferenceFragment.setOrientation(this);
        AtakPreferenceFragment.setSoftKeyIllumination(this);

        // get menu configuration
        _baselineActionBars = ActionBarReceiver.loadActionBars(this);
        if (_baselineActionBars == null || !_baselineActionBars.isValid()) {
            Toast.makeText(AllToolsConfigMenuActivity.this,
                    R.string.tool_text1,
                    Toast.LENGTH_LONG).show();
            return;
        }

        _undo = new UndoStack();

        _dragDropBackground = getResources().getDrawable(
                R.drawable.all_tools_config_drop);
        StartDragLongClickListener startDragLongClickListener = new StartDragLongClickListener();
        DisplayLabelOnClickListener displayLabelOnClickListener = new DisplayLabelOnClickListener();
        ActionListDragListener actionDragListener = new ActionListDragListener();
        ActionListParentDragListener actionDragParentListener = new ActionListParentDragListener();
        ActionEmptyTextDragListener emptyTextDragListener = new ActionEmptyTextDragListener();

        // setup grid/list views

        bumpToOverflow(_baselineActionBars);
        _gridviewActionBar = findViewById(
                R.id.gridAllToolsConfigActionBar);
        _adapterActionBar = new ActionBarMenuAdapter(this, _baselineActionBars
                .getActionBar(_prefs, this)
                .getActions(PreferredMenu.actionBar), _iconSize, maxIcons,
                displayLabelOnClickListener, startDragLongClickListener,
                actionDragListener);
        _gridviewActionBar.setAdapter(_adapterActionBar);
        _gridviewActionBar.setNumColumns(maxIcons);
        _gridviewActionBar.setOnDragListener(actionDragParentListener);
        _gridviewActionBar.setLayoutParams(new LayoutParams(
                maxIcons * _iconSize,
                LayoutParams.WRAP_CONTENT));

        _gridviewHidden = findViewById(
                R.id.gridAllToolsConfigHidden);
        _adapterHidden = new HiddenMenuAdapter(this, _baselineActionBars
                .getActionBar(_prefs, this)
                .getActions(PreferredMenu.hidden),
                displayLabelOnClickListener, startDragLongClickListener,
                actionDragListener);
        _gridviewHidden.setAdapter(_adapterHidden);
        _gridviewHidden.setOnDragListener(actionDragParentListener);

        _listviewOverflow = findViewById(
                R.id.listAllToolsConfigOverflow);
        _adapterOverflow = new OverflowMenuAdapter(this, _baselineActionBars
                .getActionBar(_prefs,
                        this)
                .getActions(PreferredMenu.overflow),
                displayLabelOnClickListener, startDragLongClickListener,
                actionDragListener);
        _listviewOverflow.setAdapter(_adapterOverflow);
        _listviewOverflow.setOnDragListener(actionDragParentListener);

        _textActionBarEmpty = findViewById(
                R.id.allToolsConfigActionBarEmptyText);
        _textActionBarEmpty.setOnDragListener(emptyTextDragListener);
        _textOverflowEmpty = findViewById(
                R.id.allToolsConfigOverflowEmptyText);
        _textOverflowEmpty.setOnDragListener(emptyTextDragListener);
        _textHiddenEmpty = findViewById(
                R.id.allToolsConfigHiddenEmptyText);
        _textHiddenEmpty.setOnDragListener(emptyTextDragListener);

        final String orientationPrefName = AtakActionBarListData
                .getOrientationPrefName(_orientation);

        String label = _prefs.getString(orientationPrefName,
                AtakActionBarListData.DEFAULT_LABEL);
        if (!_baselineActionBars.has(label, _orientation)) {
            // that label no longer available...
            Log.d(TAG, "Reverting to default label, from: " + label);
            label = AtakActionBarListData.DEFAULT_LABEL;
            Editor editor = _prefs.edit();
            editor.putString(orientationPrefName,
                    AtakActionBarListData.DEFAULT_LABEL);
            editor.apply();
        }

        // setup action bar
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setTitle(getString(R.string.tool_text2)
                    + (FileSystemUtils.isEmpty(label) ? "" : (": " + label)));
            actionBar.setSubtitle(R.string.tool_text3);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        updateEmptyText();
        Log.d(TAG, "Configuring menu: " + label);

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
            AllToolsConfigMenuActivity.this.finish();
        }
    };

    @Override
    protected void onResume() {
        AtakPreferenceFragment.setOrientation(this);
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        try {
            super.onDestroy();
        } catch (Exception e) {
            Log.e(TAG, "onDestroy error", e);
        }

        _gridviewActionBar = null;
        _gridviewHidden = null;
        _listviewOverflow = null;

        _textActionBarEmpty = null;
        _textOverflowEmpty = null;
        _textHiddenEmpty = null;

        if (_adapterActionBar != null) {
            _adapterActionBar.clear();
            _adapterActionBar = null;
        }
        if (_adapterOverflow != null) {
            _adapterOverflow.clear();
            _adapterOverflow = null;
        }
        if (_adapterHidden != null) {
            _adapterHidden.clear();
            _adapterHidden = null;
        }

        if (_undo != null) {
            _undo.dispose();
            _undo = null;
        }

        _dragDropBackground = null;
    }

    @Override
    protected void onPause() {
        if (bCheckForChanges) {
            AtakActionBarListData snapshot = getSnapshotActionBars();
            if (snapshot != null && !snapshot.equals(_baselineActionBars)) {
                Log.d(TAG, "onPause updating baseline");
                if (!snapshot.save()) {
                    Log.w(TAG, "Failed to save changes");
                    Toast.makeText(this, R.string.failed_to_save_changes,
                            Toast.LENGTH_SHORT).show();
                }

                // invalidate ATAK menu...
                AtakBroadcast.getInstance().sendBroadcast(new Intent(
                        ActionBarReceiver.RELOAD_ACTION_BAR));
            } else {
                Log.d(TAG, "onPause no change");
            }
        } else {
            // user saved new menu
            Log.d(TAG, "onPause not checking for changes");
        }

        super.onPause();
    }

    private void updateEmptyText() {
        if (_adapterActionBar == null || _adapterActionBar.getCount() < 1) {
            _gridviewActionBar.setVisibility(GridView.GONE);
            _textActionBarEmpty.setVisibility(GridView.VISIBLE);
        } else {
            _gridviewActionBar.setVisibility(GridView.VISIBLE);
            _textActionBarEmpty.setVisibility(GridView.GONE);
        }

        if (_adapterHidden == null || _adapterHidden.getCount() < 1) {
            _gridviewHidden.setVisibility(GridView.GONE);
            _textHiddenEmpty.setVisibility(GridView.VISIBLE);
        } else {
            _gridviewHidden.setVisibility(GridView.VISIBLE);
            _textHiddenEmpty.setVisibility(GridView.GONE);
        }

        if (_adapterOverflow == null || _adapterOverflow.getCount() < 1) {
            _listviewOverflow.setVisibility(GridView.GONE);
            _textOverflowEmpty.setVisibility(GridView.VISIBLE);
        } else {
            _listviewOverflow.setVisibility(GridView.VISIBLE);
            _textOverflowEmpty.setVisibility(GridView.GONE);
        }
    }

    private boolean loadActionBar(AtakActionBarListData actionBars) {
        Log.d(TAG, "Loading Action Bar...");
        if (actionBars == null || !actionBars.isValid()) {
            Toast.makeText(AllToolsConfigMenuActivity.this,
                    R.string.tool_text1,
                    Toast.LENGTH_LONG).show();
            Log.w(TAG, "Failed to load Action Bar, cannot reset");
            return false;
        }

        bumpToOverflow(actionBars);

        _adapterActionBar.clear();
        _adapterActionBar.add(actionBars.getActionBar(_prefs, this).getActions(
                PreferredMenu.actionBar), PreferredMenu.actionBar);

        _adapterOverflow.clear();
        _adapterOverflow.add(actionBars.getActionBar(_prefs, this).getActions(
                PreferredMenu.overflow), PreferredMenu.overflow);

        _adapterHidden.clear();
        _adapterHidden.add(actionBars.getActionBar(_prefs, this).getActions(
                PreferredMenu.hidden), PreferredMenu.hidden);

        redraw();
        return true;
    }

    /**
     * We want action bar gridview to have a single row (width of device) so lets manually move any
     * that won't fit into the overflow menu. Note, this UI also attempts to disallow configuring
     * too many
     * 
     * @param actionBars
     */
    private void bumpToOverflow(AtakActionBarListData actionBars) {
        for (AtakActionBarMenuData actionBar : actionBars.getActionBars(
                _orientation)) {

            int widthRemaining = _maxWidth;
            //Log.d(TAG, "Initial width (pixels)=" + widthRemaining);

            List<ActionMenuData> toRemove = new ArrayList<>();
            List<ActionMenuData> actions = actionBar.getActions(
                    PreferredMenu.actionBar);
            for (ActionMenuData action : actions) {
                if (widthRemaining >= _iconSize) {
                    widthRemaining -= _iconSize;
                    //Log.d(TAG, "Width remaining after: " + action.getTitle() + " " + actionWidth + " is " + widthRemaining);
                } else if (action.isPlaceholder()) {
                    Log.d(TAG,
                            "Removing demoted action: " + action.toString());
                    toRemove.add(action);
                } else {
                    Log.d(TAG,
                            "Demoting action to overflow: " + action.toString()
                                    + ", size: " + _iconSize + ", remaining "
                                    + widthRemaining);
                    action.setPreferredMenu(PreferredMenu.overflow);
                }
            } // end action loop

            //now see if any actions to remove
            for (ActionMenuData action : toRemove)
                actionBar.remove(action);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.all_tools_config_menu_options, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        final Orientation orientation = AtakActionBarListData
                .getOrientation(AllToolsConfigMenuActivity.this);

        final int id = item.getItemId();
        if (id == android.R.id.home) {
            try {
                NavUtils.navigateUpFromSameTask(this);
            } catch (IllegalArgumentException iae) {
                Log.d(TAG, "error occurred", iae);
                finish();
            }

        } else if (id == R.id.allToolsConfigUndo) {
            if (!_undo.hasAction()) {
                Toast.makeText(this, R.string.tool_text4,
                        Toast.LENGTH_SHORT).show();
                Log.d(TAG, "No operations to undo");
                return true;
            }

            UndoActionResult result = _undo.undo();
            if (result == null || !result.isValid()) {
                Toast.makeText(this, R.string.tool_text5,
                        Toast.LENGTH_SHORT).show();
                Log.w(TAG, "Failed undo operation");
                return true;
            }

            if (!result.isResult()) {
                Toast.makeText(this, R.string.tool_text6,
                        Toast.LENGTH_SHORT).show();
                Log.w(TAG, "Failed undo operation: "
                        + result.getAction().getDescription());
                return true;
            }

            Log.d(TAG, "Undo last op: "
                    + result.getAction().getDescription());
        } else if (id == R.id.allToolsConfigHideAll) {
            Log.d(TAG, "Hiding all actions");
            _undo.push(getSnapshotAction("Hide All"));

            _adapterHidden.clear();
            _adapterOverflow.clear();
            _adapterActionBar.clear();

            // separate hideable from not hideable, omit all placeholders
            List<ActionMenuData> hideable = _baselineActionBars
                    .getActionBar(_prefs, this)
                    .getHideableActions(true, false);
            List<ActionMenuData> notHideable = _baselineActionBars
                    .getActionBar(_prefs, this)
                    .getHideableActions(false, false);

            if (!FileSystemUtils.isEmpty(hideable))
                _adapterHidden.add(hideable, PreferredMenu.hidden);

            if (!FileSystemUtils.isEmpty(notHideable))
                _adapterOverflow.add(notHideable, PreferredMenu.overflow);

            redraw();
        } else if (id == R.id.allToolsConfigSave) {
            final EditText editName = new EditText(this);
            editName.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            editName.setFilters(getNameFilters(this));
            AlertDialog.Builder b = new AlertDialog.Builder(this);
            b.setTitle(R.string.tool_text7);
            b.setView(editName);
            b.setPositiveButton(R.string.save,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            String label = sanitize(
                                    editName.getText().toString());
                            if (FileSystemUtils.isEmpty(label)) {
                                Log.w(TAG,
                                        "Label is required to save Action Bar");
                                Toast.makeText(AllToolsConfigMenuActivity.this,
                                        R.string.label_required,
                                        Toast.LENGTH_SHORT)
                                        .show();
                                return;
                            }

                            if (AtakActionBarListData.DEFAULT_LABEL
                                    .equalsIgnoreCase(label)) {
                                Log.w(TAG, "Label may not be reused");
                                Toast.makeText(AllToolsConfigMenuActivity.this,
                                        R.string.label_may_not_be_reused,
                                        Toast.LENGTH_LONG).show();
                                return;
                            }

                            if (_baselineActionBars.has(label, orientation)) {
                                Log.w(TAG,
                                        "Action Bar already exists with label: "
                                                + label + " and orientation: "
                                                + orientation);
                                Toast.makeText(AllToolsConfigMenuActivity.this,
                                        getString(R.string.tool_text8) + label,
                                        Toast.LENGTH_SHORT).show();
                                return;
                            }

                            if (_baselineActionBars.isFull(orientation)) {
                                Log.w(TAG,
                                        "Action Bar is full, cannot add label: "
                                                + label + " and orientation: "
                                                + orientation);
                                Toast.makeText(AllToolsConfigMenuActivity.this,
                                        R.string.tool_text9, Toast.LENGTH_SHORT)
                                        .show();
                                return;
                            }

                            // add it, serialize out, onDestroy
                            AtakActionBarListData snapshot = new AtakActionBarListData(
                                    _baselineActionBars);

                            // create new action bar based on current layout
                            AtakActionBarMenuData actionbar = new AtakActionBarMenuData();
                            actionbar.setLabel(label);
                            actionbar.setOrientation(orientation.toString());
                            if (_adapterActionBar != null && _adapterActionBar
                                    .getCount() > 0) {
                                for (ActionMenuData action : _adapterActionBar
                                        .getActions())
                                    actionbar.add(new ActionMenuData(action));
                            }

                            if (_adapterOverflow != null && _adapterOverflow
                                    .getCount() > 0) {
                                for (ActionMenuData action : _adapterOverflow
                                        .getActions())
                                    actionbar.add(new ActionMenuData(action));
                            }

                            if (_adapterHidden != null && _adapterHidden
                                    .getCount() > 0) {
                                for (ActionMenuData action : _adapterHidden
                                        .getActions())
                                    actionbar.add(new ActionMenuData(action));
                            }

                            if (!snapshot.add(actionbar)) {
                                Log.w(TAG,
                                        "Failed to add Action Bar with label: "
                                                + label);
                                Toast.makeText(AllToolsConfigMenuActivity.this,
                                        getString(R.string.failed_to_add)
                                                + label,
                                        Toast.LENGTH_SHORT).show();
                                return;
                            }

                            if (!snapshot.save()) {
                                Log.w(TAG, "Failed to add/save: "
                                        + label);
                                Toast.makeText(AllToolsConfigMenuActivity.this,
                                        R.string.failed_to_save_changes,
                                        Toast.LENGTH_SHORT).show();
                                return;
                            }

                            final String orientationPrefName = AtakActionBarListData
                                    .getOrientationPrefName(orientation);
                            _prefs.edit().putString(orientationPrefName, label)
                                    .apply();
                            AtakBroadcast.getInstance()
                                    .sendBroadcast(new Intent(
                                            ActionBarReceiver.RELOAD_ACTION_BAR)
                                                    .putExtra("label", label));

                            // don't squash these changes as we return to AllTools activity
                            Log.d(TAG, "Saved menu layout: "
                                    + actionbar.toString());
                            bCheckForChanges = false;
                            NavUtils.navigateUpFromSameTask(
                                    AllToolsConfigMenuActivity.this);
                        }
                    });
            b.setNegativeButton(R.string.cancel, null);
            b.show();
        } else if (id == R.id.allToolsConfigDelete) {
            final String orientationPrefName = AtakActionBarListData
                    .getOrientationPrefName(orientation);

            final String label = _prefs.getString(orientationPrefName,
                    AtakActionBarListData.DEFAULT_LABEL);
            if (FileSystemUtils.isEmpty(label)
                    || label.equals(AtakActionBarListData.DEFAULT_LABEL)) {
                Log.w(TAG, "Cannot delete Default Action Bar");
                Toast.makeText(this, R.string.tool_text10,
                        Toast.LENGTH_SHORT)
                        .show();
                return true;
            }

            new AlertDialog.Builder(this)
                    .setMessage(
                            getString(R.string.delete) + label
                                    + getString(R.string.question_mark_symbol))
                    .setTitle(getString(R.string.delete))
                    .setPositiveButton(R.string.delete2,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog,
                                        int whichButton) {
                                    // remove, serialize it out, onDestroy
                                    AtakActionBarListData snapshot = new AtakActionBarListData(
                                            _baselineActionBars);
                                    AtakActionBarMenuData toDelete = snapshot
                                            .getActionBar(label,
                                                    orientation);
                                    if (toDelete == null) {
                                        Log.w(TAG,
                                                "Cannot delete missing Action Bar: "
                                                        + label);
                                        Toast.makeText(
                                                AllToolsConfigMenuActivity.this,
                                                R.string.tool_text12,
                                                Toast.LENGTH_SHORT)
                                                .show();
                                        return;
                                    }

                                    if (!snapshot.remove(toDelete)) {
                                        Log.w(TAG,
                                                "Cannot delete Action Bar: "
                                                        + label);
                                        Toast.makeText(
                                                AllToolsConfigMenuActivity.this,
                                                R.string.tool_text12,
                                                Toast.LENGTH_SHORT)
                                                .show();
                                        return;
                                    }

                                    // TODO: After this is deployed to 4.1.1 as a localized fix, go ahead and
                                    // make it more robust for 4.2.

                                    File actionBarDir = FileSystemUtils
                                            .getItem("config/actionbars");
                                    File actionBarFile = new File(actionBarDir,
                                            FileSystemUtils
                                                    .sanitizeWithSpacesAndSlashes(
                                                            toDelete.getLabel()
                                                                    + "_"
                                                                    + toDelete
                                                                            .getOrientation()
                                                                    + ".xml".toLowerCase(
                                                                            LocaleUtil
                                                                                    .getCurrent())));
                                    if (!IOProviderFactory
                                            .delete(actionBarFile)) {
                                        Log.w(TAG,
                                                "Failed to delete/save: "
                                                        + label);
                                        Toast.makeText(
                                                AllToolsConfigMenuActivity.this,
                                                R.string.failed_to_save_changes,
                                                Toast.LENGTH_SHORT).show();
                                        return;
                                    }

                                    // reset selected menu to Default
                                    Editor editor = _prefs.edit();
                                    editor.putString(
                                            orientationPrefName,
                                            AtakActionBarListData.DEFAULT_LABEL);
                                    editor.apply();

                                    AtakBroadcast
                                            .getInstance()
                                            .sendBroadcast(
                                                    new Intent(
                                                            ActionBarReceiver.RELOAD_ACTION_BAR)
                                                                    .putExtra(
                                                                            "label",
                                                                            AtakActionBarListData.DEFAULT_LABEL));

                                    // don't squash these changes as we return to AllTools activity
                                    Log.d(TAG, "Deleted menu layout: "
                                            + toDelete.toString());
                                    bCheckForChanges = false;
                                    NavUtils.navigateUpFromSameTask(
                                            AllToolsConfigMenuActivity.this);
                                }
                            })
                    .setNegativeButton(R.string.cancel, null).show();
        }

        return true;
    }

    /**
     * Prepare for XML storage and use as a filename restrict characters during input i.e.
     * alphanumeric and '.' and '-' and '_' Also max of 10 characters
     * 
     * @param s
     * @return
     */
    public static String sanitize(String s) {
        if (FileSystemUtils.isEmpty(s))
            return s;

        // set max name length
        if (s.length() > MAX_NAME_LENGTH)
            s = s.substring(0, MAX_NAME_LENGTH);

        String retval = s.trim().replaceAll(NAME_REGEX, "");
        Log.d(TAG, "sanitized text: " + retval);
        return retval;
    }

    private static final class StartDragLongClickListener implements
            OnLongClickListener {

        @Override
        public boolean onLongClick(View view) {
            ClipData data = ClipData.newPlainText("", "");
            DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(view);
            view.startDrag(data, shadowBuilder, view, 0);
            view.setAlpha(0.25f);
            return true;
        }
    }

    private final class DisplayLabelOnClickListener implements OnClickListener {

        @Override
        public void onClick(View view) {
            if (view != null && view.getTag() != null
                    && view.getTag() instanceof ActionMenuAdapter.ViewHolder) {
                ActionMenuData action = ((ActionMenuAdapter.ViewHolder) view
                        .getTag()).action;
                if (action != null && action.isValid()) {
                    Toast.makeText(AllToolsConfigMenuActivity.this,
                            action.getTitle(),
                            Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            Log.w(TAG, "Invalid view click");
        }
    }

    /**
     * Drag & Drop listener for individual action views
     * 
     * 
     */
    private class ActionListDragListener implements OnDragListener {

        private static final String TAG = "ActionListDragListener";

        @Override
        public boolean onDrag(View targetView, DragEvent event) {
            int action = event.getAction();
            final View droppedView = (View) event.getLocalState();

            //            Log.d(TAG, "onDrag "
            //                    + targetView.getClass().getSimpleName()
            //                    + ":" + event.toString());

            if (targetView == null) {
                Log.w(TAG, "target view is null");
                return false;
            }

            switch (action) {
                case DragEvent.ACTION_DRAG_STARTED:
                    break;
                case DragEvent.ACTION_DRAG_LOCATION:
                    break;
                case DragEvent.ACTION_DRAG_ENTERED:
                    targetView.setBackgroundDrawable(_dragDropBackground);
                    break;
                case DragEvent.ACTION_DRAG_EXITED:
                    targetView.setBackgroundDrawable(null);
                    break;
                case DragEvent.ACTION_DROP:
                    if (droppedView == targetView) {
                        // no-op if dropped on self (same position in same parent list/view)
                        Log.d(TAG, "No Op dropped on self");
                        return true;
                    } else {
                        if (droppedView == null) {
                            Log.w(TAG, "dropped view is null");
                            return false;
                        }

                        Log.d(TAG, "droppedView: "
                                + droppedView.getClass().getName());
                        Log.d(TAG, "targetView: "
                                + targetView.getClass().getName());

                        ActionMenuAdapter.ViewHolder droppedViewHolder = (ActionMenuAdapter.ViewHolder) droppedView
                                .getTag();
                        if (droppedViewHolder == null) {
                            Log.w(TAG, "dropped view holder is null");
                            return false;
                        }

                        ActionMenuData droppedAction = droppedViewHolder.action;
                        if (droppedAction == null) {
                            Log.w(TAG, "dropped action is null");
                            return false;
                        }

                        AbsListView droppedParentView = (AbsListView) droppedView
                                .getParent();
                        if (droppedParentView == null) {
                            Log.w(TAG, "dropped parent is null");
                            return false;
                        }

                        ActionMenuAdapter droppedAdapter = (ActionMenuAdapter) droppedParentView
                                .getAdapter();
                        if (droppedAdapter == null) {
                            Log.w(TAG, "dropped parent adapter is null");
                            return false;
                        }

                        ActionMenuAdapter.ViewHolder targetViewHolder = (ActionMenuAdapter.ViewHolder) targetView
                                .getTag();
                        if (targetViewHolder == null) {
                            Log.w(TAG, "target view holder is null");
                            return false;
                        }

                        ActionMenuData targetAction = targetViewHolder.action;
                        if (targetAction == null) {
                            Log.w(TAG, "target action is null");
                            return false;
                        }

                        AbsListView targetParentView = (AbsListView) targetView
                                .getParent();
                        if (targetParentView == null) {
                            Log.w(TAG, "target parent is null");
                            return false;
                        }

                        ActionMenuAdapter targetAdapter = (ActionMenuAdapter) targetParentView
                                .getAdapter();
                        if (targetAdapter == null) {
                            Log.w(TAG, "target parent adapter is null");
                            return false;
                        }

                        // check if hideable
                        if (targetAdapter == _adapterHidden
                                && !droppedAction.isHideable()) {
                            Toast.makeText(AllToolsConfigMenuActivity.this,
                                    R.string.tool_text13,
                                    Toast.LENGTH_SHORT).show();
                            Log.w(TAG,
                                    "Tool in not hideable: "
                                            + droppedAction.toString());
                            return false;
                        }

                        List<ActionMenuData> targetActions = targetAdapter
                                .getActions();
                        List<ActionMenuData> droppedActions = droppedAdapter
                                .getActions();

                        // verify target index
                        int targetIndex = targetActions.indexOf(targetAction);
                        if (targetIndex < 0
                                || targetIndex > targetActions.size()) {
                            Log.w(TAG, "target index is invalid");
                            return false;
                        }

                        // verify drop index
                        int droppedIndex = droppedActions
                                .indexOf(droppedAction);
                        if (droppedIndex < 0
                                || droppedIndex > droppedActions.size()) {
                            Log.w(TAG, "dropped index is invalid");
                            return false;
                        }

                        // be sure adapter has room
                        if (droppedAdapter != targetAdapter
                                && !targetAdapter.hasRoom(droppedAction)) {
                            Toast.makeText(AllToolsConfigMenuActivity.this,
                                    R.string.tool_text14,
                                    Toast.LENGTH_SHORT).show();
                            Log.w(TAG, "target adapter does not have room: "
                                    + targetAdapter.getClass().getSimpleName());
                            return false;
                        }

                        // cache the op so it can be undone
                        UndoAction undoSnapshot = getSnapshotAction("Move "
                                + droppedAction.getTitle());

                        // special case since actionbaradapter swaps out during add
                        if (droppedAdapter != targetAdapter
                                || targetAdapter != _adapterActionBar) {
                            droppedAdapter.remove(droppedAction);
                        }

                        // add to target adapter
                        if (!targetAdapter.add(targetIndex, droppedAction)) {
                            // should not happen,... but, add it back b/c it was already removed...
                            Log.w(TAG,
                                    "Failed to add: "
                                            + droppedAction.toString()
                                            + " at index: "
                                            + targetIndex);
                            if (droppedAdapter == targetAdapter
                                    && targetAdapter != _adapterActionBar) {
                                droppedActions.add(droppedIndex, droppedAction);
                            }
                            return false;
                        }

                        _undo.push(undoSnapshot);
                        Log.d(TAG,
                                "Moved "
                                        + droppedAdapter.getClass()
                                                .getSimpleName()
                                        + ":"
                                        + droppedAction.getTitle()
                                        + " in front of "
                                        +
                                        targetAdapter.getClass()
                                                .getSimpleName()
                                        + ":"
                                        + targetAction.getTitle());

                        // now redraw lists
                        updateEmptyText();
                        clearBackgrounds();
                        droppedAdapter.notifyDataSetChanged();
                        // if dropping in a different list, then notify both, otherwise dont redraw
                        // twice
                        if (droppedAdapter != targetAdapter)
                            targetAdapter.notifyDataSetChanged();
                    }
                    break;
                case DragEvent.ACTION_DRAG_ENDED:
                    targetView.setBackgroundDrawable(null);
                    if (droppedView == null) {
                        Log.w(TAG, "dropped view is null");
                        return false;
                    }

                    droppedView.post(new Runnable() {
                        @Override
                        public void run() {
                            droppedView.setAlpha(1);
                            clearBackgrounds();
                        }
                    });
                    break;
            }
            return true;
        }
    }

    private void redraw() {
        // Assume only happens via UI thread in response to user touches
        updateEmptyText();
        _adapterActionBar.notifyDataSetChanged();
        _adapterOverflow.notifyDataSetChanged();
        _adapterHidden.notifyDataSetChanged();
    }

    /**
     * Drag & Drop listener for "Empty Text" views
     * 
     * 
     */
    private class ActionEmptyTextDragListener implements OnDragListener {

        private static final String TAG = "ActionEmptyTextDragListener";

        @Override
        public boolean onDrag(View targetView, DragEvent event) {
            int action = event.getAction();
            final View droppedView = (View) event.getLocalState();

            //            Log.d(TAG, "onDrag "
            //                    + targetView.getClass().getSimpleName()
            //                    + ":" + event.toString());

            if (targetView == null) {
                Log.w(TAG, "target view is null");
                return false;
            }

            switch (action) {
                case DragEvent.ACTION_DRAG_STARTED:
                    break;
                case DragEvent.ACTION_DRAG_LOCATION:
                    break;
                case DragEvent.ACTION_DRAG_ENTERED:
                    targetView.setBackgroundDrawable(_dragDropBackground);
                    break;
                case DragEvent.ACTION_DRAG_EXITED:
                    targetView.setBackgroundDrawable(null);
                    break;
                case DragEvent.ACTION_DROP:
                    if (droppedView == targetView) {
                        // no-op if dropped on self (same position in same parent list/view)
                        Log.d(TAG, "No Op dropped on self");
                        return true;
                    } else {
                        if (droppedView == null) {
                            Log.w(TAG, "dropped view is null");
                            return false;
                        }

                        Log.d(TAG, "droppedView: "
                                + droppedView.getClass().getName());
                        Log.d(TAG, "targetView: "
                                + targetView.getClass().getName());

                        ActionMenuAdapter.ViewHolder droppedViewHolder = (ActionMenuAdapter.ViewHolder) droppedView
                                .getTag();
                        if (droppedViewHolder == null) {
                            Log.w(TAG, "dropped view holder is null");
                            return false;
                        }

                        ActionMenuData droppedAction = droppedViewHolder.action;
                        if (droppedAction == null) {
                            Log.w(TAG, "dropped action is null");
                            return false;
                        }

                        AbsListView droppedParentView = (AbsListView) droppedView
                                .getParent();
                        if (droppedParentView == null) {
                            Log.w(TAG, "dropped parent is null");
                            return false;
                        }

                        ActionMenuAdapter droppedAdapter = (ActionMenuAdapter) droppedParentView
                                .getAdapter();
                        if (droppedAdapter == null) {
                            Log.w(TAG, "dropped parent adapter is null");
                            return false;
                        }

                        // find the target adapter, based on the target "empty" text view
                        ActionMenuAdapter targetAdapter = null;
                        if (targetView == _textActionBarEmpty) {
                            targetAdapter = _adapterActionBar;
                        } else if (targetView == _textOverflowEmpty) {
                            targetAdapter = _adapterOverflow;
                        } else if (targetView == _textHiddenEmpty) {
                            targetAdapter = _adapterHidden;
                        }

                        if (targetAdapter == null) {
                            Log.w(TAG, "target parent adapter is null");
                            return false;
                        }

                        // verify dropped index
                        List<ActionMenuData> droppedActions = droppedAdapter
                                .getActions();
                        int droppedIndex = droppedActions
                                .indexOf(droppedAction);
                        if (droppedIndex < 0
                                || droppedIndex > droppedActions.size()) {
                            Log.w(TAG, "dropped index is invalid");
                            return false;
                        }

                        // check if action is hideable
                        if (targetAdapter == _adapterHidden
                                && !droppedAction.isHideable()) {
                            Toast.makeText(AllToolsConfigMenuActivity.this,
                                    R.string.tool_text13,
                                    Toast.LENGTH_SHORT).show();
                            Log.w(TAG,
                                    "Tool in not hideable: "
                                            + droppedAction.toString());
                            return false;
                        }

                        // verify target adapter has room
                        if (droppedAdapter != targetAdapter
                                && !targetAdapter.hasRoom(droppedAction)) {
                            Toast.makeText(AllToolsConfigMenuActivity.this,
                                    R.string.tool_text14,
                                    Toast.LENGTH_SHORT).show();
                            Log.w(TAG, "target adapter does not have room: "
                                    + targetAdapter.getClass().getSimpleName());
                            return false;
                        }

                        // cache the op so it can be undone
                        UndoAction undoSnapshot = getSnapshotAction("Move "
                                + droppedAction.getTitle());

                        // remove from old adapter, then add to target adapter
                        droppedAdapter.remove(droppedAction);
                        if (!targetAdapter.add(droppedAction)) {
                            Log.w(TAG,
                                    "Failed to add: "
                                            + droppedAction.toString());
                            // should not happen,... but, add it back b/c it was already removed...
                            droppedActions.add(droppedIndex, droppedAction);
                            return false;
                        }

                        _undo.push(undoSnapshot);
                        Log.d(TAG, "Moved "
                                + droppedAdapter.getClass().getSimpleName()
                                + ":"
                                + droppedAction.getTitle() + " into empty " +
                                targetAdapter.getClass().getSimpleName());

                        // now redraw lists
                        updateEmptyText();
                        droppedAdapter.notifyDataSetChanged();
                        // if dropping in a different list, then notify both, otherwise dont redraw
                        // twice
                        if (droppedAdapter != targetAdapter)
                            targetAdapter.notifyDataSetChanged();
                    }
                    break;
                case DragEvent.ACTION_DRAG_ENDED:
                    targetView.setBackgroundDrawable(null);
                    droppedView.post(new Runnable() {
                        @Override
                        public void run() {
                            droppedView.setAlpha(1);
                            clearBackgrounds();
                        }
                    });
                    break;
            }
            return true;
        }
    }

    /**
     * Drag & Drop listener for grid/list views (which contain the individual action views)
     * 
     * 
     */
    private class ActionListParentDragListener implements OnDragListener {

        private static final String TAG = "ActionListParentDragListener";

        @Override
        public boolean onDrag(View targetView, DragEvent event) {
            int action = event.getAction();
            final View droppedView = (View) event.getLocalState();

            //            Log.d(TAG, "onDrag "
            //                    + targetView.getClass().getSimpleName()
            //                    + ":" + event.toString());

            switch (action) {
                case DragEvent.ACTION_DRAG_ENTERED:
                    targetView.setBackgroundDrawable(_dragDropBackground);
                    break;
                case DragEvent.ACTION_DRAG_EXITED:
                    targetView.setBackgroundDrawable(null);
                    break;
                case DragEvent.ACTION_DROP:
                    if (droppedView == targetView) {
                        // no-op if dropped on self (same position in same parent list/view)
                        Log.d(TAG, "No Op dropped on self");
                        return true;
                    } else {
                        if (droppedView == null) {
                            Log.w(TAG, "dropped view is null");
                            return false;
                        }
                        if (targetView == null) {
                            Log.w(TAG, "target view is null");
                            return false;
                        }

                        Log.d(TAG, "droppedView: "
                                + droppedView.getClass().getName());
                        Log.d(TAG, "targetView: "
                                + targetView.getClass().getName());

                        ActionMenuAdapter.ViewHolder droppedViewHolder = (ActionMenuAdapter.ViewHolder) droppedView
                                .getTag();
                        if (droppedViewHolder == null) {
                            Log.w(TAG, "dropped view holder is null");
                            return false;
                        }

                        ActionMenuData droppedAction = droppedViewHolder.action;
                        if (droppedAction == null) {
                            Log.w(TAG, "dropped action is null");
                            return false;
                        }

                        AbsListView droppedParentView = (AbsListView) droppedView
                                .getParent();
                        if (droppedParentView == null) {
                            Log.w(TAG, "dropped parent is null");
                            return false;
                        }

                        ActionMenuAdapter droppedAdapter = (ActionMenuAdapter) droppedParentView
                                .getAdapter();
                        if (droppedAdapter == null) {
                            Log.w(TAG, "dropped parent adapter is null");
                            return false;
                        }

                        AbsListView targetListView = (AbsListView) targetView;

                        ActionMenuAdapter targetAdapter = (ActionMenuAdapter) targetListView
                                .getAdapter();
                        if (targetAdapter == null) {
                            Log.w(TAG, "target parent adapter is null");
                            return false;
                        }

                        // verify dropped index
                        List<ActionMenuData> droppedActions = droppedAdapter
                                .getActions();
                        int droppedIndex = droppedActions
                                .indexOf(droppedAction);
                        if (droppedIndex < 0
                                || droppedIndex > droppedActions.size()) {
                            Log.w(TAG, "dropped index is invalid");
                            return false;
                        }

                        // check if action is hideable
                        if (targetAdapter == _adapterHidden
                                && !droppedAction.isHideable()) {
                            Toast.makeText(AllToolsConfigMenuActivity.this,
                                    R.string.tool_text13,
                                    Toast.LENGTH_SHORT).show();
                            Log.w(TAG,
                                    "Tool in not hideable: "
                                            + droppedAction.toString());
                            return false;
                        }

                        // verify target adapter does not already contain dropped action
                        if (targetAdapter.contains(droppedAction)) {
                            Log.w(TAG, "Tool already in adapter: "
                                    + droppedAction.toString());
                            return false;
                        }

                        // verify target adapter has room
                        if (droppedAdapter != targetAdapter
                                && !targetAdapter.hasRoom(droppedAction)) {
                            Toast.makeText(AllToolsConfigMenuActivity.this,
                                    R.string.tool_text14,
                                    Toast.LENGTH_SHORT).show();
                            Log.w(TAG, "target adapter does not have room: "
                                    + targetAdapter.getClass().getSimpleName());
                            return false;
                        }

                        // cache the op so it can be undone
                        UndoAction undoSnapshot = getSnapshotAction("Move "
                                + droppedAction.getTitle());

                        // remove from dropped adapter, add to target adapter
                        droppedAdapter.remove(droppedAction);
                        if (!targetAdapter.add(droppedAction)) {
                            Log.w(TAG,
                                    "Failed to add: "
                                            + droppedAction.toString());
                            // should not happen,... but, add it back b/c it was already removed...
                            droppedActions.add(droppedIndex, droppedAction);
                            return false;
                        }

                        // cache so op can be undone
                        _undo.push(undoSnapshot);
                        Log.d(TAG, "Moved "
                                + droppedAdapter.getClass().getSimpleName()
                                + ":"
                                + droppedAction.getTitle() + " to back of " +
                                targetAdapter.getClass().getSimpleName());

                        // now redraw lists
                        updateEmptyText();
                        clearBackgrounds();
                        droppedAdapter.notifyDataSetChanged();
                        // if dropping in a different list, then notify both, otherwise dont redraw
                        // twice
                        if (droppedAdapter != targetAdapter)
                            targetAdapter.notifyDataSetChanged();

                        return true;
                    }
                case DragEvent.ACTION_DRAG_ENDED:
                    targetView.setBackgroundDrawable(null);
                    droppedView.post(new Runnable() {
                        @Override
                        public void run() {
                            droppedView.setAlpha(1);
                            clearBackgrounds();
                        }
                    });
                    break;
            }

            return true;
        }
    }

    private void clearBackgrounds() {
        // entered drag on specific action view, be sure background got cleared on
        // parent list/grid view
        _gridviewActionBar.setBackgroundDrawable(null);
        _gridviewHidden.setBackgroundDrawable(null);
        _listviewOverflow.setBackgroundDrawable(null);
    }

    private AtakActionBarListData getSnapshotActionBars() {
        // TODO probably not as efficient as we could be... lots of copy ctor, for MOVE op
        // could store source list/index and just put it back...
        // Also if we store in DB, may not need to modify/track/RAM all action bars, just the active
        // one...

        // copy ctor from baseline
        AtakActionBarListData actionBars = new AtakActionBarListData(
                _baselineActionBars);

        // now apply current config for current action bar & orientation mode
        AtakActionBarMenuData menu = actionBars.getActionBar(_prefs, this);
        if (menu != null) {
            final List<ActionMenuData> actions = menu.getActions();
            actions.clear();

            if (_adapterActionBar != null && _adapterActionBar.getCount() > 0) {
                for (ActionMenuData action : _adapterActionBar.getActions()) {
                    menu.add(new ActionMenuData(action));
                }
            }

            if (_adapterOverflow != null && _adapterOverflow.getCount() > 0) {
                for (ActionMenuData action : _adapterOverflow.getActions()) {
                    menu.add(new ActionMenuData(action));
                }
            }

            if (_adapterHidden != null && _adapterHidden.getCount() > 0) {
                for (ActionMenuData action : _adapterHidden.getActions()) {
                    menu.add(new ActionMenuData(action));
                }
            }
        }

        return actionBars;
    }

    private UndoAction getSnapshotAction(String description) {
        return new SnapshotAction(description, getSnapshotActionBars());
    }

    /**
     * Revert to an action bar snapshot
     * 
     * 
     */
    public class SnapshotAction implements UndoAction {
        final AtakActionBarListData _snapshot;
        final String _description;

        public SnapshotAction(String description,
                AtakActionBarListData atakActionBars) {
            _snapshot = atakActionBars;
            _description = description;
        }

        @Override
        public boolean undo() {
            return loadActionBar(_snapshot);
        }

        @Override
        public String getDescription() {
            return _description;
        }
    }

    public static InputFilter[] getNameFilters(final Activity act) {
        return new InputFilter[] {
                new InputFilter.LengthFilter(MAX_NAME_LENGTH),
                new InputFilter() {
                    @Override
                    public CharSequence filter(CharSequence source,
                            int s, int e, Spanned dest, int dstart, int dend) {
                        for (int i = s; i < e; i++) {
                            if (Character.toString(source.charAt(i))
                                    .matches(NAME_REGEX)) {
                                Toast.makeText(act, R.string.invalid_input,
                                        Toast.LENGTH_SHORT).show();
                                return "";
                            }
                        }
                        return null;
                    }
                }
        };
    }
}
