
package com.atakmap.android.missionpackage.ui;

import com.atakmap.annotations.DeprecatedApi;

public class MissionPackageViewUserState {

    private static final String TAG = "MissionPackageViewUserState";
    private boolean _bLastMapItemViaMapView;
    private boolean _bIncludeAttachments;

    public MissionPackageViewUserState() {
        this._bLastMapItemViaMapView = false;
        this._bIncludeAttachments = true;
    }

    public boolean isLastMapItemViaMapView() {
        return _bLastMapItemViaMapView;
    }

    public void setLastMapItemViaMapView(boolean bLastMapItemViaMapView) {
        this._bLastMapItemViaMapView = bLastMapItemViaMapView;
    }

    public boolean isIncludeAttachments() {
        return _bIncludeAttachments;
    }

    public void setIncludeAttachments(boolean bIncludeAttachments) {
        this._bIncludeAttachments = bIncludeAttachments;
    }
}
