
package com.atakmap.android.importfiles.sort;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Pair;

import com.atakmap.android.bluetooth.BluetoothDevicesConfig;
import com.atakmap.android.favorites.FavoriteListAdapter;
import com.atakmap.android.importexport.ImportExportMapComponent;
import com.atakmap.android.importexport.ImportReceiver;
import com.atakmap.android.importfiles.ui.ImportManagerView;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.layers.LayersManagerBroadcastReceiver;
import com.atakmap.android.layers.LayersMapComponent;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.android.wfs.WFSImporter;
import com.atakmap.app.R;
import com.atakmap.app.preferences.GeocoderPreferenceFragment;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.layer.feature.wfs.XMLWFSSchemaHandler;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

/**
 * Sorts ATAK TXT & XML Files.   Allows for augmentation by adding calls to addSignature.
 * 
 * 
 */
final public class ImportTXTSort extends ImportInternalSDResolver {

    private static final String TAG = "ImportTXTSort";
    private static final String CONTENT_TYPE = "TXT or XML File";
    private static final String MIME_TYPE = "application/xml";

    private final Context _context;

    public static class TxtType {

        public interface AfterAction {
            void doAction(File dst);
        }

        public String signature;
        public String folder;
        public AfterAction action;

        public TxtType(final String signature, final String folder,
                final AfterAction action) {
            this.signature = signature;
            this.folder = folder;
            this.action = action;
        }

        public String toString() {
            return signature + ": " + folder;
        }
    }

    /**
     * Enumeration of ATAK TXT & XML files including matching string and storage location
     */
    private static List<TxtType> types = new ArrayList<>();

    /**
     * Adds a signature to the TXT and XML importer that comprises a signature, directory and an 
     * action to be called if import succeeds.
     * @param signature the signature to look for within the file to match.   
     * @param directory the directory to copy the file to if the signature was found.
     * @param action the action to execute after the file has been copied.
     */
    public static void addSignature(String signature, String directory,
            TxtType.AfterAction action) {
        types.add(new TxtType(signature, directory, action));
    }

    public ImportTXTSort(final Context context, final String ext,
            final boolean validateExt,
            boolean copyFile) {
        super(ext, "", validateExt, copyFile, CONTENT_TYPE,
                context.getDrawable(R.drawable.ic_details));
        this._context = context;

        addSignature("<remoteResources", ImportManagerView.XML_FOLDER, null);
        addSignature("<NominatimProperties",
                GeocoderPreferenceFragment.ADDRESS_DIR, geocoderaction);
        addSignature("<devices", BluetoothDevicesConfig.DIRNAME, null);
        addSignature(XMLWFSSchemaHandler.WFS_CONFIG_ROOT, "wfs", wfsaction); // ATAK/wfs
        addSignature(FavoriteListAdapter.FAVS, FavoriteListAdapter.DIRNAME,
                favaction);
    }

    @Override
    public boolean match(File file) {
        if (!super.match(file))
            return false;

        // it is a .xml or .txt, now lets see if content inspection passes
        TxtType t = null;
        try (InputStream fis = IOProviderFactory.getInputStream(file)) {
            t = getType(fis);
        } catch (IOException e) {
            Log.e(TAG, "Failed to match TXT file: " + file.getAbsolutePath(),
                    e);
        }

        return t != null;
    }

    public static TxtType getType(InputStream stream) {
        try {
            // read first few hundred bytes and search for known strings
            char[] buffer = new char[1024];
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    stream));
            int numRead = reader.read(buffer);
            reader.close();

            if (numRead < 1) {
                Log.d(TAG, "Failed to read txt stream");
                return null;
            }

            String content = String.valueOf(buffer, 0, numRead);
            for (TxtType t : types) {
                if (content.contains(t.signature)) {
                    Log.d(TAG, "Match ATAK TXT content: " + t);
                    return t;
                }
            }

