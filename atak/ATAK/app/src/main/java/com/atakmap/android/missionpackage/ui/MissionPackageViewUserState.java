
package com.atakmap.android.missionpackage.ui;

public class MissionPackageViewUserState {

    private static final String TAG = "MissionPackageViewUserState";
    protected String _lastImportDirectory;
    private boolean _bLastMapItemViaMapView;
    private boolean _bIncludeAttachments;

    public MissionPackageViewUserState() {
        this._lastImportDirectory = null;
        this._bLastMapItemViaMapView = false;
        this._bIncludeAttachments = false;
    }

    public String getLastImportDirectory() {
        return _lastImportDirectory;
    }

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
