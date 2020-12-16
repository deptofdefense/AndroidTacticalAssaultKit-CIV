
package com.atakmap.android.munitions;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ToggleButton;
import android.graphics.Color;

import com.atakmap.android.dropdown.DropDown;
import com.atakmap.android.munitions.util.MunitionsHelper;
import com.atakmap.android.util.SimpleItemSelectedListener;

import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.util.Circle;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/**
 * 
 */

public class DangerCloseReceiver extends DropDownReceiver implements
        View.OnClickListener, DropDown.OnStateListener {

    public static final String TAG = "DangerCloseReceiver";

    public static final String ADD = "com.atakmap.android.munitions.ADD";
    public static final String REMOVE = "com.atakmap.android.munitions.REMOVE";
    public static final String REMOVE_WEAPON = "com.atakmap.android.munitions.REMOVE_WEAPON";
    public static final String TOGGLE = "com.atakmap.android.munitions.TOGGLE";
    public static final String OPEN = "com.atakmap.android.munitions.OPEN";
    public static final String REMOVE_ALL = "com.atakmap.android.munitions.REMOVE_MUNITION_FROM_ALL";
    public static final String TOGGLE_LABELS = "com.atakmap.android.munitions.LABEL_TOGGLE";

    public enum VIEW_MODE {
        CUSTOM_VIEW,
        DEFAULT,
        FAVORITES_VIEW,
        ADD_FAV,
        REMOVE_FAV,
        REMOVE_CUSTOM
    }

    /**
     * Implement in order to obtain any external munition information.
     */
    public interface ExternalMunitionQuery {
        /**
         * Retreives a munition list in xml format, otherwise null if no munitions exist.
         */
        String queryMunitions();
    }

    private ExternalMunitionQuery emq;

    private VIEW_MODE mode = VIEW_MODE.DEFAULT;
    private final Stack<VIEW_MODE> previousMode = new Stack<>();

    public final static String SEND_GENERATE_FLIGHT_MUNITIONS_REQUEST = "com.atakmap.android.dangerClose.DangerCloseReceiver.GENERATE_FLIGHT_MUNITIONS_REQUEST";
    public final static String EXTRA_FLIGHT_MUNITIONS_GENERATED = "com.atakmap.android.dangerClose.DangerCloseReceiver.FLIGHT_MUNITIONS_GENERATED";
    public final static String EXTRA_FLIGHT_MUNITIONS_DATA = "com.atakmap.android.dangerClose.DangerCloseReceiver.FLIGHT_MUNITIONS_DATA";

    private final MapView _mapView;
    private final Context _context;
    private final MapGroup _mapGroup;

    private String target;
    private DangerCloseAdapter _dcAdapter;
    private String _fromLine;
    private String _category;

    private TextView titleBar;
    private RelativeLayout customToolbar;
    private RelativeLayout favortesToolbar;

    private ImageButton addFav;
    private ImageButton removeFav;
    private ImageButton addCustom;
    private ImageButton removeCustom;
    private Button done;
    private ToggleButton hide;
    private ImageButton back;
    private Button delete;
    private ImageButton exit;
    private final SharedPreferences _prefs;

    private Spinner menu_options;

    private static DangerCloseReceiver _instance;

    public DangerCloseReceiver(final MapView mapView) {
        super(mapView);
        _mapView = mapView;
        _context = mapView.getContext();
        _mapGroup = mapView.getRootGroup().findMapGroup("Weapons");
        _prefs = PreferenceManager
                .getDefaultSharedPreferences(_mapView
                        .getContext());

        _instance = this;
    }

    public void setExternalMunitionQuery(ExternalMunitionQuery emq) {
        this.emq = emq;
    }

    public static synchronized DangerCloseReceiver getInstance() {
        return _instance;
    }

    @Override
    public void disposeImpl() {
    }

    @Override
    public void onReceive(final Context ignoreCtx, Intent intent) {
        String action = intent.getAction();
        if (FileSystemUtils.isEmpty(action))
            return;

        // Add weapon to target
        switch (action) {
            case ADD: {
                String tgt = intent.getStringExtra("targetUID");
                String wpn = intent.getStringExtra("weaponName");
                int inner = intent.getIntExtra("inner", -1);
                int outer = intent.getIntExtra("outer", -1);
                boolean defaultVis = intent.getBooleanExtra(
                        "defaultVisibility", true);
                String fromLine = intent.getStringExtra("fromLine");

                if (inner == -1 || outer == -1)
                    Log.d(TAG, "error occurred, inner or outer set to -1");

                //Mortar tool doesn't send out this attribute
                if (fromLine == null) {
                    fromLine = "";
                }
                RangeRing rr = (RangeRing) _mapView.getRootGroup()
                        .deepFindItem("uid",
                                tgt + "." + wpn + "." + fromLine);

                if (rr != null) {
                    rr.remove();
                    rr = null;
                }

                MapItem mi = _mapView.getRootGroup().deepFindItem("uid", tgt);

                Log.d(TAG,
                        "creating a danger close ring for: " + tgt + "named: "
                                + wpn + " inner: " + inner + " outer: "
                                + outer);

                if (mi instanceof PointMapItem) {
                    rr = new RangeRing(_mapView, _mapGroup,
                            (PointMapItem) mi, wpn, inner, outer, fromLine);
                }
                if (!defaultVis) {
                    if (rr != null && rr.get_standing() != null) {
                        rr.get_standing().setVisible(false);
                    }
                    if (rr != null && rr.get_prone() != null) {
                        rr.get_prone().setVisible(false);
                    }
                }
                break;
            }

            // Toggle range ring
            case TOGGLE: {
                String weaponTarget = intent.getStringExtra("target");
                MapItem mi = _mapView.getRootGroup().deepFindUID(weaponTarget);
                if (!(mi instanceof PointMapItem))
                    return;
                PointMapItem tar = (PointMapItem) mi;
                String weaponName = intent.getStringExtra("name");
                String categoryName = intent.getStringExtra("category");
                String description = intent.getStringExtra("description");
                int inner = intent.getIntExtra("innerRange", 0);
                int outer = intent.getIntExtra("outerRange", 0);
                boolean remove = intent.getBooleanExtra("remove", false);
                boolean persist = intent.getBooleanExtra("persist", true);
                String fromLine = intent.getStringExtra("fromLine");
                createDangerClose(tar, weaponName, categoryName, description,
                        inner, outer, remove, fromLine, persist);
                break;
            }

            // Remove munition from target
            case REMOVE:
            case REMOVE_WEAPON: {
                String tgt = intent.getStringExtra("targetUID");
                String wpn = intent.getStringExtra("weaponName");
                String fromLine = intent.getStringExtra("fromLine");

                //Mortar tool does not send out fromLine attribute
                if (wpn == null)
                    wpn = "";
                if (fromLine == null)
                    fromLine = "";

                String rangeUID;
                if (wpn.isEmpty() && fromLine.isEmpty())
                    rangeUID = tgt;
                else
                    rangeUID = tgt + "." + wpn + "." + fromLine;

                MapItem item = _mapView.getRootGroup().deepFindUID(rangeUID);
                if (item instanceof RangeRing) {
                    RangeRing rr = (RangeRing) item;
                    PointMapItem target = rr.getAnchorItem();
                    if (target != null) {
                        MunitionsHelper helper = new MunitionsHelper(_mapView,
                                target, fromLine);
                        helper.removeRangeRing(rr,
                                action.equals(REMOVE_WEAPON));
                        helper.persist();
                    }
                }
                break;
            }

            // Remove munition from all targets
            case REMOVE_ALL:
                removeMunitionFromAllTargets(intent.getStringExtra("weapon"),
                        intent.getStringExtra("category"));

                break;

            // Toggle range ring labels
            case TOGGLE_LABELS:
                String uid = intent.getStringExtra("uid");
                if (FileSystemUtils.isEmpty(uid))
                    return;
                MapItem foundItem = _mapView.getRootGroup().deepFindUID(uid);
                if (!(foundItem instanceof Circle))
                    return;
                Circle circ = (Circle) foundItem;
                boolean labelsOn = circ.hasMetaValue("labels_on");
                if (labelsOn) {
                    circ.removeMetaData("labels_on");
                    circ.setLabel(null);
                } else {
                    circ.setMetaBoolean("labels_on", true);
                    int radiusInMeters = (int) Math.round(circ.getRadius());
                    if (radiusInMeters >= 1000)
                        circ.setLabel(radiusInMeters / 1000 + "km");
                    else
                        circ.setLabel(radiusInMeters + "m");
                }
                break;

            // Open REDs and MSDs drop-down
            case OPEN:
                target = null;
                if (intent.getStringExtra("targetUID") != null) {
                    target = intent.getStringExtra("targetUID");
                }

                _fromLine = intent.getStringExtra("fromLine");
                _category = intent.getStringExtra("category");

                _dcAdapter = new DangerCloseAdapter(_context, _mapView, target,
                        _fromLine);

                try {
                    if (emq != null) {
                        String muni = emq.queryMunitions();
                        if (muni != null)
                            _dcAdapter.parseFlightMunitions(muni);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "error: ", e);
                }

                LayoutInflater inflater = LayoutInflater.from(_context);

                final LinearLayout content = (LinearLayout) inflater.inflate(
                        R.layout.danger_close_view, _mapView, false);

                favortesToolbar = content
                        .findViewById(R.id.favorites_toolbar);
                customToolbar = content
                        .findViewById(R.id.custom_toolbar);

                addFav = content
                        .findViewById(R.id.danger_add_fav_button);
                removeFav = content
                        .findViewById(R.id.danger_remove_fav_button);
                addCustom = content
                        .findViewById(R.id.danger_add_custom_button);
                removeCustom = content
                        .findViewById(R.id.danger_remove_custom_button);
                done = content
                        .findViewById(R.id.danger_done_button);
                exit = content
                        .findViewById(R.id.danger_exit);

                //set up the drop down menu

                String[] menu_opts = new String[] {
                        _context.getString(R.string.home),
                        _context.getString(R.string.favorites),
                        _context.getString(R.string.dangerclose_text2)
                };

                menu_options = content
                        .findViewById(R.id.menu_spinner);
                final ArrayAdapter<Object> adapter = new ArrayAdapter<Object>(
                        _context,
                        R.layout.spinner_text_view, menu_opts);
                adapter.setDropDownViewResource(
                        android.R.layout.simple_spinner_dropdown_item);
                menu_options.setAdapter(adapter);

                menu_options.setSelection(0, false);
                View view = menu_options.getSelectedView();
                if (view instanceof TextView)
                    ((TextView) view).setTextColor(Color.WHITE);

                menu_options
                        .setOnItemSelectedListener(
                                new SimpleItemSelectedListener() {
                                    @Override
                                    public void onItemSelected(
                                            AdapterView<?> parent,
                                            View view, int position, long id) {

                                        if (view instanceof TextView)
                                            ((TextView) view)
                                                    .setTextColor(Color.WHITE);

                                        parent.getItemAtPosition(position);
                                        _prefs.edit().putInt("msdred.screen",
                                                position).apply();

                                        if (position == 0) { //Home
                                            if (!_dcAdapter.isAddMode()
                                                    && !_dcAdapter
                                                            .isRemoveMode()) {
                                                _dcAdapter.ascendToRoot();
                                                setCurrentMode(
                                                        VIEW_MODE.DEFAULT);
                                            } else {
                                                _dcAdapter.ascendToRoot();
                                            }
                                        } else if (position == 1) { //Favorites
                                            if (!_dcAdapter.isAddMode()) {
                                                _dcAdapter.getFavorites();
                                                back.setVisibility(View.GONE);
                                                setCurrentMode(
                                                        VIEW_MODE.FAVORITES_VIEW);
                                            }
                                        } else if (position == 2) { //Customs
                                            _dcAdapter.getCustoms();
                                            back.setVisibility(View.GONE);
                                            if (!_dcAdapter.isAddMode()) {
                                                setCurrentMode(
                                                        VIEW_MODE.CUSTOM_VIEW);
                                            }
                                        }
                                    }

                                });

                hide = content
                        .findViewById(R.id.danger_toggle_button);
                MapGroup root = _mapView.getRootGroup();
                MapItem targetItem = root.deepFindItem("uid", target);

                if (targetItem instanceof PointMapItem) {
                    MunitionsHelper helper = new MunitionsHelper(_mapView,
                            (PointMapItem) targetItem, _fromLine);
                    // At this point we should set the visibility to true by default
                    // Meaning if this drop-down is opened for a specific line type
                    // the visibility is turned on (unless it was explicitly turn off)
                    boolean visible = helper.isVisible(true);
                    helper.setVisible(visible);
                    hide.setChecked(visible);
                }

                back = content
                        .findViewById(R.id.danger_back_button);
                delete = content
                        .findViewById(R.id.danger_delete);
                titleBar = content
                        .findViewById(R.id.danger_title_textview);

                delete.setOnClickListener(this);
                exit.setOnClickListener(this);
                addFav.setOnClickListener(this);
                addCustom.setOnClickListener(this);
                removeFav.setOnClickListener(this);
                removeCustom.setOnClickListener(this);
                done.setOnClickListener(this);

                hide.setOnCheckedChangeListener(new OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton cb,
                            boolean checked) {
                        setItemsVisible(_fromLine, checked);
                    }
                });

                back.setOnClickListener(this);

                final ListView lView = content
                        .findViewById(R.id.danger_list);

                lView.setAdapter(_dcAdapter);

                lView.setOnItemClickListener(new OnItemClickListener() {

                    @Override
                    public void onItemClick(AdapterView<?> parent, View v,
                            int pos, long id) {

                        if (_dcAdapter.getCurrentNode().equals("munitions")) {
                            back.setVisibility(View.VISIBLE);
                            hide.setVisibility(View.GONE);
                        }

                        // if we can't descend that means it is a weapon so go ahead and toggle weapon
                        // on or off
                        if (!_dcAdapter.descend(pos)) {
                            if (!_dcAdapter.isAddMode()
                                    && !_dcAdapter.isRemoveMode()
                                    && !_dcAdapter.isRemoveCustomMode()) {
                                CheckBox box = v
                                        .findViewById(R.id.dangerCheck);
                                box.setChecked(!box.isChecked());
                            } else if (_dcAdapter.isAddMode()) {
                                CheckBox box = v
                                        .findViewById(R.id.addFavorite);
                                box.setChecked(!box.isChecked());
                            } else if (_dcAdapter.isRemoveMode()) {
                                CheckBox box = v
                                        .findViewById(R.id.removeFavorite);
                                box.setChecked(!box.isChecked());
                            } else {
                                CheckBox box = v
                                        .findViewById(R.id.removeCustom);
                                box.setChecked(!box.isChecked());
                            }
                        } else {
                            lView.setSelectionAfterHeaderView();
                        }

                        if (!_dcAdapter.isAddMode()
                                && !_dcAdapter.isRemoveMode()
                                && !_dcAdapter.isRemoveCustomMode()) {
                            TextView title = content
                                    .findViewById(R.id.danger_title_textview);
                            title.setText(getTitle());
                        }

                    }
                });

                lView.setOnItemLongClickListener(
                        new AdapterView.OnItemLongClickListener() {
                            @Override
                            public boolean onItemLongClick(
                                    AdapterView<?> parent,
                                    View view, int pos, long id) {
                                if (!_dcAdapter.descend(pos)) {
                                    _dcAdapter.buildInfoDialog(pos);
                                    return true;
                                }
                                return false;
                            }
                        });
                lView.invalidateViews();
                showDropDown(content, DropDownReceiver.HALF_WIDTH, FULL_HEIGHT,
                        FULL_WIDTH, FIVE_TWELFTHS_HEIGHT, this);

                // in order to not be disruptive for back porting
                if (_category != null) {
                    if (_category.equalsIgnoreCase("Fixed")) {
                        _dcAdapter.descend(1);
                        setCurrentMode(VIEW_MODE.DEFAULT);
                        back.setVisibility(View.VISIBLE);
                    } else if (_category.equalsIgnoreCase("Rotary")) {
                        _dcAdapter.descend(2);
                        setCurrentMode(VIEW_MODE.DEFAULT);
                        back.setVisibility(View.VISIBLE);
                    } else if (_category.equalsIgnoreCase("Unguided Mortar")) {
                        _dcAdapter.descend(3);
                        setCurrentMode(VIEW_MODE.DEFAULT);
                        back.setVisibility(View.VISIBLE);
                    } else if (_category.equalsIgnoreCase("Unguided Cannon")) {
                        _dcAdapter.descend(4);
                        setCurrentMode(VIEW_MODE.DEFAULT);
                        back.setVisibility(View.VISIBLE);
                    } else if (_category.equalsIgnoreCase("Precision Guided")) {
                        _dcAdapter.descend(5);
                        setCurrentMode(VIEW_MODE.DEFAULT);
                        back.setVisibility(View.VISIBLE);
                    } else if (_category.equalsIgnoreCase("Naval Gunfire")) {
                        _dcAdapter.descend(6);
                        setCurrentMode(VIEW_MODE.DEFAULT);
                        back.setVisibility(View.VISIBLE);
                    } else if (_category.equalsIgnoreCase("Tomahawk")) {
                        _dcAdapter.descend(7);
                        setCurrentMode(VIEW_MODE.DEFAULT);
                        back.setVisibility(View.VISIBLE);
                    } else if (_category.equalsIgnoreCase("Nato")) {
                        _dcAdapter.descend(8);
                        setCurrentMode(VIEW_MODE.DEFAULT);
                        back.setVisibility(View.VISIBLE);
                    } else if (_category.equalsIgnoreCase("Antiaircraft")) {
                        _dcAdapter.descend(9);
                        setCurrentMode(VIEW_MODE.DEFAULT);
                        back.setVisibility(View.VISIBLE);
                    } else if (_category.equalsIgnoreCase("Surface to Air")) {
                        _dcAdapter.descend(10);
                        setCurrentMode(VIEW_MODE.DEFAULT);
                        back.setVisibility(View.VISIBLE);
                    } else if (_category
                            .equalsIgnoreCase("Minimum Safe Distances")) {
                        _dcAdapter.descend(11);
                        setCurrentMode(VIEW_MODE.DEFAULT);
                        back.setVisibility(View.VISIBLE);
                    }
                } else {
                    // category is null, go ahead an pick up where you left
                    // off last.
                    final int screen = _prefs.getInt("msdred.screen", 0);
                    if (screen > 0)
                        menu_options.setSelection(screen, false);

                }
                break;
        }
    }

    @Override
    public void onClick(View v) {

        // Delete all REDs and MSDs
        if (v == delete)
            removeAllItems(_fromLine);

        // Add favorites mode
        else if (v == addFav) {
            _dcAdapter.toggleAddMode();
            _dcAdapter.ascendToRoot();
            menu_options.setSelection(0); //set option to "Home"
            setCurrentMode(VIEW_MODE.ADD_FAV);
        }

        // Remove favorites mode
        else if (v == removeFav) {
            _dcAdapter.toggleRemoveMode();
            //running getFavorites again in order to refresh
            //list with the 'removeFavorites' checkboxes
            _dcAdapter.getFavorites();
            setCurrentMode(VIEW_MODE.REMOVE_FAV);
        }

        // Custom creator dialog
        else if (v == addCustom) {
            CustomCreator customCreator = new CustomCreator(
                    _context, _dcAdapter);
            customCreator.buildTypeDialog();
        }

        // Remove customs mode
        else if (v == removeCustom) {
            _dcAdapter.toggleRemoveCustomMode();
            //calling getCustoms() again to refresh
            //the customs list with the remove custom checkboxes
            _dcAdapter.getCustoms();
            setCurrentMode(VIEW_MODE.REMOVE_CUSTOM);
        }

        // Exit mode
        else if (v == done) {
            if (mode == VIEW_MODE.REMOVE_CUSTOM)
                _dcAdapter.removeCustoms();

            if (mode == VIEW_MODE.ADD_FAV)
                menu_options.setSelection(1);

            togglePreviousMode();
            if (previousMode.size() > 0)
                mode = previousMode.pop();

            done.setVisibility(View.GONE);
            setViewToMode();
        }

        // Back out of list/mode
        else if (v == back) {
            // TODO: set title
            _dcAdapter.ascend();
            if (_dcAdapter.atRoot())
                back.setVisibility(View.GONE);

            if (!_dcAdapter.isAddMode() && !_dcAdapter.isRemoveMode()) {
                setCurrentMode(VIEW_MODE.DEFAULT);
            }
        }

        // Exit drop-down
        else if (v == exit)
            closeDropDown();
    }

    @Override
    public void onDropDownSelectionRemoved() {
    }

    @Override
    public void onDropDownClose() {
    }

    @Override
    public void onDropDownSizeChanged(double width, double height) {
    }

    @Override
    public void onDropDownVisible(boolean v) {
        if (!v && !FileSystemUtils.isEmpty(target)) {
            MapItem targetItem = _mapView.getRootGroup().deepFindUID(target);
            if (targetItem != null)
                targetItem.persist(_mapView.getMapEventDispatcher(),
                        null, getClass());
        }
    }

    protected void removeMunitionFromAllTargets(String weapon,
            String category) {
        // remove any range rings referencing weapon, remove weapon from associated target
        String ringTargetUID;
        for (MapItem i : _mapGroup.getItems()) {
            if (!(i instanceof RangeRing) || !((RangeRing) i).getWeaponName()
                    .equals(weapon))
                continue;
            boolean bRemoveRangeRing = false;
            // retrieve target associated with weapon range ring
            ringTargetUID = i.getMetaString("target", "");
            PointMapItem tar = (PointMapItem) _mapView.getRootGroup()
                    .deepFindUID(ringTargetUID);
            if (tar != null) {
                Map<String, Object> metaData = new HashMap<>();
                // retrieve target meta data
                tar.getMetaData(metaData);
                // retrieve munitions meta data
                Map<String, Object> munitionsMap = (Map<String, Object>) metaData
                        .get("targetMunitions");
                if (munitionsMap != null) {
                    // make sure weapon is removed from the specified category
                    Map<String, Object> categoryMap = (Map<String, Object>) munitionsMap
                            .get(category);
                    if (categoryMap != null) {
                        Map<String, Object> weaponMap = (Map<String, Object>) categoryMap
                                .get(weapon);
                        if (weaponMap != null) {
                            // remove weapon
                            categoryMap.remove(weapon);
                            bRemoveRangeRing = true;
                            // Clear out category if empty
                            if (categoryMap.isEmpty()) {
                                //remove category from taget munitions
                                munitionsMap.remove(category);
                            }
                        }
                    }
                }
            }

            // if matching target weapon removed, remove associated range ring
            if (bRemoveRangeRing)
                ((RangeRing) i).remove();
        }
    }

    /**
     * Remove all items is based on how the dropdown was opened.
     * fromLine determines where the dropdown was opened from.
     * @param fromLine - Represents if the call came from a fiveline or a nineline
     */
    protected void removeAllItems(final String fromLine) {
        MapItem targetItem = _mapView.getRootGroup().deepFindUID(target);
        if (target == null || !(targetItem instanceof PointMapItem)
                || _mapGroup == null)
            return;

        Log.d(TAG, "group size: " + _mapGroup.getItemCount());
        MunitionsHelper helper = new MunitionsHelper(_mapView,
                (PointMapItem) targetItem, fromLine);
        helper.removeAllWeapons();

        Log.d(TAG, "group size: " + _mapGroup.getItemCount());
        _dcAdapter.removeAll();
    }

    /**
     * Set munitions visibility
     * @param fromLine Line type
     * @param visible Visibility
     */
    private void setItemsVisible(String fromLine, boolean visible) {
        MapGroup group = _mapView.getRootGroup().findMapGroup(
                "Weapons");
        MapGroup root = _mapView.getRootGroup();
        MapItem targetItem = root.deepFindUID(target);

        // if any of these are null, return

        if (target == null || !(targetItem instanceof PointMapItem)
                || group == null)
            return;

        MunitionsHelper helper = new MunitionsHelper(_mapView,
                (PointMapItem) targetItem, fromLine);
        helper.setVisible(visible);
        helper.setRangeRingsVisible(visible);
    }

    private String getTitle() {
        StringBuilder result = new StringBuilder();
        String node = _dcAdapter.getCurrentNode();

        if (!node.equals("munitions")) {
            //if the name has an additional part in parenthesis
            //we're going to section it off to improve the UI
            if (node.contains("(")) {
                int start = node.indexOf("(");
                //wrap substring without parenthesis
                String nameSubstring = node.substring(start);
                //remove the substring from name
                node = node.replace(" " + nameSubstring, "");

            }
            result.append(node.replace("_", " "));
        } else
            result.append(_context.getString(R.string.reds_and_msds));

        return result.toString();
    }

    /**
     * Toggle the previous mode off
     */
    private void togglePreviousMode() {
        if (mode == VIEW_MODE.REMOVE_CUSTOM) {
            _dcAdapter.toggleRemoveCustomMode();
            menu_options.setVisibility(View.VISIBLE);
        } else if (mode == VIEW_MODE.REMOVE_FAV) {
            _dcAdapter.toggleRemoveMode();
            menu_options.setVisibility(View.VISIBLE);
        } else if (mode == VIEW_MODE.ADD_FAV)
            _dcAdapter.toggleAddMode();
    }

    private void setCurrentMode(VIEW_MODE m) {
        previousMode.push(mode);
        mode = m;
        setViewToMode();
    }

    /**
     * Set the buttons in the view to the corresponding mode
     */
    private void setViewToMode() {
        switch (mode) {
            case REMOVE_CUSTOM:
                titleBar.setText(R.string.dangerclose_text3);

                delete.setVisibility(View.GONE);
                exit.setVisibility(View.GONE);
                menu_options.setVisibility(View.GONE);
                addCustom.setVisibility(View.GONE);
                back.setVisibility(View.GONE);
                removeCustom.setVisibility(View.GONE);
                done.setVisibility(View.VISIBLE);
                break;

            case DEFAULT:
                titleBar.setText(getTitle());

                delete.setVisibility(View.VISIBLE);
                exit.setVisibility(View.VISIBLE);
                customToolbar.setVisibility(View.GONE);
                favortesToolbar.setVisibility(View.GONE);
                addCustom.setVisibility(View.GONE);
                addFav.setVisibility(View.GONE);
                removeFav.setVisibility(View.GONE);
                removeCustom.setVisibility(View.GONE);
                hide.setVisibility(View.GONE);

                if (_dcAdapter.atRoot()) {
                    hide.setVisibility(View.VISIBLE);
                }

                break;

            case CUSTOM_VIEW:
                titleBar.setText(R.string.dangerclose_text4);

                _dcAdapter.getCustoms();

                delete.setVisibility(View.VISIBLE);
                exit.setVisibility(View.VISIBLE);
                favortesToolbar.setVisibility(View.GONE);
                customToolbar.setVisibility(View.VISIBLE);
                addCustom.setVisibility(View.VISIBLE);
                removeCustom.setVisibility(View.VISIBLE);
                hide.setVisibility(View.GONE);
                break;

            case FAVORITES_VIEW:
                titleBar.setText(R.string.favorites);

                _dcAdapter.getFavorites();

                delete.setVisibility(View.VISIBLE);
                exit.setVisibility(View.VISIBLE);
                customToolbar.setVisibility(View.GONE);
                favortesToolbar.setVisibility(View.VISIBLE);
                addFav.setVisibility(View.VISIBLE);
                removeFav.setVisibility(View.VISIBLE);
                hide.setVisibility(View.GONE);
                break;

            case REMOVE_FAV:
                titleBar.setText(R.string.dangerclose_text3);

                delete.setVisibility(View.GONE);
                exit.setVisibility(View.GONE);
                menu_options.setVisibility(View.GONE);
                favortesToolbar.setVisibility(View.GONE);
                addFav.setVisibility(View.GONE);
                removeFav.setVisibility(View.GONE);
                back.setVisibility(View.GONE);
                done.setVisibility(View.VISIBLE);
                break;

            case ADD_FAV:
                titleBar.setText(R.string.dangerclose_text5);

                delete.setVisibility(View.GONE);
                exit.setVisibility(View.GONE);
                favortesToolbar.setVisibility(View.GONE);
                addFav.setVisibility(View.GONE);
                removeFav.setVisibility(View.GONE);
                back.setVisibility(View.GONE);
                done.setVisibility(View.VISIBLE);
                break;
        }
    }

    public boolean createDangerClose(PointMapItem tar, String weaponName,
            String categoryName, String description, int inner, int outer,
            boolean remove, String fromLine, boolean persist) {
        MunitionsHelper helper = new MunitionsHelper(_mapView, tar, fromLine);
        if (!remove)
            helper.addWeapon(categoryName, weaponName, description, inner,
                    outer);
        else
            helper.removeWeapon(categoryName, weaponName);
        if (persist)
            helper.persist();
        return true;
    }
}
