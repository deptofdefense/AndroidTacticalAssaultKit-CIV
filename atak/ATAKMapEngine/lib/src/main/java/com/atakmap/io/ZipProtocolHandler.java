package com.atakmap.io;

import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;

// derives from `FilterProtocolHandler` in the event that client code tries to
// make any assumptions with instanceof FileProtocolHandler

public class ZipProtocolHandler extends FilterProtocolHandler {

    public ZipProtocolHandler() {
        super(new ZipFileHandler());
    }

    @Override
    public Collection<String> getSupportedSchemes() {
        return Arrays.asList("zip");
    }

    private static class ZipFileHandler extends FileProtocolHandler {
        @Override
        InputStream openStream(File f) throws IOException {
            if(f instanceof ZipVirtualFile)
                return ((ZipVirtualFile)f).openStream();
            else
                return super.openStream(f);
        }

        @Override
        ZipVirtualFile getFile(String uri) {
            try {
                // short circuit for regular path
                ZipVirtualFile f = new ZipVirtualFile(uri);
                if (IOProviderFactory.exists(f))
                    return f;
            } catch(Throwable ignored) {}
            try {
                URI uriObj = new URI(uri);
                if (uriObj.getScheme() == null ||
                        uriObj.getScheme().compareToIgnoreCase("zip") == 0 ||
                    uriObj.getScheme().compareToIgnoreCase("file") == 0) {

                    return new ZipVirtualFile(uriObj.getPath());
                }
            } catch (Exception ex) {
                // ignore
            }
            return null;
        }
    }
}