
package com.atakmap.android.maps.assets;

import android.content.Context;
import android.content.res.AssetFileDescriptor;

import com.atakmap.coremap.log.Log;
import com.atakmap.io.ProtocolHandler;
import com.atakmap.io.UriFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

public final class AssetProtocolHandler implements ProtocolHandler {
    final Context _context;

    public AssetProtocolHandler(Context context) {
        _context = context;
    }

    @Override
    public UriFactory.OpenResult handleURI(String url) {
        if (url == null)
            return null;
        if (!url.startsWith("asset:/"))
            return null;
        try {
            String path = url.substring(8);
            if (path.startsWith("/"))
                path = path.substring(1);

            final AssetFileDescriptor f = _context.getAssets().openFd(path);
            if (f == null)
                return null;

            UriFactory.OpenResult result = new UriFactory.OpenResult();
            result.contentLength = f.getLength();
            result.handler = this;
            result.inputStream = f.createInputStream();

            return result;
        } catch (Exception e) {
            Log.e("AssetProtocolHandler", "Failed to open asset", e);
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
        return Collections.singleton("asset");
    }
}
