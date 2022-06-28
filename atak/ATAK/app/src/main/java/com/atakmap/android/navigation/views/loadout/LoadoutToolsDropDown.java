
package com.atakmap.android.navigation.views.loadout;

import com.atakmap.android.gui.TextEntryDialog;
import com.atakmap.android.importexport.send.SendDialog;
import com.atakmap.android.importfiles.sort.ImportPrefSort;
import com.atakmap.android.missionpackage.MissionPackageMapComponent;
import com.atakmap.android.missionpackage.file.MissionPackageManifest;
import com.atakmap.android.missionpackage.file.task.MissionPackageBaseTask;
import com.atakmap.android.navigation.models.NavButtonIntentAction;
import com.atakmap.android.navigation.models.NavButtonModel;
import com.atakmap.android.navigation.views.NavView;

import android.app.AlertDialog;
import android.content.ClipDescription;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.view.DragEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.navigation.NavButtonManager;
import com.atakmap.android.navigation.models.LoadoutItemModel;
import com.atakmap.android.navigation.views.buttons.NavButton;
import com.atakmap.android.navigationstack.NavigationStackItem;
import com.atakmap.android.preference.AtakPreferences;
import com.atakmap.android.toolbar.ToolbarBroadcastReceiver;
import com.atakmap.android.util.MappingAdapterEventReceiver;
import com.atakmap.android.util.MappingVM;
import com.atakmap.android.widgets.TakEditText;
import com.atakmap.app.R;
import com.atakmap.app.preferences.CustomActionBarFragment;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.math.MathUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Drop-down that displays the list of available loadout tools (overflow menu)
 * By default this only shows tools that are not part of the current loadout
 * In edit mode all tools are shown and can be added or removed from the loadout
 */
