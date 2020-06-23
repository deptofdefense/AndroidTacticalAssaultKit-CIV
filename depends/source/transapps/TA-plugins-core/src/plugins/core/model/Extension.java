package plugins.core.model;

import plugins.core.Provider;
import android.content.Context;
import android.net.Uri;
import android.provider.BaseColumns;

/**
 *
 * Class to represent the extensions within a plugin. It defines a static class and fields used to define the table
 * and columns to store the extensions.
 *
 */
public class Extension {

    /**
     *
     * Static class and members to define the Fields in in the Extensions table.
     */
    public static class Fields {
        public static final String ID = BaseColumns._ID;
        public static final String TYPE = "type";
        public static final String IMPL = "impl";
        public static final String SINGLETON = "singleton";
        
        /**
         * The content:// style URL for this table
         */
        public static final Uri getContentUri(Context context) {
            return Uri.withAppendedPath(Provider.getBaseUri(context), "extensions");
        }

        /**
         * The MIME type of {@link #CONTENT_URI} providing a directory of extensions.
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.plugin.extension";

        /**
         * The MIME type of a {@link #CONTENT_URI} sub-directory of a single extension.
         */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.plugin.extension";
        
        private Fields() {}
    }
    
    private long id;
    private String type;
    private String impl;
    private boolean singleton;

    /**
     * Get the id of the extension
     *
     * @return long id of the extension
     */
    public long getId() {
        return id;
    }

    /**
     * Set the if of the Extension
     *
     * @param id long id of the extension
     */
    public void setId(long id) {
        this.id = id;
    }

    /**
     * Get the fully qualified type of the extension
     *
     * @return String get the fully qualified type of the extension
     */
    public String getType() {
        return type;
    }

    /**
     * Set the fully qualified type of the Extension
     *
     * @param type
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Get the fully qualified type of the implementation class that implements the the Extension in the plugin
     * @return
     */
    public String getImpl() {
        return impl;
    }

    /**
     * Set the fully qualified type name of the extension implementation class that implements the Extension type
     *
     * @param impl String fully qualified type implementation name
     */
    public void setImpl(String impl) {
        this.impl = impl;
    }

    /**
     * Should this Extension implementation be a singleton?
     *
     * @return boolean should this extension be a singleton
     */
    public boolean isSingleton() {
        return singleton;
    }

    /**
     * Set if this extension implementation should be an extension?
     *
     * @param singleton boolean to indicate if this should be a singleton?
     */
    public void setSingleton(boolean singleton) {
        this.singleton = singleton;
    }

    /**
     * Method to create a string representation of this class.
     * @return String string representation of this class
     */
    @Override
    public String toString() {
        return impl + "[" + type + "]";
    }
}
