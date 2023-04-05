
package com.atakmap.android.user.icon;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.atakmap.android.gui.ColorPalette;
import com.atakmap.android.gui.ColorPalette.OnColorSelectedListener;
import com.atakmap.android.icons.UserIcon;
import com.atakmap.android.icons.UserIconDatabase;
import com.atakmap.android.icons.UserIconSet;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.toolbar.Tool;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.user.CustomNamingView;
import com.atakmap.android.user.EnterLocationTool;
import com.atakmap.android.user.ExpandableGridView;
import com.atakmap.android.user.PlacePointTool;
import com.atakmap.android.user.icon.IconPallet.CreatePointException;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 
 * 
 */
public class UserIconPalletFragment extends Fragment {
    private static final String TAG = "UserIconPalletFragment";

    public static final String DEFAULT_GROUP = "Other";

    private Context _context;
    private UserIconSet iconset;

    private ExpandableGridView gridview;

    private Button groupButton;
    private ImageButton colorButton;
    private int curColor;

    private UserIcon selectedIcon;
    private View selectedView;
    private View v;
    private final Object lock = new Object();
    private CustomNamingView _customNamingView = null;

    /**
     * Selected position within the currently selected group
     */
    private int selectedPosition;

    private UserIconDatabase userIconDatabase;
    private UserIconsetAdapter iconsetAdapter;

    private SharedPreferences _prefs;

    /**
     * Create a new instance of CountingFragment, providing "num"
     * as an argument.
     */
    public static UserIconPalletFragment newInstance(UserIconSet iconset) {
        UserIconPalletFragment f = new UserIconPalletFragment();

        Bundle args = new Bundle();
        args.putInt("id", iconset.getId());
        args.putString("name", iconset.getName());
        args.putString("uid", iconset.getUid());
        f.setArguments(args);

        return f;
    }

    public UserIconPalletFragment() {
    }

