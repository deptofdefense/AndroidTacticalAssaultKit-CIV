
package com.atakmap.android.data;

import android.net.Uri;

import com.atakmap.android.importexport.ImportReceiver;

import java.io.File;

/**
 * Abstract content handler specifically for files
 */
public abstract class FileContentHandler extends URIContentHandler {

    protected final File _file;

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
