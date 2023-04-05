
package com.atakmap.android.hierarchy;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;

import com.atakmap.android.gui.TileButtonDialog;
import com.atakmap.android.gui.TileButtonDialog.TileButton;
import com.atakmap.android.gui.drawable.CheckBoxDrawable;
import com.atakmap.android.hierarchy.filters.AuthorFilter;
import com.atakmap.android.hierarchy.filters.AutoSendFilter;
import com.atakmap.android.importexport.ExportDialog;
import com.atakmap.android.importexport.send.SendDialog;
import com.atakmap.android.util.AfterTextChangedWatcher;
import com.atakmap.android.util.SimpleItemSelectedListener;

import com.atakmap.android.hierarchy.HierarchyListItem.Sort;
import com.atakmap.android.hierarchy.HierarchyListItem.SortAlphabet;
import com.atakmap.android.hierarchy.action.ItemClick;
import com.atakmap.android.hierarchy.items.AbstractHierarchyListItem2;
import com.atakmap.android.hierarchy.items.MapItemUser;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.atakmap.android.cot.CotMapAdapter;
import com.atakmap.android.dropdown.DropDown.OnStateListener;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.hierarchy.action.GoTo;
import com.atakmap.android.hierarchy.action.Visibility;
import com.atakmap.android.hierarchy.filters.FOVFilter;
import com.atakmap.android.importexport.ExportMarshal;
import com.atakmap.android.importexport.ExporterManager;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapOverlayManager;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.menu.MapMenuReceiver;
import com.atakmap.android.toolbar.IToolbarExtension;
import com.atakmap.android.toolbar.Tool;
import com.atakmap.android.toolbar.ToolbarBroadcastReceiver;
import com.atakmap.android.tools.ActionBarReceiver;
import com.atakmap.android.tools.ActionBarView;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.spatial.file.ShapefileSpatialDb;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import com.atakmap.coremap.locale.LocaleUtil;

import java.util.Map;
import java.util.Set;
import java.util.Stack;

import gov.tak.api.annotation.ModifierApi;

