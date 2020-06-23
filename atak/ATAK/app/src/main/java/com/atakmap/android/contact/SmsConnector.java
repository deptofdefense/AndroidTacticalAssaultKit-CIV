
package com.atakmap.android.contact;

import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;

public class SmsConnector extends Connector {

    public final static String CONNECTOR_TYPE = "connector.sms";

    private final String _phoneNumber;

    public SmsConnector(String phoneNumber) {
        _phoneNumber = phoneNumber;
    }

    @Override
    public String getConnectionString() {
        return _phoneNumber;
    }

    @Override
    public String getConnectionDisplayString() {

        if (_phoneNumber != null && _phoneNumber.length() == 10) {
            return TelephoneConnector.format10(_phoneNumber);
        } else if (_phoneNumber != null && _phoneNumber.length() == 11) {
            return TelephoneConnector.format11(_phoneNumber);
        }

        return super.getConnectionDisplayString();
    }

    @Override
    public String getConnectionType() {
        return CONNECTOR_TYPE;
    }

    @Override
    public String getConnectionLabel() {
        return "SMS";
    }

    @Override
    public String getIconUri() {
        return "android.resource://"
                + MapView.getMapView().getContext()
                        .getPackageName()
                + "/"
                + R.drawable.sms_icon;
    }
}
