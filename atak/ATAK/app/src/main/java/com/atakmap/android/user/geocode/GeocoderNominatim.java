/**
 * assets/license/license-lgpl21.txt
 * source code: 
 *       https://github.com/MKergall/osmbonuspack/tree/master/OSMBonusPack/src/main/java/org/osmdroid/bonuspack/location
 * author: MKergall
 */

package com.atakmap.android.user.geocode;

import android.content.Context;
import android.location.Address;
import android.os.Bundle;
import com.atakmap.coremap.log.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Implements an equivalent to Android Geocoder class, based on OpenStreetMap data and Nominatim API. <br>
 * @see <a href="http://wiki.openstreetmap.org/wiki/Nominatim">Nominatim Reference</a>
 * @see <a href="http://open.mapquestapi.com/nominatim/">Nominatim at MapQuest Open</a>
 *
 * Important: to use the public Nominatim service, you will have to define a user agent,
 * and adhere to the <a href="http://wiki.openstreetmap.org/wiki/Nominatim_usage_policy">Nominatim usage policy</a>.
 *
 */
class GeocoderNominatim {
    private static final String TAG = "GeocoderNominatim";

    private static final String MAPQUEST_SERVICE_URL = "http://open.mapquestapi.com/nominatim/v1/";

    private final Locale mLocale;
    private String mServiceUrl;
    private String mServiceKey;
    private String mUserAgent;
    private boolean mPolygon;

    GeocoderNominatim(Context context, Locale locale, String userAgent) {
        mLocale = locale;
        setOptions(false);
        setService(MAPQUEST_SERVICE_URL); //default service
        mUserAgent = userAgent;
    }

    GeocoderNominatim(Context context, String userAgent) {
        this(context, Locale.getDefault(), userAgent);
    }

    GeocoderNominatim(Context context, Locale locale) {
        mLocale = locale;
        setService(MAPQUEST_SERVICE_URL);
    }

    static public boolean isPresent() {
        return true;
    }

    /**
     * Specify the url of the Nominatim service provider to use. 
     * Can be one of the predefined (MAPQUEST_SERVICE_URL), 
     * or another one, your local instance of Nominatim for instance. 
     */
    public void setService(String serviceUrl) {
        mServiceUrl = serviceUrl;
    }

    /**
     * Specify a key required to use this service.
     */
    public void setServiceKey(final String key) {
        mServiceKey = key;
    }

    /**
     * @param polygon true to get the polygon enclosing the location. 
     */
    public void setOptions(boolean polygon) {
        mPolygon = polygon;
    }