    /**
     * When creating, retrieve this instance's number from its arguments.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        selectedIcon = null;
        selectedView = null;
        selectedPosition = -1;
        synchronized (lock) {
            if (_customNamingView == null) {
                _customNamingView = new CustomNamingView(
                        CustomNamingView.DEFAULT);
            }
        }

        if (getArguments() == null || getArguments().size() < 1) {
            Log.w(TAG, "onCreate no arguments");
            return;
        }

        //setup DB
        userIconDatabase = UserIconDatabase.instance(getActivity());
        if (userIconDatabase == null) {
            Log.w(TAG, "onCreate no userIconDatabase");
            return;
        }

        iconset = userIconDatabase.getIconSet(
                getArguments().getString("uid"), true, false);

        if (iconset == null) {
            Log.w(TAG, "onCreate no iconset");
            iconset = new UserIconSet("Invalid", UUID.randomUUID().toString());
        }

        if (iconset.getIcons() == null) {
            iconset.setIcons(new ArrayList<UserIcon>());
            Log.w(TAG, "No icons found for: " + iconset.toString());
        }

        _prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        curColor = _prefs.getInt("iconset.selected.color", Color.WHITE);
        Log.d(TAG, "onCreate: " + iconset.toString());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        //Log.d(TAG, "onCreateView");
        _context = inflater.getContext();
        v = inflater.inflate(R.layout.enter_location_user_icon, container,
                false);
        final List<String> groups = iconset.getGroups();
        Collections.sort(groups, NameSort);

        groupButton = v.findViewById(R.id.userIconGroupBtn);
        groupButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                //first clean up current selection and tool status
                clearSelection(false);
                Intent myIntent = new Intent();
                myIntent.setAction(ToolManagerBroadcastReceiver.END_TOOL);
                myIntent.putExtra("tool", EnterLocationTool.TOOL_NAME);
                AtakBroadcast.getInstance().sendBroadcast(myIntent);

                if (FileSystemUtils.isEmpty(groups)) {
                    Log.w(TAG,
                            "No groups found for iconset: "
                                    + iconset.toString());
                    return;
                }

                final String[] groupsArray = groups.toArray(new String[0]);

                AlertDialog.Builder builder = new AlertDialog.Builder(
                        getActivity())
                                .setTitle(R.string.select_group)
                                .setNegativeButton(R.string.cancel, null);

                GridView gridView = new GridView(getActivity());
                gridView.setAdapter(new UserIconsetGroupAdapter(getActivity(),
                        groups));
                gridView.setNumColumns(5);
                builder.setView(gridView);

                final Dialog dialog = builder.create();
                gridView.setOnItemClickListener(
                        new AdapterView.OnItemClickListener() {
                            @Override
                            public void onItemClick(AdapterView<?> parent,
                                    View view,
                                    int position, long id) {
                                dialog.dismiss();

                                String selectedGroup = groupsArray[position];
                                if (FileSystemUtils.isEmpty(selectedGroup)) {
                                    Log.w(TAG, "Failed to select group: "
                                            + position);
                                }

                                refreshGroup(selectedGroup);
                            }
                        });

                dialog.show();
            }
        });

        colorButton = v.findViewById(R.id.userIconColorBtn);
        _updateColorButtonDrawable();
        colorButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                _onColorSelected();
            }
        });

        gridview = v.findViewById(R.id.gridUserIcons);
        iconsetAdapter = new UserIconsetAdapter(getActivity(),
                new ArrayList<UserIcon>());
        gridview.setAdapter(iconsetAdapter);
        gridview.setExpanded(true);

        //init to first alphabetical group
        if (FileSystemUtils.isEmpty(groups)) {
            Log.w(TAG,
                    "No initial groups found for iconset: "
                            + iconset.toString());
            refreshGroup(DEFAULT_GROUP);
        } else {
            String previousGroup = iconset.getSelectedGroup();
            boolean groupSet = false;

            for (String group : groups) {
                if (!FileSystemUtils.isEmpty(group)
                        && group.equals(previousGroup)) {
                    Log.d(TAG, "Initializing to previous group: " + group);
                    refreshGroup(group);
                    groupSet = true;
                    break;
                }
            }

            if (!groupSet) {
                Log.d(TAG, "Initializing to first group: " + groups.get(0));
                refreshGroup(groups.get(0));
            }
        }

        gridview.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View v,
                    int position, long id) {
                if (position >= iconset.getIcons().size()) {
                    Log.e(TAG,
                            "Failed to select icon in position: " + position);
                    return;
                }

                UserIcon icon = iconsetAdapter.mGroupIcons.get(position);
                if (icon == null || !icon.isValid()) {
                    Log.e(TAG, "Unable to load icon: "
                            + (icon == null ? "" : (", " + icon)));
                    return;
                }

                if (selectedPosition == position) {
                    //Log.d(TAG, toString() + ": unselected: " + position);

                    //unselect
                    selectedPosition = -1;
                    selectedIcon = null;
                    selectedView = null;
                    setSelected(v, false);

                    Intent myIntent = new Intent();
                    myIntent.setAction(ToolManagerBroadcastReceiver.END_TOOL);
                    myIntent.putExtra("tool", EnterLocationTool.TOOL_NAME);
                    AtakBroadcast.getInstance().sendBroadcast(myIntent);
                } else {
                    //Log.d(TAG, toString() + ": selected: " + position);

                    //icon selected
                    selectedPosition = position;
                    selectedIcon = icon;
                    setSelected(selectedView, false);
                    selectedView = v;
                    setSelected(selectedView, true);

                    //if point select tool is already active, do not relaunch b/c it is 
                    //"ended" by Tool Mgr in the process
                    Tool tool = ToolManagerBroadcastReceiver.getInstance()
                            .getActiveTool();
                    if (tool != null
                            && EnterLocationTool.TOOL_NAME.equals(tool
                                    .getIdentifier())) {
                        //Log.d(TAG, "Skipping BEGIN_TOOL intent");
                        return;
                    }

                    Intent myIntent = new Intent();
                    myIntent.setAction(
                            "com.atakmap.android.maps.toolbar.BEGIN_TOOL");
                    myIntent.putExtra("tool", EnterLocationTool.TOOL_NAME);
                    myIntent.putExtra("current_type", "point");
                    myIntent.putExtra("checked_position", 0); //not using for this pallet, just set not -1                
                    AtakBroadcast.getInstance().sendBroadcast(myIntent);
                }
            }
        });

        return v;
    }

    private void _updateColorButtonDrawable() {
        curColor = _prefs.getInt("iconset.selected.color", Color.WHITE);
        colorButton.setColorFilter(curColor, Mode.MULTIPLY);
    }

    protected void _onColorSelected() {
        AlertDialog.Builder b = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.point_dropper_text56);
        ColorPalette palette = new ColorPalette(getActivity(), _prefs.getInt(
                "iconset.selected.color", Color.BLACK));
        b.setView(palette);
        final AlertDialog alert = b.create();
        OnColorSelectedListener l = new OnColorSelectedListener() {
            @Override
            public void onColorSelected(int color, String label) {
                alert.cancel();
                _prefs.edit().putInt("iconset.selected.color", color).apply();
                _updateColorButtonDrawable();
                setGroupButtonColor();
                iconsetAdapter.notifyDataSetChanged();
            }
        };
        palette.setOnColorSelectedListener(l);
        alert.show();
    }

    private void refreshGroup(String groupName) {
        List<UserIcon> groupIcons = iconset.getIcons(groupName);
        if (FileSystemUtils.isEmpty(groupIcons)) {
            Log.w(TAG, "No icons for group: " + groupName + " in iconset: "
                    + iconset.toString());
            return;
        }

        iconsetAdapter.setIcons(groupIcons);
        String groupText = groupName;
        groupButton.setText(groupText);
        setGroupButtonColor();

        iconset.setSelectedGroup(groupName);
        userIconDatabase.setSelectedGroup(iconset);
        Log.d(TAG, "Set icon group: " + groupName + " with icon count: "
                + groupIcons.size());
    }

    private void setGroupButtonColor() {
        if (groupButton == null || iconsetAdapter == null
                || iconsetAdapter.mGroupIcons == null)
            return;

        //take first (most used) icon since setIcons() sorts the  group icons
        Bitmap groupIcon = UserIconDatabase.instance(getActivity())
                .getIconBitmap(
                        iconsetAdapter.mGroupIcons.get(0).getId());
        if (groupIcon != null) {
            Drawable groupIconDrawable = new BitmapDrawable(getActivity()
                    .getResources(), groupIcon);
            groupIconDrawable.setBounds(0, 0, 48, 48);
            groupIconDrawable.setColorFilter(curColor, Mode.MULTIPLY);
            groupButton.setCompoundDrawables(null, groupIconDrawable, null,
                    null);
        } else {
            Log.w(TAG, "No button icon to set for group");
        }
    }

    @Override
    public void onPause() {
        clearSelection(true);
        LinearLayout customHolder = v
                .findViewById(R.id.customHolder);
        customHolder.removeAllViews();
        customHolder.removeView(_customNamingView.getMainView());
        super.onPause();
    }

    @Override
    public void onResume() {
        LinearLayout customHolder = v
                .findViewById(R.id.customHolder);
        customHolder.addView(_customNamingView.getMainView());
        super.onResume();
    }

    public void clearSelection(boolean bPauseListener) {
        //Log.d(TAG, "clearSelection: " + bPauseListener);

        selectedIcon = null;
        selectedPosition = -1;
        if (selectedView != null) {
            setSelected(selectedView, false);
            selectedView = null;
        }
    }

    /**
     * More reliable way of representing a selected item
     * @param v
     * @param selected
     */
    private void setSelected(View v, boolean selected) {
        if (v == null)
            return;
        v.setBackgroundColor(_context.getResources().getColor(
                selected ? R.color.led_green
                        : R.color.dark_gray));
    }

