package plugins.core;

import android.content.Context;
import android.net.Uri;


/**
 * Utility class definition for the plugin registry hat defines some static constants.
 * It defines the PluginRegistry Authority and Base Uri.
 */
public final class Provider {
    private static final String AUTHORITY = ".plugins.registry";

    public static final String getAuthority( Context context ) {
        return context.getPackageName() + AUTHORITY;
    }
    
    public static final Uri getBaseUri( Context context ) {
        return Uri.parse("content://" + getAuthority(context));
    }
    
    
    private Provider() {}
}
