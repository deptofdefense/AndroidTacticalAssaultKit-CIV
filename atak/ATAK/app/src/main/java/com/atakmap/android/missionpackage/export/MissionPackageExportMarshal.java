
package com.atakmap.android.missionpackage.export;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import com.atakmap.android.features.FeatureSetHierarchyListItem;
import com.atakmap.android.gui.TileButtonDialog;
import com.atakmap.android.hierarchy.HierarchyListItem;
import com.atakmap.android.hierarchy.action.Export;
import com.atakmap.android.importexport.ExportMarshal;
import com.atakmap.android.importexport.ExportMarshalTask;
import com.atakmap.android.importexport.Exportable;
import com.atakmap.android.importexport.FormatNotSupportedException;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.missionpackage.MissionPackageUtils;
import com.atakmap.android.missionpackage.api.DisplayMissionPackageToolCallback;
import com.atakmap.android.missionpackage.api.MissionPackageApi;
import com.atakmap.android.missionpackage.file.MissionPackageManifest;
import com.atakmap.android.util.AttachmentManager;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

/**
 * Marshals <code>Export</code> instances to a mission package
 * 
 * 
 */
public class MissionPackageExportMarshal extends ExportMarshal {

    private static final String TAG = "MissionPackageExportMarshal";
    public static final String IGNORE_IN_MP_KEY = "ignoreMissionPackage";

    protected final List<String> mapItemUIDs;
    protected final List<String> filepaths;
    protected final Context context;
    private boolean newPackage;
    private Boolean incAtt;
    private boolean incChildren = true;

    /**
     * White list of group names to export
     */
    private static final List<String> groupNamesToAllow;

    /**
     * White list of group names to allow child groups
     */
    private static final List<String> parentGroupNamesToAllow;

    static {
        groupNamesToAllow = new ArrayList<>();
        groupNamesToAllow.add("DIPs");
        groupNamesToAllow.add("Route");
        groupNamesToAllow.add("Drawing Objects");
        groupNamesToAllow.add("Range & Bearing");
        groupNamesToAllow.add("Go-To Navigation Markers");
        groupNamesToAllow.add("DRW");
        groupNamesToAllow.add("LPT");
        groupNamesToAllow.add("Icons");
        groupNamesToAllow.add("Mission");
        groupNamesToAllow.add("Overhead Markers");
        groupNamesToAllow.add("CASEVAC");
        groupNamesToAllow.add("Airspace");
        //TODO Bug 5748, support GG overlays explicitly, until better solution can be provided
        groupNamesToAllow.add("Ground Guidance");

        parentGroupNamesToAllow = new ArrayList<>();
        parentGroupNamesToAllow.add("DRW");
        parentGroupNamesToAllow.add("LPT");
        parentGroupNamesToAllow.add("Icons");
        //TODO Bug 5748, support GG overlays explicitly, until better solution can be provided
        parentGroupNamesToAllow.add("Ground Guidance");
        parentGroupNamesToAllow.add("Intervisibility");
        parentGroupNamesToAllow.add("Routes");
        parentGroupNamesToAllow.add("Cordons");
        parentGroupNamesToAllow.add("Movement Projections");

    }

    /**
     * Optionally update an existing package w/out prompting user
     */
    private String missionPackageUID;

    /**
     * The ability to add a group name to allow when considering what can be exported.
     * @param groupName the groupName to add.
     */
    public static void addGroupNameToAllow(final String groupName) {
        groupNamesToAllow.add(groupName);
    }

    /**
     * The ability to remove a group name to allow when considering what can be exported.
     * @param groupName the groupName to remove.
     */
    public static void removeGroupNameToAllow(final String groupName) {
        groupNamesToAllow.remove(groupName);
    }

    /**
     * The ability to add a parent group name to allow when considering what can be 
     * exported.
     * @param groupName the groupName to add.
     */
    public static void addParentGroupNameToAllow(final String groupName) {
        parentGroupNamesToAllow.add(groupName);
    }

    /**
     * The ability to add a parent group name to allow when considering what can be 
     * exported.
     * @param groupName the groupName to remove.
     */
    public static void removeParentGroupNameToAllow(final String groupName) {
        parentGroupNamesToAllow.remove(groupName);
    }

    public MissionPackageExportMarshal(Context context, Boolean incAtt) {
        mapItemUIDs = new ArrayList<>();
        filepaths = new ArrayList<>();
        this.context = context;
        this.newPackage = false;
        this.incAtt = incAtt;
    }

    public MissionPackageExportMarshal(Context context) {
        this(context, null);
    }

