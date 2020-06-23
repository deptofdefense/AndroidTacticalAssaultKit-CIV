package plugins.host.provider;

import java.util.ArrayList;
import java.util.List;

import plugins.core.Provider;
import plugins.core.model.DependencyGraph;
import plugins.core.model.Extension;
import plugins.core.model.Plugin;
import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.OperationApplicationException;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;

/**
 *
 * Content Provider to access the plugins, extensions and dependencies/
 */
public class PluginProvider extends ContentProvider {
    
    private static final String TABLES_PLUGIN_DEPENDENCIES = DbHelper.TABLE_PLUGINS + ", " + DbHelper.TABLE_DEPENDENCIES;
    private static final String TABLES_EXTENSION_PLUGIN = DbHelper.TABLE_PLUGINS + ", " + DbHelper.TABLE_EXTENSIONS;

    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
    
    private static final int PLUGINS = 0;
    private static final int PLUGIN = 1;
    private static final int EXTENSIONS = 2;
    private static final int EXTENSION = 3;
    private static final int PLUGIN_EXTENSIONS = 4;
    private static final int EXTENSION_PLUGIN = 5;
    private static final int PLUGIN_DEPENDENCIES = 6;
    
    private DbHelper helper;
    
    @Override
    public boolean onCreate() {        
        helper = new DbHelper(getContext());
        
        String authority = Provider.getAuthority(getContext());
        MATCHER.addURI(authority, "plugins", PLUGINS);
        MATCHER.addURI(authority, "plugins/#", PLUGIN);
        MATCHER.addURI(authority, "extensions", EXTENSIONS);
        MATCHER.addURI(authority, "extensions/#", EXTENSION);
        MATCHER.addURI(authority, "plugins/#/extensions", PLUGIN_EXTENSIONS);
        MATCHER.addURI(authority, "extensions/#/plugins", EXTENSION_PLUGIN);
        MATCHER.addURI(authority, "dependencies", PLUGIN_DEPENDENCIES);
        
        return false;
    }
    
    @Override
    public ContentProviderResult[] applyBatch(
            ArrayList<ContentProviderOperation> operations)
            throws OperationApplicationException {
        final SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            final int numOperations = operations.size();
            final ContentProviderResult[] results = new ContentProviderResult[numOperations];
            for (int i = 0; i < numOperations; i++) {
                results[i] = operations.get(i).apply(this, results, i);
            }
            db.setTransactionSuccessful();
            return results;
        } finally {
            db.endTransaction();
        }
    }
    
    @Override
    public String getType(Uri uri) {
        int code = MATCHER.match(uri);
        
        switch (code) {
        case PLUGINS:
            return Plugin.Fields.CONTENT_TYPE;
        case PLUGIN:
            return Plugin.Fields.CONTENT_ITEM_TYPE;
        case EXTENSIONS:
            return Extension.Fields.CONTENT_TYPE;
        case EXTENSION:
            return Extension.Fields.CONTENT_ITEM_TYPE;
        case PLUGIN_EXTENSIONS:
            return Extension.Fields.CONTENT_TYPE; 
        case EXTENSION_PLUGIN:
            return Plugin.Fields.CONTENT_ITEM_TYPE;
        case PLUGIN_DEPENDENCIES:
            return Plugin.Fields.CONTENT_TYPE;
        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        
        int code = MATCHER.match(uri);
        if( code == UriMatcher.NO_MATCH )
            throw new IllegalArgumentException("Unknown URI " + uri);
        
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();        
        List<String> pathSegments = null;
        
        switch (code) {
        case PLUGIN:
            qb.appendWhere(Plugin.Fields.ID + "=" + ContentUris.parseId(uri));
        case PLUGINS:
            qb.setTables(DbHelper.TABLE_PLUGINS);
            break;
            
        case EXTENSION:
            qb.appendWhere(Extension.Fields.ID + "=" + ContentUris.parseId(uri));
        case EXTENSIONS:        
            qb.setTables(DbHelper.TABLE_EXTENSIONS);
            break;
        case PLUGIN_EXTENSIONS:
            pathSegments = uri.getPathSegments();
            qb.appendWhere(Plugin.Fields.PLUGIN_ID + "=" + pathSegments.get(pathSegments.size()-2));
            qb.setTables(DbHelper.TABLE_EXTENSIONS);
            break;
            
        case EXTENSION_PLUGIN:
            pathSegments = uri.getPathSegments();
            qb.setTables(TABLES_EXTENSION_PLUGIN);
            qb.appendWhere(Extension.Fields.ID + "=" + pathSegments.get(pathSegments.size()-2) + 
                    " AND " + DbHelper.TABLE_EXTENSIONS + "." + Plugin.Fields.PLUGIN_ID + 
                    "=" + DbHelper.TABLE_PLUGINS + "." + Plugin.Fields.PLUGIN_ID);
            break;
            
        case PLUGIN_DEPENDENCIES:
            qb.setTables(TABLES_PLUGIN_DEPENDENCIES);
            qb.appendWhere(DbHelper.TABLE_DEPENDENCIES + "." + DependencyGraph.Fields.DEPENDENT_ID + 
                    "=" + DbHelper.TABLE_PLUGINS + "." + Plugin.Fields.PLUGIN_ID);            
        }
        
        SQLiteDatabase readableDatabase = helper.getReadableDatabase();
        Cursor c = qb.query(readableDatabase, projection, selection, selectionArgs, null, null, sortOrder);
        
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        String table = getTable(uri);
        SQLiteDatabase db = helper.getWritableDatabase();
        long rowId = db.insert(table, null, values);
        if (rowId > 0) {
            uri = ContentUris.withAppendedId(uri, rowId);
            getContext().getContentResolver().notifyChange(uri, null);
            return uri;
        }
        throw new SQLException("Failed to insert row into " + uri);
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        String table = getTable(uri);
        SQLiteDatabase db = helper.getWritableDatabase();
        int count = db.delete(table, selection, selectionArgs);
        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        String table = getTable(uri);
        SQLiteDatabase db = helper.getWritableDatabase();
        int count = db.update(table, values, selection, selectionArgs);    
        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }
    
    private static String getTable(Uri uri) {
        int code = MATCHER.match(uri);
        
        switch (code) {
        case PLUGINS:
            return DbHelper.TABLE_PLUGINS;

        case EXTENSIONS:        
            return DbHelper.TABLE_EXTENSIONS;
            
        case PLUGIN_DEPENDENCIES:
            return DbHelper.TABLE_DEPENDENCIES;
            
        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }
}
