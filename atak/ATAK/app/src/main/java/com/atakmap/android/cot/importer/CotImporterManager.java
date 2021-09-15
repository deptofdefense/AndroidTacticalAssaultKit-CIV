
package com.atakmap.android.cot.importer;

import android.net.Uri;
import android.os.Bundle;

import com.atakmap.android.emergency.EmergencyDetailHandler;
import com.atakmap.android.image.quickpic.QuickPicReceiver;
import com.atakmap.android.importexport.AbstractCotEventImporter;
import com.atakmap.android.importexport.CotEventImporter;
import com.atakmap.android.importfiles.resource.RemoteResource;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.log.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Import manager optimized for CoT imports
 */
public class CotImporterManager extends AbstractCotEventImporter {

    private static final String TAG = "CotImporterManager";

    private static CotImporterManager _instance;

    public static CotImporterManager getInstance() {
        return _instance;
    }

    private final MapView _mapView;

    // Importers which map to a specific set of CoT types
    // These are always called first
    private final Map<String, List<CotEventTypeImporter>> _typeImporters;

    // Importers which do not follow any specific CoT types
    private final List<CotEventImporter> _importers;

    // Fallback importer for unrecognized CoT markers
    // Not registered because we only want to use it when all other importers fail
    private final CotEventImporter _otherImporter;

    // CoT MIME type
    private final Set<String> _mimeTypes;

    public CotImporterManager(MapView mapView) {
        super(mapView.getContext(), "cot");
        _mapView = mapView;
        _typeImporters = new HashMap<>();
        _importers = new ArrayList<>();
        _mimeTypes = Collections.singleton("application/cot+xml");
        _otherImporter = new MarkerImporter(_mapView, "Other",
                (Set<String>) null);
        if (_instance == null)
            _instance = this;

        try {
            registerDefaultImporters();
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize default importers", e);
        }
    }

    /**
     * Register a CoT importer
     * Type importers with a non-type set are automatically mapped
     *
     * @param importer CoT importer
     */
    public synchronized void registerImporter(CotEventImporter importer) {
        if (importer instanceof CotEventTypeImporter) {
            CotEventTypeImporter typeImporter = (CotEventTypeImporter) importer;
            Set<String> types = typeImporter.getSupportedCotTypes();
            if (!types.isEmpty() && !typeImporter.isPrefixOnly()) {
                for (String type : types) {
                    List<CotEventTypeImporter> list = _typeImporters.get(type);
                    if (list == null)
                        _typeImporters.put(type, list = new ArrayList<>());
                    list.add(typeImporter);
                }
                return;
            }
        }
        _importers.add(importer);
    }

    /**
     * Unregister a CoT importer
     *
     * @param importer CoT importer
     */
    public synchronized void unregisterImporter(CotEventImporter importer) {
        if (importer instanceof CotEventTypeImporter) {
            CotEventTypeImporter typeImporter = (CotEventTypeImporter) importer;
            Set<String> types = typeImporter.getSupportedCotTypes();
            for (String type : types) {
                List<CotEventTypeImporter> list = _typeImporters.get(type);
                if (list != null && list.remove(importer) && list.isEmpty())
                    _typeImporters.remove(type);
            }
        }
        _importers.remove(importer);
    }

    @Override
    public Set<String> getSupportedMIMETypes() {
        return _mimeTypes;
    }

    @Override
    public ImportResult importData(Uri uri, String mime, Bundle bundle)
            throws IOException {
        return ImportResult.FAILURE;
    }

    @Override
    public boolean deleteData(Uri uri, String mime) {
        return false;
    }

    @Override
    public ImportResult importNonCotData(InputStream source, String mime) {
        return ImportResult.FAILURE;
    }

    /**
     * Process CoT events using the registered importers
     *
     * @param event CoT event
     * @param extras Import extras
     * @return Import result
     */
    @Override
    public ImportResult importData(CotEvent event, Bundle extras) {
        if (event == null)
            return ImportResult.FAILURE;

        if (extras == null)
            extras = new Bundle();

        // Find matching importers and queue in priority
        List<CotEventImporter> importers = new ArrayList<>();
        String type = event.getType();
        synchronized (this) {
            List<CotEventTypeImporter> typeImporters = _typeImporters.get(type);
            if (typeImporters != null)
                importers.addAll(typeImporters);
            else
                importers.addAll(_importers);
        }

        // Process with matching importers
        for (CotEventImporter importer : importers) {
            // Process import
            ImportResult r = importer.importData(event, extras);
            if (r != ImportResult.IGNORE) {
                if (r == ImportResult.FAILURE)
                    Log.e(TAG, "Importer " + importer
                            + " failed on event with type: "
                            + event.getType());
                return r;
            }
        }

        // Fallback to the unknown/other marker importer
        Log.w(TAG, "Failed to find importer for event: "
                + event.getType() + "/" + event.getUID());
        return _otherImporter.importData(event, extras);
    }

    /**
     * Register all default marker importers in core
     * Ideally importers should be registered in their associated
     * map components...
     */
    private void registerDefaultImporters() {

        MapGroup cotGroup = _mapView.getRootGroup().findMapGroup(
                "Cursor on Target");

        // Legacy markers
        registerImporter(new LegacyMarkerImporter(_mapView,
                RemoteResource.COT_TYPE));

        // Emergency markers
        registerImporter(new MarkerImporter(_mapView, "Emergency",
                EmergencyDetailHandler.EMERGENCY_TYPE_PREFIX, true));

        // CASEVAC markers
        registerImporter(new CASEVACMarkerImporter(_mapView));

        // User icon markers
        registerImporter(new UserIconMarkerImporter(_mapView));

        // 2525C markers
        registerImporter(new MarkerImporter(_mapView,
                cotGroup.findMapGroup("Hostile"), "a-h", true));
        registerImporter(new FriendlyMarkerImporter(_mapView,
                cotGroup.findMapGroup("Friendly")));
        registerImporter(new MarkerImporter(_mapView,
                cotGroup.findMapGroup("Neutral"), "a-n", true));
        registerImporter(new MarkerImporter(_mapView,
                cotGroup.findMapGroup("Unknown"), "a-u", true));

        // Airspace markers
        registerImporter(new MarkerImporter(_mapView, "Airspace",
                new HashSet<>(Arrays.asList("b-m-p-c-cp", "b-m-p-c-ip"))));

        // Waypoints
        registerImporter(new MarkerImporter(_mapView,
                cotGroup.findMapGroup("Waypoint"),
                new HashSet<>(Arrays.asList("b-m-p-w", "b-m-p-c", "b-m-p-i")),
                true));

        // DIPs
        registerImporter(new DIPMarkerImporter(_mapView));

        // Mission markers
        registerImporter(new MarkerImporter(_mapView, "Mission",
                new HashSet<>(Arrays.asList("b-m-p-s-p-loc", "b-m-p-s-p-op"))));

        // SPIs
        registerImporter(new SPIMarkerImporter(_mapView));

        // Quick pic markers
        registerImporter(new MarkerImporter(_mapView, "Quick Pic",
                QuickPicReceiver.QUICK_PIC_IMAGE_TYPE, true));

        // Spot map markers and labels
        registerImporter(new SpotMapImporter(_mapView));
    }
}
