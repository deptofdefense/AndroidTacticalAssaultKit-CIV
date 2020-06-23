
package com.atakmap.android.importexport.handlers;

import android.os.Handler;

import com.atakmap.android.importexport.FormatNotSupportedException;
import com.atakmap.android.importexport.KmlMapItemImportFactory;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.log.Log;
import com.atakmap.spatial.kml.FeatureHandler;
import com.atakmap.spatial.kml.KMLConversion;
import com.atakmap.spatial.kml.KMLUtil;
import com.ekito.simpleKML.Serializer;
import com.ekito.simpleKML.model.Data;
import com.ekito.simpleKML.model.Document;
import com.ekito.simpleKML.model.ExtendedData;
import com.ekito.simpleKML.model.Feature;
import com.ekito.simpleKML.model.Folder;
import com.ekito.simpleKML.model.Kml;
import com.ekito.simpleKML.model.Placemark;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

public class KmlMapItemImportHandler {
    public static final String TAG = "KmlMapItemHandler";
    private final MapGroup _group;
    private final String _srcName;
    private final Map<String, KmlMapItemImportFactory> _factoryMap;
    private final Handler _handler;
    private final boolean persist;
    private final MapView _mapView;

    public KmlMapItemImportHandler(MapView mapView, MapGroup mapGroup,
            String srcName,
            Map<String, KmlMapItemImportFactory> factoryMap, Handler handler,
            boolean persist) {
        super();
        _mapView = mapView;
        _group = mapGroup;
        _srcName = srcName;
        _factoryMap = factoryMap;
        _handler = handler;
        this.persist = persist;
    }

    public void importKml(InputStream inputStream, boolean findName)
            throws IOException {
        Kml kml = null;
        try {
            Serializer serializer = new Serializer();
            kml = serializer.read(inputStream);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse KML", e);
            return;
        }

        if (findName && kml != null) {
            String name = findName(kml.getFeature());
            if (name != null)
                _group.setFriendlyName(name);
        }

        // Handle all placemarks in the KML
        KMLUtil.deepFeatures(kml, new FeatureHandler<Placemark>() {

            @Override
            public boolean process(Placemark placemark) {
                handlePlacemark(placemark);
                return false;
            }
        }, Placemark.class);
    }

    private static String findName(Feature feature) {
        if (!isContainer(feature))
            return null;
        else if (feature.getName() != null)
            return feature.getName();
        if (feature instanceof Document) {
            if (((Document) feature).getFeatureList() != null) {
                for (Feature child : ((Document) feature).getFeatureList()) {
                    if (isContainer(child)) {
                        if (feature.getName() != null)
                            return feature.getName();
                    }
                }
                String retval = null;
                for (Feature child : ((Document) feature).getFeatureList()) {
                    if (isContainer(child)) {
                        retval = findName(child);
                        if (retval != null)
                            return retval;
                    }
                }
            }
        } else if (feature instanceof Folder) {
            if (((Folder) feature).getFeatureList() != null) {
                for (Feature child : ((Folder) feature).getFeatureList()) {
                    if (isContainer(child)) {
                        if (feature.getName() != null)
                            return feature.getName();
                    }
                }
                String retval = null;
                for (Feature child : ((Folder) feature).getFeatureList()) {
                    if (isContainer(child)) {
                        retval = findName(child);
                        if (retval != null)
                            return retval;
                    }
                }
            }
        }

        return null;
    }

    private static boolean isContainer(Feature feature) {
        return (feature instanceof Document) || (feature instanceof Folder);
    }

    /**
     * Moved in from KMLHandler SAX code
     */
    private void handlePlacemark(final Placemark placemark) {
        if (placemark == null) {
            Log.w(TAG, "Ignoring null Placemark");
            return;
        }

        Log.d(TAG, "Processing Placemark: " + placemark.getName());

        ExtendedData extended = placemark.getExtendedData();
        boolean handledByFactory = false;
        if (extended != null && extended.getDataList() != null) {
            List<Data> extendedData = extended.getDataList();
            for (Data d : extendedData) {
                if (d.getName().equalsIgnoreCase("factory")) {

                    final KmlMapItemImportFactory factory = _factoryMap.get(d
                            .getValue());

                    if (factory != null) {

                        // Posting the instanceFromKml because some objects add child items in their
                        // instantiation, and those need to happen in the main thread.
                        //
                        // If we define a different best practice for map items-with-children
                        // (ie, if we make them not depend on adding things during instantiation?)
                        // then
                        // this could change. -ts
                        _handler.post(new Runnable() {
                            @Override
                            public void run() {

                                MapItem item;

                                try {
                                    item = factory.instanceFromKml(placemark,
                                            _group);
                                } catch (FormatNotSupportedException e) {
                                    // should never happen
                                    Log.e(TAG, "instanceFromKml error", e);
                                    return;
                                }

                                if (item != null) {
                                    addMapItemToGroup(_mapView, item, _group,
                                            persist);
                                }
                            }
                        });
                        handledByFactory = true;
                    }
                } else if (d.getName().equalsIgnoreCase("ignore")
                        && d.getValue().equalsIgnoreCase("true")) {
                    handledByFactory = true;
                }
            }
        }

        if (!handledByFactory) {
            final MapItem[] items = KMLConversion.toMapItems(placemark);
            if (items == null || items.length < 1) {
                Log.e(TAG, "Placemark " + placemark.getName()
                        + " has no map items");
                return;
            }

            Log.d(TAG,
                    "Processing " + items.length + " Placemark"
                            + placemark.getName()
                            + " map items");

            _handler.post(new Runnable() {
                @Override
                public void run() {
                    addMapItemsToGroup(_mapView, items, _group, persist);
                }
            });
        }
    }

    private static void addMapItemsToGroup(MapView mapView, MapItem[] items,
            MapGroup group,
            boolean persist) {
        for (MapItem item : items) {
            addMapItemToGroup(mapView, item, group, persist);
        }
    }

    private static void addMapItemToGroup(MapView mapView, MapItem item,
            MapGroup group,
            boolean persist) {
        // if it hasn't already been added... shouldn't happen, but some mapItems add themselves
        // currently!
        if (item.getGroup() != group) {
            group.addItem(item);
        }

        item.refresh(mapView.getMapEventDispatcher(), null,
                KmlMapItemImportHandler.class);

        // persist to resave the item in statesaver
        if (persist)
            item.persist(mapView.getMapEventDispatcher(), null,
                    KmlMapItemImportHandler.class);

    }
}