    public Marker getPointPlacedIntent(final GeoPointMetaData point,
            final String uid)
            throws CreatePointException {
        //Log.d(TAG, "getPointPlacedIntent: " + selectedCotType);

        if (selectedIcon == null)
            throw new CreatePointException(
                    "Select an icon before entering a location.");

        //if type is set in iconset, use type but set affiliation
        //based on color
        String type = null;
        if (!FileSystemUtils.isEmpty(selectedIcon.get2525cType())) {
            type = getTypeFromColor(selectedIcon.get2525cType());
        }
        //no type set, just set default based on affiliation
        if (FileSystemUtils.isEmpty(type)) {
            type = getTypeFromColor();
        }

        String iconsetPath = selectedIcon.getIconsetPath();

        //increment use count to display user most common icons
        selectedIcon.setUseCount(selectedIcon.getUseCount() + 1);
        UserIconDatabase.instance(getActivity()).incrementIconUseCount(
                selectedIcon);

        _prefs.edit().putString("lastCoTTypeSet", type)
                .putString("lastIconsetPath", iconsetPath).apply();
        String callsign;
        if (!_customNamingView.genCallsign().equals("")) {
            callsign = _customNamingView.genCallsign();
            _customNamingView.incrementStartIndex();
        } else {
            callsign = FileSystemUtils
                    .stripExtension(selectedIcon.getFileName()) + " "
                    + selectedIcon.getUseCount();
        }

        PlacePointTool.MarkerCreator mc = new PlacePointTool.MarkerCreator(
                point)
                        .setUid(uid)
                        .setType(type)
                        .setColor(curColor)
                        .setIconPath(iconsetPath)
                        .showCotDetails(false)
                        .setCallsign(callsign);
        return mc.placePoint();
    }