public class HierarchyListReceiver extends BroadcastReceiver implements
        OnClickListener, OnCheckedChangeListener,
        SharedPreferences.OnSharedPreferenceChangeListener {

    public enum HIERARCHY_MODE {
        NONE,
        TOOL_SELECT,
        SEARCH,
        ITEM_SELECTED,
        MULTISELECT,
    }

    private static final String TAG = "HierarchyListReceiver";

    // Intent actions
    public static final String CLEAR_HIERARCHY = "com.atakmap.android.maps.CLEAR_HIERARCHY";
    public static final String CLOSE_HIERARCHY = "com.atakmap.android.maps.CLOSE_HIERARCHY";
    public static final String REFRESH_HIERARCHY = "com.atakmap.android.maps.REFRESH_HIERARCHY";
    public static final String MANAGE_HIERARCHY = "com.atakmap.android.maps.MANAGE_HIERARCHY";

    // Singleton - only to be set by HierarchyMapComponent
    private static HierarchyListReceiver _instance;

    static void setInstance(HierarchyListReceiver instance) {
        _instance = instance;
    }

    public static HierarchyListReceiver getInstance() {
        return _instance;
    }

    @ModifierApi(since = "4.6", target = "4.9", modifiers = {
            "private"
    })
    protected HIERARCHY_MODE mode = HIERARCHY_MODE.NONE;
    private final Stack<HIERARCHY_MODE> previousMode = new Stack<>();
    @ModifierApi(since = "4.6", target = "4.9", modifiers = {
            "private"
    })
    protected final double[] overlayManagerSizeValues = new double[4];
    private final MapOverlayManager overlayManager;
    protected HierarchyManagerView content;
    protected HierarchyListAdapter adapter;
    protected View titleBar;
    protected View actionsLayout;
    protected LinearLayout customView;
    protected LinearLayout listHeader;
    protected LinearLayout listFooter;
    protected Button titleTextButton;
    protected EditText searchText;
    protected final Context _context;
    protected final MapView _mapView;
    protected final SharedPreferences prefs;
    private HierarchyListItem selectedItem = null;
    protected View checkAllLayout;
    protected ImageView checkAll;
    private final CheckBoxDrawable checkAllIcon;
    private int checkAllState;
    protected CheckBox showAll;
    protected ImageButton filterBtn;
    protected ListView listView;
    protected ImageButton backBtn;
    protected ImageButton hierarchyClearBtn;
    protected ImageButton multiSelectBtn;
    protected ImageButton searchBtn;
    protected SortSpinner sortSpinner;
    protected ProgressBar searchProgress;
    protected HierarchyListDropDown overlayManagerDropDown;

    // List UID which OM navigates to initially
    private String _navList;

    // OM was started with a search
    private boolean _searchOnly;

    // State listeners
    private final Set<HierarchyStateListener> _listeners = new HashSet<>();

    public HierarchyListReceiver(final MapView mapView, final Context context) {
        this._mapView = mapView;
        this._context = context;
        this.overlayManager = mapView.getMapOverlayManager();
        //register preference change listener and listen for any changes
        this.prefs = PreferenceManager.getDefaultSharedPreferences(
                mapView.getContext());
        this.prefs.registerOnSharedPreferenceChangeListener(this);

        // Initialize view
        this.content = (HierarchyManagerView) LayoutInflater.from(_context)
                .inflate(R.layout.hierarchy_manager_list_view, _mapView, false);

        titleBar = content.findViewById(R.id.hierarchy_action_layout);
        customView = content.findViewById(
                R.id.hierarchy_custom_view);
        listHeader = content.findViewById(
                R.id.hierarchy_list_header);
        listFooter = content.findViewById(
                R.id.hierarchy_list_footer);

        titleTextButton = content
                .findViewById(R.id.hierarchy_title_textview);
        titleTextButton.setOnClickListener(this);
        checkAllLayout = titleBar.findViewById(R.id.selectAll_layout);
        listView = titleBar
                .findViewById(R.id.hierarchy_manager_list);

        // Select all
        checkAll = titleBar.findViewById(R.id.selectAll_cb);
        checkAll.setImageDrawable(checkAllIcon = new CheckBoxDrawable());
        checkAll.setOnClickListener(this);

        // Show all
        showAll = titleBar.findViewById(R.id.showAll_cb);
        showAll.setOnCheckedChangeListener(this);

        // Bottom-right filter configuration button
        filterBtn = titleBar.findViewById(R.id.filter_btn);
        filterBtn.setOnClickListener(this);

        // Bottom-right [X] button
        titleBar.findViewById(R.id.close_hmv).setOnClickListener(this);

        // Multi-select finish/cancel
        titleBar.findViewById(R.id.hierarchy_process_user_selected_button)
                .setOnClickListener(this);
        titleBar.findViewById(R.id.hierarchy_back_out_mode)
                .setOnClickListener(this);

        // Actions (sort, clear, etc.)
        actionsLayout = titleBar.findViewById(R.id.hierarchy_actions_layout);

        searchProgress = titleBar.findViewById(
                R.id.search_progress);
        searchText = content.findViewById(R.id.hierarchy_search);
        searchText.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (adapter != null && mode == HIERARCHY_MODE.SEARCH)
                    adapter.searchListAndFilterResults(s.toString());
            }
        });
        // hide and show the keyboard depending on the focus
        searchText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean b) {
                InputMethodManager imm = (InputMethodManager) _context
                        .getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    if (b)
                        imm.showSoftInput(view,
                                InputMethodManager.SHOW_IMPLICIT);
                    else
                        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                }
            }
        });
        searchText.setOnEditorActionListener(
                new TextView.OnEditorActionListener() {
                    @Override
                    public boolean onEditorAction(TextView v, int a,
                            KeyEvent e) {
                        if (a == EditorInfo.IME_ACTION_SEARCH) {
                            searchText.clearFocus();
                            return true;
                        }
                        return false;
                    }
                });

        // Ghost button
        hierarchyClearBtn = titleBar.findViewById(
                R.id.hierarchy_clear_btn);
        hierarchyClearBtn.setOnClickListener(this);

        // Multi-select button
        multiSelectBtn = titleBar.findViewById(
                R.id.hierarchy_multiselect_btn);
        multiSelectBtn.setOnClickListener(this);

        // Search button
        searchBtn = titleBar.findViewById(
                R.id.hierarchy_search_btn);
        searchBtn.setOnClickListener(this);

        // Back button
        backBtn = titleBar
                .findViewById(R.id.hierarchy_back_button);
        backBtn.setOnClickListener(this);

        sortSpinner = titleBar
                .findViewById(R.id.sort_spinner);
        sortSpinner.setOnItemSelectedListener(
                new SimpleItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent,
                            View v, int pos,
                            long id) {
                        Sort sort = (Sort) sortSpinner
                                .getItemAtPosition(pos);
                        if (adapter != null && sort != null) {
                            adapter.sort(findSort(
                                    adapter.getCurrentList(false),
                                    sort.getClass()).getClass());
                            setViewToMode();
                        }
                    }

                });
        HierarchyListItemClickListener itemClickListener = new HierarchyListItemClickListener();
        this.content.setOnItemClickListener(itemClickListener);
        this.content.setOnItemLongClickListener(itemClickListener);
        this.overlayManagerDropDown = new HierarchyListDropDown();
    }

    void dispose() {
        this.prefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onReceive(final Context ignoreCtx, final Intent intent) {
        determineDropDownSizeBasedOnDeviceState();
        final String action = intent.getAction();
        if (action == null)
            return;

        switch (action) {
            case CLEAR_HIERARCHY:
                handleClearHierarchy();
                break;
            case CLOSE_HIERARCHY:
                final List<Intent> l = getIntents(intent);
                closeDropDown(l);
                break;
            case REFRESH_HIERARCHY:
                // Handles "hier_mode" and "list_item_paths"
                refreshDropDown(intent);
                break;
            case MANAGE_HIERARCHY:
                handleManageHierarchy(intent);
                break;
        }
    }

    @ModifierApi(since = "4.6", target = "4.9", modifiers = {
            "private"
    })
    protected List<Intent> getIntents(Intent intent) {
        Parcelable closeIntent = intent
                .getParcelableExtra("closeIntent");
        final List<Intent> l = new ArrayList<>();
        if (closeIntent instanceof Intent) {
            l.add((Intent) closeIntent);
        } else {
            Parcelable[] closeIntents = intent.getParcelableArrayExtra(
                    "closeIntents");
            if (!FileSystemUtils.isEmpty(closeIntents)) {
                for (Parcelable p : closeIntents) {
                    if (p instanceof Intent)
                        l.add((Intent) p);
                }
            }
        }
        return l;
    }

    @ModifierApi(since = "4.6", target = "4.9", modifiers = {
            "private"
    })
    protected void handleClearHierarchy() {
        if (adapter != null)
            adapter.clearHandler();
        if (mode == HIERARCHY_MODE.TOOL_SELECT)
            setPreviousMode();
    }

    @ModifierApi(since = "4.6", target = "4.9", modifiers = {
            "private"
    })
    protected void handleManageHierarchy(Intent intent) {
        final String handlerClassName = intent
                .getStringExtra("hier_userselect_handler");
        HIERARCHY_MODE setMode = (HIERARCHY_MODE) intent
                .getSerializableExtra("hier_mode");
        final List<String> listItemPaths = intent
                .getStringArrayListExtra("list_item_paths");
        final List<String> selectedPaths = intent
                .getStringArrayListExtra(
                        "hier_userselect_selected_paths");
        boolean refresh = intent.getBooleanExtra("refresh", false);
        boolean isRootList = intent.getBooleanExtra("isRootList",
                false);

        // Refresh if we can, otherwise instantiate
        if (handlerClassName == null && (overlayManagerDropDown
                .isShowing()
                || refresh && !overlayManagerDropDown.isClosed())) {
            // Navigate to top-level list if no usable extras specified
            // Usually means this intent was sent from the action bar
            if (setMode == null && listItemPaths == null)
                intent.putExtra("list_item_paths",
                        new ArrayList<String>());

            // This handles "hier_mode" and "list_item_paths"
            refreshDropDown(intent);
            return;
        }

        // Log.d(TAG, "HEIRARCHY RECD: "+_getCenterViewLocation());

        mode = HIERARCHY_MODE.NONE;

        HierarchyListUserSelect userSelectHandler = null;
        if (handlerClassName != null) {
            final boolean multiSelect = intent.getBooleanExtra(
                    "hier_multiselect", true);
            String handlerTag = intent.getStringExtra("hier_usertag");
            final List<String> handlerMapItemUIDs = intent
                    .getStringArrayListExtra(
                            "hier_userselect_mapitems_uids");
            try {
                Log.d(TAG, "Creating User Select Handler of type: "
                        + handlerClassName
                        + ", with tag: " + handlerTag);

                HierarchyListUserSelect hsh = HierarchySelectHandler
                        .get(handlerClassName);

                if (hsh == null) {
                    Class<?> clazz = Class.forName(handlerClassName);
                    Constructor<?> ctor = clazz.getConstructor();
                    Object object = ctor.newInstance();
                    userSelectHandler = (HierarchyListUserSelect) object;
                } else {
                    Log.d(TAG,
                            "preinstantiated registered object detected for class: "
                                    + handlerClassName);
                    userSelectHandler = hsh;

                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to create hier_userselect_handler: "
                        + handlerClassName, e);
                // fall back to normal mode
            }

            if (userSelectHandler != null) {
                userSelectHandler.setTag(handlerTag);
                userSelectHandler.setMultiSelect(multiSelect);
                setMode = HIERARCHY_MODE.TOOL_SELECT;

                if (!FileSystemUtils.isEmpty(handlerMapItemUIDs)) {
                    userSelectHandler
                            .setMapItemUIDs(handlerMapItemUIDs);
                } else {
                    userSelectHandler
                            .setMapItemUIDs(new ArrayList<String>());
                }
            } else {
                Log.w(TAG, "Unable to create handler: "
                        + handlerClassName);
            }
        }

        // In case overlay manager is opened twice
        this.adapter = null;
        closeDropDown();

        this.adapter = new HierarchyListAdapter(_context, _mapView,
                userSelectHandler, this);
        this.adapter.registerListener();
        this.content.setAdapter(this.adapter);
        if (selectedPaths != null)
            this.adapter.setSelectedPaths(selectedPaths);
        setCurrentMode(setMode);
        overlayManagerDropDown.showDropDown();
        updateCheckAll(HierarchyListAdapter.UNCHECKED);
        DocumentedIntentFilter filter = new DocumentedIntentFilter();
        filter.addAction(MapMenuReceiver.HIDE_MENU,
                "Clear the selection outline");
        filter.addAction(ToolbarBroadcastReceiver.UNSET_TOOLBAR,
                "Toolbar listener used to update the overlay manager view");
        AtakBroadcast.getInstance().registerReceiver(
                overlayManagerDropDown, filter);

        //see if invoker wants us to drill down to a specific list item
        _navList = null;
        if (listItemPaths != null) {
            Log.d(TAG,
                    "Navigating menu count: " + listItemPaths.size());
            if (!openMenu(listItemPaths)) {
                Log.w(TAG, "openMenu failure");
                AtakBroadcast.getInstance().sendBroadcast(
                        new Intent(CLEAR_HIERARCHY));
            } else if (isRootList && !listItemPaths.isEmpty())
                // OM was opened to this list - close OM when we back out of it
                _navList = listItemPaths.get(listItemPaths.size() - 1);
        }
        if (_navList == null)
            refreshDropDownSize();

        // Default search
        if (intent.hasExtra("searchTerms")) {
            startSearch(intent.getStringExtra("searchTerms"));
            _searchOnly = true;
        }
    }

    /* View interactions */

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.hierarchy_title_textview
                || id == R.id.hierarchy_back_button) {
            HierarchyListItem i = adapter != null ? adapter
                    .getCurrentList(false) : null;

            // Check if list needs to handle back button
            if (i instanceof HierarchyListStateListener &&
                    ((HierarchyListStateListener) i).onBackButton(adapter,
                            false))
                return;

            // Focus on the button in order to hide the soft keyboard
            v.requestFocus();
            onBack(v.getId());
        }

        // Prompt user to begin multi-select
        else if (id == R.id.hierarchy_multiselect_btn) {
            multiselectPrompt();
        }

        // Cancel multi-select mode
        else if (id == R.id.hierarchy_back_out_mode)
            cancelUserSelection();

        // Finish multi-select mode
        else if (id == R.id.hierarchy_process_user_selected_button) {
            if (this.adapter != null)
                this.adapter.processUserSelections();
        }

        // Select all checkbox
        else if (id == R.id.selectAll_cb) {
            adapter.setItemChecked(adapter.getCurrentList(true),
                    this.checkAllState == HierarchyListAdapter.UNCHECKED);
        }

        // User-selected filters
        else if (id == R.id.filter_btn) {
            promptUserFilters();
        }

        // Toggle search mode
        else if (id == R.id.hierarchy_search_btn) {
            if (isModeInStack(HIERARCHY_MODE.SEARCH))
                adapter.endSearch();
            else
                setCurrentMode(HIERARCHY_MODE.SEARCH);
        }

        // Clear ghost items
        else if (id == R.id.hierarchy_clear_btn) {
            clearHierarchyPrompt();
        }

        // Close OM
        else if (id == R.id.close_hmv) {
            cancelUserSelection();
            closeDropDown(false);
        }
    }

    @ModifierApi(since = "4.6", target = "4.9", modifiers = {
            "private"
    })
    protected void multiselectPrompt() {
        // Find handlers that support external usage, if any
        List<HierarchyListUserSelect> handlers = HierarchySelectHandler
                .getExternalHandlers();
        Collections.sort(handlers, HierarchyListUserSelect.COMP_TITLE);

        // Get the current list to determine if it's appropriate to show a
        // given external select handler in the current context
        final HierarchyListItem list = adapter.getCurrentList(true);
        final boolean isRootList = list instanceof HierarchyListAdapter.ListModelImpl;

        TileButtonDialog d = new TileButtonDialog(_mapView);
        d.addButton(R.drawable.export_menu_default, R.string.export);
        final List<HierarchyListUserSelect> externalHandlers = new ArrayList<>();
        for (HierarchyListUserSelect h : handlers) {
            if (h == null)
                continue;

            // Icon and title required
            Drawable icon = h.getIcon();
            String title = h.getTitle();
            if (icon == null || title == null) {
                Log.w(TAG, "Missing icon/title on select handler: " + h);
                continue;
            }

            // Make sure this handler is permitted to show up as an option
            // when multi-selecting from the root list
            if (isRootList && !h.acceptRootList())
                continue;

            // Make sure the handler is supported by the current non-root list
            if (!isRootList && !h.accept(list))
                continue;

            // Add handler
            d.addButton(icon, title);
            externalHandlers.add(h);
        }
        d.addButton(R.drawable.ic_menu_delete, R.string.delete_no_space);
        d.show(R.string.multiselect_dialogue, true);
        d.setOnClickListener(new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    // Export items mode
                    exportPrompt();
                } else if (!externalHandlers.isEmpty() && which >= 1
                        && which < externalHandlers.size() + 1) {
                    // External handler
                    HierarchyListUserSelect handler = externalHandlers
                            .get(which - 1);
                    setSelectHandler(handler);
                } else if (which == externalHandlers.size() + 1) {
                    // Delete items mode
                    HierarchyListUserDelete hlud = new HierarchyListUserDelete();
                    hlud.setCloseHierarchyWhenComplete(false);
                    setSelectHandler(hlud);
                }
            }
        });
    }

    /**
     * Set the current select handler
     * @param selectHandler Select handler
     */
    public void setSelectHandler(HierarchyListUserSelect selectHandler) {
        if (selectHandler == null) {
            adapter.clearHandler();
            return;
        }
        adapter.setSelectHandler(selectHandler);
        if (selectHandler.isMultiSelect())
            setCurrentMode(HIERARCHY_MODE.MULTISELECT);
        else
            setCurrentMode(HIERARCHY_MODE.TOOL_SELECT);
    }

    /**
     * Prompt the user to toggle temporary filters in Overlay Manager
     * TODO: Possibly allow for plugins to add filters here
     */
    private void promptUserFilters() {
        if (adapter == null)
            return;

        // Map existing filters by class so we can quickly check if they're
        // enabled or not
        List<HierarchyListFilter> filters = adapter.getUserFilters();
        final Map<Class<? extends HierarchyListFilter>, HierarchyListFilter> filterMap = new HashMap<>();
        final Map<Class<? extends HierarchyListFilter>, TileButton> btnMap = new HashMap<>();

        TileButtonDialog d = new TileButtonDialog(_mapView, true);
        btnMap.put(AutoSendFilter.class, d.addButton(
                R.drawable.ic_broadcast_cot, R.string.auto_send));
        btnMap.put(AuthorFilter.class, d.addButton(
                R.drawable.ic_user, R.string.self_authored));

        // Toggle buttons on for existing filters
        for (HierarchyListFilter filter : filters) {
            filterMap.put(filter.getClass(), filter);
            TileButton tb = btnMap.get(filter.getClass());
            if (tb != null)
                tb.setSelected(true);
        }

        d.setOnClickListener(new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                HierarchyListFilter filter;
                SortAlphabet sort = new SortAlphabet();
                if (which == 0) {
                    filter = filterMap.get(AutoSendFilter.class);
                    if (filter == null)
                        filter = new AutoSendFilter(sort);
                } else if (which == 1) {
                    filter = filterMap.get(AuthorFilter.class);
                    if (filter == null)
                        filter = new AuthorFilter(sort, MapView.getDeviceUid());
                } else {
                    dialog.dismiss();
                    return;
                }

                Class<? extends HierarchyListFilter> cl = filter.getClass();
                boolean enable = !filterMap.containsKey(cl);
                TileButton tb = btnMap.get(cl);
                if (tb != null)
                    tb.setSelected(enable);

                if (adapter != null) {
                    if (enable) {
                        adapter.addUserFilter(filter);
                        filterMap.put(cl, filter);
                    } else {
                        adapter.removeUserFilter(filter);
                        filterMap.remove(cl);
                    }
                    setViewToMode();
                }
            }
        });
        d.setCancelText(R.string.ok);
        d.show(R.string.toggle_filters, true);
    }

    @Override
    public void onCheckedChanged(CompoundButton cb, boolean isChecked) {
        int i = cb.getId();
        if (i == R.id.showAll_cb) {
            if (adapter != null)
                adapter.filterFOV(!isChecked);
        }
    }

    private void onBack(int buttonId) {
        if (adapter == null) {
            closeDropDown();
            return;
        }

        if (mode == HIERARCHY_MODE.ITEM_SELECTED) {
            selectItem(null);
        } else if (mode == HIERARCHY_MODE.SEARCH) {
            // Close OM if we hit the device back button on a search that was
            // immediately initiated on open
            if (_searchOnly) {
                _searchOnly = false;
                if (buttonId == 0) {
                    closeDropDown(false);
                    return;
                }
            }
            // End search mode
            adapter.endSearch();
        } else if (!adapter.isRootList()) {
            // if in a sub menu, go back
            HierarchyListItem curList = adapter.getCurrentList(true);
            if (curList != null && _navList != null
                    && _navList.equals(curList.getUID())) {
                if (buttonId == 0) {
                    // Device back button hit on the nav list
                    if (mode == HIERARCHY_MODE.NONE)
                        closeDropDown(false);
                    else if (hasSelectHandler())
                        cancelUserSelection();
                    else
                        setPreviousMode();
                    return;
                } else
                    _navList = null;
            }
            adapter.popList();
            //resetScroll();
        } else if ((buttonId == R.id.hierarchy_back_button || buttonId == 0)
                && hasSelectHandler()) {
            // else if at root level, exit any special modes
            cancelUserSelection();
        } else if (buttonId == 0) {
            // Device back button hit on root list
            cancelUserSelection();
            closeDropDown();
        }
    }

    /**
     * Check if Overlay Manager currently has a select handler active
     * @return True if select handler is active
     */
    private boolean hasSelectHandler() {
        return adapter != null && adapter.getSelectHandler() != null;
    }

    /**
     * Cancel the currently selected user select handler
     */
    private void cancelUserSelection() {
        if (hasSelectHandler())
            adapter.cancelUserSelection();
    }

    protected void refreshDropDown(Intent intent) {
        if (!overlayManagerDropDown.isShowing())
            // Ignore refresh events when OM is closed
            return;

        if (adapter != null) {
            // Attempt to refresh lists
            adapter.refreshList();

            // Notify adapter for immediate changes to existing list views
            adapter.notifyDataSetChanged();
        }

        // List UIDs to navigate through
        // i.e. Alerts, Geo Fences, <Geo Fence UID>
        if (intent.hasExtra("list_item_paths")) {
            final List<String> listItemPaths = intent
                    .getStringArrayListExtra("list_item_paths");

            //see if invoker wants us to drill down to a specific list item
            if (listItemPaths != null) {
                Log.d(TAG, "Navigating menu count: " + listItemPaths.size());

                if (_navList != null && (listItemPaths.isEmpty() ||
                        !listItemPaths.get(listItemPaths.size() - 1)
                                .equals(_navList)))
                    // Nav list changed while OM was opened - clear it
                    _navList = null;

                if (!openMenu(listItemPaths)) {
                    Log.w(TAG, "openMenu failure");
                    AtakBroadcast.getInstance().sendBroadcast(
                            new Intent(CLEAR_HIERARCHY));
                }
            }
        }

        // Start search mode
        if (intent.hasExtra("searchTerms"))
            startSearch(intent.getStringExtra("searchTerms"));

        // View mode
        else if (intent.hasExtra("hier_mode")) {
            HIERARCHY_MODE setMode = (HIERARCHY_MODE) intent
                    .getSerializableExtra("hier_mode");
            if (setMode != null && mode != setMode)
                setCurrentMode(setMode);
        }
    }

    /**
     * checks if user device is a Tablet, if tablet and user selected 33% preference
     * adjust values for overlay manager size, if device is not a tablet and user preference is not 33%
     * attach the normal defaults values of 50%
     */
    @ModifierApi(since = "4.6", target = "4.9", modifiers = {
            "private"
    })
    protected void determineDropDownSizeBasedOnDeviceState() {

        double landscapeW = DropDownReceiver.HALF_WIDTH;
        double landscapeH = DropDownReceiver.FULL_HEIGHT;
        double portraitW = DropDownReceiver.FULL_WIDTH;
        double portraitH = DropDownReceiver.HALF_HEIGHT;

        ///isDeviceTablet?
        if (_mapView.getContext().getResources().getBoolean(R.bool.isTablet)) {
            String size = this.prefs.getString("overlay_manager_width_height",
                    "33");
            if (size.equals("33")) {
                landscapeW = DropDownReceiver.FIVE_TWELFTHS_WIDTH;
                landscapeH = DropDownReceiver.FULL_HEIGHT;
                portraitW = DropDownReceiver.FULL_WIDTH;
                portraitH = DropDownReceiver.FIVE_TWELFTHS_WIDTH;
            } else if (size.equals("25")) {
                landscapeW = DropDownReceiver.QUARTER_SCREEN;
                landscapeH = DropDownReceiver.FULL_HEIGHT;
                portraitW = DropDownReceiver.FULL_WIDTH;
                portraitH = DropDownReceiver.QUARTER_SCREEN;
            }
        }

        overlayManagerSizeValues[0] = landscapeW;
        overlayManagerSizeValues[1] = landscapeH;
        overlayManagerSizeValues[2] = portraitW;
        overlayManagerSizeValues[3] = portraitH;
    }

    @ModifierApi(since = "4.6", target = "4.9", modifiers = {
            "private"
    })
    protected void exportPrompt() {
        ExportDialog d = new ExportDialog(_mapView);
        d.setCallback(new ExportDialog.Callback() {
            @Override
            public void onExporterSelected(ExportMarshal marshal) {
                HierarchyListUserExport hlue = new HierarchyListUserExport(
                        marshal, null);
                adapter.setSelectHandler(hlue);
                setCurrentMode(
                        HierarchyListReceiver.HIERARCHY_MODE.MULTISELECT);
            }
        });
        d.show();
    }

    @ModifierApi(since = "4.6", target = "4.9", modifiers = {
            "private"
    })
    protected void sendFile(File file) {
        if (file == null)
            return;

        //TODO better place for this mapping/logic? Perhaps have ExporterManager
        //ExportMarshalMetadata match supported file extension?
        ExportMarshal exporter;
        String ext = FileSystemUtils.getExtension(file, false, false);
        if (ShapefileSpatialDb.SHP_TYPE.equalsIgnoreCase(ext))
            exporter = ExporterManager.findExporter(_context,
                    ShapefileSpatialDb.SHP_CONTENT_TYPE);
        else
            exporter = ExporterManager.findExporter(_context,
                    ext.toUpperCase(LocaleUtil.getCurrent()));

        if (exporter == null) {
            Log.w(TAG, "No exporter available for : " + file);
            return;
        }

        Log.d(TAG, "Sending previously exported file: "
                + file.getAbsolutePath());

        SendDialog.Builder b = new SendDialog.Builder(_mapView);
        b.setName(file.getName());
        b.setIcon(exporter.getIconId());
        b.addFile(file, exporter.getContentType());
        b.show();
    }

    @ModifierApi(since = "4.6", target = "4.9", modifiers = {
            "private"
    })
    protected void clearHierarchyPrompt() {
        AlertDialog.Builder builder = new AlertDialog.Builder(_context);
        builder.setTitle(R.string.delete_dialog)
                .setMessage(R.string.delete_details)
                .setPositiveButton(R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                _mapView.getRootGroup().deepForEachItem(
                                        new MapGroup.MapItemsCallback() {
                                            @Override
                                            public boolean onItemFunction(
                                                    MapItem item) {
                                                if (nonArchivedMarker(item))
                                                    item.removeFromGroup();
                                                //Need to return false so it continues
                                                return false;
                                            }
                                        });
                            }
                        })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * Check if map item meets the following conditions:
     *  - Is a marker
     *  - Isn't the self marker
     *  - Is visible
     *  - Isn't archived
     * @param item Map item to check
     * @return True if non-archived
     */
    private boolean nonArchivedMarker(MapItem item) {
        return item instanceof Marker
                && !_mapView.getSelfMarker().equals(item)
                && item.getVisible()
                && !CotMapAdapter.isAtakSpecialType(item);
    }

    MapOverlayManager getOverlayManager() {
        return overlayManager;
    }

    /**
     * Update the display title for the current list
     */
    void refreshTitle() {
        if (adapter != null)
            titleTextButton.setText(adapter.getListTitle());
    }

    void setScroll(int scroll) {
        listView.setSelection(scroll);
    }

    int getScroll() {
        return listView.getFirstVisiblePosition();
    }

    public boolean isTouchActive() {
        return content.isTouchActive();
    }

    /**
     * Start search with provided terms
     * @param searchTerms Search terms
     */
    public void startSearch(final String searchTerms) {
        _mapView.post(new Runnable() {
            @Override
            public void run() {
                setCurrentMode(HIERARCHY_MODE.SEARCH);
                searchText.setText(searchTerms);
                if (searchTerms != null)
                    searchText.setSelection(searchTerms.length());
            }
        });
    }

    /**
     * Show the search progress icon
     * @param show True to show the progress icon
     */
    void showSearchLoader(boolean show) {
        if (this.searchProgress != null)
            this.searchProgress.setVisibility(
                    show && this.mode == HIERARCHY_MODE.SEARCH ? View.VISIBLE
                            : View.GONE);
    }

    void closeDropDown(List<Intent> closeIntent) {
        overlayManagerDropDown.onCloseList(true);
        overlayManagerDropDown.setCloseIntents(closeIntent);
        overlayManagerDropDown.closeDropDown();
    }

    private void closeDropDown(boolean forceClose) {
        if (!forceClose && overlayManagerDropDown.onCloseList(false))
            return;
        closeDropDown();
    }

    void closeDropDown() {
        closeDropDown(null);
    }

    /**
     * update the status of the check all checkbox if necessary
     *
     * @param state Check state (CHECKED, UNCHECKED, or SEMI_CHECKED)
     */
    public void updateCheckAll(int state) {
        if (this.checkAllState != state) {
            this.checkAllState = state;
            this.checkAllIcon.setChecked(state);
        }
    }

    /**
     * Update the drop-down size based on preferences and the current list
     */
    void refreshDropDownSize() {
        if (adapter == null || adapter.isNavigating())
            return;

        double width = overlayManagerSizeValues[0];
        double height = overlayManagerSizeValues[3];

        // Get preferred drop-down size for current list
        HierarchyListItem curList = adapter.getCurrentList(true);
        if (curList instanceof HierarchyListItem2) {
            double[] size = ((HierarchyListItem2) curList)
                    .getDropDownSize();
            if (size != null && size.length == 2) {
                if (size[0] >= 0)
                    width = size[0];
                if (size[1] >= 0)
                    height = size[1];
            }
        }

        if (!overlayManagerDropDown.isPortrait())
            // Landscape mode
            overlayManagerDropDown.callResize(
                    width,
                    overlayManagerSizeValues[1]);
        else {
            // See ATAK-9273 - Expand OM height when searching in portrait mode
            if (isModeInStack(HIERARCHY_MODE.SEARCH)) {
                boolean isTablet = _context.getResources().getBoolean(
                        R.bool.isTablet);
                double searchHeight = isTablet ? DropDownReceiver.HALF_HEIGHT
                        : DropDownReceiver.TWO_THIRDS_HEIGHT;
                if (height < searchHeight)
                    height = searchHeight;
            }

            // Portrait mode
            overlayManagerDropDown.callResize(
                    overlayManagerSizeValues[2],
                    height);
        }
    }

    private void updateBackBtnState() {
        if (mode != HIERARCHY_MODE.NONE && mode != HIERARCHY_MODE.TOOL_SELECT)
            backBtn.setVisibility(View.VISIBLE);
        else {
            if (adapter.isRootList())
                backBtn.setVisibility(View.GONE);
            else
                backBtn.setVisibility(View.VISIBLE);
        }
    }

    @ModifierApi(since = "4.6", target = "4.9", modifiers = {
            "private"
    })
    protected void setCurrentMode(HIERARCHY_MODE m) {
        if (m == null)
            return;
        if (mode == HIERARCHY_MODE.ITEM_SELECTED)
            setPreviousMode();

        previousMode.push(mode);
        mode = m;
        setViewToMode();
    }

    @ModifierApi(since = "4.6", target = "4.9", modifiers = {
            "private"
    })
    protected void setPreviousMode() {
        if (mode == HIERARCHY_MODE.ITEM_SELECTED) {
            if (adapter != null)
                adapter.unhighlight();
            hideRadialMenu();
        }

        if (previousMode.isEmpty())
            mode = HIERARCHY_MODE.NONE;
        else
            mode = previousMode.pop();

        // Prevent search reset if we're backing out of another mode
        if (mode == HIERARCHY_MODE.SEARCH)
            searchText.setVisibility(View.VISIBLE);

        setViewToMode();
    }

    @ModifierApi(since = "4.6", target = "4.9", modifiers = {
            "private"
    })
    protected boolean isModeInStack(HIERARCHY_MODE mode) {
        return this.mode == mode || previousMode.contains(mode);
    }

    void endSearch() {
        while (isModeInStack(HIERARCHY_MODE.SEARCH))
            setPreviousMode();
    }

    void handlerFinished() {
        setPreviousMode();
    }

    /**
     * This sets up the Overlay Manager Action bar to the current mode
     */
    public void setViewToMode() {
        if (adapter == null || !overlayManagerDropDown.isVisible())
            return;

        IToolbarExtension toolbar = null;
        boolean showSort = !adapter.isRootList();

        // Views that are hidden by default
        hierarchyClearBtn.setVisibility(View.GONE);
        multiSelectBtn.setVisibility(View.GONE);
        listHeader.setVisibility(View.GONE);
        listFooter.setVisibility(View.GONE);
        checkAllLayout.setVisibility(View.GONE);

        // Update "Show All" checkbox in case preference was changed outside of OM
        boolean showAll = !prefs.getBoolean(FOVFilter.PREF, false);
        if (this.showAll.isChecked() != showAll)
            this.showAll.setChecked(showAll);

        // Update filter button selected state
        filterBtn.setSelected(!adapter.getUserFilters().isEmpty());

        View customView = null;
        HIERARCHY_MODE mode = this.mode;
        if (isModeInStack(HIERARCHY_MODE.SEARCH))
            // Show search text if search is our mode stack
            // regardless of the current mode
            mode = HIERARCHY_MODE.SEARCH;

        switch (mode) {
            case NONE:
            case ITEM_SELECTED:
                HierarchyListItem curList = adapter.getCurrentList(false);
                if (curList != null) {
                    // Show multi-select button if the current list supports it
                    if (!(curList instanceof HierarchyListItem2)
                            || ((HierarchyListItem2) curList)
                                    .isMultiSelectSupported())
                        multiSelectBtn.setVisibility(View.VISIBLE);

                    // Show custom header and/or footer if there is one
                    if (curList instanceof HierarchyListItem2) {

                        customView = ((HierarchyListItem2) curList)
                                .getCustomLayout();
                        if (customView != null)
                            break;

                        View header = ((HierarchyListItem2) curList)
                                .getHeaderView();
                        View footer = ((HierarchyListItem2) curList)
                                .getFooterView();
                        replaceView(listHeader, header);
                        replaceView(listFooter, footer);
                    }

                    // Show list toolbar if there is one
                    if (curList instanceof IToolbarExtension)
                        toolbar = (IToolbarExtension) curList;
                }

                // go back to the root page's view
                titleTextButton.setVisibility(View.VISIBLE);
                actionsLayout.setVisibility(View.VISIBLE);
                searchText.setVisibility(View.GONE);
                searchBtn.setVisibility(View.VISIBLE);

                // Show clear hierarchy (ghost) button on root list
                if (adapter.isRootList())
                    hierarchyClearBtn.setVisibility(View.VISIBLE);
                break;

            case TOOL_SELECT:
                // in this mode, the overlay manager is just used to select items for a tool
                titleTextButton.setVisibility(View.VISIBLE);
                actionsLayout.setVisibility(View.VISIBLE);
                searchText.setVisibility(View.GONE);
                searchBtn.setVisibility(View.VISIBLE);
                break;

            case SEARCH:
                // show the search view
                titleTextButton.setVisibility(View.GONE);
                if (searchText.getVisibility() != View.VISIBLE) {
                    searchText.setVisibility(View.VISIBLE);
                    searchText.setText("");
                    searchText.requestFocus();
                }
                showSort = false;
                break;

            case MULTISELECT:
                // show the multi select view
                titleTextButton.setVisibility(View.VISIBLE);
                titleBar.findViewById(R.id.hierarchy_actions_layout)
                        .setVisibility(View.VISIBLE);
                checkAllLayout.setVisibility(View.VISIBLE);
                searchText.setVisibility(View.GONE);
                searchBtn.setVisibility(View.VISIBLE);
                break;
            default:
                break;
        }

        if (customView != null) {
            // This replaces the entire view
            replaceView(this.customView, customView);
            titleBar.setVisibility(View.GONE);
            showSort = false;
        } else {
            replaceView(this.customView, null);
            titleBar.setVisibility(View.VISIBLE);
        }

        HierarchyListUserSelect selectHandler = adapter.getSelectHandler();
        if (selectHandler != null) {
            // Make sure multi-select button is hidden
            multiSelectBtn.setVisibility(View.GONE);
            if (selectHandler.isMultiSelect())
                checkAllLayout.setVisibility(View.VISIBLE);
            // Show toolbar if applicable
            if (selectHandler.getToolbar() != null)
                toolbar = selectHandler.getToolbar();
        }
        if (showSort) {
            // Check that current sort is included in this list's sort list
            HierarchyListItem item = adapter.getCurrentList(false);
            List<Sort> sorts = getSortModes(item);
            if (sorts == null) {
                sorts = new ArrayList<>();
                sorts.add(new SortAlphabet());
            }
            int selectionIndex = 0;
            Class<?> curSort = adapter.getSortType();
            if (showSort = sorts.size() > 1) {
                boolean found = false;
                for (int i = 0; i < sorts.size(); i++) {
                    if (sorts.get(i).getClass().equals(curSort)) {
                        curSort = sorts.get(i).getClass();
                        selectionIndex = i;
                        found = true;
                        break;
                    }
                }
                if (!found)
                    curSort = sorts.get(0).getClass();
            } else
                curSort = !FileSystemUtils.isEmpty(sorts)
                        ? sorts.get(0).getClass()
                        : SortAlphabet.class;

            // Update sort if needed
            if (curSort != null && !curSort.equals(adapter.getSortType()))
                adapter.sort(curSort);

            // Update sort icon
            sortSpinner.setSortModes(sorts);
            sortSpinner.setSelection(selectionIndex);
        }
        sortSpinner.setVisibility(showSort ? View.VISIBLE : View.GONE);
        updateBackBtnState();
        refreshTitle();
        overlayManagerDropDown.setToolbar(toolbar);
        refreshDropDownSize();
    }

    /**
     * Get the list of sort modes supported by the current list
     * @return List of sort modes
     */
    public static List<Sort> getSortModes(HierarchyListItem item) {
        if (item instanceof HierarchyListItem2)
            return ((HierarchyListItem2) item).getSorts();
        else
            return AbstractHierarchyListItem2.getDefaultSortModes(item);
    }

    /**
     * Get the sort instance for a given item
     * @param item List item
     * @param sortType Sort class
     * @return Sort instance
     */
    public static Sort findSort(HierarchyListItem item, Class<?> sortType) {
        List<Sort> sorts = getSortModes(item);
        if (sorts != null) {
            for (Sort s : sorts) {
                if (s.getClass().equals(sortType))
                    return s;
            }
        }
        if (sortType.equals(HierarchyListItem.SortDistanceFrom.class))
            return new HierarchyListItem.SortDistanceFrom(null);
        return new SortAlphabet();
    }

    void backToRoot() {
        updateBackBtnState();
    }

    /**
     * Replace a view group's children with a single view
     * @param parent Parent view group
     * @param child Child view
     */
    @ModifierApi(since = "4.6", target = "4.9", modifiers = {
            "private"
    })
    protected static void replaceView(ViewGroup parent, View child) {
        if (parent == null)
            return;

        // Don't replace with the exact same view
        View existing = parent.getChildAt(0);
        if (existing != null && existing == child) {
            parent.setVisibility(View.VISIBLE);
            return;
        }

        // Clear out existing child views
        parent.removeAllViews();

        if (child != null) {
            // Make sure child view isn't attached to another parent
            if (child.getParent() != null && child.getParent() != parent)
                ((ViewGroup) child.getParent()).removeView(child);

            // Add child view and display
            parent.addView(child, 0);
            parent.setVisibility(View.VISIBLE);
        } else
            // Hide if empty
            parent.setVisibility(View.GONE);
    }

    /**
     * Attempt to navigate based on the specified paths
     * Each path is the UID of a HierarchyListItem, beginning at the root
     * e.g. Shapes/Drawing Objects/<uid>
     *
     * @param paths A list of UIDs to navigate by
     * @return True if the adapter was sent the request, false otherwise
     */
    private boolean openMenu(List<String> paths) {
        if (paths == null) {
            Log.w(TAG, "Cannot open null menu paths");
            return false;
        }

        if (adapter == null) {
            Log.w(TAG,
                    "Cannot open menu paths, on empty adapter. Try MANAGE_HIERARCHY rather than REFRESH_HIERARCHY");
            return false;
        }

        // Turn off FOV filtering as it might filter out the list the
        // user is trying to navigate to
        if (!paths.isEmpty())
            showAll.setChecked(true);

        // Adapter is probably busy waiting for initList so
        // don't expect a result back immediately
        adapter.navigateTo(paths, true);
        return true;
    }

    private void selectItem(HierarchyListItem item) {
        selectedItem = item;
        if (adapter != null)
            adapter.unhighlight();
        overlayManagerDropDown.setSelected(null);
        if (item != null) {
            if (adapter != null)
                adapter.toggleHighlight(item);
            if (item instanceof MapItemUser)
                overlayManagerDropDown.setSelected(((MapItemUser) item)
                        .getMapItem());
            if (this.mode != HIERARCHY_MODE.ITEM_SELECTED)
                setCurrentMode(HIERARCHY_MODE.ITEM_SELECTED);
        } else if (mode == HIERARCHY_MODE.ITEM_SELECTED)
            setPreviousMode();
    }

    private void hideRadialMenu() {
        // Close radial menu
        AtakBroadcast.getInstance().sendBroadcast(
                new Intent(MapMenuReceiver.HIDE_MENU));

        // Close coordinate overlay
        AtakBroadcast.getInstance().sendBroadcast(new Intent(
                "com.atakmap.android.maps.HIDE_DETAILS"));

        // Unfocus (stops map from auto-panning to marker)
        AtakBroadcast.getInstance().sendBroadcast(new Intent(
                "com.atakmap.android.maps.UNFOCUS"));
    }

    /**
     * Called when a shared preference is changed, added, or removed. This
     * may be called even if a preference is set to its existing value.
     * <p/>
     * <p>This callback will be run on your main thread.
     *
     * @param sharedPreferences The {@link SharedPreferences} that received
     *                          the change.
     * @param key               The key of the preference that was changed, added, or
     */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key) {

        if (key == null)
            return;

        if (key.equals("overlay_manager_width_height")
                && overlayManagerDropDown.isVisible()) {
            determineDropDownSizeBasedOnDeviceState();
            refreshDropDownSize();
        }
    }

    public synchronized void registerListener(HierarchyStateListener l) {
        _listeners.add(l);
    }

    public synchronized void unregisterListener(HierarchyStateListener l) {
        _listeners.remove(l);
    }

    private synchronized Set<HierarchyStateListener> getListeners() {
        return new HashSet<>(_listeners);
    }

    void onCurrentListChanged(HierarchyListAdapter adapter,
            HierarchyListItem oldList, HierarchyListItem newList) {
        for (HierarchyStateListener l : getListeners())
            l.onCurrentListChanged(adapter, oldList, newList);
    }

    @ModifierApi(since = "4.6", target = "4.9", modifiers = {
            "private"
    })
    protected class HierarchyListItemClickListener implements
            OnItemClickListener, OnItemLongClickListener {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position,
                long id) {

            if (adapter == null)
                return;
            HierarchyListItem item = (HierarchyListItem) adapter
                    .getItem(position);

            if (item == null) {
                Log.w(TAG, "onItemClick failed to find position: " + position);
                return;
            }

            HierarchyListUserSelect handler = adapter != null
                    ? adapter.getSelectHandler()
                    : null;
            if (handler == null) {
                ItemClick onClick = item.getAction(ItemClick.class);
                if (onClick != null && onClick.onClick())
                    // Item click event handled
                    return;
            }

            if (item.isChildSupported() && (handler == null
                    || handler.acceptEntry(item))) {
                // set the title to the sublist title
                adapter.unhighlight();
                adapter.pushList(item, true);
                if (mode == HIERARCHY_MODE.SEARCH) {
                    setPreviousMode();
                } else {
                    adapter.refreshList();
                    setScroll(0);
                    refreshTitle();
                }
            } else {
                if (handler != null && !handler.isMultiSelect()) {
                    adapter.setItemChecked(item, true);
                    adapter.processUserSelections();
                    return;
                }
                GoTo itemGoTo = item.getAction(GoTo.class);
                if (itemGoTo != null) {
                    if (handler != null) {
                        // Go to the item but don't go into item-selected mode
                        itemGoTo.goTo(false);
                        return;
                    }
                    Visibility visAction = item.getAction(Visibility.class);
                    if (mode == HIERARCHY_MODE.NONE
                            || mode == HIERARCHY_MODE.SEARCH) {
                        // enter item_selected mode if goTo was successful
                        if (itemGoTo.goTo(true)) {
                            selectItem(item);
                            if (visAction != null && !visAction.isVisible())
                                adapter.setVisibleAsync(visAction, true);
                        }
                    } else if (mode == HIERARCHY_MODE.ITEM_SELECTED) {
                        String selectedUID = selectedItem == null ? null
                                : selectedItem.getUID();
                        String miUID = item.getUID();
                        if (selectedItem != item && (selectedItem == null
                                || selectedUID == null
                                || !selectedUID.equals(miUID))) {
                            // change the selected item
                            selectItem(item);
                            if (visAction != null && !visAction.isVisible())
                                adapter.setVisibleAsync(visAction, true);
                            itemGoTo.goTo(true);
                        } else {
                            hideRadialMenu();
                            selectItem(null);
                        }
                    } else {
                        // if in another mode, go to the item, but dont enter ITEM_SELECTED mode
                        itemGoTo.goTo(false);
                    }
                } else {
                    Log.d(TAG, "Action: GoTo not supported for overlay item "
                            + item.getTitle());
                }
            }
        }

        @Override
        public boolean onItemLongClick(AdapterView<?> parent, View view,
                int position, long id) {
            if (adapter == null)
                return false;
            HierarchyListItem item = (HierarchyListItem) adapter
                    .getItem(position);

            if (item == null) {
                Log.w(TAG, "onItemLongClick failed to find position: "
                        + position);
                return false;
            }

            ItemClick onClick = item.getAction(ItemClick.class);
            return onClick != null && onClick.onLongClick();
        }
    }

    @ModifierApi(since = "4.6", target = "4.9", modifiers = {
            "private"
    })
    protected class HierarchyListDropDown extends DropDownReceiver
            implements IToolbarExtension, OnStateListener {

        private final ActionBarView _toolbarView;
        private final ActionBarReceiver _toolbarReceiver;
        private List<Intent> _onCloseIntents;
        @ModifierApi(since = "4.6", target = "4.9", modifiers = {
                "private"
        })
        protected HierarchyListAdapter _adapter;
        @ModifierApi(since = "4.6", target = "4.9", modifiers = {
                "private"
        })
        protected boolean _showingDropDown;

        // Used to check if we already fired onCloseList
        private HierarchyListStateListener _onCloseListListener;

        public HierarchyListDropDown() {
            super(_mapView);
            _toolbarView = new ActionBarView(_context);
            _toolbarReceiver = ActionBarReceiver.getInstance();
        }

        @ModifierApi(since = "4.6", target = "4.9", modifiers = {
                "private"
        })
        protected void showDropDown() {
            showDropDown(content, 0, overlayManagerSizeValues[1],
                    overlayManagerSizeValues[2], 0, true, this);
            _showingDropDown = true;
        }

        @ModifierApi(since = "4.6", target = "4.9", modifiers = {
                "private"
        })
        protected void setCloseIntents(List<Intent> intents) {
            _onCloseIntents = intents;
        }

        @ModifierApi(since = "4.6", target = "4.9", modifiers = {
                "private"
        })
        protected boolean onCloseList(boolean forceClose) {
            if (_adapter == null) {
                _onCloseListListener = null;
                return false;
            }
            boolean ret = false;
            HierarchyListStateListener l = _adapter.getListStateListener();
            if (l != null && _onCloseListListener != l)
                ret = l.onCloseList(_adapter, forceClose);
            if (!ret || forceClose)
                _onCloseListListener = l;
            return ret;
        }

        @Override
        protected boolean onBackButtonPressed() {
            HierarchyListItem i = _adapter != null ? _adapter
                    .getCurrentList(false) : null;

            // Check if list needs to handle back button
            if (i instanceof HierarchyListStateListener &&
                    ((HierarchyListStateListener) i).onBackButton(_adapter,
                            true))
                return true;

            // Default device back button behavior
            onBack(0);
            return true;
        }

        @Override
        public String getAssociationKey() {
            if (_adapter != null) {
                // Allow list to specify its own settings key
                HierarchyListItem curList = _adapter.getCurrentList(true);
                if (curList instanceof HierarchyListItem2) {
                    String key = ((HierarchyListItem2) curList)
                            .getAssociationKey();
                    if (!FileSystemUtils.isEmpty(key))
                        return key;
                }
            }
            return super.getAssociationKey();
        }

        @Override
        public void disposeImpl() {
        }

        public String getToolbarTitle() {
            return adapter.getListTitle();
        }

        public void setToolbar(IToolbarExtension toolbar) {
            if (!isVisible())
                return;
            ActionBarView toolbarView = toolbar != null
                    ? toolbar.getToolbarView()
                    : null;
            ActionBarView current = _toolbarReceiver.getToolView();
            boolean show = toolbarView != null;
            boolean newTb = false;

            // Update toolbar view if needed
            if (show && toolbarView.getParent() != _toolbarView) {
                _toolbarView.removeAllViews();
                if (toolbarView.getParent() != null)
                    ((ViewGroup) toolbarView.getParent())
                            .removeView(toolbarView);
                _toolbarView.addView(toolbarView);
                if (current == _toolbarView)
                    // Notify action bar of view update
                    ((Activity) _mapView.getContext()).invalidateOptionsMenu();
                newTb = true;
            } else if (!show && _toolbarView.getChildCount() > 0)
                _toolbarView.removeAllViews();

            // Don't interrupt other toolbars
            if (!newTb && show && current != null || !show
                    && current != _toolbarView)
                return;

            // Toggle the toolbar view
            _toolbarReceiver.setToolView(show ? _toolbarView : null);
        }

        @Override
        public List<Tool> getTools() {
            return null;
        }

        @Override
        public ActionBarView getToolbarView() {
            return _toolbarView;
        }

        @Override
        public boolean hasToolbar() {
            return true;
        }

        @Override
        public void onToolbarVisible(final boolean vis) {
        }

        public void setSelected(final MapItem m) {
            super.setSelected(m, "asset:/icons/outline.png", false);
        }

        @Override
        public void onReceive(Context c, Intent intent) {
            String action = intent.getAction();
            if (action == null)
                return;

            if (action.equals(MapMenuReceiver.HIDE_MENU)) {
                // Hide the icon
                selectItem(null);
            } else if (action.equals(ToolbarBroadcastReceiver.UNSET_TOOLBAR))
                setViewToMode();
        }

        @Override
        public void onDropDownSelectionRemoved() {
            if (mode == HIERARCHY_MODE.ITEM_SELECTED)
                setPreviousMode();
        }

        @Override
        public void onDropDownVisible(boolean v) {
            Set<HierarchyStateListener> listeners = getListeners();
            if (_showingDropDown && v && adapter != null) {
                // Notify listeners that OM has been opened
                for (HierarchyStateListener l : listeners)
                    l.onOpened(adapter);
            }
            _showingDropDown = false;
            if (v) {
                if (_adapter == null)
                    _adapter = adapter;
                if (_adapter != null) {
                    if (mode == HIERARCHY_MODE.ITEM_SELECTED)
                        setPreviousMode();
                    _adapter.refreshList();
                }
            } else {
                if (_toolbarReceiver.getToolView() == _toolbarView)
                    // Hide the toolbar container when the drop-down is hidden
                    ActionBarReceiver.getInstance().setToolView(null);
                _toolbarView.removeAllViews();
            }

            // Notify listeners of OM visibility state
            for (HierarchyStateListener l : listeners)
                l.onVisible(_adapter, v);

            // Notify current list of visibility state
            HierarchyListStateListener l;
            if (_adapter != null
                    && (l = _adapter.getListStateListener()) != null)
                l.onListVisible(_adapter, v);
        }

        @Override
        public void onDropDownSizeChanged(double width,
                double height) {
        }

        @Override
        public void onDropDownClose() {
            onCloseList(true);
            AtakBroadcast.getInstance().unregisterReceiver(
                    overlayManagerDropDown);
            if (_adapter != null) {
                // Notify listeners that OM has been closed
                for (HierarchyStateListener l : getListeners())
                    l.onClosed(_adapter);

                // Destroy OM adapter
                _adapter.unregisterListener();
            }
            _adapter = null;
            if (_onCloseIntents != null) {
                for (Intent i : _onCloseIntents) {
                    AtakBroadcast.getInstance().sendBroadcast(i);
                }
                _onCloseIntents = null;
            }
            _navList = null;
            _onCloseListListener = null;
        }

        @ModifierApi(since = "4.6", target = "4.9", modifiers = {
                "private"
        })
        protected boolean isShowing() {
            return _showingDropDown || isVisible();
        }
    }
}
