
package com.atakmap.android.importexport;

import android.net.Uri;
import android.os.Bundle;

import com.atakmap.android.network.URIStreamHandlerFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.comms.CommsMapComponent.ImportResult;

import java.io.IOException;
import java.io.InputStream;

public abstract class AbstractImporter implements Importer {

    private static final String TAG = "AbstractImporter";

    protected final String contentType;

    public AbstractImporter(String contentType) {
        this.contentType = contentType;
    }

    @Override
    public final String getContentType() {
        return this.contentType;
    }

    public static ImportResult importUriAsStream(Importer importer, Uri uri,
            String mime, Bundle bundle)
            throws IOException {
        InputStream inputStream = null;
        try {
            inputStream = URIStreamHandlerFactory.openInputStream(uri);
            return importer.importData(inputStream, mime, bundle);
        } finally {
            if (inputStream != null)
                inputStream.close();
        }
    }

    @Override
    public boolean deleteData(Uri uri, String mime) throws IOException {
        // TODO Auto-generated method stub  
        Log.w(TAG, "Ignoring delete data: " + uri.toString());
        return false;
    }
}
