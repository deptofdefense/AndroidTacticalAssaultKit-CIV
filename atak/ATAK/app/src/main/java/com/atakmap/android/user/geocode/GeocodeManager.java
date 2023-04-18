
package com.atakmap.android.user.geocode;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Address;
import android.preference.PreferenceManager;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.xml.XMLUtils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Ability to register a variety of types of Geocoding capabilities via a plugin architecture.
 * Users must implement the Geocode Interface in order to be used.
 */
public class GeocodeManager {

    private static final String TAG = "GeocodeManager";

    private static GeocodeManager _instance;

    public static final String ANDROID_DEFAULT = "android-geocoder";

    public static final String ADDRESS_DIR = FileSystemUtils.TOOL_DATA_DIRECTORY
            + File.separatorChar + "address";

    private final List<Geocoder> geocoders = new ArrayList<>();
    private final SharedPreferences _prefs;
    private final Context context;

    private final ConcurrentLinkedQueue<GeocoderChangedListener> geocoderChangeListeners = new ConcurrentLinkedQueue<>();

    public interface Geocoder {
        /**
         * Used to identify the Geocoder when the user selects it for use in the system.
         * @return the suggested return is a constant unique identifier for the specifc
         * geocoder implementation.
         */
        String getUniqueIdentifier();

        /**
         * A freetext title used to describe to the user the actual name of the geocoding
         * capability.
         * @return a text string not guaranteed to be unique.
         */
        String getTitle();

        /**
         * A freetext description used to describe to the user the actual geocoding
         * capability.
         * @return a text string not guaranteed to be unique.
         */
        String getDescription();

        boolean testServiceAvailable();

        /**
         * The actual work required to turn a geopoint into an address.
         * @param gp the geopoint
         * @return the android address.
         */
        List<Address> getLocation(GeoPoint gp);

        /**
         * Given a freetext address and an optionally supplied geobounds, attempt to return
         * an address. the GeoBounds can be null.
         * @return a list of Addresses containing the latitude and longitude.  For the purposes
         * of display in ATAK, the address lines are the only thing looked at.
         */
        List<Address> getLocation(String address, GeoBounds bounds);
    }

    /**
     * The interface for listenining when the default geocoder is changed in the system.
     */
    public interface GeocoderChangedListener {
        /**
         * The new default geocoder
         * @param geocoder the geocoder that has been selected to be the default
         */
        void onGeocoderChanged(Geocoder geocoder);
    }

    private GeocodeManager(Context c) {
        _prefs = PreferenceManager.getDefaultSharedPreferences(c);
        context = c;
        registerGeocoder(AndroidGeocoder);

        loadNominatim();
    }

    /**
     * Returns the instance of the GeocodeManager.
     * @return the instance
     */
    public synchronized static GeocodeManager getInstance(
            final Context c) {
        if (_instance == null)
            _instance = new GeocodeManager(c);

        return _instance;
    }

    /**
     * Ability to register a new geocoder to the system.
     * @param geocoder the geocoder to register
     */
    synchronized public void registerGeocoder(Geocoder geocoder) {
        synchronized (geocoders) {
            if (!geocoders.contains(geocoder))
                geocoders.add(geocoder);
        }
    }

    /**
     * Ability to remove a geocoder from the system.
     * @param geocoder the geocoder to unregister
     */
    synchronized public void unregisterGeocoder(Geocoder geocoder) {
        synchronized (geocoders) {
            if (geocoder.equals(AndroidGeocoder))
                return;
            geocoders.remove(geocoder);
        }
    }

    /**
     * Gets the currently selected geocoder.
     * @return the geocoder
     */
    synchronized public Geocoder getSelectedGeocoder() {
        final String selected_uid = _prefs.getString("selected_geocoder_uid",
                "");

        for (Geocoder geocoder : geocoders) {
            if (selected_uid.equals(geocoder.getUniqueIdentifier()))
                return geocoder;
        }
        return geocoders.get(0);

    }

    /**
     * Given a unique identifier, set that to be the default geocoder.
     * @param uid the unique identifier
     */
    synchronized public void setDefaultGeocoder(String uid) {
        _prefs.edit().putString("selected_geocoder_uid", uid).apply();
        fireGeocoderChangeListener(getSelectedGeocoder());
    }

    synchronized public List<Geocoder> getAllGeocoders() {
        return new ArrayList<>(geocoders);
    }

    private final Geocoder AndroidGeocoder = new Geocoder() {

        private final static String TAG = "AndroidGeocoder";

        @Override
        public String getUniqueIdentifier() {
            return ANDROID_DEFAULT;
        }

        @Override
        public String getTitle() {
            return "Native Android Geocoder";
        }

        @Override
        public String getDescription() {
            return "Native Android Geocoder";
        }

        @Override
        public boolean testServiceAvailable() {
            return true;
        }

        @Override
        public List<Address> getLocation(GeoPoint gp) {
            android.location.Geocoder geocoder = new android.location.Geocoder(
                    context, Locale.getDefault());
            try {
                return geocoder.getFromLocation(
                        gp.getLatitude(),
                        gp.getLongitude(), 1);
            } catch (IOException e) {
                Log.w(TAG, "getFromLocation error", e);
                return null;
            }
        }

        @Override
        public List<Address> getLocation(String address, GeoBounds bounds) {
            android.location.Geocoder geocoder = new android.location.Geocoder(
                    context, Locale.getDefault());
            try {
                // First filter by view bounds
                List<Address> ret = geocoder.getFromLocationName(
                        address, 1,
                        bounds.getSouth(), bounds.getWest(), bounds.getNorth(),
                        bounds.getEast());

                // Then filter by entire world
                if (ret == null || ret.isEmpty())
                    ret = geocoder.getFromLocationName(address, 1);
                return ret;
            } catch (Exception e) {
                Log.w(TAG, "getFromLocationName error", e);
                return null;
            }
        }
    };

