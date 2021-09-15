
package com.atakmap.android.rubbersheet.data;

import android.content.Context;

import com.atakmap.android.data.ClearContentRegistry;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.rubbersheet.maps.AbstractSheet;
import com.atakmap.android.rubbersheet.maps.RubberImage;
import com.atakmap.android.rubbersheet.maps.RubberModel;
import com.atakmap.android.rubbersheet.maps.RubberSheetMapGroup;
import com.atakmap.coremap.concurrent.NamedThreadFactory;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Used to persist and load rubber sheets/models
 */
public class RubberSheetManager implements MapGroup.OnItemListChangedListener,
        ClearContentRegistry.ClearContentListener {

    private static final String TAG = "RubberSheetManager";

    public static final File DIR = new File(FileSystemUtils.getItem("tools"),
            "rubbersheet");
    private static final File SAVE_FILE = new File(DIR, ".saved_sheets");

    private final MapView _mapView;
    private final Context _context;
    private final RubberSheetMapGroup _group;
    private ExecutorService _workers;

    private boolean _saveLoadBusy;

    public RubberSheetManager(MapView mapView, RubberSheetMapGroup group) {
        _mapView = mapView;
        _context = mapView.getContext();
        _group = group;

        init();
    }

    public void init() {

        _workers = Executors.newFixedThreadPool(5,
                new NamedThreadFactory(TAG + "-Pool"));

        // Create the working plugin directory
        if (!IOProviderFactory.exists(DIR) && !IOProviderFactory.mkdirs(DIR))
            Log.e(TAG, "Failed to make tools directory: " + DIR);

        ClearContentRegistry.getInstance().registerListener(this);

        _group.addOnItemListChangedListener(this);

        loadSheets();

    }

    public void shutdown() {
        _group.removeOnItemListChangedListener(this);
        ClearContentRegistry.getInstance().unregisterListener(this);
        if (IOProviderFactory.exists(SAVE_FILE))
            saveSheets();
        _group.clearItems();
        _group.clearGroups();
        _workers.shutdown();
    }

    @Override
    public void onItemAdded(MapItem item, MapGroup group) {
        saveSheets();
    }

    @Override
    public void onItemRemoved(MapItem item, MapGroup group) {
        File dir = new File(DIR, item.getUID());
        if (IOProviderFactory.exists(dir))
            FileSystemUtils.deleteDirectory(dir, false);
        saveSheets();
    }

    private void saveSheets() {
        if (_saveLoadBusy || _group.getParentGroup() == null)
            return;
        _saveLoadBusy = true;
        Collection<MapItem> items = _group.getItems();
        List<AbstractSheetData> data = new ArrayList<>();
        for (MapItem mi : items) {
            if (mi instanceof RubberImage) {
                RubberImageData rsd = new RubberImageData((RubberImage) mi);
                if (rsd.isValid())
                    data.add(rsd);
            } else if (mi instanceof RubberModel) {
                RubberModelData rmd = new RubberModelData((RubberModel) mi);
                if (rmd.isValid())
                    data.add(rmd);
            }
        }
        try (OutputStream fos = IOProviderFactory.getOutputStream(SAVE_FILE)) {
            JSONArray arr = new JSONArray();
            for (AbstractSheetData rsd : data) {
                JSONObject o = rsd.toJSON();
                if (o == null)
                    continue;
                arr.put(o);
            }
            FileSystemUtils.write(fos, arr.toString());
        } catch (Exception e) {
            Log.e(TAG, "Failed to write rubber sheet data to: "
                    + SAVE_FILE, e);
        }
        _saveLoadBusy = false;
    }

    private void loadSheets() {
        if (_saveLoadBusy || !IOProviderFactory.exists(SAVE_FILE))
            return;
        _saveLoadBusy = true;
        String json = null;
        try {
            json = new String(FileSystemUtils.read(SAVE_FILE),
                    FileSystemUtils.UTF8_CHARSET);
        } catch (IOException e) {
            Log.e(TAG, "Failed to read saved data: " + SAVE_FILE, e);
        }
        if (!FileSystemUtils.isEmpty(json)) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    AbstractSheetData data = AbstractSheetData.create(
                            arr.getJSONObject(i));
                    load(data);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to read file: " + SAVE_FILE, e);
            }
        }
        _saveLoadBusy = false;
    }

    private void load(AbstractSheetData data) {
        if (data == null || !data.isValid())
            return;

        // Create the placeholder rectangle
        AbstractSheet s = null;
        if (data instanceof RubberImageData) {
            // Image sheets load during render so no need to use worker here
            s = RubberImage.create(_mapView, (RubberImageData) data);
        } else if (data instanceof RubberModelData) {
            // Load the model
            s = RubberModel.create((RubberModelData) data, null, null);
        }
        if (s == null)
            return;

        _group.add(s);
        if (!s.isLoaded()) {
            final AbstractSheet fs = s;
            _workers.execute(new Runnable() {
                @Override
                public void run() {
                    fs.load();
                }
            });
        }
    }

    @Override
    public void onClearContent(boolean clearmaps) {
        FileSystemUtils.deleteDirectory(DIR, false);
    }

}
