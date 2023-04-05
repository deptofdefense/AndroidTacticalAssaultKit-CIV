
package com.atakmap.android.elev;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import android.content.Context;
import android.content.Intent;

import com.atakmap.android.elev.dt2.Dt2FileWatcher;
import com.atakmap.android.elev.dt2.Dt2OutlineMapOverlay;
import com.atakmap.android.elev.dt2.Dt2ElevationData;
import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.PointMapItem.OnPointChangedListener;
import com.atakmap.app.R;
import com.atakmap.app.preferences.ToolsPreferenceFragment;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.elevation.ElevationManager;
import com.atakmap.map.elevation.ElevationSource;
import com.atakmap.map.elevation.ElevationSourceManager;
import com.atakmap.map.formats.dted.DtedElevationSource;
import com.atakmap.map.formats.srtm.SrtmElevationSource;
import com.atakmap.util.Collections2;

public class ElevationMapComponent extends AbstractMapComponent {

    final public static String TAG = "ElevationMapComponent";

    private MapView _mapView;
    private final Set<ElevationSource> dt2dbs = Collections2
            .newIdentityHashSet();
    private Dt2OutlineMapOverlay _outlineOverlay;
    private ElevationDownloader _downloader;
    private ElevationContentChangedDispatcher _dt2Content;

    @Override
    public void onCreate(Context context, Intent intent, MapView view) {
        _mapView = view;
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_ADDED,
                _itemAddedListener);

        ToolsPreferenceFragment.register(
                new ToolsPreferenceFragment.ToolPreference(
                        _mapView.getContext().getString(
                                R.string.elevationPreferences),
                        "Adjust Elevation Display Preferences",
                        "dtedPreference",
                        _mapView.getContext().getResources().getDrawable(
                                R.drawable.ic_overlay_dted),
                        new ElevationOverlaysPreferenceFragment()));

        ElevationManager.registerDataSpi(Dt2ElevationData.SPI);

        _dt2Content = new ElevationContentChangedDispatcher("DTED");
        ElevationSourceManager.attach(_dt2Content);

        // Initialize DTED watcher
        List<File> rootDirs = Arrays.asList(FileSystemUtils.getItems("DTED"));
        final Dt2FileWatcher dtedWatcher = new Dt2FileWatcher(_mapView,
                rootDirs);
        String[] dt2paths = findDtedPaths();
        for (String dt2path : dt2paths) {
            dt2dbs.add(DtedElevationSource.create(dt2path));
        }

        for (ElevationSource db : dt2dbs)
            ElevationSourceManager.attach(db);

        String[] srtmPaths = findDataPaths("SRTM");
        for (String srtmPath : srtmPaths)
            SrtmElevationSource.mountDirectory(new File(srtmPath));

        // DTED tile outlines
        _outlineOverlay = new Dt2OutlineMapOverlay(view, dtedWatcher);

        // Automatic DTED downloader
        _downloader = new ElevationDownloader(view);

        dtedWatcher.addListener(new Dt2FileWatcher.Listener() {
            @Override
            public void onDtedFilesUpdated() {
                _dt2Content.contentChanged();
            }
        });
        dtedWatcher.start();
    }

    public static String[] findDtedPaths() {
        return findDataPaths("DTED");
    }

    private static String[] findDataPaths(String dataDirName) {
        String[] mountPoints = FileSystemUtils.findMountPoints();
        String[] dtedPaths = new String[mountPoints.length];

        int i = 0;
        for (String mountPoint : mountPoints) {
            dtedPaths[i] = mountPoint + File.separator + dataDirName
                    + File.separator;
            i++;
        }
        return dtedPaths;
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        for (ElevationSource db : dt2dbs) {
            ElevationSourceManager.detach(db);
            if (db instanceof gov.tak.api.util.Disposable)
                ((gov.tak.api.util.Disposable) db).dispose();
        }
        dt2dbs.clear();

        ElevationSourceManager.detach(_dt2Content);
        ElevationManager.unregisterDataSpi(Dt2ElevationData.SPI);
        _outlineOverlay.dispose();
        _downloader.dispose();
    }

    private final OnPointChangedListener _onPointChangedListener = new OnPointChangedListener() {

        @Override
        public void onPointChanged(final PointMapItem item) {
            final GeoPoint gp = item.getPoint();
            if (gp != null && item.getGroup() != null
                    && !gp.isAltitudeValid()) {
                GeoPointMetaData gpm = ElevationManager
                        .getElevationMetadata(gp);

                if (gpm.get().isAltitudeValid()) {
                    GeoPoint newgp = new GeoPoint(gp.getLatitude(),
                            gp.getLongitude(), gp.getAltitude(),
                            gp.getAltitudeReference(), gp.getCE(),
                            gp.getLE());
                    item.setPoint(newgp);
                    item.copyMetaData(gpm.getMetaData());
                    // Log.d(TAG, "new point encountered, elevation looked up: " + a);

                    // Notify other components of the need to persist
                    item.persist(_mapView.getMapEventDispatcher(), null,
                            this.getClass());
                }
            }
        }
    };

    private final MapEventDispatcher.MapEventDispatchListener _itemAddedListener = new MapEventDispatcher.MapEventDispatchListener() {
        @Override
        public void onMapEvent(MapEvent event) {
            if (event.getItem() instanceof PointMapItem) {
                final PointMapItem item = (PointMapItem) event.getItem();
                String entry = item.getMetaString("entry", null);
                if (entry != null && entry.equals("user")) {
                    // Log.d(TAG,
                    // "new point encountered, looking up elevation if the elevation is not valid");
                    // process the lookup.
                    _onPointChangedListener.onPointChanged(item);
                    // register a listener to process future lookups
                    item.addOnPointChangedListener(_onPointChangedListener);
                }
            }
        }
    };

}
