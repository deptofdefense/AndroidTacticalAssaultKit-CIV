
package com.atakmap.android.user.geocode;

import android.content.Context;
import android.location.Address;
import android.os.AsyncTask;
import android.widget.Toast;

import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.List;

/**
 * Task to take an address and convert it to a GeoPoint.
 */
public class GeocodingTask extends AsyncTask<Object, Void, List<Address>> {

    private static final String TAG = "GeocodingTask";

    public interface ResultListener {
        void onResult();
    }

    final private String userAgent;
    final private Context context;

    private String humanAddress = null;
    private GeoPoint humanAddressPoint;
    private ResultListener rl;
    private final double lowerLeftLat;
    private final double lowerLeftLon;
    private final double upperRightLat;
    private final double upperRightLon;
    private final boolean toastOnFail;

    public GeocodingTask(Context context,
            double lowerLeftLat, double lowerLeftLon,
            double upperRightLat, double upperRightLon, boolean toastOnFail) {
        this.context = context;
        this.userAgent = context.getString(R.string.app_name);

        this.lowerLeftLat = lowerLeftLat;
        this.lowerLeftLon = lowerLeftLon;
        this.upperRightLat = upperRightLat;
        this.upperRightLon = upperRightLon;
        this.toastOnFail = toastOnFail;
    }

    public GeocodingTask(Context context,
            double lowerLeftLat, double lowerLeftLon,
            double upperRightLat, double upperRightLon) {
        this(context, lowerLeftLat, lowerLeftLon,
                upperRightLat, upperRightLon, true);
    }

    public void setOnResultListener(final ResultListener rl) {
        this.rl = rl;
    }

    public String getHumanAddress() {
        return humanAddress;
    }

    public GeoPoint getPoint() {
        return humanAddressPoint;
    }

    @Override
    protected List<Address> doInBackground(Object... params) {
        String locationAddress = (String) params[0];
        GeocodeManager.Geocoder geocoder = GeocodeManager.getInstance(context)
                .getSelectedGeocoder();
        List<Address> ret = geocoder.getLocation(
                locationAddress,
                new GeoBounds(lowerLeftLat, lowerLeftLon, upperRightLat,
                        upperRightLon));
        return ret;
    }

    @Override
    protected void onPostExecute(List<Address> foundAddresses) {
        GeocodeManager.Geocoder geocoder = GeocodeManager.getInstance(context)
                .getSelectedGeocoder();

        if (foundAddresses == null) {
            Log.w(TAG, geocoder.getTitle() + " Address lookup error occurred");
            if (toastOnFail)
                Toast.makeText(context, R.string.address_lookup_error,
                        Toast.LENGTH_LONG).show();
        } else if (foundAddresses.size() == 0) { //if no address found, display an error
            Log.w(TAG, geocoder.getTitle() + " Address not found");
            if (toastOnFail)
                Toast.makeText(context, R.string.address_not_found,
                        Toast.LENGTH_LONG).show();
        } else {
            Log.w(TAG,
                    geocoder.getTitle() + " address matches: "
                            + foundAddresses.size());
            Address address = foundAddresses.get(0); //get first address
            humanAddress = GeocodeConverter.getAddressString(address);
            humanAddressPoint = new GeoPoint(address.getLatitude(),
                    address.getLongitude());
        }
        if (rl != null)
            rl.onResult();
    }
}
