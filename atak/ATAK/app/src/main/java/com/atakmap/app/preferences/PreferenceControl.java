
package com.atakmap.app.preferences;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.widget.Toast;
import android.util.Base64;

import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.app.R;
import com.atakmap.comms.TAKServer;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.comms.CotServiceRemote;
import com.atakmap.comms.CotServiceRemote.ConnectionListener;
import com.atakmap.coremap.xml.XMLUtils;
import com.atakmap.net.AtakAuthenticationDatabase;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class PreferenceControl implements ConnectionListener {

    static public final String TAG = "PreferenceControl";

    /**
     * App's current preference path (based on manifest package)
     */
    private final String DEFAULT_PREFERENCES_NAME;

    public final String[] PreferenceGroups;
    static private PreferenceControl _instance;
    private final Context _context;

    public static final String DIRNAME = FileSystemUtils.CONFIG_DIRECTORY
            + File.separatorChar + "prefs";
    public static final String DIRPATH = FileSystemUtils.getItem(DIRNAME)
            .getPath();
    private CotServiceRemote _remote;
    private boolean connected;

    /**
     * Singleton class for exporting and importing external preferences into the system.
     */
    public static synchronized PreferenceControl getInstance(final Context c) {
        if (_instance == null) {
            _instance = new PreferenceControl(c);
        }

        return _instance;
    }

    public static synchronized void dispose() {
        if (_instance != null)
            _instance.disposeImpl();
        _instance = null;
    }

    private PreferenceControl(Context context) {
        _context = context;
        connected = false;
        DEFAULT_PREFERENCES_NAME = _context.getPackageName() + "_preferences";
        PreferenceGroups = new String[] {
                "cot_inputs", "cot_outputs", "cot_streams",
                DEFAULT_PREFERENCES_NAME
        };
    }

    /**
     * Preserves the original intent of connecting. Now that the PreferenceControl is started early,
     * allow for this to be called in the original location. This should not be called during the
     * ATAKActivity instantiation.
     */
    public void connect() {
        _remote = new CotServiceRemote();
        _remote.connect(this);
    }

    public void disconnect() {
        try {
            connected = false;
            if (_remote != null)
                _remote.disconnect();
            _remote = null;
        } catch (Exception e) {
            Log.e(TAG, "disconnection error: " + e);
        }
    }

    private void disposeImpl() {

        try {
            connected = false;
            if (_remote != null)
                _remote.disconnect();
            _remote = null;
        } catch (Exception e) {
            Log.e(TAG, "disconnection error occurred during shutdown: " + e);
        }
    }

    /**
     * Obtains all of the Shared Preferences used by the system.   Please note that the preference
     * do contain private device specific information.   If transfering to a new device please omit
     * the key "bestDeviceUID", if cloning please omit the key "bestDeviceUID" and "locationCallsign".
     * @return a hashmap containing the preference name as the key and the SharedPreference as a value.
     */
    public HashMap<String, SharedPreferences> getAllPreferences() {
        HashMap<String, SharedPreferences> prefs = new HashMap<>();
        for (String group : PreferenceGroups) {
            SharedPreferences pref = _context.getSharedPreferences(
                    group,
                    Context.MODE_PRIVATE);
            prefs.put(group, pref);
        }
        return prefs;
    }

    public void saveSettings(String path) {
        File configFile = new File(DIRPATH, path);
        if (!FileSystemUtils.deleteFile(configFile)) {
            Log.d(TAG, "error deleting: " + configFile);
        }
        boolean created = IOProviderFactory.createNewFile(configFile);
        if (!created) {
            Toast.makeText(_context,
                    R.string.preferences_text409,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder sb = new StringBuilder(
                "<?xml version='1.0' standalone='yes'?>\r\n");
        sb.append("<preferences>\r\n");
        for (String PreferenceGroup : PreferenceGroups) {
            SharedPreferences pref = _context.getSharedPreferences(
                    PreferenceGroup,
                    Context.MODE_PRIVATE);

            sb.append("<preference version=\"1\" name=\"")
                    .append(PreferenceGroup).append("\">\r\n");
            Map<String, ?> keyValuePairs = pref.getAll();

            final String k1 = Base64.encodeToString(
                    AtakAuthenticationDatabase.TAG
                            .getBytes(FileSystemUtils.UTF8_CHARSET),
                    Base64.NO_WRAP);
            final String k2 = Base64.encodeToString(
                    AtakAuthenticationDatabase.TAG
                            .getBytes(FileSystemUtils.UTF8_CHARSET),
                    Base64.NO_WRAP);

            for (Map.Entry<String, ?> e : keyValuePairs.entrySet()) {
                String key = (String) e.getKey();
                Object value = e.getValue();

                if (value == null) {
                    Log.d(TAG, "null value for key: " + key);
                    continue;
                }

                String strClass = value.getClass().toString();

                if (key.equals("locationCallsign") ||
                        key.equals("bestDeviceUID") ||
                        key.equals(k1) || key.equals(k2)) {
                    // do nothing
                } else {
                    sb.append("<entry key=\"");
                    sb.append(encode(key));
                    sb.append("\" class=\"");
                    sb.append(strClass);
                    sb.append("\">");

                    if (value instanceof Set<?>) {
                        // Store each entry in the set
                        sb.append("\r\n");
                        Set<?> set = (Set<?>) value;
                        for (Object v : set) {
                            sb.append("<element>");
                            if (v instanceof String)
                                v = encode((String)v);
                            sb.append(v);
                            sb.append("</element>\r\n");
                        }
                    } else {
                        if (value instanceof String)
                            value = encode((String)value);
                        sb.append(value);
                    }
                    sb.append("</entry>\r\n");
                }
            }

            sb.append("</preference>\r\n");

        }
        sb.append("</preferences>\r\n");

        try (BufferedWriter bw = new BufferedWriter(
                IOProviderFactory.getFileWriter(configFile))) {
            bw.write(sb.toString());
        } catch (IOException e) {
            Log.e(TAG, "error: ", e);
        }

    }

    /**
     * Loads a new default set of configuration options and then removes the file containing these
     * values. This is useful for staging a system once and then pushing configuration changes out
     * to many system post deployment. The file is named default and it is required to be in the
     * prefs directory. The prefs directory can either be internal or external. All other
     * preferences are loaded from just the internal card. XXX - This is a partial fix for the
     * staging of defaults to devices where ClockworkMod recovery is being used to stage the
     * devices.
     */
    public boolean ingestDefaults() {
        final String filename = "defaults";
        String[] mounts = FileSystemUtils.findMountPoints();

        for (String mount : mounts) {
            File configFile = new File(mount + File.separator + DIRNAME
                    + File.separator
                    + filename);
            if (IOProviderFactory.exists(configFile)) {
                Log.d(TAG,
                        "default configuration file found, loading entries: "
                                + configFile);
                // do not perform a connection check
                try {
                    loadSettings(configFile);
                } catch (Exception e) {
                    Log.e(TAG,
                            "default configuration file contained an error: "
                                    + configFile);
                    Log.e(TAG, "error: ", e);
                }
                FileSystemUtils.deleteFile(configFile);
            } else {
                Log.d(TAG, "no default config file found: " + configFile);
            }
        }
        return true;
    }

    public void loadSettings(String path, boolean bCheckConnection) {
        if (path.equals("<none>"))
            return;

        File configFile = new File(DIRPATH, path);
        if (!IOProviderFactory.exists(configFile)) {
            Log.w(TAG, "File not found: " + configFile.getAbsolutePath());
            Toast.makeText(_context, "File not found", Toast.LENGTH_SHORT)
                    .show();
            return;
        }
        if (bCheckConnection && !connected) {
            Log.w(TAG,
                    "System still loading.  Please try again: "
                            + configFile.getAbsolutePath());
            Toast.makeText(_context,
                    R.string.preferences_text410,
                    Toast.LENGTH_SHORT)
                    .show();
            return;
        }

        try {
            loadSettings(configFile);
        } catch (Exception e) {
            Log.e(TAG, "default configuration file contained an error: "
                    + configFile.getAbsolutePath(), e);
        }
    }

    public void loadPartialSettings(Context context, String path,
            boolean bCheckConnection, String[] categorySet) {
        if (path.equals("<none>"))
            return;

        File configFile = new File(DIRPATH, path);
        if (!IOProviderFactory.exists(configFile)) {
            Log.w(TAG, "File not found: " + configFile.getAbsolutePath());
            Toast.makeText(_context, "File not found", Toast.LENGTH_SHORT)
                    .show();
            return;
        }
        if (bCheckConnection && !connected) {
            Log.w(TAG,
                    "System still loading.  Please try again: "
                            + configFile.getAbsolutePath());
            Toast.makeText(_context, R.string.preferences_text410,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            loadPartialSettings(context, configFile, categorySet);
        } catch (Exception e) {
            Log.e(TAG, "default configuration file contained an error: "
                    + configFile.getAbsolutePath(), e);
        }
    }

    /**
     * Loads the settings, and optionally performs a connection check to see if the
     * CotServiceRemote is running (leaving that in as legacy)
     * @return list of keys that were imported.
     */
    public List<String> loadSettings(final File configFile) {
        Log.d(TAG, "Loading settings: " + configFile.getAbsolutePath());

        try (InputStream is = IOProviderFactory.getInputStream(configFile)) {
            return loadSettings(is);
        } catch (SAXException e) {
            Log.e(TAG, "SAXException while parsing: " + configFile, e);
        } catch (IOException e) {
            Log.e(TAG, "IOException while parsing: " + configFile, e);
        } catch (ParserConfigurationException e) {
            Log.e(TAG, "ParserConfigurationException while parsing: "
                    + configFile, e);
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse: " + configFile, e);
        }

        return new ArrayList<>();
    }

    /**
     * Loads the settings, and optionally performs a connection check to see if the
     * CotServiceRemote is running (leaving that in as legacy)
     * @return list of keys that were imported.
     */
    public List<String> loadSettings(InputStream is) throws Exception {
        // Do file opening here

        List<String> retval = new ArrayList<>();

        DocumentBuilderFactory dbf = XMLUtils.getDocumenBuilderFactory();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(is);

        ConnectionHolder[] connections = {
                new ConnectionHolder("cot_inputs", new HashMap<>()),
                new ConnectionHolder("cot_outputs", new HashMap<>()),
                new ConnectionHolder("cot_streams", new HashMap<>())
        };

        Node root = doc.getDocumentElement();
        NodeList preferences = root.getChildNodes();
        for (int i = 0; i < preferences.getLength(); i++) {
            Node preference = preferences.item(i);
            if (preference.getNodeName().equals("preference")) {

                String name = preference.getAttributes()
                        .getNamedItem("name").getNodeValue();
                Log.d(TAG, "name=" + name);

                switch (name) {
                    case "cot_inputs":
                        loadConnectionHolder(connections[0], preference);
                        break;
                    case "cot_outputs":
                        loadConnectionHolder(connections[1], preference);
                        break;
                    case "cot_streams":
                        loadConnectionHolder(connections[2], preference);
                        break;
                    default:
                        loadSettings(preference, name, retval);
                        break;
                }
            }
        }

        CotMapComponent cotMapCompoent = CotMapComponent.getInstance();

        for (ConnectionHolder connection : connections) {
            Map<String, String> mapping = connection.getContents();
            if (mapping == null || mapping.size() < 1) {
                continue;
            }

            String countString = mapping.get("count");
            if (FileSystemUtils.isEmpty(countString)) {
                continue;
            }
            int count = Integer.parseInt(countString);
            Log.d(TAG, "Loading " + count + " connections");
            for (int j = 0; j < count; j++) {
                String description = mapping.get("description" + j);
                String connectString = mapping
                        .get(TAKServer.CONNECT_STRING_KEY + j);
                boolean enabled = Boolean
                        .parseBoolean(mapping.get("enabled" + j));

                String strUseAuth = mapping.get("useAuth" + j);
                boolean useAuth = strUseAuth != null && Boolean
                        .parseBoolean(strUseAuth);
                String strCompress = mapping.get("compress" + j);
                boolean compress = strCompress != null && Boolean
                        .parseBoolean(strCompress);
                String cacheCreds = mapping.get("cacheCreds" + j);

                String caPassword = mapping.get("caPassword" + j);
                String clientPassword = mapping.get("clientPassword" + j);
                String caLocation = mapping.get("caLocation" + j);
                String certificateLocation = mapping.get("certificateLocation"
                        + j);

                boolean enrollForCertificateWithTrust = Boolean
                        .parseBoolean(mapping
                                .get("enrollForCertificateWithTrust" + j));

                String exp = mapping.get(TAKServer.EXPIRATION_KEY + j);
                long expiration = exp == null ? -1 : Long.parseLong(exp);

                Bundle data = new Bundle();
                data.putString("description", description);
                data.putBoolean("enabled", enabled);
                data.putBoolean("useAuth", useAuth);
                data.putBoolean("compress", compress);
                data.putString("cacheCreds", cacheCreds);

                data.putString("caPassword", caPassword);
                data.putString("clientPassword", clientPassword);
                data.putString("caLocation", caLocation);
                data.putString("certificateLocation", certificateLocation);

                data.putBoolean("enrollForCertificateWithTrust",
                        enrollForCertificateWithTrust);
                data.putLong(TAKServer.EXPIRATION_KEY, expiration);

                if (cotMapCompoent != null) {
                    Log.d(TAG, "Loading " + connection.getName()
                            + " connection " + connectString);
                    switch (connection.getName()) {
                        case "cot_inputs":
                            cotMapCompoent.getCotServiceRemote().addInput(
                                    connectString, data);
                            break;
                        case "cot_outputs":
                            cotMapCompoent.getCotServiceRemote().addOutput(
                                    connectString, data);
                            break;
                        case "cot_streams":
                            cotMapCompoent.getCotServiceRemote().addStream(
                                    connectString, data);
                            break;
                    }
                } else {
                    Log.d(TAG,
                            "CotMapComponent not yet loaded, using temp pref storage for "
                                    + connection.getName() + " connection "
                                    + connectString);
                    saveInputOutput(connection.getName(), connectString, data);
                }
            }
        }

        Intent prefLoaded = new Intent();
        prefLoaded.setAction("com.atakmap.app.PREFERENCES_LOADED");
        AtakBroadcast.getInstance().sendBroadcast(prefLoaded);
        return retval;
    }

    private void loadSettings(Node preference, String name,
            List<String> retval) {
        if (name.equals("com.atakmap.app_preferences")
                || name.equals("com.atakmap.civ_preferences")
                || name.equals("com.atakmap.fvey_preferences")) {

            name = DEFAULT_PREFERENCES_NAME;

            //import legacy prefs using current package
            Log.d(TAG, "Fixing up baseline prefs: " + name);
        }

        SharedPreferences pref = _context.getSharedPreferences(name,
                Context.MODE_PRIVATE);
        Editor editor = pref.edit();

        NodeList items = preference.getChildNodes();
        //Log.d(TAG, "#items=" + items.getLength());
        for (int i = 0; i < items.getLength(); i++) {
            Node entry = items.item(i);

            // Skip non-entry elements
            if (!entry.getNodeName().equals("entry"))
                continue;

            String key = decode(entry.getAttributes()
                    .getNamedItem("key").getNodeValue());
            String value = "";
            Node firstChild;
            if ((firstChild = entry
                    .getFirstChild()) != null)
                value = firstChild.getNodeValue();

            //Log.d(TAG, "key=" + key + " val=" + value);

            String className = entry.getAttributes()
                    .getNamedItem("class")
                    .getNodeValue();

            switch (className) {
                case "class java.lang.String":
                    editor.putString(key, decode(value));
                    retval.add(key);
                    break;
                case "class java.lang.Boolean":
                    editor.putBoolean(key, Boolean.parseBoolean(value));
                    retval.add(key);
                    break;
                case "class java.lang.Integer":
                    editor.putInt(key, Integer.parseInt(value));
                    retval.add(key);
                    break;
                case "class java.lang.Float":
                    editor.putFloat(key, Float.parseFloat(value));
                    retval.add(key);
                    break;
                case "class java.lang.Long":
                    editor.putLong(key, Long.parseLong(value));
                    retval.add(key);
                    break;
                default: {
                    // Special handling for string sets
                    if (isSetClass(className)) {
                        NodeList elements = entry.getChildNodes();
                        Set<String> valueSet = new HashSet<>();
                        for (int j = 0; j < elements.getLength(); j++) {
                            Node el = elements.item(j);
                            if (!el.getNodeName().equals("element"))
                                continue;
                            firstChild = el.getFirstChild();
                            if (firstChild != null)
                                valueSet.add(decode(firstChild.getNodeValue()));
                        }
                        editor.putStringSet(key, valueSet);
                        retval.add(key);
                    }
                    break;
                }
            }

            editor.apply();
        }
    }

    private int findSavedInputOutputIndex(SharedPreferences prefs,
            String connectString) {
        int index = -1;
        int count = prefs.getInt("count", 0);
        for (int i = 0; i < count; ++i) {
            if (connectString
                    .equals(prefs.getString(TAKServer.CONNECT_STRING_KEY + i,
                            ""))) {
                index = i;
                break;
            }
        }
        return index;
    }

    private void saveInputOutput(String prefsName, String connectString,
            Bundle data) {
        SharedPreferences prefs = _context.getSharedPreferences(prefsName,
                Context.MODE_PRIVATE);

        SharedPreferences.Editor editor = prefs.edit();
        int index = findSavedInputOutputIndex(prefs, connectString);
        if (index == -1) {
            index = prefs.getInt("count", 0);
            editor.putInt("count", index + 1);
            editor.putString(TAKServer.CONNECT_STRING_KEY + index,
                    connectString);
        }

        editor.putBoolean("enabled" + index,
                data.getBoolean("enabled", true));
        editor.putString("description" + index,
                data.getString("description"));
        editor.putBoolean("compress" + index,
                data.getBoolean("compress", false));

        editor.apply();
    }

    // Loads the partial settings
    public boolean loadPartialSettings(final Context context,
            final File configFile, final String[] categorySet) {
        // Do file opening here
        //Log.d(TAG, "Loading settings: " + configFile.getAbsolutePath());

        DocumentBuilderFactory dbf = XMLUtils.getDocumenBuilderFactory();

        Document doc;
        try (InputStream is = IOProviderFactory.getInputStream(configFile)) {
            DocumentBuilder db = dbf.newDocumentBuilder();
            doc = db.parse(is);
        } catch (SAXException e) {
            Log.e(TAG,
                    "SAXException while parsing: "
                            + configFile.getAbsolutePath(),
                    e);
            return false;
        } catch (IOException e) {
            Log.e(TAG,
                    "IOException while parsing: "
                            + configFile.getAbsolutePath(),
                    e);
            return false;
        } catch (ParserConfigurationException e) {
            Log.e(TAG, "ParserConfigurationException while parsing: "
                    + configFile.getAbsolutePath(), e);
            return false;
        }

        if (doc == null)
            return false;

        try {
            Node root = doc.getDocumentElement();
            NodeList preferences = root.getChildNodes();
            for (int i = 0; i < preferences.getLength(); i++) {
                final Node preference = preferences.item(i);
                if (preference.getNodeName().equals("preference")) {
                    String name = preference.getAttributes()
                            .getNamedItem("name").getNodeValue();
                    Log.d(TAG, "name=" + name);

                    // We are only interested in loading app preferences for ATAK
                    if (name.equals("com.atakmap.app_preferences")) {
                        // Get the shared preferences to edit
                        SharedPreferences pref = _context.getSharedPreferences(
                                name, Context.MODE_PRIVATE);
                        final Editor editor = pref.edit();

                        final CharSequence[] prefsList = {
                                "My Preferences -> Device",
                                "My Preferences -> Alternate Contact",
                                "My Preferences -> Reporting",
                                "Display Preferences -> Display",
                                "Network Preferences -> Network",
                                "Network Preferences -> Bluetooth",
                                "Tools Preferences -> Tools",
                                "Control Preferences -> Media",
                                "Control Preferences -> Stale Data",
                                "Control Preferences -> User Touch",
                                "Control Preferences -> Self Coordinate",
                                "Control Preferences -> Other",
                                "Control Preferences -> Debug"
                        };
                        boolean[] prefsListChoices = new boolean[prefsList.length];
                        final ArrayList<Integer> prefsSelectedList = new ArrayList<>();

                        // Build an alert dialog with a list of categories of preferences to load
                        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(
                                context);
                        alertBuilder
                                .setTitle(
                                        "Select which set of preferences to load");
                        alertBuilder
                                .setMultiChoiceItems(
                                        prefsList,
                                        prefsListChoices,
                                        new DialogInterface.OnMultiChoiceClickListener() {
                                            @Override
                                            public void onClick(
                                                    DialogInterface dialog,
                                                    int which,
                                                    boolean isChecked) {
                                                // If user selected any item in the list, add it to the selected list
                                                if (isChecked) {
                                                    prefsSelectedList
                                                            .add(which);
                                                } else if (prefsSelectedList
                                                        .contains(which)) {
                                                    // User selected something that was checked already, this means remove
                                                    prefsSelectedList
                                                            .remove(Integer
                                                                    .valueOf(
                                                                            which));
                                                }
                                            }
                                        });

                        // Set a listener on the OK button
                        alertBuilder.setPositiveButton(R.string.ok,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog,
                                            int which) {
                                        for (int i = 0; i < prefsSelectedList
                                                .size(); i++) {
                                            // Cycle through list to see which preferences were added to load
                                            Log.d(TAG,
                                                    "Selected "
                                                            + prefsList[prefsSelectedList
                                                                    .get(i)]);
                                        }
                                        dialog.dismiss();

                                        Log.d(TAG,
                                                "Moving on to check preference selection list");

                                        // If no selections were made, then return
                                        if (prefsSelectedList.isEmpty()) {
                                            Log.d(TAG,
                                                    "No preferences were selected");
                                            return;
                                        }

                                        // Now that we have our selected list, check against the nodelist of items for
                                        // matches and allow matches to proceed with preference load
                                        NodeList items = preference
                                                .getChildNodes();
                                        List<Node> matchedItems = new ArrayList<>();
                                        for (int j = 0; j < items
                                                .getLength(); j++) {
                                            Node entry = items.item(j);
                                            if (entry.getNodeName().equals(
                                                    "entry")) {
                                                String key = entry
                                                        .getAttributes()
                                                        .getNamedItem("key")
                                                        .getNodeValue();
                                                String value = "";
                                                Node firstChild;
                                                if ((firstChild = entry
                                                        .getFirstChild()) != null)
                                                    value = firstChild
                                                            .getNodeValue();
                                                // now check the key against the partial selection
                                                for (int i = 0; i < prefsSelectedList
                                                        .size(); i++) {
                                                    final CharSequence pref = prefsList[prefsSelectedList
                                                            .get(i)];
                                                    if (pref == null)
                                                        continue;

                                                    // TODO: This code is completely broken when it comes to
                                                    // translation.   For right now just protect againt possible
                                                    // null pointer issues
                                                    switch (pref.toString()) {
                                                        case "My Preferences -> Device":
                                                            // Check against device preferences
                                                            String[] devicePreferencesList = _context
                                                                    .getResources()
                                                                    .getStringArray(
                                                                            R.array.device_preferences);
                                                            for (String prefs : devicePreferencesList) {
                                                                if (key.equals(
                                                                        prefs)) {
                                                                    // Found a match, add to matched list
                                                                    matchedItems
                                                                            .add(items
                                                                                    .item(j));
                                                                }
                                                            }
                                                            break;
                                                        case "My Preferences -> Alternate Contact":
                                                            // Check against alternate contact preferences
                                                            String[] alternateContactPreferencesList = _context
                                                                    .getResources()
                                                                    .getStringArray(
                                                                            R.array.alternate_contact_preferences);
                                                            for (String prefs : alternateContactPreferencesList) {
                                                                if (key.equals(
                                                                        prefs)) {
                                                                    // Found a match, add to matched list
                                                                    matchedItems
                                                                            .add(items
                                                                                    .item(j));
                                                                }
                                                            }
                                                            break;
                                                        case "My Preferences -> Reporting":
                                                            // Check against reporting preferences
                                                            String[] reportingPreferencesList = _context
                                                                    .getResources()
                                                                    .getStringArray(
                                                                            R.array.reporting_preferences);
                                                            for (String prefs : reportingPreferencesList) {
                                                                if (key.equals(
                                                                        prefs)) {
                                                                    // Found a match, add to matched list
                                                                    matchedItems
                                                                            .add(items
                                                                                    .item(j));
                                                                }
                                                            }
                                                            break;
                                                        case "Display Preferences -> Display":
                                                            // Check against display preferences
                                                            String[] displayPreferencesList = _context
                                                                    .getResources()
                                                                    .getStringArray(
                                                                            R.array.display_preferences);
                                                            for (String prefs : displayPreferencesList) {
                                                                if (key.equals(
                                                                        prefs)) {
                                                                    // Found a match, add to matched list
                                                                    matchedItems
                                                                            .add(items
                                                                                    .item(j));
                                                                }
                                                            }
                                                            break;
                                                        case "Network Preferences -> Network":
                                                            // Check against network preferences
                                                            String[] networkPreferencesList = _context
                                                                    .getResources()
                                                                    .getStringArray(
                                                                            R.array.network_preferences);
                                                            for (String prefs : networkPreferencesList) {
                                                                if (key.equals(
                                                                        prefs)) {
                                                                    // Found a match, add to matched list
                                                                    matchedItems
                                                                            .add(items
                                                                                    .item(j));
                                                                }
                                                            }
                                                            break;
                                                        case "Network Preferences -> Bluetooth":
                                                            // Check against bluetooth preferences
                                                            String[] bluetoothPreferencesList = _context
                                                                    .getResources()
                                                                    .getStringArray(
                                                                            R.array.bluetooth_preferences);
                                                            for (String prefs : bluetoothPreferencesList) {
                                                                if (key.equals(
                                                                        prefs)) {
                                                                    // Found a match, add to matched list
                                                                    matchedItems
                                                                            .add(items
                                                                                    .item(j));
                                                                }
                                                            }
                                                            break;
                                                        case "Tools Preferences -> Tools":
                                                            // Check against tools preferences
                                                            String[] toolsPreferencesList = _context
                                                                    .getResources()
                                                                    .getStringArray(
                                                                            R.array.tools_preferences);
                                                            for (String prefs : toolsPreferencesList) {
                                                                if (key.equals(
                                                                        prefs)) {
                                                                    // Found a match, add to matched list
                                                                    matchedItems
                                                                            .add(items
                                                                                    .item(j));
                                                                }
                                                            }
                                                            break;
                                                        case "Control Preferences -> Media":
                                                            // Check against control preferences
                                                            String[] mediaPreferencesList = _context
                                                                    .getResources()
                                                                    .getStringArray(
                                                                            R.array.media_preferences);
                                                            for (String prefs : mediaPreferencesList) {
                                                                if (key.equals(
                                                                        prefs)) {
                                                                    // Found a match, add to matched list
                                                                    matchedItems
                                                                            .add(items
                                                                                    .item(j));
                                                                }
                                                            }
                                                            break;
                                                        case "Control Preferences -> Stale Data":
                                                            // Check against control preferences
                                                            String[] stalePreferencesList = _context
                                                                    .getResources()
                                                                    .getStringArray(
                                                                            R.array.stale_preferences);
                                                            for (String prefs : stalePreferencesList) {
                                                                if (key.equals(
                                                                        prefs)) {
                                                                    // Found a match, add to matched list
                                                                    matchedItems
                                                                            .add(items
                                                                                    .item(j));
                                                                }
                                                            }
                                                            break;
                                                        case "Control Preferences -> User Touch":
                                                            // Check against control preferences
                                                            String[] userPreferencesList = _context
                                                                    .getResources()
                                                                    .getStringArray(
                                                                            R.array.user_preferences);
                                                            for (String prefs : userPreferencesList) {
                                                                if (key.equals(
                                                                        prefs)) {
                                                                    // Found a match, add to matched list
                                                                    matchedItems
                                                                            .add(items
                                                                                    .item(j));
                                                                }
                                                            }
                                                            break;
                                                        case "Control Preferences -> Self Coordinate":
                                                            // Check against control preferences
                                                            String[] selfPreferencesList = _context
                                                                    .getResources()
                                                                    .getStringArray(
                                                                            R.array.self_preferences);
                                                            for (String prefs : selfPreferencesList) {
                                                                if (key.equals(
                                                                        prefs)) {
                                                                    // Found a match, add to matched list
                                                                    matchedItems
                                                                            .add(items
                                                                                    .item(j));
                                                                }
                                                            }
                                                            break;
                                                        case "Control Preferences -> Other":
                                                            // Check against control preferences
                                                            String[] otherPreferencesList = _context
                                                                    .getResources()
                                                                    .getStringArray(
                                                                            R.array.other_preferences);
                                                            for (String prefs : otherPreferencesList) {
                                                                if (key.equals(
                                                                        prefs)) {
                                                                    // Found a match, add to matched list
                                                                    matchedItems
                                                                            .add(items
                                                                                    .item(j));
                                                                }
                                                            }
                                                            break;
                                                        case "Control Preferences -> Debug":
                                                            // Check against control preferences
                                                            String[] debugPreferencesList = _context
                                                                    .getResources()
                                                                    .getStringArray(
                                                                            R.array.debug_preferences);
                                                            for (String prefs : debugPreferencesList) {
                                                                if (key.equals(
                                                                        prefs)) {
                                                                    // Found a match, add to matched list
                                                                    matchedItems
                                                                            .add(items
                                                                                    .item(j));
                                                                }
                                                            }
                                                            break;
                                                    }
                                                }
                                            }
                                        }

                                        Log.d(TAG,
                                                "#items="
                                                        + matchedItems.size());
                                        for (int j = 0; j < matchedItems
                                                .size(); j++) {
                                            Node entry = matchedItems.get(j);
                                            if (entry.getNodeName().equals(
                                                    "entry")) {
                                                String key = entry
                                                        .getAttributes()
                                                        .getNamedItem("key")
                                                        .getNodeValue();
                                                String value = "";
                                                Node firstChild;
                                                if ((firstChild = entry
                                                        .getFirstChild()) != null)
                                                    value = firstChild
                                                            .getNodeValue();
                                                //Log.d(TAG, "key=" + key + " val=" + value);
                                                switch (entry
                                                        .getAttributes()
                                                        .getNamedItem("class")
                                                        .getNodeValue()) {
                                                    case "class java.lang.String":
                                                        editor.putString(key,
                                                                value);
                                                        break;
                                                    case "class java.lang.Boolean":
                                                        editor.putBoolean(
                                                                key,
                                                                Boolean.parseBoolean(
                                                                        value));
                                                        break;
                                                    case "class java.lang.Integer":
                                                        editor.putInt(key,
                                                                Integer
                                                                        .parseInt(
                                                                                value));
                                                        break;
                                                    case "class java.lang.Float":
                                                        editor.putFloat(key,
                                                                Float
                                                                        .parseFloat(
                                                                                value));
                                                        break;
                                                    case "class java.lang.Long":
                                                        editor.putLong(key, Long
                                                                .parseLong(
                                                                        value));
                                                        break;
                                                }
                                            }
                                            editor.apply();
                                        }
                                    }
                                });
                        alertBuilder.setNegativeButton(R.string.cancel, null);
                        AlertDialog partialPreferenceDialog = alertBuilder
                                .create();
                        partialPreferenceDialog.show();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse: " + configFile.getAbsolutePath(), e);
            return false;
        }

        Intent prefLoaded = new Intent();
        prefLoaded.setAction("com.atakmap.app.PREFERENCES_LOADED");
        AtakBroadcast.getInstance().sendBroadcast(prefLoaded);
        return true;
    }

    private void loadConnectionHolder(ConnectionHolder holder,
            Node preference) {
        NodeList items = preference.getChildNodes();
        Log.d(TAG, "connection #items=" + items.getLength());
        for (int j = 0; j < items.getLength(); j++) {
            Node entry = items.item(j);
            if (entry.getNodeName().equals("entry")) {
                String key = entry.getAttributes().getNamedItem("key")
                        .getNodeValue();
                String value = "";
                Node firstChild;
                if ((firstChild = entry.getFirstChild()) != null)
                    value = firstChild.getNodeValue();

                //Log.d(TAG, "connection key=" + key + " val=" + value);
                holder.getContents().put(key, value);
            }
        }
    }

    private static class ConnectionHolder {
        private final Map<String, String> _contents;
        private final String _name;

        ConnectionHolder(String name, Map<String, String> contents) {
            _name = name;
            _contents = contents;
        }

        public String getName() {
            return _name;
        }

        public Map<String, String> getContents() {
            return _contents;
        }
    }

    @Override
    public void onCotServiceConnected(Bundle fullServiceState) {
        connected = true;
    }

    @Override
    public void onCotServiceDisconnected() {
        connected = false;

    }

    /**
     * Check if the given classname implements {@link Set}
     * @param className Class name
     * @return True if the class implements {@link Set}
     */
    private static boolean isSetClass(String className) {
        try {
            if (className.startsWith("class "))
                className = className.substring(6);
            Class<?> clazz = Class.forName(className);
            return Set.class.isAssignableFrom(clazz);
        } catch (Exception e) {
            return false;
        }
    }


    /**
     * This method is private to enable easier backporting to prior versions.   This
     * encodes the following into the appropriate unicode
     * "   &quot;   \u0022
     * '   &apos;   \u0027
     * <   &lt;     \u003c
     * >   &gt;     \u003e
     * &   &amp;    \u0026
     * @param v the unencoded string
     * @return the encoded string
     */
    private String encode(final String v) {
        return v.replaceAll("\"", "\\\\u0022")
                .replaceAll("'", "\\\\u0027")
                .replaceAll("<", "\\\\u003c")
                .replaceAll(">", "\\\\u003e")
                .replaceAll("&", "\\\\u0026");
    }

    /**
     * This method is private to enable easier backporting to prior versions.   This
     * decodes the unicode into the appropriate value
     * "   &quot;   \u0022
     * '   &apos;   \u0027
     * <   &lt;     \u003c
     * >   &gt;     \u003e
     * &   &amp;    \u0026
     * @param v the encoded string
     * @return the unencoded string
     */
    private String decode(String v) {
        return v.replaceAll("\\\\u0022", "\"")
                .replaceAll("\\\\u0027", "'")
                .replaceAll("\\\\u003c", "<")
                .replaceAll("\\\\u003e", ">")
                .replaceAll("\\\\u0026", "&");
    }
}