    /** 
     * Build an Android Address object from the Nominatim address in JSON format. 
     * Current implementation is mainly targeting french addresses,
     * and will be quite basic on other countries. 
     */
    protected Address buildAndroidAddress(JSONObject jResult)
            throws JSONException {
        Address gAddress = new Address(mLocale);
        gAddress.setLatitude(jResult.getDouble("lat"));
        gAddress.setLongitude(jResult.getDouble("lon"));

        JSONObject jAddress = jResult.getJSONObject("address");

        int addressIndex = 0;
        if (jAddress.has("road")) {
            gAddress.setAddressLine(addressIndex++, jAddress.getString("road"));
            gAddress.setThoroughfare(jAddress.getString("road"));
        }
        if (jAddress.has("suburb")) {
            //gAddress.setAddressLine(addressIndex++, jAddress.getString("suburb"));
            //not kept => often introduce "noise" in the address.
            gAddress.setSubLocality(jAddress.getString("suburb"));
        }
        if (jAddress.has("postcode")) {
            gAddress.setAddressLine(addressIndex++,
                    jAddress.getString("postcode"));
            gAddress.setPostalCode(jAddress.getString("postcode"));
        }

        if (jAddress.has("city")) {
            gAddress.setAddressLine(addressIndex++, jAddress.getString("city"));
            gAddress.setLocality(jAddress.getString("city"));
        } else if (jAddress.has("town")) {
            gAddress.setAddressLine(addressIndex++, jAddress.getString("town"));
            gAddress.setLocality(jAddress.getString("town"));
        } else if (jAddress.has("village")) {
            gAddress.setAddressLine(addressIndex++,
                    jAddress.getString("village"));
            gAddress.setLocality(jAddress.getString("village"));
        }

        if (jAddress.has("county")) { //France: departement
            gAddress.setSubAdminArea(jAddress.getString("county"));
        }
        if (jAddress.has("state")) { //France: region
            gAddress.setAdminArea(jAddress.getString("state"));
        }
        if (jAddress.has("country")) {
            gAddress.setAddressLine(addressIndex++,
                    jAddress.getString("country"));
            gAddress.setCountryName(jAddress.getString("country"));
        }
        if (jAddress.has("country_code"))
            gAddress.setCountryCode(jAddress.getString("country_code"));

        /* Other possible OSM tags in Nominatim results not handled yet: 
         * subway, golf_course, bus_stop, parking,...
         * house, house_number, building
         * city_district (13e Arrondissement)
         * road => or highway, ...
         * sub-city (like suburb) => locality, isolated_dwelling, hamlet ...
         * state_district
        */

        //Add non-standard (but very useful) information in Extras bundle:
        Bundle extras = new Bundle();
        /**
                if (jResult.has("polygonpoints")){
                    JSONArray jPolygonPoints = jResult.getJSONArray("polygonpoints");
                    ArrayList<GeoPoint> polygonPoints = new ArrayList<GeoPoint>(jPolygonPoints.length());
                    for (int i=0; i<jPolygonPoints.length(); i++){
                        JSONArray jCoords = jPolygonPoints.getJSONArray(i);
                        double lon = jCoords.getDouble(0);
                        double lat = jCoords.getDouble(1);
                        GeoPoint p = new GeoPoint(lat, lon);
                        polygonPoints.add(p);
                    }
                    extras.putParcelableArrayList("polygonpoints", polygonPoints);
                }
                if (jResult.has("boundingbox")){
                    JSONArray jBoundingBox = jResult.getJSONArray("boundingbox");
                    BoundingBoxE6 bb = new BoundingBoxE6(
                            jBoundingBox.getDouble(1), jBoundingBox.getDouble(2), 
                            jBoundingBox.getDouble(0), jBoundingBox.getDouble(3));
                    extras.putParcelable("boundingbox", bb);
                }
        **/
        if (jResult.has("osm_id")) {
            long osm_id = jResult.getLong("osm_id");
            extras.putLong("osm_id", osm_id);
        }
        if (jResult.has("osm_type")) {
            String osm_type = jResult.getString("osm_type");
            extras.putString("osm_type", osm_type);
        }
        if (jResult.has("display_name")) {
            String display_name = jResult.getString("display_name");
            final int lastComma = display_name.lastIndexOf(",");
            if (lastComma > 0)
                display_name = display_name.substring(0, lastComma);
            extras.putString("display_name", display_name);
        }
        gAddress.setExtras(extras);

        return gAddress;
    }

    /**
     * Equivalent to Geocoder::getFromLocation(double latitude, double longitude, int maxResults). 
     */
    public List<Address> getFromLocation(double latitude, double longitude,
            int maxResults)
            throws IOException {
        String keyPart = "";
        if (mServiceKey != null && mServiceKey.length() > 0)
            keyPart = "&key=" + mServiceKey;

        String url = mServiceUrl
                + "reverse?"
                + "format=json"
                + keyPart
                + "&accept-language=" + mLocale.getLanguage()
                + "&addressdetails=1"
                + "&lat=" + latitude
                + "&lon=" + longitude;
        Log.d(GeocodeConverter.TAG, "GeocoderNominatim::getFromLocation:"
                + url);
        String result = GeocodeConverter.requestStringFromUrl(url, mUserAgent);

        Log.d(GeocodeConverter.TAG, result);
        try {

            if (result == null)
                throw new IOException();

            JSONObject jResult = new JSONObject(result);
            Address gAddress = buildAndroidAddress(jResult);
            List<Address> list = new ArrayList<>(1);
            list.add(gAddress);
            Log.e(GeocodeConverter.TAG, list.toString());
            return list;

        } catch (JSONException e) {
            throw new IOException();
        }
    }

