
package com.atakmap.android.importexport;

import android.net.Uri;
import android.os.Bundle;

import com.atakmap.comms.CommsMapComponent;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

public interface Importer {

    /**
     * Returns the cotent type supported by this importer.
     * @return the content type
     */
    String getContentType();

    /**
     * Returns the set of mime types supported by this importer.
     * @return the set of mimetypes
     */
    Set<String> getSupportedMIMETypes();

    /**
     * The method used to import the data
     * @param source the input stream to use when importing
     * @param mime the mime type for the input stream
     * @param b the bundle that would have additional information concerning
     *          the import
     * @return the state of the import (Success, Failure or Deferred)
     * @throws IOException if there is an error during import
     */
    CommsMapComponent.ImportResult importData(InputStream source,
            String mime,
            Bundle b)
            throws IOException;

    /**
     * The method used to import the data
     * @param uri the uri to use when importing
     * @param mime the mime type for the input stream
     * @param b the bundle that would have additional information concerning
     *          the import
     * @return the state of the import (Success, Failure or Deferred)
     * @throws IOException if there is an error during import
     */
    CommsMapComponent.ImportResult importData(Uri uri, String mime,
            Bundle b)
            throws IOException;

    /**
     * The method used to remove the imported data
     * @param uri the input stream to use when importing
     * @param mime the mime type for the input stream
     * @return the state of the import (Success is true, Failure is false)
     * @throws IOException if there is an error during the deletion
     */
    boolean deleteData(Uri uri, String mime) throws IOException;

}
