
package com.atakmap.android.contact;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.location.LocationMapComponent;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Searches for components to handle communications for contacts
 *  Including, but not limited to: XMPP, VOIP, EMAIL, PHONE, SMS
 *
 * Provides a default implementation for several connectors
 *
 *
 */
public class ContactConnectorManager {

    private final static String TAG = "ContactConnectorManager";

    private static final String CONNECTOR_DEFAULT_PREFIX = "contact.connector.default.";
    private static final String CONNECTOR_LASTUSED_PREFIX = "contact.connector.lastused.";

    /**
     * Optional features supported by ContactConnectorHandler implementations
     *  Profile provided as ActionBroadcastData, e.g. an intent to launch
     *  Avatar provided as an AvatarFeature
     *  NotificationCount provided as an Integer
     *  Presence provided as a Contact.UpdateStatus
     */
    public enum ConnectorFeature {
        Profile,
        Avatar,
        NotificationCount,
        Presence
    }

    /**
     * Handler to filter and process connectors
     */
    public static abstract class ContactConnectorHandler {

        /**
         * Check if this handler supports the connector
         *
         * @param connector the connector
         * @return true if it is supported
         */
        public boolean isSupported(Connector connector) {
            return isSupported(connector.getConnectionType());
        }

        /**
         * Check if this handler supports the connector type
         *
         * @param connectorType the connector type
         * @return true if it is supported
         */
        public abstract boolean isSupported(String connectorType);

        /**
         * Check if this handler supports the feature
         *
         * @param feature the connector feature
         * @return true if the contact manager supports that connector feature.
         */
        public abstract boolean hasFeature(ConnectorFeature feature);

        /**
         * Provide the human-readable handler name/title
         * @return handler name
         */
        public abstract String getName();

        /**
         * Provide human readable description of the handler
         *
         * @return the description of the handler
         */
        public abstract String getDescription();

        /**
         * Happens on UI thread, so please be quick
         * At least one of these two identifiers is required
         *
         * @param connectorType the type of connector
         * @param contactUID the contact identifier
         * @param connectorAddress the address
         * @return true if the connection manager can handle the contact
         */
        public abstract boolean handleContact(String connectorType,
                String contactUID, String connectorAddress);

        /**
         * Get data for the specified feature
         *
         * @return
         */
        public abstract Object getFeature(String connectorType,
                ConnectorFeature feature, String contactUID,
                String connectorAddress);
    }

    private final Context _context;
    private final SharedPreferences _prefs;

    /**
     * Currently support one dynamic handler per contact type
     */
    private final List<ContactConnectorHandler> _contactHandlers;

    /**
     * Have one default handler per contact type
     */
    private final List<ContactConnectorHandler> _defaultHandlers;

    public ContactConnectorManager(Context context,
            SharedPreferences preferences) {
        _context = context;
        _prefs = preferences;

        _contactHandlers = new ArrayList<>();

        _defaultHandlers = new ArrayList<>();
        _defaultHandlers.add(new PhoneHandler());
        _defaultHandlers.add(new SMSHandler());
        _defaultHandlers.add(new VOIPHandler());
        _defaultHandlers.add(new XMPPHandler());
        _defaultHandlers.add(new EmailHandler());
    }

    public synchronized void addContactHandler(
            ContactConnectorHandler handler) {
        if (handler == null) {
            Log.w(TAG, "Invalid contact handler");
            return;
        }

        if (!_contactHandlers.contains(handler)) {
            _contactHandlers.add(handler);
            Log.d(TAG, "Adding contact handler class: "
                    + handler.getClass().getName());
        }
    }

    /**
     * Remove a contact handler that has been registered with the ContactConnectorManager.
     * @param handler the registered contact handler.
     */
    public synchronized void removeContactHandler(
            ContactConnectorHandler handler) {
        if (handler == null) {
            Log.w(TAG, "Invalid contact handler");
            return;
        }
        _contactHandlers.remove(handler);
        Log.d(TAG, "Removing contact handler class: "
                + handler.getClass().getName());

    }