public class LoadoutToolsDropDown extends NavigationStackItem implements
        SharedPreferences.OnSharedPreferenceChangeListener,
        NavButtonManager.OnModelListChangedListener,
        NavButtonManager.OnModelChangedListener,
        LoadoutManager.OnLoadoutChangedListener,
        MappingAdapterEventReceiver<MappingVM>,
        View.OnLayoutChangeListener,
        View.OnDragListener {

    private static final String TAG = "LoadoutToolsDropDown";

    public static final String TOOLS_DROPDOWN_KEY = "loadout_tools_dropdown_key";
    private static final String PREF_TOOLS_LAYOUT = "loadout_tools_layout";

    private final AtakPreferences _prefs;
    private final LoadoutToolsAdapter _adapter;
    private final LoadoutManager _loadouts;

    private final LoadoutToolsGridView _list;

    private boolean _editMode;
    private boolean _isList = false;
    private int _numGridColumns = 1;
    private final TakEditText _nameTxt;

    private LoadoutItemModel _currentLoadout;
    private LoadoutItemModel _revertLoadout;
    private boolean _editable;

    private int _dragPos = LoadoutToolsVM.NONE;
    private float _dragX, _dragY;
    private int _dragScrollAmount;
    private boolean _dragScrollActive;
    private final float _dragScrollThresh;

    protected LoadoutToolsDropDown(MapView mapView) {
        super(mapView);
        _prefs = new AtakPreferences(mapView);
        _prefs.registerListener(this);
        _loadouts = LoadoutManager.getInstance();

        setAssociationKey(TOOLS_DROPDOWN_KEY);

        AtakBroadcast.getInstance().registerReceiver(this,
                new DocumentedIntentFilter(NavView.REFRESH_BUTTONS));
        _loadouts.addListener(this);
        NavButtonManager.getInstance().addModelListChangedListener(this);
        NavButtonManager.getInstance().addModelChangedListener(this);

        _itemView = LayoutInflater.from(_context)
                .inflate(R.layout.loadout_tools, mapView, false);
        _itemView.addOnLayoutChangeListener(this);

        _list = _itemView.findViewById(R.id.tools_list);
        _nameTxt = _itemView.findViewById(R.id.toolbar_name);
        _nameTxt.setDragEnabled(false);

        _adapter = new LoadoutToolsAdapter(_context);
        _adapter.setEventReceiver(this);
        _list.setAdapter(_adapter);
        _list.setOnDragListener(this);

        Resources res = mapView.getResources();
        _dragScrollAmount = (int) (res.getDisplayMetrics().density * 4);
        _dragScrollThresh = res.getDimension(R.dimen.nav_child_button_size);

        onSharedPreferenceChanged(_prefs.getSharedPrefs(), PREF_TOOLS_LAYOUT);
    }

    @Override
    protected void disposeImpl() {
        AtakBroadcast.getInstance().unregisterReceiver(this);
        _prefs.unregisterListener(this);
        _loadouts.removeListener(this);
        _itemView.removeOnLayoutChangeListener(this);
        NavButtonManager.getInstance().removeModelListChangedListener(this);
    }

    /**
     * Set the loadout used by this drop-down
     * @param loadout Loadout to use
     */
    public void setLoadout(LoadoutItemModel loadout) {
        _currentLoadout = loadout;
        _revertLoadout = null;
        _editable = _currentLoadout != null && !_currentLoadout.isDefault();
        _nameTxt.setText(_currentLoadout != null
                ? _currentLoadout.getTitle()
                : "");
        if (!_editable)
            setEditMode(false);
        updateButtons();
        refresh();
    }

    /**
     * Check if the tools drop-down is in edit mode
     * @return True if in edit mode
     */
    public boolean isInEditMode() {
        return _editMode;
    }

    /**
     * Set whether this loadout is in edit mode
     * @param edit True if enable mode enabled
     */
    public void setEditMode(boolean edit) {
        if (edit && !_editable)
            return;
        if (_editMode != edit) {
            _editMode = edit;

            // Save the original loadout if needed
            if (edit && _currentLoadout != null)
                _revertLoadout = new LoadoutItemModel(_currentLoadout);
            else
                _revertLoadout = null;

            Intent i = new Intent(NavView.NAV_EDITING_KEY);
            i.putExtra("editing", _editMode);
            AtakBroadcast.getInstance().sendBroadcast(i);

            _nameTxt.setVisibility(_editMode ? View.VISIBLE : View.GONE);

            // Close the sub-toolbar if open
            if (edit)
                AtakBroadcast.getInstance().sendBroadcast(new Intent(
                        ToolbarBroadcastReceiver.UNSET_TOOLBAR));

            // Refresh the list of tools
            updateButtons();
            refresh();
        }
    }

    @Override
    public List<ImageButton> getButtons() {
        List<ImageButton> buttons = new ArrayList<>();

        // Edit this layout
        if (_editable) {
            buttons.add(createButton(_editMode ? R.drawable.ic_navstack_save
                    : R.drawable.ic_navstack_edit, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (_editMode && !save())
                                return;
                            setEditMode(!_editMode);
                        }
                    }));
        } else if (_currentLoadout != null) {
            // Show an edit button that jumps into the new loadout editor
            // if we don't have any custom loadouts available
            List<LoadoutItemModel> loadouts = _loadouts.getLoadouts();
            if (loadouts.isEmpty() || loadouts.size() == 1
                    && loadouts.get(0).isDefault()) {
                buttons.add(createButton(R.drawable.ic_navstack_edit,
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                LoadoutItemModel ld = new LoadoutItemModel(
                                        _context.getString(
                                                R.string.new_loadout),
                                        _currentLoadout);
                                ld.setTemporary(true);
                                ld.showZoomButton(_currentLoadout
                                        .containsButton("zoom"));
                                _loadouts.addLoadout(ld);
                                _loadouts.setCurrentLoadout(ld);
                                setLoadout(ld);
                                setEditMode(true);
                            }
                        }));
            }
        }

        // Export layout
        if (!_editMode && _currentLoadout != null
                && !_currentLoadout.isDefault()) {
            buttons.add(createButton(R.drawable.ic_navstack_export,
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            promptExportLoadout();
                        }
                    }));
        }

        // Change the way tools are displayed
        buttons.add(createButton(_isList ? R.drawable.ic_navstack_grid
                : R.drawable.ic_navstack_list,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        _prefs.set(PREF_TOOLS_LAYOUT,
                                _isList ? "grid" : "list");
                    }
                }));

        // Go back to the loadout list
        buttons.add(createButton(R.drawable.ic_navstack_settings,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        promptDiscardChanges(new Runnable() {
                            @Override
                            public void run() {
                                popView();
                            }
                        });
                    }
                }));

        return buttons;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (NavView.REFRESH_BUTTONS.equals(action))
            refresh();
    }

    @Override
    public boolean onBackButton() {
        promptDiscardChanges();
        return true;
    }

    @Override
    public boolean onCloseButton() {
        promptDiscardChanges();
        return true;
    }

    /**
     * Make sure the user-entered name isn't empty and isn't taken by another
     * loadout
     * @return Error string if there's an issue, null if no issue
     */
    private String checkName() {
        if (_currentLoadout == null)
            return null;

        // Check if the new name is empty text
        String newName = _nameTxt.getText().toString();
        if (FileSystemUtils.isEmpty(newName))
            return _context.getString(R.string.enter_loadout_name);

        // Check if the name is the same as before (good to go)
        String curName = _currentLoadout.getTitle();
        if (newName.equals(curName))
            return null;

        // Check for existing loadouts with this name
        for (LoadoutItemModel lm : _loadouts.getLoadouts()) {
            String name = lm.getTitle();
            if (newName.equals(name))
                return _context.getString(R.string.enter_different_name);
        }

        return null;
    }

    @Override
    public String getTitle() {
        return _context.getString(R.string.tools);
    }

    @Override
    public void onClose() {
        discardChanges();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key) {

        if (key == null)
            return;

        // Controls how tools are laid out (grid vs. list)
        if (key.equals(PREF_TOOLS_LAYOUT)) {
            _isList = FileSystemUtils.isEquals(_prefs.get(PREF_TOOLS_LAYOUT,
                    "grid"), "list");
            _list.setNumColumns(_isList ? 1 : _numGridColumns);
            updateButtons();
            refresh();
        }

        // Icon color has been changed
        else if (key.equals(CustomActionBarFragment.ACTIONBAR_ICON_COLOR_KEY))
            refresh();
    }

    @Override
    public void onModelListChanged() {
        refresh();
    }

    @Override
    public void onModelChanged(NavButtonModel model) {
        refresh();
    }

    /**
     * Build the list of tools shown
     */
    private void refresh() {
        ArrayList<MappingVM> navItems = new ArrayList<>();
        List<NavButtonModel> models = _currentLoadout != null
                ? _currentLoadout.getSortedModels()
                : NavButtonManager.getInstance().getButtonModels();

        for (NavButtonModel mdl : models) {
            boolean used = false;
            boolean hidden = false;
            boolean visible = false;
            boolean hasActions = mdl.getAction() != null
                    || mdl.getActionLong() != null;
            if (_currentLoadout != null) {
                NavButton btn = NavView.getInstance().findButtonByModel(mdl);
                used = _currentLoadout.containsButton(mdl);
                hidden = !_currentLoadout.isToolVisible(mdl.getReference());
                visible = btn != null && btn.getVisibility() == View.VISIBLE;
            }

            // Don't show used or hidden tools when we're not in edit mode
            if (!_editMode && (used && visible || hidden || !hasActions))
                continue;

            if (_isList)
                navItems.add(new LoadoutToolsListVM(mdl, used,
                        _editMode, hidden));
            else
                navItems.add(new LoadoutToolsGridVM(mdl, used,
                        _editMode, hidden));
        }
        _adapter.replaceItems(navItems);
    }

    /**
     * Persist the current loadout to preferences
     * @return True if successful, false otherwise
     */
    private boolean save() {
        if (_currentLoadout == null)
            return true;

        // Make sure the name is valid
        String nameCheck = checkName();
        if (nameCheck != null) {
            Toast.makeText(_context, nameCheck, Toast.LENGTH_LONG).show();
            return false;
        }

        _currentLoadout.setTitle(_nameTxt.getText().toString());
        _currentLoadout.setTemporary(false);
        _currentLoadout.persist();
        return true;
    }

    /**
     * Discard unsaved changes
     */
    private void discardChanges() {
        if (_editMode) {
            if (_currentLoadout != null) {
                if (_currentLoadout.isTemporary())
                    LoadoutManager.getInstance().removeLoadout(_currentLoadout);
                else if (_revertLoadout != null) {
                    _currentLoadout.copy(_revertLoadout);
                    _loadouts.notifyLoadoutChanged(_currentLoadout);
                }
            }
            setEditMode(false);
        }
    }

    /**
     * Prompt to export the current loadout
     */
    private void promptExportLoadout() {
        final LoadoutItemModel loadout = _currentLoadout;
        if (loadout == null)
            return;

        TextEntryDialog d = new TextEntryDialog(loadout.getTitle());
        d.setTitle(_context.getString(R.string.export_loadout));
        d.setHint(_context.getString(R.string.name));
        d.setValidator(new TextEntryDialog.Predicate<String>() {
            @Override
            public boolean apply(String s) {
                return !FileSystemUtils.isEmpty(s);
            }
        });
        d.subscribe(new TextEntryDialog.TextEntryEventListener() {
            @Override
            public void onEvent(TextEntryDialog.TextEntryEvent event) {
                String text = event.getText();
                exportLoadout(loadout, text);
            }
        });
        d.show();
    }

    /**
     * Export the current loadout to the given DP file
     * @param loadout Loadout to export
     * @param fileName File name
     */
    private void exportLoadout(LoadoutItemModel loadout, String fileName) {

        // Convert loadout to preferences XML
        String xml = _loadouts.serializeToXML(loadout);

        final File exportDir = FileSystemUtils
                .getItem(FileSystemUtils.EXPORT_DIRECTORY);

        // Save XML to export directory
        final File file = new File(exportDir, fileName + ".pref");
        try (FileOutputStream fos = IOProviderFactory.getOutputStream(file)) {
            fos.write(xml.getBytes(FileSystemUtils.UTF8_CHARSET));
        } catch (Exception e) {
            Log.e(TAG, "Failed to save loadout XML: " + file, e);
            Toast.makeText(_context, _context.getString(
                    R.string.importmgr_failed_to_export, file.getName()),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Save to data package
        MissionPackageManifest mpm = new MissionPackageManifest(fileName,
                loadout.getUID(), exportDir.getAbsolutePath());
        mpm.getConfiguration().setImportInstructions(true, true, null);
        mpm.addFile(file, ImportPrefSort.CONTENT_TYPE, null);
        MissionPackageMapComponent.getInstance().getFileIO().save(mpm, false,
                new MissionPackageBaseTask.Callback() {
                    @Override
                    public void onMissionPackageTaskComplete(
                            MissionPackageBaseTask task, boolean success) {
                        FileSystemUtils.delete(file);
                        promptSend(task.getManifest());
                    }
                });
    }

    /**
     * Prompt the user to send a newly exported loadout data package
     * @param mpm Loadout data package
     */
    private void promptSend(MissionPackageManifest mpm) {
        final File file = new File(mpm.getPath());
        AlertDialog.Builder b = new AlertDialog.Builder(_context);
        b.setTitle(
                _context.getString(R.string.importmgr_exported, mpm.getName()));
        b.setIcon(R.drawable.nav_package);
        b.setMessage(_context.getString(R.string.importmgr_exported_file,
                FileSystemUtils.prettyPrint(file)));
        b.setPositiveButton(R.string.send,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        SendDialog.Builder b = new SendDialog.Builder(_mapView);
                        b.setName(file.getName());
                        b.setIcon(R.drawable.nav_package);
                        b.setMissionPackage(mpm);
                        b.show();
                    }
                });
        b.setNegativeButton(R.string.done, null);
        b.show();
    }

    /**
     * Prompt to discard saved changes and, if confirmed, continue with a given
     * task. If there are no changes to discard then the task is performed anyway.
     *
     * @param task Task performed when dialog confirmed (not cancelled)
     */
    private void promptDiscardChanges(final Runnable task) {
        if (!_editMode) {
            task.run();
            return;
        }
        AlertDialog.Builder b = new AlertDialog.Builder(_context);
        b.setTitle(R.string.discard_changes2);
        b.setMessage(R.string.discard_all_unsaved_changes);
        b.setPositiveButton(R.string.yes,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        discardChanges();
                        task.run();
                    }
                });
        b.setNeutralButton(R.string.no, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (save()) {
                    setEditMode(false);
                    task.run();
                }
            }
        });
        b.setNegativeButton(R.string.cancel, null);
        b.show();
    }

    /**
     * Prompt to discard saved changes and, if confirmed, close the entire
     * drop-down. If there are no changes to discard then the drop-down is
     * immediately closed.
     */
    private void promptDiscardChanges() {
        promptDiscardChanges(new Runnable() {
            @Override
            public void run() {
                closeNavigationStack();
            }
        });
    }

    @Override
    public void onLoadoutAdded(LoadoutItemModel loadout) {
    }

    @Override
    public void onLoadoutModified(LoadoutItemModel loadout) {
        if (_currentLoadout == loadout)
            refresh();
    }

    @Override
    public void onLoadoutRemoved(LoadoutItemModel loadout) {
    }

    @Override
    public void onLoadoutSelected(LoadoutItemModel loadout) {
    }

    @Override
    public void eventReceived(MappingVM event) {
        if (event instanceof LoadoutToolsVM) {
            // Fire the intent action based on the latest touch event
            NavButtonIntentAction action = ((LoadoutToolsVM) event).action;
            if (action != null) {
                if (action.shouldDismissMenu())
                    closeNavigationStack();
                AtakBroadcast.getInstance().sendBroadcast(action.getIntent());
            }
        }
    }

    @Override
    public void onLayoutChange(View v, int l, int t, int r, int b,
            int ol, int ot, int or, int ob) {
        // Update the number of displayed columns in grid mode
        int width = r - l;
        int oldWidth = or - ol;
        if (width != oldWidth) {
            float cellSize = _context.getResources().getDimension(
                    R.dimen.nav_grid_item_size);
            _numGridColumns = (int) Math.max(width / cellSize, 1);
            if (!_isList)
                _list.setNumColumns(_numGridColumns);
        }
    }

    @Override
    public boolean onDrag(View v, DragEvent event) {
        int action = event.getAction();

        ClipDescription desc = event.getClipDescription();
        if (desc == null)
            return false;

        // Only respond to valid content labels
        CharSequence label = desc.getLabel();
        if (label == null)
            return false;

        boolean adding = NavView.DRAG_ADD_TOOL.contentEquals(label);
        boolean removing = NavView.DRAG_MOVE_TOOL.contentEquals(label);
        if (!adding && !removing)
            return false;

        // Make sure the current loadout is valid
        if (_currentLoadout == null)
            return false;

        // Drag when deleting
        switch (action) {
            case DragEvent.ACTION_DRAG_STARTED:
                break;
            case DragEvent.ACTION_DRAG_ENTERED:
                if (removing)
                    _list.showDeleteOverlay(true);
                break;

            // Track which tool is being dragged where in the list
            case DragEvent.ACTION_DRAG_LOCATION: {
                if (removing)
                    break;

                // Highlight the side that's being dragged closest
                _dragX = event.getX();
                _dragY = event.getY();
                updateDragSide();

                // Set scroll direction
                boolean scrollActive = false;
                if (_dragY < _dragScrollThresh) {
                    _dragScrollAmount = -Math.abs(_dragScrollAmount);
                    scrollActive = true;
                } else if (_dragY > _list.getHeight() - _dragScrollThresh) {
                    _dragScrollAmount = Math.abs(_dragScrollAmount);
                    scrollActive = true;
                }

                // Start scroll loop
                setScrollActive(scrollActive);
                break;
            }
            case DragEvent.ACTION_DROP: {
                NavButtonModel mdl = NavButton.getButtonModel(event);
                if (mdl == null)
                    return false;
                if (adding) {
                    // Move the tool to a new position in the overflow
                    List<NavButtonModel> models = _currentLoadout
                            .getSortedModels();
                    int idx = models.indexOf(mdl);
                    if (_dragPos >= 0 && _dragPos <= models.size()
                            && (_dragPos < idx || _dragPos > idx + 1)) {
                        models.add(_dragPos, mdl);
                        models.remove(_dragPos < idx ? idx + 1 : idx);
                        _currentLoadout.setSortOrder(models);
                        _loadouts.notifyLoadoutChanged(_currentLoadout);
                    }
                } else {
                    // Remove the selected tool from the current loadout
                    _currentLoadout.removeButton(mdl);
                    _loadouts.notifyLoadoutChanged(_currentLoadout);
                }
                // Fallthrough to reset the name entry and hide the overlay
            }
            case DragEvent.ACTION_DRAG_EXITED:
            case DragEvent.ACTION_DRAG_ENDED: {
                setScrollActive(false);
                if (adding) {
                    // Reset drag highlights
                    _dragPos = LoadoutToolsVM.NONE;
                    int count = _adapter.getCount();
                    for (int i = 0; i < count; i++) {
                        LoadoutToolsVM vm = (LoadoutToolsVM) _adapter
                                .getItem(i);
                        vm.setDragPosition(LoadoutToolsVM.NONE);
                    }
                    _adapter.notifyDataSetChanged();
                } else
                    _list.showDeleteOverlay(false);
                break;
            }
        }

        return true;
    }

    /**
     * Update the side that's being dragged towards
     */
    private void updateDragSide() {
        int firstPos = _list.getFirstVisiblePosition();
        int childCount = _list.getChildCount();
        int adapterCount = _adapter.getCount();
        boolean update = false;
        for (int i = 0; i < childCount; i++) {
            View c = _list.getChildAt(i);
            if (c == null)
                continue;

            // Get the corresponding position
            int pos = firstPos + i;
            if (pos < 0 || pos >= adapterCount)
                continue;

            // Get the view model
            LoadoutToolsVM vm = (LoadoutToolsVM) _adapter.getItem(pos);
            if (vm == null)
                continue;

            // Determine the side of the view the drag is closest to
            int dragSide = LoadoutToolsVM.NONE;
            int cLeft = c.getLeft();
            int cRight = c.getRight();
            int cTop = c.getTop();
            int cBottom = c.getBottom();
            if (_dragX >= cLeft && _dragX < cRight
                    && _dragY >= cTop && _dragY < cBottom) {
                float xOff = ((_dragX - cLeft) / c.getWidth()) - 0.5f;
                float yOff = ((_dragY - cTop) / c.getHeight()) - 0.5f;
                if (_isList) {
                    if (yOff < 0) {
                        dragSide = LoadoutToolsVM.TOP;
                        _dragPos = pos;
                    } else {
                        dragSide = LoadoutToolsVM.BOTTOM;
                        _dragPos = pos + 1;
                    }
                } else {
                    if (xOff < 0) {
                        dragSide = LoadoutToolsVM.LEFT;
                        _dragPos = pos;
                    } else {
                        dragSide = LoadoutToolsVM.RIGHT;
                        _dragPos = pos + 1;
                    }
                }
                _dragPos = MathUtils.clamp(_dragPos, 0, adapterCount);
            }

            // Update drag position while checking if we need to
            // perform a full view update
            if (dragSide != vm.getDragPosition()) {
                vm.setDragPosition(dragSide);
                update = true;
            }
        }

        // Update the items in the list/grid
        if (update)
            _adapter.notifyDataSetChanged();
    }

    /**
     * Set whether the scroll is active
     * @param scrollActive True if scroll is active
     */
    private void setScrollActive(boolean scrollActive) {
        if (_dragScrollActive != scrollActive) {
            _dragScrollActive = scrollActive;
            _list.setScrollHint(scrollActive ? _dragScrollAmount : 0);
            if (scrollActive)
                scrollList();
        }
    }

    /**
     * Scroll the list by a set amount
     */
    private void scrollList() {
        if (_dragScrollActive) {
            _list.scrollListBy(_dragScrollAmount);
            _mapView.post(new Runnable() {
                @Override
                public void run() {
                    if (_dragScrollActive) {
                        updateDragSide();
                        scrollList();
                    }
                }
            });
        }
    }
}
