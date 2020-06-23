
package com.atakmap.android.importexport;

import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.ekito.simpleKML.model.Placemark;

public abstract class KmlMapItemImportFactory {
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
    public MapItem instanceFromKml(Placemark placemark, MapGroup mapGroup)
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
