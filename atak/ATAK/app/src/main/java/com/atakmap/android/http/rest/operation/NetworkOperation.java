
package com.atakmap.android.http.rest.operation;

import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.locale.LocaleUtil;
import com.foxykeep.datadroid.requestmanager.RequestManager;
import com.foxykeep.datadroid.service.RequestService;

/**
 *
 */
public abstract class NetworkOperation implements RequestService.Operation {

    /**
     * E.g. HTTP Status code or FTP Error Code
     */
    public static final String PARAM_STATUSCODE = NetworkOperation.class
            .getName() + ".STATUSCODE";

    public static final int STATUSCODE_UNKNOWN = -1;

    /**
     * Given a connection error return a human readable string corresponding to the error message
     * @param ce the connection error
     * @return the human readable string
     */
    public static String getErrorMessage(RequestManager.ConnectionError ce) {
        if (ce == null)
            return MapView.getMapView().getContext()
                    .getString(R.string.notification_text30);

        String error = ce.getReason();
        if (FileSystemUtils.isEmpty(error))
            error = MapView.getMapView().getContext()
                    .getString(R.string.notification_text30);
        if (ce.getStatusCode() != NetworkOperation.STATUSCODE_UNKNOWN)
            error += String.format(LocaleUtil.getCurrent(), " (%d)",
                    ce.getStatusCode());
        return error;
    }
}
