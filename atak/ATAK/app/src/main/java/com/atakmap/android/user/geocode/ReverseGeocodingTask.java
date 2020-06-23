
package com.atakmap.android.user.geocode;

import android.content.Context;
import android.location.Address;
import android.os.AsyncTask;
import android.widget.Toast;

import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.List;

public class ReverseGeocodingTask extends AsyncTask<Void, Void, Address> {

    private static final String TAG = "ReverseGeocodingTask";

    public interface ResultListener {
        void onResult();
    }

    final private String userAgent;
    final private GeoPoint point;
    final private Context context;
    final private boolean toastOnFail;
    private Address address = null;
    private String humanAddress = null;
    private ResultListener rl = null;

    public ReverseGeocodingTask(final GeoPoint point,
            final Context context,
            final boolean toastOnFail) {
        this.point = point;
        this.context = context;
        this.userAgent = context.getString(R.string.app_name);

        this.toastOnFail = toastOnFail;
    }

    public ReverseGeocodingTask(final GeoPoint point,
            final Context context) {
        this(point, context, true);
    }

    public void setOnResultListener(final ResultListener rl) {
        this.rl = rl;
    }

    public Address getAddress() {
        return address;
    }

    public String getHumanAddress() {
        return humanAddress;
    }

    public GeoPoint getPoint() {
        return point;
    }

    @Override
    protected Address doInBackground(Void... params) {
        try {
            return getAddress(point);
        } catch (Exception e) {
            Log.w(TAG, "getFromLocation error", e);
            return null;
        }
    }

    @Override
    protected void onPostExecute(Address result) {

        GeocodeManager.Geocoder geocoder = GeocodeManager.getInstance(context)
                .getSelectedGeocoder();

        address = result;
        humanAddress = GeocodeConverter.getAddressString(result);

        if (result == null) {
            Log.d(TAG, "address lookup error occurred: " + geocoder.getTitle());
            if (toastOnFail)
                Toast.makeText(context, R.string.address_lookup_error,
                        Toast.LENGTH_LONG).show();
        } else if (humanAddress.isEmpty()) {
            Log.w(TAG, geocoder.getTitle() + " Address not found");
            if (toastOnFail)
                Toast.makeText(context, R.string.address_not_found,
                        Toast.LENGTH_LONG).show();
        }

        if (rl != null)
            rl.onResult();
    }

    /** 
     * Provide a capability to reverse the lookup of the point and get an address.  
     * Not yet enabled.   See the associated ReverseGeoCodingTask.
     */
    private Address getAddress(final GeoPoint p) {
        GeocodeManager.Geocoder geocoder = GeocodeManager.getInstance(context)
                .getSelectedGeocoder();

        try {
            List<Address> addresses = geocoder.getLocation(p);

            return !FileSystemUtils.isEmpty(addresses) ? addresses.get(0)
                    : null;
        } catch (Exception e) {
            Log.e(TAG, "Error while performing Reverse lookup", e);

            return null;
        }
    }
}