            Log.d(TAG, "Failed to match ATAK TXT content");
            return null;
        } catch (Exception e) {
            Log.d(TAG, "Failed to match txt", e);
            return null;
        }
    }

    /**
     * Move to new location on same SD card Defer to TxtType for the relative path
     */
    @Override
    public File getDestinationPath(File file) {

        TxtType t = null;
        try (InputStream is = IOProviderFactory.getInputStream(file)) {
            t = getType(is);
        } catch (IOException e) {
            Log.e(TAG, "Failed to match TXT file: " + file.getAbsolutePath(),
                    e);
        }

        if (t == null) {
            Log.e(TAG, "Failed to match TXT file: " + file.getAbsolutePath());
            return null;
        }

        File folder = FileSystemUtils.getItem(t.folder != null ? t.folder : "");

        // ATAK directory watchers expect files to have certain extensions so force it here
        String fileName = file.getName();
        if (!FileSystemUtils.isEmpty(getExt())
                && !fileName.endsWith(getExt())) {
            Log.d(TAG, "Added extension to destination path: " + fileName);
            fileName += getExt();
        }

        return new File(folder, fileName);
    }

    TxtType.AfterAction wmsaction = new TxtType.AfterAction() {
        @Override
        public void doAction(File dst) {
            Log.d(TAG, "notify that a new wms file was imported: " + dst);
            Intent loadwmsintent = new Intent(
                    ImportExportMapComponent.ACTION_IMPORT_DATA);
            loadwmsintent.putExtra(ImportReceiver.EXTRA_CONTENT,
                    LayersMapComponent.IMPORTER_CONTENT_TYPE);
            loadwmsintent.putExtra(ImportReceiver.EXTRA_MIME_TYPE,
                    LayersMapComponent.IMPORTER_DEFAULT_MIME_TYPE);
            loadwmsintent.putExtra(ImportReceiver.EXTRA_URI,
                    Uri.fromFile(dst).toString());
            AtakBroadcast.getInstance().sendBroadcast(loadwmsintent);
        }
    };

    private final TxtType.AfterAction geocoderaction = new TxtType.AfterAction() {
        @Override
        public void doAction(File dst) {
            Log.d(TAG, "notify that a new geocoder file was imported: " + dst);
            GeocoderPreferenceFragment.load(dst);
        }
    };

    private TxtType.AfterAction wfsaction = new TxtType.AfterAction() {
        @Override
        public void doAction(File dst) {
            Log.d(TAG, "notify that a new wfs file was imported: " + dst);
            Intent loadwmsintent = new Intent(
                    ImportExportMapComponent.ACTION_IMPORT_DATA);
            loadwmsintent.putExtra(ImportReceiver.EXTRA_CONTENT,
                    WFSImporter.CONTENT);
            loadwmsintent.putExtra(ImportReceiver.EXTRA_MIME_TYPE,
                    WFSImporter.MIME_XML);
            loadwmsintent.putExtra(ImportReceiver.EXTRA_URI,
                    Uri.fromFile(dst).toString());

            AtakBroadcast.getInstance().sendBroadcast(loadwmsintent);

        }
    };

    private TxtType.AfterAction favaction = new TxtType.AfterAction() {
        @Override
        public void doAction(File dst) {
            Log.d(TAG, "notify that a fav file was imported: " + dst);

            //file has been moved, import each fav in the file
            List<FavoriteListAdapter.Favorite> favs = FavoriteListAdapter
                    .loadList(dst.getAbsolutePath());
            if (FileSystemUtils.isEmpty(favs)) {
                Log.w(TAG, "favaction none loaded");
                return;
            }

            Intent intent = new Intent(
                    LayersManagerBroadcastReceiver.ACTION_ADD_FAV);
            intent.putExtra("favorites", favs
                    .toArray(new FavoriteListAdapter.Favorite[0]));
            AtakBroadcast.getInstance().sendBroadcast(intent);

            //intent to view one of the favs
            FavoriteListAdapter.Favorite fav = favs.get(0);
            if (fav != null && !FileSystemUtils.isEmpty(fav.layer)
                    && !FileSystemUtils.isEmpty(fav.selection)) {
                intent = new Intent(
                        LayersManagerBroadcastReceiver.ACTION_VIEW_FAV);
                intent.putExtra("favorite", fav);
                NotificationUtil.getInstance().postNotification(
                        R.drawable.spi1_icon,
                        _context.getString(R.string.fav_notif_title, fav.title),
                        _context.getString(R.string.fav_notif_msg, fav.title),
                        _context.getString(R.string.fav_notif_msg, fav.title),
                        intent);
            }
        }
    };

    @Override
    protected void onFileSorted(File src, File dst, Set<SortFlags> flags) {
        super.onFileSorted(src, dst, flags);

        //special case import actions
        TxtType t;
        try (InputStream fis = IOProviderFactory.getInputStream(dst)) {
            t = getType(fis);
            if (t != null && t.action != null)
                t.action.doAction(dst);
        } catch (IOException e) {
            Log.w(TAG,
                    "onFileSorted Failed to match TXT file: "
                            + dst.getAbsolutePath(),
                    e);
        }
    }

    @Override
    public Pair<String, String> getContentMIME() {
        return new Pair<>(CONTENT_TYPE, MIME_TYPE);
    }
}
