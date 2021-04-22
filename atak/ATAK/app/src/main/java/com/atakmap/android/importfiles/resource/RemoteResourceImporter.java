
package com.atakmap.android.importfiles.resource;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Pair;
import android.widget.Toast;

import com.atakmap.android.hierarchy.HierarchyListReceiver;
import com.atakmap.android.importexport.Importer;
import com.atakmap.android.importexport.ImporterManager;
import com.atakmap.android.importfiles.sort.ImportInPlaceResolver;
import com.atakmap.android.importfiles.ui.AddEditResource;
import com.atakmap.android.importfiles.ui.ImportManagerMapOverlay;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.spatial.file.KmlFileSpatialDb;
import com.atakmap.spatial.kml.FeatureHandler;
import com.atakmap.spatial.kml.KMLUtil;
import com.ekito.simpleKML.model.Link;
import com.ekito.simpleKML.model.NetworkLink;

import org.simpleframework.xml.core.Persister;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class RemoteResourceImporter extends ImportInPlaceResolver {

    private static final String TAG = "KMLNetworkLinkImporter";
    private static final File KML_FOLDER = FileSystemUtils
            .getItem("tools/import/cache");

    public static final String CONTENT_TYPE = "Remote Resource";

    private static final String KML_MATCH = "<kml";
    private static final String LINK_MATCH = "<NetworkLink";
    private static final String RR_MATCH = "<RemoteResource>";

    private final MapView _mapView;
    private final Context _context;
    private final Persister _xmlPersister;
    private Importer _vectorImporter;

    public RemoteResourceImporter(MapView mapView) {
        super(".kml", FileSystemUtils.OVERLAYS_DIRECTORY, true,
                false, true,
                mapView.getContext()
                        .getString(R.string.importmgr_remote_resource),
                mapView.getContext().getDrawable(R.drawable.ic_kml_network));
        _mapView = mapView;
        _context = mapView.getContext();
        _xmlPersister = new Persister();
    }

    @Override
    public boolean match(File file) {
        boolean isKML = FileSystemUtils.checkExtension(file, "kml");
        boolean isXML = FileSystemUtils.checkExtension(file, "xml");
        if (_bValidateExt && !(isKML || isXML))
            return false;

        // it is a .kml, now lets see if it contains reasonable xml
        try (FileInputStream fis = IOProviderFactory.getInputStream(file)) {
            return isKML && isNetworkKML(fis) || isXML && isNetworkXML(fis);
        } catch (IOException e) {
            Log.e(TAG, "Error checking if KML: " + file.getAbsolutePath(), e);
        }

        return false;
    }

    private static boolean isNetworkKML(InputStream stream) {
        boolean isKML = false;
        boolean isLink = false;
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(stream))) {
            char[] buffer = new char[FileSystemUtils.BUF_SIZE];
            int numRead;
            while ((numRead = r.read(buffer)) > 0) {
                String content = String.valueOf(buffer, 0, numRead);
                if (content.contains(KML_MATCH))
                    isKML = true;
                if (content.contains(LINK_MATCH))
                    isLink = true;
                if (isKML && isLink)
                    return true;
            }
        } catch (Exception e) {
            Log.d(TAG, "Failed to match KML network link", e);
        }
        return false;
    }

    private static boolean isNetworkXML(InputStream stream) {
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(stream))) {
            char[] buffer = new char[FileSystemUtils.BUF_SIZE];
            int numRead = r.read(buffer);
            if (numRead > 0) {
                String content = String.valueOf(buffer, 0, numRead);
                return content.contains(RR_MATCH);
            }
        } catch (Exception e) {
            Log.d(TAG, "Failed to match KML network link", e);
        }
        return false;
    }

    @Override
    public boolean beginImport(File file, Set<SortFlags> flags) {
        return beginImport(file);
    }

    @Override
    public boolean beginImport(File file) {
        try (FileInputStream fis = IOProviderFactory.getInputStream(file)) {
            final List<RemoteResource> resources = new ArrayList<>();
            if (FileSystemUtils.checkExtension(file, "kml")) {
                KMLUtil.parseNetworkLinks(fis,
                        new FeatureHandler<NetworkLink>() {
                            @Override
                            public boolean process(NetworkLink nl) {
                                RemoteResource res = createRemoteResource(nl);
                                if (res != null)
                                    resources.add(res);
                                return false;
                            }
                        });
            } else {
                RemoteResources rrs = RemoteResources.load(file, _xmlPersister);
                if (rrs != null)
                    resources.addAll(rrs.getResources());
            }

            // If we're only adding 1 link then prompt the user for any changes
            if (resources.size() == 1) {
                _mapView.post(new Runnable() {
                    @Override
                    public void run() {
                        new AddEditResource(_mapView)
                                .setForceUpdate(true)
                                .edit(resources.get(0));
                    }
                });
            } else {
                _mapView.post(new Runnable() {
                    @Override
                    public void run() {
                        ImportManagerMapOverlay overlay = ImportManagerMapOverlay
                                .getOverlay(_mapView);
                        String toast = _context.getString(
                                R.string.kml_links_added_failed_msg);
                        if (overlay != null
                                && overlay.addResources(resources, true)) {
                            toast = _context.getString(
                                    R.string.kml_links_added_msg,
                                    resources.size());
                            ArrayList<String> overlayPaths = new ArrayList<>();
                            overlayPaths.add(overlay.getIdentifier());
                            AtakBroadcast.getInstance()
                                    .sendBroadcast(new Intent(
                                            HierarchyListReceiver.MANAGE_HIERARCHY)
                                                    .putStringArrayListExtra(
                                                            "list_item_paths",
                                                            overlayPaths)
                                                    .putExtra("isRootList",
                                                            true));
                        }
                        Toast.makeText(_context, toast, Toast.LENGTH_LONG)
                                .show();
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse KML", e);
        } finally {
            // Import the rest of the KML normally
            if (FileSystemUtils.checkExtension(file, "kml")) {
                try {
                    if (_vectorImporter == null)
                        _vectorImporter = ImporterManager.findImporter(
                                KmlFileSpatialDb.KML_CONTENT_TYPE,
                                KmlFileSpatialDb.KMZ_FILE_MIME_TYPE);
                    if (_vectorImporter != null)
                        _vectorImporter.importData(Uri.fromFile(file),
                                KmlFileSpatialDb.KML_FILE_MIME_TYPE, null);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to import KML", e);
                }
            }
        }
        return false;
    }

    @Override
    public Pair<String, String> getContentMIME() {
        return new Pair<>(CONTENT_TYPE, KmlFileSpatialDb.KML_FILE_MIME_TYPE);
    }

    private RemoteResource createRemoteResource(NetworkLink nl) {
        Link link = nl.getLink();
        String url = link.getHref();
        if (FileSystemUtils.isEmpty(url))
            return null;

        // Get file name based on ID or URL
        String fileName = FileSystemUtils.sanitizeFilename(
                url.substring(url.lastIndexOf('/') + 1));
        int quesIdx = fileName.indexOf('?');
        if (quesIdx > 0)
            fileName = fileName.substring(0, quesIdx);

        // Get display name
        String name = nl.getName();
        if (FileSystemUtils.isEmpty(name))
            name = fileName;

        // Get extension
        String ext = null;
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > -1)
            ext = fileName.substring(lastDot + 1);

        // Use ID for filename if available
        String id = nl.getId();
        if (!FileSystemUtils.isEmpty(id))
            fileName = id + (ext != null ? ("." + ext) : "");

        RemoteResource res = new RemoteResource();
        res.setName(name);
        res.setUrl(url);
        res.setType(ext != null ? ext : "OTHER");
        res.setLocalPath(KML_FOLDER + "/" + fileName);
        res.setRefreshSeconds(link.getRefreshInterval());
        res.setDeleteOnExit(false);
        return res;
    }
}