    /**
     * Attempt to initiate contact with the specified address
     * First searches for registered components that support the specified type
     * Then falls back on default implementation if none is found or none is successful in
     * initiating contact
     *
     * @param contact the contact to initate contact with
     * @param connector the connector to use
     * @return true if the initiation was sucessful otherwise false
     */
    public synchronized boolean initiateContact(IndividualContact contact,
            Connector connector) {
        if (contact == null || connector == null) {
            Log.w(TAG,
                    "Contact or Connector not set correctly, unable to initateContact "
                            +
                            contact + ": " + connector);
            return false;
        }

        return initiateContact(connector.getConnectionType(), contact.getUID(),
                connector.getConnectionString());
    }

    /**
     * Attempt to initiate contact with the specified address
     * First searches for registered components that support the specified type
     * Then falls back on default implementation if none is found or none is successful in
     * initiating contact
     *
     * @param connectorType     required
     * @param contactUID        UID or address is required
     * @param connectorAddress  UID or address is required
     * @return true if initiation was successful otherwise false
     */
    public synchronized boolean initiateContact(String connectorType,
            String contactUID, String connectorAddress) {
        if (FileSystemUtils.isEmpty(contactUID)
                && FileSystemUtils.isEmpty(connectorAddress)) {
            Log.w(TAG, "No UID or address provided for: " + connectorType);
            Toast.makeText(_context,
                    "No contact address provided...",
                    Toast.LENGTH_SHORT).show();

            AtakBroadcast.getInstance().sendBroadcast(
                    new Intent("com.atakmap.android.maps.HIDE_MENU"));

            return false;
        }

        Log.d(TAG, "Searching a handler for " + connectorType + ": "
                + connectorAddress + "...");
        for (ContactConnectorHandler handler : _contactHandlers) {
            if (handler.isSupported(connectorType)
                    && handler.handleContact(connectorType, contactUID,
                            connectorAddress)) {
                Log.d(TAG, "" + connectorType + ": " + connectorAddress
                        + ", handled by dynamic: "
                        + handler.getClass().getName());
                updatedContactPrefs(_prefs, contactUID, connectorType);
                AtakBroadcast.getInstance().sendBroadcast(
                        new Intent("com.atakmap.android.maps.HIDE_MENU"));
                return true;
            }
        }

        Log.d(TAG, "No registered handler for " + connectorType + ": "
                + connectorAddress + ", searching for external activities");
        for (ContactConnectorHandler handler : _defaultHandlers) {
            if (handler.isSupported(connectorType)
                    && handler.handleContact(connectorType, contactUID,
                            connectorAddress)) {
                Log.d(TAG, "" + connectorType + ": " + connectorAddress
                        + ", handled by default: "
                        + handler.getClass().getName());
                updatedContactPrefs(_prefs, contactUID, connectorType);
                AtakBroadcast.getInstance().sendBroadcast(
                        new Intent("com.atakmap.android.maps.HIDE_MENU"));
                return true;
            }
        }

        Log.w(TAG,
                "No registered handler found for " + connectorType
                        + ": "
                        + connectorAddress);
        return false;
    }

    /**
     * Find a handler for the specified connector type
     * First searches for registered components that support the specified type
     * Then falls back on default implementation if none is found or none is successful in
     * initiating contact
     *
     * @param connectorType     required
     * @return the handler
     */
    public synchronized ContactConnectorHandler getHandler(
            String connectorType) {
        Log.d(TAG, "Searching a handler for " + connectorType);
        for (ContactConnectorHandler handler : _contactHandlers) {
            if (handler.isSupported(connectorType)) {
                Log.d(TAG, "" + connectorType + ": is supported by dynamic: "
                        + handler.getClass().getName());
                return handler;
            }
        }

        Log.d(TAG, "No registered handler for " + connectorType
                + ", searching for external activities");
        for (ContactConnectorHandler handler : _defaultHandlers) {
            if (handler.isSupported(connectorType)) {
                Log.d(TAG, "" + connectorType + ": is supported by default: "
                        + handler.getClass().getName());
                return handler;
            }
        }

        Log.w(TAG,
                "No registered handler found for " + connectorType);
        return null;
    }

