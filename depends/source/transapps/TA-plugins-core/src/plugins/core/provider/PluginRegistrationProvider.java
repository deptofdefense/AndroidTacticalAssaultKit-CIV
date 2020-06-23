package plugins.core.provider;

import java.io.FileNotFoundException;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

/**
 * Simple provider that is only here to receive a tickle from the plugin host
 * when time comes to register plugins.  We need this just in case the dalvik-cache
 * gets cleared for some reason.  Maps will not have the appropriate permissions to 
 * create the dalvik cache for a plugin so this will do it when the plugin is
 * registered.
 * 
 * @author mriley
 */
public class PluginRegistrationProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) {
        return 0;
    }
    
    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String fileName = uri.getLastPathSegment();
        Resources resources = getContext().getResources();
        try {
            AssetFileDescriptor afd = resources.getAssets().openFd(fileName);
            return afd.getParcelFileDescriptor();
        } catch ( Exception e ) {
            String fileNameWithoutExtension = fileName.substring(0,fileName.lastIndexOf('.'));
            int identifier = resources.getIdentifier(fileNameWithoutExtension, "raw", null);
            AssetFileDescriptor afd = resources.openRawResourceFd(identifier);
            return afd.getParcelFileDescriptor();
        }
    }
}
