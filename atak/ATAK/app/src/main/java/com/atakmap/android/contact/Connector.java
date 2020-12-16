
package com.atakmap.android.contact;

import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;

/**
 * Represents a communications path for a contact
 */
public abstract class Connector {

    /**
     * Address used to connect
     * Required to be non-null
     *
     * @return the connection string
     */
    public abstract String getConnectionString();

    /**
     * Get connect string for UI display
     * @return the connection display string
     */
    public String getConnectionDisplayString() {
        String s = getConnectionString();
        if (s != null && s.startsWith(ContactUtil.TAK_SERVER_CONNECTION_STRING))
            s = MapView.getMapView().getContext()
                    .getString(R.string.MARTI_sync_server);

        return s;
    }

    /**
     * Uniquely identifies a connection type
     * Required to be non-null
     *
     * @return the connection type
     */
    public abstract String getConnectionType();

    /**
     * Provides a human read-able label
     * Required to be non-null
     *
     * @return the label
     */
    public abstract String getConnectionLabel();

    /**
     * Specifies whether this connector is directly available/displayed to user
     * @return true for the base class connector
     */
    public boolean isUserConnector() {
        return true;
    }

    /**
     * Get icon for this connector
     *
     * @return the icon for this connector
     */
    public abstract String getIconUri();

    /**
     * Higher priority results in better chance of being the default connector, for a contact
     *
     * @return the priority of the default connector.
     */
    public int getPriority() {
        return 0;
    }

    @Override
    public String toString() {
        return getConnectionType() + ": " + getConnectionString();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Connector) {
            return this.equals((Connector) o);
        } else {
            return super.equals(o);
        }
    }

    public boolean equals(Connector c) {
        if (!FileSystemUtils.isEquals(getConnectionType(),
                c.getConnectionType()))
            return false;

        return FileSystemUtils.isEquals(getConnectionString(),
                c.getConnectionString());
    }

    @Override
    public int hashCode() {
        return 31 * getConnectionType().hashCode()
                + getConnectionString().hashCode()
                + getConnectionLabel().hashCode();
    }
}
