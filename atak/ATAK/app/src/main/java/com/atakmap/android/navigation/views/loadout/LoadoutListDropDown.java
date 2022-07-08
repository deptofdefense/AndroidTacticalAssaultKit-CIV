
package com.atakmap.android.navigation.views.loadout;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Toast;

import com.atakmap.android.dropdown.DropDownManager;
import com.atakmap.android.gui.ImportFileBrowserDialog;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.navigation.models.LoadoutItemModel;
import com.atakmap.android.navigation.views.NavView;
import com.atakmap.android.navigationstack.DropDownNavigationStack;
import com.atakmap.android.navigationstack.NavigationStackItem;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.toolbar.ToolbarBroadcastReceiver;
import com.atakmap.android.tools.ActionBarView;
import com.atakmap.android.util.MappingAdapterEventReceiver;
import com.atakmap.android.util.MappingVM;
import com.atakmap.app.R;
import com.atakmap.app.preferences.PreferenceControl;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.util.zip.IoUtils;
import com.atakmap.util.zip.ZipEntry;
import com.atakmap.util.zip.ZipFile;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The drop-down that displays the list of available loadouts
 * For the list of tools (overflow menu) see {@link LoadoutToolsDropDown}
 */
public class LoadoutListDropDown extends NavigationStackItem
        implements MappingAdapterEventReceiver<MappingVM>,
        LoadoutManager.OnLoadoutChangedListener {

    private static final String TAG = "LoadoutListDropDown";

    // For sorting loadouts alphabetically while keeping default on top
    private static final Comparator<LoadoutItemModel> SORT_LOADOUT_NAME = new Comparator<LoadoutItemModel>() {
        @Override
        public int compare(LoadoutItemModel l1, LoadoutItemModel l2) {
            if (l1.isDefault())
                return -1;
            else if (l2.isDefault())
                return 1;
            return l1.getTitle().compareToIgnoreCase(l2.getTitle());
        }
    };

    private static final String PACKAGE_PREFIX = "com.atakmap.android.loadout.";
    public static final String SHOW_LOADOUT = PACKAGE_PREFIX + "SHOW_LOADOUT";
    public static final String TOGGLE_LOADOUT = PACKAGE_PREFIX
            + "TOGGLE_LOADOUT";
    public static final String CLOSE_LOADOUT = PACKAGE_PREFIX + "CLOSE_LOADOUT";
    public static final String REFRESH_LIST = PACKAGE_PREFIX + "REFRESH_LIST";

    private final LoadoutManager _loadouts;
    private final LoadoutListAdapter _adapter;
    private LoadoutToolsDropDown _toolsDropdown;
    private final List<MappingVM> _vms = new ArrayList<>();
    private boolean _buttonsShowing;

    protected LoadoutListDropDown(MapView mapView) {
        super(mapView);

        _loadouts = LoadoutManager.getInstance();
        _loadouts.addListener(this);

        setNavigationStack(new DropDownNavigationStack(mapView));
        _navigationStack.setAssociationKey("overflow_menu");
        _itemView = LayoutInflater.from(mapView.getContext())
                .inflate(R.layout.loadout_list, mapView, false);
        ListView toolbarList = _itemView.findViewById(R.id.toolbar_list);
        _adapter = new LoadoutListAdapter(_context);
        _adapter.setEventReceiver(this);
        toolbarList.setAdapter(_adapter);

        DocumentedIntentFilter filter = new DocumentedIntentFilter();
        filter.addAction(SHOW_LOADOUT, "Show options for the current loadout");
        filter.addAction(TOGGLE_LOADOUT,
                "Toggle the loadout/overflow drop-down");
        filter.addAction(CLOSE_LOADOUT, "Close the loadout/overflow drop-down");
        filter.addAction(REFRESH_LIST, "Refresh the loadout list");
        AtakBroadcast.getInstance().registerReceiver(this, filter);

        // Flags this drop-down to not interrupt settings association keys
        // or non-retained drop-downs
        setTransient(true);
    }

    @Override
    protected void disposeImpl() {
        super.disposeImpl();
        _loadouts.removeListener(this);
        AtakBroadcast.getInstance().unregisterReceiver(this);
        getNavigationStack().dispose();
    }

    private void refresh() {
        _vms.clear();
        _vms.add(new LoadoutAllToolsVM());
        List<LoadoutItemModel> loadouts = _loadouts.getLoadouts();
        Collections.sort(loadouts, SORT_LOADOUT_NAME);
        for (LoadoutItemModel profile : loadouts) {
            LoadoutListVM vm = new LoadoutListVM(profile);
            _vms.add(vm);
        }
        _adapter.replaceItems(_vms);
    }

    private void addLoadout() {
        if (_mapView == null)
            return;

        LoadoutItemModel current = _loadouts.getCurrentLoadout();
        LoadoutItemModel ld = new LoadoutItemModel(generateLoadoutName(
                _context.getString(R.string.new_loadout)));
        ld.setTemporary(true);
        ld.showZoomButton(current == null || current.containsButton("zoom"));
        _loadouts.addLoadout(ld);
        _loadouts.setCurrentLoadout(ld);
        openLoadout(true);
    }

    private String generateLoadoutName(final String name) {

        String newName = name;
        List<LoadoutItemModel> loadouts = _loadouts.getLoadouts();
        final Set<String> names = new HashSet<>(loadouts.size());
        for (LoadoutItemModel lm : loadouts)
            names.add(lm.getTitle());
        int i = 1;
        while (names.contains(newName)) {
            newName = name + " (" + i + ")";
            i++;
        }

        return newName;
    }

    @Override
    public String getTitle() {
        return getMapView().getContext().getString(R.string.toolbars);
    }

    @Override
    public List<ImageButton> getButtons() {

        // Add new loadouts
        ImageButton addButton = new ImageButton(_context);
        addButton.setImageResource(R.drawable.ic_navstack_add);
        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addLoadout();
            }
        });

        // Import loadout
        ImageButton importBtn = new ImageButton(_context);
        importBtn.setImageResource(R.drawable.ic_navstack_import);
        importBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                promptImportLoadout();
            }
        });

        return Arrays.asList(addButton, importBtn);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (action == null)
            return;

        switch (action) {

            // Show options for the current loadout
            case SHOW_LOADOUT:
                showDropDown();

                // Toggle the drop-down
            case TOGGLE_LOADOUT:
                if (isVisible()) {
                    if (_toolsDropdown != null && _toolsDropdown.isInEditMode())
                        _toolsDropdown.onCloseButton();
                    else
                        closeNavigationStack();
                } else
                    showDropDown();
                break;

            // Close the drop-down
            case CLOSE_LOADOUT:
                closeNavigationStack();
                break;

            // Refresh the list of loadouts
            case REFRESH_LIST:
                refresh();
                break;
        }
    }

    @Override
    public void onClose() {
        NavView.getInstance().toggleButtons(_buttonsShowing);
    }

    @Override
    public void eventReceived(MappingVM model) {

        if (model instanceof LoadoutListVM) {
            LoadoutListVM loadoutVM = (LoadoutListVM) model;

            ToolManagerBroadcastReceiver.getInstance().endCurrentTool();
            ToolbarBroadcastReceiver.getInstance().closeToolbar(
                    ActionBarView.TOP_LEFT);

            LoadoutMode vmMode = loadoutVM.getMode();
            LoadoutItemModel mdl = loadoutVM.getLoadout();
            switch (vmMode) {
                case VIEW:
                case EDIT:
                    _loadouts.setCurrentLoadout(mdl);
                    openLoadout(vmMode == LoadoutMode.EDIT);
                    break;
                case SELECT:
                    _loadouts.setCurrentLoadout(mdl);
                    break;
                case DELETE:
                    _loadouts.removeLoadout(mdl);
                    break;
            }
        } else if (model instanceof LoadoutAllToolsVM) {
            openLoadout(null, false);
        }
    }

    private void showDropDown() {
        if (isVisible())
            return;

        if (DropDownManager.getInstance().isTopDropDown(_navigationStack)) {
            DropDownManager.getInstance().unHidePane();
            return;
        }

        // Make sure the toolbar buttons are shown
        _buttonsShowing = NavView.getInstance().buttonsVisible();
        NavView.getInstance().toggleButtons(true);

        // Open the loadout editor drop-down
        refresh();
        pushView(this);

        // Open the tools for the current loadout
        openLoadout(false);
    }

    private void openLoadout(LoadoutItemModel loadout, boolean edit) {
        if (_toolsDropdown == null)
            _toolsDropdown = new LoadoutToolsDropDown(getMapView());
        _toolsDropdown.setLoadout(loadout);
        _toolsDropdown.setEditMode(edit);
        pushView(_toolsDropdown);
    }

    private void openLoadout(boolean edit) {
        openLoadout(_loadouts.getCurrentLoadout(), edit);
    }

    @Override
    public void onLoadoutAdded(LoadoutItemModel loadout) {
        refresh();
    }

    @Override
    public void onLoadoutModified(LoadoutItemModel loadout) {
        refresh();
    }

    @Override
    public void onLoadoutRemoved(LoadoutItemModel loadout) {
        refresh();
    }

    @Override
    public void onLoadoutSelected(LoadoutItemModel loadout) {
        _adapter.notifyDataSetChanged();
    }

    /**
     * Prompt the user to import a loadout from a file
     */
    private void promptImportLoadout() {
        ImportFileBrowserDialog d = new ImportFileBrowserDialog(_mapView);
        d.setTitle(_context.getString(R.string.import_toolbar));
        d.setExtensionTypes("zip", "pref");
        d.setOnDismissListener(new ImportFileBrowserDialog.DialogDismissed() {
            @Override
            public void onFileSelected(File f) {
                importLoadouts(f);
            }

            @Override
            public void onDialogClosed() {
            }
        });
        d.show();
    }

    /**
     * Import loadouts from a file
     * @param file Loadout file (.pref or .zip)
     */
    private void importLoadouts(File file) {

        List<String> prefs = new ArrayList<>();
        PreferenceControl prefCtrl = PreferenceControl.getInstance(_context);

        // Import preferences from data package or .pref file
        if (FileSystemUtils.checkExtension(file, "zip")) {
            ZipFile zipFile = null;
            try {
                zipFile = new ZipFile(file);
                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.getName().endsWith(".pref"))
                        prefs.addAll(prefCtrl.loadSettings(
                                zipFile.getInputStream(entry)));
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to import preferences: " + file, e);
            } finally {
                IoUtils.close(zipFile);
            }
        } else
            prefs.addAll(prefCtrl.loadSettings(file));

        // Check if any loadouts were included
        int loCount = 0;
        LoadoutItemModel primaryLoadout = null;
        for (String pref : prefs) {
            if (pref.startsWith(LoadoutManager.LOADOUT_PREFIX)) {
                primaryLoadout = LoadoutManager.getInstance().getLoadout(
                        pref.substring(LoadoutManager.LOADOUT_PREFIX.length()));
                loCount++;
            }
        }

        // Toast user if success/fail
        String msg;
        if (loCount == 1 && primaryLoadout != null)
            msg = _context.getString(R.string.import_toolbar_success_msg1,
                    primaryLoadout.getTitle());
        else if (loCount <= 0)
            msg = _context.getString(R.string.import_toolbar_fail_msg);
        else
            msg = _context.getString(R.string.import_toolbar_success_msg2,
                    loCount);

        Toast.makeText(_context, msg, Toast.LENGTH_LONG).show();
    }
}
