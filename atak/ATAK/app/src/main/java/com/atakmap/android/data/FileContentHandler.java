
package com.atakmap.android.data;

import android.net.Uri;

import com.atakmap.android.importexport.ImportReceiver;
import com.atakmap.android.maps.visibility.VisibilityCondition;
import com.atakmap.android.maps.visibility.VisibilityListener;
import com.atakmap.android.maps.visibility.VisibilityUtil;

import java.io.File;
import java.util.List;

/**
 * Abstract content handler specifically for files
 */
public abstract class FileContentHandler extends URIContentHandler
        implements VisibilityListener {

    protected final File _file;
    protected int _visCond = VisibilityCondition.IGNORE;
    protected boolean _previouslyVisible = true;

    protected FileContentHandler(File file) {
        super(URIHelper.getURI(file));
        _file = file;
    }

    /**
     * Get the file for this handler
     *
     * @return File
     */
    public File getFile() {
        return _file;
    }

    /**
     * Get the content type used for removing via Import Manager
     *
     * @return Content type
     */
    public abstract String getContentType();

    /**
     * Get the MIME type used for removing via Import Manager
     *
     * @return MIME type
     */
    public abstract String getMIMEType();

    /**
     * Set the visibility of this file
     * @param visible True if visible
     * @return True if visibility state changed
     */
    public boolean setVisible(boolean visible) {
        _visCond = VisibilityCondition.IGNORE;
        return setVisibleImpl(visible);
    }

    /**
     * Sub-classes should override this
     */
    protected boolean setVisibleImpl(boolean visible) {
        return false;
    }

    /**
     * Check if this file is visible based on visibility condition state
     * @return True if visible
     */
    public boolean isVisible() {
        return true;
    }

    protected boolean isConditionVisible() {
        return _visCond != VisibilityCondition.INVISIBLE;
    }

    @Override
    public void onVisibilityConditions(List<VisibilityCondition> conditions) {
        int newVis = VisibilityUtil.checkConditions(getFile(), conditions);
        if (_visCond != newVis) {
            Boolean visible = null;
            if (newVis == VisibilityCondition.INVISIBLE) {
                _previouslyVisible = isVisible();
                visible = false;
            } else if (_visCond == VisibilityCondition.INVISIBLE) {
                visible = _previouslyVisible;
            }
            _visCond = newVis;
            if (visible != null)
                setVisibleImpl(visible);
        }
    }

    @Override
    public void importContent() {
        // Not supported by default - only used for already-imported content
    }

    @Override
    public void deleteContent() {
        ImportReceiver.remove(Uri.fromFile(_file), getContentType(),
                getMIMEType());
    }

    @Override
    public String getTitle() {
        return _file.getName();
    }
}
