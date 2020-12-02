package android.content;

import android.net.Uri;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

public final class ContentResolver {
    public final static String SCHEME_CONTENT = "content";
    public final static String SCHEME_FILE = "file";
    public final static String SCHEME_ANDROID_RESOURCE = "android.resource";

    final Context.Runtime ctx;

    ContentResolver(Context.Runtime ctx) {
        this.ctx = ctx;
    }

    public InputStream openInputStream(Uri uri) throws FileNotFoundException {
        if(uri == null)
            throw new IllegalArgumentException();
        final String scheme = uri.getScheme();
        if(scheme.equals(SCHEME_CONTENT)) {
            throw new FileNotFoundException(SCHEME_CONTENT + " not implemented");
        } else if(scheme.equals(SCHEME_ANDROID_RESOURCE)) {
            if(!uri.getHost().equals(ctx.packageName))
                throw new FileNotFoundException("Unknown package: " + uri.getHost());
            if(uri.getPath().matches("\\d+")) {
                return ctx.resources.openRawResource(Integer.parseInt(uri.getPath()));
            } else if(uri.getPath().matches("[a-zA-Z\\d]+/[a-zA-Z\\d]+")) {
                String[] split = uri.getPath().split("/");
                final int resid = ctx.resources.getIdentifier(split[1], split[0], uri.getHost());
                if(resid < 1)
                    throw new FileNotFoundException("Resource ID not found");
                return ctx.resources.openRawResource(resid);
            } else {
                throw new FileNotFoundException("Malformed " + uri.getScheme() + " URI");
            }
        } else if(scheme.equals(SCHEME_FILE)) {
            return new FileInputStream(uri.getPath());
        } else {
            throw new FileNotFoundException("Invalid scheme: " + uri.getScheme());
        }
    }
}
