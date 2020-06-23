
package com.atakmap.android.importexport;

import android.net.Uri;
import android.os.Bundle;

import com.atakmap.comms.CommsMapComponent;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

public interface Importer {

    String getContentType();

    Set<String> getSupportedMIMETypes();

    CommsMapComponent.ImportResult importData(InputStream source,
            String mime,
            Bundle b)
            throws IOException;

    CommsMapComponent.ImportResult importData(Uri uri, String mime,
            Bundle b)
            throws IOException;

    boolean deleteData(Uri uri, String mime) throws IOException;

}