    private class NominatimGeocoder implements Geocoder {

        public static final String TAG = "NominatimGeocoder";

        private final String uuid;
        private final String title;
        private final String description;
        private final String url;
        private final String key;

        public NominatimGeocoder(String uuid, String title, String description,
                String url, String key) {
            this.uuid = uuid;
            this.title = title;
            this.description = description;
            this.url = url;
            this.key = key;
        }

        @Override
        public String getUniqueIdentifier() {
            return uuid;
        }

        @Override
        public String getTitle() {
            return title;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public boolean testServiceAvailable() {
            try {
                Log.d(TAG, "Testing connection to server: " + url);
                HttpURLConnection connection = (HttpURLConnection) new URL(
                        url)
                                .openConnection();
                connection.setRequestProperty("User-Agent", "TAK");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.connect();
                return true;
            } catch (IOException e) {
                return false;
            }

        }

        @Override
        public List<Address> getLocation(GeoPoint gp) {
            String geoService = url;
            String geoServiceKey = key;
            String userAgent = "ATAK";

            GeocoderNominatim geocoder = new GeocoderNominatim(context,
                    userAgent);

            geocoder.setService(geoService);
            geocoder.setServiceKey(geoServiceKey);

            geocoder.setOptions(false); //ask for enclosing polygon (if any)
            try {
                return geocoder.getFromLocation(gp.getLatitude(),
                        gp.getLongitude(), 1);
            } catch (IOException e) {
                Log.w(TAG, "getFromLocation error: " + geoService, e);
                return null;
            }
        }

        @Override
        public List<Address> getLocation(final String address,
                final GeoBounds bounds) {
            String geoService = url;
            String geoServiceKey = key;
            String userAgent = "ATAK";

            GeocoderNominatim geocoder = new GeocoderNominatim(context,
                    userAgent);

            geocoder.setService(geoService);
            geocoder.setServiceKey(geoServiceKey);

            geocoder.setOptions(false); //ask for enclosing polygon (if any)
            try {
                return geocoder.getFromLocationNameFallback(
                        address, 1,
                        bounds.getSouth(), bounds.getWest(), bounds.getNorth(),
                        bounds.getEast());
            } catch (Exception e) {
                Log.w(TAG, "getFromLocationNameFallback error: " + geoService,
                        e);
                return null;
            }
        }
    }

    private void loadNominatim() {
        try {
            final File dir = FileSystemUtils.getItem(ADDRESS_DIR);
            if (IOProviderFactory.exists(dir)) {
                File[] files = IOProviderFactory.listFiles(dir);
                if (files != null) {
                    for (File file : files) {
                        Log.d(TAG, "loading: " + file);

                        DocumentBuilderFactory dbf = XMLUtils
                                .getDocumenBuilderFactory();

                        DocumentBuilder db = dbf.newDocumentBuilder();
                        Document document;
                        try (InputStream is = IOProviderFactory
                                .getInputStream(file)) {
                            document = db.parse(is);
                        }
                        document.getDocumentElement().normalize();
                        NodeList nList = document.getElementsByTagName("entry");

                        for (int i = 0; i < nList.getLength(); ++i) {

                            Node nNode = nList.item(i);
                            if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                                Element eElement = (Element) nNode;
                                final String name = eElement
                                        .getAttribute("name");
                                final String url = eElement.getAttribute("url");
                                final String serviceKey = eElement
                                        .getAttribute("serviceKey");

                                Log.d(TAG, "registering server: " + name
                                        + " url: "
                                        + url + " serviceKey: " + serviceKey);
                                // in this case use the name as the description and the url as the key
                                registerGeocoder(new NominatimGeocoder(url,
                                        name, name, url, serviceKey));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "error occurred reading geocoding servers config file",
                    e);
        }

    }

    private void fireGeocoderChangeListener(Geocoder geocoder) {
        for (GeocoderChangedListener geocoderChangedListener : geocoderChangeListeners) {
            try {
                geocoderChangedListener.onGeocoderChanged(geocoder);
            } catch (Exception e) {
                Log.e(TAG, "error notifying: " + geocoderChangedListener, e);
            }
        }
    }

    /**
     * Registers a geocoder change listener.
     * @param listener listener for the geocoder change
     */
    public void registerGeocoderChangeListener(
            GeocoderChangedListener listener) {
        if (!geocoderChangeListeners.contains(listener))
            geocoderChangeListeners.add(listener);
    }

    /**
     * Unregisters a geocoder change listener.
     * @param listener listener for the geocoder change
     */
    public void unregisterGeocoderChangeListener(
            GeocoderChangedListener listener) {
        geocoderChangeListeners.remove(listener);
    }

}
