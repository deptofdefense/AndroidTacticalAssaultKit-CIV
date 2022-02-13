
package com.atakmap.android.missionpackage.ui;

import com.atakmap.annotations.DeprecatedApi;

public class MissionPackageViewUserState {

    private static final String TAG = "MissionPackageViewUserState";
    protected String _lastImportDirectory;
    private boolean _bLastMapItemViaMapView;
    private boolean _bIncludeAttachments;

    public MissionPackageViewUserState() {
        this._lastImportDirectory = null;
        this._bLastMapItemViaMapView = false;
        this._bIncludeAttachments = true;
    }

    /**
     * @deprecated This method is deprecated as of 4.3
     * The method is no longer utilized given the transition to ATAKUtilities.getStartDirectory()
     */
    @Deprecated
    @DeprecatedApi(since = "4.3", forRemoval = true, removeAt = "4.6")
    public String getLastImportDirectory() {
        return _lastImportDirectory;
    }

    /**
     * @deprecated This method is deprecated as of 4.3
     * The method is no longer utilized given the transition to ATAKUtilities.getStartDirectory()
     */
    @Deprecated
    @DeprecatedApi(since = "4.3", forRemoval = true, removeAt = "4.6")
    public void setLastImportDirectory(String lastImportDirectory) {
        this._lastImportDirectory = lastImportDirectory;
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
