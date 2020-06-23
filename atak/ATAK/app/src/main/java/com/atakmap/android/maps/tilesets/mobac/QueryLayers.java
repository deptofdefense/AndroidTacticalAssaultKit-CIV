
package com.atakmap.android.maps.tilesets.mobac;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * <p>
 * Class to query a web map server's GetCapabilities function and provide metadata about the layers it
 * serves.
 * </p>
 * <p>
 * To use this class, first instantiate a new object, then call process(). If process() returns
 * successfully, then metadata about the layers and the server itself may be queried using
 * getLayers(), getServerName(), etc.
 * </p>
 */
public abstract class QueryLayers {
    // human-readable name of this map server
    protected String serviceTitle;

    // The list of layers this map server provides. Layers may have children, so
    // this list may form a recursive tree structure.
    protected List<WebMapLayer> layers;

    // Mime-type strings (e.g. "image/jpeg") advertised by this server.
    protected Set<String> mimeTypes;

    // The URL that should be used for GetMap calls.
    protected String getMapURL;

    // The base URL of this server, as provided in the constructor.
    protected URL baseURL;

    // Whether or not process() has been called successfully or not.
    protected boolean isProcessed = false;

    /**
     * Return the URL that this object was created with.
     */
    public URL getBaseURL() {
        return baseURL;
    }

    /**
     * Return the list of layers provided by this map server. Since Layer objects may contain
     * children, this list may form a recursive tree structure. This method should only be called
     * after a successful call to process().
     */
    public List<WebMapLayer> getLayers() {
        return Collections.unmodifiableList(layers);
    }

    /**
     * Return the human-readable title of this map server.
     */
    public String getServiceTitle() {
        return serviceTitle;
    }

    /**
     * Return the list of map imagery formats this map server provides, in mime-type form (e.g.
     * "image/jpeg"). This method should only be called after a successful call to process().
     */
    public Set<String> getMimeTypes() {
        return Collections.unmodifiableSet(mimeTypes);
    }

    /**
     * Return the URL string that should be used for this map server's GetMap calls.
     */
    public String getGetMapURL() {
        return getMapURL;
    }

    /**
     * Returns true if process() has been called successfully. Many functions on this class return
     * undefined results if isProcessed() returns false.
     */
    public boolean isProcessed() {
        return isProcessed;
    }

    /**
     * Actually perform the query of the map server. An exception will be thrown in case of any
     * failure. This function performs network access.
     * 
     * @throws IOException in case of error.
     */
    public abstract void process() throws IOException;

    // ////////////////// Style class ///////////////////////

    /**
     * Class to encapsulate a layer style. Styles only have a name and title associated with them
     * (name is programmatic name; title is human-readable title.);
     */
    public static class Style {
        final String name;
        final String title;

        public Style(String name, String title) {
            this.name = name;
            this.title = title;
        }

        /**
         * Return the machine-usable name for this style. This is the name that would be provided in
         * tile queries.
         */
        public String getName() {
            return name;
        }

        /**
         * Return the human-readable name of this style.
         */
        public String getTitle() {
            return title;
        }

        public boolean equals(Object o) {
            if (!(o instanceof Style))
                return false;
            Style s = (Style) o;
            return name.equals(s.name) && title.equals(s.title);
        }

        public int hashCode() {
            return name.hashCode() + title.hashCode();
        }
    }

}