    /**
     * Find a handler by its name or title
     * @param connectorName Connector name
     * @return Handler with connector name or null if not found
     */
    public synchronized ContactConnectorHandler getHandlerByName(
            String connectorName) {
        for (ContactConnectorHandler handler : _contactHandlers) {
            if (handler.getName().equals(connectorName))
                return handler;
        }
        for (ContactConnectorHandler handler : _defaultHandlers) {
            if (handler.getName().equals(connectorName))
                return handler;
        }
        Log.w(TAG, "No registered handler found for " + connectorName);
        return null;
    }

    /**
     * Return all registered connector handlers
     * Handlers are uniquely keyed by name, where registered
     * handlers take priority over the defaults
     * @return List of connector handlers
     */
    public synchronized List<ContactConnectorHandler> getHandlers() {
        Map<String, ContactConnectorHandler> ret = new HashMap<>();
        // Defaults first
        for (ContactConnectorHandler handler : _defaultHandlers)
            ret.put(handler.getName(), handler);
        // Then registered (so plugins can overwrite any defaults)
        for (ContactConnectorHandler handler : _contactHandlers)
            ret.put(handler.getName(), handler);
        // For convenience, sort registered first then defaults alphabetically
        List<ContactConnectorHandler> sorted = new ArrayList<>(
                ret.values());
        final List<ContactConnectorHandler> defaults = new ArrayList<>(
                _defaultHandlers);
        final List<ContactConnectorHandler> registered = new ArrayList<>(
                _contactHandlers);
        Collections.sort(sorted, new Comparator<ContactConnectorHandler>() {
            @Override
            public int compare(ContactConnectorHandler lhs,
                    ContactConnectorHandler rhs) {
                if (registered.contains(lhs) && defaults.contains(rhs))
                    return -1;
                else if (defaults.contains(lhs) && registered.contains(rhs))
                    return 1;
                return lhs.getName().compareTo(rhs.getName());
            }
        });
        return sorted;
    }

    /**
     * Get list of connectors which have at least one handler which supports the specified feature,
     * for the given contact
     *
     * e.g. is an avatar available for the contact
     *
     * @param contact
     * @param feature
     * @return
     */
    public synchronized Collection<Connector> getConnectors(
            IndividualContact contact, ConnectorFeature feature) {
        Set<Connector> matching = new HashSet<>();

        //loop connectors for this contact
        for (Connector connector : contact.getConnectors(false)) {
            boolean bFound = false;

            //see if we can find a handler for this connector which supports the feature
            for (ContactConnectorHandler handler : _contactHandlers) {
                if (handler.isSupported(connector.getConnectionType())
                        && handler.hasFeature(feature)) {
                    //                    Log.d(TAG, "" + connector.getConnectionType()
                    //                            + ": has feature: " + feature
                    //                            + ", by dynamic: "
                    //                            + handler.getClass().getName());
                    matching.add(connector);
                    bFound = true;
                    break;
                }
            }

            if (bFound)
                continue;

            for (ContactConnectorHandler handler : _defaultHandlers) {
                if (handler.isSupported(connector.getConnectionType())
                        && handler.hasFeature(feature)) {
                    //                    Log.d(TAG, "" + connector.getConnectionType()
                    //                            + ": has feature: " + feature
                    //                            + ", by default: "
                    //                            + handler.getClass().getName());
                    matching.add(connector);
                    break;
                }
            }
        }

        return matching;
    }

