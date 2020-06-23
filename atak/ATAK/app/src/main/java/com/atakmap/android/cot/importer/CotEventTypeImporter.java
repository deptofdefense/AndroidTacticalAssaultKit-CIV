
package com.atakmap.android.cot.importer;

import android.os.Bundle;

import com.atakmap.android.importexport.AbstractCotEventImporter;
import com.atakmap.android.maps.MapView;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotEvent;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * CoT event importer that filters by CoT type
 */
public abstract class CotEventTypeImporter extends AbstractCotEventImporter {

    // Set of types this importer supports (unmodifiable)
    private final Set<String> _types;

    // Whether to check if the type only starts with any of the set types
    private boolean _prefixOnly;

    protected CotEventTypeImporter(MapView mapView, Set<String> types) {
        super(mapView.getContext(), "cot");
        _types = types != null ? Collections.unmodifiableSet(types)
                : new HashSet<String>();
    }

    protected CotEventTypeImporter(MapView mapView, String... types) {
        this(mapView, new HashSet<>(Arrays.asList(types)));
    }

    // No type filtering
    protected CotEventTypeImporter(MapView mapView) {
        this(mapView, new HashSet<String>());
    }

    /**
     * Get the set of map item types this importer supports
     *
     * @return Set of detail names
     */
    public final Set<String> getSupportedCotTypes() {
        return _types;
    }

    /**
     * Set whether or not to only check for type prefixes
     *
     * @param prefixOnly True to only check type prefix
     */
    protected final void setPrefixOnly(boolean prefixOnly) {
        _prefixOnly = prefixOnly;
    }

    public final boolean isPrefixOnly() {
        return _prefixOnly;
    }

    @Override
    public ImportResult importData(CotEvent event, Bundle extras) {
        if (!_types.isEmpty()) {
            String type = event.getType();
            if (_prefixOnly) {
                for (String t : _types) {
                    if (type.startsWith(t))
                        return ImportResult.SUCCESS;
                }
            } else if (_types.contains(type))
                return ImportResult.SUCCESS;
            return ImportResult.IGNORE;
        }
        return ImportResult.SUCCESS;
    }

    @Override
    protected ImportResult importNonCotData(InputStream source, String mime) {
        return ImportResult.IGNORE;
    }
}
