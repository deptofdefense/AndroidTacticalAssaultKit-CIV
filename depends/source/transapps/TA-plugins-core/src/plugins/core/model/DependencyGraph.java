package plugins.core.model;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import plugins.core.Provider;
import android.content.Context;
import android.net.Uri;
import android.provider.BaseColumns;

/**
 * Class to represent any dependencies between plugins. It also defines the table fields and uri
 * for the SQL table to store this information.
 *
 */
public class DependencyGraph {

    public static class Fields {
        public static final String ID = BaseColumns._ID;
        public static final String DEPENDENT_ID = "dependentid";
        public static final String DEPENDEE_ID = "dependeeid";
        
        /**
         * The content:// style URL for this table
         */        
        public static final Uri getContentUri(Context context) {
            return Uri.withAppendedPath(Provider.getBaseUri(context), "dependencies");
        }

        /**
         * The MIME type of {@link #CONTENT_URI} providing a directory of dependencies.
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.plugin.dependency";

        /**
         * The MIME type of a {@link #CONTENT_URI} sub-directory of a single dependency.
         */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.plugin.dependency";
        
        private Fields() {}
    }
    
    /** a cache of the plugins we've loaded */
    private final Map<Long,Plugin> plugins = new ConcurrentHashMap<Long, Plugin>();
    /** an upsidedown graph of the dependencies */
    private final Map<Long,List<Long>> graph = new ConcurrentHashMap<Long, List<Long>>();
    
    public void add( Plugin dependent, Plugin dependee ) {
        plugins.put(dependee.getId(), dependee);
        plugins.put(dependent.getId(), dependent);
        
        List<Long> list = graph.get(dependent.getId());
        if( list == null ) {
            list = new LinkedList<Long>();
            graph.put(dependent.getId(), list);
        }
        
        list.add(dependee.getId());
    }
}
