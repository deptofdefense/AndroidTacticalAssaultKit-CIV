
package com.atakmap.android.fires;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ListView;
import android.graphics.Color;

import com.atakmap.android.data.ClearContentRegistry;
import com.atakmap.android.dropdown.DropDown;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.hierarchy.HierarchyManagerView;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.util.LimitingThread;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.AtakMapView;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class HostileManagerDropDownReceiver extends DropDownReceiver implements
        MapEventDispatcher.MapEventDispatchListener,
        AtakMapView.OnMapMovedListener, DropDown.OnStateListener {

    private static final String TAG = "HostileManagerDropDownReceiver";
    private static final String FILENAME = "ninelinemarkers.dat";

    private CheckBox filterCb;
    private final MapEventDispatcher dispatcher;

    private final List<Marker> hostilesListBase; //Holds all known 9-line markers
    private final List<Marker> displayList; //Holds what's currently being displayed

    private final ArrayList<MapItemHolder> previousList;
    private final HostileManagerAdapter adapter;
    private final Object lock = new Object();

    private final LimitingThread calc;

    private ImageButton upButton;
    private ImageButton downButton;
    private HierarchyManagerView content;

    /**
     * used to store color and order info
     */
    private static class MapItemHolder {
        public final String uid;
        public final int color;
        public boolean display;

        MapItemHolder(final String u, final int c) {
            uid = u;
            color = c;
        }

        @Override
        public boolean equals(final Object comp) {
            if (comp instanceof MapItemHolder) {
                MapItemHolder holder = (MapItemHolder) comp;
                if (holder.uid.equals(this.uid)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public int hashCode() {
            int retval = (uid == null) ? 0 : uid.hashCode();
            retval = 31 * retval + color;
            retval = 31 * retval + (display ? 1 : 0);
            return retval;
        }
    }

    protected HostileManagerDropDownReceiver(MapView mapView) {
        super(mapView);
        dispatcher = getMapView().getMapEventDispatcher();
        dispatcher.addMapEventListener(MapEvent.ITEM_ADDED, this);
        dispatcher.addMapEventListener(MapEvent.ITEM_GROUP_CHANGED, this);
        dispatcher.addMapEventListener(MapEvent.ITEM_REMOVED, this);
        dispatcher.addMapEventListener(MapEvent.ITEM_REFRESH, this);
        dispatcher.addMapEventListener(MapEvent.ITEM_PERSIST, this);

        ClearContentRegistry.getInstance().registerListener(dataMgmtReceiver);

        hostilesListBase = new ArrayList<>();
        displayList = new ArrayList<>();

        previousList = readContacts();

        // before the system is loaded.
        mapView.getRootGroup().deepForEachItem(
                new MapGroup.OnItemCallback<PointMapItem>(PointMapItem.class) {
                    @Override
                    protected boolean onMapItem(PointMapItem item) {
                        synchronized (lock) {
                            if (item instanceof Marker) {
                                Marker marker = (Marker) item;
                                if (marker.getType().startsWith("a-h")
                                        && !hostilesListBase.contains(marker)) {
                                    addHostile(marker);
                                }
                            }
                        }
                        return false;
                    }
                });
        adapter = new HostileManagerAdapter(this, displayList);

        mapView.addOnMapMovedListener(this);

        calc = new LimitingThread("Hostile-AOI-Intersection",
                new Runnable() {
                    @Override
                    public void run() {
                        updateDisplayList();
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ignored) {
                        }
                    }
                });

    }

    @Override
    public void onMapMoved(AtakMapView view, boolean animate) {
        if (isVisible())
            calc.exec();
    }

    @Override
    protected void disposeImpl() {
        dispatcher.removeMapEventListener(MapEvent.ITEM_ADDED, this);
        dispatcher.removeMapEventListener(MapEvent.ITEM_GROUP_CHANGED, this);
        dispatcher.removeMapEventListener(MapEvent.ITEM_REMOVED, this);
        dispatcher.removeMapEventListener(MapEvent.ITEM_REFRESH, this);
        dispatcher.removeMapEventListener(MapEvent.ITEM_PERSIST, this);
        ClearContentRegistry.getInstance().unregisterListener(dataMgmtReceiver);

        saveContacts();
        calc.dispose();
    }

    /**
     * add a new hostile, but check to see if color/ordering info is available
     * @param item  item to add
     */
    private void addHostile(Marker item) {
        synchronized (lock) {
            int index = previousList
                    .indexOf(new MapItemHolder(item.getUID(),
                            HostileListItem.WHITE));
            if (index != -1) {
                item.setMetaInteger("textColor", previousList.get(index).color);
                if (index < hostilesListBase.size())
                    hostilesListBase.set(index, item);
                else
                    hostilesListBase.add(item);
            } else {
                hostilesListBase.add(item);
            }
        }
        updateDisplayList();
        getMapView().post(new Runnable() {
            @Override
            public void run() {
                if (adapter != null)
                    adapter.notifyDataSetChanged();
            }
        });
    }

    /**
     * read color and order info from file
     * @return  array of sorted info
     */

    private ArrayList<MapItemHolder> readContacts() {
        ArrayList<MapItemHolder> items = new ArrayList<>();
        File inputFile = FileSystemUtils.getItem("Databases/" + FILENAME);
        if (IOProviderFactory.exists(inputFile)) {
            try (InputStream is = IOProviderFactory.getInputStream(inputFile)) {
                byte[] temp = new byte[is.available()];
                int read = is.read(temp);
                String menuString = new String(temp, 0, read,
                        FileSystemUtils.UTF8_CHARSET);
                String[] lines = menuString.split("\r\n");
                for (int index = 0; index < lines.length; index++) {
                    String[] line = lines[index].split("\t");
                    if (line.length == 2) {
                        int color = HostileListItem.WHITE;
                        try {
                            color = Integer.parseInt(line[1]);
                        } catch (Exception ignored) {
                            try {
                                color = Color.parseColor(line[1]);
                            } catch (Exception ignored2) {
                            }
                        }

                        items.add(index, new MapItemHolder(line[0], color));

                        synchronized (lock) {
                            hostilesListBase.add(null);
                            displayList.add(null);
                        }
                        updateDisplayList();
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "error occurred reading the list of hostiles", e);
            }
        } else
            Log.d(TAG, "File not found: " + FILENAME);

        return items;
    }

    /**
     * Write list info to file
     */

    private void saveContacts() {
        final File outputFile = FileSystemUtils
                .getItem("Databases/" + FILENAME);

        if (IOProviderFactory.exists(outputFile))
            FileSystemUtils.delete(outputFile);

        StringBuilder builder = new StringBuilder();
        synchronized (lock) {
            if (hostilesListBase.isEmpty()) {
                return;
            }
            for (MapItem item : hostilesListBase) {
                if (item != null) {
                    builder.append(item.getUID())
                            .append("\t")
                            .append(item
                                    .getMetaInteger("textColor",
                                            HostileListItem.WHITE))
                            .append("\r\n");
                }
            }
        }

        try (OutputStream os = IOProviderFactory.getOutputStream(outputFile)) {
            try (InputStream is = new ByteArrayInputStream(
                    builder.toString().getBytes())) {
                FileSystemUtils.copy(is, os);
            }
        } catch (IOException e) {
            Log.e(TAG, "error occurred", e);
        }
    }

    void showArrows(boolean state) {
        if (state) {
            upButton.setVisibility(View.VISIBLE);
            downButton.setVisibility(View.VISIBLE);
        } else {
            upButton.setVisibility(View.INVISIBLE);
            downButton.setVisibility(View.INVISIBLE);
        }
    }

    synchronized public void init() {
        if (content == null) {
            LayoutInflater inflater = LayoutInflater.from(MapView.getMapView()
                    .getContext());
            content = (HierarchyManagerView) inflater
                    .inflate(R.layout.hostile_manager_list_view, null);
            final ListView list = content
                    .findViewById(R.id.hostile_manager_list);
            filterCb = content
                    .findViewById(R.id.hostile_manager_filter_cb);
            upButton = content
                    .findViewById(R.id.hostile_manager_move_up_btn);
            downButton = content
                    .findViewById(R.id.hostile_manager_move_down_btn);

            updateDisplayList(); //Update the display list

            list.setEmptyView(content.findViewById(R.id.empty));
            list.setAdapter(adapter);
            final SharedPreferences prefs = PreferenceManager
                    .getDefaultSharedPreferences(getMapView().getContext());

            filterCb.setOnCheckedChangeListener(
                    new CompoundButton.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton buttonView,
                                boolean isChecked) {
                            synchronized (lock) {
                                if (displayList.size() > 0) {
                                    int index = adapter.getSelectedIndex();
                                    if (index != -1
                                            && index < displayList.size()) {
                                        Marker selectedItem = displayList
                                                .get(index);
                                        //updateDisplayList();
                                        int selectedIndex = displayList
                                                .indexOf(selectedItem);
                                        if (selectedIndex != -1)
                                            adapter.updateSelected(
                                                    selectedIndex);
                                    }
                                }
                                boolean flipChecked = !isChecked;
                                prefs.edit()
                                        .putBoolean("HostileListOnlyInViewport",
                                                flipChecked)
                                        .apply();
                                calc.exec();
                            }
                        }
                    });

            final boolean b = prefs.getBoolean(
                    "HostileListOnlyInViewport", false);
            filterCb.setChecked(!b);

            upButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    synchronized (lock) {
                        try {
                            int index = adapter.getSelectedIndex();
                            if (index <= 0)
                                return; //if it's already at the start of the list, do nothing
                            Marker item = hostilesListBase
                                    .remove(hostilesListBase
                                            .indexOf(displayList.get(index)));
                            int newIndex = index - 1; //get previous index
                            index = hostilesListBase.indexOf(displayList
                                    .get(newIndex)); //and find where it's displayed
                            if (index < 0)
                                index = 0; //put it up front
                            hostilesListBase.add(index, item);

                            updateDisplayList();
                            adapter.updateSelected(displayList
                                    .indexOf(hostilesListBase
                                            .get(index)));
                            adapter.notifyDataSetChanged();
                        } catch (Exception ignored) {
                        }
                    }
                }
            });

            downButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    synchronized (lock) {
                        try {
                            int index = adapter.getSelectedIndex();
                            if (index == -1 || index == displayList.size() - 1)
                                return; //if it's already at the end of the list, do nothing
                            Marker item = hostilesListBase
                                    .remove(hostilesListBase
                                            .indexOf(displayList.get(index)));
                            int newIndex = index + 1; //get next item in display list
                            index = hostilesListBase.indexOf(displayList
                                    .get(newIndex)) + 1; //and move on index back
                            if (index > hostilesListBase.size())
                                index = hostilesListBase.size() - 1; //put it at the end
                            hostilesListBase.add(index, item);
                            updateDisplayList();
                            adapter.updateSelected(displayList
                                    .indexOf(hostilesListBase
                                            .get(index)));
                            adapter.notifyDataSetChanged();
                        } catch (Exception ignored) {
                        }
                    }
                }
            });

            showArrows(false);
        }

    }

    @Override
    public void onReceive(final Context context, Intent intent) {
        init();
        setRetain(true);
        if (isClosed()) {

            if (isTablet()) {
                showDropDown(content, DropDownReceiver.FIVE_TWELFTHS_WIDTH,
                        DropDownReceiver.FULL_HEIGHT,
                        DropDownReceiver.FULL_WIDTH,
                        DropDownReceiver.FIVE_TWELFTHS_WIDTH, false, this);
            } else {
                showDropDown(content, DropDownReceiver.HALF_WIDTH,
                        DropDownReceiver.FULL_HEIGHT,
                        DropDownReceiver.FULL_WIDTH,
                        DropDownReceiver.HALF_HEIGHT, false, this);
            }

        }

    }

    @Override
    public void onDropDownSelectionRemoved() {
    }

    @Override
    public void onDropDownVisible(boolean v) {
        if (v)
            calc.exec();

    }

    @Override
    public void onDropDownSizeChanged(double width, double height) {
    }

    @Override
    public void onDropDownClose() {
    }

    /**
     * update the display list.  If Show All is checked,
     * display everything, else only show whats on screen
     */
    private void updateDisplayList() {
        synchronized (lock) {
            displayList.clear();
            for (Marker marker : hostilesListBase) {
                if (marker != null) {
                    if (filterCb != null && !filterCb.isChecked()) {
                        if (getMapView().getBounds()
                                .contains(marker.getPoint()))
                            displayList.add(marker);
                    } else {
                        displayList.add(marker);
                    }
                }
            }
        }

        getMapView().post(new Runnable() {
            @Override
            public void run() {
                if (adapter != null)
                    adapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    public void onMapEvent(MapEvent event) {
        if (event.getType().equals(MapEvent.ITEM_ADDED)) {
            MapItem item = event.getItem();
            synchronized (lock) {
                if (item instanceof Marker && item.getType().startsWith("a-h")
                        &&
                        !hostilesListBase.contains(item)) {
                    addHostile((Marker) item);
                    updateDisplayList();
                }
            }
        }
        if (event.getType().equals(MapEvent.ITEM_GROUP_CHANGED)) {
            MapItem item = event.getItem();
            synchronized (lock) {
                if (item instanceof Marker && item.getType().startsWith("a-h")
                        &&
                        !hostilesListBase.contains(item)) {
                    addHostile((Marker) item);
                    updateDisplayList();
                } else {
                    if (item instanceof Marker
                            && hostilesListBase.remove(item)) {
                        updateDisplayList();
                        adapter.notifyDataSetChanged();
                    }
                }
            }
        }
        if (event.getType().equals(MapEvent.ITEM_REMOVED)) {
            MapItem item = event.getItem();
            synchronized (lock) {
                if (item instanceof Marker && hostilesListBase.remove(item)) {
                    updateDisplayList();
                    adapter.notifyDataSetChanged();
                }
            }
        }
        if (event.getType().equals(MapEvent.ITEM_REFRESH) ||
                event.getType().equals(MapEvent.ITEM_PERSIST)) {
            final MapItem item = event.getItem();
            synchronized (lock) {
                if (item.getType().startsWith("a-h")) {
                    if (item instanceof Marker
                            && !hostilesListBase.contains(item)) {
                        addHostile((Marker) item);
                        updateDisplayList();
                        adapter.notifyDataSetChanged();
                    }
                }

            }
        }
    }

    private final ClearContentRegistry.ClearContentListener dataMgmtReceiver = new ClearContentRegistry.ClearContentListener() {
        @Override
        public void onClearContent(boolean clearmaps) {
            File inputFile = FileSystemUtils.getItem("Databases/" + FILENAME);
            if (!IOProviderFactory.delete(inputFile)) {
                Log.d(TAG, "could not clear: " + FILENAME);
            }
        }
    };
}