    /**
     * Get features for the specified contact
     *
     * @param contact
     * @param feature
     * @param max   limit number of features
     * @return
     */
    public synchronized List<Object> getFeatures(IndividualContact contact,
            ConnectorFeature feature, int max) {
        List<Object> ret = new ArrayList<>();
        if (max < 1 || contact == null)
            return ret;

        //loop connectors for this contact
        for (Connector connector : contact.getConnectors(false)) {

            //see if we can find a handler for this connector which supports the feature
            for (ContactConnectorHandler handler : _contactHandlers) {
                if (handler.isSupported(connector.getConnectionType())
                        && handler.hasFeature(feature)) {
                    //                    Log.d(TAG, "" + connector.getConnectionType()
                    //                            + ": providing feature: " + feature
                    //                            + ", by dynamic: "
                    //                            + handler.getClass().getName());
                    Object obj = handler.getFeature(
                            connector.getConnectionType(),
                            feature, contact.getUID(),
                            connector.getConnectionString());
                    if (obj != null) {
                        ret.add(obj);
                        if (ret.size() >= max)
                            return ret;
                    } else {
                        //                        Log.w(TAG, "Feature not provided: " + feature);
                    }
                }
            }

            for (ContactConnectorHandler handler : _defaultHandlers) {
                if (handler.isSupported(connector.getConnectionType())
                        && handler.hasFeature(feature)) {
                    //                    Log.d(TAG, "" + connector.getConnectionType()
                    //                            + ": providing feature: " + feature
                    //                            + ", by default: "
                    //                            + handler.getClass().getName());
                    Object obj = handler.getFeature(
                            connector.getConnectionType(),
                            feature, contact.getUID(),
                            connector.getConnectionString());
                    if (obj != null) {
                        ret.add(obj);
                        if (ret.size() >= max)
                            return ret;
                    } else {
                        //                        Log.w(TAG, "Feature not provided: " + feature);
                    }
                }
            }
        }

        return ret;
    }

    /**
     * Get feature for the specified contact's connector
     *
     * @param contact the contact to be used
     * @param connector the connector
     * @param feature the feature desired
     * @return the feature for the specified contact/connector
     */
    public synchronized Object getFeature(IndividualContact contact,
            Connector connector, ConnectorFeature feature) {
        if (contact == null || connector == null)
            return null;

        //see if we can find a handler for this connector which supports the feature
        for (ContactConnectorHandler handler : _contactHandlers) {
            if (handler.isSupported(connector.getConnectionType())
                    && handler.hasFeature(feature)) {
                //                Log.d(TAG, "" + connector.getConnectionType()
                //                        + ": providing feature: " + feature
                //                        + ", by dynamic: "
                //                        + handler.getClass().getName());
                Object obj = handler.getFeature(connector.getConnectionType(),
                        feature, contact.getUID(),
                        connector.getConnectionString());
                if (obj != null) {
                    return obj;
                } else {
                    //                    Log.w(TAG, "Feature not provided: " + feature);
                }
            }
        }

        for (ContactConnectorHandler handler : _defaultHandlers) {
            if (handler.isSupported(connector.getConnectionType())
                    && handler.hasFeature(feature)) {
                //                Log.d(TAG, "" + connector.getConnectionType()
                //                        + ": providing feature: " + feature
                //                        + ", by default: "
                //                        + handler.getClass().getName());
                Object obj = handler.getFeature(connector.getConnectionType(),
                        feature, contact.getUID(),
                        connector.getConnectionString());
                if (obj != null) {
                    return obj;
                } else {
                    //                    Log.w(TAG, "Feature not provided: " + feature);
                }
            }
        }

        return null;
    }

    private static void updatedContactPrefs(SharedPreferences prefs,
            String contactUID, String connectorType) {
        prefs.edit()
                .putString(CONNECTOR_DEFAULT_PREFIX + contactUID, connectorType)
                .putLong(
                        CONNECTOR_LASTUSED_PREFIX + contactUID + connectorType,
                        System.currentTimeMillis())
                .apply();
    }

    public static long getLastUsed(SharedPreferences prefs, String contactUID,
            String connectorType) {
        return prefs.getLong(CONNECTOR_LASTUSED_PREFIX + contactUID
                + connectorType, -1);
    }

    public static String getDefaultConnectorType(SharedPreferences prefs,
            String contactUID) {
        return prefs.getString(CONNECTOR_DEFAULT_PREFIX + contactUID, null);
    }

    private class PhoneHandler extends ContactConnectorHandler {

        @Override
        public boolean isSupported(String type) {
            return FileSystemUtils.isEquals(type,
                    TelephoneConnector.CONNECTOR_TYPE);
        }

