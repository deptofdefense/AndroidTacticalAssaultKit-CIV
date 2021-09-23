package com.atakmap.map.formats.c3dt;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.io.ProtocolHandler;
import com.atakmap.io.UriFactory;
import com.atakmap.util.Collections2;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Set;

public final class ContentSources {
    private ContentSources() {}

    public static ContentSource create(final ProtocolHandler handler) {
        return new ContentSource() {
            @Override
            public byte[] getData(String uri, long[] version) {
                try {
                    if(version != null)
                        version[0] = System.currentTimeMillis();
                    try(UriFactory.OpenResult result = handler.handleURI(uri)) {
                        if (result == null)
                            return null;
                        else if (result.contentLength > 0L)
                            return FileSystemUtils.read(result.inputStream, (int) result.contentLength, false);
                        else
                            return FileSystemUtils.read(result.inputStream);
                    }
                } catch(Throwable t) {
                    return null;
                }
            }

            @Override
            public void addOnContentChangedListener(OnContentChangedListener l) {}

            @Override
            public void removeOnContentChangedListener(OnContentChangedListener l) {}

            @Override
            public void connect() {}

            @Override
            public void disconnect() {}
        };
    }

    public static ContentSource createDefault() {
        return createDefault(false);
    }

    public static ContentSource createDefault(final boolean preferLast) {
        return new ContentSource() {
            ProtocolHandler preferred = null;

            @Override
            public byte[] getData(String uri, long[] version) {
                try {
                    if(version != null)
                        version[0] = System.currentTimeMillis();
                    try(UriFactory.OpenResult result = Util.open(preferred, uri)) {
                        if (result == null)
                            return null;
                        if(preferLast && result.handler != null)
                            preferred = result.handler;
                        if (result.contentLength > 0L)
                            return FileSystemUtils.read(result.inputStream, (int) result.contentLength, false);
                        else
                            return FileSystemUtils.read(result.inputStream);
                    }
                } catch(Throwable t) {
                    return null;
                }
            }

            @Override
            public void addOnContentChangedListener(OnContentChangedListener l) {}

            @Override
            public void removeOnContentChangedListener(OnContentChangedListener l) {}

            @Override
            public void connect() {}

            @Override
            public void disconnect() {}
        };
    }

    public static ContentContainer createCache(final File cacheDir, final String relativeUri) {
        return new ContentContainer() {
            final Set<OnContentChangedListener> listeners = Collections2.newIdentityHashSet();

            @Override
            public void put(String uri, byte[] data, long version) {
                final File cacheFile = getFile(uri);
                IOProviderFactory.mkdirs(cacheFile.getParentFile());
                try {
                    try(FileOutputStream fos = new FileOutputStream(cacheFile)) {
                        fos.write(data);
                    }
                    cacheFile.setLastModified(version);
                } catch(IOException ignored) {}

                synchronized(listeners) {
                    for(OnContentChangedListener l : this.listeners)
                        l.onContentChanged(this);
                }
            }

            @Override
            public byte[] getData(String uri, long[] version) {
                final File cacheFile = getFile(uri);
                if(cacheFile == null)
                    return null;
                try {
                    byte[] data = new byte[(int) IOProviderFactory.length(cacheFile)];
                    try(FileInputStream fis = IOProviderFactory.getInputStream(cacheFile)) {
                        int off = 0;
                        while(off < data.length) {
                            final int r = fis.read(data, off, (data.length-off));
                            if(r < 0) // unexpected EOF
                                return null;
                            off += r;
                        }
                    }
                    if(version != null)
                        version[0] = IOProviderFactory.lastModified(cacheFile);
                    return data;
                } catch(IOException ignored) {
                    return null;
                }
            }

            @Override
            public synchronized void addOnContentChangedListener(OnContentChangedListener l) {
                this.listeners.add(l);
            }

            @Override
            public synchronized void removeOnContentChangedListener(OnContentChangedListener l) {
                this.listeners.remove(l);
            }

            @Override
            public void connect() {

            }

            @Override
            public void disconnect() {

            }

            private File getFile(String uriStr) {
                if(relativeUri != null && uriStr.startsWith(relativeUri))
                    uriStr = uriStr.replace(relativeUri, "");
                uriStr = uriStr.substring(uriStr.indexOf(':')+1);
                while(uriStr.length() > 0 && uriStr.charAt(0) == '/')
                    uriStr = uriStr.substring(1);
                return new File(cacheDir, uriStr);
            }
        };
    }

    public static byte[] getData(ContentSource source, String uri, long[] version, boolean async) {
        if(source instanceof ContentProxy)
            return ((ContentProxy)source).getData(uri, version, async);
        else
            return source.getData(uri, version);
    }
}
