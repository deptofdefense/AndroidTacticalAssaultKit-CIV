
package com.atakmap.app.preferences;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.preference.Preference;

import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.user.geocode.GeocodeManager;
import com.atakmap.android.user.geocode.GeocodingTask;
import com.atakmap.android.user.geocode.ReverseGeocodingTask;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.xml.XMLUtils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.InputStream;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * TODO: move under the user.geocode package.
 * augment the external file to look like this (inclusion of a type and url):
 * <p>
 * suppliers.put("MapQuest", "type=nomination,url=http://open.mapquestapi.com/nominatim/v1/");
 */
public class GeocoderPreferenceFragment extends AtakPreferenceFragment {

    public static final String TAG = "GeocoderPreferenceFragment";

    public static final String ADDRESS_DIR = FileSystemUtils.TOOL_DATA_DIRECTORY
            + File.separatorChar + "address";

    public GeocoderPreferenceFragment() {
        super(R.xml.geocoder_preference_fragment, R.string.geocoderPreference);
    }

    @Override
    public String getSubTitle() {
        return getSubTitle(getString(R.string.toolPreferences),
                getSummary());
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        super.addPreferencesFromResource(getResourceID());

        final Preference supplier = findPreference("geocodeSupplier");
        Preference test = findPreference("geocodeTestConnection");

        supplier.setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        showPicker();
                        return true;
                    }
                });
        test.setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        testConnection();
                        return true;
                    }
                });
    }

    public void testConnection() {
        new Thread(testConnectionRunnable,
                "TestConnectionThread").start();

    }

    public void showPicker() {

        final AlertDialog.Builder adb = new AlertDialog.Builder(getActivity());

        final List<GeocodeManager.Geocoder> pluginRegistered = GeocodeManager
                .getInstance(getActivity()).getAllGeocoders();

        // the list is composed of suppliers found and plugin registered geocoders

        final String[] name = new String[pluginRegistered.size()];

        int selected = 0;

        GeocodeManager.Geocoder curr = GeocodeManager.getInstance(getActivity())
                .getSelectedGeocoder();

        for (int i = 0; i < pluginRegistered.size(); ++i) {
            name[i] = pluginRegistered.get(i).getTitle();
            if (pluginRegistered.get(i).equals(curr))
                selected = i;
        }

        adb.setSingleChoiceItems(name, selected, new OnClickListener() {

            @Override
            public void onClick(DialogInterface d, int n) {
                Log.d(TAG, "selected: " + name[n]);
                GeocodeManager.getInstance(getActivity()).setDefaultGeocoder(
                        pluginRegistered.get(n).getUniqueIdentifier());
            }

        });
        adb.setNegativeButton(R.string.ok, null);
        adb.setTitle(R.string.preferences_text383);
        adb.show();
    }

    private final Runnable testConnectionRunnable = new Runnable() {
        ProgressDialog pd = null;

        @Override
        public void run() {

            // show spinner
            getActivity().runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    pd = ProgressDialog.show(getActivity(),
                            getString(R.string.working),
                            getString(R.string.preferences_text384), true,
                            false);
                    pd.show();
                }
            });
            GeocodeManager.Geocoder curr = GeocodeManager
                    .getInstance(getActivity()).getSelectedGeocoder();

            try {
                //shb todo
                final GeocodingTask gt = new GeocodingTask(getActivity(), 0, 0,
                        0, 0);
                gt.setOnResultListener(new GeocodingTask.ResultListener() {
                    @Override
                    public void onResult() {
                        final String address = gt.getHumanAddress();
                        if (address != null && address.length() > 0) {
                            performReverse();
                        } else {
                            result(false,
                                    getString(R.string.preferences_text395));
                        }
                    }
                });

                gt.execute("Washington");
            } catch (Exception ioe) {
                Log.w(TAG, "Failed to reach server: " + curr.getTitle(), ioe);
                result(false,
                        getString(R.string.preferences_text396));
            }
        }

        private void performReverse() {
            final Context c = getActivity();

            if (c == null)
                return;

            final ReverseGeocodingTask rgt = new ReverseGeocodingTask(
                    new GeoPoint(42, -72), c);
            rgt.setOnResultListener(new ReverseGeocodingTask.ResultListener() {
                @Override
                public void onResult() {
                    final String address = rgt.getHumanAddress();
                    if (address != null && address.length() > 0) {
                        result(true, "");
                    } else {
                        result(false,
                                c.getString(R.string.preferences_text398));
                    }
                }
            });

            rgt.execute();

        }

        private void result(final boolean success, final String additional) {

            try {
                Context context = getActivity();

                if (context == null)
                    return;

                GeocodeManager.Geocoder geocoder = GeocodeManager
                        .getInstance(context)
                        .getSelectedGeocoder();
                final String name = geocoder.getTitle();

                Log.d(TAG, "geocoder: " + geocoder.getTitle() + " "
                        + geocoder.getClass());

                final String msg;
                if (success) {
                    msg = String.format(
                            context.getString(R.string.preferences_text397),
                            name);
                } else {
                    msg = String.format(
                            context.getString(R.string.preferences_text402),
                            name,
                            additional);
                }

                getActivity().runOnUiThread(new Runnable() {

                    @Override
                    public void run() {

                        AlertDialog.Builder b = new AlertDialog.Builder(
                                context);
                        b.setTitle(success ? context.getString(R.string.success)
                                : context.getString(R.string.failure));
                        b.setMessage(msg);
                        b.setPositiveButton(context.getString(R.string.ok),
                                null);

                        if (pd != null)
                            pd.dismiss();

                        b.show();
                    }
                });
            } catch (Exception ignored) {
            }
        }

    };

    /**
     * Load the specified file, and set that as the current supplier
     *
     * @param supplier the file to set the supplier
     */
    public static void load(File supplier) {
        try {
            if (FileSystemUtils.isFile(supplier)) {
                Log.d(TAG, "loading: " + supplier.getAbsolutePath());

                DocumentBuilderFactory dbf = XMLUtils
                        .getDocumenBuilderFactory();

                DocumentBuilder db = dbf.newDocumentBuilder();
                Document document;
                try (InputStream is = IOProviderFactory
                        .getInputStream(supplier)) {
                    document = db.parse(is);
                }
                document.getDocumentElement().normalize();
                NodeList nList = document.getElementsByTagName("entry");

                for (int i = 0; i < nList.getLength(); ++i) {
                    Node nNode = nList.item(i);
                    if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                        Element eElement = (Element) nNode;
                        final String name = eElement.getAttribute("name");
                        final String url = eElement.getAttribute("url");
                        String serviceKey = eElement.getAttribute("serviceKey");
                        if (serviceKey == null)
                            serviceKey = "";

                        Log.d(TAG, "registering current supplier: " + name
                                + " url: " + url + " serviceKey: "
                                + serviceKey);
                    }
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "error occurred reading geocoding servers config file",
                    e);
        }
    }
}