    /**
     * Set affiliation based on color
     * 
     * @param type2525c
     * @return
     */
    private String getTypeFromColor(String type2525c) {
        switch (curColor) {
            //red
            case ColorPalette.COLOR4:
            case ColorPalette.COLOR5:
            case ColorPalette.COLOR6:
            case ColorPalette.COLOR7:
                if (type2525c.startsWith("a-u-"))
                    return type2525c.replaceFirst("a-u-", "a-h-");
                else
                    return type2525c;

                //blue
            case ColorPalette.COLOR8:
            case ColorPalette.COLOR9:
            case ColorPalette.COLOR10:
            case ColorPalette.COLOR11:
                if (type2525c.startsWith("a-u-"))
                    return type2525c.replaceFirst("a-u-", "a-f-");
                else
                    return type2525c;

                //green
            case ColorPalette.COLOR12:
            case ColorPalette.COLOR13:
                if (type2525c.startsWith("a-u-"))
                    return type2525c.replaceFirst("a-u-", "a-n-");
                else
                    return type2525c;

                //yellow, other
            default:
            case ColorPalette.COLOR1:
            case ColorPalette.COLOR2:
            case ColorPalette.COLOR3:
            case ColorPalette.COLOR14:
            case ColorPalette.COLOR15:
                //no fixup needed
                return type2525c;
        }
    }

    /**
     * Map colors to 2525C based on <code>ColorPalette</code>
     * @return
     */
    private String getTypeFromColor() {
        switch (curColor) {
            //red
            case ColorPalette.COLOR4:
            case ColorPalette.COLOR5:
            case ColorPalette.COLOR6:
            case ColorPalette.COLOR7:
                return "a-h-G";

            //blue
            case ColorPalette.COLOR8:
            case ColorPalette.COLOR9:
            case ColorPalette.COLOR10:
            case ColorPalette.COLOR11:
                return "a-f-G";

            //green
            case ColorPalette.COLOR12:
            case ColorPalette.COLOR13:
                return "a-n-G";

            //yellow, other
            default:
            case ColorPalette.COLOR1:
            case ColorPalette.COLOR2:
            case ColorPalette.COLOR3:
            case ColorPalette.COLOR14:
            case ColorPalette.COLOR15:
                return "a-u-G";
        }
    }

    /**
     * Reset the icons to force a sort
     */
    public void refresh() {
        // Log.d(TAG, toString() + ": refresh");
        if (iconsetAdapter != null)
            iconsetAdapter.setIcons(iconsetAdapter.mGroupIcons);
    }

    /**
     * Adapter to manage User Icon grid
     * 
     * 
     */
    class UserIconsetAdapter extends IconsetAdapterBase {
        private static final String TAG = "UserIconsetAdapter";

        public UserIconsetAdapter(Context c, List<UserIcon> icons) {
            super(c, icons);
        }

        @Override
        public void setIcons(List<UserIcon> icons) {
            mGroupIcons = icons;
            Collections.sort(mGroupIcons, UseCountSort);
            notifyDataSetChanged();
        }