    public void setMissionPackageUID(String uid) {
        missionPackageUID = uid;
    }

    @Override
    public Class<?> getTargetClass() {
        return MissionPackageExportWrapper.class;
    }

    public boolean isIncChildren() {
        return incChildren;
    }

    public void setIncChildren(boolean incChildren) {
        this.incChildren = incChildren;
    }

    @Override
    public void execute(final List<Exportable> exports)
            throws IOException, FormatNotSupportedException {
        if (!FileSystemUtils.isEmpty(missionPackageUID)) {
            newPackage = false;
            beginMarshal(context, exports);
            return;
        }

        MapView mv = MapView.getMapView();
        if (mv == null)
            return;
        View v = LayoutInflater.from(context)
                .inflate(R.layout.include_attachment, null);
        final CheckBox attCb = v.findViewById(R.id.include_attachment);
        attCb.setChecked(incAtt == Boolean.TRUE);

        TileButtonDialog d = new TileButtonDialog(mv);
        d.setTitle(R.string.choose_new_or_existing_mission_package,
                context.getString(R.string.mission_package_name));
        d.setIcon(R.drawable.ic_menu_missionpackage);
        d.setCustomView(v);

        d.addButton(R.drawable.ic_menu_missionpackage, R.string.new_text);
        d.addButton(R.drawable.ic_missionpackage_modified, R.string.existing);
        d.setOnClickListener(new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int w) {
                if (w < 0)
                    return;
                incAtt = attCb.isChecked();
                newPackage = w == 0;
                beginMarshal(context, exports);
            }
        });
        d.show(true);
    }

    /**
     * Begin the export, do not display progress dialog
     * 
     * @param context the context to use during the beginMarshal task
     * @param exports the list of exportable items
     */
    @Override
    protected void beginMarshal(Context context, List<Exportable> exports) {
        new ExportMarshalTask(context, this, exports, false).execute();
    }

    @Override
    public boolean marshal(Collection<Exportable> exports) throws IOException,
            FormatNotSupportedException {
        //loop exports
        for (final Exportable export : exports) {
            if (export == null
                    || !export.isSupported(MissionPackageExportWrapper.class)) {
                Log.d(TAG, "Skipping unsupported export "
                        + (export == null ? "" : export.getClass().getName()));
                continue;
            }

            MissionPackageExportWrapper folder = null;
            try {
                folder = (MissionPackageExportWrapper) export.toObjectOf(
                        MissionPackageExportWrapper.class, getFilters());
            } catch (Exception e) {
                Log.e(TAG, "Failed to export object to Mission Package", e);
            }
            if (folder == null || folder.isEmpty()) {
                Log.d(TAG, "Skipping empty folder");
                continue;
            }

            Log.d(TAG, "Adding folder with: " + folder.getUIDs().size()
                    + " map items, and "
                    + folder.getFilepaths().size() + " files.");
            for (String uid : folder.getUIDs()) {
                if (!mapItemUIDs.contains(uid)) {
                    mapItemUIDs.add(uid);
                }
            }

            if (incChildren) {
                MapView mv = MapView.getMapView();
                MapItemSet itemSet = new MapItemSet();
                for (String uid : mapItemUIDs) {
                    findAssociatedItems(mv, uid, itemSet);
                }
                if (itemSet.size() > 0) {
                    mapItemUIDs.addAll(itemSet.keySet());
                }
            }

            for (String filepath : folder.getFilepaths()) {
                if (!filepaths.contains(filepath)) {
                    filepaths.add(filepath);
                }
            }
        }

        return mapItemUIDs.size() > 0 || filepaths.size() > 0;
    }

    protected void findAssociatedItems(MapView mv, String uid,
            MapItemSet itemSet) {
        if (uid == null || itemSet == null)
            return;

        MapItem item = mv.getRootGroup().deepFindUID(uid);
        if (item == null || itemSet == null)
            return;

        // Add the main item itself
        itemSet.add(item);

        // Child map item UIDs
        if (item.hasMetaValue("childUIDs")) {
            ArrayList<String> childUIDs = item
                    .getMetaStringArrayList("childUIDs");
            if (childUIDs != null) {
                for (String childUID : childUIDs) {
                    if (itemSet.containsKey(childUID))
                        continue;
                    findAssociatedItems(mv, childUID, itemSet);
                }
            }
        }
    }

    /**
     * List of non-duplicate map items
     */
    protected static class MapItemSet extends HashMap<String, MapItem> {

        public MapItemSet(int capacity) {
            super(capacity);
        }

        public MapItemSet() {
            super();
        }

        public void add(MapItem item) {
            put(item.getUID(), item);
        }

        public void addAll(Collection<? extends MapItem> items) {
            for (MapItem item : items)
                add(item);
        }

        public void remove(MapItem item) {
            if (item != null)
                remove(item.getUID());
        }

        public void removeAll(Collection<? extends MapItem> items) {
            for (MapItem item : items)
                remove(item);
        }
    }

    @Override
    public void finalizeMarshal() {
    }

    //imputfilter to prevent % characters from being entered as a mission package name
    public static final InputFilter NAME_FILTER = new InputFilter() {
        @Override
        public CharSequence filter(CharSequence source, int start, int end,
                Spanned dest, int dstart, int dend) {
            for (int k = start; k < end; k++) {
                char c = source.charAt(k);
                if (!FileSystemUtils.isAcceptableInFilename(c))
                    return "";
            }
            return null;
        }
    };

    @Override
    public void postMarshal() {

        if (mapItemUIDs.size() < 1 && filepaths.size() < 1) {
            Log.w(TAG, "Failed to collect any map items or files");
            return;
        }

        if (!FileSystemUtils.isEmpty(missionPackageUID)) {
            String[] mapItemUIDArray = mapItemUIDs
                    .toArray(new String[0]);
            String[] filepathsArray = filepaths.toArray(new String[0]);

            MissionPackageApi.Update(context, missionPackageUID,
                    mapItemUIDArray, filepathsArray, false, null);
            return;
        }

        if (newPackage) {
            // pop up dialog for user to enter name, then create new Mission Package
            final EditText editName = new EditText(context);
            editName.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            //prevent % characters from being entered in mission package name
            editName.setFilters(new InputFilter[] {
                    NAME_FILTER
            });
            // auto increment if MP with this name already exists...?
            MapView mv = MapView.getMapView();
            editName.setText(MissionPackageUtils.getUniqueName(context,
                    MissionPackageUtils.getDefaultName(context, mv != null
                            ? mv.getDeviceCallsign()
                            : context.getString(R.string.export))));

            AlertDialog.Builder b = new AlertDialog.Builder(context);
            b.setTitle(R.string.new_mission_package_name);
            b.setView(editName);
            b.setPositiveButton(R.string.build, null);
            b.setNegativeButton(R.string.cancel, null);
            final AlertDialog d = b.create();
            d.show();
            d.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(
                    new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            String name = editName.getText().toString().trim();
                            if (FileSystemUtils.isEmpty(name)) {
                                Toast.makeText(
                                        context,
                                        context.getString(
                                                R.string.mission_package_name_cannot_be_blank,
                                                context.getString(
                                                        R.string.mission_package_name)),
                                        Toast.LENGTH_LONG).show();
                                return;
                            }
                            if (createNewMissionPackage(name))
                                d.dismiss();
                            else
                                Toast.makeText(
                                        context,
                                        context.getString(
                                                R.string.mission_package_with_name_already_exists,
                                                context.getString(
                                                        R.string.mission_package_name),
                                                name),
                                        Toast.LENGTH_LONG)
                                        .show();
                        }
                    });
        } else {
            // show a list of mission packages and let the user pick one
            final MissionPackageAdapter adapter = new MissionPackageAdapter(
                    context);
            if (adapter.getCount() == 0) {
                Log.d(TAG, "No packages to send");
                Toast.makeText(context, "No " + context.getString(
                        R.string.mission_package_name) + "s available",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            AlertDialog.Builder b = new AlertDialog.Builder(context);
            b.setTitle(context.getString(R.string.choose_mission_package));
            b.setIcon(R.drawable.ic_menu_missionpackage);
            b.setAdapter(adapter, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    MissionPackageManifest mpm = (MissionPackageManifest) adapter
                            .getItem(which);
                    if (mpm == null || !mpm.isValid()) {
                        Log.w(TAG, "Failed to select mission package");
                        return;
                    }

                    String[] mapItemUIDArray = mapItemUIDs
                            .toArray(new String[0]);
                    String[] filepathsArray = filepaths
                            .toArray(new String[0]);

                    MissionPackageApi.Update(context, mpm.getUID(),
                            mapItemUIDArray, filepathsArray, true,
                            DisplayMissionPackageToolCallback.class);
                }
            });
            b.setCancelable(true);
            b.setNegativeButton(R.string.cancel, null);
            final AlertDialog d = b.create();
            d.show();
        }
    }

    @Override
    protected void cancelMarshal() {
    }

    @Override
    public String getContentType() {
        return context.getString(R.string.mission_package_name);
    }

    @Override
    public String getMIMEType() {
        return "application/octet-stream";
    }

    @Override
    public int getIconId() {
        return R.drawable.ic_menu_missionpackage;
    }

    @Override
    public boolean acceptEntry(HierarchyListItem list) {
        // Don't allow user to go below feature file level
        return !(list instanceof FeatureSetHierarchyListItem);
    }

    /*
     * The below filter methods accept items if you return FALSE, not TRUE
     * XXX - Whoever wrote these methods thought that wouldn't be confusing...
     */

    @Override
    public boolean filterListItemImpl(HierarchyListItem item) {
        // Allow export if the item supports Mission Package export wrapper
        return !(item instanceof Export && ((Export) item)
                .isSupported(MissionPackageExportWrapper.class));
    }

    @Override
    public boolean filterItem(MapItem item) {

        //Log.d(TAG, "filterItem " + item.getClass().getName() + ", " + item.getUID());
        if (item.getMetaBoolean(IGNORE_IN_MP_KEY, false))
            return true;

        // filter out machine generated (which ATAK does not persist)
        // Also filter out map items with a role type (team marker)
        String how = item.getMetaString("how", "");
        String role = item.getMetaString("atakRoleType", null);
        if (!FileSystemUtils.isEmpty(how) && how.startsWith("m-")
                || !FileSystemUtils.isEmpty(role)) {
            // Log.d(TAG, "Filtering " + item.getUID() + " with how=" + how);
            return true;
        }

        // Filter out items which should never be turned to CoT
        if (item.hasMetaValue("nevercot"))
            return true;

        return super.filterItem(item);
    }

    @Override
    public boolean filterGroup(MapGroup group) {
        String grpName = group.getFriendlyName();
        //Log.d(TAG, "filterGroup " + grpName);

        //check white list for group names
        boolean bFilter = !groupNamesToAllow.contains(grpName);

        // also check parent group name
        if (bFilter && group.getParentGroup() != null
                && parentGroupNamesToAllow.contains(group.getParentGroup()
                        .getFriendlyName()))
            bFilter = false;

        // If the group explicitly states not to ignore MP then allow
        if (!group.getMetaBoolean(IGNORE_IN_MP_KEY, true))
            bFilter = false;

        return bFilter;
    }

    private boolean createNewMissionPackage(String name) {
        String[] mapItemUIDArray = mapItemUIDs
                .toArray(new String[0]);
        String[] filepathsArray = filepaths
                .toArray(new String[0]);

        //use MPT API to create a new Mission Package and display it
        String path = FileSystemUtils
                .getItem(
                        FileSystemUtils.TOOL_DATA_DIRECTORY
                                + File.separatorChar
                                +
                                context.getString(
                                        R.string.mission_package_folder))
                .getAbsolutePath();

        // Create the Mission Package containing the file, and the CoT for this map item
        name = FileSystemUtils.sanitizeFilename(name);
        if (FileSystemUtils.isEmpty(name)) {
            name = MissionPackageUtils
                    .getUniqueName(
                            context,
                            MissionPackageUtils
                                    .getDefaultName(
                                            context,
                                            context.getString(
                                                    R.string.export)));
        }

        MissionPackageManifest manifest = new MissionPackageManifest(
                name, path);
        File f = new File(FileSystemUtils
                .sanitizeWithSpacesAndSlashes(manifest.getPath()));
        if (IOProviderFactory.exists(f))
            return false;
        if (mapItemUIDArray.length > 0) {
            manifest.addMapItems(mapItemUIDArray);
            if (this.incAtt == Boolean.TRUE) {
                for (String mapIemUID : mapItemUIDArray) {
                    // send all attachments
                    List<File> attachments = AttachmentManager
                            .getAttachments(mapIemUID);
                    if (attachments.size() < 1) {
                        Log.d(TAG,
                                "Found no attachments to send for item: "
                                        + mapIemUID);
                        continue;
                    }

                    // send all attachments
                    Log.v(TAG,
                            "Including all "
                                    + attachments.size()
                                    + " files via Mission Package Tool: "
                                    + mapIemUID);

                    for (File attachment : attachments) {
                        manifest.addFile(attachment,
                                mapIemUID);
                    }
                }
            }
        }

        if (filepathsArray.length > 0) {
            for (String filepath : filepathsArray)
                manifest.addFile(
                        new File(filepath), null);
        }

        MissionPackageApi
                .Save(context,
                        manifest,
                        DisplayMissionPackageToolCallback.class);
        return true;
    }
}
