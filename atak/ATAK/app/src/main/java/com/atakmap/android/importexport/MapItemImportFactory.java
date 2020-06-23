
package com.atakmap.android.importexport;

import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.coremap.cot.event.CotEvent;

/**
 * Abstract base class for factory classes for importing MapItems from various data sources.
 */
public abstract class MapItemImportFactory {

    /**
     * Supported possible import data types.
     */
    private enum ImportType {
        COT("CoT", ".cot", ""),
        KML("KML", ".kml", "application/vnd.google-earth.kml+xml"),
        XML("XML", ".xml", "text/xml");

        public final String type;
        public final String ext;
        public final String mimeType;

        ImportType(String type, String ext, String mimeType) {
            this.type = type;
            this.ext = ext;
            this.mimeType = mimeType;
        }

        public static ImportType fromMIMEType(String mimeType) {
            for (ImportType it : ImportType.values()) {
                if (it.mimeType.equalsIgnoreCase(mimeType)) {
                    return it;
                }
            }
            return null;
        }

        public static ImportType fromFileExtension(String extension) {
            for (ImportType it : ImportType.values()) {
                if (it.ext.equalsIgnoreCase(extension)) {
                    return it;
                }
            }
            return null;
        }
    }

    /**
     * Create MapItem from KML Placemark element.
     * 
     * @param placemark KML Placemark element to parse
     * @param mapGroup MapGroup that the MapItem will belong to after creation. Note that the method
     *            will not necessarily add the MapItem to this group. This should be done by the
     *            calling method.
     * @return new MapItem
     * @throws FormatNotSupportedException
     */
    /*
     * public MapItem instanceFromKml(Placemark placemark, MapGroup mapGroup) throws
     * FormatNotSupportedException { throw new FormatNotSupportedException(); }
     */

    /**
     * Create MapItem from CoT event.
     * 
     * @param cotEvent event to parse
     * @param existingMapItem Existing map item representing this CoT event, if any. Implementations
     *            can optionally update this map item instead of creating an entirely new one. (This
     *            item is found by UID and provided to save doing an additional find)
     * @param mapGroup MapGroup that the MapItem will belong to after creation. Note that the method
     *            will not necessarily add the MapItem to this group. This should be done by the
     *            calling method.
     * @return new MapItem
     * @throws FormatNotSupportedException
     */
    public MapItem instanceFromCot(CotEvent cotEvent, MapItem existingMapItem,
            MapGroup mapGroup)
            throws FormatNotSupportedException {
        throw new FormatNotSupportedException();
    }

    /**
     * Returns the factory name. This is the identifying string that is written to a KML file to
     * indicate that this factory should parse the KML element.
     * 
     * @return factory name
     */
    public abstract String getFactoryName();

}
