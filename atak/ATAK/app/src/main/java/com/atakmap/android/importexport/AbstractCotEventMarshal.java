
package com.atakmap.android.importexport;

import android.net.Uri;

import com.atakmap.android.network.URIStreamHandlerFactory;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.io.SubInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

public abstract class AbstractCotEventMarshal extends AbstractMarshal implements
        CotEventMarshal {

    private static final String TAG = "AbstractCotEventMarshal";

    public AbstractCotEventMarshal(String contentType) {
        super(contentType);
    }

    protected abstract boolean accept(CotEvent event);

    /**************************************************************************/
    // CoT Event Marshal

    @Override
    public String marshal(CotEvent event) {
        if (event.getType() != null && this.accept(event))
            return "application/cot+xml";
        else
            return null;
    }

    /**************************************************************************/
    // Marshal

    @Override
    public String marshal(InputStream inputStream, int probeSize)
            throws IOException {

        // XXX - we may not get a complete event!!!
        final InputStreamReader reader = new InputStreamReader(
                new SubInputStream(inputStream,
                        probeSize));

        return this.marshalImpl(reader);
    }

    @Override
    public String marshal(Uri uri) throws IOException {
        InputStream inputStream = null;
        InputStreamReader reader = null;
        try {
            inputStream = URIStreamHandlerFactory.openInputStream(uri);
            if (inputStream == null)
                return null;

            reader = new InputStreamReader(inputStream);

            return this.marshalImpl(reader);
        } finally {
            if (reader != null)
                reader.close();
            if (inputStream != null)
                inputStream.close();
        }
    }

    private String marshalImpl(Reader reader) throws IOException {
        char[] arr = new char[128];
        int numChars;
        StringBuilder cot = new StringBuilder();
        do {
            numChars = reader.read(arr);
            if (numChars < 0)
                break;
            cot.append(arr, 0, numChars);
        } while (true);

        return this.marshal(CotEvent.parse(cot.toString()));
    }
}