    /**
     * Equivalent to Geocoder::getFromLocation(String locationName, int maxResults, double lowerLeftLatitude, double lowerLeftLongitude, double upperRightLatitude, double upperRightLongitude)
     * but adding bounded parameter. 
     * @param bounded true = return only results which are inside the view box; false = view box is used as a preferred area to find search results. 
     */
    public List<Address> getFromLocationName(String locationName,
            int maxResults,
            double lowerLeftLatitude, double lowerLeftLongitude,
            double upperRightLatitude, double upperRightLongitude,
            boolean bounded)
            throws IOException {
        String keyPart = "";
        if (mServiceKey != null && mServiceKey.length() > 0)
            keyPart = "&key=" + mServiceKey;
        String url = mServiceUrl
                + "search?"
                + "format=json"
                + keyPart
                + "&accept-language=" + mLocale.getLanguage()
                + "&addressdetails=1"
                + "&limit=" + maxResults
                + "&q=" + URLEncoder.encode(locationName);
        if (lowerLeftLatitude != 0.0 && upperRightLatitude != 0.0) {
            //viewbox = left, top, right, bottom:
            url += "&viewbox=" + lowerLeftLongitude
                    + "%2C" + upperRightLatitude
                    + "%2C" + upperRightLongitude
                    + "%2C" + lowerLeftLatitude
                    + "&bounded=" + (bounded ? 1 : 0);
        }
        if (mPolygon) {
            //get polygon outlines for items found:
            url += "&polygon=1";
            //TODO: polygon param is obsolete. Should be replaced by polygon_geojson. 
            //Upgrade is on hold, waiting for MapQuest service to become compatible. 
        }
        Log.d(GeocodeConverter.TAG,
                "GeocoderNominatim::getFromLocationName:" + url);
        String result = GeocodeConverter.requestStringFromUrl(url, mUserAgent);
        //Log.d(BonusPackHelper.LOG_TAG, result);
        if (result == null)
            throw new IOException();
        try {
            JSONArray jResults = new JSONArray(result);
            List<Address> list = new ArrayList<>(jResults.length());
            for (int i = 0; i < jResults.length(); i++) {
                JSONObject jResult = jResults.getJSONObject(i);
                Address gAddress = buildAndroidAddress(jResult);
                list.add(gAddress);
            }
            Log.d(GeocodeConverter.TAG, "done");
            return list;
        } catch (JSONException e) {
            Log.w(TAG, "Failed to parse response: " + url, e);
            throw new IOException();
        }
    }

    /**
     * Equivalent to Geocoder::getFromLocation(String locationName, int maxResults, double lowerLeftLatitude, double lowerLeftLongitude, double upperRightLatitude, double upperRightLongitude)
     * @see #getFromLocationName(String locationName, int maxResults) about extra data added in Address results. 
     */
    public List<Address> getFromLocationName(String locationName,
            int maxResults,
            double lowerLeftLatitude, double lowerLeftLongitude,
            double upperRightLatitude, double upperRightLongitude)
            throws IOException {
        return getFromLocationName(locationName, maxResults,
                lowerLeftLatitude, lowerLeftLongitude,
                upperRightLatitude, upperRightLongitude, true);
    }

    /**
     * Equivalent to Geocoder::getFromLocation(String locationName, int maxResults). <br>
     * 
     * Some useful information, returned by Nominatim, that doesn't fit naturally within Android Address, are added in the bundle Address.getExtras():<br>
     * "boundingbox": the enclosing bounding box, as a BoundingBoxE6<br>
     * "osm_id": the OSM id, as a long<br>
     * "osm_type": one of the 3 OSM types, as a string (node, way, or relation). <br>
     * "display_name": the address, as a single String<br>
     * "polygonpoints": the enclosing polygon of the location (depending on setOptions usage), as an ArrayList of GeoPoint<br>
     */
    public List<Address> getFromLocationName(String locationName,
            int maxResults)
            throws IOException {
        return getFromLocationName(locationName, maxResults, 0.0, 0.0, 0.0,
                0.0, false);

    }

    /**
     * Same as the bounded search, but will fall back to an unbounded search if the first 
     * one returns no values.
     */
    public List<Address> getFromLocationNameFallback(String locationName,
            int maxResults,
            double lowerLeftLatitude, double lowerLeftLongitude,
            double upperRightLatitude, double upperRightLongitude)
            throws IOException {
        List<Address> retval = getFromLocationName(locationName, maxResults,
                lowerLeftLatitude, lowerLeftLongitude,
                upperRightLatitude, upperRightLongitude, true);
        if (retval.size() == 0) {
            Log.d(GeocodeConverter.TAG,
                    "no values in the field of view, expanding");
            retval = getFromLocationName(locationName, maxResults,
                    lowerLeftLatitude, lowerLeftLongitude,
                    upperRightLatitude, upperRightLongitude, false);
        }
        return retval;

    }

}
