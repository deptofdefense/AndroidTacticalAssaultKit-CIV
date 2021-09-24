
package com.atakmap.android.contact;

import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;

public class TelephoneConnector extends Connector {

    public final static String CONNECTOR_TYPE = "connector.telephone";

    private final String _phoneNumber;

    public TelephoneConnector(String phoneNumber) {
        _phoneNumber = phoneNumber;
    }

    @Override
    public String getConnectionString() {
        return _phoneNumber;
    }

    @Override
    public String getConnectionDisplayString() {

        if (_phoneNumber != null && _phoneNumber.length() == 10) {
            return format10(_phoneNumber);
        } else if (_phoneNumber != null && _phoneNumber.length() == 11) {
            return format11(_phoneNumber);
        }

        return super.getConnectionDisplayString();
    }

    static String format10(String phoneNumber) {
        return String.format("(%s) %s-%s",
                phoneNumber.substring(0, 3),
                phoneNumber.substring(3, 6),
                phoneNumber.substring(6, 10));
    }

    static String format11(String phoneNumber) {
        return String.format("%s (%s) %s-%s",
                phoneNumber.charAt(0),
                phoneNumber.substring(1, 4),
                phoneNumber.substring(4, 7),
                phoneNumber.substring(7, 11));
    }

    @Override
    public String getConnectionType() {
        return CONNECTOR_TYPE;
    }

    @Override
    public String getConnectionLabel() {
        return "Telephone";
    }

    @Override
    public String getIconUri() {
        return "android.resource://"
                + MapView.getMapView().getContext()
                        .getPackageName()
                + "/"
                + R.drawable.phone_icon;
    }
}
