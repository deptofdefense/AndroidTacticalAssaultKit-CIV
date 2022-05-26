
package com.atakmap.android.maps.assets;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.net.Uri;

import com.atakmap.coremap.log.Log;
import com.atakmap.io.ProtocolHandler;
import com.atakmap.io.UriFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ResourceProtocolHandler implements ProtocolHandler {
    final Context _context;

    public ResourceProtocolHandler(Context context) {
        _context = context;
    }

    @Override
    public UriFactory.OpenResult handleURI(String url) {
        if (url == null)
            return null;
        if (!url.startsWith("android.resource://"))
            return null;
        try {
            Uri uri = Uri.parse(url);
            List<String> path = uri.getPathSegments();
            AssetFileDescriptor f = null;
            if (path.size() == 0) {
                // android.resource://id_number BUT: this isn't actually a valid URI according to
                // Android docs; do we really need it?
                // in = _context.getResources().openRawResource(Integer.parseInt(path.get(0)));
            } else if (path.size() == 1) {
                // android.resource://package_name/id_number
                Resources res = _context.getPackageManager()
                        .getResourcesForApplication(
                                uri.getHost());
                f = res.openRawResourceFd(Integer.parseInt(path.get(0)));
            } else if (path.size() == 2) {
                // android.resource://package_name/type/resource_name
                Resources res = _context.getPackageManager()
                        .getResourcesForApplication(
                                uri.getHost());

                int id = res.getIdentifier(path.get(1), path.get(0),
                        uri.getHost());

                f = res.openRawResourceFd(id);
            }
            if (f == null)
                return null;

            UriFactory.OpenResult result = new UriFactory.OpenResult();
            result.contentLength = f.getLength();
            result.handler = this;
            result.inputStream = f.createInputStream();

            return result;
        } catch (Exception e) {
            Log.e("ResourceProtocolHandler", "Failed to open resource", e);
            return null;
        }
    }

    @Override
    public long getContentLength(String url) {
        try {
            try (UriFactory.OpenResult result = handleURI(url)) {
                if (result == null)
                    return 0L;
                return result.contentLength;
            }
        } catch (IOException e) {
            return 0L;
        }
    }

    @Override
    public Collection<String> getSupportedSchemes() {
        return Collections.singleton("android.resource");
    }
}
