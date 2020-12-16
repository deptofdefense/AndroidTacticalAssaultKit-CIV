package com.atakmap.map.formats.c3dt;

import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.io.ProtocolHandler;
import com.atakmap.io.UriFactory;

import java.io.File;
import java.net.URI;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;

final class Util {
    private Util() {}

    public static Buffer get(ByteBuffer buf, int pos, byte[] dst, int off, int len) {
        ByteBuffer view = buf.duplicate();
        view.position(pos);
        view.get(dst, off, len);
        return buf;
    }
    public static Buffer skip(ByteBuffer buf, int count) {
        return buf.position(buf.position()+count);
    }
    public static Executor newImmediateExecutor() {
        return new Executor() {
            @Override
            public void execute(Runnable command) {
                command.run();
            }
        };
    }
    public static UriFactory.OpenResult open(ProtocolHandler preferred, String uri) {
        if(preferred != null) {
            UriFactory.OpenResult result = preferred.handleURI(uri);
            if (result != null) {
                result.handler = preferred;
                return result;
            }
        }
        return UriFactory.open(uri);
    }
    public static String resolve(String uri) {
        final File f = new File(uri);
        if(IOProviderFactory.exists(f))
            return f.getAbsolutePath();
        try {
            URI uriObj = new URI(uri);
            if (uriObj.getPath() != null) {
                if (uriObj.getPath().endsWith("/"))
                    uriObj = uriObj.resolve("..");
                else
                    uriObj = uriObj.resolve(".");
            }
            return uriObj.toString();
        } catch(Exception ignored) {}
        return uri;
    }
}
