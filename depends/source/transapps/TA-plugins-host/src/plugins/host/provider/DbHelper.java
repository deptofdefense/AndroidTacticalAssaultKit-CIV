package plugins.host.provider;

import plugins.core.Paths;
import plugins.core.model.ClassLoaderInfo;
import plugins.core.model.DependencyGraph;
import plugins.core.model.Extension;
import plugins.core.model.Plugin;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Database to hold all the plugins, extensions and dependencies
 */
public class DbHelper extends SQLiteOpenHelper {
    
    public static final String TABLE_PLUGINS = "plugins";    
    public static final String TABLE_EXTENSIONS = "extensions";
    public static final String TABLE_DEPENDENCIES = "dependencies";
    
    public DbHelper(Context context) {
        super(context, Paths.DB, null, 3);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        StringBuilder builder = new StringBuilder();
        builder.append("create table ").append(TABLE_PLUGINS)
        .append(" (").append(Plugin.Fields.ID).append(" INTEGER PRIMARY KEY AUTOINCREMENT")
        .append(", ").append(Plugin.Fields.PLUGIN_ID).append(" TEXT UNIQUE NOT NULL")
        .append(", ").append(Plugin.Fields.VERSION).append(" TEXT")
        .append(", ").append(Plugin.Fields.NAME).append(" TEXT")
        .append(", ").append(Plugin.Fields.ACTIVATOR).append(" TEXT")
        .append(", ").append(ClassLoaderInfo.Fields.DEX_PATH).append(" TEXT")
        .append(", ").append(ClassLoaderInfo.Fields.DEX_OUTPUT_DIR).append(" TEXT")
        .append(", ").append(ClassLoaderInfo.Fields.LIB_PATH).append(" TEXT")
        .append(");");
        db.execSQL(builder.toString());
        builder.setLength(0);
        
        builder.append("create table ").append(TABLE_EXTENSIONS)
        .append(" (").append(Extension.Fields.ID).append(" INTEGER PRIMARY KEY AUTOINCREMENT")
        .append(", ").append(Plugin.Fields.PLUGIN_ID).append(" TEXT")
        .append(", ").append(Extension.Fields.TYPE).append(" TEXT")
        .append(", ").append(Extension.Fields.IMPL).append(" TEXT")
        .append(", ").append(Extension.Fields.SINGLETON).append(" INTEGER NOT NULL DEFAULT 0")
        .append(", ").append("FOREIGN KEY (").append(Plugin.Fields.PLUGIN_ID).append(") REFERENCES ").append(TABLE_PLUGINS).append("(").append(Plugin.Fields.PLUGIN_ID).append(")")
        .append(");");
        db.execSQL(builder.toString());
        builder.setLength(0);
        
        builder.append("create table ").append(TABLE_DEPENDENCIES)
        .append(" (").append(DependencyGraph.Fields.ID).append(" INTEGER PRIMARY KEY AUTOINCREMENT")
        .append(", ").append(DependencyGraph.Fields.DEPENDENT_ID).append(" TEXT")
        .append(", ").append(DependencyGraph.Fields.DEPENDEE_ID).append(" TEXT")
//        we don't want these foreign keys.  we want deps to exist without a plugin entry
//        .append(", ").append("FOREIGN KEY (").append(DependencyGraph.Fields.DEPENDENT_ID).append(") REFERENCES ").append(TABLE_PLUGINS).append("(").append(Plugin.Fields.PLUGIN_ID).append(")")
//        .append(", ").append("FOREIGN KEY (").append(DependencyGraph.Fields.DEPENDEE_ID).append(") REFERENCES ").append(TABLE_PLUGINS).append("(").append(Plugin.Fields.PLUGIN_ID).append(")")
        .append(");");
        db.execSQL(builder.toString());
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if( oldVersion < 3 ) {
            StringBuilder builder = new StringBuilder();
            builder.append("alter table ").append(TABLE_EXTENSIONS)
            .append(" ADD ").append(Extension.Fields.SINGLETON).append(" INTEGER NOT NULL DEFAULT 0");
            db.execSQL(builder.toString());
        }
    }
}
