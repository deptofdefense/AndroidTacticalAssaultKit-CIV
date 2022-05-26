
package com.atakmap.android.network;

import android.net.Uri;

import com.atakmap.comms.http.TakHttpClient;
import com.atakmap.comms.http.TakHttpResponse;
import com.atakmap.coremap.log.Log;
import com.atakmap.io.ProtocolHandler;
import com.atakmap.io.UriFactory;

import org.apache.http.HttpEntity;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

public final class TakServerHttpsProtocolHandler implements ProtocolHandler {
    @Override
    public UriFactory.OpenResult handleURI(String url) {
        if (url == null)
            return null;
        if (!url.startsWith("https://"))
            return null;
        try {
            Uri uri = Uri.parse(url);
            TakHttpClient client = TakHttpClient
                    .GetHttpClient(uri.getScheme() + "://" + uri.getHost());
            TakHttpResponse response = client
                    .execute(new org.apache.http.client.methods.HttpGet(url));
            if (response == null)
                return null;

            if (!response.isOk())
                return null;

            final HttpEntity entity = response.getEntity();
            if (entity == null)
                return null;

            UriFactory.OpenResult result = new UriFactory.OpenResult();
            result.contentLength = response.getContentLength();
            result.handler = this;
            result.inputStream = entity.getContent();

            return result;
        } catch (Exception e) {
            Log.e("TakHttpClientProtocolHandler", "Failed to connect", e);
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
        return Collections.singletonList("https");
    }
}