        @Override
        public boolean handleContact(String connectorType, String contactUID,
                String intentTelephone) {

            if (!LocationMapComponent.isValidTelephoneNumber(intentTelephone)) {
                Log.w(TAG, "Cannot call invalid number: " + intentTelephone);
                return false;
            }

            String uri = "tel:" + intentTelephone;
            Intent callIntent = new Intent(Intent.ACTION_DIAL,
                    Uri.parse(uri));
            if (callIntent.resolveActivity(_context
                    .getPackageManager()) != null) {
                Log.d(TAG, "Calling: " + uri + ", for: " + contactUID);
                try {
                    _context.startActivity(callIntent);
                    return true;
                } catch (Exception e) {
                    Log.e(TAG, "Failed to launch call app: " + uri,
                            e);
                    Toast.makeText(_context,
                            "No Phone app installed...",
                            Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.w(TAG, "Unable to find call app: " + uri);
                Toast.makeText(_context,
                        "No Phone app installed...",
                        Toast.LENGTH_SHORT).show();
            }

            return false;
        }

        @Override
        public boolean hasFeature(ConnectorFeature feature) {
            return false;
        }

        @Override
        public Object getFeature(String connectorType,
                ConnectorFeature feature, String contactUID,
                String connectorAddress) {
            return null;
        }

        @Override
        public String getName() {
            return _context.getString(R.string.connector_phone);
        }

        @Override
        public String getDescription() {
            return _context.getString(R.string.app_name)
                    + " allows the user to select an external app to places phone calls";
        }
    }

    private class SMSHandler extends ContactConnectorHandler {

        @Override
        public boolean isSupported(String type) {
            return FileSystemUtils.isEquals(type, SmsConnector.CONNECTOR_TYPE);
        }

        @Override
        public boolean handleContact(String connectorType, String contactUID,
                String intentTelephone) {
            if (!LocationMapComponent.isValidTelephoneNumber(intentTelephone)) {
                Log.w(TAG, "Cannot text invalid number: " + intentTelephone);
                return false;
            }

            String uri = "smsto:" + intentTelephone;
            Uri smsUri = Uri.parse(uri);

            Intent smsIntent = new Intent(Intent.ACTION_SENDTO,
                    smsUri);
            if (smsIntent.resolveActivity(_context
                    .getPackageManager()) != null) {
                Log.d(TAG, "SMS to: " + uri + ", for: " + contactUID);
                try {
                    _context.startActivity(smsIntent);
                    return true;
                } catch (Exception e) {
                    Log.e(TAG, "Failed to launch call app: " + uri,
                            e);
                    Toast.makeText(_context,
                            "No Text app installed...",
                            Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.w(TAG, "Unable to find sms app: " + uri);
                Toast.makeText(_context,
                        "No Text app installed...",
                        Toast.LENGTH_SHORT).show();
            }

            return false;
        }

        @Override
        public boolean hasFeature(ConnectorFeature feature) {
            return false;
        }

        @Override
        public Object getFeature(String connectorType,
                ConnectorFeature feature, String contactUID,
                String connectorAddress) {
            return null;
        }

        @Override
        public String getName() {
            return _context.getString(R.string.connector_sms);
        }

        @Override
        public String getDescription() {
            return _context.getString(R.string.app_name)
                    + " allows the user to select an external app to send SMS text messages";
        }
    }

    private class VOIPHandler extends ContactConnectorHandler {

        @Override
        public boolean isSupported(String type) {
            return FileSystemUtils.isEquals(type, VoIPConnector.CONNECTOR_TYPE);
        }

        @Override
        public boolean handleContact(String connectorType, String contactUID,
                String intentSipAddress) {

            String uri = "sip:" + intentSipAddress;
            Intent voipIntent = new Intent(Intent.ACTION_VIEW, Uri
                    .parse(uri));

            if (voipIntent.resolveActivity(_context
                    .getPackageManager()) != null) {
                Log.d(TAG, "VoIP calling: " + uri + ", for: " + contactUID);
                try {
                    _context.startActivity(voipIntent);
                    return true;
                } catch (Exception e) {
                    Log.e(TAG, "Failed to launch voip app: " + uri,
                            e);
                    Toast.makeText(_context,
                            "No VoIP app installed...",
                            Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.w(TAG,
                        "Unable to find voip app: " + uri);
                Toast.makeText(_context,
                        "No VoIP app installed...",
                        Toast.LENGTH_SHORT).show();
            }

            return false;
        }

        @Override
        public boolean hasFeature(ConnectorFeature feature) {
            return false;
        }

        @Override
        public Object getFeature(String connectorType,
                ConnectorFeature feature, String contactUID,
                String connectorAddress) {
            return null;
        }

        @Override
        public String getName() {
            return _context.getString(R.string.connector_voip);
        }

        @Override
        public String getDescription() {
            return _context.getString(R.string.app_name)
                    + " allows the user to select an external app to place VoIP calls";
        }
    }

    private class XMPPHandler extends ContactConnectorHandler {

        @Override
        public boolean isSupported(String type) {
            return FileSystemUtils.isEquals(type, XmppConnector.CONNECTOR_TYPE);
        }

        @Override
        public boolean handleContact(String connectorType, String contactUID,
                String xmppIntentUsername) {

            SharedPreferences preferences = PreferenceManager
                    .getDefaultSharedPreferences(_context);

            //use imto://jabber/username, but allow customization via .pref
            String scheme = preferences.getString(
                    "xmppIntentScheme", "imto");
            String authority = preferences.getString(
                    "xmppIntentAuthority", "jabber");
            Uri uri = new Uri.Builder().scheme(scheme)
                    .authority(authority).appendPath(xmppIntentUsername)
                    .build();
            Intent xmppIntent = new Intent(Intent.ACTION_SENDTO,
                    uri);

            if (xmppIntent.resolveActivity(_context
                    .getPackageManager()) != null) {
                Log.d(TAG, "XMPP chatting: " + uri + ", for: " + contactUID);
                try {
                    _context.startActivity(xmppIntent);
                    return true;
                } catch (Exception e) {
                    Log.e(TAG, "Failed to launch xmpp app: " + uri,
                            e);
                    Toast.makeText(_context,
                            "No XMPP app installed...",
                            Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.w(TAG,
                        "Unable to find xmpp app: " + uri);
                Toast.makeText(_context,
                        "No XMPP app installed...",
                        Toast.LENGTH_SHORT).show();
            }

            return false;
        }

        @Override
        public boolean hasFeature(ConnectorFeature feature) {
            return false;
        }

        @Override
        public Object getFeature(String connectorType,
                ConnectorFeature feature, String contactUID,
                String connectorAddress) {
            return null;
        }

        @Override
        public String getName() {
            return _context.getString(R.string.connector_xmpp);
        }

        @Override
        public String getDescription() {
            return _context.getString(R.string.app_name)
                    + " allows the user to select an external app to send (server-based) XMPP chat messages";
        }
    }

    private class EmailHandler extends ContactConnectorHandler {

        @Override
        public boolean isSupported(String type) {
            return FileSystemUtils
                    .isEquals(type, EmailConnector.CONNECTOR_TYPE);
        }

        @Override
        public boolean handleContact(String connectorType, String contactUID,
                String intentSipAddress) {
            return composeEmail(contactUID, intentSipAddress,
                    _context.getString(R.string.app_name) + " email");
        }

        private boolean composeEmail(String contactUID, String address,
                String subject) {
            Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
            emailIntent.setData(Uri.parse("mailto:"));
            emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[] {
                    address
            });
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
            if (emailIntent.resolveActivity(_context
                    .getPackageManager()) != null) {
                Log.d(TAG, "Sending email to: " + address + ", for: "
                        + contactUID);
                try {
                    _context.startActivity(emailIntent);
                    return true;
                } catch (Exception e) {
                    Log.e(TAG, "Failed to launch email app: "
                            + address, e);
                    Toast.makeText(_context,
                            "No Email app installed...",
                            Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.w(TAG, "Unable to find email app : " + address);
                Toast.makeText(_context,
                        "No Email app installed...",
                        Toast.LENGTH_SHORT).show();
            }

            return false;
        }

        @Override
        public boolean hasFeature(ConnectorFeature feature) {
            return false;
        }

        @Override
        public Object getFeature(String connectorType,
                ConnectorFeature feature, String contactUID,
                String connectorAddress) {
            return null;
        }

        @Override
        public String getName() {
            return _context.getString(R.string.connector_email);
        }

        @Override
        public String getDescription() {
            return _context.getString(R.string.app_name)
                    + " allows the user to select an external app to send email";
        }
    }
}