        // create a new ImageView for each item referenced by the Adapter
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = super.getView(position, convertView, parent);
            IconsetAdapterBase.ViewHolder holder = (IconsetAdapterBase.ViewHolder) row
                    .getTag();
            if (holder.image != null) {
                holder.image.getDrawable().setColorFilter(curColor,
                        Mode.MULTIPLY);
            }

            if (position == selectedPosition)
                setSelected(row, true);
            return row;
        }
    }

    /**
     * Adapter to manage User Icon Group grid
     * 
     * 
     */
    class UserIconsetGroupAdapter extends BaseAdapter {
        private static final String TAG = "UserIconsetGroupAdapter";

        class ViewHolder {
            ImageView image;
            TextView label;
        }

        protected final Context mContext;
        protected final List<String> mGroupNames;

        public UserIconsetGroupAdapter(Context c, List<String> groupNames) {
            mContext = c;
            mGroupNames = groupNames;
        }

        @Override
        public int getCount() {
            return mGroupNames.size();
        }

        @Override
        public Object getItem(int position) {
            if (position < mGroupNames.size())
                return mGroupNames.get(position);

            return null;
        }

        @Override
        public long getItemId(int position) {
            if (position < mGroupNames.size())
                return mGroupNames.get(position).hashCode();

            return 0;
        }

        // create a new ImageView for each item referenced by the Adapter
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (position >= mGroupNames.size()) {
                Log.e(TAG, "Invalid position: " + position);
                LayoutInflater inflater = ((Activity) mContext)
                        .getLayoutInflater();
                return inflater.inflate(R.layout.empty, parent, false);
            }

            View row = convertView;
            ViewHolder holder = null;

            if (row == null) {
                LayoutInflater inflater = ((Activity) mContext)
                        .getLayoutInflater();
                row = inflater.inflate(R.layout.enter_location_group_child,
                        parent, false);

                holder = new ViewHolder();
                holder.image = row
                        .findViewById(R.id.enter_location_group_childImage);
                holder.label = row
                        .findViewById(R.id.enter_location_group_childLabel);
                row.setTag(holder);
            } else {
                holder = (ViewHolder) row.getTag();
            }

            String groupName = mGroupNames.get(position);
            holder.label.setText(groupName);

            List<UserIcon> groupIcons = iconset.getIcons(groupName);
            Collections.sort(groupIcons, UseCountSort);
            if (FileSystemUtils.isEmpty(groupIcons)) {
                Log.w(TAG, "No icons for group: " + groupName + " in iconset: "
                        + iconset.toString());
            } else {
                try {
                    // attempt to create image view for icon
                    Bitmap bitmap = UserIconDatabase.instance(getActivity())
                            .getIconBitmap(
                                    groupIcons.get(0).getId());
                    holder.image.setImageBitmap(bitmap);
                    Drawable d = holder.image.getDrawable();
                    if (bitmap != null && d != null) {
                        d.setColorFilter(curColor, Mode.MULTIPLY);
                        holder.image.setImageDrawable(d);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to load icon for group: " + groupName,
                            e);
                    Drawable d = mContext.getResources().getDrawable(
                            R.drawable.close);
                    holder.image.setImageDrawable(d);
                }
            }

            return row;
        }
    }

    @Override
    public String toString() {
        return iconset == null ? "<no iconset>" : iconset.toString();
    }

    private final Comparator<? super String> NameSort = new Comparator<String>() {

        @Override
        public int compare(String lhs, String rhs) {
            if (FileSystemUtils.isEmpty(lhs) || FileSystemUtils.isEmpty(rhs))
                return 0;

            return lhs.compareToIgnoreCase(rhs);
        }
    };

    public final Comparator<? super UserIcon> UseCountSort = new Comparator<UserIcon>() {

        @Override
        public int compare(UserIcon lhs, UserIcon rhs) {
            if (lhs == null || !lhs.isValid() || rhs == null || !rhs.isValid())
                return 0;

            //if use count is same, fall back on filename
            if (lhs.getUseCount() == rhs.getUseCount())
                return lhs.getFileName().compareToIgnoreCase(rhs.getFileName());

            //display those used more often in front of grid
            return rhs.getUseCount() - lhs.getUseCount();
        }
    };
}
