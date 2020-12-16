
package com.atakmap.android.helloworld;

import android.content.Context;

import com.atakmap.android.chat.ChatManagerMapComponent;
import com.atakmap.android.contact.Contact;
import com.atakmap.android.contact.ContactConnectorManager;
import com.atakmap.android.contact.Contacts;
import com.atakmap.android.contact.IndividualContact;
import com.atakmap.android.contact.PluginConnector;
import com.atakmap.android.helloworld.plugin.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;

/**
 * Contact handler for the Hello World contact
 */
public class HelloWorldContactHandler extends
        ContactConnectorManager.ContactConnectorHandler {

    private static final String TAG = "HelloWorldContactHandler";

    private final Context pluginContext;

    public HelloWorldContactHandler(Context pluginContext) {
        this.pluginContext = pluginContext;
    }

    @Override
    public boolean isSupported(String type) {
        return FileSystemUtils.isEquals(type, PluginConnector.CONNECTOR_TYPE);
    }

    @Override
    public boolean hasFeature(
            ContactConnectorManager.ConnectorFeature feature) {
        return false;
    }

    @Override
    public String getName() {
        return pluginContext.getString(R.string.hello_world);
    }

    @Override
    public boolean handleContact(String connectorType, String contactUID,
            String connectorAddress) {
        // Called when the connector button is pressed
        // For now we'll just open the geo-chat window

        // Find the contact with this UID
        Contact contact = Contacts.getInstance().getContactByUuid(contactUID);
        if (contact instanceof IndividualContact)
            // Open the chat window using this contact
            ChatManagerMapComponent.getInstance().openConversation(
                    ((IndividualContact) contact), true);

        return true;
    }

    @Override
    public Object getFeature(String connectorType,
            ContactConnectorManager.ConnectorFeature feature, String contactUID,
            String connectorAddress) {
        return null;
    }

    @Override
    public String getDescription() {
        return "Contact handler for the "
                + pluginContext.getString(R.string.app_name);
    }
}
